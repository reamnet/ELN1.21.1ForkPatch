package mods.eln.transparentnode.utilitylight

import kotlin.math.floor

/** Quarter-block travel along the pole, leaving clearance below its conductors. */
object StreetLightMount {
    const val MIN_STEP = -4
    const val MAX_STEP = 1
    const val DEFAULT_HEIGHT = 2.30
    const val STEP_METRES = .25
    fun validStep(step: Int) = step in MIN_STEP..MAX_STEP
    fun offset(step: Int) = step.coerceIn(MIN_STEP, MAX_STEP) * STEP_METRES
    fun height(step: Int) = DEFAULT_HEIGHT + offset(step)
    fun lightY(step: Int) = floor(height(step)).toInt()
}
