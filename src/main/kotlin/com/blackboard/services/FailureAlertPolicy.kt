package com.blackboard.services

/**
 * Decides when a persistent save failure deserves a balloon notification: once per streak of
 * [threshold] consecutive failures, never for single failures (the status line shows those) and never for successes.
 *
 * Works on a conflated status stream: intermediate states may be skipped, so it relies on
 * [SaveStatus.Failed.consecutiveFailures] rather than counting failures itself. Not thread-safe.
 */
class FailureAlertPolicy(private val threshold: Int = DEFAULT_THRESHOLD) {

    enum class Decision { NONE, NOTIFY, EXPIRE }

    private var alerted = false

    fun onStatus(status: SaveStatus): Decision = when {
        status is SaveStatus.Failed && status.consecutiveFailures >= threshold ->
            if (alerted) Decision.NONE else Decision.NOTIFY.also { alerted = true }
        // A new streak (count below threshold) or a success ends the previous alert.
        status is SaveStatus.Failed || status == SaveStatus.Saved ->
            if (alerted) Decision.EXPIRE.also { alerted = false } else Decision.NONE
        // Loading/Saving: a retry in progress, the outcome is not known yet.
        else -> Decision.NONE
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3
    }
}
