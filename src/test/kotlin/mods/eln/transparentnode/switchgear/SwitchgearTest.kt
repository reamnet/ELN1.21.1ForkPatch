package mods.eln.transparentnode.switchgear

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class SwitchgearTest {
    private fun element(kind: SwitchgearKind)=SwitchgearElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<SwitchgearDescriptor>().single { it.kind==kind })
    private class Circuit(val device: SwitchgearElement,val mode: RelayControl=RelayControl.SIGNAL) {
        val root=RootSystem(.01,1)
        val signal=VoltageSource("control",device.gate,null)
        val coil=VoltageSource("coil",device.coilA,device.coilB)
        val feeds=device.commons.mapIndexed { i,c->VoltageSource("feed $i",c,null).apply { voltage=120.0+i*17 } }
        val noLoads=device.normallyOpen.map { Resistor(it,null).apply { resistance=12.0 } }
        val ncLoads=device.normallyClosed.map { Resistor(it,null).apply { resistance=24.0 } }
        init {
            device.electricalLoadList.forEach(root::addState);device.electricalComponentList.forEach(root::addComponent)
            (feeds+listOf(signal,coil)).forEach(root::addComponent);(noLoads+ncLoads).forEach(root::addComponent)
            device.setControl(mode);advance(6)
        }
        fun advance(n: Int=1) { repeat(n) {
            device.step(.01)
            assertTrue(device.noContacts.indices.all { i->!device.noContacts[i].closed || device.ncContacts.getOrNull(i)?.closed!=true })
            root.step()
        } }
    }
    @Test fun allContactFamiliesKeepEachPowerCircuitIsolatedAndReportRealPower() {
        for(kind in SwitchgearKind.entries) {
            val d=element(kind);val c=Circuit(d)
            assertFalse(d.energized);assertTrue(d.noContacts.all { !it.closed });assertTrue(d.ncContacts.all { it.closed })
            c.noLoads.forEach { assertEquals(0.0,it.power,1e-8) }
            c.ncLoads.forEach { assertTrue(it.power>100) }
            c.signal.voltage=Eln.SVU;c.advance(8)
            assertTrue(d.energized);assertTrue(d.noContacts.all { it.closed });assertTrue(d.ncContacts.all { !it.closed })
            for(i in d.commons.indices)assertEquals(c.feeds[i].voltage*12/(12+kind.resistance),d.normallyOpen[i].voltage,1e-7)
            c.ncLoads.forEach { assertEquals(0.0,it.power,1e-8) }
            assertEquals(c.feeds.sumOf { it.power },c.noLoads.sumOf { it.power }+d.contactLosses,1e-7)
            c.signal.voltage=0.0;c.advance(8);assertFalse(d.energized)
            assertTrue(d.ncContacts.all { it.closed });c.noLoads.forEach { assertEquals(0.0,it.power,1e-8) }
            assertEquals(kind.poles,d.commons.size)
            for(i in 0 until kind.poles) {
                assertSame(d.commons[i],d.getElectricalLoad(d.front.left(),LRDU.entries[i]))
                assertSame(d.normallyOpen[i],d.getElectricalLoad(d.front.right(),LRDU.entries[i]))
            }
        }
    }
    @Test fun bothCoilVoltagesConsumeRatedPowerAndUsePickupDropoutHysteresis() {
        for(kind in SwitchgearKind.entries)for(mode in listOf(RelayControl.COIL_120,RelayControl.COIL_240)) {
            val d=element(kind);val c=Circuit(d,mode)
            c.signal.voltage=Eln.SVU;c.coil.voltage=mode.nominal*.8;c.advance(8);assertFalse(d.energized)
            c.coil.voltage=mode.nominal;c.advance(8);assertTrue(d.energized)
            assertEquals(kind.coilWatts,d.coil.power,1e-8)
            assertEquals(kind.coilWatts,c.coil.power,1e-8)
            c.coil.voltage=mode.nominal*.70;c.advance(8);assertTrue(d.energized)
            c.coil.voltage=mode.nominal*.55;c.advance(8);assertFalse(d.energized)
        }
    }
    @Test fun solidStateLossBecomesHeatAndFaultsLatchAllContactsOpenAcrossReload() {
        val d=element(SwitchgearKind.SSR);val c=Circuit(d)
        c.signal.voltage=Eln.SVU;c.noLoads[0].resistance=4.0;c.advance(100)
        assertTrue(d.contactLosses>15.0);assertTrue(d.temperature>20.0);assertFalse(d.tripped)
        c.noLoads[0].resistance=.2;c.advance(15)
        assertTrue(d.tripped);assertFalse(d.noContacts[0].closed)
        val restored=element(SwitchgearKind.SSR);restored.readFromNBT(CompoundTag().also(d::writeToNBT))
        assertTrue(restored.tripped);assertFalse(restored.noContacts[0].closed)
        restored.resetTrip();assertFalse(restored.tripped)
        val control=element(SwitchgearKind.RELAY_FOUR);control.setControl(RelayControl.COIL_240);control.setEnabled(false)
        val copy=element(SwitchgearKind.RELAY_FOUR);copy.readItemStackNBT(control.getItemStackNBT())
        assertEquals(RelayControl.COIL_240,copy.control);assertFalse(copy.enabled)
        assertTrue(copy.noContacts.all { !it.closed });assertTrue(copy.ncContacts.all { !it.closed })
    }
    @Test fun everyNewSwitchgearItemHasItsOwnStableIdAndOriginalModel() {
        assertEquals(6,SwitchgearKind.entries.map { it.subId }.toSet().size)
        for(kind in SwitchgearKind.entries) {
            val d=Eln.transparentNodeItem.subItemList[kind.subId+(125 shl 6)] as SwitchgearDescriptor
            val fresh=element(kind);fresh.readItemStackNBT(CompoundTag());assertEquals(20.0,fresh.temperature,0.0)
            assertEquals(kind,d.kind);assertSame(Eln.creativeTabPowerElectronics,d.creativeTab)
            val model=Obj3D().apply { assertTrue(loadFile("${kind.model}/${kind.model}.obj")) }
            for(part in listOf("Base","Case","Terminal","Control","Indicator","NO"))assertNotNull(model.getPart(part),"${kind.name} / $part")
            if(kind.changeover)assertNotNull(model.getPart("NC"))
            assertTrue(model.yDim<1.0)
        }
    }
}
