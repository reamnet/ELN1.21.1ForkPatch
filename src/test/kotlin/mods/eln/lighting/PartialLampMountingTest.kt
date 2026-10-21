package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.sixnode.lampsocket.*
import net.minecraft.world.phys.AABB
import org.junit.Test
import kotlin.test.*

class PartialLampMountingTest {
    @Test fun slabsStairsAndNarrowTablesUseTheirActualCentralSurface() {
        val bottom=listOf(AABB(0.0,0.0,0.0,1.0,.5,1.0))
        val top=listOf(AABB(0.0,.5,0.0,1.0,1.0,1.0))
        assertEquals(.5,LampMounting.surface(bottom,Direction.YN))
        assertEquals(0.0,LampMounting.surface(bottom,Direction.YP))
        assertEquals(1.0,LampMounting.surface(top,Direction.YN))
        assertEquals(.5,LampMounting.surface(top,Direction.YP))
        assertEquals(1.5,LampMounting.surface(listOf(AABB(.375,0.0,.375,.625,1.5,.625)),Direction.YN))
        assertNull(LampMounting.surface(emptyList(),Direction.YN))
        assertNull(LampMounting.surface(listOf(AABB(0.0,0.0,0.0,.2,1.0,.2)),Direction.YP))
    }
    @Test fun ceilingSocketsAndEveryTableStyleAllowPartialSupportsWithoutChangingWireRules() {
        val sockets=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>()
        for(socket in sockets) {
            assertTrue(LampMounting.partialAllowed(socket,Direction.YP))
            assertEquals(socket.fixture?.tableLamp==true,LampMounting.partialAllowed(socket,Direction.YN))
        }
        assertFalse(LampMounting.partialAllowed(Eln.instance.highVoltageCableDescriptor,Direction.YP))
        assertEquals(setOf(DecorativeLampStyle.DESK,DecorativeLampStyle.TABLE_DRUM,DecorativeLampStyle.TABLE_BANKER,
            DecorativeLampStyle.TABLE_TASK,DecorativeLampStyle.WINDOW_CANDLE,DecorativeLampStyle.HALLOWEEN_JACK,DecorativeLampStyle.DECK_POST_CAP),
            DecorativeLampStyle.entries.filter { it.tableLamp }.toSet())
    }
}
