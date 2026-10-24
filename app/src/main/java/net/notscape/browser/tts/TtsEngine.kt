package net.notscape.browser.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import net.notscape.browser.data.AppStore
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Read-aloud (Phase H, Robert's spec 2026-10-22): sherpa-onnx is
 * the single runtime; Kokoro-82M (multi-lang v1.0 pack, English
 * lexicons) is the primary voice and Piper (en_US-amy-low) the
 * fast fallback. The packs are far too large for the APK, so they
 * download once on first use into app files and run fully offline
 * afterwards. Playback is sentence-chunked: the next chunk
 * synthesises while the current one plays, which keeps Kokoro
 * (roughly real-time on modern hardware) ahead of the listener.
 */

enum class TtsPhase { Idle, Downloading, Extracting, Loading, Speaking, Paused, Error }

/** The sample the Voice sheet previews a voice/speed with. */
private const val PREVIEW_LINE =
    "This is my reading voice, at the speed you have chosen. Happy listening."

/** Compose-observable playback state, read by the reader chrome. */
object TtsState {
    var phase by mutableStateOf(TtsPhase.Idle)
    var engineLabel by mutableStateOf("")
    /** Download percent 0..100, or -1 while indeterminate. */
    var progress by mutableStateOf(-1)
    var message by mutableStateOf("")
    /** Title of the text being read (book / article). */
    var nowTitle by mutableStateOf("")
    /** Speakers the loaded engine reports; 0 until first load. */
    var speakers by mutableStateOf(0)
    /** True while a pack download or extract is running,
     * whichever flow started it (sheet or Listen tap). */
    var installing by mutableStateOf(false)
    /** Paragraph now being spoken, as an index into the
     * list handed to speak(); -1 while the title plays or
     * nothing is (0.0.987, follow-along). */
    var nowParagraph by mutableStateOf(-1)
    /** Which text the running session belongs to; the
     * reader compares it with its own key before it
     * highlights or auto-scrolls. */
    var nowSourceKey by mutableStateOf("")
    /** Sleep-timer deadline (epoch ms), 0 = off, and the
     * minutes chosen, for the Voice sheet's pills. */
    var sleepDeadline by mutableStateOf(0L)
    var sleepMinutes by mutableStateOf(0)
    val active: Boolean
        get() = phase == TtsPhase.Downloading || phase == TtsPhase.Extracting ||
            phase == TtsPhase.Loading || phase == TtsPhase.Speaking ||
            phase == TtsPhase.Paused
}

object TtsModels {
    class Pack(
        val id: String,
        val label: String,
        val fileName: String,
        val dirName: String,
        val marker: String,
        val sha256: String
    ) {
        val url: String
            get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$fileName"
    }

    val kokoro = Pack(
        "kokoro", "Kokoro",
        "kokoro-multi-lang-v1_0.tar.bz2", "kokoro-multi-lang-v1_0", "model.onnx",
        "c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298"
    )
    val piper = Pack(
        "piper", "Piper",
        "vits-piper-en_US-amy-low.tar.bz2", "vits-piper-en_US-amy-low",
        "en_US-amy-low.onnx",
        "c70f5284a09a7fd4ed203b39b2ff51cac1432b422b852eb647b481dade3cf639"
    )

    fun root(context: Context): File = File(context.filesDir, "tts")
    fun packDir(context: Context, pack: Pack): File = File(root(context), pack.dirName)
    fun isInstalled(context: Context, pack: Pack): Boolean {
        val dir = packDir(context, pack)
        // The completion marker is written only after a full,
        // verified extraction; a lone model file can be the
        // remains of an interrupted one (0.0.965).
        return File(dir, pack.marker).exists() && File(dir, COMPLETE).exists()
    }

    private const val COMPLETE = ".tts-complete"

    // Extraction caps (0.0.9993): a hostile or corrupt
    // archive must not fill the disk. Far above any real
    // pack (Kokoro extracts to roughly 380 MB).
    private const val MAX_ENTRY_BYTES = 700L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 1_200L * 1024 * 1024

    /** SHA-256 of the downloaded archive against its pin. */
    private fun sha256Matches(file: File, expected: String): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest()
            .joinToString("") { "%02x".format(it) } == expected
    }

    /**
     * Download and extract [pack], reporting into [TtsState].
     * Runs on the caller's (background) thread. [shouldStop] is
     * polled so a user Stop aborts promptly and cleans up.
     */
    fun install(context: Context, pack: Pack, shouldStop: () -> Boolean): Boolean {
        val rootDir = root(context).apply { mkdirs() }
        val archive = File(rootDir, pack.fileName)
        // Any previous partial install goes first: extraction
        // starts from an empty directory or not at all.
        packDir(context, pack).deleteRecursively()
        TtsState.installing = true
        try {
            TtsState.phase = TtsPhase.Downloading
            TtsState.progress = -1
            val conn = URL(pack.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            if (conn.responseCode !in 200..299) return false
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                FileOutputStream(archive).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (shouldStop()) return false
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            TtsState.progress = (downloaded * 100 / total).toInt()
                        }
                    }
                }
            }
            // A short download is a failed download: extracting a
            // truncated archive wastes minutes and poisons state.
            if (total > 0 && downloaded != total) return false
            // The archive must match its pin before anything
            // unpacks it (0.0.9993): a swapped or corrupted
            // download fails here, never inside the engine.
            if (!sha256Matches(archive, pack.sha256)) return false
            if (shouldStop()) return false
            TtsState.phase = TtsPhase.Extracting
            TtsState.progress = -1
            val rootCanonical = rootDir.canonicalPath + File.separator
            val archiveSize = archive.length().coerceAtLeast(1)
            // Extraction progress from the compressed stream's
            // position - the old indeterminate "Preparing" read
            // as a hang on device (0.0.965).
            val counting = CountingInputStream(archive.inputStream().buffered()) { consumed ->
                TtsState.progress = (consumed * 100 / archiveSize)
                    .toInt().coerceIn(0, 99)
            }
            var totalBytes = 0L
            counting.use { raw ->
                TarArchiveInputStream(BZip2CompressorInputStream(raw)).use { tar ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (shouldStop()) return false
                        val entry = tar.nextEntry ?: break
                        // The Chinese payload (the jieba dict is
                        // several hundred MB, plus zh lexicons and
                        // FSTs) is never written at all - skipping
                        // it here, instead of extracting then
                        // deleting, is most of the speed fix.
                        if (isSkippedPayload(entry.name)) continue
                        val dest = File(rootDir, entry.name)
                        // No path may escape the tts directory.
                        if (!dest.canonicalPath.startsWith(rootCanonical)) continue
                        if (entry.isDirectory) {
                            dest.mkdirs()
                        } else {
                            dest.parentFile?.mkdirs()
                            var entryBytes = 0L
                            FileOutputStream(dest).use { out ->
                                while (true) {
                                    val n = tar.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    entryBytes += n
                                    totalBytes += n
                                    if (entryBytes > MAX_ENTRY_BYTES ||
                                        totalBytes > MAX_TOTAL_BYTES
                                    ) return false
                                }
                            }
                        }
                    }
                }
            }
            // Sanity: every file the engine config points at must
            // be present before the pack counts as installed.
            val dir = packDir(context, pack)
            val sane = File(dir, pack.marker).exists() &&
                File(dir, "tokens.txt").exists() &&
                File(dir, "espeak-ng-data").isDirectory &&
                (pack != kokoro || File(dir, "voices.bin").exists())
            if (!sane) return false
            File(dir, COMPLETE).writeText("ok")
            TtsState.progress = 100
            return true
        } catch (e: Exception) {
            return false
        } finally {
            TtsState.installing = false
            archive.delete()
            if (!isInstalled(context, pack)) {
                // A stopped or failed install leaves nothing behind
                // that could be mistaken for a working pack.
                packDir(context, pack).deleteRecursively()
            }
        }
    }
}

/**
 * The selectable voices (0.0.968). Kokoro's English set in pack
 * order - speaker ids 0-27 of kokoro-multi-lang-v1_0; id 3
 * (af_heart) is the long-standing default. Piper's pack carries
 * the single Amy voice.
 */
object TtsVoices {
    data class Voice(val sid: Int, val name: String)

    val kokoro: List<Voice> = listOf(
        "Alloy", "Aoede", "Bella", "Heart", "Jessica", "Kore",
        "Nicole", "Nova", "River", "Sarah", "Sky",
        "Adam", "Echo", "Eric", "Fenrir", "Liam", "Michael",
        "Onyx", "Puck", "Santa",
        "Alice", "Emma", "Isabella", "Lily",
        "Daniel", "Fable", "George", "Lewis"
    ).mapIndexed { i, n -> Voice(i, n) }

    val piper: List<Voice> = listOf(Voice(0, "Amy"))

    fun forPack(packId: String): List<Voice> =
        if (packId == TtsModels.piper.id) piper else kokoro

    fun defaultSid(packId: String): Int =
        if (packId == TtsModels.piper.id) 0 else 3

    fun sidSettingKey(packId: String): String =
        if (packId == TtsModels.piper.id) "ttsVoicePiper" else "ttsVoiceKokoro"

    fun clampSpeed(value: Float): Float = value.coerceIn(0.5f, 2.0f)

    fun clampSid(packId: String, sid: Int, speakers: Int): Int {
        val packMax = forPack(packId).maxOfOrNull { it.sid } ?: 0
        val max = if (speakers > 0) minOf(packMax, speakers - 1) else packMax
        return sid.coerceIn(0, max)
    }
}

/** Entries in the Kokoro pack this English-only install skips. */
private fun isSkippedPayload(entryName: String): Boolean {
    val name = entryName.substringAfter('/', "")
    return name.startsWith("dict/") ||
        name in setOf(
            "lexicon-zh.txt", "date-zh.fst", "number-zh.fst", "phone-zh.fst"
        )
}

/** Counts bytes passing through, for extraction progress. */
private class CountingInputStream(
    inner: java.io.InputStream,
    private val onProgress: (Long) -> Unit
) : java.io.FilterInputStream(inner) {
    private var count = 0L

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) {
            count++
            onProgress(count)
        }
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) {
            count += n
            onProgress(count)
        }
        return n
    }
}

/**
 * Split paragraphs into speakable chunks of at most ~[maxChars]:
 * whole sentences are packed together, a paragraph break always
 * ends a chunk (a natural pause), and an over-long sentence is
 * hard-split at spaces. Pure and unit-tested.
 */
/**
 * Chunk [paragraphs] for speech, remembering which
 * paragraph each chunk came from (0.0.987, follow-along).
 * chunkForSpeech flushes at every paragraph end, so
 * chunking paragraph by paragraph yields the identical
 * sequence as chunking the whole list at once. Paragraphs
 * before [startAt], and blank ones, produce no chunks.
 * Returns the chunks and, per chunk, its paragraph index.
 */
fun chunkParagraphs(
    paragraphs: List<String>,
    startAt: Int = 0
): Pair<List<String>, List<Int>> {
    val chunks = ArrayList<String>()
    val paraMap = ArrayList<Int>()
    paragraphs.forEachIndexed { idx, p ->
        if (idx < startAt || p.isBlank()) {
            return@forEachIndexed
        }
        val cs = chunkForSpeech(listOf(p))
        chunks += cs
        repeat(cs.size) { paraMap += idx }
    }
    return chunks to paraMap
}

fun chunkForSpeech(paragraphs: List<String>, maxChars: Int = 150): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    fun flush() {
        val s = sb.toString().trim()
        if (s.isNotEmpty()) out += s
        sb.clear()
    }
    for (p in paragraphs) {
        val sentences = p.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }.filter { it.isNotEmpty() }
        for (sent in sentences) {
            if (sent.length > maxChars) {
                flush()
                var rest = sent
                while (rest.length > maxChars) {
                    val cut = rest.lastIndexOf(' ', maxChars)
                    val at = if (cut > 40) cut else maxChars
                    out += rest.substring(0, at).trim()
                    rest = rest.substring(at).trim()
                }
                if (rest.isNotEmpty()) sb.append(rest)
                continue
            }
            if (sb.length > 0 && sb.length + 1 + sent.length > maxChars) flush()
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(sent)
        }
        flush()
    }
    flush()
    return out
}

class TtsPlayer private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: TtsPlayer? = null

        fun get(context: Context): TtsPlayer =
            instance ?: synchronized(this) {
                instance ?: TtsPlayer(context.applicationContext).also { instance = it }
            }
    }

    @Volatile private var stopRequested = false
    @Volatile private var pausedFlag = false
    /** Voice/speed the running session speaks with; the typeset
     *  sheet's pickers update these live (next chunk onward). */
    @Volatile private var liveSid = 3
    @Volatile private var liveSpeed = 1.0f
    private val installGuard =
        java.util.concurrent.atomic.AtomicBoolean(false)

    private fun store(): AppStore = AppStore.get(appContext)

    /** The pack the user chose in the typeset sheet. */
    fun chosenPack(): TtsModels.Pack =
        if (store().setting("ttsEngine", TtsModels.kokoro.id) ==
            TtsModels.piper.id
        ) {
            TtsModels.piper
        } else {
            TtsModels.kokoro
        }

    fun setEngine(packId: String) {
        store().putSetting("ttsEngine", packId)
        // A different pack is a different engine: stop any
        // current reading so the next Listen uses the new voice.
        if (TtsState.phase == TtsPhase.Speaking ||
            TtsState.phase == TtsPhase.Paused
        ) {
            stop()
        }
    }

    fun setVoice(sid: Int) {
        val packId = chosenPack().id
        val clamped = TtsVoices.clampSid(packId, sid, TtsState.speakers)
        store().putSetting(TtsVoices.sidSettingKey(packId), clamped.toString())
        liveSid = clamped
    }

    fun setSpeed(value: Float) {
        val clamped = TtsVoices.clampSpeed(value)
        store().putSetting("ttsSpeed", clamped.toString())
        liveSpeed = clamped
    }

    @Volatile private var previewActive = false

    // Sleep timer (0.0.987): a deadline the write loop
    // watches; pausing suspends it.
    @Volatile private var sleepDeadlineMs = 0L
    @Volatile private var sleepPausedAtMs = 0L
    /** Paragraph index per chunk of the running session. */
    @Volatile private var chunkParaMap: List<Int> = emptyList()

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            sleepDeadlineMs = 0L
            TtsState.sleepDeadline = 0L
            TtsState.sleepMinutes = 0
        } else {
            sleepDeadlineMs =
                System.currentTimeMillis() + minutes * 60_000L
            TtsState.sleepDeadline = sleepDeadlineMs
            TtsState.sleepMinutes = minutes
        }
    }

    private fun sleepDue(): Boolean =
        sleepDeadlineMs > 0 &&
            System.currentTimeMillis() >= sleepDeadlineMs

    /** Fade the voice out over ~1.5 s, then end the session. */
    private fun fadeAndStop(track: AudioTrack) {
        runCatching {
            var v = 1.0f
            while (v > 0.05f && !stopRequested) {
                v -= 0.08f
                track.setVolume(v.coerceAtLeast(0f))
                Thread.sleep(120)
            }
        }
        sleepDeadlineMs = 0L
        TtsState.sleepDeadline = 0L
        TtsState.sleepMinutes = 0
        TtsState.nowParagraph = -1
        stopRequested = true
        sessionEpoch++
        TtsState.phase = TtsPhase.Idle
        TtsService.stop(appContext)
    }

    private fun publishChunk(index: Int) {
        TtsState.nowParagraph =
            chunkParaMap.getOrNull(index) ?: -1
    }

    /**
     * Speak the sample sentence in the currently chosen voice
     * (0.0.970). Only from rest, and only with the chosen pack
     * installed; it never interrupts a real reading, though a
     * fresh tap may restart a sample already playing. No
     * foreground service - a few seconds inside the sheet only.
     */
    fun preview() {
        if (TtsState.active && !previewActive) return
        val pack = chosenPack()
        if (!TtsModels.isInstalled(appContext, pack)) return
        val st = store()
        liveSid = TtsVoices.clampSid(
            pack.id,
            st.setting(
                TtsVoices.sidSettingKey(pack.id),
                TtsVoices.defaultSid(pack.id).toString()
            ).toIntOrNull() ?: TtsVoices.defaultSid(pack.id),
            TtsState.speakers
        )
        liveSpeed = TtsVoices.clampSpeed(
            st.setting("ttsSpeed", "1.0").toFloatOrNull() ?: 1.0f
        )
        val prevWorker = stopInternal()
        stopRequested = false
        pausedFlag = false
        previewActive = true
        chunkParaMap = emptyList()
        TtsState.nowParagraph = -1
        val myEpoch = sessionEpoch
        worker = thread(name = "notscape-tts-preview", isDaemon = true) {
            runCatching { prevWorker?.join(3_000) }
            if (myEpoch == sessionEpoch) {
                runSession(listOf(PREVIEW_LINE), myEpoch)
            }
        }
    }

    /** Download [pack] from the typeset sheet, off the UI thread. */
    fun installPack(pack: TtsModels.Pack) {
        if (TtsModels.isInstalled(appContext, pack)) return
        if (!installGuard.compareAndSet(false, true)) return
        stopRequested = false
        TtsState.installing = true
        thread(name = "notscape-tts-install", isDaemon = true) {
            try {
                val ok = TtsModels.install(appContext, pack) { stopRequested }
                if (!ok && !stopRequested) {
                    TtsState.message =
                        "Couldn't download the ${pack.label} voice."
                    TtsState.phase = TtsPhase.Error
                } else if (TtsState.phase == TtsPhase.Downloading ||
                    TtsState.phase == TtsPhase.Extracting
                ) {
                    TtsState.phase = TtsPhase.Idle
                }
            } finally {
                TtsState.installing = false
                installGuard.set(false)
            }
        }
    }
    private var worker: Thread? = null
    /** Session epoch (0.0.99994): every speak/stop bumps
     * it, and a session only acts while its epoch is
     * current - a dying session can no longer be revived
     * by the next session resetting a shared stop flag
     * (the zombie-session wedge behind the stalls, the
     * frozen highlight and the dead tap-to-read). */
    @Volatile private var sessionEpoch = 0
    /** Serialises generate() on the shared engine:
     * sherpa engines are not thread-safe (0.0.99994). */
    private val engineLock = Any()
    private var track: AudioTrack? = null
    private var engine: OfflineTts? = null
    private var enginePackId: String = ""

    fun speak(
        paragraphs: List<String>,
        title: String,
        sourceKey: String = "",
        startAt: Int = 0
    ) {
        val prevWorker = stopInternal()
        // Per-paragraph chunking (0.0.987): identical output
        // to the old whole-list call, but every chunk knows
        // its paragraph - follow-along highlights by it. The
        // title is spoken only from the beginning.
        val (bodyChunks, bodyMap) = chunkParagraphs(paragraphs, startAt)
        val chunks = ArrayList<String>()
        val paraMap = ArrayList<Int>()
        if (startAt <= 0) {
            val tc = chunkForSpeech(listOf(title))
            chunks += tc
            repeat(tc.size) { paraMap += -1 }
        }
        chunks += bodyChunks
        paraMap += bodyMap
        if (chunks.isEmpty()) return
        stopRequested = false
        pausedFlag = false
        previewActive = false
        chunkParaMap = paraMap
        TtsState.nowTitle = title
        TtsState.nowSourceKey = sourceKey
        TtsState.nowParagraph = -1
        // A sleep deadline that passed while nothing was
        // playing is spent, not a firing offence.
        if (sleepDeadlineMs > 0 &&
            sleepDeadlineMs <= System.currentTimeMillis()
        ) {
            sleepDeadlineMs = 0L
            TtsState.sleepDeadline = 0L
            TtsState.sleepMinutes = 0
        }
        val pack = chosenPack()
        val st = store()
        liveSid = TtsVoices.clampSid(
            pack.id,
            st.setting(
                TtsVoices.sidSettingKey(pack.id),
                TtsVoices.defaultSid(pack.id).toString()
            ).toIntOrNull() ?: TtsVoices.defaultSid(pack.id),
            TtsState.speakers
        )
        liveSpeed = TtsVoices.clampSpeed(
            st.setting("ttsSpeed", "1.0").toFloatOrNull() ?: 1.0f
        )
        // Background playback (0.0.968): the foreground service
        // keeps the reading alive past this screen.
        TtsService.start(appContext)
        val myEpoch = sessionEpoch
        worker = thread(name = "notscape-tts", isDaemon = true) {
            // The previous session dies at its next chunk
            // boundary (epoch); wait for it to leave the
            // engine before this session starts (0.0.99994).
            runCatching { prevWorker?.join(3_000) }
            if (myEpoch == sessionEpoch) {
                runSession(chunks, myEpoch)
            }
        }
    }

    fun pause() {
        if (TtsState.phase != TtsPhase.Speaking) return
        pausedFlag = true
        if (sleepDeadlineMs > 0) {
            sleepPausedAtMs = System.currentTimeMillis()
        }
        runCatching { track?.pause() }
        TtsState.phase = TtsPhase.Paused
    }

    fun resume() {
        if (TtsState.phase != TtsPhase.Paused) return
        pausedFlag = false
        if (sleepDeadlineMs > 0 && sleepPausedAtMs > 0) {
            sleepDeadlineMs +=
                System.currentTimeMillis() - sleepPausedAtMs
            TtsState.sleepDeadline = sleepDeadlineMs
            sleepPausedAtMs = 0L
        }
        runCatching { track?.play() }
        TtsState.phase = TtsPhase.Speaking
    }

    fun stop() {
        stopInternal()
        sleepDeadlineMs = 0L
        sleepPausedAtMs = 0L
        TtsState.sleepDeadline = 0L
        TtsState.sleepMinutes = 0
        TtsState.nowParagraph = -1
        TtsState.nowSourceKey = ""
        TtsState.phase = TtsPhase.Idle
        TtsService.stop(appContext)
    }

    /** Ends the current session (the epoch bump makes its
     * threads stale at their next check) and returns the
     * worker thread, so the next session can wait for it
     * to leave the engine (0.0.99994). */
    private fun stopInternal(): Thread? {
        sessionEpoch++
        stopRequested = true
        pausedFlag = false
        track?.let { runCatching { it.pause(); it.flush(); it.stop() } }
        val old = worker
        worker = null
        old?.interrupt()
        return old
    }

    private fun fail(message: String) {
        if (stopRequested) return
        TtsState.message = message
        TtsState.phase = TtsPhase.Error
    }

    /**
     * One reading session (0.0.99994). A producer thread is
     * the engine's only caller: it generates sentence-sized
     * chunks into a small queue, serialised on [engineLock];
     * this thread consumes - publishing each chunk's
     * paragraph as the chunk starts playing (follow-along)
     * and writing it to the track. The old shape generated
     * on the consuming thread, so a generate slower than
     * real time starved the track outright (speech,
     * silence, speech - Robert's "slow"), and the shared
     * stop flag let a dying session be revived by the next
     * speak() and wedge the engine for good.
     */
    private fun runSession(chunks: List<String>, epoch: Int) {
        fun alive() = epoch == sessionEpoch
        try {
            android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_AUDIO
            )
            // Engine: the pack chosen in the typeset sheet,
            // downloaded first if it isn't installed yet.
            val pack = chosenPack()
            if (!TtsModels.isInstalled(appContext, pack)) {
                if (!TtsModels.install(appContext, pack) { !alive() }) {
                    if (alive()) {
                        fail("Couldn't download the ${pack.label} voice.")
                    }
                    return
                }
            }
            if (!alive()) return
            TtsState.engineLabel = pack.label
            TtsState.phase = TtsPhase.Loading
            val tts = loadEngine(pack)
            if (!alive()) return
            TtsState.speakers =
                runCatching { tts.numSpeakers() }.getOrDefault(0)
            liveSid = TtsVoices.clampSid(pack.id, liveSid, TtsState.speakers)
            val queue = java.util.concurrent.ArrayBlockingQueue<
                Pair<Int, FloatArray>>(2)
            val genError = java.util.concurrent.atomic
                .AtomicReference<Exception?>(null)
            val producerDone = java.util.concurrent.atomic
                .AtomicBoolean(false)
            val firstRate = java.util.concurrent.atomic
                .AtomicInteger(0)
            val producer = thread(
                name = "notscape-tts-gen", isDaemon = true
            ) {
                try {
                    for (i in chunks.indices) {
                        if (!alive()) break
                        val audio = synchronized(engineLock) {
                            tts.generate(
                                chunks[i], liveSid, liveSpeed
                            )
                        }
                        if (firstRate.get() == 0) {
                            firstRate.set(audio.sampleRate)
                        }
                        var offered = false
                        while (alive() && !offered) {
                            offered = queue.offer(
                                i to audio.samples, 200,
                                java.util.concurrent.TimeUnit
                                    .MILLISECONDS
                            )
                        }
                        if (!alive()) break
                    }
                } catch (e: Exception) {
                    genError.set(e)
                } finally {
                    producerDone.set(true)
                }
            }
            // First audio decides the track's sample rate.
            var item: Pair<Int, FloatArray>? = null
            while (alive() && item == null && !producerDone.get()) {
                item = queue.poll(
                    200, java.util.concurrent.TimeUnit.MILLISECONDS
                )
            }
            if (item == null) item = queue.poll()
            if (item == null) {
                genError.get()?.let {
                    if (alive()) {
                        fail(
                            "Read-aloud stopped: " +
                                (it.message ?: "unknown error")
                        )
                    }
                }
                return
            }
            val audioTrack = newTrack(
                firstRate.get().takeIf { it > 0 } ?: 24000
            )
            track = audioTrack
            audioTrack.play()
            TtsState.phase = TtsPhase.Speaking
            while (item != null && alive()) {
                publishChunk(item.first)
                writeAll(audioTrack, item.second, epoch)
                if (!alive()) break
                item = null
                while (alive() && item == null &&
                    !producerDone.get()
                ) {
                    item = queue.poll(
                        200,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                    )
                }
                if (item == null) item = queue.poll()
            }
            runCatching { audioTrack.stop() }
            audioTrack.release()
            if (track === audioTrack) track = null
            if (alive()) {
                TtsState.nowParagraph = -1
                // A session that ends on its own spends the
                // sleep timer with it.
                sleepDeadlineMs = 0L
                TtsState.sleepDeadline = 0L
                TtsState.sleepMinutes = 0
                if (TtsState.phase != TtsPhase.Error) {
                    TtsState.phase = TtsPhase.Idle
                }
                genError.get()?.let {
                    fail(
                        "Read-aloud stopped: " +
                            (it.message ?: "unknown error")
                    )
                }
            }
            runCatching { producer.join(500) }
        } catch (e: InterruptedException) {
            // Stopped from another thread; state is set by stop().
        } catch (e: Exception) {
            if (epoch == sessionEpoch) {
                fail(
                    "Read-aloud stopped: " +
                        (e.message ?: "unknown error")
                )
            }
        }
    }

    /** Write [samples] in small slices so pause/stop stay snappy. */
    private fun writeAll(
        track: AudioTrack, samples: FloatArray, epoch: Int
    ) {
        var off = 0
        val slice = 2048
        while (off < samples.size) {
            if (epoch != sessionEpoch) return
            while (pausedFlag && epoch == sessionEpoch) {
                Thread.sleep(100)
            }
            if (epoch != sessionEpoch) return
            if (sleepDue()) {
                fadeAndStop(track)
                return
            }
            val n = minOf(slice, samples.size - off)
            val written = track.write(
                samples, off, n, AudioTrack.WRITE_BLOCKING
            )
            if (written <= 0) return
            off += written
        }
    }

    private fun newTrack(sampleRate: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, sampleRate * 6 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun loadEngine(pack: TtsModels.Pack): OfflineTts {
        engine?.let { if (enginePackId == pack.id) return it }
        val dir = TtsModels.packDir(appContext, pack)
        val config = if (pack == TtsModels.kokoro) {
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = File(dir, "model.onnx").absolutePath,
                        voices = File(dir, "voices.bin").absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").absolutePath,
                        lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt")
                            .joinToString(",") { File(dir, it).absolutePath }
                    ),
                    numThreads = 4
                )
            )
        } else {
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, "en_US-amy-low.onnx").absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").absolutePath
                    ),
                    numThreads = 4
                )
            )
        }
        return OfflineTts(assetManager = null, config = config).also {
            engine = it
            enginePackId = pack.id
        }
    }
}
