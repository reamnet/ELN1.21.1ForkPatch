package mods.eln.transparentnode.solarpanel

import mods.eln.ghost.GhostGroup
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.math.*
import kotlin.test.*

class SolarRoofMountTest {
    @Test fun roofPitchRisesInEachStairDirectionAndKeepsItsMountingFootFixed() {
        val foot=Vec3(0.0,-.5,0.0)
        for(up in net.minecraft.core.Direction.Plane.HORIZONTAL) {
            val roof=SolarRoofMount(up)
            assertEquals(foot,roof.point(foot))
            val along=roof.vector(Vec3(up.stepX.toDouble(),0.0,up.stepZ.toDouble()))
            assertEquals(sqrt(.5),along.y,1e-12)
            assertEquals(sqrt(.5),along.x*up.stepX+along.z*up.stepZ,1e-12)
            assertEquals(1.0,along.length(),1e-12)
        }
    }

    @Test fun arrayAndReturnTerminalReserveTheUphillRoofCourseRatherThanReplacingStairs() {
        val flat=GhostGroup().apply { addRectangle(0,1,0,0,-1,1);removeElement(0,0,0) }
        for(up in net.minecraft.core.Direction.Plane.HORIZONTAL) {
            val roof=SolarRoofMount(up)
            val front=Direction.fromFacing(up.opposite)
            val footprint=assertNotNull(roof.footprint(flat,front))
            assertEquals(5,footprint.elementList.size)
            val high=footprint.elementList.filter { it.y==1 }
            assertEquals(3,high.size)
            assertTrue(high.all { it.x*up.stepX+it.z*up.stepZ==1 })
            val terminal=roof.groundOffset(Coordinate(1,0,0,0))
            val local=intArrayOf(terminal.x,terminal.y,terminal.z);front.rotateFromXN(local)
            assertTrue(high.any { it.x==local[0] && it.y==local[1] && it.z==local[2] })
        }
        assertEquals(0,SolarRoofMount().groundOffset(Coordinate(1,0,0,0)).y)
    }

    @Test fun productionUsesThePitchedPanelNormalAndTrackerProjectsOntoItsActualAxis() {
        val flat=SolarRoofMount()
        for(p in listOf(PI/4,PI/2,3*PI/4))for(s in listOf(.1,PI/2,PI-.1))
            assertEquals(max(0.0,cos(p-s)),flat.incidence(p,s),1e-12)
        val east=SolarRoofMount(net.minecraft.core.Direction.EAST)
        assertEquals(sqrt(.5),east.incidence(PI/2,PI/2),1e-12)
        val optimum=east.targetAlpha(PI/2)
        assertEquals(1.0,east.incidence(optimum,PI/2),1e-12)
        assertEquals(0.0,east.incidence(PI/2,PI+1))
        assertEquals(0.0,east.incidence(Double.NaN,1.0))
        val north=SolarRoofMount(net.minecraft.core.Direction.NORTH)
        assertEquals(sqrt(.5),north.incidence(north.targetAlpha(PI/2),PI/2),1e-12)
    }
    @Test fun roofOrientationAndManualAngleSurviveSavesWithoutInventingPitchForOldSaves() {
        val descriptor=mods.eln.Eln.transparentNodeItem.subItemList.values.filterIsInstance<SolarPanelDescriptor>().first()
        for(up in net.minecraft.core.Direction.Plane.HORIZONTAL) {
            val original=SolarPanelElement(mods.eln.node.transparent.TransparentNode(),descriptor)
            original.roofMount=SolarRoofMount(up);original.roofMountKnown=true
            val tag=net.minecraft.nbt.CompoundTag().also(original::writeToNBT)
            val copy=SolarPanelElement(mods.eln.node.transparent.TransparentNode(),descriptor).also { it.readFromNBT(tag) }
            assertTrue(copy.roofMountKnown);assertEquals(original.roofMount,copy.roofMount)
        }
        val old=SolarPanelElement(mods.eln.node.transparent.TransparentNode(),descriptor)
        old.readFromNBT(net.minecraft.nbt.CompoundTag())
        assertTrue(old.roofMountKnown);assertEquals(SolarRoofMount(),old.roofMount);assertEquals(PI/2,old.panelAlpha)
        assertEquals(SolarRoofMount(),SolarRoofMount.fromId(100))
    }
}
