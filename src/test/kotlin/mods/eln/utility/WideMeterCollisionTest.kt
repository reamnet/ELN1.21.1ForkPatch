package mods.eln.utility

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import org.junit.Test
import kotlin.test.*

class WideMeterCollisionTest {
    @Test fun secondCabinetCellHasTheShallowShapeOnClientsWithoutAnyGhostOwnerLookup() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.wide }
        for(front in listOf(Direction.ZN,Direction.ZP,Direction.XN,Direction.XP)) {
            val reserved=d.getGhostGroupAt(Coordinate(0,0,0,0),front)!!.elementList.single()
            val right=front.right().toFacing()
            assertEquals(right.stepX,reserved.x);assertEquals(right.stepZ,reserved.z)
            val state=Eln.ghostBlock.stateForMeta(reserved.meta)
            val shape=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty())
            assertFalse(shape.isEmpty);val b=shape.bounds()
            assertEquals(.33,if(front==Direction.ZN || front==Direction.ZP)b.zsize else b.xsize,1e-9)
            assertFalse(b.contains(.5,.5,.5),"$front must not obstruct the open space in front of the can")
            assertEquals(b,state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty()).bounds())
            val owner=when(front) {
                Direction.ZP->AABB(.08,0.0,0.0,1.92,1.0,.33)
                Direction.ZN->AABB(-.92,0.0,.67,.92,1.0,1.0)
                Direction.XP->AABB(0.0,0.0,-.92,.33,1.0,.92)
                else->AABB(.67,0.0,.08,1.0,1.0,1.92)
            }
            val expected=owner.move(-right.stepX.toDouble(),0.0,-right.stepZ.toDouble()).intersect(AABB(0.0,0.0,0.0,1.0,1.0,1.0))
            assertEquals(expected.minX,b.minX,1e-9);assertEquals(expected.maxX,b.maxX,1e-9)
            assertEquals(expected.minZ,b.minZ,1e-9);assertEquals(expected.maxZ,b.maxZ,1e-9)
        }
    }
    @Test fun existingCubeAndClearanceShapeIdsKeepTheirOriginalMeaning() {
        val cube=Eln.ghostBlock.stateForMeta(GhostBlock.tCube)
        assertEquals(AABB(0.0,0.0,0.0,1.0,1.0,1.0),cube.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds())
        for(meta in listOf(GhostBlock.tLadder,GhostBlock.tClearance,GhostBlock.tLampClearance))
            assertTrue(Eln.ghostBlock.stateForMeta(meta).getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty)
    }
}
