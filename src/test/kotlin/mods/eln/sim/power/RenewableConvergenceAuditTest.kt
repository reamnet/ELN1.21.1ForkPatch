package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.renewable.RenewableSupply
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenewableConvergenceAuditTest {
    @Test fun aTemporaryBackfeedTrialDoesNotLatchTheRenewableOpenForTheRestOfTheStep() {
        val root = RootSystem(.01, 1)
        val pin = VoltageState(); val grid = VoltageState()
        root.addState(pin); root.addState(grid)
        val source = SwitchableVoltageSource("renewable").apply { connectTo(pin, null) }
        val other = mods.eln.sim.mna.component.VoltageSource("grid", grid, null).apply { voltage = 14000.0 }
        root.addComponent(source); root.addComponent(other)
        root.addComponent(Resistor(pin, grid).apply { resistance = .02 })
        val controller = RenewableSupply(source) { 20000.0 }
        root.addProcess(controller); root.generate()
        controller.beginStep(.01); controller.prepareStep(); controller.rootSystemPreStepProcess()
        assertTrue(!source.enabled, "A genuinely higher-voltage grid must not backfeed the renewable")
        // Coupled solver probes change commands without advancing physical time.
        other.voltage = 3100.0
        root.systems.forEach { it.stepCalc() }
        assertTrue(!controller.acceptsCandidate(), "An obsolete open decision hid available generation")
        controller.rootSystemPreStepProcess()
        assertTrue(source.enabled, "A temporary trial disabled a valid renewable supply")
        root.systems.forEach { it.stepCalc() }
        assertTrue(controller.acceptsCandidate())
        assertEquals(20000.0, pendingSourcePower(source), .1)
        assertEquals(0.0, source.power, 1e-8, "A trial must not commit physical state")
    }

    @Test fun parallelWindAndWaterSuppliesShareFinitePowerAndRecoverAcrossLoadChanges() {
        for (count in listOf(1, 2, 4, 10)) for (reverse in listOf(false, true)) {
            val root = RootSystem(.01, 1)
            val bus = VoltageState(); root.addState(bus)
            val budgets = List(count) { if (it % 2 == 0) 1000.0 else 20000.0 }
            val sources = List(count) { i ->
                val pin = VoltageState(); root.addState(pin)
                SwitchableVoltageSource("renewable-$i").apply {
                    connectTo(pin, null); root.addComponent(this)
                    root.addComponent(Resistor(pin, bus).apply { resistance = .02 })
                }
            }
            val processes = sources.mapIndexed { i, source -> RenewableSupply(source) { budgets[i] } }
            (if (reverse) processes.reversed() else processes).forEach(root::addProcess)
            val load = Resistor(bus, null); root.addComponent(load)
            for (demand in listOf(100.0, 10000.0, 50000.0, 100000.0, 0.0, 100.0)) {
                load.resistance = if (demand == 0.0) 1e19 else 3200.0 * 3200 / demand
                repeat(10) { step ->
                    root.step()
                    sources.forEachIndexed { i, source ->
                        assertTrue(source.power in -1e-6..(budgets[i] * 1.000001 + 1e-5),
                            "count=$count reverse=$reverse demand=$demand step=$step powers=${sources.map { it.power }}")
                    }
                }
                val available = budgets.sum()
                if (demand < available) assertTrue(bus.voltage > 3190.0,
                    "count=$count reverse=$reverse demand=$demand bus=${bus.voltage}")
                else assertEquals(available, sources.sumOf { it.power }, .1,
                    "An overloaded bus must use the available renewable power before shedding voltage")
            }
        }
    }
}
