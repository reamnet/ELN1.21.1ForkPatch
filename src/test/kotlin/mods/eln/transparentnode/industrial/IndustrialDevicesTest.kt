package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.utility.PoleHardware
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.Test
import kotlin.test.*

class IndustrialDevicesTest {
    private fun device(kind: IndustrialKind)=IndustrialElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList[kind.ordinal+(126 shl 6)] as IndustrialDescriptor)
    private class Circuit(val d: IndustrialElement,val volts: Double=d.nominal) {
        val root=RootSystem(.05,1)
        val source=VoltageSource("industrial test supply",d.input,null).apply { voltage=volts }
        val load=Resistor(d.outputs[0],null).apply { resistance=1e19 }
        init { d.electricalLoadList.forEach(root::addState);d.electricalComponentList.forEach(root::addComponent);root.addComponent(source);root.addComponent(load) }
        fun advance(count: Int=1) { repeat(count) { d.prepare(true,6000);root.step();d.account(.05);d.tick(.05);d.prepare(true,6000) } }
    }
    @Test fun loadBankUsesRealVoltageSquaredPowerAndStopsDrawingWhenDisabled() {
        val d=device(IndustrialKind.LOAD_BANK);val c=Circuit(d)
        c.advance(5);assertEquals(50000.0,d.resistor.power,1e-5)
        c.source.voltage=432.0;c.advance(5);assertEquals(40500.0,d.resistor.power,1e-5)
        assertTrue(d.temperature>20);assertTrue(d.lifetimeJoules>0)
        d.setEnabled(false);c.advance(3);assertTrue(d.resistor.power<1e-6)
    }
    @Test fun transformerIsPassiveFollowsSagAndRequiresBothPaidItems() {
        val d=device(IndustrialKind.PAD);val c=Circuit(d);c.load.resistance=24.0
        c.advance();assertEquals(0.0,d.outputs[0].voltage,1e-6)
        d.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[0],1));d.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));d.inventoryChange(d.inventory)
        c.advance(5);val full=d.outputs[0].voltage;assertTrue(full in 239.0..240.0)
        c.source.voltage=d.nominal*.95;c.advance(5);assertEquals(full*.95,d.outputs[0].voltage,1e-5)
        assertEquals(c.source.power,c.load.power+d.winding.power,1e-5)
        d.setEnabled(false);c.advance(3);assertTrue(c.load.power<1e-7)
    }
    @Test fun padFuseBlowsUnderSustainedOverloadAndCannotResetWithoutReplacement() {
        val d=device(IndustrialKind.PAD);val c=Circuit(d);c.load.resistance=.5
        d.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[0],1));d.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));d.inventoryChange(d.inventory)
        c.advance(45);assertTrue(d.protection.tripped);assertTrue(PoleHardware.fuse(d.inventory.getItem(1))!!.spent)
        d.resetTrip();c.advance(2);assertFalse(d.tap.enabled);assertTrue(c.load.power<1e-5)
        c.load.resistance=24.0;d.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));d.inventoryChange(d.inventory);c.advance(2);assertTrue(c.load.power>2000)
    }
    @Test fun driveRampsAndConservesPowerWithFiniteShortCircuitCurrent() {
        val d=device(IndustrialKind.DRIVE);assertTrue(d.applySettings(doubleArrayOf(480.0,480.0,30.0,5.0,100.0)))
        val c=Circuit(d);c.load.resistance=24.0;c.advance(20);assertEquals(12.0,d.hertz,1e-8)
        c.advance(40);assertEquals(30.0,d.hertz,1e-8);assertEquals(200.0,d.outputs[0].voltage,1e-4)
        assertEquals(c.source.power,c.load.power+d.winding.power,1e-5)
        c.load.resistance=.001;c.advance(2);assertTrue(d.outputCurrent<=50.001)
        d.setEnabled(false);c.advance(10);assertEquals(24.0,d.hertz,1e-7);assertTrue(d.tap.enabled);c.advance(50);assertFalse(d.tap.enabled)
    }
    @Test fun relayHasDelayedLatchedTripsAndIndependentForwardReturnMeasurements() {
        val d=device(IndustrialKind.RELAY);d.applySettings(doubleArrayOf(480.0,10.0,.8,1.2,.5,0.0));val c=Circuit(d);c.load.resistance=24.0
        c.advance(8);assertFalse(d.protection.tripped);assertTrue(d.contacts[0].closed)
        c.advance(5);assertTrue(d.protection.tripped);assertFalse(d.contacts[0].closed)
        c.load.resistance=96.0;d.resetTrip();c.advance(3);assertFalse(d.protection.tripped);assertTrue(c.load.power>2000)
        d.applySettings(doubleArrayOf(480.0,100.0,.8,1.2,.1,.1));c.advance(4);assertTrue(d.protection.tripped);assertEquals(3,d.protection.reason)
        assertSame(d.returnIn,d.getElectricalLoad(d.front.left(),LRDU.Right));assertSame(d.returnOut,d.getElectricalLoad(d.front.right(),LRDU.Right))
    }
    @Test fun healthyTwoWireReturnDoesNotTripDifferentialProtection() {
        val p=Protection();repeat(100) { p.step(.05,240.0,20.0,-20.0,240.0,30.0,.8,1.2,.1,.05) };assertFalse(p.tripped)
        repeat(3) { p.step(.05,240.0,20.0,-19.8,240.0,30.0,.8,1.2,.1,.05) };assertTrue(p.tripped)
    }
    @Test fun loadShedderDropsLowestPriorityFirstAndRestoresSlowly() {
        val d=device(IndustrialKind.SHEDDER);val c=Circuit(d,190.0)
        c.advance(21);assertContentEquals(booleanArrayOf(true,true,true,false),d.shed.closed)
        c.advance(20);assertContentEquals(booleanArrayOf(true,true,false,false),d.shed.closed)
        c.source.voltage=240.0;c.advance(99);assertFalse(d.shed.closed[2]);c.advance(3);assertTrue(d.shed.closed[2]);assertFalse(d.shed.closed[3])
        c.advance(102);assertTrue(d.shed.closed.all { it })
    }
    @Test fun chargerStoresActualEnergyNeverOverfillsAndRespectsSchedule() {
        val d=device(IndustrialKind.CHARGER);val stack=Eln.findItemStack(IndustrialItems.PACK,1);val b=IndustrialElement.battery(stack)!!;d.inventory.setItem(0,stack)
        val c=Circuit(d);c.advance(20);assertEquals(7200.0*.94,b.getEnergy(stack),1e-4);assertEquals(b.getEnergy(stack),d.sessionJoules,1e-4)
        b.setEnergy(stack,b.getEnergyMax(stack)-1);c.advance(3);assertEquals(b.getEnergyMax(stack),b.getEnergy(stack),1e-6)
        assertTrue(d.resistor.power<1e-5)
        assertTrue(IndustrialEnergy.schedule(23000,22,6));assertFalse(IndustrialEnergy.schedule(12000,22,6));assertTrue(IndustrialEnergy.schedule(18000,22,6));assertFalse(IndustrialEnergy.schedule(0,22,6));assertTrue(IndustrialEnergy.schedule(12000,0,0))
    }
    @Test fun weldingConsumesTwoInputsAndEnergyWithoutDuplicatingOutput() {
        val d=device(IndustrialKind.WELDER);val c=Circuit(d)
        d.inventory.setItem(0,ItemStack(Items.IRON_INGOT,3));d.inventory.setItem(1,ItemStack(Items.IRON_INGOT,3))
        c.advance(399);assertTrue(d.inventory.getItem(2).isEmpty)
        c.advance(5);assertEquals(Eln.findItemStack(IndustrialItems.FRAME,1).item,d.inventory.getItem(2).item);assertEquals(2,d.inventory.getItem(0).count);assertEquals(2,d.inventory.getItem(1).count)
        c.advance(500);assertEquals(1,d.inventory.getItem(2).count);assertEquals(2,d.inventory.getItem(0).count)
    }
    @Test fun changingWeldingInputCannotReuseWorkAndRepairPreservesComponents() {
        val d=device(IndustrialKind.WELDER);val c=Circuit(d)
        d.inventory.setItem(0,ItemStack(Items.IRON_INGOT));d.inventory.setItem(1,ItemStack(Items.IRON_INGOT,3));c.advance(200);assertTrue(d.workJoules>50000)
        val tool=ItemStack(Items.IRON_PICKAXE).also { it.damageValue=200;it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Kept name")) };d.inventory.setItem(0,tool)
        c.advance(2);assertTrue(d.workJoules<1000)
        c.advance(400);val result=d.inventory.getItem(2);assertEquals(138,result.damageValue);assertEquals("Kept name",result.hoverName.string)
    }
    @Test fun arresterAbsorbsFiniteElectricalSurgeEnergyAndFailsOpen() {
        val d=device(IndustrialKind.ARRESTER);val c=Circuit(d,d.nominal*1.4)
        c.advance(2);assertTrue(d.surgeRemaining<200000);assertTrue(d.resistor.power>100000)
        c.advance(20);assertEquals(0.0,d.surgeRemaining);assertTrue(d.resistor.power<1e-5)
        val restored=device(IndustrialKind.ARRESTER);restored.readItemStackNBT(d.getItemStackNBT());assertEquals(0.0,restored.surgeRemaining)
    }
    @Test fun allDeviceSettingsAndTripsSurviveSaveAndRejectedPacketsDoNotMutateThem() {
        for(kind in IndustrialKind.entries) {
            val d=device(kind);d.setEnabled(false);d.protection.tripped=true
            val p=d.settings.copyOf();p[0]=Double.NaN;assertFalse(d.applySettings(p));assertContentEquals(kind.defaults,d.settings)
            val copy=device(kind);copy.readFromNBT(CompoundTag().also(d::writeToNBT));assertFalse(copy.enabled);assertTrue(copy.protection.tripped);assertContentEquals(d.settings,copy.settings)
        }
    }
    @Test fun allNewIdsModelsAndCraftingStacksResolveWithoutChangingOldIds() {
        for(kind in IndustrialKind.entries) {
            val desc=Eln.transparentNodeItem.subItemList[kind.ordinal+(126 shl 6)] as IndustrialDescriptor
            assertEquals(kind,desc.kind);assertFalse(Eln.findItemStack(kind.title,1).isEmpty)
            val mesh=Obj3D().apply { assertTrue(loadFile("${kind.model}/${kind.model}.obj")) }
            assertNotNull(mesh.getPart("Indicator"));assertNotNull(mesh.getPart("Input"))
        }
        for(name in listOf(IndustrialItems.FRAME,IndustrialItems.PACK,IndustrialItems.FORKLIFT,IndustrialItems.PALLET))assertFalse(Eln.findItemStack(name,1).isEmpty)
        assertEquals(0.0,IndustrialElement.battery(Eln.findItemStack(IndustrialItems.PACK,1))!!.getEnergy(Eln.findItemStack(IndustrialItems.PACK,1)))
    }
    @Test fun remoteControlDoesNotAutoResetTrippedBreakers() {
        val b=mods.eln.utility.PanelBreaker();assertTrue(b.step(3.0,500.0,5.0));assertTrue(b.tripped)
        b.remoteEnabled(false);b.remoteEnabled(true);assertTrue(b.tripped)
    }
    @Test fun pairedRelayConductorsPassHealthyLoadButDetectAnActualGroundLeak() {
        val d=device(IndustrialKind.RELAY);d.applySettings(doubleArrayOf(480.0,100.0,.8,1.2,.1,.1));val c=Circuit(d)
        c.root.addComponent(VoltageSource("return ground",d.returnIn,null).apply { voltage=0.0 })
        c.root.addComponent(Resistor(d.outputs[0],d.returnOut).apply { resistance=24.0 })
        c.advance(10);assertFalse(d.protection.tripped);assertEquals(-d.contacts[0].current,d.returnContact.current,1e-5)
        c.load.resistance=4800.0;c.advance(2);assertFalse(d.protection.tripped)
        c.load.resistance=2400.0;c.advance(4);assertTrue(d.protection.tripped);assertEquals(3,d.protection.reason)
    }
    @Test fun wiredSignalTripsThePhysicalRelayContactsAndPublishesALatchedFault() {
        val d=device(IndustrialKind.RELAY);val c=Circuit(d);c.load.resistance=48.0
        c.root.addComponent(VoltageSource("trip signal",d.signal,null).apply { voltage=Eln.SVU })
        c.advance(3);assertTrue(d.protection.tripped);assertEquals(5,d.protection.reason);assertFalse(d.contacts[0].closed)
    }
    @Test fun chargerSessionResetsForANewPackAndAutomationCannotInsertIntoWeldingOutput() {
        val d=device(IndustrialKind.CHARGER);val c=Circuit(d);d.inventory.setItem(0,Eln.findItemStack(IndustrialItems.PACK,1));c.advance(20);assertTrue(d.sessionJoules>6000)
        val second=Eln.findItemStack(IndustrialItems.PACK,1);d.inventory.setItem(0,second);c.advance(2);assertTrue(d.sessionJoules<1000)
        assertEquals(1,d.inventory.maxStackSize)
        assertFalse(d.accepts(0,second.copyWithCount(2)))
        val welder=device(IndustrialKind.WELDER);assertFalse(welder.inventory.canPlaceItemThroughFace(2,ItemStack(Items.IRON_INGOT),0));assertTrue(welder.inventory.canTakeItemThroughFace(2,ItemStack(Items.IRON_INGOT),0))
    }
    @Test fun installedPoleKitAbsorbsSwitchingSurgeOnceAndKeepsItsCartridgeEnergyAcrossReload() {
        val d=device(IndustrialKind.RELAY);val kit=PoleSurgeUpgrade(d,listOf(d.input),480.0)
        kit.read(CompoundTag().apply { putBoolean("poleArrester",true);putDouble("poleArresterJoules",200000.0) })
        val c=Circuit(d,672.0)
        repeat(3) {
            d.slowPreProcessList.forEach { it.process(.05) };c.root.step();d.electricalProcessList.forEach { it.process(.05) }
        }
        assertTrue(kit.remaining in 1000.0..199999.0)
        val tag=CompoundTag().also(kit::write);val copyOwner=device(IndustrialKind.RELAY);val copy=PoleSurgeUpgrade(copyOwner,listOf(copyOwner.input),480.0);copy.read(tag)
        assertTrue(copy.fitted);assertEquals(kit.remaining,copy.remaining,1e-8)
        val cartridge=device(IndustrialKind.ARRESTER);cartridge.readItemStackNBT(kit.item().tagCompound);assertEquals(kit.remaining,cartridge.surgeRemaining,1e-8)
    }

    @Test fun lightningEnergyDoesNotBecomeAnExtraLoadOnAHealthyGrid() {
        val d=device(IndustrialKind.ARRESTER);val c=Circuit(d);c.advance(3)
        d.absorbLightning();c.advance(6);assertEquals(160000.0,d.surgeRemaining,1e-6);assertTrue(d.resistor.power<1e-5)
        val owner=device(IndustrialKind.RELAY);val kit=PoleSurgeUpgrade(owner,listOf(owner.input),480.0)
        kit.read(CompoundTag().apply { putBoolean("poleArrester",true);putDouble("poleArresterJoules",200000.0) })
        val supply=Circuit(owner);assertTrue(kit.lightning())
        repeat(6) { owner.slowPreProcessList.forEach { it.process(.05) };supply.root.step();owner.electricalProcessList.forEach { it.process(.05) } }
        assertEquals(160000.0,kit.remaining,1e-6);assertTrue(supply.source.power<1e-5)
    }

    @Test fun elevatedSupplyCannotChargeAPackAboveItsAllowedRate() {
        val d=device(IndustrialKind.CHARGER);d.applySettings(doubleArrayOf(240.0,50000.0,0.0,0.0))
        val stack=Eln.findItemStack(IndustrialItems.PACK,1);val battery=IndustrialElement.battery(stack)!!;d.inventory.setItem(0,stack)
        val c=Circuit(d,300.0);c.advance(20)
        assertEquals(battery.getChargePower(stack),battery.getEnergy(stack),1e-5)
        assertTrue(d.lifetimeJoules>battery.getEnergy(stack))
    }

}
