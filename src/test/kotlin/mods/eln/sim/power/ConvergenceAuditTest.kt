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

/** Physical source/winding/load circuits across the supported voltage and idle-load ranges. */
class ConvergenceAuditTest {
    internal class Circuit(val feedVolts: Double, val inputOhms: Double, val outputVolts: Double, loadOhms: Double) {
        val root = RootSystem(.01, 1)
        val feed = VoltageState()
        val primary = VoltageState()
        val secondary = VoltageState()
        val supply = VoltageSource("feed", feed, null).apply { voltage = feedVolts }
        val input = SwitchableVoltageSource("input").apply { connectTo(primary, null) }
        val output = SwitchableVoltageSource("output").apply { connectTo(secondary, null) }
        val load = Resistor(secondary, null).apply { resistance = loadOhms }
        val controller = RegulatedPowerProcess(primary, null, secondary, null, input, output,
            { true }, { ConverterLimits(1.0, 51200.0, 51200.0, 200.0, 200.0, 20000.0, .97, 1.0 / 65536, 65536.0) },
            { outputVolts })
        init {
            listOf(feed, primary, secondary).forEach(root::addState)
            listOf(supply, input, output, load,
                Resistor(feed, primary).apply { resistance = inputOhms }).forEach(root::addComponent)
            root.addProcess(controller)
        }
        fun diagnostic() = "$feedVolts V -> $outputVolts V; input $inputOhms ohm, load ${load.resistance} ohm; " +
            "${controller.status}, input ${input.power} W, output ${output.power} W"
    }

    @Test fun idleRegulatedConvertersDoNotTripAcrossVoltageTiersAndCableImpedances() {
        val failures = mutableListOf<String>()
        for (feed in listOf(120.0, 240.0, 3200.0, 12470.0, 33000.0, 51200.0)) {
            for (wire in listOf(.0001, .002, .01, 1.0)) {
                for (target in listOf(12.0, 120.0, 240.0, 850.0, 3200.0)) {
                    for (load in listOf(1e6, 1e9, 1e12, 1e16, 1e19)) {
                        val c = Circuit(feed, wire, target, load)
                        repeat(3) { c.root.step() }
                        if (!c.output.enabled || c.controller.status == "NON_CONVERGENT" ||
                            kotlin.math.abs(c.secondary.voltage - target) > 1e-6) failures += c.diagnostic()
                        else {
                            val quantum = feed * (Math.ulp(feed) + Math.ulp(c.input.voltage)) / wire
                            assertEquals(c.output.power, -c.input.power * .97, quantum + 1e-7, c.diagnostic())
                            if (load < 1e18) assertEquals(target * target / load, c.output.power, 1e-8, c.diagnostic())
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} rejected idle circuits:\n${failures.take(20).joinToString("\n")}")
    }

    @Test fun changesFromIdleToLoadedAndBackDoNotStartRetryTimers() {
        for (feed in listOf(3200.0, 12470.0, 33000.0)) {
            val c = Circuit(feed, .002, 240.0, 1e9)
            for (watts in listOf(.0000576, 100.0, 5000.0, .0000576, 100.0)) {
                c.load.resistance = 240.0 * 240 / watts
                repeat(5) { c.root.step() }
                assertTrue(c.output.enabled, c.diagnostic())
                assertEquals(240.0, c.secondary.voltage, 1e-6, c.diagnostic())
                assertEquals(watts, c.load.power, 1e-6, c.diagnostic())
            }
        }
    }

    @Test fun voltageResolutionDoesNotExcuseLargerPowerErrors() {
        val c = Circuit(12470.0, .002, 240.0, 1e9)
        repeat(3) { c.root.step() }
        assertTrue(c.output.enabled, c.diagnostic())
        // Smaller than commandMatches' relative voltage tolerance, but far beyond
        // the port's rounding uncertainty and the permitted power imbalance.
        repeat(128) { c.input.voltage = Math.nextDown(c.input.voltage) }
        c.primary.subSystem.stepCalc()
        c.secondary.subSystem.stepCalc()
        assertFalse(c.controller.acceptsCandidate(), "Accepted a real excess input power error")
    }

    @Test fun aBalancedCandidateMustStillRespectTheConvertersWattageRating() {
        val c = Circuit(12470.0, .002, 240.0, 1e6)
        repeat(3) { c.root.step() }
        assertTrue(c.output.enabled)
        // Other coupled devices can change their source/load during algebraic trials.
        // Keep this converter's commands unchanged, with legal currents and balanced
        // input/output power, but exceed its 20 kW electronics rating.
        val watts = 30000.0
        c.load.resistance = 240.0 * 240 / watts
        c.supply.voltage = c.input.voltage + watts / .97 / c.input.voltage * c.inputOhms
        c.primary.subSystem.stepCalc(); c.secondary.subSystem.stepCalc()
        assertTrue(balancedPower(-pendingSourcePower(c.input), pendingSourcePower(c.output), .97))
        assertTrue(c.input.subSystem.pendingValue(c.input.currentState) in 0.0..200.0)
        assertTrue(-c.output.subSystem.pendingValue(c.output.currentState) in 0.0..200.0)
        assertFalse(c.controller.acceptsCandidate(), "Accepted 30 kW on a 20 kW converter")
    }
}
