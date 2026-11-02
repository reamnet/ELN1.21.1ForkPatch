package mods.eln.vehicle
import mods.eln.Eln
import mods.eln.api.media.ReservedMediaDevice
import org.junit.Test
import kotlin.test.*
class RoadVehicleTest {
    @Test fun normalTurnsAndStraightTravelDoNotTriggerTheCartHazard() {
        assertFalse(RoadPhysics.dangerousCorner(4.0,1.5));assertFalse(RoadPhysics.dangerousCorner(10.0,0.0));assertFalse(RoadPhysics.dangerousCorner(10.0,.5));assertTrue(RoadPhysics.dangerousCorner(10.0,1.25));assertTrue(RoadPhysics.dangerousCorner(-10.0,-1.25))
    }
    @Test fun chargingCannotCreateEnergyOverfillOrExceedPackRating() {
        assertEquals(576.0,RoadPhysics.storedCharge(0.0,72000000.0,11520.0,1200.0,.05),1e-9)
        assertEquals(1.0,RoadPhysics.storedCharge(71999999.0,72000000.0,11520.0,1200.0,.05),1e-9)
        assertEquals(0.0,RoadPhysics.storedCharge(72000000.0,72000000.0,11520.0,1200.0,.05),1e-9)
        assertEquals(576.0,RoadPhysics.storedCharge(0.0,72000000.0,11520.0,100000.0,.05),1e-9)
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-100.0))assertEquals(0.0,RoadPhysics.storedCharge(0.0,72000000.0,11520.0,v,.05),1e-9)
    }
    @Test fun chargerCollisionFollowsItsPedestalRatherThanAnInvisibleFullCube() {
        val shapes=mutableListOf<net.minecraft.world.phys.AABB?>()
        VehicleChargerDescriptor().addCollisionBoxesToList(net.minecraft.world.phys.AABB(-1.0,-1.0,-1.0,2.0,2.0,2.0),shapes,null,0,0,0)
        assertEquals(2,shapes.size);assertFalse(shapes.any { it!!.contains(.5,.5,.9) });assertTrue(shapes.any { it!!.contains(.5,1.5,.5) })
    }
    @Test fun originalModelsHavePassengerBodyWheelsAndReadouts() {
        for(name in listOf("electric_golf_cart","arc_runner_coupe")) {
            val m=Eln.obj.getObj(name)
            for(part in listOf("Body","Seats","Frame","Roof","Headlights","BatteryDisplay","Wheel0","Wheel1","Wheel2","Wheel3"))assertNotNull(m.getPart(part),"$name/$part")
        }
        assertEquals(1,RoadVehicleKind.CART.packs);assertEquals(4,RoadVehicleKind.CART.seats);assertEquals(6,RoadVehicleKind.COUPE.packs);assertEquals(2,RoadVehicleKind.COUPE.seats)
        assertEquals(13,ReservedMediaDevice.FLOOR_SPEAKER.subId);assertEquals(14,ReservedMediaDevice.CEILING_SPEAKER.subId)
        assertEquals("Four Cell Plug-in UPS",Eln.transparentNodeItem.subItemList[12+(126 shl 6)]?.name)
    }
}
