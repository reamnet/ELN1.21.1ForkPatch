package mods.eln.transparentnode.renewable

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.utility.HouseServiceDescriptor
import mods.eln.utility.HouseServiceElement
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class SolarGridFeedTest {
    private fun element(hybrid: Boolean=false)=SolarElement(TransparentNode(),Eln.transparentNodeItem.subItemList[(if(hybrid)9 else 8)+(124 shl 6)]!!)
    private class Circuit(val nominal: Double=120.0) {
        val element=SolarGridFeedTest().element();val u=element.unit
        val root=RootSystem(.05,1)
        val meter=HouseServiceElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.meter && !it.wide })
        val mains=SwitchableVoltageSource("absorbing utility grid").apply { connectTo(meter.line[2],null);voltage=nominal;enabled=true }
        val pv=SwitchableVoltageSource("paid PV boundary").apply { connectTo(u.solar,u.negative);voltage=850.0;enabled=true }
        val load=Resistor(u.output,null).apply { resistance=nominal*nominal/100.0 }
        var pvJ=0.0;var generatedJ=0.0
        init {
            meter.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));meter.inventoryChange(meter.inventory)
            (element.electricalLoadList+meter.electricalLoadList).forEach(root::addState)
            (element.electricalComponentList+meter.electricalComponentList).forEach(root::addComponent)
            root.addComponent(mains);root.addComponent(pv);root.addComponent(load)
            root.addComponent(ElectricalConnection(meter.load[2],u.output));root.addProcess(u.controller)
            u.setVoltage(nominal);u.gridFeed=true
        }
        fun run(n: Int,physical:()->Unit={}) { repeat(n) {
            root.step();u.account(.05);physical();meter.electricalProcessList.forEach { it.process(.05) }
            pvJ+=u.pvWatts*.05;generatedJ+=u.outputWatts*.05
            assertNotEquals("PROTECTED",u.status,u.diagnostic)
            assertTrue(u.outputSource.current in -.00001..200.0002)
            assertTrue(u.outputWatts<=u.rating*1.000001)
            assertTrue(generatedJ+u.energy*.97<=pvJ*.98*.97+.02,"Grid feed must spend accepted PV energy")
        } }
    }
    @Test fun gridFeedReallyReversesTheServiceMeterAfterLocalLoadsAndKeepsImportSeparate() {
        val c=Circuit();c.run(160)
        assertEquals("EXPORTING",c.u.status);assertTrue(c.u.feedWatts>9000)
        assertTrue(c.mains.power< -8000);assertTrue(c.meter.energy.exportedKwh>.01)
        val imports=c.meter.energy.importedKwh;val exported=c.meter.energy.exportedKwh
        c.load.resistance=120.0*120/15000;c.run(100)
        assertTrue(c.meter.energy.importedKwh>imports);assertEquals(exported,c.meter.energy.exportedKwh,1e-10)
        assertTrue(c.u.feedWatts>9000,"Local consumption reduces sell-back, not inverter generation")
    }
    @Test fun gridFeedCannotStartADeadBusAndStopsOnLossOvervoltageNightAndDisable() {
        val c=Circuit();c.mains.enabled=false;c.run(100)
        assertEquals("WAIT_GRID",c.u.status);assertFalse(c.u.outputSource.enabled);assertEquals(0.0,c.u.fedJoules)
        c.mains.enabled=true;c.run(100);assertTrue(c.u.feedWatts>9000)
        c.mains.voltage=140.0;c.run(4);assertEquals("GRID_VOLTAGE",c.u.status);assertEquals(0.0,c.u.feedWatts)
        c.mains.voltage=80.0;c.run(4);assertEquals("WAIT_GRID",c.u.status)
        c.mains.voltage=120.0;c.run(40);assertTrue(c.u.feedWatts>9000)
        c.pv.enabled=false;c.run(80);assertEquals(0.0,c.u.feedWatts);assertEquals("NO_POWER",c.u.status)
        c.pv.enabled=true;c.run(100);c.u.enabled=false;c.run(2);assertEquals("DISABLED",c.u.status);assertEquals(0.0,c.u.feedWatts)
    }
    @Test fun lowVoltageExportHonoursCurrentLimitAndOrdinaryOutputStillBlocksBackfeed() {
        val c=Circuit(12.0);c.run(160);assertTrue(c.u.feedWatts in 2300.0..2640.0,"feed ${c.u.feedWatts}, current ${c.u.outputSource.current}, state ${c.u.status}");assertEquals(200.0,c.u.outputSource.current,.001)
        c.mains.voltage=12.5;c.u.gridFeed=false;c.run(20);assertEquals("BACKFEED",c.u.status);assertEquals(0.0,c.u.feedWatts)
        c.mains.voltage=12.0;c.u.gridFeed=true;c.run(40);assertTrue(c.u.feedWatts>2300)
        val hybrid=element(true).unit;hybrid.gridFeed=true;assertFalse(hybrid.gridFeed)
    }
    @Test fun modeAndCounterSurviveSaveButItemsCannotDuplicateEnergyOrCounters() {
        val c=Circuit();c.run(100);val saved=CompoundTag().also(c.element::writeToNBT)
        val restored=element();restored.readFromNBT(saved)
        assertTrue(restored.unit.gridFeed);assertEquals(c.u.fedJoules,restored.unit.fedJoules)
        restored.readItemStackNBT(c.element.getItemStackNBT());assertTrue(restored.unit.gridFeed)
        assertEquals(0.0,restored.unit.energy);assertEquals(0.0,restored.unit.fedJoules)
        restored.unit.read(CompoundTag());assertFalse(restored.unit.gridFeed,"Legacy saves retain regulated output")
    }
    @Test fun twoMpptsShareAnEnergizedGridWithoutCreatingEnergyOrFightingVoltageRegulation() {
        val c=Circuit();val second=element();val u=second.unit;u.gridFeed=true
        second.electricalLoadList.forEach(c.root::addState);second.electricalComponentList.forEach(c.root::addComponent)
        c.root.addComponent(SwitchableVoltageSource("second paid PV boundary").apply { connectTo(u.solar,u.negative);voltage=850.0;enabled=true })
        c.root.addComponent(Resistor(u.output,c.u.output).apply { resistance=.002 });c.root.addProcess(u.controller)
        var input=0.0;var output=0.0
        c.run(300) {
            u.account(.05);input+=u.pvWatts*.05;output+=u.outputWatts*.05
            assertNotEquals("PROTECTED",u.status,u.diagnostic)
            assertTrue(output+u.energy*.97<=input*.98*.97+.02)
            assertTrue(u.outputSource.current in -.00001..200.0002)
        }
        assertTrue(c.u.feedWatts>9000);assertTrue(u.feedWatts>9000)
        assertTrue(c.mains.power< -17000);assertTrue(c.meter.energy.exportedKwh>.05)
    }
}
