package com.techducat.thot.chrono

/**
 * A schedulable automation unit.
 *
 * @param id          Unique identifier used to cancel/reschedule.
 * @param description Human-readable description of what the script does.
 * @param isEnabled   Whether the script should run when added.
 * @param action      Suspend lambda executed on the IO dispatcher when triggered.
 */
data class ChronoScript(
    val id: String,
    val description: String,
    val isEnabled: Boolean = true,
    val action: suspend () -> Unit
)
