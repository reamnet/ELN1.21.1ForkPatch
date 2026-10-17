package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.gridnode.WireSpanConnection
import mods.eln.gridnode.electricalpole.ElectricalPoleDescriptor
import mods.eln.gridnode.electricalpole.ElectricalPoleElement
import mods.eln.mechanical.GeneratorDescriptor
import mods.eln.mechanical.GeneratorElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.sixnode.electricalcable.WirePhysics
import net.minecraft.nbt.CompoundTag
import kotlin.test.*

class LongLineMainsTest {
    @Test fun olderTransformerPoleOnTheLineDoesNotHoldASpinningGeneratorAtStartupVoltage() {
        val root = RootSystem(.01, 1)
        val descriptors = Eln.transparentNodeItem.subItemList.values
        val generator = GeneratorElement(TransparentNode(), descriptors.filterIsInstance<GeneratorDescriptor>()
            .single { it.name == "Large Generator" })
        generator.shaft._mass = generator.shaftMass; generator.shaft.rads = 202.0
        val pole = ElectricalPoleElement(TransparentNode(), descriptors.filterIsInstance<ElectricalPoleDescriptor>()
            .single { it.name == "Utility Pole w/DC-DC Converter" })
        pole.trafo!!.interSystemProcess.ratio = .25
        val light = StepDownUnitElement(TransparentNode(), descriptors.filterIsInstance<StepDownUnitDescriptor>().single())
        for (e in listOf(generator, pole, light)) {
            e.electricalLoadList.forEach(root::addState); e.electricalComponentList.forEach(root::addComponent)
        }
        root.addProcess(generator.electricalProcess)
        root.addProcess(pole.trafo!!.interSystemProcess)
        root.addProcess(light.unit.controller)
        root.addComponent(Resistor(generator.inputLoad, pole.electricalLoad).apply { resistance = .01 })
        root.addComponent(Resistor(pole.electricalLoad, light.unit.primary).apply { resistance = 1.0 })
        root.addComponent(Resistor(light.unit.secondary, null).apply { resistance = 240.0 })
        repeat(300) { root.step() }
        assertTrue(generator.inputLoad.voltage > 3000.0, "Generator held at ${generator.inputLoad.voltage} V")
        assertEquals(120.0, light.unit.secondary.voltage, .1, light.unit.controller.status)
        assertEquals(-pole.trafo.primaryVoltageSource.power, pole.trafo.secondaryVoltageSource.power, .001)
        val tapLoad = Resistor(pole.trafo.secondaryLoad, null).apply { resistance = 500.0 }
        root.addComponent(tapLoad)
        repeat(100) { root.step() }
        assertTrue(tapLoad.power > 1200.0)
        assertEquals(pole.trafo.primaryVoltageSource.voltage * .25, pole.trafo.secondaryVoltageSource.voltage, 1e-6)
        assertEquals(-pole.trafo.primaryVoltageSource.power, pole.trafo.secondaryVoltageSource.power, .001)
        assertEquals(120.0, light.unit.secondary.voltage, .1)
        generator.shaft.rads = 0.0
        repeat(150) { root.step() }
        assertEquals(0.0, tapLoad.power, 1e-8)
        assertEquals(0.0, light.unit.output.power, 1e-8)
        generator.shaft.rads = 202.0
        repeat(300) { root.step() }
        assertTrue(generator.inputLoad.voltage > 3000.0)
        assertTrue(tapLoad.power > 1200.0)
        assertEquals(120.0, light.unit.secondary.voltage, .1)
    }

    @Test fun kilometreFeederStaysLitAtBothMainsVoltagesWhileSourceAndWireTemperaturesChange() {
        for ((grid, shaft, feedOhms) in listOf(Triple(3200.0, false, 100.0), Triple(12800.0, false, 100.0),
            Triple(51200.0, false, 100.0), Triple(3200.0, true, 100.0),
            Triple(3200.0, false, 760.0), Triple(51200.0, false, 200000.0))) {
            val root = RootSystem(.01, 1)
            val feed = VoltageState(); root.addState(feed)
            val source = VoltageSource("grid", feed, null).apply { voltage = grid }
            val generator = if (shaft) GeneratorElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
                .filterIsInstance<GeneratorDescriptor>().single { it.name == "Large Generator" }).also { g ->
                g.shaft._mass = g.shaftMass; g.shaft.rads = 202.0
                g.electricalLoadList.forEach(root::addState); g.electricalComponentList.forEach(root::addComponent)
                root.addProcess(g.electricalProcess)
                root.addComponent(Resistor(g.inputLoad, feed).apply { resistance = .0001 })
            } else null
            if (generator == null) root.addComponent(source)
            val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
            val units = List(32) { i ->
                StepDownUnitElement(TransparentNode(), descriptor).also { e ->
                    e.unit.read(CompoundTag().apply { putInt("mainsOutputVoltage", if (i % 2 == 0) 120 else 240) })
                    e.electricalLoadList.forEach(root::addState)
                    e.electricalComponentList.forEach(root::addComponent)
                    root.addProcess(e.unit.controller)
                    root.addComponent(Resistor(e.unit.secondary, null).apply {
                        resistance = e.unit.outputVoltage.toDouble().let { it * it / 60.0 }
                    })
                }
            }
            val feeder = Resistor(feed, units[0].unit.primary).apply { resistance = feedOhms }; root.addComponent(feeder)
            val spans = (1 until units.size).map { i ->
                WireSpanConnection(units[i - 1].unit.primary, units[i].unit.primary,
                    WirePhysics.resistance(UtilityCableMaterial.ALUMINUM, 506.7, 40.0)).also(root::addComponent)
            }
            repeat(if (shaft) 150 else 0) { root.step() }
            repeat(250) { step ->
                source.voltage = grid * (1.0 - .00001 * (1.0 + kotlin.math.sin(step.toDouble())))
                generator?.shaft?.rads = 202.0 + .001 * kotlin.math.sin(step.toDouble())
                if (step % 5 == 0) spans.forEach { it.spanOhms *= 1.000001; it.notifyRsChange() }
                root.step()
                units.forEachIndexed { i, e ->
                    assertEquals(e.unit.outputVoltage.toDouble(), e.unit.secondary.voltage, .1,
                        "grid=$grid feeder=$feedOhms step=$step pole=$i ${e.unit.controller.status}")
                    assertTrue(e.unit.output.power > 59.0)
                    assertNotEquals("NON_CONVERGENT", e.unit.controller.status)
                }
            }
            source.voltage = 0.0
            generator?.shaft?.rads = 0.0
            repeat(3) { root.step() }
            val factorizations = root.systems.sumOf { it.fullFactorizations }
            repeat(20) { root.step(); units.forEach { assertEquals(0.0, it.unit.output.power, 1e-8) } }
            assertEquals(factorizations, root.systems.sumOf { it.fullFactorizations }, "Unpowered poles repeatedly rebuilt their matrix")
            source.voltage = grid
            generator?.shaft?.rads = 202.0
            repeat(if (shaft) 150 else 10) { root.step() }
            units.forEach { assertTrue(it.unit.output.power > 59.0, "Reconnect: ${it.unit.controller.status}") }
        }
    }
}
