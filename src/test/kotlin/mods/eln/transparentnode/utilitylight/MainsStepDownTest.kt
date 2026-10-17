package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.*

class MainsStepDownTest {
    private class Circuit(volts: Double, target: Int, loadOhms: Double) {
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
        val element = StepDownUnitElement(TransparentNode(), descriptor)
        val unit = element.unit
        val root = RootSystem(.01, 1)
        val feed = VoltageState()
        val supply = VoltageSource("grid", feed, null).apply { voltage = volts }
        val feedWire = Resistor(feed, unit.primary).apply { resistance = .01 }
        val load = Resistor(unit.secondary, null).apply { resistance = loadOhms }
        init {
            unit.read(CompoundTag().apply { putInt("mainsOutputVoltage", target) })
            root.addState(feed)
            element.electricalLoadList.forEach(root::addState)
            element.electricalComponentList.forEach(root::addComponent)
            listOf(supply, feedWire, load).forEach(root::addComponent)
            root.addProcess(unit.controller)
        }
        fun settle() { repeat(12) { root.step() } }
    }

    @Test fun bothMainsVoltagesWorkAcrossTheSupportedGridRangeAndConservePower() {
        for (target in listOf(120, 240)) for (grid in listOf(240.0, 800.0, 3200.0, 12800.0, 51200.0)) {
            // A step-down input needs some headroom for the feed and winding resistance.
            if (grid <= target) continue
            val c = Circuit(grid, target, target.toDouble() * target / 500.0)
            c.settle()
            assertTrue(c.unit.output.enabled, "Failed at $grid V -> $target V: ${c.unit.controller.status}")
            assertEquals(target.toDouble(), c.unit.secondary.voltage, .1)
            assertTrue(c.load.power > 490.0)
            assertEquals(-c.unit.input.power * MainsStepDown.EFFICIENCY, c.unit.output.power, .001)
            assertTrue(c.load.power <= c.supply.power * MainsStepDown.EFFICIENCY + .001)
        }
    }

    @Test fun undervoltageOvervoltageAndDisableOpenTheBranches() {
        for (volts in listOf(50.0, 100.0, 60000.0)) {
            val c = Circuit(volts, 120, 240.0)
            c.settle()
            assertFalse(c.unit.input.enabled)
            assertFalse(c.unit.output.enabled)
            assertEquals(0.0, c.load.power, 1e-8)
            assertEquals(volts, c.unit.primary.voltage, 1e-6)
        }
        val c = Circuit(3200.0, 240, 960.0)
        c.settle()
        c.unit.read(CompoundTag().apply { putInt("mainsOutputVoltage", 240); putBoolean("mainsEnabled", false) })
        c.settle()
        assertFalse(c.unit.output.enabled)
        assertEquals(0.0, c.load.power, 1e-8)
    }

    @Test fun overloadRespectsCurrentAndPowerLimits() {
        for (resistance in listOf(.01, 1.0)) {
            val c = Circuit(3200.0, 120, resistance)
            c.settle()
            // At 0.01 ohm even the minimum supported gain would exceed the current rating:
            // remaining open is the correct protection response, rather than forcing output.
            if (resistance == 1.0) assertTrue(c.unit.output.enabled)
            assertTrue(c.load.current <= MainsStepDown.limits(120).maxOutputAmps * 1.000001)
            assertTrue(c.unit.output.power <= MainsStepDown.MAX_OUTPUT_WATTS + .001)
            assertTrue(c.load.power <= c.supply.power * MainsStepDown.EFFICIENCY + .001)
        }
    }

    @Test fun tenKilowattRatingHasUsableCurrentAndCoolingAtBothOutputVoltages() {
        for (target in listOf(120, 240)) for (grid in listOf(target * 2.0, 3200.0, 12800.0)) {
            val c = Circuit(grid, target, target.toDouble() * target / 9900.0)
            c.settle()
            assertTrue(c.load.power > 9500.0, "$grid V -> $target V: ${c.unit.controller.status}, ${c.load.power} W")
            assertTrue(c.unit.output.power <= MainsStepDown.MAX_OUTPUT_WATTS * 1.000001)
            assertTrue(-c.unit.input.current <= MainsStepDown.MAX_INPUT_AMPS * 1.000001)
            c.unit.thermal.PcTemp = 0.0
            c.element.electricalProcessList.forEach { it.process(.01) }
            val losses = c.unit.thermal.PcTemp
            assertTrue(losses > 490.0)
            assertTrue(losses * c.unit.thermal.Rp < 100.0, "Rated load would overheat continuously")
            assertTrue(c.load.power <= c.supply.power * MainsStepDown.EFFICIENCY + .001)
        }
    }

    @Test fun anIdleOutputDoesNotClampTheHighVoltageLineOrRepeatedlySwitchItsInput() {
        val c = Circuit(12800.0, 120, 1e19)
        c.settle()
        assertFalse(c.unit.input.enabled)
        assertTrue(c.unit.output.enabled)
        assertEquals(120.0, c.unit.secondary.voltage, 1e-7)
        val before = c.root.systems.sumOf { it.fullFactorizations + it.updatedFactorizations }
        repeat(20) { c.root.step(); assertFalse(c.unit.input.enabled) }
        assertEquals(before, c.root.systems.sumOf { it.fullFactorizations + it.updatedFactorizations })
        c.load.resistance = 240.0
        c.settle()
        assertTrue(c.unit.input.enabled)
        assertTrue(c.load.power > 59.0)
    }

    @Test fun settingsSurviveItemAndSaveRoundTripsAndRejectInvalidPackets() {
        val c = Circuit(3200.0, 240, 960.0)
        val item = c.element.getItemStackNBT()
        val second = StepDownUnitElement(TransparentNode(), c.descriptor)
        second.readItemStackNBT(item)
        assertEquals(240, second.unit.outputVoltage)
        val saved = CompoundTag().also(second.unit::write)
        c.unit.read(saved)
        assertEquals(240, c.unit.outputVoltage)
        c.unit.packet(80, DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0))))
        assertEquals(240, c.unit.outputVoltage)
        c.unit.packet(80, DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0, 0, 42))))
        assertEquals(240, c.unit.outputVoltage)
        assertTrue(isStepDownUnit(c.descriptor.newItemStack(1)))
        assertFalse(isStepDownUnit(Eln.findItemStack("Utility Pole", 1)))
    }
}
