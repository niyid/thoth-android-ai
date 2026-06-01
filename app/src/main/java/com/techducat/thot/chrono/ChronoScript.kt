package com.techducat.thot.chrono

/**
 * A schedulable automation unit.
 *
 * Note: intentionally NOT a data class because it holds a suspend function reference,
 * which breaks the auto-generated equals/hashCode/copy contract.
 *
 * @param id          Unique identifier used to cancel/reschedule.
 * @param description Human-readable description of what the script does.
 * @param isEnabled   Whether the script should run when added.
 * @param action      Suspend lambda executed on the IO dispatcher when triggered.
 */
class ChronoScript(
    val id: String,
    val description: String,
    val isEnabled: Boolean = true,
    val action: suspend () -> Unit
) {
    override fun toString(): String = "ChronoScript(id='$id', description='$description', isEnabled=$isEnabled)"
}
