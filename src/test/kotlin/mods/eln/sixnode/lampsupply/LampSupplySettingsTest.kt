package mods.eln.sixnode.lampsupply

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.six.SixNode
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketElement
import mods.eln.sixnode.lampsocket.LampSocketProcess
import net.minecraft.nbt.CompoundTag
import java.io.*
import kotlin.test.*

class LampSupplySettingsTest {
    private fun supply(): LampSupplyElement {
        val descriptor = Eln.sixNodeItem.getDescriptor(Eln.findItemStack("120V Lamp Supply", 1)) as LampSupplyDescriptor
        val node = SixNode().apply { coordinate = Coordinate(0, 64, 0, 0) }
        return LampSupplyElement(node, Direction.YN, descriptor)
    }
    private fun packet(action: DataOutputStream.() -> Unit): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(action)
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
    private fun range(supply: LampSupplyElement, range: Int) {
        supply.networkUnserialize(packet { writeByte(LampSupplyElement.setTransmissionRange.toInt()); writeInt(range) })
    }

    @Test fun rangePacketsAndSavesAreBoundedAndIncludeTheBoundary() {
        val supply = supply()
        range(supply, 128)
        assertTrue(supply.isInRange(Coordinate(128, 64, 0, 0)))
        assertFalse(supply.isInRange(Coordinate(129, 64, 0, 0)))
        assertFalse(supply.isInRange(Coordinate(0, 64, 0, -1)))
        range(supply, Int.MAX_VALUE)
        assertEquals(128, supply.range)
        val tag = CompoundTag()
        supply.writeToNBT(tag)
        val loaded = supply()
        loaded.readFromNBT(tag)
        assertEquals(128, loaded.range)
    }

    @Test fun wirelessControlNamesDoNotAdvertisePowerChannelsAndBadIndexesAreIgnored() {
        val supply = supply()
        val control = "test-lamp-control-${System.nanoTime()}"
        supply.networkUnserialize(packet { writeByte(2); writeByte(0); writeUTF(control) })
        assertFalse(LampSupplyElement.channelMap.containsKey(control))
        supply.networkUnserialize(packet { writeByte(1); writeByte(255); writeUTF("bad") })
        supply.networkUnserialize(packet { writeByte(3); writeByte(255); writeByte(255) })
        assertEquals(3, supply.entries.size)
    }

    @Test fun cachedLampSupplyIsDroppedWhenRangeShrinks() {
        val supply = supply()
        val channel = "test-lamp-range-${System.nanoTime()}"
        supply.networkUnserialize(packet { writeByte(1); writeByte(0); writeUTF(channel) })
        try {
            range(supply, 128)
            val descriptor = Eln.sixNodeItem.getDescriptor(Eln.findItemStack("Classic Lamp Socket", 1)) as LampSocketDescriptor
            val socket = LampSocketElement(SixNode().apply { coordinate = Coordinate(64, 64, 0, 0) }, Direction.YN, descriptor)
            socket.lampSupplyChannel = channel
            val process = LampSocketProcess(socket)
            val search = LampSocketProcess::class.java.getDeclaredMethod("findBestLampSupply", Coordinate::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            val cached = LampSocketProcess::class.java.getDeclaredField("cachedBestChannelHandle").apply { isAccessible = true }
            search.invoke(process, socket.coordinate, false)
            assertNotNull(cached.get(process))
            range(supply, 32)
            search.invoke(process, socket.coordinate, false)
            assertNull(cached.get(process))
        } finally { supply.unload() }
    }
}
