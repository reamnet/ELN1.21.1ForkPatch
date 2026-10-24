package mods.eln.sim.power

import mods.eln.Eln
import mods.eln.mechanical.GeneratorDescriptor
import mods.eln.mechanical.GeneratorElement
import mods.eln.mechanical.ShaftNetwork
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.renewable.RenewableSupply
import mods.eln.transparentnode.renewable.LargeRenewableElement
import org.junit.Test
import kotlin.test.assertTrue

/** The wind farm joins a bus already energized by two maintained 202 rad/s shafts. */
class WindBankGeneratorGridTest {
    @Test fun sharedWindBankAndTwoGeneratorsFeedA15200VoltStepUpWithoutBlackouts() {
        for (ohms in listOf(.000001, .01, 1.0)) for (reverse in listOf(false, true))
            exercise(false, ohms, reverse)
    }

    @Test fun independentlyConvertedWindTurbinesAlsoRecoverAcrossSharedLimiterTransitions() {
        for (ohms in listOf(.000001, .01, 1.0)) for (reverse in listOf(false, true))
            exercise(true, ohms, reverse)
    }

    @Test fun unevenWindBankStaysOnThroughSustainedLoadAndWindChanges() {
        for (reverse in listOf(false, true)) exercise(false, .01, reverse, uneven = true, holdSteps = 150)
    }

    @Test fun theSharedGridAlsoSupportsAThirdTargetVoltageConverterFeedingTheLoad() {
        for (reverse in listOf(false, true)) exercise(false, .01, reverse,
            uneven = true, holdSteps = 30, regulatedLoad = true)
    }

    private fun exercise(separate: Boolean, windingOhms: Double, reverse: Boolean,
                         uneven: Boolean = false, holdSteps: Int = 10, regulatedLoad: Boolean = false) {
        val root = RootSystem(.01, 1)
        val grid = VoltageState(); root.addState(grid)
        val windBuses = List(if (separate) 10 else 1) { VoltageState().also(root::addState) }
        val generators = List(2) { GeneratorElement(TransparentNode(),
            Eln.transparentNodeItem.subItemList.values.filterIsInstance<GeneratorDescriptor>()
                .single { it.name == "Large Generator" }).also {
            it.shaft = ShaftNetwork(it, it.shaftConnectivity.iterator()).apply { rads = 202.0 }
            it.electricalLoadList.forEach(root::addState)
            it.electricalComponentList.forEach(root::addComponent)
            root.addComponent(Resistor(it.inputLoad, grid).apply { resistance = windingOhms })
        } }
        var windWatts = 0.0
        val wind = List(10) { i ->
            val pin = VoltageState(); root.addState(pin)
            val source = SwitchableVoltageSource("wind-$i").apply { connectTo(pin, null) }
            root.addComponent(source)
            root.addComponent(Resistor(pin, windBuses[if (separate) i else 0]).apply { resistance = LargeRenewableElement.OUTPUT_RESISTANCE })
            root.addComponent(Resistor(windBuses[if (separate) i else 0], null).pullDown())
            RenewableSupply(source) { windWatts * if (uneven) 1 - i * .03 else 1.0 }
        }
        val transformers = windBuses.map { windBus ->
            val primary = VoltageState(); val secondary = VoltageState()
            listOf(primary, secondary).forEach(root::addState)
            val input = SwitchableVoltageSource("wind transformer input").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("wind transformer output").apply { connectTo(secondary, null) }
            listOf(input, output,
                Resistor(windBus, primary).apply { resistance = windingOhms },
                Resistor(secondary, grid).apply { resistance = windingOhms }).forEach(root::addComponent)
            SafeTransformerProcess(primary, secondary, input, output) { true }.apply {
                ratio = 3300.0 / 3200; voltageTarget = 3300.0
            }
        }
        val hvPrimary = VoltageState(); val hvSecondary = VoltageState()
        listOf(hvPrimary, hvSecondary).forEach(root::addState)
        val hvInput = SwitchableVoltageSource("15.2 kV input").apply { connectTo(hvPrimary, null) }
        val hvOutput = SwitchableVoltageSource("15.2 kV output").apply { connectTo(hvSecondary, null) }
        listOf(hvInput, hvOutput, Resistor(grid, hvPrimary).apply { resistance = windingOhms })
            .forEach(root::addComponent)
        val stepUp = SafeTransformerProcess(hvPrimary, hvSecondary, hvInput, hvOutput) { true }.apply {
            ratio = 15_200.0 / 3200; voltageTarget = 15_200.0
        }
        var loadPin = hvSecondary
        var loadVolts = 15_200.0
        val consumer = if (regulatedLoad) {
            val p = VoltageState(); val s = VoltageState()
            listOf(p, s).forEach(root::addState)
            val a = SwitchableVoltageSource("load input").apply { connectTo(p, null) }
            val b = SwitchableVoltageSource("load output").apply { connectTo(s, null) }
            listOf(a, b, Resistor(hvSecondary, p).apply { resistance = .01 }).forEach(root::addComponent)
            loadPin = s; loadVolts = 850.0
            SafeTransformerProcess(p, s, a, b) { true }.apply {
                ratio = 850.0 / 15_200; voltageTarget = 850.0
            }
        } else null
        val processes = generators.map { it.electricalProcess } + wind + transformers + stepUp + listOfNotNull(consumer)
        (if (reverse) processes.reversed() else processes).forEach(root::addProcess)
        val load = Resistor(loadPin, null).apply { resistance = loadVolts * loadVolts / 20_000 }
        root.addComponent(load)
        repeat(200) { root.step() }
        assertTrue(hvSecondary.voltage > 14_000, "Grid did not start with idle wind turbines: ${stepUp.status}")
        for (demand in listOf(20_000.0, 100_000.0, 1000.0, 60_000.0)) {
            load.resistance = loadVolts * loadVolts / demand
            for (watts in listOf(20_000.0, 5000.0, 1000.0, 0.0, 15_000.0)) {
                windWatts = watts
                repeat(holdSteps) { step ->
                    root.step()
                    assertTrue(grid.voltage > 2900 && hvSecondary.voltage > 14_000 &&
                        (transformers + stepUp + listOfNotNull(consumer)).none { it.status == "NON_CONVERGENT" } &&
                        (!regulatedLoad || loadPin.voltage > 800),
                        "separate=$separate ohms=$windingOhms reverse=$reverse demand=$demand wind=$watts step=$step grid=${grid.voltage} " +
                            "HV=${hvSecondary.voltage} stepUp=${stepUp.status} transformers=${transformers.map { it.status }} windBus=${windBuses.map { it.voltage }} " +
                            "wind=${wind.map { it.source.power }} generators=${generators.map { it.electricalPowerSource.power }}")
                    wind.forEach {
                        assertTrue(it.source.power in -1e-5..(it.watts * 1.00001 + 1e-4))
                        assertTrue(it.source.current in -1e-6..50.0001)
                    }
                    if (watts >= 15_000) assertTrue(wind.sumOf { it.source.power } > 1000,
                        "Wind contribution failed: separate=$separate uneven=$uneven ohms=$windingOhms reverse=$reverse demand=$demand watts=$watts step=$step " +
                            "windBus=${windBuses.map { it.voltage }} wind=${wind.map { it.source.power }} on=${wind.map { it.source.enabled }} " +
                            "transformers=${transformers.map { it.status }} gen=${generators.map { it.electricalPowerSource.power }}")
                }
            }
        }
    }
}
