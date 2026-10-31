package mods.eln.sim.mna

import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import org.junit.Test
import kotlin.test.*

class VoltageDifferenceProbeTest {
    @Test fun changingCommandsAndMatrixRebuildsMatchCheckedTerminalReadsWithoutCommitting() {
        for (size in listOf(12, 96)) {
            val c = ProbeCircuit(size, true, ports = 3)
            val before = c.pins.map { it.state }
            repeat(100) { step ->
                c.sample(step)
                for (pin in c.pins.take(8)) {
                    val expected = c.system.solveChecked(pin) - c.system.solveChecked(c.pins.last())
                    assertEquals(expected, c.system.solveVoltageDifferenceChecked(pin, c.pins.last()), 1e-8)
                }
                if (step == 30) c.wire.resistance = .02
                if (step == 60) c.probes[0].enabled = false
            }
            assertEquals(before, c.pins.map { it.state })
            assertTrue(c.system.differentialVoltageProbeCount > 500)
            c.feed.voltage = Double.NaN
            assertTrue(c.system.solveVoltageDifferenceChecked(c.pins[1], c.pins.last()).isNaN())
            c.sample(101)
            assertTrue(c.system.solveVoltageDifferenceChecked(c.pins[1], c.pins.last()).isFinite())
        }
    }

    @Test fun discardedReferenceCurrentsStillRejectUnbalancedFloatingIslands() {
        val system = SubSystem(null, .01)
        val a = VoltageState(); val b = VoltageState()
        system.addState(a); system.addState(b)
        val feed = VoltageSource("floating", a, b).apply { voltage = 12.0 }
        val leak = CurrentSource("unbalanced", a, null)
        system.addComponent(feed); system.addComponent(Resistor(a, b).setResistance(10.0)); system.addComponent(leak)
        repeat(20) { assertEquals(12.0, system.solveVoltageDifferenceChecked(a, b), 1e-10) }
        leak.current = 1.0
        assertTrue(system.solveVoltageDifferenceChecked(a, b).isNaN())
        leak.current = 0.0
        feed.voltage = 24.0
        assertEquals(24.0, system.solveVoltageDifferenceChecked(a, b), 1e-10)
        val ground = Resistor(b, null).setResistance(1.0)
        system.addComponent(ground)
        leak.current = 1.0
        assertEquals(24.0, system.solveVoltageDifferenceChecked(a, b), 1e-10)
        system.removeComponent(ground)
        assertTrue(system.solveVoltageDifferenceChecked(a, b).isNaN())
    }

    @Test fun terminalDifferenceRetainsPrecisionAcrossLargeCommonModeVoltages() {
        val system = SubSystem(null, .01)
        val a = VoltageState(); val b = VoltageState()
        system.addState(a); system.addState(b)
        system.addComponent(VoltageSource("common", b, null).apply { voltage = 1e14 })
        system.addComponent(VoltageSource("difference", a, b).apply { voltage = 4.2 })
        assertEquals(4.2, system.solveVoltageDifferenceChecked(a, b), 1e-10)
        assertEquals(-4.2, system.solveVoltageDifferenceChecked(b, a), 1e-10)
        assertEquals(0.0, system.solveVoltageDifferenceChecked(a, a), 0.0)
        assertEquals(0.0, a.state, 0.0)
        assertEquals(0.0, b.state, 0.0)
    }

    @Test fun sourceSnapshotsSeeEveryChangedCommandAndUnknownCallbacksRemainFresh() {
        val system = SubSystem(null, .01)
        val a = VoltageState(); val b = VoltageState()
        system.addState(a); system.addState(b)
        val source = CurrentSource("current", a, b).apply { current = 1.0 }
        system.addComponent(source)
        system.addComponent(Resistor(a, b).setResistance(10.0))
        system.addComponent(Resistor(b, null).setResistance(1.0))
        repeat(50) { assertEquals(10.0, system.solveVoltageDifferenceChecked(a, b), 1e-10) }
        assertTrue(system.commandRhsReuses > 40)
        source.current = 2.0
        assertEquals(20.0, system.solveVoltageDifferenceChecked(a, b), 1e-10)
        var external = 0.0
        val custom = object : CurrentSource("custom", a, b) {
            override fun simProcessI(s: SubSystem) { s.addToI(a, external); s.addToI(b, -external) }
        }
        system.addComponent(custom)
        val reused = system.commandRhsReuses
        repeat(20) { i ->
            external = i.toDouble()
            assertEquals((2 + external) * 10, system.solveVoltageDifferenceChecked(a, b), 1e-10)
        }
        assertEquals(reused, system.commandRhsReuses)
    }

    @Test fun aFiniteDifferenceCannotHideOverflowingAbsoluteTerminalVoltages() {
        val system = SubSystem(null, .01)
        val a = VoltageState(); val b = VoltageState()
        system.addState(a); system.addState(b)
        val ground = Resistor(b, null).setResistance(1e300)
        system.addComponent(ground)
        system.addComponent(VoltageSource("difference", a, b).apply { voltage = 4.2 })
        val source = CurrentSource("common", b, null).apply { current = 1e100 }
        system.addComponent(source)
        assertTrue(system.solveVoltageDifferenceChecked(a, b).isNaN())
        ground.resistance = 1.0; source.current = 0.0
        assertEquals(4.2, system.solveVoltageDifferenceChecked(a, b), 1e-10)
    }
}
