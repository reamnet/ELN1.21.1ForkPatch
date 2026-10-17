package mods.eln.sim.mna

import mods.eln.sim.mna.state.VoltageState
import kotlin.test.*

class VoltageGaugeTest {
    @Test fun separateFloatingIslandsRetainTheirOwnDiscardedCurrentsAcrossProbes() {
        val matrix = arrayOf(
            doubleArrayOf(1.0, -1.0, 0.0, 0.0),
            doubleArrayOf(-1.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, -1.0),
            doubleArrayOf(0.0, 0.0, -1.0, 1.0)
        )
        val gauge = VoltageGauge(matrix, List(4) { VoltageState() })
        assertTrue(gauge.active)
        val physical = doubleArrayOf(1.0, -1.0, 2.0, -2.0)
        val rhs = physical.copyOf()
        gauge.applyRhs(rhs)
        assertContentEquals(doubleArrayOf(0.0, -1.0, 0.0, -2.0), rhs)
        assertTrue(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        gauge.applyRhs(doubleArrayOf(3.0, -3.0, 5.0, -5.0))
        assertFalse(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        assertTrue(gauge.valid(doubleArrayOf(0.0, -3.0, 0.0, -5.0)))
        gauge.applyRhs(physical.copyOf())
        assertTrue(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        assertFalse(gauge.valid(doubleArrayOf(0.0, Double.NaN, 0.0, -2.0)))
    }
}
