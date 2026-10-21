package mods.eln.transparentnode.windturbine

import mods.eln.Eln
import mods.eln.misc.WindProcess
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyWindTurbineTest {
    private fun element() = WindTurbineElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
        .filterIsInstance<WindTurbineDescriptor>().single())

    private class Circuit(initialRads: Double = 0.0, ohms: Double = 20.0) {
        val e = LegacyWindTurbineTest().element()
        val root = RootSystem(.01, 1)
        val rotor = e.slowProcess
        val load = Resistor(e.electricalLoadList.single(), null).apply { resistance = ohms }
        init {
            rotor.readFromNBT(CompoundTag().apply { putDouble("slowProcessrotorRads", initialRads) }, "")
            e.electricalLoadList.forEach(root::addState)
            e.electricalComponentList.forEach(root::addComponent)
            root.addComponent(load); root.addProcess(e.electricalProcess)
        }
        fun step(wind: Double) {
            val before = rotor.rotorEnergy
            root.step()
            val power = e.powerSource.power
            assertTrue(power >= -1e-7, "Wind generator absorbed reverse power: $power")
            assertTrue(power * .01 <= before * e.descriptor.generationEfficiency + 1e-7,
                "Exported $power W without sufficient kinetic energy: $before J")
            rotor.advanceRotor(.01, wind)
            assertEquals(before + rotor.lastAerodynamicPower * .01,
                rotor.rotorEnergy + (power + rotor.lastLossPower) * .01, 1e-7 + before * 1e-9)
            assertTrue(rotor.rotorRads.isFinite() && rotor.rotorRads in 0.0..rotor.maximumRotorRads())
            assertTrue(kotlin.math.abs(e.powerSource.current) <= e.descriptor.regulatorCurrentLimit * 1.000001 + 1e-7)
        }
    }

    @Test fun windSpeedScalesWithHeightInsteadOfBecomingTheAltitude() {
        val wind = WindProcess().apply { windHit = 5.0 }
        assertEquals(5.0, wind.getWind(100), 1e-12)
        assertEquals(3.2, wind.getWind(64), 1e-12)
        assertEquals(10.0, wind.getWind(200), 1e-12)
        assertEquals(0.0, wind.getWind(-64), 1e-12)
        wind.windHit = 30.0
        assertEquals(96.0, wind.getWind(320), 1e-12)
        wind.windHit = Double.NaN
        assertEquals(0.0, wind.getWind(100))
    }

    @Test fun anOldOverspeedSaveCannotRestoreAnUnboundedRotor() {
        val e = element()
        val process = WindTurbineSlowProcess("slowProcess", e)
        val maximum = e.descriptor.nominalRotorRads * e.descriptor.maxVoltage / e.descriptor.nominalVoltage
        for (rads in listOf(1e6, Double.NaN, Double.POSITIVE_INFINITY, -20.0)) {
            process.readFromNBT(CompoundTag().apply { putDouble("slowProcessrotorRads", rads) }, "")
            assertTrue(process.rotorRads.isFinite() && process.rotorRads in 0.0..maximum,
                "Invalid saved rotor speed remained ${process.rotorRads}")
        }
    }

    @Test fun anUnloadedRotorIsGovernedAtLowNormalAndStrongWind() {
        for (wind in listOf(3.1, 12.0, 21.9)) {
            val e = element(); val rotor = e.slowProcess
            repeat(12000) {
                rotor.advanceRotor(.05, wind)
                assertTrue(rotor.rotorRads <= rotor.governedRotorRads(wind) + 1e-6,
                    "wind=$wind rotor=${rotor.rotorRads}")
            }
            assertTrue(rotor.rotorRads > 0.0)
        }
    }

    @Test fun calmAndCutoutWindsBrakeInsteadOfAddingRotorEnergy() {
        for (wind in listOf(0.0, 2.9, 22.0, 100.0)) {
            val c = Circuit(48.0, 1e9)
            var before = c.rotor.rotorEnergy
            repeat(12000) {
                c.step(wind)
                assertEquals(0.0, c.rotor.lastAerodynamicPower)
                assertTrue(c.rotor.rotorEnergy <= before)
                before = c.rotor.rotorEnergy
            }
            assertEquals(0.0, c.rotor.rotorRads, 1e-8)
            assertEquals(0.0, c.e.powerSource.power, 1e-8)
        }
    }

    @Test fun electricalOutputIsPaidAtEveryStepAcrossLoadsAndShortCircuits() {
        val c = Circuit()
        repeat(6000) { c.step(12.0) }
        assertTrue(c.load.power > 100.0, "Turbine never started under a normal load")
        for (ohms in listOf(1e9, .001, 20.0, 1000.0, .01, 20.0)) {
            c.load.resistance = ohms
            repeat(600) { c.step(12.0) }
        }
        assertTrue(c.load.power > 10.0, "Turbine did not recover after opening/shorting the circuit")
    }

    @Test fun aHigherVoltageGridCannotMotorOrChargeTheWindRotor() {
        val c = Circuit(48.0)
        val grid = VoltageState(); c.root.addState(grid)
        c.root.addComponent(VoltageSource("live grid", grid, null).apply { voltage = 480.0 })
        c.root.addComponent(Resistor(grid, c.e.electricalLoadList.single()).apply { resistance = .01 })
        val before = c.rotor.rotorEnergy
        repeat(200) { c.step(0.0); assertEquals(0.0, c.e.powerSource.power, 1e-8) }
        assertTrue(c.rotor.rotorEnergy < before)
    }

    @Test fun algebraicRetriesCannotAdvanceRotorEnergyOrTheVoltageRamp() {
        val c = Circuit(48.0)
        c.root.generate(); c.e.electricalProcess.beginStep(.01); c.e.electricalProcess.prepareStep()
        val before = c.rotor.rotorEnergy
        val target = c.e.electricalProcess.voltageTarget
        repeat(100) { c.e.electricalProcess.rootSystemPreStepProcess() }
        assertEquals(before, c.rotor.rotorEnergy)
        assertEquals(target, c.e.electricalProcess.voltageTarget)
    }

    @Test fun aSavedRotorResumesThroughTheRegisteredElectricalAccountingCallback() {
        val c = Circuit()
        repeat(4000) { c.step(12.0) }
        val saved = CompoundTag()
        c.rotor.writeToNBT(saved, ""); c.e.powerSource.writeToNBT(saved, "")
        saved.putDouble("slowProcessenvironementWindFactor", 1.0)
        val restored = Circuit()
        restored.rotor.readFromNBT(saved, ""); restored.e.powerSource.readFromNBT(saved, "")
        assertEquals(c.rotor.rotorEnergy, restored.rotor.rotorEnergy, 1e-8)
        restored.e.node!!.coordinate = Coordinate(0, 100, 0, 0)
        val previousWind = Eln.wind
        try {
            Eln.wind = WindProcess().apply { windHit = 12.0 }
            repeat(200) {
                val before = restored.rotor.rotorEnergy
                restored.root.step()
                restored.e.electricalProcessList.forEach { it.process(.01) }
                assertEquals(before + restored.rotor.lastAerodynamicPower * .01,
                    restored.rotor.rotorEnergy + (restored.e.powerSource.power + restored.rotor.lastLossPower) * .01,
                    1e-6)
            }
            assertTrue(restored.load.power > 100.0)
        } finally { Eln.wind = previousWind }
    }
}
