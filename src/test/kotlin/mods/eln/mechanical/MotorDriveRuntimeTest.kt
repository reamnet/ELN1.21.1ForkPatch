package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.ConservativePowerProcess
import kotlin.math.abs
import kotlin.test.*

class MotorDriveRuntimeTest {
    private class Circuit(samples: Int, extraTrials: Int) {
        val root = RootSystem(.01, samples)
        val motor = MotorElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<MotorDescriptor>().single { it.name == "Large Shaft Motor" })
        val bus = VoltageState()
        val source = VoltageSource("supply", bus, null).apply { voltage = motor.desc.nominalU.toDouble() }
        init {
            motor.shaft = ShaftNetwork(motor, motor.shaftConnectivity.iterator()).apply { _mass = motor.desc.shaftMass }
            root.addState(bus); root.addComponent(source)
            motor.electricalLoadList.forEach(root::addState)
            motor.electricalComponentList.forEach(root::addComponent)
            root.addComponent(Resistor(bus, motor.wireLoad).apply { resistance = .1 })
            root.addProcess(object : ConservativePowerProcess by motor.electricalProcess {
                override fun rootSystemPreStepProcess() {
                    repeat(extraTrials + 1) { motor.electricalProcess.rootSystemPreStepProcess() }
                }
            })
        }
    }

    @Test fun extraAlgebraicTrialsAndLegacySamplingDoNotAdvanceTheDriveClock() {
        val a = Circuit(1, 0); val b = Circuit(50, 7)
        repeat(120) { step ->
            a.root.step(); b.root.step()
            assertFalse(a.motor.electricalProcess.faulted || b.motor.electricalProcess.faulted)
            assertEquals(a.motor.powerSource.current, b.motor.powerSource.current, 1e-8, "step=$step")
            assertEquals(a.motor.powerSource.voltage, b.motor.powerSource.voltage, 1e-8, "step=$step")
            a.motor.shaftProcess.process(.01); b.motor.shaftProcess.process(.01)
            assertEquals(a.motor.shaft.energy, b.motor.shaft.energy, 1e-7, "step=$step")
        }
    }

    @Test fun coastingAtTheSupplySpeedOpensTheIdleDriveWithoutFaulting() {
        val c = Circuit(50, 0)
        c.motor.shaft.rads = c.motor.desc.nominalRads.toDouble()
        repeat(30) {
            c.root.step()
            assertFalse(c.motor.electricalProcess.faulted)
            assertFalse(c.motor.powerSource.enabled)
            assertEquals(0.0, c.motor.powerSource.current, 0.0)
            assertEquals(0.0, c.motor.powerSource.power, 0.0)
            assertEquals(c.source.voltage, c.motor.wireLoad.voltage, 1e-7)
        }
    }

    @Test fun runtimeDriveStartsAndRegeneratesWithinItsCurrentAndEnergyLimits() {
        val c = Circuit(50, 0)
        repeat(1200) {
            c.root.step()
            assertFalse(c.motor.electricalProcess.faulted)
            assertTrue(abs(c.motor.powerSource.current) <= c.motor.desc.driveCurrentLimit * 1.000001 + 1e-7)
            c.motor.shaftProcess.process(.01)
        }
        assertTrue(c.motor.shaft.rads > c.motor.desc.nominalRads * .75)
        c.source.voltage *= .5
        repeat(30) {
            c.motor.shaft.rads = c.motor.desc.nominalRads.toDouble()
            val before = c.motor.shaft.energy
            c.root.step()
            assertFalse(c.motor.electricalProcess.faulted)
            assertTrue(c.motor.powerSource.power > 0.0)
            assertTrue(c.motor.powerSource.power <= before / .01 * c.motor.desc.generationEfficiency + 1e-6)
            assertTrue(abs(c.motor.powerSource.current) <= c.motor.desc.driveCurrentLimit * 1.000001 + 1e-7)
            c.motor.thermal.PcTemp = 0.0
            c.motor.shaftProcess.process(.01)
            assertEquals((before - c.motor.shaft.energy) / .01,
                c.motor.powerSource.power + c.motor.thermal.PcTemp, .01)
        }
    }
}
