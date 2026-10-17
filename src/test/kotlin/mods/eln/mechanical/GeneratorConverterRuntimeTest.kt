package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.SafeTransformerProcess
import mods.eln.transparentnode.utilitylight.StepDownUnitDescriptor
import mods.eln.transparentnode.utilitylight.StepDownUnitElement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A maintained 202 rad/s shaft is the mechanical boundary; all electrical parts are production code. */
class GeneratorConverterRuntimeTest {
    private fun generator(root: RootSystem) = GeneratorElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<GeneratorDescriptor>()
            .single { it.name == "Large Generator" }).also { generator ->
        generator.shaft = ShaftNetwork(generator, generator.shaftConnectivity.iterator()).apply { rads = 202.0 }
        generator.electricalLoadList.forEach(root::addState)
        generator.electricalComponentList.forEach(root::addComponent)
        root.addProcess(generator.electricalProcess)
    }

    private fun light(root: RootSystem, feed: mods.eln.sim.mna.state.State, target: Int, watts: Double) =
        StepDownUnitElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<StepDownUnitDescriptor>().single()).also { unit ->
            unit.unit.read(net.minecraft.nbt.CompoundTag().apply { putInt("mainsOutputVoltage", target) })
            unit.electricalLoadList.forEach(root::addState)
            unit.electricalComponentList.forEach(root::addComponent)
            root.addComponent(Resistor(feed, unit.unit.primary).apply { resistance = .0001 })
            root.addComponent(Resistor(unit.unit.secondary, null).apply { resistance = target.toDouble() * target / watts })
            root.addProcess(unit.unit.controller)
        }

    @Test fun largeGeneratorStartsAndContinuouslyPowersAMainsLight() {
        val descriptors = Eln.transparentNodeItem.subItemList.values
        val generator = GeneratorElement(TransparentNode(), descriptors.filterIsInstance<GeneratorDescriptor>()
            .single { it.name == "Large Generator" })
        generator.shaft = ShaftNetwork(generator, generator.shaftConnectivity.iterator()).apply { rads = 202.0 }
        val unit = StepDownUnitElement(TransparentNode(), descriptors.filterIsInstance<StepDownUnitDescriptor>().single())
        val root = RootSystem(.01, 1)
        for (element in listOf(generator, unit)) {
            element.electricalLoadList.forEach(root::addState)
            element.electricalComponentList.forEach(root::addComponent)
        }
        val cable = Resistor(generator.inputLoad, unit.unit.primary).apply { resistance = .0001 }
        val light = Resistor(unit.unit.secondary, null).apply { resistance = 240.0 }
        root.addComponent(cable); root.addComponent(light)
        root.addProcess(generator.electricalProcess); root.addProcess(unit.unit.controller)
        repeat(600) { step ->
            root.step()
            if (step >= 150) {
                assertTrue(generator.inputLoad.voltage > 3000.0,
                    "step=$step generator=${generator.inputLoad.voltage} converter=${unit.unit.controller.status}")
                assertEquals(120.0, unit.unit.secondary.voltage, .1, "step=$step ${unit.unit.controller.status}")
                assertTrue(light.power > 59.0)
                assertEquals(-unit.unit.input.power * .95, unit.unit.output.power, .001)
            }
        }
    }

    @Test fun aSharedGridWithAStepUpTransformerAndSeveralMainsLoadsStaysOnAndRestarts() {
        for (voltageControl in listOf(false, true)) {
            val root = RootSystem(.01, 1)
            val generator = generator(root)
            val primary = VoltageState(); val secondary = VoltageState(); val grid = VoltageState()
            listOf(primary, secondary, grid).forEach(root::addState)
            val input = SwitchableVoltageSource("transformer input").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("transformer output").apply { connectTo(secondary, null) }
            listOf(input, output,
                Resistor(generator.inputLoad, primary).apply { resistance = .01 },
                Resistor(secondary, grid).apply { resistance = .01 }).forEach(root::addComponent)
            val transformer = SafeTransformerProcess(primary, secondary, input, output) { true }.apply {
                ratio = 4.0
                if (voltageControl) voltageTarget = 12800.0
            }
            root.addProcess(transformer)
            val loads = listOf(light(root, grid, 120, 60.0), light(root, grid, 240, 500.0),
                light(root, grid, 120, 150.0))
            repeat(500) { step ->
                root.step()
                if (step >= 150) {
                    assertTrue(generator.inputLoad.voltage > 3000.0, "step=$step transformer=${transformer.status}")
                    loads.forEach { assertEquals(it.unit.outputVoltage.toDouble(), it.unit.secondary.voltage, .1,
                        "step=$step ${it.unit.controller.status}") }
                    assertTrue(generator.electricalPowerSource.power > 740.0)
                }
            }
            generator.shaft.rads = 0.0
            repeat(200) { root.step() }
            loads.forEach { assertEquals(0.0, it.unit.output.power, 1e-8) }
            generator.shaft.rads = 202.0
            repeat(300) { root.step() }
            loads.forEach { assertEquals(it.unit.outputVoltage.toDouble(), it.unit.secondary.voltage, .1,
                "After restarting: ${it.unit.controller.status}") }
        }
    }

    @Test fun parallelGeneratorsShareLoadWithoutGettingStuckAtTheConvertersInputVoltage() {
        for (reverseOrder in listOf(false, true)) {
            val root = RootSystem(.01, 1)
            val a = generator(root); val b = generator(root)
            root.addComponent(Resistor(a.inputLoad, b.inputLoad).apply { resistance = .0001 })
            val loads = (0..3).map { light(root, if (reverseOrder) b.inputLoad else a.inputLoad,
                if (it % 2 == 0) 120 else 240, 500.0) }
            repeat(400) { root.step() }
            loads.forEach { assertEquals(it.unit.outputVoltage.toDouble(), it.unit.secondary.voltage, .1) }
            assertTrue(a.electricalPowerSource.power > 900.0)
            assertTrue(b.electricalPowerSource.power > 900.0)
            assertEquals(a.electricalPowerSource.power, b.electricalPowerSource.power, 2.0)
        }
    }

    @Test fun currentAndMechanicalEnergyLimitsStillApplyAndRampsAdvanceOnlyOncePerPhysicalStep() {
        val root = RootSystem(.01, 5)
        val generator = generator(root)
        val short = Resistor(generator.inputLoad, null).apply { resistance = .001 }
        root.addComponent(short)
        root.step()
        assertEquals(generator.desc.nominalU / generator.desc.regulatorRampTime * .01,
            generator.electricalProcess.voltageTarget, 1e-9)
        repeat(100) {
            root.step()
            assertTrue(generator.electricalPowerSource.current <= generator.desc.regulatorCurrentLimit + .0001)
            assertTrue(generator.electricalPowerSource.power <= generator.shaft.energy / .01 * .95 + .001)
        }
        // A stiff powered line can still backdrive a stopped generator, within its reverse limit.
        val battery = VoltageState(); root.addState(battery)
        root.removeComponent(short); short.breakConnection()
        root.addComponent(VoltageSource("powered grid", battery, null).apply { voltage = 3200.0 })
        root.addComponent(Resistor(battery, generator.inputLoad).apply { resistance = .1 })
        generator.shaft.rads = 0.0
        repeat(150) { root.step() }
        assertTrue(generator.electricalPowerSource.power < 0.0)
        assertEquals(-generator.desc.regulatorCurrentLimit.toDouble(), generator.electricalPowerSource.current, .0001)
    }
}
