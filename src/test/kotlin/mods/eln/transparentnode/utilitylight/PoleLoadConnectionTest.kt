package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.mechanical.ShaftGeneratorProcess
import mods.eln.mechanical.MotorElement
import mods.eln.mechanical.MotorDescriptor
import mods.eln.mechanical.ShaftNetwork
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.SafeTransformerProcess
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sim.power.pendingSourcePower
import org.junit.Test
import kotlin.test.*

class PoleLoadConnectionTest {
    @Test fun attachingATargetVoltageLoadToALitFeederKeepsTheGridPowered() {
        for (poleTransformer in listOf(false, true)) for (count in listOf(10, 120)) {
            for (target in listOf(850.0, 3200.0)) runFeeder(count, poleTransformer, target)
        }
    }

    @Test fun attachingALargeShaftMotorBehindATargetVoltageConverterKeepsTheGridPowered() {
        for (poleTransformer in listOf(false, true)) for (count in listOf(10, 120)) {
            for (target in listOf(850.0, 3200.0)) runFeeder(count, poleTransformer, target, true)
        }
    }

    @Test fun anIdleMachineResistanceDoesNotShutDownALitFeeder() {
        for (poleTransformer in listOf(false, true)) for (target in listOf(850.0, 3200.0)) {
            runFeeder(120, poleTransformer, target, idleResistance = 1e9)
        }
    }

    @Test fun aLoadedFeederSurvivesA14KvTo3200VoltSupplyChange() {
        for (poleTransformer in listOf(false, true)) for (target in listOf(850.0, 3200.0)) {
            runFeeder(120, poleTransformer, target, motorLoad = true, idealSupply = true)
        }
    }

    @Test fun aCoastingMotorAtTheConverterTargetDoesNotTripTheFeeder() {
        for (poleTransformer in listOf(false, true)) for (target in listOf(850.0, 3200.0)) {
            runFeeder(120, poleTransformer, target, motorLoad = true, idealSupply = true, coasting = true)
        }
    }

    private fun runFeeder(count: Int, poleTransformer: Boolean, target: Double, motorLoad: Boolean = false,
                          idleResistance: Double = 1e18, idealSupply: Boolean = false, coasting: Boolean = false) {
        val root = RootSystem(.05, 50)
        fun pin() = VoltageState().also(root::addState)
        fun wire(a: State, b: State, ohms: Double = .01) = Resistor(a, b).apply {
            resistance = ohms; root.addComponent(this)
        }
        val sourcePin = pin()
        var supplyVolts = 14000.0
        val source = if (idealSupply) VoltageSource("grid", sourcePin, null).apply { voltage = supplyVolts }
            else SwitchableVoltageSource("shaft").apply { connectTo(sourcePin, null) }
        root.addComponent(source)
        if (!idealSupply) root.addProcess(ShaftGeneratorProcess(sourcePin, null, source as SwitchableVoltageSource,
            { supplyVolts }, { 1e8 }, 20000.0, 20.0, 100.0, .95))
        var feeder: State = sourcePin
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
        val lights = List(count) {
            val light = StepDownUnitElement(TransparentNode(), descriptor)
            light.electricalLoadList.forEach(root::addState)
            light.electricalComponentList.forEach(root::addComponent)
            wire(feeder, light.unit.primary, .002)
            feeder = light.unit.primary
            root.addComponent(Resistor(light.unit.secondary, null).apply { resistance = 240.0 })
            root.addProcess(light.unit.controller)
            light
        }
        var tapTrips = 0
        fun transformer(feed: State, ratio: Double, target: Double? = null): VoltageState {
            val a = pin(); val b = pin()
            wire(feed, a)
            val input = SwitchableVoltageSource("input").apply { connectTo(a, null) }
            val output = SwitchableVoltageSource("output").apply { connectTo(b, null) }
            root.addComponent(input); root.addComponent(output)
            val controller = SafeTransformerProcess(a, b, input, output) { true }.apply {
                this.ratio = ratio; voltageTarget = target
            }
            root.addProcess(object : ConservativePowerProcess by controller {
                override fun failClosed() {
                    tapTrips++
                    println("TAP_REJECT count=$count pole=$poleTransformer target=${controller.voltageTarget} input=${input.voltage} output=${output.voltage} pin=${-pendingSourcePower(input)} pout=${pendingSourcePower(output)}")
                    controller.failClosed()
                }
            })
            return b
        }
        repeat(40) { root.step() }
        assertTrue(sourcePin.voltage > 10000.0)
        val tap = if (poleTransformer) transformer(feeder, .25) else feeder
        val output = transformer(tap, 1.0, target)
        val load = Resistor(output, null).apply { resistance = idleResistance }
        root.addComponent(load)
        fun checkPowered(label: String) {
            assertEquals(0, tapTrips, "$label count=$count poleTransformer=$poleTransformer")
            assertTrue(sourcePin.voltage > supplyVolts * .7,
                "$label count=$count poleTransformer=$poleTransformer HV=${sourcePin.voltage} output=${output.voltage}")
            lights.forEach { assertEquals(it.unit.outputVoltage.toDouble(), it.unit.secondary.voltage, .1,
                "$label count=$count poleTransformer=$poleTransformer ${it.unit.controller.status}") }
        }
        repeat(40) { step -> root.step(); checkPowered("tap initialization step=$step") }
        checkPowered("before load")
        if (motorLoad) {
            val motor = MotorElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
                .filterIsInstance<MotorDescriptor>().single { it.name == "Large Shaft Motor" })
            motor.shaft = ShaftNetwork(motor, motor.shaftConnectivity.iterator())
            if (coasting) motor.shaft.rads = motor.desc.nominalRads * target / motor.desc.nominalU
            motor.electricalLoadList.forEach(root::addState)
            motor.electricalComponentList.forEach(root::addComponent)
            wire(output, motor.wireLoad, .01)
            root.addProcess(motor.electricalProcess)
            repeat(120) { step ->
                if (coasting && step == 30) lights.first().unit.read(net.minecraft.nbt.CompoundTag().apply { putInt("mainsOutputVoltage", 240) })
                if (idealSupply && step == 60) { supplyVolts = 3200.0; source.voltage = supplyVolts }
                root.step()
                checkPowered("motor step=$step")
                assertFalse(motor.electricalProcess.faulted,
                    "motor step=$step count=$count poleTransformer=$poleTransformer motor drive tripped")
                assertTrue(kotlin.math.abs(motor.powerSource.current) <= motor.desc.driveCurrentLimit * 1.000001 + 1e-7)
                assertTrue(motor.wireLoad.voltage > target * .9,
                    "motor step=$step motor=${motor.wireLoad.voltage} converter=${output.voltage}")
                if (idealSupply && !coasting) motor.shaftProcess.process(.05)
            }
            return
        }
        for (watts in listOf(2000.0, 10000.0, 0.0, 2000.0)) {
            if (watts == 0.0) load.resistance = 1e18 else load.resistance = target * target / watts
            repeat(30) { step ->
                root.step()
                checkPowered("load=$watts step=$step")
                if (watts > 0.0) assertTrue(load.power > watts * .95,
                    "load=$watts actual=${load.power} output=${output.voltage}")
            }
        }
    }
}
