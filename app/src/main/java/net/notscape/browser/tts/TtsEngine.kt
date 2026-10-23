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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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

/** Compose-observable playback state, read by the reader chrome. */
object TtsState {
    var phase by mutableStateOf(TtsPhase.Idle)
    var engineLabel by mutableStateOf("")
    /** Download percent 0..100, or -1 while indeterminate. */
    var progress by mutableStateOf(-1)
    var message by mutableStateOf("")
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
        val marker: String
    ) {
        val url: String
            get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$fileName"
    }

    val kokoro = Pack(
        "kokoro", "Kokoro",
        "kokoro-multi-lang-v1_0.tar.bz2", "kokoro-multi-lang-v1_0", "model.onnx"
    )
    val piper = Pack(
        "piper", "Piper",
        "vits-piper-en_US-amy-low.tar.bz2", "vits-piper-en_US-amy-low",
        "en_US-amy-low.onnx"
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
                            FileOutputStream(dest).use { out ->
                                while (true) {
                                    val n = tar.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
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
            archive.delete()
            if (!isInstalled(context, pack)) {
                // A stopped or failed install leaves nothing behind
                // that could be mistaken for a working pack.
                packDir(context, pack).deleteRecursively()
            }
        }
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
fun chunkForSpeech(paragraphs: List<String>, maxChars: Int = 280): List<String> {
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
    private var worker: Thread? = null
    private var track: AudioTrack? = null
    private var engine: OfflineTts? = null
    private var enginePackId: String = ""

    fun speak(paragraphs: List<String>, title: String) {
        stopInternal()
        val chunks = chunkForSpeech(
            listOf(title) + paragraphs.filter { it.isNotBlank() }
        )
        if (chunks.isEmpty()) return
        stopRequested = false
        pausedFlag = false
        worker = thread(name = "notscape-tts", isDaemon = true) {
            runSession(chunks)
        }
    }

    fun pause() {
        if (TtsState.phase != TtsPhase.Speaking) return
        pausedFlag = true
        runCatching { track?.pause() }
        TtsState.phase = TtsPhase.Paused
    }

    fun resume() {
        if (TtsState.phase != TtsPhase.Paused) return
        pausedFlag = false
        runCatching { track?.play() }
        TtsState.phase = TtsPhase.Speaking
    }

    fun stop() {
        stopInternal()
        TtsState.phase = TtsPhase.Idle
    }

    private fun stopInternal() {
        stopRequested = true
        pausedFlag = false
        track?.let { runCatching { it.pause(); it.flush(); it.stop() } }
        worker?.interrupt()
        worker = null
    }

    private fun fail(message: String) {
        if (stopRequested) return
        TtsState.message = message
        TtsState.phase = TtsPhase.Error
    }

    private fun runSession(chunks: List<String>) {
        try {
            // Engine choice: Kokoro when installed, else Piper,
            // else download Kokoro (the primary) first.
            val pack = when {
                TtsModels.isInstalled(appContext, TtsModels.kokoro) -> TtsModels.kokoro
                TtsModels.isInstalled(appContext, TtsModels.piper) -> TtsModels.piper
                else -> {
                    if (!TtsModels.install(appContext, TtsModels.kokoro) { stopRequested }) {
                        if (!stopRequested) fail("Couldn't download the Kokoro voice.")
                        return
                    }
                    TtsModels.kokoro
                }
            }
            if (stopRequested) return
            TtsState.engineLabel = pack.label
            TtsState.phase = TtsPhase.Loading
            val tts = loadEngine(pack)
            if (stopRequested) return
            val sid = if (pack == TtsModels.kokoro) 3 else 0 // 3 = af_heart
            val first = tts.generate(chunks[0], sid, 1.0f)
            if (stopRequested) return
            val queue = ArrayBlockingQueue<FloatArray>(3)
            val producer = thread(name = "notscape-tts-gen", isDaemon = true) {
                var ok = true
                for (i in 1 until chunks.size) {
                    if (stopRequested) { ok = false; break }
                    val audio = runCatching { tts.generate(chunks[i], sid, 1.0f) }
                        .getOrNull() ?: run { ok = false; break }
                    var offered = false
                    while (!offered && !stopRequested) {
                        offered = queue.offer(audio.samples, 200, TimeUnit.MILLISECONDS)
                    }
                    if (!offered) { ok = false; break }
                }
                if (ok) {
                    while (!stopRequested) {
                        if (queue.offer(FloatArray(0), 200, TimeUnit.MILLISECONDS)) break
                    }
                }
            }
            val audioTrack = newTrack(first.sampleRate)
            track = audioTrack
            audioTrack.play()
            TtsState.phase = TtsPhase.Speaking
            writeAll(audioTrack, first.samples)
            var ended = false
            while (!stopRequested && !ended) {
                val item = queue.poll(200, TimeUnit.MILLISECONDS)
                if (item == null) {
                    if (!producer.isAlive && queue.isEmpty()) ended = true
                    continue
                }
                if (item.isEmpty()) { ended = true; break }
                writeAll(audioTrack, item)
            }
            runCatching { audioTrack.stop() }
            audioTrack.release()
            if (track === audioTrack) track = null
            producer.join(500)
            if (!stopRequested && TtsState.phase != TtsPhase.Error) {
                TtsState.phase = TtsPhase.Idle
            }
        } catch (e: InterruptedException) {
            // Stopped from another thread; state is set by stop().
        } catch (e: Exception) {
            fail("Read-aloud stopped: ${e.message ?: "unknown error"}")
        }
    }

    /** Write [samples] in small slices so pause/stop stay snappy. */
    private fun writeAll(track: AudioTrack, samples: FloatArray) {
        var off = 0
        val slice = 2048
        while (off < samples.size) {
            if (stopRequested) return
            while (pausedFlag && !stopRequested) {
                Thread.sleep(100)
            }
            if (stopRequested) return
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
            .setBufferSizeInBytes(maxOf(minBuf, sampleRate * 2 * 4))
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
