package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.utility.DistributionKind
import mods.eln.utility.DistributionPoleDescriptor
import mods.eln.utility.DistributionPoleElement
import org.junit.Test
import kotlin.test.*

/** Actual three-phase poles with unfed B/C legs and a mix of loaded/unloaded secondaries.
 * Open contacts leave floating islands in the structurally connected HV subsystem.
 */
class PoleGridPerformanceTest {
    @Test fun measuresTransformerProbesOnPartiallyEnergisedThreePhaseFeeders() {
        Eln.debugEnabled=false
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>()
            .single { it.kind==DistributionKind.TRANSFORMER_THREE }
        for(count in listOf(10,40,120)) {
            val root=RootSystem(.01,50)
            val feed=List(3) { VoltageState().also(root::addState) }
            val supply=VoltageSource("phase A",feed[0],null).apply { voltage=12_470.0 }.also(root::addComponent)
            var previous: List<mods.eln.sim.mna.state.State> = feed
            val loads=mutableListOf<Resistor>()
            val wires=mutableListOf<Resistor>()
            val poles=List(count) { i ->
                DistributionPoleElement(TransparentNode(),descriptor).also { pole ->
                    pole.inventory.setItem(0,Eln.findItemStack("10 kW Pole Transformer",1));pole.inventoryChange(pole.inventory)
                    pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
                    root.addProcess(pole.unit!!.controller)
                    repeat(3) { phase -> wires+=Resistor(previous[phase],pole.lines[phase]).apply { resistance=.1 }.also(root::addComponent) }
                    previous=pole.lines
                    if(i%3!=0)loads+=Resistor(pole.services[0],null).apply { resistance=240.0 }.also(root::addComponent)
                }
            }
            val started=System.nanoTime();root.step();val startup=(System.nanoTime()-started)/1e6
            repeat(12) { root.step() }
            val samples=DoubleArray(40) {
                val start=System.nanoTime();root.step();(System.nanoTime()-start)/1e6
            }.sorted()
            supply.voltage*=.95
            val changed=System.nanoTime();repeat(10) { root.step() };val sag=(System.nanoTime()-changed)/1e6/10
            poles.forEach { assertNotEquals("NON_CONVERGENT",it.unit!!.controller.status);assertEquals(114.0,it.services[0].voltage,.3) }
            assertTrue(supply.power>=loads.sumOf { it.power })
            assertTrue(poles.first().lines[0].subSystem.captureDebugSnapshot().hasReferenceGauge())
            val warming=DoubleArray(4) {
                wires.forEach { it.resistance*=1.00001 }
                val start=System.nanoTime();root.step();(System.nanoTime()-start)/1e6
            }
            poles.forEach { assertNotEquals("NON_CONVERGENT",it.unit!!.controller.status);assertEquals(114.0,it.services[0].voltage,.3) }
            assertTrue(supply.power>=loads.sumOf { it.power })
            println("POLE_GRID_BENCH count=$count states=${root.systems.maxOf { it.states.size }} startup_ms=$startup p50_ms=${samples[19]} p95_ms=${samples[37]} sag_mean_ms=$sag warming_mean_ms=${warming.average()} factorizations=${root.systems.sumOf { it.fullFactorizations }}")
        }
    }
}
