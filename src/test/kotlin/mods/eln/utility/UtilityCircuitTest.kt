package mods.eln.utility

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

class UtilityCircuitTest {
    @Test fun serviceTapsSharePowerAndReportEachTerminalCurrentCorrectly() {
        val root=RootSystem(.01,1)
        val input=NbtElectricalLoad("input");val a=NbtElectricalLoad("a");val b=NbtElectricalLoad("b");val c=NbtElectricalLoad("c")
        listOf(input,a,b,c).forEach(root::addState)
        val source=VoltageSource("service",input,null).apply { voltage=240.0 };root.addComponent(source)
        val loads=listOf(Resistor(a,null).apply { resistance=12.0 },Resistor(b,null).apply { resistance=24.0 },Resistor(c,null).apply { resistance=48.0 })
        listOf(ServiceTap(input,a,.5),ServiceTap(input,b,.5),ServiceTap(input,c,1.0)).forEach(root::addComponent)
        loads.forEach(root::addComponent);repeat(3) { root.step() }
        assertEquals(120.0,a.voltage,1e-8);assertEquals(120.0,b.voltage,1e-8);assertEquals(240.0,c.voltage,1e-8)
        assertEquals(10.0,a.current,1e-8);assertEquals(5.0,b.current,1e-8);assertEquals(5.0,c.current,1e-8)
        assertEquals(loads.sumOf { it.power },source.power,1e-7)
        assertEquals(3000.0,source.power,1e-7)
    }
    private class UnitCircuit(rating: Int) {
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<StepDownUnitDescriptor>().single()
        val e=StepDownUnitElement(TransparentNode(),descriptor)
        val unit=e.unit
        val service=ServiceSecondary(e,unit.secondary) { unit.outputVoltage.toDouble() }
        val root=RootSystem(.01,50)
        val sourcePin=NbtElectricalLoad("grid")
        val source=VoltageSource("grid",sourcePin,null).apply { voltage=14_000.0 }
        val feeder=Resistor(sourcePin,unit.primary).apply { resistance=.1 }
        val loads=List(3) { i -> Resistor(service.loads[i],null).apply { resistance=service.voltages[i]*service.voltages[i]/(rating*.30) } }
        init {
            unit.read(CompoundTag().apply { putInt("mainsRatedWatts",rating);putInt("mainsOutputVoltage",240) });service.update()
            root.addState(sourcePin);e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            root.addComponent(source);root.addComponent(feeder);loads.forEach(root::addComponent);root.addProcess(unit.controller)
        }
    }
    @Test fun allFiveRatingsFeedThreeServiceCircuitsWithOneSharedConservedBudget() {
        for(rating in MainsStepDown.RATINGS) {
            val c=UnitCircuit(rating);repeat(20) { c.root.step() }
            assertTrue(c.unit.output.enabled,"$rating: ${c.unit.controller.status}")
            c.service.loads.forEachIndexed { i,l -> assertEquals(c.service.voltages[i],l.voltage,1.0,"$rating output $i: ${c.unit.controller.status}") }
            assertTrue(c.loads.sumOf { it.power }>rating*.87)
            assertTrue(c.loads.sumOf { it.power }<=c.source.power*MainsStepDown.EFFICIENCY+.01)
            assertTrue(c.unit.output.power<=rating*1.000001)
            c.loads.forEach { it.resistance/=4 };repeat(12) { c.root.step() }
            assertTrue(c.unit.output.power<=rating*1.000001,"Overloaded $rating: ${c.unit.output.power}")
            assertTrue(c.loads.sumOf { it.power }<=c.source.power*MainsStepDown.EFFICIENCY+.01)
        }
    }
    @Test fun stableServiceBranchesReuseTheirFactorizationAndNeverLeaveAStaleTapStateAfterRebuild() {
        val c=UnitCircuit(100_000);repeat(12) { c.root.step() }
        val before=c.root.systems.sumOf { it.fullFactorizations }
        repeat(40) { c.root.step() }
        assertEquals(before,c.root.systems.sumOf { it.fullFactorizations })
        c.loads[0].resistance*=2;repeat(4) { c.root.step() }
        assertEquals(120.0,c.service.loads[0].voltage,1.0)
        c.root.removeComponent(c.loads[1]);repeat(4) { c.root.step() }
        assertEquals(240.0,c.service.loads[2].voltage,1.0)
        assertTrue(c.root.systems.all { s->s.states.distinct().size==s.states.size })
    }
    @Test fun aPanelBreakerOnlyOpensItsOwnRealSupplyBranchAndPreservesOtherBuses() {
        val desc=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single()
        val panel=BreakerPanelElement(TransparentNode(),desc)
        val breaker=Eln.sixNodeItem.subItemList.values.filterIsInstance<mods.eln.sixnode.electricalbreaker.ElectricalBreakerDescriptor>().first { it.currentLimit==20.0 }
        for(i in 0..2)panel.inventory.setItem(i,breaker.newItemStack())
        panel.inventoryChange(panel.inventory)
        val root=RootSystem(.01,1)
        panel.electricalLoadList.forEach(root::addState);panel.electricalComponentList.forEach(root::addComponent)
        panel.buses.forEachIndexed { i,b -> root.addComponent(VoltageSource("bus$i",b,null).apply { voltage=if(i==2)240.0 else 120.0 }) }
        root.addComponent(Resistor(panel.circuits[0].load,null).apply { resistance=2.0 })
        root.addComponent(Resistor(panel.circuits[1].load,null).apply { resistance=24.0 })
        repeat(3) { root.step() }
        val c=panel.circuits[0]
        assertTrue(c.breaker.step(.1,c.contacts[c.bus].current,c.rating));c.configure();root.step()
        assertEquals(0.0,c.load.voltage,1e-6);assertFalse(c.handle.element.getChannelState(0))
        assertEquals(120.0,panel.circuits[1].load.voltage,.05)
        assertFalse(panel.circuits[1].breaker.tripped)
        assertEquals(0.0,panel.circuits[3].load.voltage,1e-7,"Empty breaker slot must be open")
    }
    @Test fun aPhaseFaultOnACutoutDoesNotJoinOrDisconnectTheOtherTwoPhases() {
        val desc=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.CUTOUT_THREE }
        val pole=DistributionPoleElement(TransparentNode(),desc)
        val root=RootSystem(.01,1)
        pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
        pole.lines.forEachIndexed { i,pin -> root.addComponent(VoltageSource("phase$i",pin,null).apply { voltage=1000.0+i*100 }) }
        root.addComponent(Resistor(pole.outs[1],null).apply { resistance=1.0 })
        repeat(3) { root.step() };pole.electricalProcessList.forEach { it.process(.1) };root.step()
        assertTrue(pole.fuses[1].blown);assertFalse(pole.fuses[0].blown);assertFalse(pole.fuses[2].blown)
        assertEquals(1000.0,pole.outs[0].voltage,1e-7);assertEquals(0.0,pole.outs[1].voltage,1e-7);assertEquals(1200.0,pole.outs[2].voltage,1e-7)
    }
    @Test fun legacyRatingDefaultsAndMalformedRatingSavesCannotCreateUnboundedConverters() {
        val c=UnitCircuit(100_000)
        c.unit.read(CompoundTag());assertEquals(10_000,c.unit.ratedWatts)
        c.unit.read(CompoundTag().apply { putInt("mainsRatedWatts",Int.MAX_VALUE) });assertEquals(10_000,c.unit.ratedWatts)
    }
    @Test fun aLimitedPoleOutputBlowsItsInputFuseWithoutCollapsingTheHighVoltageSource() {
        val desc=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.TRANSFORMER_SINGLE }
        for(resistance in listOf(1.0,.000001)) {
        val pole=DistributionPoleElement(TransparentNode(),desc)
        pole.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));pole.inventoryChange(pole.inventory)
        val root=RootSystem(.01,50)
        pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
        root.addComponent(VoltageSource("pole feed",pole.lines[0],null).apply { voltage=14_000.0 })
        root.addComponent(Resistor(pole.services[2],null).apply { this.resistance=resistance })
        root.addProcess(pole.unit!!.controller)
        repeat(60) { root.step();pole.electricalProcessList.forEach { p -> p.process(.01) } }
        assertTrue(pole.fuses[0].blown,"Fuse did not trip: ${pole.unit.controller.status}")
        assertFalse(pole.fuses[0].closed);assertEquals(14_000.0,pole.lines[0].voltage,1e-6)
        assertEquals(0.0,pole.services[2].voltage,1e-6)
        }
    }

}
