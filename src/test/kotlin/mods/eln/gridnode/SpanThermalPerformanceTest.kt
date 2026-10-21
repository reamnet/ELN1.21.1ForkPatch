package mods.eln.gridnode

import mods.eln.sixnode.electricalcable.*
import org.junit.Test
import kotlin.test.*

class SpanThermalPerformanceTest {
    @Test fun manyConductorsShareOneClimateReadPerEndpointPerSecondAndHandleClockRollback() {
        val sample=SpanAmbientSample();var reads=0
        repeat(60) { tick -> repeat(12) { assertEquals(20.0+tick/20,sample.sample(tick.toLong()) { reads++;20.0+tick/20 }) } }
        assertEquals(3,reads)
        assertEquals(-10.0,sample.sample(0) { reads++;-10.0 });assertEquals(4,reads)
    }
    @Test fun warmingSpansRetainBoundedResistanceAccuracyWithoutRebuildingForEachTinyDrift() {
        for(material in UtilityCableMaterial.entries) {
            val control=SpanResistanceRefresh();var stamped=0.0;var writes=0
            repeat(4000) { i ->
                val exact=WirePhysics.resistance(material,53.5,40.0,20.0+i*.0001)
                control.update(exact) { stamped=it;writes++ }
                assertTrue(kotlin.math.abs(stamped-exact)/exact<=SpanResistanceRefresh.MAX_RELATIVE_CHANGE*1.0002)
            }
            assertTrue(writes<25,"Unnecessary resistance stamps: $writes / 4000")
            val fault=WirePhysics.resistance(material,53.5,40.0,300.0)
            assertTrue(control.update(fault) { stamped=it;writes++ });assertEquals(fault,stamped)
            assertFalse(control.update(fault) { error("Unchanged resistance stamped again") })
        }
    }
    @Test fun cachedGeometryPreservesBareAndInsulatedCoolingAtColdNormalAndFaultTemperatures() {
        for(material in UtilityCableMaterial.entries)for(area in listOf(.8,53.5,500.0))for(insulated in listOf(false,true)) {
            val physics=WireThermalPhysics(material,area,40.0)
            for(t in listOf(-20.0,20.0,100.0,500.0)) {
                val inner=kotlin.math.sqrt(area*1e-6/kotlin.math.PI);val outer=inner+if(insulated).0005 else 0.0
                val surface=2*kotlin.math.PI*outer*40.0;val kelvin=t+273.15;val air=20+273.15
                val h=8+(if(insulated).9 else .7)*5.670374419e-8*(kelvin*kelvin+air*air)*(kelvin+air)
                val jacket=if(insulated)kotlin.math.ln(outer/inner)/(2*kotlin.math.PI*.2*40.0) else 0.0
                assertEquals(1/(1/(surface*h)+jacket),physics.coolingConductance(t,20.0,insulated),1e-12)
            }
        }
    }
}
