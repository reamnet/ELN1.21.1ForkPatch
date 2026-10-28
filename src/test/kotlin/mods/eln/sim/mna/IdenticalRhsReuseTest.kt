package mods.eln.sim.mna

import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.misc.ISubSystemProcessI
import mods.eln.sim.mna.misc.ISubSystemProcessFlush
import mods.eln.sim.mna.state.VoltageState
import org.junit.Test
import kotlin.test.*

class IdenticalRhsReuseTest {
    private class Circuit(reuse: Boolean,withCapacitor: Boolean=false) {
        val system: SubSystem
        val pins=List(80) { VoltageState() }
        val supply=VoltageSource("grid",pins[0],null).apply { voltage=24.0 }
        val injection=CurrentSource("load",pins.last(),null)
        val load=Resistor(pins.last(),null).apply { resistance=100.0 }
        val cap=Capacitor(pins.last(),null).apply { coulombs=.01 }
        var stamps=0;var flushes=0
        init {
            val key="eln.disableIdenticalRhsReuse";val old=System.getProperty(key)
            try { System.setProperty(key,(!reuse).toString());system=SubSystem(null,.01) }
            finally { if(old==null)System.clearProperty(key) else System.setProperty(key,old) }
            pins.forEach(system::addState);system.addComponent(supply);system.addComponent(injection);system.addComponent(load)
            for(i in 1 until pins.size)system.addComponent(Resistor(pins[i-1],pins[i]).apply { resistance=.1 })
            if(withCapacitor)system.addComponent(cap)
            system.addProcess(ISubSystemProcessI { stamps++ })
            system.addProcess(ISubSystemProcessFlush { flushes++ })
        }
    }
    @Test fun repeatedLinearSolutionsStillAssembleAndCommitEveryPhysicalStep() {
        val a=Circuit(false);val b=Circuit(true)
        repeat(50) { a.system.step();b.system.step();assertEquals(a.pins.last().state,b.pins.last().state,0.0) }
        assertEquals(50,b.stamps);assertEquals(50,b.flushes);assertTrue(b.system.identicalRhsReuses>=49)
        a.supply.voltage=12.0;b.supply.voltage=12.0;a.system.step();b.system.step()
        assertEquals(a.pins.last().state,b.pins.last().state,0.0)
        a.load.resistance=10.0;b.load.resistance=10.0;a.system.step();b.system.step()
        assertEquals(a.pins.last().state,b.pins.last().state,0.0)
    }
    @Test fun capacitorHistoryCurrentInjectionAndNonfiniteTrialsNeverReuseStaleResults() {
        val a=Circuit(false,true);val b=Circuit(true,true)
        repeat(300) { step->
            if(step==100) { a.supply.voltage=12.0;b.supply.voltage=12.0 }
            if(step==150) { a.injection.current=.2;b.injection.current=.2 }
            a.system.step();b.system.step()
            assertEquals(a.pins.last().state,b.pins.last().state,0.0)
            assertEquals(a.cap.current,b.cap.current,0.0);assertEquals(a.cap.energy,b.cap.energy,0.0)
        }
        b.supply.voltage=Double.NaN;b.system.stepCalc();assertFalse(b.system.hasValidStepSolution())
        b.supply.voltage=12.0;a.system.step();b.system.step()
        assertEquals(a.pins.last().state,b.pins.last().state,0.0)
    }
}
