package mods.eln.transparentnode.kitchen

import org.junit.Test
import kotlin.test.*

class RefrigeratorThermalTest {
    @Test fun browsingWarmsEvenWhileTheCompressorRuns() {
        val closed=RefrigeratorThermal.advance(4.0,20.0,1.0,false,20.0)
        val open=RefrigeratorThermal.advance(4.0,20.0,1.0,true,20.0)
        assertEquals(3.5,closed)
        assertTrue(open>10.0 && open<20.0)
    }
    @Test fun closingTheDoorLetsPoweredStorageBecomeColdAgain() {
        var t=RefrigeratorThermal.advance(4.0,20.0,1.0,true,20.0)
        repeat(30) { t=RefrigeratorThermal.advance(t,20.0,1.0,false,1.0) }
        assertTrue(t<=5.0)
    }
    @Test fun openingAnUnpoweredFridgeAcceleratesHeatGainWithoutExceedingRoomTemperature() {
        val closed=RefrigeratorThermal.advance(4.0,25.0,0.0,false,30.0)
        val open=RefrigeratorThermal.advance(4.0,25.0,0.0,true,30.0)
        assertTrue(closed<5.0)
        assertTrue(open>23.0 && open<25.0)
    }
    @Test fun hotAndColdRoomsAffectTheInterior() {
        assertTrue(RefrigeratorThermal.advance(4.0,38.0,1.0,true,30.0)>25.0)
        assertEquals(3.5,RefrigeratorThermal.advance(4.0,-10.0,0.0,true,30.0))
    }
    @Test fun PhysicalTimeDoesNotDependOnTheNumberOfSimulationSubsteps() {
        val single=RefrigeratorThermal.advance(4.0,20.0,.6,true,24.0)
        var split=4.0
        repeat(480) { split=RefrigeratorThermal.advance(split,20.0,.6,true,.05) }
        assertEquals(single,split,1e-10)
    }
    @Test fun ALongTickCannotOvershootTheRoomOrInventCompressorEnergy() {
        assertEquals(40.0,RefrigeratorThermal.advance(4.0,40.0,0.0,true,10000.0))
        assertEquals(3.5,RefrigeratorThermal.advance(20.0,20.0,1.0,false,10000.0))
        assertEquals(4.0,RefrigeratorThermal.advance(4.0,20.0,1.0,true,Double.NaN))
        assertEquals(4.0,RefrigeratorThermal.advance(4.0,20.0,1.0,true,0.0))
    }
}
