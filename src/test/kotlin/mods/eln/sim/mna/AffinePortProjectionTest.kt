package mods.eln.sim.mna

import mods.eln.sim.mna.component.CurrentSource
import org.apache.commons.numbers.core.DD
import org.junit.Test
import kotlin.test.*

class AffinePortProjectionTest {
    private fun circuit(size: Int, optimized: Boolean, asymmetric: Boolean=false): ProbeCircuit {
        val key="eln.disableAffinePortProjections";val old=System.getProperty(key)
        try {
            System.setProperty(key,(!optimized).toString())
            return ProbeCircuit(size,true,asymmetric,ports=8,gaugeRows=true).also {
                if(optimized)it.system.beginPortProjection()
            }
        } finally { if(old==null)System.clearProperty(key) else System.setProperty(key,old) }
    }

    @Test fun changingCommandsMatchDirectProjectionsIncludingMasksResistanceAndNonreciprocalStamps() {
        for(size in listOf(12,80,160))for(asymmetric in listOf(false,true)) {
            val base=circuit(size,false,asymmetric);val fast=circuit(size,true,asymmetric)
            val states=fast.pins.map { it.state }
            repeat(300) { step ->
                if(step==100) { base.wire.resistance=.012;fast.wire.resistance=.012 }
                if(step==150) { base.probes[0].enabled=false;fast.probes[0].enabled=false }
                if(step==200) { base.probes[0].enabled=true;fast.probes[0].enabled=true }
                assertEquals(base.sample(step),fast.sample(step),1e-7,"size=$size asymmetric=$asymmetric step=$step")
            }
            assertEquals(states,fast.pins.map { it.state },"Probing committed a physical state")
            val a=CurrentSource("unbalanced",base.pins[0],null);val b=CurrentSource("unbalanced",fast.pins[0],null)
            base.system.addComponent(a);fast.system.addComponent(b)
            a.current=1.0;b.current=1.0
            assertTrue(base.system.sourceThevenin(base.probes[0]).voltage.isNaN())
            assertTrue(fast.system.sourceThevenin(fast.probes[0]).voltage.isNaN())
            a.current=0.0;b.current=0.0
            base.feed.voltage=Double.MAX_VALUE;fast.feed.voltage=Double.MAX_VALUE
            assertEquals(base.system.sourceThevenin(base.probes[0]).voltage.isNaN(),fast.system.sourceThevenin(fast.probes[0]).voltage.isNaN())
            assertEquals(base.sample(301),fast.sample(301),1e-7)
            fast.system.endPortProjection()
            assertEquals(base.sample(302),fast.sample(302),1e-7,"Ordinary probes must remain valid after scope cleanup")
        }
    }

    @Test fun stiffCancellationUsesDoubleDoubleDeltasAndNonfiniteTrialsDoNotPoisonRecovery() {
        val row=Array(80) { DD.of(if(it%2==0)1e10 else -1e10) }
        val rhs=DoubleArray(80) { 15_200.0 }
        val fast=AffinePortProjection(row)
        repeat(1000) { step ->
            rhs[step%80]=Math.nextUp(rhs[step%80])
            var expected=DD.ZERO
            for(i in row.indices)expected=expected.add(row[i].multiply(rhs[i]))
            assertEquals(expected.toDouble(),fast.project(rhs).toDouble(),1e-12)
        }
        assertTrue(fast.shortcuts>500)
        rhs[0]=Double.NaN;assertTrue(fast.project(rhs).toDouble().isNaN())
        rhs.fill(15_200.0);assertEquals(0.0,fast.project(rhs).toDouble())
    }
}
