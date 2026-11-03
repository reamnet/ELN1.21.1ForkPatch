package mods.eln.sim.mna

import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Resistor
import org.junit.Test
import kotlin.test.*

class ProbeDataReuseTest {
    private fun circuit(size: Int, reuse: Boolean, asymmetric: Boolean = false): ProbeCircuit {
        val key = "eln.disableProbeDataReuse"
        val saved = System.getProperty(key)
        try {
            System.setProperty(key, (!reuse).toString())
            return ProbeCircuit(size, true, asymmetric, ports = 3, gaugeRows = true)
        } finally {
            if (saved == null) System.clearProperty(key) else System.setProperty(key, saved)
        }
    }

    private fun compare(expected: Double, actual: Double, tolerance: Double = 1e-8) {
        if (expected.isFinite()) assertEquals(expected, actual, tolerance)
        else assertEquals(expected, actual)
    }

    private fun compareReads(full: ProbeCircuit, cached: ProbeCircuit) {
        repeat(4) {
            full.probes.zip(cached.probes).forEach { (a, b) ->
                val expected = full.system.sourceThevenin(a)
                val actual = cached.system.sourceThevenin(b)
                compare(expected.voltage, actual.voltage)
                compare(expected.resistance, actual.resistance, 1e-11)
                // A caller may modify its own result without poisoning the cached port.
                actual.voltage = -123.0
                actual.resistance = -456.0
            }
            full.pins.take(5).zip(cached.pins.take(5)).forEach { (a, b) ->
                compare(full.system.solveChecked(a), cached.system.solveChecked(b))
                compare(full.system.solveVoltageDifferenceChecked(a, full.pins.last()),
                    cached.system.solveVoltageDifferenceChecked(b, cached.pins.last()))
            }
        }
    }

    @Test fun reuseMatchesFreshProbesAcrossCommandsMasksAndMatrixRebuilds() {
        for (size in listOf(12, 80, 160)) for (asymmetric in listOf(false, true)) {
            val full = circuit(size, false, asymmetric)
            val cached = circuit(size, true, asymmetric)
            val before = cached.pins.map { it.state }
            repeat(35) { tick ->
                full.sample(tick); cached.sample(tick)
                if (tick == 8) { full.wire.resistance = .02; cached.wire.resistance = .02 }
                if (tick == 16) { full.probes[0].enabled = false; cached.probes[0].enabled = false }
                if (tick == 24) { full.probes[0].enabled = true; cached.probes[0].enabled = true }
                compareReads(full, cached)
            }
            assertEquals(before, cached.pins.map { it.state })
            assertTrue(cached.system.portResultReuses > 100)
            assertTrue(cached.system.differentialResultReuses > 100)
            assertTrue(cached.system.probeValidationReuses > 100)
            full.feed.voltage = Double.NaN; cached.feed.voltage = Double.NaN
            compareReads(full, cached)
            full.sample(36); cached.sample(36)
            compareReads(full, cached)
            // A forced restamp without a source setter also discards all result caches.
            full.system.generateMatrix(); cached.system.generateMatrix()
            compareReads(full, cached)
        }
    }

    @Test fun floatingFaultsAndTopologyChangesInvalidateEveryProbePath() {
        val full = circuit(80, false); val cached = circuit(80, true)
        val leaks = listOf(full, cached).map { c -> CurrentSource("fault", c.pins[0], null).also(c.system::addComponent) }
        compareReads(full, cached)
        leaks.forEach { it.current = 1.0 }
        compareReads(full, cached)
        assertTrue(cached.system.sourceThevenin(cached.probes[0]).voltage.isNaN())
        assertTrue(cached.system.solveChecked(cached.pins[1]).isNaN())
        assertTrue(cached.system.solveVoltageDifferenceChecked(cached.pins[1], cached.pins.last()).isNaN())
        val grounds = listOf(full, cached).map { c -> Resistor(c.pins.last(), null).setResistance(1.0).also(c.system::addComponent) }
        compareReads(full, cached)
        assertTrue(cached.system.solveChecked(cached.pins[1]).isFinite())
        listOf(full, cached).zip(grounds).forEach { (c, ground) -> c.system.removeComponent(ground) }
        compareReads(full, cached)
        leaks.forEach { it.current = 0.0 }
        compareReads(full, cached)
        listOf(full, cached).zip(leaks).forEach { (c, leak) -> c.system.removeComponent(leak) }
        compareReads(full, cached)
    }

    @Test fun untrackedCallbackChangesNeverReuseAResultOrValidation() {
        val full = circuit(80, false); val cached = circuit(80, true)
        var external = 0.0
        listOf(full, cached).forEach { c ->
            c.system.addComponent(object : CurrentSource("external", c.pins[1], c.pins.last()) {
                override fun simProcessI(s: SubSystem) {
                    s.addToI(c.pins[1], external); s.addToI(c.pins.last(), -external)
                }
            })
        }
        val hits = cached.system.portResultReuses + cached.system.differentialResultReuses + cached.system.probeValidationReuses
        repeat(40) { external = it * 2.5; compareReads(full, cached) }
        external = Double.NaN; compareReads(full, cached)
        external = 10.0; compareReads(full, cached)
        assertEquals(hits, cached.system.portResultReuses + cached.system.differentialResultReuses + cached.system.probeValidationReuses)
    }

    @Test fun directLegacyPortsMatchFiniteDifferencesAndPreserveFinalCommands() {
        for (asymmetric in listOf(false, true)) {
            val full = circuit(80, false, asymmetric); val cached = circuit(80, true, asymmetric)
            repeat(80) { tick ->
                full.sample(tick); cached.sample(tick)
                if (tick == 30) { full.wire.resistance = .02; cached.wire.resistance = .02 }
                full.probes.zip(cached.probes).forEach { (a, b) ->
                    val expected = full.system.getTh(full.pins[1], a)
                    val actual = cached.system.getTh(cached.pins[1], b)
                    compare(expected.voltage, actual.voltage, 1e-7)
                    compare(expected.resistance, actual.resistance, 1e-9)
                    assertEquals(cached.pins[1].state, b.voltage)
                    assertEquals(a.voltage, b.voltage)
                }
            }
            assertTrue(cached.system.directLegacyProbes > 200)
            assertEquals(0L, full.system.directLegacyProbes)
        }
    }
}
