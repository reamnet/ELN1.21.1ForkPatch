package mods.eln.sim.mna

import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.state.VoltageState
import org.apache.commons.numbers.core.DD
import org.junit.Test
import kotlin.test.*

class StampedMatrixCacheTest {
    @Test fun exactCoefficientsAndStateIdentitiesAreRequired() {
        val cache = StampedMatrixCache<String>()
        val states = List(2) { VoltageState() }
        val matrix = arrayOf(arrayOf(DD.ONE, DD.ZERO), arrayOf(DD.ZERO, DD.ONE))
        cache.put(matrix, states, "solver")
        assertEquals("solver", cache.find(Array(2) { matrix[it].clone() }, states))
        assertNull(cache.find(matrix, states.reversed()))
        assertNull(cache.find(matrix, List(2) { VoltageState() }))
        val different = Array(2) { matrix[it].clone() }
        different[0][0] = DD.ONE.add(1e-30)
        assertEquals(matrix[0][0].toDouble(), different[0][0].toDouble())
        assertNull(cache.find(different, states), "The DD low part must participate in matching")
    }

    @Test fun leastRecentlyUsedEntriesRespectTheCellBudget() {
        val cache = StampedMatrixCache<String>(8, 4)
        val states = List(2) { VoltageState() }
        fun matrix(value: Double) = Array(2) { row -> Array(2) { column ->
            if (row == column) DD.of(value) else DD.ZERO
        } }
        val a = matrix(1.0); val b = matrix(2.0); val c = matrix(3.0)
        cache.put(a, states, "a"); cache.put(b, states, "b")
        assertEquals("a", cache.find(a, states))
        cache.put(c, states, "c")
        assertNull(cache.find(b, states))
        assertEquals("a", cache.find(a, states))
        assertEquals("c", cache.find(c, states))
        val oversized = Array(3) { Array(3) { DD.ONE } }
        val oversizedStates = List(3) { VoltageState() }
        cache.put(oversized, oversizedStates, "too big")
        assertNull(cache.find(oversized, oversizedStates))
        assertEquals("a", cache.find(a, states))
        cache.retainCurrent(a)
        assertNull(cache.find(c, states))
        assertEquals("a", cache.find(a, states))
    }

    private fun feeder(nodes: Int, reuse: Boolean): MnaRebuildTest.Feeder {
        val key = "eln.disableStampedMatrixReuse"
        val previous = System.getProperty(key)
        return try {
            System.setProperty(key, (!reuse).toString())
            MnaRebuildTest.Feeder(nodes, true)
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    @Test fun revisitedSourceMasksRetainFreshRhsAndChangedResistances() {
        for (nodes in listOf(24, 96)) {
            val baseline = feeder(nodes, false); val cached = feeder(nodes, true)
            val initial = cached.pins.map { it.state }
            repeat(40) { step ->
                baseline.probe.enabled = step % 2 == 0
                cached.probe.enabled = baseline.probe.enabled
                baseline.source.voltage = 14_000.0 + step * .2
                cached.source.voltage = baseline.source.voltage
                if (step == 20) {
                    baseline.wires[0].resistance = .123
                    cached.wires[0].resistance = .123
                }
                val a = baseline.system.sourceThevenin(baseline.probe)
                val b = cached.system.sourceThevenin(cached.probe)
                assertEquals(a.voltage, b.voltage, 1e-8)
                assertEquals(a.resistance, b.resistance, 1e-10)
            }
            assertTrue(cached.system.reusedMatrixSolvers >= 30)
            assertTrue(cached.system.fullFactorizations < baseline.system.fullFactorizations)
            assertEquals(initial, cached.pins.map { it.state }, "Probes must not commit physical state")
        }
    }

    @Test fun cachedGaugeStillRejectsUnbalancedFloatingIslandCurrent() {
        val circuit = feeder(96, true)
        val leak = CurrentSource("unbalanced", circuit.pins[3], null)
        circuit.system.addComponent(leak)
        repeat(12) { step ->
            circuit.probe.enabled = step % 2 == 0
            leak.current = if (step % 3 == 0) .1 else 0.0
            val response = circuit.system.sourceThevenin(circuit.probe)
            if (leak.current == 0.0) assertTrue(response.voltage.isFinite())
            else assertTrue(response.voltage.isNaN(), "A cached gauge accepted an unbalanced RHS")
        }
        assertTrue(circuit.system.reusedMatrixSolvers > 5)
    }
}
