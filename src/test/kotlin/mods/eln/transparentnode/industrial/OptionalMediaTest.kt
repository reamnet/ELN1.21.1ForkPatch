package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.api.media.*
import mods.eln.node.transparent.TransparentNode
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class OptionalMediaTest {
    @Test fun coreKeepsHiddenSaveIdsWithoutAnyMediaProcessesOrBackend() {
        assertFalse(MediaAddons.available)
        assertEquals(8,IndustrialKind.WELDER.ordinal);assertEquals(9,IndustrialKind.entries.size)
        for(reserved in ReservedMediaDevice.entries) {
            val descriptor=Eln.transparentNodeItem.subItemList[reserved.legacyId] as MissingMediaDescriptor
            assertTrue(descriptor.isHidden());assertEquals(reserved.title,descriptor.name)
            val element=MissingMediaElement(TransparentNode(),descriptor)
            assertTrue(element.electricalLoadList.isEmpty());assertTrue(element.electricalComponentList.isEmpty());assertTrue(element.slowProcessList.isEmpty());assertFalse(element.hasGui())
        }
        assertEquals((126 shl 6)+9,ReservedMediaDevice.JUKEBOX.legacyId)
        assertEquals((126 shl 6)+10,ReservedMediaDevice.TV.legacyId)
    }
    @Test fun inertDevicePreservesUnknownLegacySettingsUrlAndCordStateAcrossSaves() {
        val original=CompoundTag().apply {
            putDouble("industrialSetting0",240.0);putDouble("industrialSetting1",.35)
            putString("mediaUrl","https://example.org/watch#saved");putBoolean("mediaRequested",true);putInt("mediaRevision",17)
            put("applianceCord",CompoundTag().apply { putString("token","preserved") })
            put("unknownFutureSetting",CompoundTag().apply { putLong("value",123456789L) })
        }
        val d=Eln.transparentNodeItem.subItemList[ReservedMediaDevice.TV.legacyId] as MissingMediaDescriptor
        val element=MissingMediaElement(TransparentNode(),d);element.readFromNBT(original)
        original.putString("mediaUrl","changed after load")
        val saved=CompoundTag().also(element::writeToNBT)
        assertEquals("https://example.org/watch#saved",saved.getString("mediaUrl"));assertEquals(240.0,saved.getDouble("industrialSetting0"))
        assertEquals("preserved",saved.getCompound("applianceCord").getString("token"));assertEquals(123456789L,saved.getCompound("unknownFutureSetting").getLong("value"))
        assertFalse(element.getItemStackNBT().contains("applianceCord"));assertTrue(saved.contains("applianceCord"))
    }
}
