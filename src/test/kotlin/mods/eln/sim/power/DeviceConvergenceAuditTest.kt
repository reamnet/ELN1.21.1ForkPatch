package mods.eln.sim.power

import mods.eln.Eln
import mods.eln.mechanical.ShaftGeneratorProcess
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.OneWayDcDcDescriptor
import mods.eln.transparentnode.OneWayDcDcElement
import mods.eln.transparentnode.OneWayDcDcMode
import mods.eln.transparentnode.OneWayDcDcProcess
import mods.eln.transparentnode.renewable.SolarElement
import mods.eln.transparentnode.renewable.SolarPriority
import mods.eln.devtest.StandbyTestFluids
import mods.eln.transparentnode.standby.StandbyGeneratorElement
import mods.eln.transparentnode.standby.StandbyMode
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceConvergenceAuditTest {
    @Test fun parallelStandbyAlternatorsReachTheirSharedPowerLimitBeforeCommitting() {
        val root = RootSystem(.05, 1)
        val bus = VoltageState(); root.addState(bus)
        val generators = List(2) {
            StandbyGeneratorElement(TransparentNode(), Eln.transparentNodeItem.subItemList[7 + (124 shl 6)]!!).also { e ->
                e.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0), 20000), FluidAction.EXECUTE)
                e.unit.mode = StandbyMode.MANUAL; e.unit.setVoltage(240.0)
                e.electricalLoadList.forEach(root::addState)
                e.electricalComponentList.forEach(root::addComponent)
                root.addComponent(Resistor(e.unit.output, bus).apply { resistance = .02 })
                e.unit.attach(root)
            }
        }
        val load = Resistor(bus, null).apply { resistance = 240.0 * 240 / 100.0 }; root.addComponent(load)
        repeat(65) { root.step(); generators.forEach { it.unit.tick(.05) } }
        assertTrue(generators.all { it.unit.running })
        load.resistance = 240.0 * 240 / 25000.0
        // Inspect the one accepted electrical operating point before advancing the
        // engines' independent sustained-overload timers or consuming fuel.
        root.step()
        assertEquals(20000.0, generators.sumOf { it.unit.source.power }, .1,
            "Two loaded 10 kW alternators accepted an incomplete coupled solve")
    }

    @Test fun fixedIsolatedAndVariableOneWayConvertersHandleIdleLoadsInOldAndNewSaves() {
        val failures = mutableListOf<String>()
        val descriptors = Eln.transparentNodeItem.subItemList.values.filterIsInstance<OneWayDcDcDescriptor>()
        for (mode in listOf(OneWayDcDcMode.FIXED, OneWayDcDcMode.ISOLATION, OneWayDcDcMode.BOOST_BUCK)) {
            for (version in listOf(1, 2)) for (volts in listOf(3200.0, 12470.0)) {
                for (ohms in listOf(1e6, 1e9, 1e12, 1e19)) {
                    val element = OneWayDcDcElement(TransparentNode(), descriptors.first { it.mode == mode })
                    val target = if (mode == OneWayDcDcMode.ISOLATION) volts else 120.0
                    element.inventory.setItem(0, Eln.instance.veryHighVoltageCableDescriptor.newItemStack(1))
                    element.inventory.setItem(1, Eln.instance.veryHighVoltageCableDescriptor.newItemStack(1))
                    element.inventory.setItem(2, Eln.findItemStack("Optimal Ferromagnetic Core", 1))
                    element.settings.version = version
                    element.settings.mode = if (version == 2 && mode == OneWayDcDcMode.BOOST_BUCK) "VOLTAGE" else "RATIO"
                    element.settings.value = if (element.settings.mode == "VOLTAGE") target else target / volts
                    element.ratioControl = target / volts
                    element.populated = true; element.primaryMeltCurrent = 200.0; element.secondaryMeltCurrent = 200.0
                    element.primaryWindingResistance.resistance = .01
                    element.secondaryWindingResistance.resistance = .01
                    val primaryReturn = if (element.isolated) element.primaryReferenceLoad else null
                    val secondaryReturn = if (element.isolated) element.secondaryReferenceLoad else null
                    element.inputSink.connectTo(element.primaryInternal, primaryReturn)
                    element.outputSource.connectTo(element.secondaryInternal, secondaryReturn)
                    val root = RootSystem(.01, 1)
                    element.electricalLoadList.forEach(root::addState)
                    element.electricalComponentList.forEach(root::addComponent)
                    val feed = VoltageState(); root.addState(feed)
                    root.addComponent(VoltageSource("grid", feed, primaryReturn).apply { voltage = volts })
                    root.addComponent(Resistor(feed, element.primaryLoad).apply { resistance = .002 })
                    root.addComponent(Resistor(element.secondaryLoad, secondaryReturn).apply { resistance = ohms })
                    val controller = OneWayDcDcProcess(element); root.addProcess(controller)
                    repeat(3) { root.step() }
                    val actual = element.secondaryLoad.voltage - (secondaryReturn?.voltage ?: 0.0)
                    if (!element.outputSource.enabled || kotlin.math.abs(actual - target) > .001)
                        failures += "$mode v$version $volts -> $target V, load=$ohms: ${controller.status}, output=$actual"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun aHybridInverterCanFeedStandbyLoadsFromAStiffHighVoltageGrid() {
        val failures = mutableListOf<String>()
        for (volts in listOf(3200.0, 12470.0, 33000.0)) for (target in listOf(120.0, 240.0)) {
            for (ohms in listOf(1e6, 1e9, 1e12, 1e19)) {
                val element = SolarElement(TransparentNode(), Eln.transparentNodeItem.subItemList[9 + (124 shl 6)]!!)
                val unit = element.unit
                unit.priority = SolarPriority.GRID_ONLY; unit.setVoltage(target)
                val root = RootSystem(.01, 1)
                val feed = VoltageState(); root.addState(feed)
                element.electricalLoadList.forEach(root::addState)
                element.electricalComponentList.forEach(root::addComponent)
                val load = Resistor(unit.output, null).apply { resistance = ohms }
                root.addComponent(VoltageSource("grid", feed, null).apply { voltage = volts })
                root.addComponent(Resistor(feed, unit.grid).apply { resistance = .002 })
                root.addComponent(load); root.addProcess(unit.controller)
                repeat(5) { root.step(); unit.account(.01) }
                if (!unit.outputSource.enabled || kotlin.math.abs(unit.output.voltage - target) > 1e-4)
                    failures += "$volts -> $target V, load=$ohms: ${unit.status}: ${unit.diagnostic}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun aRejectedOneWayConverterIsOpenedLocallyWithoutTrippingItsGenerator() {
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<OneWayDcDcDescriptor>()
            .first { it.mode == OneWayDcDcMode.BOOST_BUCK }
        val element = OneWayDcDcElement(TransparentNode(), descriptor)
        element.settings.version = 1; element.settings.mode = "RATIO"; element.settings.value = 1.0
        element.populated = true
        element.inputSink.connectTo(element.primaryInternal, null)
        element.outputSource.connectTo(element.secondaryInternal, null)
        element.primaryWindingResistance.resistance = .01
        element.secondaryWindingResistance.resistance = .01
        val controller = OneWayDcDcProcess(element)
        val root = RootSystem(.01, 1)
        val feed = VoltageState(); root.addState(feed)
        val source = SwitchableVoltageSource("generator").apply { connectTo(feed, null) }
        val generator = ShaftGeneratorProcess(feed, null, source, { 3200.0 }, { 1e8 }, 1e6, 10.0, 100.0, .95)
        var trips = 0
        root.addComponent(source)
        root.addProcess(object : ConservativePowerProcess by generator {
            override fun failClosed() { trips++; generator.failClosed() }
        })
        root.addComponent(Resistor(feed, null).apply { resistance = 5000.0 })
        repeat(2) { root.step() }
        element.electricalLoadList.forEach(root::addState)
        element.electricalComponentList.forEach(root::addComponent)
        root.addComponent(Resistor(feed, element.primaryLoad).apply { resistance = .01 })
        root.addComponent(Resistor(element.secondaryLoad, null).apply { resistance = 1000.0 })
        // Inject a rejected candidate at the load boundary, exercising the production
        // adapter's isolation contract through RootSystem rather than testing a flag.
        root.addProcess(object : ConservativePowerProcess by controller {
            override fun acceptsCandidate() = !element.inputSink.enabled && !element.outputSource.enabled
        })
        root.step()
        assertEquals("NON_CONVERGENT", controller.status)
        assertEquals(0, trips, "A faulty converter tripped its otherwise healthy upstream generator")
        assertTrue(source.enabled && feed.voltage > 3000.0)
        assertEquals(0.0, element.inputSink.power, 1e-8)
    }
}
