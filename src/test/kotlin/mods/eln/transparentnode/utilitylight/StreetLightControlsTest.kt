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
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<UtilityStreetLightDescriptor>().single { it.style==PoleLampStyle.ORIGINAL })
    private fun bool(value: Boolean) = DataInputStream(ByteArrayInputStream(byteArrayOf(if (value) 1 else 0)))
    private fun movement(value: Int) = DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also {
        DataOutputStream(it).writeInt(value)
    }.toByteArray()))

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
        repeat(8) { e.unit.packet(84, movement(-1)); sample(0, true) }
        repeat(8) { e.unit.packet(84, movement(1)); sample(0, true) }
        for (turn in listOf(5, -1, -5, 1)) { e.unit.packet(85, movement(turn)); sample(0, true) }
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

    @Test fun repeatedHeightMovementsRespectTheLimitsAndRejectInvalidOrTruncatedPackets() {
        val unit = pole().unit
        assertEquals(0, unit.lampHeightStep)
        repeat(12) { unit.packet(84, movement(-1)) }
        assertEquals(StreetLightMount.MIN_STEP, unit.lampHeightStep)
        repeat(12) { unit.packet(84, movement(1)) }
        assertEquals(StreetLightMount.MAX_STEP, unit.lampHeightStep)
        for (invalid in listOf(0, 2, -2, Int.MAX_VALUE, Int.MIN_VALUE)) {
            unit.packet(84, movement(invalid))
            assertEquals(StreetLightMount.MAX_STEP, unit.lampHeightStep)
        }
        unit.packet(84, DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0))))
        assertEquals(StreetLightMount.MAX_STEP, unit.lampHeightStep)
        unit.packet(84, movement(-1))
        assertEquals(0, unit.lampHeightStep)
    }

    @Test fun savedHeightUsesTheExistingPositionForOldAndInvalidSaves() {
        val unit = pole().unit
        repeat(4) { unit.packet(84, movement(-1)) }
        val saved = CompoundTag().also(unit::write)
        val copy = pole().unit
        copy.read(saved)
        assertEquals(StreetLightMount.MIN_STEP, copy.lampHeightStep)
        assertEquals(1, StreetLightMount.lightY(copy.lampHeightStep))
        copy.read(CompoundTag())
        assertEquals(0, copy.lampHeightStep)
        assertEquals(2, StreetLightMount.lightY(copy.lampHeightStep))
        copy.read(CompoundTag().apply { putInt("mainsLampHeightStep", Int.MAX_VALUE) })
        assertEquals(0, copy.lampHeightStep)
    }

    @Test fun lampRotationWrapsSupportsFineStepsAndPersistsWithoutChangingHeight() {
        val unit = pole().unit
        unit.packet(84, movement(-1))
        for ((turn, expected) in listOf(-5 to 355, 1 to 356, 5 to 1, -1 to 0)) {
            unit.packet(85, movement(turn))
            assertEquals(expected, unit.lampYaw)
        }
        repeat(9) { unit.packet(85, movement(5)) }
        assertEquals(45, unit.lampYaw)
        assertEquals(-1, unit.lampHeightStep)
        for (invalid in listOf(0, 2, -2, 360, Int.MAX_VALUE, Int.MIN_VALUE)) {
            unit.packet(85, movement(invalid))
            assertEquals(45, unit.lampYaw)
        }
        unit.packet(85, DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0))))
        assertEquals(45, unit.lampYaw)
        val saved = CompoundTag().also(unit::write)
        val copy = pole().unit
        copy.read(saved)
        assertEquals(45, copy.lampYaw)
        assertEquals(-1, copy.lampHeightStep)
        copy.read(CompoundTag())
        assertEquals(0, copy.lampYaw)
        for (invalid in listOf(-1, 360, Int.MAX_VALUE)) {
            copy.read(CompoundTag().apply { putInt("mainsLampYaw", invalid) })
            assertEquals(0, copy.lampYaw)
        }
    }
}
