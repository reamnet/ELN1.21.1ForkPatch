package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.festive.*
import mods.eln.transparentnode.kitchen.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class FairyAndCordLifecycleTest {
    @Test fun fairyStringsUseExactAnchorsSagAndCloselySpacedBulbs() {
        val a=Vec3(1.0,4.0,-2.0);val b=Vec3(7.0,4.0,-2.0)
        val p=FairyLightPath.points(a,b)
        assertEquals(a,p.first());assertEquals(b,p.last());assertTrue(p[p.size/2].y<3.2)
        assertTrue(p.size>=60);assertTrue(p.zipWithNext().all { (x,y)->x.distanceTo(y)<.16 })
        assertTrue(FairyLightPath.points(a,a.add(9.0,0.0,0.0)).isEmpty())
        assertTrue(FairyLightPath.points(a,Vec3(Double.NaN,4.0,0.0)).isEmpty())
        for(id in 58..59)assertTrue(Eln.transparentNodeItem.subItemList[(124 shl 6)+id] is FairyLightsDescriptor)
    }
    @Test fun deckGlobeStringsKeepExactAnchorsAndSpaceLargerBulbsApart() {
        val points=FairyLightPath.points(Vec3(0.0,5.0,0.0),Vec3(6.0,5.0,0.0))
        val bulbs=FairyLightPath.bulbPoints(points,true)
        assertEquals(points.first(),bulbs.first());assertEquals(points.last(),bulbs.last())
        assertTrue(bulbs.size in 12..16);assertTrue(bulbs.size<points.size/3)
        assertTrue(bulbs.zipWithNext().dropLast(1).all { (a,b)->a.distanceTo(b) in .45..0.62 })
        for(id in 6..7) {
            val d=Eln.transparentNodeItem.subItemList[(125 shl 6)+id] as FairyLightsDescriptor
            assertTrue(d.largeBulbs);assertEquals(id==7,d.colored)
            val e=FairyLightsElement(TransparentNode(),d)
            assertTrue(e.acceptsCord(CordRating.V120));assertTrue(e.acceptsCord(CordRating.V240));assertFalse(e.acceptsCord(CordRating.V3200))
        }
    }
    @Test fun treeCandleAndStoveAcceptTheirAppropriateCordsAndRetainHolidayChannels() {
        for(d in Eln.transparentNodeItem.subItemList.values.filterIsInstance<TransparentNodeDescriptor>().filter { it.ElementClass==FestiveElement::class.java }) {
            val e=FestiveElement(TransparentNode(),d)
            assertTrue(e.acceptsCord(CordRating.V120));assertTrue(e.acceptsCord(CordRating.V240))
            e.powerChannel="Porch Christmas";val tag=CompoundTag().also(e::writeToNBT)
            val copy=FestiveElement(TransparentNode(),d).also { it.readFromNBT(tag) };assertEquals(e.powerChannel,copy.powerChannel)
        }
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<KitchenDescriptor>().single { it.type==KitchenType.STOVE }
        val stove=KitchenElement(TransparentNode(),d)
        assertTrue(stove.acceptsCord(CordRating.V240));assertFalse(stove.acceptsCord(CordRating.V120));assertTrue(stove.cordPortable)
    }
    @Test fun recessedOutletsKeepTheWallAndMoveThePlugOutsideItsFace() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<OutletDescriptor>().single { it.rating==CordRating.V120 && !it.strip }
        val e=OutletElement(TransparentNode(),d)
        val wall=net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState()
        e.readItemStackNBT(CompoundTag().apply { put("outletWall",net.minecraft.nbt.NbtUtils.writeBlockState(wall)) })
        assertTrue(e.recessed);assertEquals(wall,e.wallState)
        val tag=CompoundTag().also(e::writeToNBT);val copy=OutletElement(TransparentNode(),d).also { it.readFromNBT(tag) }
        assertEquals(wall,copy.wallState)
        val normal=OutletGeometry.tipLocal(0,false,false);val flush=OutletGeometry.tipLocal(0,false,false,true)
        assertEquals(1.0,flush.z-normal.z,1e-9)
    }
}
