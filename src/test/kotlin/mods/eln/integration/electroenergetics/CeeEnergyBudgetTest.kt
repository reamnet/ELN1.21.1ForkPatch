package mods.eln.integration.electroenergetics

import org.junit.Test
import kotlin.test.*

class CeeEnergyBudgetTest {
    @Test fun extractionUsesJoulesPerFeAndSimulationCannotMutateEnergyOrQuota() {
        val b=CeeEnergyBudget();b.charge(1000.0,1.0);assertEquals(950.0,b.joules,0.0)
        val count=b.extract(100,true,0,1.7,3400.0);assertEquals(100,count);assertEquals(950.0,b.joules,0.0)
        assertEquals(100,b.extract(100,false,0,1.7,3400.0));assertEquals(780.0,b.joules,1e-9)
        assertEquals(0,b.extract(100,false,0,1.7,3400.0))
        assertEquals(100,b.extract(100,false,1,1.7,3400.0));assertEquals(610.0,b.joules,1e-9)
        assertEquals(50.0,b.lossJoules,0.0);assertEquals(340.0,b.exportedJoules,1e-9)
    }
    @Test fun fractionalRateRemaindersPreserveLowPowerThroughputWithoutBurstingPastEachTickQuota() {
        val b=CeeEnergyBudget();b.restore(10_000.0);var fe=0
        for(tick in 0L until 200)fe+=b.extract(Int.MAX_VALUE,false,tick,1.7,95.0)
        assertEquals(950.0,fe*1.7,1.7)
        assertEquals(10_000-fe*1.7,b.joules,1e-7)
    }
    @Test fun invalidRequestsAndCorruptSavesCannotCreateEnergy() {
        val b=CeeEnergyBudget();b.restore(Double.NaN);assertEquals(0.0,b.joules,0.0)
        b.charge(Double.POSITIVE_INFINITY,1.0);b.charge(-10.0,1.0);assertEquals(0.0,b.joules,0.0)
        b.restore(Double.MAX_VALUE);assertEquals(CeeEnergyBudget.CAPACITY,b.joules,0.0)
        assertEquals(0,b.extract(-1,false,0,1.7,1000.0));assertEquals(0,b.extract(100,false,0,Double.NaN,1000.0))
        val first=b.extract(Int.MAX_VALUE,false,1,2.5,10_000.0);val second=b.extract(Int.MAX_VALUE,false,1,2.5,10_000.0)
        assertEquals(200,first);assertEquals(0,second)
    }
    @Test fun conversionRatioChangesDoNotChangeStoredPhysicalEnergy() {
        val b=CeeEnergyBudget();b.restore(1000.0)
        assertEquals(588,b.available(1.7));assertEquals(400,b.available(2.5));assertEquals(1000.0,b.joules,0.0)
        assertEquals(50,b.extract(50,false,0,2.5,2500.0));assertEquals(875.0,b.joules,0.0)
    }
}
