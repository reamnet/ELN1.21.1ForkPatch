package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.*
import kotlin.test.*

class StreetLightControlsTest {
    private fun pole() = UtilityStreetLightElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<UtilityStreetLightDescriptor>().single())
    private fun bool(value: Boolean) = DataInputStream(ByteArrayInputStream(byteArrayOf(if (value) 1 else 0)))

    @Test fun duskSwitchingLeavesTheBaseOutputPoweredAndManualOffAlwaysWins() {
        val e = pole()
        e.inventory.setItem(0, Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single().newItemStack(1))
        e.inventoryChange(e.inventory)
        val root = RootSystem(.05, 1)
        e.electricalLoadList.forEach(root::addState); e.electricalComponentList.forEach(root::addComponent)
        root.addComponent(VoltageSource("grid", e.unit.primary, null).apply { voltage = 14000.0 })
        val baseLoad = Resistor(e.unit.secondary, null).apply { resistance = 240.0 }
        root.addComponent(baseLoad); root.addProcess(e.unit.controller)
        e.unit.packet(83, bool(true))
        fun sample(darkness: Int, lit: Boolean) {
            e.updateAmbientDarkness(darkness)
            repeat(8) { root.step() }
            assertEquals(120.0, e.unit.secondary.voltage, .1)
            assertTrue(baseLoad.power > 59.0)
            if (lit) assertTrue(e.lamp.power > 59.0) else assertTrue(e.lamp.power < 1e-6)
        }
        sample(0, false); sample(4, true); sample(3, true); sample(2, false); sample(11, true)
        e.unit.packet(82, bool(false)); sample(11, false)
        e.unit.packet(82, bool(true)); e.unit.packet(83, bool(false)); sample(0, true)
        e.unit.packet(81, bool(false))
        e.updateAmbientDarkness(11); repeat(8) { root.step() }
        assertFalse(e.unit.output.enabled); assertTrue(e.lamp.power < 1e-6)
    }

    @Test fun duskModePersistsAndOldSavesRetainManualLighting() {
        val original = pole().unit
        original.packet(83, bool(true))
        val saved = CompoundTag().also(original::write)
        val copy = pole().unit; copy.read(saved)
        assertTrue(copy.lampAutomatic)
        assertTrue(copy.lampEnabled)
        copy.read(CompoundTag().apply { putInt("mainsOutputVoltage", 240) })
        assertFalse(copy.lampAutomatic)
        assertTrue(copy.lampEnabled)
        copy.packet(83, DataInputStream(ByteArrayInputStream(byteArrayOf())))
        assertFalse(copy.lampAutomatic)
    }
}
