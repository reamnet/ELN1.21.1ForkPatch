package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.ISubSystemProcessFlush
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.*

class ConverterOversamplingTest {
    private class Counted(private val process: ConservativePowerProcess) : ConservativePowerProcess by process {
        var evaluations = 0
        var beginnings = 0
        override fun rootSystemPreStepProcess() { evaluations++; process.rootSystemPreStepProcess() }
        override fun beginStep(seconds: Double) { beginnings++; process.beginStep(seconds) }
    }

    @Test fun defaultLegacySamplingDoesNotMultiplyExactConverterWorkOrPhysicalCommits() {
        fun scenario(samples: Int): Pair<Int, Double> {
            val root = RootSystem(.05, samples)
            val feed = VoltageState(); val primary = VoltageState(); val secondary = VoltageState()
            listOf(feed, primary, secondary).forEach(root::addState)
            val grid = VoltageSource("grid", feed, null).apply { voltage = 3200.0 }
            root.addComponent(grid)
            root.addComponent(Resistor(feed, primary).apply { resistance = 1.0 })
            val load = Resistor(secondary, null).apply { resistance = 240.0 }
            root.addComponent(load)
            val input = SwitchableVoltageSource("in").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("out").apply { connectTo(secondary, null) }
            root.addComponent(input); root.addComponent(output)
            val limits = ConverterLimits(1.0, 51200.0, 240.0, 100.0, 100.0, 10000.0, .95, 1.0 / 65536.0, 1.0)
            val converter = Counted(RegulatedPowerProcess(primary, null, secondary, null, input, output,
                { true }, { limits }, { 120.0 }))
            root.addProcess(converter)
            var commits = 0
            root.addProcess(ISubSystemProcessFlush { commits++ })
            repeat(10) { root.step() }
            converter.evaluations = 0
            repeat(20) { step ->
                grid.voltage = 3200.0 + step
                root.step()
                assertEquals(120.0, secondary.voltage, 1e-7)
                assertEquals(-input.power * .95, output.power, 1e-6)
            }
            assertEquals(30, commits)
            assertEquals(30, converter.beginnings)
            assertTrue(converter.evaluations <= 200, "Repeated ${converter.evaluations} controller sweeps")
            return converter.evaluations to load.power
        }
        assertEquals(scenario(1), scenario(50), "The legacy setting changed an exact converter network")
    }

    @Test fun legacyTransformerBeforeOrAfterConverterStillBalancesAfterLoadAndSupplyChanges() {
        for (legacyFirst in listOf(true, false)) {
            val root = RootSystem(.05, 50)
            val feed = VoltageState(); val tap = VoltageState(); val legacyPin = VoltageState()
            val primary = VoltageState(); val secondary = VoltageState()
            listOf(feed, tap, legacyPin, primary, secondary).forEach(root::addState)
            val grid = VoltageSource("grid", feed, null).apply { voltage = 3200.0 }
            root.addComponent(grid)
            root.addComponent(Resistor(feed, tap).apply { resistance = 1.0 })
            val legacyInput = VoltageSource("legacy in", tap, null)
            val legacyOutput = VoltageSource("legacy out", legacyPin, null)
            root.addComponent(legacyInput); root.addComponent(legacyOutput)
            val legacy = TransformerInterSystemProcess(tap, legacyPin, legacyInput, legacyOutput).apply { ratio = .5 }
            root.addComponent(Resistor(legacyPin, primary).apply { resistance = 1.0 })
            val load = Resistor(secondary, null).apply { resistance = 240.0 }
            root.addComponent(load)
            val input = SwitchableVoltageSource("in").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("out").apply { connectTo(secondary, null) }
            root.addComponent(input); root.addComponent(output)
            val converter = RegulatedPowerProcess(primary, null, secondary, null, input, output,
                { true }, { ConverterLimits(1.0, 51200.0, 240.0, 100.0, 100.0, 10000.0, .95, 1.0 / 65536.0, 1.0) }, { 120.0 })
            if (legacyFirst) { root.addProcess(legacy); root.addProcess(converter) }
            else { root.addProcess(converter); root.addProcess(legacy) }
            repeat(30) { step ->
                if (step == 10) { grid.voltage = 6400.0; load.resistance = 24.0 }
                if (step == 20) { grid.voltage = 3200.0; load.resistance = 240.0 }
                root.step()
                assertEquals(120.0, secondary.voltage, 1e-6, "step=$step ${converter.status}")
                // The established acceptance budget is one part per million plus 0.1 µW.
                assertEquals(-input.power * .95, output.power, 1e-7 + output.power * 1e-6)
                assertEquals(-legacyInput.power, legacyOutput.power, 1e-5)
            }
        }
    }
}
