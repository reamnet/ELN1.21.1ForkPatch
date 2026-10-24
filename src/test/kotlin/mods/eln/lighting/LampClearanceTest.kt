package mods.eln.lighting

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.phys.shapes.CollisionContext
import org.junit.Test
import kotlin.test.*

class LampClearanceTest {
    @Test fun everyDecorativeFixtureReservesSpaceWithoutCreatingASolidMasterOrGhostCube() {
        val fixtures=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().filter { it.fixture!=null }
        assertEquals(88,fixtures.size)
        assertTrue(fixtures.any { it.hasGhostGroup() })
        for(d in fixtures) {
            assertFalse(d.hasVolume(),d.name)
            for(side in Direction.entries) for(front in LRDU.entries) {
                d.getGhostGroup(side,front)?.elementList?.forEach {
                    assertEquals(GhostBlock.tLampClearance,it.meta,d.name)
                    val state=Eln.ghostBlock.stateForMeta(it.meta)
                    assertTrue(state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty,d.name)
                    assertTrue(state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty,d.name)
                }
            }
        }
    }
    @Test fun newPassableClearanceKeepsOldMachineFloorAndLadderStateValues() {
        val block=Eln.ghostBlock
        fun shape(meta: Int)=block.stateForMeta(meta).getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty())
        assertEquals(0,GhostBlock.tCube);assertEquals(1,GhostBlock.tFloor);assertEquals(2,GhostBlock.tLadder)
        assertTrue(shape(GhostBlock.tCube).bounds().size==1.0)
        assertEquals(1.0/16,shape(GhostBlock.tFloor).bounds().ysize,1e-8)
        assertTrue(shape(GhostBlock.tLadder).isEmpty)
        assertTrue(shape(GhostBlock.tClearance).isEmpty)
        assertEquals(GhostBlock.tClearance,block.metaOfState(block.stateForMeta(GhostBlock.tClearance)))
        assertTrue(Eln.sixNodeItem.subItemList.values.any { it != null && it !is LampSocketDescriptor && it.hasVolume() })
    }
}
