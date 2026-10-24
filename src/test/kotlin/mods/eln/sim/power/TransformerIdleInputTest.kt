package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransformerIdleInputTest {
    @Test fun targetControlDoesNotBackfeedAnIdlePrimaryThroughAnObsoleteTrialRatio() {
        for (targetControl in listOf(true, false)) {
            val root = RootSystem(.01, 1)
            val primary = VoltageState(); val secondary = VoltageState(); val grid = VoltageState()
            listOf(primary, secondary, grid).forEach(root::addState)
            val input = SwitchableVoltageSource("input").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("output").apply { connectTo(secondary, null) }
            listOf(input, output, VoltageSource("grid", grid, null).apply { voltage = 3232.0 },
                Resistor(secondary, grid).apply { resistance = .01 },
                Resistor(primary, null).pullDown()).forEach(root::addComponent)
            val process = SafeTransformerProcess(primary, secondary, input, output) { true }.apply {
                ratio = .2
                if (targetControl) voltageTarget = 3300.0
            }
            root.addProcess(process)
            repeat(10) { root.step() }
            if (targetControl) {
                assertEquals("NO_INPUT", process.status)
                assertFalse(input.enabled); assertFalse(output.enabled)
                assertEquals(0.0, primary.voltage, 1e-8)
            } else {
                assertTrue(primary.voltage > 16_000, "Fixed-ratio reverse induction must remain available")
                assertTrue(input.enabled && output.enabled)
                assertEquals(primary.voltage * .2, secondary.voltage, 1e-6)
                assertTrue(input.power > 0.0 && output.power < 0.0)
                assertTrue(balancedPower(-input.power, output.power))
            }
        }
    }
}
