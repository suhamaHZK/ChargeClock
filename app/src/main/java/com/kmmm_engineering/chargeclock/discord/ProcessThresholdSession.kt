package com.kmmm_engineering.chargeclock.discord

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide choice between a quiet first sample and later in-process crosses.
 *
 * The first [withProcessRestore] in this process reports [firstInProcess]=true so the
 * caller uses [ThresholdFireTracker.restore] (seed only; a cross while the process was
 * dead must not notify). Later calls report false so the caller uses
 * [ThresholdFireTracker.restoreKeepingSeed] and a real cross since the last in-process
 * sample can fire. Charge and discharge share this gate.
 *
 * The flag is not reset when settings change. Threshold re-arm stays inside
 * [ThresholdFireTracker.check]. The mutex wraps the caller's load + check + save so
 * two concurrent first calls cannot both cross against a stale snapshot.
 */
internal class ProcessThresholdSession {
    private val mutex = Mutex()
    private var evaluatedInThisProcess = false

    suspend fun <T> withProcessRestore(block: suspend (firstInProcess: Boolean) -> T): T =
        mutex.withLock {
            val first = !evaluatedInThisProcess
            val result = block(first)
            evaluatedInThisProcess = true
            result
        }

    /** Test-only. Not called when settings change. */
    internal fun resetForTest() {
        evaluatedInThisProcess = false
    }
}
