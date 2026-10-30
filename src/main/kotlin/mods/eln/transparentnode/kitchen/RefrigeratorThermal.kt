package mods.eln.transparentnode.kitchen

import kotlin.math.exp

/** Air exchange and a finite compressor, integrated exactly so slow ticks cannot overshoot. */
internal object RefrigeratorThermal {
    const val MINIMUM_CELSIUS = 3.5
    fun advance(celsius: Double, ambient: Double, coolingFraction: Double, doorOpen: Boolean, seconds: Double): Double {
        if (!seconds.isFinite() || seconds <= 0.0) return celsius
        val room = ambient.takeIf(Double::isFinite)?.coerceIn(-40.0, 80.0) ?: 20.0
        val cooling = (coolingFraction.takeIf(Double::isFinite) ?: 0.0).coerceIn(0.0, 1.0) * .85
        val exchange = if (doorOpen) .10 else .025 / 16.5
        val equilibrium = room - cooling / exchange
        return (equilibrium + (celsius - equilibrium) * exp(-exchange * seconds))
            .coerceIn(MINIMUM_CELSIUS, maxOf(MINIMUM_CELSIUS, celsius, room))
    }
}
