package net.notscape.browser.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Holds the actual player so playback continues with the screen off or the
 * player activity closed (listen mode), with system notification and
 * lock-screen controls provided by the media session.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerNet.buildPlayer(this)
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer)
            .setCallback(object : MediaSession.Callback {
                // Controller gate (Phase D): only trusted controllers
                // (the system, lock screen, Bluetooth — Media3 marks
                // those trusted) and our own app may connect. Any
                // other app that finds the session is refused, so a
                // random app can't drive playback or read the queue.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val trusted = runCatching { controller.isTrusted }.getOrDefault(false) ||
                        controller.packageName == packageName
                    return if (trusted) {
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                    } else {
                        MediaSession.ConnectionResult.reject()
                    }
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
