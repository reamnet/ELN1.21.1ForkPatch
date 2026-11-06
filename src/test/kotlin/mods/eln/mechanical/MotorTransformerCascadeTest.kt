package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sim.power.SafeTransformerProcess
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.State
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** Regression for connecting two drives to a four-by-seven large-flywheel bank,
 * fed by generators through 3.2 kV -> 12.5 kV -> 69 kV -> 3.2 kV stages. */
class MotorTransformerCascadeTest {
    @Test fun startingTheFlywheelBankKeepsBothTransformerModesPowered() {
        val motorDescriptor = Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<MotorDescriptor>().single { it.name == "Large Shaft Motor" }
        val flywheelDescriptor = Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<FlywheelDescriptor>().single { it.name == "Large Flywheel" }
        val generatorDescriptor = Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<GeneratorDescriptor>().single { it.name == "Large Generator" }
        val reuseKey = "eln.disableStampedMatrixReuse"
        val previousReuse = System.getProperty(reuseKey)
        val baselineResults = HashMap<Boolean, List<Double>>()
        try {
            for (targetMode in listOf(false, true)) for (reuse in listOf(false, true)) {
                System.setProperty(reuseKey, (!reuse).toString())
                val count = 2
                val root = RootSystem(.01, 50)
                fun pin() = VoltageState().also(root::addState)
                fun wire(a: State, b: State, resistance: Double = .01) =
                    root.addComponent(Resistor(a, b).apply { this.resistance = resistance })
                val supply = pin()
                repeat(4) {
                    val generator = GeneratorElement(TransparentNode(), generatorDescriptor)
                    generator.shaft = ShaftNetwork(generator, generator.shaftConnectivity.iterator()).apply { rads = 200.0 }
                    generator.electricalLoadList.forEach(root::addState)
                    generator.electricalComponentList.forEach(root::addComponent)
                    wire(generator.inputLoad, supply)
                    root.addProcess(generator.electricalProcess)
                }
                repeat(200) { root.step() }
                val transformerProcesses = ArrayList<SafeTransformerProcess>()
                fun transformer(feed: State, inputVolts: Double, outputVolts: Double): State {
                    val primary = pin(); val secondary = pin()
                    wire(feed, primary)
                    val input = SwitchableVoltageSource("primary").apply {
                        connectTo(primary, null); voltage = inputVolts; enabled = true
                    }
                    val output = SwitchableVoltageSource("secondary").apply {
                        connectTo(secondary, null); voltage = outputVolts; enabled = true
                    }
                    root.addComponent(input); root.addComponent(output)
                    val controller = SafeTransformerProcess(primary, secondary, input, output) { true }.apply {
                        ratio = outputVolts / inputVolts
                        if (targetMode) voltageTarget = outputVolts
                    }
                    transformerProcesses += controller
                    root.addProcess(controller)
                    return pin().also { wire(secondary, it) }
                }
                val medium = transformer(supply, 3200.0, 12500.0)
                val high = transformer(medium, 12500.0, 69000.0)
                val bus = transformer(high, 69000.0, 3200.0)
                val shaft = ShaftNetwork()
                val motors = List(count) { MotorElement(TransparentNode(), motorDescriptor) }
                val flywheels = List(28) { FlyWheelElement(TransparentNode(), flywheelDescriptor) }
                for (element in motors + flywheels) {
                    element.shaft = shaft
                    element.shaftConnectivity.forEach { shaft.parts.add(ShaftPart(element, it)) }
                }
                shaft.updateCache()
                var trials = 0
                for (motor in motors) {
                    motor.electricalLoadList.forEach(root::addState)
                    motor.electricalComponentList.forEach(root::addComponent)
                    root.addComponent(Resistor(bus, motor.wireLoad).apply { resistance = .01 })
                    root.addProcess(object : ConservativePowerProcess by motor.electricalProcess {
                        override fun rootSystemPreStepProcess() {
                            trials++
                            motor.electricalProcess.rootSystemPreStepProcess()
                        }
                    })
                }
                var physicalStep = 0
                var startupTrials = 0
                fun step() {
                    root.step()
                    for (motor in motors) {
                        assertFalse(motor.electricalProcess.faulted,
                            "target=$targetMode reuse=$reuse step=$physicalStep shaft=${shaft.rads} " +
                                "transformers=${transformerProcesses.map { it.status }} " +
                                "voltages=${listOf(supply.state, medium.state, high.state, bus.state)} " +
                                "motor=${motors.map { listOf(it.powerSource.voltage, it.powerSource.current) }} trials=$trials")
                        assertTrue(abs(motor.powerSource.current) <= motorDescriptor.driveCurrentLimit * 1.000001 + 1e-7)
                        motor.shaftProcess.process(.01)
                    }
                    assertTrue(shaft.energy.isFinite() && shaft.energy >= 0.0)
                    assertTrue(transformerProcesses.none { it.status == "NON_CONVERGENT" })
                    if (physicalStep == 0) startupTrials = trials
                    physicalStep++
                }
                repeat(150) { step() }
                assertTrue(shaft.rads > 0.0)
                assertTrue(supply.state > 3000.0 && high.state > 60_000.0 && bus.state > 3000.0)
                val result = listOf(shaft.energy, shaft.rads) + motors.flatMap {
                    listOf(it.powerSource.voltage, it.powerSource.current)
                }
                if (!reuse) baselineResults[targetMode] = result
                else result.indices.forEach { assertEquals(baselineResults.getValue(targetMode)[it], result[it], 1e-7) }
                println("SHAFT_CASCADE targetMode=$targetMode reuse=$reuse motors=$count flywheels=28 startupMotorTrials=$startupTrials physicalSteps=$physicalStep rads=${shaft.rads} reusedSolvers=${root.systems.sumOf { it.reusedMatrixSolvers }}")
            }
        } finally {
            if (previousReuse == null) System.clearProperty(reuseKey) else System.setProperty(reuseKey, previousReuse)
        }
    }
}
