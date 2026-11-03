package mods.eln.transparentnode.offgrid

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.transparentnode.solarpanel.SolarPanelPowerProcess
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

class OffGridTest {
    private fun element(type: OffGridType)=OffGridElement(TransparentNode(),Eln.transparentNodeItem.subItemList[type.ordinal+(128 shl 6)]!!)
    private class Circuit(val type: OffGridType=OffGridType.INVERTER,fraction: Double=.5) {
        val pack=OffGridTest().element(OffGridType.BANK16);val bank=pack.bank!!
        val box=OffGridTest().element(type);val ac=box.ac!!
        val root=RootSystem(.05,1)
        val grid=SwitchableVoltageSource("incomingGrid").apply { connectTo(ac.grid,null);voltage=240.0;enabled=true }
        val load=Resistor(ac.load,null).apply { resistance=57.6 }
        var inputJ=0.0;var outputJ=0.0
        init {
            bank.state.transfer(bank.state.capacity*fraction)
            for(e in listOf(pack,box)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent) }
            listOf(grid,load,Resistor(bank.positive,ac.dc).apply { resistance=.001 },Resistor(bank.negative,ac.negative).apply { resistance=.001 }).forEach(root::addComponent)
            root.addProcess(bank.controller);ac.controllers.forEach(root::addProcess)
        }
        fun tick() {
            root.step()
            for(e in listOf(pack,box))e.electricalProcessList.forEach { it.process(.05) }
            inputJ+=grid.power*.05;outputJ+=load.power*.05
            assertTrue(bank.state.energy in 0.0..bank.state.capacity)
            assertTrue(abs(bank.source.current)<=bank.state.maxAmps+1e-3,"BMS ${bank.source.current} A, ${bank.volts} V")
            assertFalse(ac.bypass.closed && ac.inverterOutput.enabled)
        }
        fun run(n: Int)=repeat(n) { tick() }
    }
    @Test fun allEightCabinetsHaveExactCapacityAndNoBuiltInHouseholdInverter() {
        for(t in OffGridType.entries.filter { it.bank }) {
            val e=element(t);e.front=Direction.ZP
            assertEquals(t.kwh*3.6e6,e.bank!!.state.capacity)
            assertEquals(0.0,e.bank!!.state.energy);assertNull(e.ac)
            val plane=e.cabinet.connectionPlane(e.front)
            assertSame(e.bank!!.positive,e.getElectricalLoad(e.front.left(),e.front.left().getLRDUGoingTo(plane)!!))
            assertSame(e.bank!!.negative,e.getElectricalLoad(e.front.right(),e.front.right().getLRDUGoingTo(plane)!!))
            assertNotNull(e.cabinet.ghostGroup);assertEquals(1,e.cabinet.ghostGroup!!.elementList.single().y)
            for(part in listOf("Case","Positive","Negative","Capacity","StatusLed","ChargeLed","FaultLed"))assertNotNull(Eln.obj.getObj(t.model).getPart(part))
        }
        assertTrue(Eln.transparentNodeItem.subItemList[12+(126 shl 6)]!!.name.contains("Four Cell"))
    }
    @Test fun physicalTransferAlwaysOpensBeforeChangingSources() {
        val t=IslandTransfer()
        repeat(7) { t.advance(.05,IslandFeed.GRID) };assertEquals(IslandFeed.GRID,t.selected)
        t.advance(.05,IslandFeed.BATTERY);assertEquals(IslandFeed.OPEN,t.selected)
        repeat(4) { t.advance(.05,IslandFeed.BATTERY);assertEquals(IslandFeed.OPEN,t.selected) }
        t.advance(.05,IslandFeed.BATTERY);assertEquals(IslandFeed.BATTERY,t.selected)
        t.advance(.05,IslandFeed.OPEN);assertEquals(IslandFeed.OPEN,t.selected)
    }
    @Test fun gridSuppliesLoadsAndChargesAnEmptyBankWithoutEnergyCreation() {
        val c=Circuit(fraction=0.0);c.run(200)
        assertTrue(c.bank.state.energy>1000,"Stored ${c.bank.state.energy}; ${c.ac.charger.status}, ${c.ac.batteryVolts} V")
        assertTrue(c.load.power>990,"Output ${c.ac.outputWatts} W, ${c.ac.status}")
        assertTrue(c.bank.state.energy+c.outputJ<=c.inputJ+1e-3)
        assertEquals(IslandFeed.GRID,c.ac.transfer.selected)
    }
    @Test fun gridLossAndReturnPreserveIsolationAndBatteryPowerPaysForBackup() {
        val c=Circuit();c.run(40);c.grid.enabled=false;c.run(40)
        val before=c.bank.state.energy;val out=c.outputJ;c.run(100)
        assertTrue(c.ac.load.voltage>239.9,"${c.ac.status}, ${c.ac.supply.status}, DC ${c.ac.batteryVolts}")
        assertTrue(c.bank.state.energy<before-(c.outputJ-out));assertEquals(0.0,c.grid.power)
        c.grid.enabled=true;c.run(40);assertEquals(IslandFeed.GRID,c.ac.transfer.selected);assertTrue(c.ac.chargeWatts>1)
    }
    @Test fun offGridModeDoesNotUseThePresentGridOrChargeFromIt() {
        val c=Circuit();c.ac.mode=IslandMode.OFF_GRID;c.run(120)
        assertTrue(c.ac.outputWatts>990,"${c.ac.status}: ${c.ac.supply.status}")
        assertEquals(0.0,c.ac.gridWatts);assertFalse(c.ac.bypass.closed);assertFalse(c.ac.chargeInput.enabled)
    }
    @Test fun standaloneChargerAcceptsBothMainsVoltagesAndStopsAtFull() {
        for(v in listOf(120.0,240.0)) {
            val c=Circuit(OffGridType.AC_CHARGER,0.0);c.grid.voltage=v;c.run(100)
            assertTrue(c.bank.state.energy>100,"$v V: ${c.ac.charger.status}, DC ${c.ac.batteryVolts}")
            assertTrue(c.ac.chargeWatts<=5680.01);assertTrue(c.bank.state.energy<=c.inputJ*.98+1e-3)
            c.bank.state.transfer(c.bank.state.capacity);c.run(100)
            assertEquals(c.bank.state.capacity,c.bank.state.energy,1e-6)
            assertTrue(c.ac.chargeWatts<.1,"Full-bank draw ${c.ac.chargeWatts}")
        }
    }
    @Test fun anEmptyOffGridBatteryCannotSupplyUnpaidEnergy() {
        val c=Circuit(fraction=0.0);c.ac.mode=IslandMode.OFF_GRID;c.grid.enabled=false;c.run(100)
        assertEquals(0.0,c.bank.state.energy);assertTrue(c.outputJ<1e-4);assertTrue(c.ac.outputWatts<1e-6)
    }
    @Test fun solarMpptChargesTheDcBankAndNightCannotDrainItBackIntoPanels() {
        val pack=element(OffGridType.BANK16);val bank=pack.bank!!;val box=element(OffGridType.MPPT);val u=box.solar!!
        val root=RootSystem(.05,1)
        for(e in listOf(pack,box)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent) }
        val source=CurrentSource("realPv",u.solar,u.negative);val shunt=Resistor(u.solar,u.negative)
        val panel=SolarPanelPowerProcess(u.solar,u.negative,source,shunt,222.0,180.0,16.0,17.2).apply { setLightFactor(1.0) }
        listOf(source,shunt,Resistor(u.output,bank.positive).apply { resistance=.001 },Resistor(u.batteryReturn,bank.negative).apply { resistance=.001 }).forEach(root::addComponent)
        root.addProcess(panel);root.addProcess(bank.controller);root.addProcess(u.controller)
        var harvested=0.0
        fun run(n: Int) { repeat(n) { root.step();u.account(.05);bank.account(.05);harvested+=u.pvWatts*.05 } }
        run(1200);assertTrue(bank.state.energy>1000,"${u.status}, ${u.diagnostic}; bank ${bank.volts}")
        assertTrue(bank.state.energy<=harvested*.98*.97*.98+1e-2)
        panel.setLightFactor(0.0);run(100);val before=bank.state.energy;run(100)
        assertTrue(bank.state.energy>=before-.1);assertTrue(u.pvWatts<1e-5)
    }
    @Test fun packStateSurvivesMovingButTheConfigToolCannotDuplicateCharge() {
        val e=element(OffGridType.BANK32);e.bank!!.state.transfer(12_345_678.0)
        val saved=CompoundTag().also(e::writeToNBT);val restored=element(OffGridType.BANK32);restored.readFromNBT(saved)
        assertEquals(e.bank!!.state.energy,restored.bank!!.state.energy)
        val item=e.getItemStackNBT();val moved=element(OffGridType.WALL32);moved.readItemStackNBT(item);assertEquals(e.bank!!.state.energy,moved.bank!!.state.energy)
        val config=CompoundTag().also { it.putDouble("lfpJoules",1e100) }
        moved.configure(config);assertEquals(12_345_678.0,moved.bank!!.state.energy)
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-10.0,Double.MAX_VALUE)) {
            restored.bank!!.state.read(CompoundTag().apply { putDouble("lfpJoules",v) });assertTrue(restored.bank!!.state.energy in 0.0..restored.bank!!.state.capacity)
        }
    }
    @Test fun invalidSettingsDoNotEscapeElectricalRatings() {
        val u=element(OffGridType.INVERTER).ac!!
        for(v in listOf(0.0,1000.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(u.setVoltage(v))
        for(v in listOf(-1.0,201.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(u.setChargeAmps(v))
        assertTrue(u.setVoltage(120.0));assertTrue(u.setChargeAmps(0.0))
        val s=element(OffGridType.MPPT).solar!!;assertFalse(s.setVoltage(240.0));assertTrue(s.setVoltage(56.8));s.gridFeed=true;assertFalse(s.gridFeed)
    }
    @Test fun parallelBanksShareARealBusAndEqualizationCannotCreateEnergy() {
        val a=element(OffGridType.BANK16);val b=element(OffGridType.BANK32);val root=RootSystem(.05,1)
        a.bank!!.state.transfer(a.bank!!.state.capacity*.8);b.bank!!.state.transfer(b.bank!!.state.capacity*.2)
        for(e in listOf(a,b)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent);root.addProcess(e.bank!!.controller) }
        root.addComponent(Resistor(a.bank!!.positive,b.bank!!.positive).apply { resistance=.001 });root.addComponent(Resistor(a.bank!!.negative,b.bank!!.negative).apply { resistance=.001 })
        val before=a.bank!!.state.energy+b.bank!!.state.energy;val emptyBefore=b.bank!!.state.energy
        repeat(100) {
            root.step();a.bank!!.account(.05);b.bank!!.account(.05)
            assertTrue(abs(a.bank!!.source.current)<=100.001);assertTrue(abs(b.bank!!.source.current)<=200.001)
        }
        assertTrue(b.bank!!.state.energy>emptyBefore);assertTrue(a.bank!!.state.energy+b.bank!!.state.energy<before)
    }
    @Test fun bmsRefusesAnOvervoltageSourceAndTheCombinerNeedsANewFuseAfterOvercurrent() {
        val bank=element(OffGridType.BANK16);val root=RootSystem(.05,1)
        bank.electricalLoadList.forEach(root::addState);bank.electricalComponentList.forEach(root::addComponent);root.addProcess(bank.bank!!.controller)
        root.addComponent(VoltageSource("badCharger",bank.bank!!.positive,bank.bank!!.negative).apply { voltage=70.0 })
        repeat(5) { root.step();bank.bank!!.account(.05) }
        assertEquals(0.0,bank.bank!!.state.energy);assertFalse(bank.bank!!.source.enabled)
        val box=element(OffGridType.COMBINER);val r=RootSystem(.05,1);val p=box.protection!!
        box.electricalLoadList.forEach(r::addState);box.electricalComponentList.forEach(r::addComponent)
        r.addComponent(VoltageSource("pvFault",p.first,p.negative).apply { voltage=120.0 });r.addComponent(Resistor(p.output,p.negative).apply { resistance=1.0 })
        repeat(20) { r.step();p.step(.05) }
        assertTrue(p.fuses[0].blown);assertFalse(p.contacts[0].closed)
        p.fault="";p.enabled=false;p.update();p.enabled=true;p.update();assertFalse(p.contacts[0].closed)
    }
    @Test fun chargingLeavesHeadroomForBypassLoadsWithinOneHundredAmps() {
        val c=Circuit();c.load.resistance=3.0;c.ac.setChargeAmps(200.0);c.run(120)
        assertTrue(c.ac.bypass.closed,"${c.ac.fault}, ${c.ac.status}, ${c.ac.charger.status}")
        assertTrue(c.load.power>19_000);assertTrue(c.ac.chargeWatts>100)
        assertTrue(c.grid.current<=100.0002,"Combined service draw ${c.grid.current} A")
    }
    @Test fun externalPowerOnBackupOutputCannotBeExportedThroughTheGridInput() {
        val c=Circuit();c.root.addComponent(VoltageSource("wronglyConnectedBus",c.ac.load,null).apply { voltage=270.0 })
        repeat(40) { c.tick();assertTrue(c.grid.power>= -1e-6,"Reverse grid power ${c.grid.power}") }
        assertFalse(c.ac.bypass.closed);assertEquals("BACKFEED",c.ac.fault)
    }
    @Test fun solarGridChargerAndInverterWorkTogetherOnOnePhysicalBatteryBus() {
        val c=Circuit();val solar=element(OffGridType.MPPT);val u=solar.solar!!
        solar.electricalLoadList.forEach(c.root::addState);solar.electricalComponentList.forEach(c.root::addComponent)
        val pv=CurrentSource("sharedBusPv",u.solar,u.negative);val shunt=Resistor(u.solar,u.negative)
        val panel=SolarPanelPowerProcess(u.solar,u.negative,pv,shunt,222.0,180.0,16.0,17.2).apply { setLightFactor(1.0) }
        listOf(pv,shunt,Resistor(u.output,c.ac.dc).apply { resistance=.001 },Resistor(u.batteryReturn,c.ac.negative).apply { resistance=.001 }).forEach(c.root::addComponent)
        c.root.addProcess(panel);c.root.addProcess(u.controller)
        val start=c.bank.state.energy;var sunJ=0.0
        fun run(n: Int) { repeat(n) { c.tick();u.account(.05);sunJ+=u.pvWatts*.05 } }
        run(800);assertTrue(c.load.power>990);assertTrue(c.bank.state.energy>start);assertTrue(u.pvWatts>100,"PV ${u.pvWatts}, ${u.status}")
        assertTrue(c.bank.state.energy+u.energy+c.outputJ<=start+c.inputJ+sunJ+1e-2)
        val before=c.bank.state.energy;c.ac.mode=IslandMode.OFF_GRID;run(600)
        assertTrue(c.ac.outputWatts>990);assertEquals(0.0,c.ac.gridWatts)
        assertTrue(c.bank.state.energy>before,"Sun should supply the load and charge its surplus")
    }
    @Test fun solarFirstDoesNotMistakeTheUncomputedStartupBusForAnEmptyBattery() {
        val c=Circuit();val before=c.bank.state.energy;c.ac.mode=IslandMode.SELF_USE;c.run(100)
        assertTrue(c.ac.outputWatts>990);assertEquals(0.0,c.ac.gridWatts);assertTrue(c.bank.state.energy<before)
    }
    @Test fun lifepoColdChargeAndOvertemperatureProtectionUseAbsoluteCellTemperature() {
        val c=Circuit(OffGridType.AC_CHARGER,0.0);c.bank.thermal.temperatureCelsius= -30.0;c.run(80)
        assertEquals(-10.0,c.bank.temperature.celsius,1e-6);assertEquals(0.0,c.bank.state.energy)
        c.bank.thermal.temperatureCelsius=0.0;c.run(80);assertTrue(c.bank.state.energy>100)
        c.bank.thermal.temperatureCelsius=50.0;val before=c.bank.state.energy;c.run(40)
        assertEquals("TEMPERATURE",c.bank.state.fault);assertEquals(before,c.bank.state.energy)
    }
}
