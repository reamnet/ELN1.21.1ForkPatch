package mods.eln.sim.mna

import org.junit.Test
import kotlin.test.assertEquals

/** Alternating paired runs in one JVM. Baseline retains all earlier MNA optimizations. */
class PortProjectionBenchmarkTest {
    private fun circuit(size: Int, optimized: Boolean): ProbeCircuit {
        val key="eln.disableAffinePortProjections";val old=System.getProperty(key)
        try {
            System.setProperty(key,(!optimized).toString())
            return ProbeCircuit(size,true,ports=16,gaugeRows=true).also {
                if(optimized)it.system.beginPortProjection()
            }
        } finally { if(old==null)System.clearProperty(key) else System.setProperty(key,old) }
    }
    @Test fun measuresCompleteTheveninProbesRatherThanOnlyTheProjectionHelper() {
        for(size in listOf(80,240)) {
            val base=circuit(size,false);val fast=circuit(size,true)
            repeat(2000) { base.sample(it);fast.sample(it) }
            fun timed(c: ProbeCircuit): Double {
                val start=System.nanoTime()
                repeat(500) { c.sample(it+300) }
                return (System.nanoTime()-start)/1e6
            }
            val baseline=ArrayList<Double>();val optimized=ArrayList<Double>()
            repeat(6) {
                if(it%2==0) { baseline+=timed(base);optimized+=timed(fast) }
                else { optimized+=timed(fast);baseline+=timed(base) }
            }
            assertEquals(base.sample(900),fast.sample(900),1e-7)
            println("PORT_PROJECTION_BENCH nodes=$size ports=16 probes_per_round=8000 baseline_ms=$baseline optimized_ms=$optimized")
        }
    }
}
