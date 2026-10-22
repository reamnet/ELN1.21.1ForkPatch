package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.SafeTransformerProcess
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorStepUpRestartTest {
    @Test fun voltageControlledStepUpReachesTheNormalBranchAfterColdStart() {
        for (watts in listOf(5_000.0, 20_000.0, 60_000.0)) {
            for (startup in listOf("spinning", "delayed", "accelerating")) {
                for (converterFirst in listOf(false, true)) checkStartup(watts, startup, converterFirst)
            }
        }
    }

    private fun checkStartup(watts: Double, startup: String, converterFirst: Boolean) {
        val root = RootSystem(.01, 1)
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<GeneratorDescriptor>()
            .single { it.name == "Large Generator" }
        val generator = GeneratorElement(TransparentNode(), descriptor)
        generator.shaft = ShaftNetwork(generator, generator.shaftConnectivity.iterator()).apply {
            rads = if (startup == "spinning") 200.0 else 0.0
        }
        generator.electricalLoadList.forEach(root::addState)
        generator.electricalComponentList.forEach(root::addComponent)
        val a = VoltageState(); val b = VoltageState()
        root.addState(a); root.addState(b)
        val input = SwitchableVoltageSource("step-up input").apply { connectTo(a, null) }
        val output = SwitchableVoltageSource("step-up output").apply { connectTo(b, null) }
        root.addComponent(input); root.addComponent(output)
        root.addComponent(Resistor(generator.inputLoad, a).apply { resistance = .001 })
        val load = Resistor(b, null).apply { resistance = 12_500.0 * 12_500 / watts }
        root.addComponent(load)
        val transformer = SafeTransformerProcess(a, b, input, output) { true }.apply {
            ratio = 12_500.0 / 3200
            voltageTarget = 12_500.0
        }
        if (converterFirst) root.addProcess(transformer)
        root.addProcess(generator.electricalProcess)
        if (!converterFirst) root.addProcess(transformer)
        if (startup == "delayed") repeat(100) { root.step() }
        if (startup == "accelerating") repeat(400) { step ->
            generator.shaft.rads = (step + 1) * .5
            root.step()
        }
        generator.shaft.rads = 200.0
        repeat(400) {
            root.step()
            assertTrue(abs(generator.electricalPowerSource.current) <= descriptor.regulatorCurrentLimit + .001)
            assertTrue(generator.electricalPowerSource.power <=
                generator.shaft.energy / .01 * descriptor.generationEfficiency + .001)
        }
        assertTrue(generator.inputLoad.voltage > 3000,
            "load=$watts startup=$startup converterFirst=$converterFirst " +
                "input=${generator.inputLoad.voltage} output=${b.voltage} status=${transformer.status}")
        assertEquals(12_500.0, b.voltage, 1.0)
        assertEquals(watts, load.power, watts * .001)
        assertTrue(generator.electricalProcess.startupVoltageTargets().isEmpty(),
            "A settled generator must stop requesting startup guesses")
    }
}
