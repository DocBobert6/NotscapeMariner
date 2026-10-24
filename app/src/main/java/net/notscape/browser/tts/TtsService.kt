package net.notscape.browser.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import net.notscape.browser.MainActivity
import net.notscape.browser.R

/**
 * Foreground home for read-aloud (0.0.968, Phase H full build):
 * playback keeps going when the reader is left or the screen
 * turns off, with pause/stop on the notification and media
 * controls on the lock screen. The service synthesises nothing
 * itself - it mirrors [TtsState], which [TtsPlayer] drives -
 * requests audio focus (another app taking focus pauses the
 * reading), and stops itself when playback ends, errors, or is
 * stopped.
 */
class TtsService : Service() {

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "net.notscape.browser.tts.TOGGLE"
        const val ACTION_STOP = "net.notscape.browser.tts.STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, TtsService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, TtsService::class.java))
            }
        }
    }

    private var session: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            TtsPlayer.get(this).pause()
        }
    }

    private val watcher = object : Runnable {
        override fun run() {
            if (TtsState.active) {
                refresh()
                handler.postDelayed(this, 600)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW
            )
        )
        session = MediaSession(this, "notscape-tts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    TtsPlayer.get(this@TtsService).resume()
                }

                override fun onPause() {
                    TtsPlayer.get(this@TtsService).pause()
                }

                override fun onStop() {
                    TtsPlayer.get(this@TtsService).stop()
                }
            })
            isActive = true
        }
        requestFocus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val player = TtsPlayer.get(this)
                if (TtsState.phase == TtsPhase.Speaking) {
                    player.pause()
                } else if (TtsState.phase == TtsPhase.Paused) {
                    player.resume()
                }
            }
            ACTION_STOP -> TtsPlayer.get(this).stop()
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        handler.removeCallbacks(watcher)
        handler.post(watcher)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Closing the app stops the reading completely
        // (0.0.994, Robert): a started foreground service
        // otherwise outlives its task and keeps speaking
        // after the app has been swiped away.
        TtsPlayer.get(this).stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watcher)
        session?.release()
        session = null
        abandonFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest = request
            runCatching { am.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(
                    focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(focusListener) }
        }
    }

    private fun percentSuffix(): String =
        TtsState.progress.takeIf { it >= 0 }?.let { " - $it%" } ?: ""

    private fun refresh() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIF_ID, buildNotification())
        val state = when (TtsState.phase) {
            TtsPhase.Speaking -> PlaybackState.STATE_PLAYING
            TtsPhase.Paused -> PlaybackState.STATE_PAUSED
            TtsPhase.Error -> PlaybackState.STATE_ERROR
            TtsPhase.Idle -> PlaybackState.STATE_STOPPED
            else -> PlaybackState.STATE_BUFFERING
        }
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(
                    MediaMetadata.METADATA_KEY_TITLE,
                    TtsState.nowTitle.ifBlank { "Read aloud" }
                )
                .build()
        )
    }

    private fun buildNotification(): android.app.Notification {
        val phase = TtsState.phase
        val text = when (phase) {
            TtsPhase.Downloading -> "Downloading voice" + percentSuffix()
            TtsPhase.Extracting -> "Preparing voice" + percentSuffix()
            TtsPhase.Loading -> "Loading voice"
            TtsPhase.Speaking -> "Reading aloud - " + TtsState.engineLabel
            TtsPhase.Paused -> "Paused - " + TtsState.engineLabel
            TtsPhase.Error -> TtsState.message
            TtsPhase.Idle -> "Read aloud"
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TtsService::class.java).setAction(ACTION_TOGGLE),
            flags
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TtsService::class.java).setAction(ACTION_STOP),
            flags
        )
        val playing = phase == TtsPhase.Speaking
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_volume)
            .setContentTitle(TtsState.nowTitle.ifBlank { "Read aloud" })
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
                if (playing) "Pause" else "Resume",
                toggleIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }
}
