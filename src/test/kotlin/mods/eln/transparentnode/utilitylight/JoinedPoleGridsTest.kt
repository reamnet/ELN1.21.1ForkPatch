package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.mechanical.ShaftGeneratorProcess
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.ConservativePowerProcess
import org.junit.Test
import kotlin.test.*

class JoinedPoleGridsTest {
    private class Tracked(val delegate: ConservativePowerProcess, val diagnostic: () -> String) : ConservativePowerProcess by delegate {
        var trips = 0
        var evaluations = 0
        var hintRequests = 0
        override fun rootSystemPreStepProcess() { evaluations++; delegate.rootSystemPreStepProcess() }
        override fun voltageSearchHints(): Map<SwitchableVoltageSource, DoubleArray> {
            hintRequests++
            return delegate.voltageSearchHints()
        }
        override fun failClosed() {
            trips++
            if (trips == 1) println("JOINED_GRID_REJECT ${diagnostic()}")
            delegate.failClosed()
        }
    }

    @Test fun joiningTwoShaftSuppliesTo120PoleLoadsDoesNotTripEitherGrid() {
        for (count in listOf(10, 120)) {
            for (emfB in listOf(13800.0, 12000.0, 8000.0)) runGrid(emfB, false, count)
        }
    }

    @Test fun sharedLowVoltageGridAndChangingFeedersRemainStable() {
        runGrid(13800.0, true)
    }

    @Test fun limitedShaftEnergyWith120UnitsAndAnotherNear14KvSupplyStaysConservative() {
        runGrid(13800.0, false, 120, 8000.0)
    }

    @Test fun aPositiveFieldWithNoShaftEnergyIsIdleRatherThanNonConvergent() {
        val root = RootSystem(.05, 50)
        val pin = VoltageState(); root.addState(pin)
        val source = SwitchableVoltageSource("empty shaft").apply { connectTo(pin, null) }
        root.addComponent(source)
        root.addComponent(Resistor(pin, null).apply { resistance = 10000.0 })
        val process = Tracked(ShaftGeneratorProcess(pin, null, source, { 14000.0 }, { 0.0 },
            20000.0, 20.0, 100.0, .95)) { "empty shaft was rejected at zero output" }
        root.addProcess(process)
        repeat(25) {
            root.step()
            assertEquals(0, process.trips)
            assertEquals(0.0, source.power, 1e-10)
            assertEquals(0.0, pin.voltage, 1e-10)
        }
    }

    @Test fun rejectedLoadIsOpenedWithoutTrippingAHealthyGeneratorAndLamp() {
        val root = RootSystem(.05, 50)
        val feed = VoltageState(); val sourcePin = VoltageState(); val badPin = VoltageState()
        listOf(feed, sourcePin, badPin).forEach(root::addState)
        val source = SwitchableVoltageSource("shaft").apply { connectTo(sourcePin, null) }
        root.addComponent(source)
        val winding = Resistor(sourcePin, feed).apply { resistance = .01 }
        root.addComponent(winding)
        root.addProcess(ShaftGeneratorProcess(sourcePin, null, source, { 14000.0 }, { 1e8 },
            20000.0, 20.0, 100.0, .95))
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
        val light = StepDownUnitElement(TransparentNode(), descriptor)
        light.electricalLoadList.forEach(root::addState)
        light.electricalComponentList.forEach(root::addComponent)
        root.addComponent(Resistor(feed, light.unit.primary).apply { resistance = .01 })
        root.addComponent(Resistor(light.unit.secondary, null).apply { resistance = 240.0 })
        root.addProcess(light.unit.controller)
        val bad = SwitchableVoltageSource("invalid load").apply { connectTo(badPin, null); enabled = true; voltage = 10000.0 }
        root.addComponent(bad)
        root.addComponent(Resistor(feed, badPin).apply { resistance = 10000.0 })
        var opened = false
        root.addProcess(object : ConservativePowerProcess {
            override fun rootSystemPreStepProcess() {}
            override fun acceptsCandidate() = opened
            override fun failClosed() { opened = true; bad.enabled = false }
            override fun connectedSystems() = setOfNotNull(badPin.subSystem)
            override fun isolatesLoadOnFailure() = true
        })
        var commits = 0
        root.addProcess(mods.eln.sim.mna.misc.ISubSystemProcessFlush { commits++ })
        root.step()
        assertTrue(opened)
        assertFalse(bad.enabled)
        assertTrue(source.enabled, "A rejected load shut down its healthy generator")
        assertEquals(120.0, light.unit.secondary.voltage, .1)
        assertTrue(light.unit.output.power > 59.0)
        assertNotEquals("NON_CONVERGENT", light.unit.controller.status)
        assertEquals(1, commits)
    }

    private fun runGrid(emfB: Double, sharedOutput: Boolean, count: Int = 120, outputWattsA: Double = 1e9) {
        val root = RootSystem(.05, 50)
        val gridA = VoltageState(); val gridB = VoltageState()
        root.addState(gridA); root.addState(gridB)
        val trackers = mutableListOf<Tracked>()
        var stepNumber = 0
        val wires = mutableListOf<Resistor>()
        val passive = mutableListOf<Resistor>()
        val sources = mutableListOf<SwitchableVoltageSource>()
        fun generator(bus: VoltageState, name: String, emf: Double) {
            val pin = VoltageState(); root.addState(pin)
            val source = SwitchableVoltageSource(name).apply { connectTo(pin, null) }
            root.addComponent(source)
            sources += source
            root.addComponent(Resistor(pin, bus).apply { resistance = .01 }.also { passive += it })
            val process = ShaftGeneratorProcess(pin, null, source, { emf + .1 * kotlin.math.sin(stepNumber * .1) },
                { (if (name == "shaft A") outputWattsA else 1e9) * .05 / .95 }, 20000.0, 20.0, 100.0, .95)
            val tracked = Tracked(process) {
                "$name accepted=${process.acceptsCandidate()} command=${source.voltage} current=${-pin.subSystem.pendingValue(source.currentState)} target=${process.voltageTarget}"
            }
            trackers += tracked; root.addProcess(tracked)
        }
        generator(gridA, "shaft A", 14000.0)
        generator(gridB, "shaft B", emfB)
        val independent = VoltageState(); root.addState(independent)
        generator(independent, "independent shaft", 14000.0)
        val independentTracker = trackers.last()
        root.addComponent(Resistor(independent, null).apply { resistance = 40000.0 }.also { passive += it })
        root.addComponent(Resistor(gridB, null).apply { resistance = 40000.0 }.also { passive += it })
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
        var previous = gridA
        var previousOutput: mods.eln.sim.mna.state.State? = null
        val units = List(count) { i ->
            val element = StepDownUnitElement(TransparentNode(), descriptor)
            val unit = element.unit
            element.electricalLoadList.forEach(root::addState)
            element.electricalComponentList.forEach(root::addComponent)
            passive += element.electricalComponentList.filterIsInstance<Resistor>()
            wires += Resistor(previous, unit.primary).apply { resistance = .002 }.also(root::addComponent)
            previous = unit.primary
            if (sharedOutput && previousOutput != null) {
                wires += Resistor(previousOutput, unit.secondary).apply { resistance = .002 }.also(root::addComponent)
            }
            previousOutput = unit.secondary
            root.addComponent(Resistor(unit.secondary, null).apply { resistance = 240.0 }.also { passive += it })
            val tracked = Tracked(unit.controller) {
                "pole=$i status=${unit.controller.status} " +
                    "vin=${unit.input.voltage} iin=${unit.input.subSystem.pendingValue(unit.input.currentState)} " +
                    "vout=${unit.output.voltage} iout=${-unit.output.subSystem.pendingValue(unit.output.currentState)}"
            }
            trackers += tracked; root.addProcess(tracked)
            unit
        }
        fun checkGrid(phase: String) {
            assertTrue(trackers.all { it.trips == 0 }, "$phase ${trackers.filter { it.trips > 0 }.map { it.diagnostic() }}")
            units.forEach { assertEquals(120.0, it.secondary.voltage, .1, "$phase ${it.controller.status}") }
            assertTrue(gridA.voltage > 1000.0 && gridB.voltage > 1000.0)
            assertTrue(independent.voltage > 1000.0)
            assertEquals(0, independentTracker.hintRequests, "An unrelated healthy grid was restarted")
            val supplied = sources.sumOf { it.power }
            val losses = (passive + wires).sumOf { it.power }
            val electronics = units.sumOf { -it.input.power - it.output.power }
            assertEquals(supplied, losses + electronics, 1e-5 + kotlin.math.abs(supplied) * 1e-6, "$phase energy balance")
            assertTrue(sources[0].power <= outputWattsA * 1.000001 + 1e-7, "$phase shaft energy limit")
            sources.forEach { assertTrue(kotlin.math.abs(it.current) <= 100.000101, "$phase shaft current limit") }
        }
        repeat(40) { step ->
            stepNumber++
            root.step()
            if (step > 20) checkGrid("separate step=$step")
        }
        val tie = Resistor(gridA, gridB).apply { resistance = .01 }
        passive += tie
        root.addComponent(tie)
        repeat(120) { step ->
            stepNumber++
            if (step % 5 == 0) wires.forEach { it.resistance *= 1.00001 }
            val start = System.nanoTime()
            root.step()
            if (step == 0) println("JOINED_GRID count=$count emfB=$emfB shared=$sharedOutput join_ms=${(System.nanoTime()-start)/1e6} evaluations=${trackers.sumOf { it.evaluations }}")
            checkGrid("joined step=$step")
        }
    }
}
