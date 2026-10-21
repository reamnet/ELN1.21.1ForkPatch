package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.State
import mods.eln.sixnode.diode.DiodeDescriptor
import mods.eln.sixnode.diode.DiodeElement
import mods.eln.sixnode.electricalrelay.ElectricalRelayDescriptor
import mods.eln.sixnode.electricalrelay.ElectricalRelayElement
import mods.eln.sixnode.electricalswitch.ElectricalSwitchDescriptor
import mods.eln.sixnode.electricalswitch.ElectricalSwitchElement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real registered contacts attached to a Large Generator maintained at 200 rad/s. */
class GeneratorSwitchRuntimeTest {
    private class Circuit(warm: Boolean = true) {
        val root = RootSystem(.01, 1)
        val generator = GeneratorElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<GeneratorDescriptor>().single { it.name == "Large Generator" })
        val nominal = generator.desc.RtoU.getValue(200.0)
        init {
            generator.shaft = ShaftNetwork(generator, generator.shaftConnectivity.iterator()).apply { rads = 200.0 }
            generator.electricalLoadList.forEach(root::addState)
            generator.electricalComponentList.forEach(root::addComponent)
            root.addProcess(generator.electricalProcess)
            if (warm) {
                repeat(100) { root.step() }
                powered("before attachment")
            }
        }
        fun resistor(a: State, b: State?, ohms: Double) = Resistor(a, b).apply {
            resistance = ohms; root.addComponent(this)
        }
        fun attach(element: SixNodeElement, input: State) {
            element.initialize()
            element.electricalLoadList.forEach(root::addState)
            element.electricalComponentList.forEach(root::addComponent)
            resistor(generator.inputLoad, input, .01)
        }
        fun powered(label: String) {
            assertTrue(generator.electricalPowerSource.enabled, "$label: generator tripped")
            assertTrue(generator.inputLoad.voltage > nominal * .95,
                "$label: voltage=${generator.inputLoad.voltage}, nominal=$nominal")
        }
        fun step(label: String, after: () -> Unit = {}) {
            repeat(120) { tick -> root.step(); after(); powered("$label tick=$tick") }
        }
    }

    @Test fun allSwitchesKeepTheGeneratorPoweredWhenAttachedOpenedAndClosed() {
        val descriptors = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalSwitchDescriptor>()
        assertTrue(descriptors.isNotEmpty())
        for (descriptor in descriptors) {
            val c = Circuit()
            val contact = ElectricalSwitchElement(SixNode(), Direction.YN, descriptor)
            c.attach(contact, contact.aLoad)
            c.step("${descriptor.name} attached open")
            contact.setSwitchState(true)
            c.step("${descriptor.name} closed without load")
            val load = c.resistor(contact.bLoad, null, c.nominal * c.nominal / 100.0)
            c.step("${descriptor.name} closed with load")
            assertTrue(load.power > 90.0)
            contact.setSwitchState(false)
            c.step("${descriptor.name} reopened")
            assertTrue(load.power < .001)
        }
    }

    @Test fun allRelaysKeepTheGeneratorPoweredWhenAttachedOpenedAndClosed() {
        val descriptors = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalRelayDescriptor>()
        assertTrue(descriptors.isNotEmpty())
        for (descriptor in descriptors) {
            val c = Circuit()
            val contact = ElectricalRelayElement(SixNode(), Direction.YN, descriptor)
            c.attach(contact, contact.aLoad)
            c.step("${descriptor.name} attached open")
            // Exercise contacts directly; gate behavior has its own process and is not the electrical load.
            contact.switchResistor.setState(true)
            c.step("${descriptor.name} closed without load")
            val load = c.resistor(contact.bLoad, null, c.nominal * c.nominal / 100.0)
            c.step("${descriptor.name} closed with load")
            assertTrue(load.power > 90.0)
            contact.switchResistor.setState(false)
            c.step("${descriptor.name} reopened")
            assertTrue(load.power < .001)
        }
    }

    @Test fun allDiodesKeepTheGeneratorPoweredInBothDirectionsAndWithAnOpenOutput() {
        val descriptors = Eln.sixNodeItem.subItemList.values.filterIsInstance<DiodeDescriptor>()
        assertTrue(descriptors.isNotEmpty())
        for (descriptor in descriptors) for (reverse in listOf(false, true)) {
            val c = Circuit()
            val diode = DiodeElement(SixNode(), Direction.YN, descriptor)
            c.attach(diode, if (reverse) diode.catodeLoad else diode.anodeLoad)
            c.step("${descriptor.name} open output reverse=$reverse") { diode.diodeProcess.process(.01) }
            val load = c.resistor(if (reverse) diode.anodeLoad else diode.catodeLoad, null,
                c.nominal * c.nominal / 100.0)
            c.step("${descriptor.name} loaded reverse=$reverse") { diode.diodeProcess.process(.01) }
            if (reverse) assertTrue(load.power < .001) else assertTrue(load.power > 90.0)
        }
    }

    @Test fun tinyRealLoadsDoNotTripTheGeneratorOrGetIgnored() {
        for (ohms in listOf(1e8, 1.0001e8, 5e8, 1e9, 1e12)) {
            val c = Circuit()
            val load = c.resistor(c.generator.inputLoad, null, ohms)
            c.step("load=$ohms")
            val expectedVolts = c.nominal * ohms / (ohms + c.generator.desc.regulatorDroopResistance +
                c.generator.inputToPositiveResistor.resistance)
            assertEquals(expectedVolts, load.voltage, 1e-8)
            assertEquals(load.power, c.generator.electricalPowerSource.power, 1e-8)
        }
    }

    @Test fun aPreconnectedOpenSwitchAllowsColdStartAndShaftRestart() {
        val c = Circuit(warm = false)
        val descriptor = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalSwitchDescriptor>()
            .single { it.name == "Very High Voltage Switch" }
        val contact = ElectricalSwitchElement(SixNode(), Direction.YN, descriptor)
        c.attach(contact, contact.aLoad)
        repeat(2) { start ->
            repeat(120) { tick ->
                c.root.step()
                assertTrue(c.generator.electricalPowerSource.enabled, "start=$start tick=$tick: generator tripped")
                assertEquals(c.generator.electricalProcess.voltageTarget, c.generator.inputLoad.voltage, .001)
            }
            c.powered("start=$start")
            c.generator.shaft.rads = 0.0
            c.root.step()
            assertEquals(0.0, c.generator.inputLoad.voltage, 1e-8)
            c.generator.shaft.rads = 200.0
        }
    }
}
