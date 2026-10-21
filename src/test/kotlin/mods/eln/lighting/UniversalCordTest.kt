package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.cord.*
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class UniversalCordTest {
    @Test fun plugsMatchNominalDeviceVoltageInsteadOfGuessingFromBrownouts() {
        assertTrue(CordRating.V120.accepts(120.0));assertTrue(CordRating.V240.accepts(240.0))
        for(v in listOf(12.0,240.0,0.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(CordRating.V120.accepts(v))
        assertFalse(CordRating.V240.accepts(120.0))
        assertEquals("NEMA 5-20P",CordRating.V120.plug);assertEquals("NEMA 6-20P",CordRating.V240.plug)
        assertEquals("NEMA 5-20R",CordRating.V120.socket);assertEquals("NEMA 6-20R",CordRating.V240.socket)
    }
    @Test fun oldAnchorsStay120AndNewAnchorsPreserveVoltageAndIdentity() {
        val a=CordAnchor(0,BlockPos(1,80,9),UUID.randomUUID(),Direction.XP,CordRating.V240)
        assertEquals(a,CordAnchor.read(a.write()))
        val old=a.write().apply { remove("rating") }
        assertEquals(CordRating.V120,CordAnchor.read(old)!!.rating)
        assertEquals(a.id,CordAnchor.read(old)!!.id)
    }
    @Test fun allFixtureAndApplianceFamiliesOptIntoTheSharedCordLifecycle() {
        val classes=listOf(LampSocketElement::class.java,mods.eln.sixnode.EmergencyLampElement::class.java,
            mods.eln.transparentnode.kitchen.KitchenElement::class.java,mods.eln.transparentnode.climate.ClimateElement::class.java,
            mods.eln.transparentnode.fan.FanElement::class.java,mods.eln.transparentnode.floodlight.FloodlightElement::class.java,
            mods.eln.transparentnode.worklight.WorkLightElement::class.java,mods.eln.transparentnode.festive.FestiveElement::class.java,
            mods.eln.transparentnode.StreetLightWallElement::class.java,mods.eln.transparentnode.utilitylight.UtilityStreetLightElement::class.java,
            mods.eln.transparentnode.ups.UpsElement::class.java)
        classes.forEach { assertTrue(CordDevice::class.java.isAssignableFrom(it),it.name) }
        for(s in DecorativeLampStyle.entries) {
            val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(s)] as LampSocketDescriptor
            assertTrue(CordDevice::class.java.isAssignableFrom(d.ElementClass),s.itemName)
        }
        assertEquals(CordRating.V120,(Eln.transparentNodeItem.subItemList[32+(124 shl 6)] as OutletDescriptor).rating)
        assertEquals(CordRating.V240,(Eln.transparentNodeItem.subItemList[33+(124 shl 6)] as OutletDescriptor).rating)
        assertEquals(CordRating.V240,(Eln.sharedItem.getDescriptor(Eln.findItemStack("240 V Appliance Cord",1)) as CordDescriptor).rating)
    }
    @Test fun industrialCeilingFanHasFiveMetreMeshAndPassableClearance() {
        val s=DecorativeLampStyle.FAN_INDUSTRIAL
        val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(s)] as LampSocketDescriptor
        val m=Obj3D().apply { assertTrue(loadFile("${s.model}/${s.model}.obj")) }
        assertTrue(m.yDim>=4.9);assertTrue(m.zDim>=4.3);assertNotNull(m.getPart("Rotor"))
        assertEquals(300.0,s.fanWatts);assertTrue(s.fan)
        assertEquals(24,d.ghostGroup!!.elementList.size)
        assertTrue(d.ghostGroup!!.elementList.all { it.meta==mods.eln.ghost.GhostBlock.tLampClearance })
    }
    @Test fun ceilingHazardTouchesOnlyTheBladeDiskAndAllowsWalkingBelow() {
        val center=Vec3(1.5,85.0,1.5)
        assertTrue(CeilingFanHazard.touches(AABB(1.4,84.0,1.4,1.6,85.1,1.6),center))
        assertTrue(CeilingFanHazard.touches(AABB(3.9,84.9,1.4,4.1,85.1,1.6),center))
        assertFalse(CeilingFanHazard.touches(AABB(1.4,82.0,1.4,1.6,84.5,1.6),center))
        assertFalse(CeilingFanHazard.touches(AABB(3.9,84.9,3.9,4.1,85.1,4.1),center))
    }
    @Test fun plugMeshesIncludeGroundGripAndOppositeHorizontalBlade() {
        for(rating in CordRating.entries) {
            val name=if(rating==CordRating.V120)"nema520plug" else "nema620plug"
            val m=Obj3D().apply { assertTrue(loadFile("$name/$name.obj")) }
            for(part in listOf("Case","Grip","Blades","Badge"))assertNotNull(m.getPart(part))
            val stream=javaClass.classLoader.getResourceAsStream("assets/eln/model/$name/$name.obj")!!
            var active=false
            val vertices=stream.bufferedReader().useLines { lines -> lines.mapNotNull { line ->
                if(line.startsWith("o "))active=line=="o Blades"
                if(active && line.startsWith("v "))line.drop(2).split(" ").map(String::toDouble) else null
            }.toList() }
            fun dimensions(left: Boolean): Pair<Double,Double> {
                val v=vertices.filter { if(left)it[0]<-.02 else it[0]>.02 }
                return (v.maxOf { it[0] }-v.minOf { it[0] }) to (v.maxOf { it[1] }-v.minOf { it[1] })
            }
            val flat=dimensions(rating==CordRating.V120);val straight=dimensions(rating!=CordRating.V120)
            assertTrue(flat.first>flat.second*3);assertTrue(straight.second>straight.first*3)
            assertTrue(vertices.any { it[1]<-.15 && kotlin.math.abs(it[0])<.02 })
            val socket=if(rating==CordRating.V120)"appliancewalloutlet" else "walloutlet240"
            var slots=false
            val points=javaClass.classLoader.getResourceAsStream("assets/eln/model/$socket/$socket.obj")!!.bufferedReader().useLines { lines -> lines.mapNotNull { line ->
                if(line.startsWith("o "))slots=line=="o Black"
                if(slots && line.startsWith("v "))line.drop(2).split(" ").map(String::toDouble) else null
            }.filter { it[2]>-.37 && it[1] in -.09..0.0 }.toList() }
            val right=points.filter { it[0]>.012 }
            val width=right.maxOf { it[0] }-right.minOf { it[0] };val height=right.maxOf { it[1] }-right.minOf { it[1] }
            if(rating==CordRating.V120)assertTrue(height>width*3) else assertTrue(width>height*3)
            val left=points.filter { it[0]<-.012 }
            assertTrue(left.maxOf { it[0] }-left.minOf { it[0] }>.06)
            assertTrue(left.maxOf { it[1] }-left.minOf { it[1] }>.06)
        }
    }
}
