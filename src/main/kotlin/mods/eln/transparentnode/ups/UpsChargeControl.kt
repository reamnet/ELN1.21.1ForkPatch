package mods.eln.transparentnode.ups

import kotlin.math.max

/** Soft-started, positive input conductance. A weak source reduces charging before battery output. */
internal class UpsChargeControl {
    private var reference = 0.0
    private var allowance = 0.0

    /** Advance only once per physical step; trial solves never ramp the charger. */
    fun advance(seconds: Double, openVolts: Double, requestedWatts: Double, drawing: Boolean) {
        if (!openVolts.isFinite() || openVolts !in 12.0..UpsPowerUnit.MAX_INPUT_VOLTS) {
            reference = 0.0; allowance = 0.0; return
        }
        reference = max(reference, openVolts)
        // The open-port probe separates source droop from ordinary cable loss.
        // Comparing the loaded terminal instead would unnecessarily hold a 120 V
        // charger below the load's demand even when the generator has spare power.
        allowance = if (drawing && openVolts < reference * .98) allowance * .8
            else allowance + 5000.0 * seconds
        allowance = allowance.coerceIn(0.0, requestedWatts.coerceAtLeast(0.0))
    }

    fun outputBudget(inputVolts: Double): Double {
        if (reference <= 0 || !inputVolts.isFinite() || inputVolts <= 0) return 0.0
        val ratio = (inputVolts / reference).coerceIn(0.0, 1.0)
        // P = G V² gives a consuming input with positive incremental impedance.
        // The ordinary converter acceptance checks still enforce current and energy limits.
        return allowance * ratio * ratio * UpsPowerUnit.EFFICIENCY
    }
}
