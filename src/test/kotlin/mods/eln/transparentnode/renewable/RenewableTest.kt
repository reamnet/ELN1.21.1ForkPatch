package mods.eln.transparentnode.renewable

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.solarpanel.SolarPanelPowerProcess
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

class RenewableTest {
    private fun element(hybrid: Boolean=true)=SolarElement(TransparentNode(),Eln.transparentNodeItem.subItemList[(if(hybrid)9 else 8)+(124 shl 6)]!!)
    private class Circuit(hybrid: Boolean=true,val series: Int=1,val parallel: Int=1,val watts: Double=1000.0) {
        val element=RenewableTest().element(hybrid);val unit=element.unit;val root=RootSystem(.05,1)
        val panel=NbtElectricalLoad("pv");val minus=NbtElectricalLoad("pvReturn");val mains=VoltageState()
        val pvSource=CurrentSource("pv",panel,minus);val shunt=Resistor(panel,minus)
        val pv=SolarPanelPowerProcess(panel,minus,pvSource,shunt,37.0*series,30.0*series,8.0*parallel,8.6*parallel)
        val grid=SwitchableVoltageSource("grid").apply { connectTo(mains,null);voltage=14_000.0;enabled=hybrid }
        val load=Resistor(unit.output,null).apply { resistance=120.0*120/watts }
        var inputJ=0.0;var gridJ=0.0;var outputJ=0.0
        init {
            element.electricalLoadList.forEach(root::addState);element.electricalComponentList.forEach(root::addComponent)
            listOf(panel,minus,mains).forEach(root::addState)
            listOf(pvSource,shunt,grid,load,Resistor(panel,unit.solar).apply { resistance=.01 },
                Resistor(minus,unit.negative).apply { resistance=.001 },Resistor(mains,unit.grid).apply { resistance=4.0 }).forEach(root::addComponent)
            pv.setLightFactor(1.0);root.addProcess(pv);root.addProcess(unit.controller)
        }
        fun tick() {
            root.step();unit.account(.05)
            check(unit.status!="PROTECTED") { unit.diagnostic }
            inputJ+=unit.pvWatts*.05;gridJ+=unit.gridWatts*.05;outputJ+=unit.outputWatts*.05
            assertTrue(unit.energy.isFinite() && unit.energy in 0.0..SolarPowerUnit.CAPACITY)
            assertTrue(unit.outputWatts<=unit.rating*1.00001)
        }
        fun run(n: Int)=repeat(n) { tick() }
    }
    @Test fun trackerFindsRealPanelMaximumInsteadOfHalfOfOpenCircuitVoltage() {
        for((series,parallel) in listOf(1 to 1,6 to 2,12 to 4)) {
            val c=Circuit(false,series,parallel,10_000.0);c.run(2400)
            var mean=0.0
            repeat(100) { c.tick();mean+=c.unit.pvWatts/100 }
            val stc=240.0*series*parallel
            assertTrue(mean>minOf(stc,c.unit.rating/.97)*.94,"${series}S${parallel}P harvested $mean / $stc W, R=${c.unit.tracker.resistance}, ${c.unit.status}")
            assertTrue(mean<stc*1.001)
            assertTrue(c.outputJ<=c.inputJ*.98*.97+1e-4,"Cannot create energy")
        }
    }
    @Test fun trackerFollowsCloudsAndNeverSpendsMoreThanAcceptedInputEnergy() {
        val c=Circuit(false,6,2,5000.0);c.run(2000)
        for(light in listOf(.25,.7,0.0,1.0)) {
            c.pv.setLightFactor(light);c.run(1800)
            var mean=0.0;repeat(100) { c.tick();mean+=c.unit.pvWatts/100 }
            val maximum=2880*light*(.92+.08*light)
            assertTrue(mean>=maximum*.93,"light=$light power=$mean expected=$maximum")
            assertTrue(c.outputJ<=c.inputJ*.98*.97+1e-3)
        }
    }
    @Test fun solarFirstAssistsFromGridAndGridFirstAvoidsUnnecessarySolarDischarge() {
        val c=Circuit();c.run(2400)
        assertTrue(c.unit.pvWatts>220);assertTrue(c.unit.output.voltage>119.9)
        assertTrue(c.unit.gridWatts in 780.0..850.0,"${c.unit.gridWatts}")
        assertEquals("ASSIST",c.unit.status)
        c.unit.priority=SolarPriority.GRID_FIRST;c.run(600)
        assertEquals("GRID",c.unit.status);assertTrue(c.unit.gridWatts>1000);assertTrue(c.unit.energy>4999)
        assertTrue(c.outputJ+c.unit.energy*.97<=c.inputJ*.98*.97+c.gridJ*.97+1e-3)
    }
    @Test fun allPrioritiesFallbackAndOffNeverBackfeedOrGenerateFreePower() {
        val c=Circuit();c.unit.priority=SolarPriority.GRID_ONLY;c.run(200)
        assertTrue(c.unit.output.voltage>119.9);assertTrue(c.unit.pvWatts<1e-5)
        c.grid.enabled=false;c.run(40);assertTrue(c.unit.outputWatts<1e-5)
        c.grid.enabled=true;c.unit.priority=SolarPriority.SOLAR_ONLY;c.run(2000)
        assertEquals(0.0,c.unit.gridWatts);assertTrue(c.unit.outputWatts>200)
        c.unit.enabled=false;c.run(20);assertEquals(0.0,c.unit.outputWatts);assertTrue(c.unit.pvWatts<1e-5)
        c.pv.setLightFactor(0.0);c.unit.enabled=true;c.run(200)
        assertEquals(0.0,c.unit.outputWatts);assertEquals(0.0,c.unit.gridWatts)
    }
    @Test fun gridLossAndReturnAreStableWithoutConverterFaultOrBackfeed() {
        val c=Circuit(watts=100.0);c.unit.priority=SolarPriority.GRID_FIRST;c.run(300)
        c.grid.enabled=false;c.run(2000)
        repeat(100) { c.tick();assertTrue(c.unit.output.voltage>119.9,"${c.unit.status}");assertEquals(0.0,c.unit.gridWatts) }
        c.grid.enabled=true;c.run(40);assertEquals("GRID",c.unit.status)
    }
    @Test fun unloadedOutputCurtailsSolarAndResumesWhenLoadReturns() {
        val c=Circuit(false,6,2);c.load.resistance=1e19;c.run(1500)
        assertTrue(c.unit.energy>4999);assertTrue(c.unit.pvWatts<1e-4)
        c.load.resistance=14.4;c.run(1400);assertTrue(c.unit.output.voltage>119.9)
    }
    @Test fun lowAndHighSolarSourcesCanBuckOrBoostToEitherMainsVoltage() {
        for(vin in listOf(12.0,48.0,850.0,3200.0,14_000.0))for(vout in listOf(120.0,240.0)) {
            val c=Circuit(watts=100.0);c.pv.setLightFactor(0.0)
            val input=VoltageSource("solarDC",c.panel,c.minus).apply { voltage=vin };c.root.addComponent(input)
            c.unit.priority=SolarPriority.SOLAR_ONLY;c.unit.setVoltage(vout);c.load.resistance=vout*vout/100
            c.run(2000)
            assertTrue(c.unit.output.voltage>vout*.999,"$vin -> $vout : ${c.unit.output.voltage}, ${c.unit.status}")
            assertEquals(0.0,c.unit.gridWatts)
        }
    }
    @Test fun overloadAndShortAreLimitedLocallyWhileGridVoltageRemainsLive() {
        val c=Circuit();c.unit.priority=SolarPriority.GRID_ONLY;c.load.resistance=.00001;c.run(60)
        repeat(20) { c.tick();assertTrue(c.unit.outputSource.current<=200.0002);assertTrue(c.mains.voltage>13999) }
        c.load.resistance=14.4;c.run(100);assertTrue(c.unit.output.voltage>119.9)
    }
    @Test fun savedEnergyIsBoundedAndConfigurationCopyDoesNotCopyEnergy() {
        val c=Circuit(false);c.load.resistance=1e19;c.run(1800)
        val nbt=CompoundTag().also(c.element::writeToNBT);val restored=element(false);restored.readFromNBT(nbt)
        assertEquals(c.unit.energy,restored.unit.energy);assertEquals(c.unit.tracker.resistance,restored.unit.tracker.resistance)
        val dropped=c.element.getItemStackNBT()!!;restored.readItemStackNBT(dropped);assertEquals(0.0,restored.unit.energy)
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,Double.MAX_VALUE)) {
            restored.unit.read(CompoundTag().apply { putDouble("solarJoules",v);putDouble("solarTarget",v) })
            assertTrue(restored.unit.energy.isFinite() && restored.unit.energy in 0.0..5000.0)
            assertEquals(120.0,restored.unit.target)
        }
        assertFalse(c.unit.setVoltage(0.0));assertTrue(c.unit.setVoltage(3200.0));assertFalse(c.unit.setVoltage(3201.0))
        val h=element().unit;assertFalse(h.setVoltage(850.0));assertTrue(h.setVoltage(240.0))
    }
    @Test fun inputPortsAreDistinctAndNewIdsDoNotReplaceExistingBlocks() {
        val e=element();e.front=Direction.ZP
        assertSame(e.unit.solar,e.getElectricalLoad(Direction.XN,LRDU.Down))
        assertSame(e.unit.output,e.getElectricalLoad(Direction.XP,LRDU.Down))
        assertSame(e.unit.negative,e.getElectricalLoad(Direction.ZN,LRDU.Down))
        assertSame(e.unit.grid,e.getElectricalLoad(Direction.ZP,LRDU.Down))
        assertNull(e.getElectricalLoad(Direction.XP,LRDU.Up))
        assertTrue(Eln.transparentNodeItem.subItemList[7+(124 shl 6)]!!.name.contains("Standby"))
    }
    @Test fun renewableAlternatorHasRealPowerBoundsAt3200VoltsAndRestartsWithoutReplacingCable() {
        for(budget in listOf(1000.0,10_000.0,20_000.0)) {
            val root=RootSystem(.05,1);val pin=VoltageState();var available=budget
            val source=SwitchableVoltageSource("renewable").apply { connectTo(pin,null) }
            val controller=RenewableSupply(source) { available };val load=Resistor(pin,null).apply { resistance=3200.0*3200/(budget*.5) }
            root.addState(pin);root.addComponent(source);root.addComponent(load);root.addProcess(controller)
            repeat(5) { root.step() };assertEquals(3200.0,pin.state,1e-6)
            load.resistance=1.0;repeat(10) { root.step();assertTrue(source.power<=budget*1.00001);assertTrue(source.current<=50.0001) }
            available=0.0;root.step();assertEquals(0.0,source.power)
            available=budget;load.resistance=3200.0*3200/(budget*.5);repeat(10) { root.step() };assertEquals(3200.0,pin.state,1e-6)
        }
    }
    @Test fun finite3200VoltRenewableSupplyFeedsHybridAndSurvivesExcessDemand() {
        val c=Circuit(watts=5000.0);c.grid.enabled=false;c.pv.setLightFactor(0.0);c.unit.priority=SolarPriority.GRID_ONLY
        val pin=VoltageState();val source=SwitchableVoltageSource("finiteWind").apply { connectTo(pin,null) }
        val supply=RenewableSupply(source) { 10_000.0 };c.root.addState(pin);c.root.addComponent(source)
        c.root.addComponent(Resistor(pin,c.unit.grid).apply { resistance=.5 });c.root.addProcess(supply)
        c.run(100);assertTrue(c.unit.outputWatts>4990)
        c.load.resistance=120.0*120/40_000;c.run(150)
        repeat(20) { c.tick();assertTrue(source.power<=10_000.01);assertTrue(c.unit.outputWatts<=9700.01) }
        c.load.resistance=120.0*120/5000;c.run(80);assertTrue(c.unit.outputWatts>4990)
    }
    @Test fun standaloneMpptCanFeedTheHybridSolarInputWithoutCreatingEnergy() {
        val c=Circuit(watts=1000.0);c.pv.setLightFactor(0.0);c.grid.enabled=false;c.unit.priority=SolarPriority.SOLAR_ONLY
        val mppt=element(false);mppt.electricalLoadList.forEach(c.root::addState);mppt.electricalComponentList.forEach(c.root::addComponent)
        val source=CurrentSource("array",mppt.unit.solar,mppt.unit.negative);val shunt=Resistor(mppt.unit.solar,mppt.unit.negative)
        val panel=SolarPanelPowerProcess(mppt.unit.solar,mppt.unit.negative,source,shunt,222.0,180.0,16.0,17.2).apply { setLightFactor(1.0) }
        c.root.addComponent(source);c.root.addComponent(shunt);c.root.addProcess(panel);c.root.addProcess(mppt.unit.controller)
        c.root.addComponent(Resistor(mppt.unit.output,c.panel).apply { resistance=.01 })
        // The upstream output is ground referenced, so its receiving PV return
        // must be wired to ground, just as in the documented one-terminal setup.
        c.root.addComponent(Resistor(c.unit.negative,null).apply { resistance=.001 })
        var harvested=0.0;var delivered=0.0
        repeat(1800) { c.tick();mppt.unit.account(.05);harvested+=mppt.unit.pvWatts*.05;delivered+=c.unit.outputWatts*.05 }
        assertTrue(c.unit.output.voltage>119.9,"${c.unit.status}, ${mppt.unit.status}; hybrid ${c.unit.energy} J, ${c.unit.pvWatts} W, ${c.unit.solar.voltage} V / R ${c.unit.tracker.resistance}; MPPT ${mppt.unit.energy} J, ${mppt.unit.pvWatts} W, ${mppt.unit.output.voltage} V / ${mppt.unit.outputWatts} W / R ${mppt.unit.tracker.resistance}")
        assertTrue(delivered<=harvested*.98*.97*.98*.97+1e-3)
        repeat(100) { c.tick();mppt.unit.account(.05);assertTrue(c.unit.output.voltage>119.9) }
    }
    @Test fun invalidSolarAndGridInputsRemainIsolatedAndCannotChargeTheLink() {
        for(v in listOf(-120.0,0.0,5.0,60_000.0)) {
            val c=Circuit();c.grid.enabled=false;c.pv.setLightFactor(0.0)
            c.root.addComponent(VoltageSource("invalidPV",c.panel,c.minus).apply { voltage=v });c.run(80)
            assertTrue(c.unit.energy<1e-6);assertEquals(0.0,c.unit.outputWatts)
        }
    }
    @Test fun largeRenewableHybridAnd120PoleConvertersShareALongFeederWithoutBlinking() {
        val c=Circuit(watts=5000.0);c.grid.enabled=false;c.pv.setLightFactor(0.0)
        val source=SwitchableVoltageSource("utilityWind").apply { connectTo(c.mains,null) }
        c.root.addComponent(source);c.root.addProcess(RenewableSupply(source) { 20_000.0 })
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<mods.eln.transparentnode.utilitylight.StepDownUnitDescriptor>().single()
        val poles=List(120) { i ->
            mods.eln.transparentnode.utilitylight.StepDownUnitElement(TransparentNode(),descriptor).also { e ->
                e.unit.read(CompoundTag().apply { putInt("mainsOutputVoltage",if(i%2==0)120 else 240) })
                e.electricalLoadList.forEach(c.root::addState);e.electricalComponentList.forEach(c.root::addComponent);c.root.addProcess(e.unit.controller)
                c.root.addComponent(Resistor(e.unit.secondary,null).apply { resistance=e.unit.outputVoltage.toDouble().let { it*it/40.0 } })
            }
        }
        c.root.addComponent(Resistor(c.mains,poles[0].unit.primary).apply { resistance=4.0 })
        for(i in 1..119)c.root.addComponent(Resistor(poles[i-1].unit.primary,poles[i].unit.primary).apply { resistance=.2 })
        repeat(30) {
            c.tick();assertTrue(c.unit.output.voltage>119.9)
            poles.forEach { assertEquals(it.unit.outputVoltage.toDouble(),it.unit.secondary.voltage,.1,it.unit.controller.status) }
            assertTrue(source.power<=20_000.01)
        }
    }
    @Test fun renewableSourceBlocksReversePowerAndLargeMachinesReserveTheirRealClearance() {
        val root=RootSystem(.05,1);val pin=VoltageState();val other=VoltageState()
        val source=SwitchableVoltageSource("wind").apply { connectTo(pin,null) }
        root.addState(pin);root.addState(other);root.addComponent(source)
        root.addComponent(VoltageSource("grid",other,null).apply { voltage=14_000.0 });root.addComponent(Resistor(pin,other).apply { resistance=1.0 })
        root.addProcess(RenewableSupply(source) { 20_000.0 });repeat(10) { root.step() }
        assertEquals(0.0,source.power);assertEquals(14_000.0,pin.state,1e-6)
        for(id in 10..11) {
            val d=Eln.transparentNodeItem.subItemList[id+(124 shl 6)] as RenewableDescriptor
            assertTrue(d.ghostGroup!!.size()>50);assertTrue(d.ghostGroup!!.elementList.maxOf { it.y }>=8)
        }
        assertEquals(0.0,LargeRenewableElement.windPower(2.9,1.0));assertEquals(0.0,LargeRenewableElement.windPower(25.0,1.0))
        assertEquals(20_000.0,LargeRenewableElement.windPower(12.0,1.0));assertEquals(0.0,LargeRenewableElement.windPower(12.0,0.0))
    }
}
