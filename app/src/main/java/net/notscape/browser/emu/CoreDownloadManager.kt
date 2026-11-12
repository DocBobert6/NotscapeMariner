package net.notscape.browser.emu

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.notscape.browser.data.AppStore

/**
 * Audit #38: core downloads survive leaving the Games
 * screen. The download and update-check jobs used to
 * run in the Games screen's own rememberCoroutineScope,
 * so leaving the screen disposed the composition and
 * cancelled the job mid-download — and the progress
 * state was remember state in the composable, so
 * coming back showed nothing.
 *
 * The jobs live here now, in an application-scoped
 * singleton (the same shape as Downloads.ioScope):
 * the scope outlives the screen, the progress is a
 * StateFlow the screen re-collects on return, and the
 * dialog reappears with live progress. The .tmp Range
 * resume (CoreDownload, audit P-m9) still covers a
 * retry after the process itself dies.
 *
 * The completion callback is invoked only while the
 * Games screen is observing (see setObserverActive):
 * a download that finishes after the user left must
 * not auto-launch a game on top of the browser — the
 * user comes back to a fresh screen that re-reads the
 * installed core and plays from there instead.
 */
object CoreDownloadManager {

    /** One core job's observable state. finishedTick
     *  bumps once per completed job (download or
     *  update check) so the screen re-reads core
     *  status; lastOk is that job's outcome. */
    data class JobState(
        val running: Boolean = false,
        val title: String = "",
        val pct: Int = -1,
        val finishedTick: Int = 0,
        val lastOk: Boolean = false
    )

    private val _state = MutableStateFlow(JobState())
    val state: StateFlow<JobState> = _state

    /** Lazy, so a unit test that only touches the pure
     *  helpers never asks for Dispatchers.Main. */
    private val scope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var currentJob: Job? = null
    private var pendingDone: ((Boolean) -> Unit)? = null
    private var observerActive = false

    /** The Games screen registers while composed and
     *  unregisters on dispose; the completion callback
     *  fires only while it is registered. */
    fun setObserverActive(active: Boolean) {
        observerActive = active
    }

    /** True while a download or update check runs. */
    fun isRunning(): Boolean = _state.value.running

    /**
     * The bulk update-check summary, pure so the unit
     * suite can pin it: all-up-to-date reads as one
     * line, otherwise the non-zero counts join in
     * fixed order (updated, up to date, failed).
     */
    fun bulkUpdateSummary(
        updated: Int,
        upToDate: Int,
        failed: Int
    ): String =
        if (updated == 0 && failed == 0) {
            "All cores up to date"
        } else {
            "Core updates: " + listOf(
                if (updated > 0) "$updated updated" else null,
                if (upToDate > 0) "$upToDate up to date" else null,
                if (failed > 0) "$failed failed" else null
            ).filterNotNull().joinToString(", ")
        }

    private fun setRunning(title: String) {
        _state.value = _state.value.copy(
            running = true, title = title, pct = -1
        )
    }

    private fun setProgress(pct: Int) {
        _state.value = _state.value.copy(pct = pct)
    }

    private fun setIdle() {
        _state.value = _state.value.copy(running = false, pct = -1)
    }

    /** Terminal state for a finished job: the screen
     *  re-reads core status on the tick bump. */
    private fun finish(allOk: Boolean) {
        _state.value = _state.value.copy(
            running = false,
            pct = -1,
            finishedTick = _state.value.finishedTick + 1,
            lastOk = allOk
        )
        val done = pendingDone
        pendingDone = null
        if (observerActive) done?.invoke(allOk)
    }

    /** Early exit with no tick bump (manifest
     *  unreachable): the download callback still
     *  reports failure, as it did in the screen. */
    private fun failEarly() {
        setIdle()
        val done = pendingDone
        pendingDone = null
        if (observerActive) done?.invoke(false)
    }

    /** Download cores for [systems], driving the shared
     *  progress state. Already-downloaded cores are
     *  skipped. [onDone] reports whether every core
     *  ended up downloaded; individual failures are
     *  toasted with their refusal reason. */
    fun startDownload(
        context: Context,
        store: AppStore,
        systems: List<String>,
        labelFor: (String) -> String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (_state.value.running) {
            Toast.makeText(
                context,
                "A core download is already running",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val appContext = context.applicationContext
        pendingDone = onDone
        lateinit var job: Job
        job = scope.launch {
            try {
                setRunning("Downloading cores...")
                val manifest = CoreDownload.fetchManifest()
                    .getOrNull()
                if (manifest == null) {
                    // Cancelled while fetching: the
                    // Cancel button already reset
                    // state, so don't report a
                    // reachability failure.
                    if (currentJob?.isCancelled == true) {
                        return@launch
                    }
                    systems.forEach {
                        store.putSetting(
                            "coreError:$it",
                            "Could not reach the cores release."
                        )
                    }
                    Toast.makeText(
                        appContext,
                        "Could not reach the cores release - " +
                            "check your connection and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    failEarly()
                    return@launch
                }
                var allOk = true
                var index = 0
                for (system in systems) {
                    index++
                    val entry =
                        CoreDownload.entryFor(manifest, system)
                    if (entry == null) {
                        allOk = false
                        store.putSetting(
                            "coreError:$system",
                            "Core not listed in the cores release."
                        )
                        continue
                    }
                    store.putSetting("coreError:$system", "")
                    if (CoreDownload.isDownloaded(appContext, system)) {
                        continue
                    }
                    _state.value = _state.value.copy(
                        title = "Downloading " +
                            "${labelFor(system)} core " +
                            "($index of ${systems.size})"
                    )
                    val res = CoreDownload.download(
                        appContext, entry
                    ) { pct -> setProgress(pct) }
                    if (res.isFailure) {
                        allOk = false
                        store.putSetting(
                            "coreError:$system",
                            res.exceptionOrNull()?.message
                                ?: "Core download failed."
                        )
                        Toast.makeText(
                            appContext,
                            res.exceptionOrNull()?.message
                                ?: "Core download failed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                finish(allOk)
            } catch (e: CancellationException) {
                // Cancel already reset the state; the
                // .tmp stays for Range resume.
                setIdle()
                pendingDone = null
                throw e
            } finally {
                if (currentJob === job) currentJob = null
            }
        }
        currentJob = job
    }

    /** Core update checks: compare each installed core's
     *  hash against the manifest, and re-download only
     *  the ones that differ. A failed download leaves
     *  the old core untouched: download() discards its
     *  .tmp on failure and renames only after verify.
     *  bulk = true reports one summary toast; a single
     *  console reports its own outcome. */
    fun checkUpdates(
        context: Context,
        store: AppStore,
        consoles: List<String>,
        creditFor: (String) -> String,
        bulk: Boolean
    ) {
        if (_state.value.running) {
            Toast.makeText(
                context,
                "A core download is already running",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val appContext = context.applicationContext
        lateinit var job: Job
        job = scope.launch {
            try {
                setRunning("Checking for core updates...")
                val manifest = CoreDownload.fetchManifest()
                    .getOrNull()
                if (manifest == null) {
                    // Cancelled while fetching: the
                    // Cancel button already reset
                    // state, so don't report a
                    // reachability failure.
                    if (currentJob?.isCancelled == true) {
                        return@launch
                    }
                    setIdle()
                    Toast.makeText(
                        appContext,
                        "Could not reach the cores release - " +
                            "check your connection and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                // Installed cores only, deduped by key.
                val installed = consoles.filter {
                    CoreDownload.isDownloaded(appContext, it)
                }
                if (installed.isEmpty()) {
                    setIdle()
                    Toast.makeText(
                        appContext,
                        "No cores installed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val keys = CoreDownload.dedupeKeys(installed)
                var updated = 0
                var upToDate = 0
                var failed = 0
                for (key in keys) {
                    val entry =
                        manifest.firstOrNull { it.key == key }
                    if (entry == null) {
                        failed++
                        // Audit F-m1: a core missing from the
                        // manifest used to fail silently in the
                        // update check — record it on the console
                        // page and say so (bulk mode reports it
                        // in the summary instead of a per-core
                        // toast).
                        val system = installed.first {
                            CoreDownload.coreKeyFor(it) == key
                        }
                        store.putSetting(
                            "coreError:$system",
                            "Core not listed in the cores release."
                        )
                        if (!bulk) {
                            Toast.makeText(
                                appContext,
                                "Core not listed in the cores release.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        continue
                    }
                    val system = installed.first {
                        CoreDownload.coreKeyFor(it) == key
                    }
                    val installedHash =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                CoreDownload
                                    .downloadedFileIfPresent(
                                        appContext, system
                                    )
                                    ?.let {
                                        CoreDownload.sha256Hex(it)
                                    }
                            }.getOrNull()
                        }
                    if (!CoreDownload.isUpdateAvailable(
                            installedHash, entry.sha256
                        )
                    ) {
                        upToDate++
                        continue
                    }
                    _state.value = _state.value.copy(
                        title = "Updating " +
                            "${creditFor(system)} core..."
                    )
                    val res = CoreDownload.download(
                        appContext, entry
                    ) { pct -> setProgress(pct) }
                    if (res.isSuccess) {
                        updated++
                        if (!bulk) {
                            Toast.makeText(
                                appContext, "Core updated",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        failed++
                        Toast.makeText(
                            appContext,
                            res.exceptionOrNull()?.message
                                ?: "Core update failed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    setProgress(-1)
                }
                _state.value = _state.value.copy(
                    running = false,
                    pct = -1,
                    finishedTick = _state.value.finishedTick + 1,
                    lastOk = failed == 0
                )
                if (bulk) {
                    Toast.makeText(
                        appContext,
                        bulkUpdateSummary(updated, upToDate, failed),
                        Toast.LENGTH_LONG
                    ).show()
                } else if (updated == 0 && failed == 0) {
                    Toast.makeText(
                        appContext, "Core is up to date",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: CancellationException) {
                setIdle()
                throw e
            } finally {
                if (currentJob === job) currentJob = null
            }
        }
        currentJob = job
    }

    /** Cancel the running job, if any. The download's
     *  .tmp stays for Range resume; the caller toasts
     *  the cancellation itself. */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        pendingDone = null
        _state.value = JobState()
    }
}
