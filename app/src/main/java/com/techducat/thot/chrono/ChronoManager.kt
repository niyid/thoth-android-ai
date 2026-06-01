package com.techducat.thot.chrono

import android.util.Log
import kotlinx.coroutines.*

/**
 * Coroutine-based scheduler for [ChronoScript]s.
 *
 * Replaces the original `java.util.Timer` approach with structured concurrency,
 * giving proper lifecycle control, cancellation, and error isolation.
 *
 * Usage:
 * ```kotlin
 * val manager = ChronoManager()
 * manager.addScript(
 *     ChronoScript("send_daily", "Send daily summary") {
 *         EmailAction(context).sendEmail("you@example.com", "Daily", "Summary here")
 *     },
 *     delayMillis = 60_000L
 * )
 * // Later:
 * manager.disableScript("send_daily")
 * // On app shutdown:
 * manager.cancelAll()
 * ```
 */
class ChronoManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scripts = mutableMapOf<String, ChronoScript>()
    private val jobs = mutableMapOf<String, Job>()

    /**
     * Register [script] and schedule it to fire once after [delayMillis].
     * If a job for this script id already exists, it is cancelled first.
     */
    fun addScript(script: ChronoScript, delayMillis: Long) {
        scripts[script.id] = script
        if (script.isEnabled) {
            scheduleJob(script, delayMillis)
        }
    }

    /**
     * Register [script] and schedule it to run repeatedly every [intervalMillis],
     * optionally after an initial [initialDelayMillis].
     */
    fun addRepeatingScript(
        script: ChronoScript,
        intervalMillis: Long,
        initialDelayMillis: Long = 0L
    ) {
        scripts[script.id] = script
        if (!script.isEnabled) return

        cancelJob(script.id)
        val job = scope.launch {
            delay(initialDelayMillis)
            while (isActive) {
                runCatching { script.action() }
                    .onFailure { Log.e(TAG, "ChronoScript '${script.id}' error", it) }
                delay(intervalMillis)
            }
        }
        jobs[script.id] = job
    }

    /** Cancel and remove the scheduled job for [id]. The script registration remains. */
    fun disableScript(id: String) {
        cancelJob(id)
    }

    /**
     * Re-enable a previously registered (and possibly disabled) script,
     * scheduling it to fire once after [delayMillis].
     */
    fun enableScript(id: String, delayMillis: Long) {
        val script = scripts[id] ?: run {
            Log.w(TAG, "enableScript: no script found for id='$id'")
            return
        }
        if (!jobs.containsKey(id)) {
            scheduleJob(script, delayMillis)
        }
    }

    /** Cancel all running jobs and clean up. Call when the owning component is destroyed. */
    fun cancelAll() {
        scope.cancel()
        jobs.clear()
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun scheduleJob(script: ChronoScript, delayMillis: Long) {
        cancelJob(script.id)
        val job = scope.launch {
            delay(delayMillis)
            runCatching { script.action() }
                .onFailure { Log.e(TAG, "ChronoScript '${script.id}' error", it) }
        }
        jobs[script.id] = job
    }

    private fun cancelJob(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
    }

    companion object {
        private const val TAG = "ChronoManager"
    }
}
