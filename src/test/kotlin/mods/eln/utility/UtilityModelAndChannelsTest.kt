package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Obj3D
import mods.eln.sim.ElectricalLoad
import mods.eln.sixnode.lampsocket.DecorativeLampStyle
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import org.junit.Test
import kotlin.test.*

class UtilityModelAndChannelsTest {
    @Test fun auraHasARealThirtySixInchBladeSpanAndSevenSeparateRecessedLenses() {
        val model=Obj3D();assertTrue(model.loadFile("auraceilingfan/auraceilingfan.obj"))
        val blades=assertNotNull(model.getPart("Rotor")).boundingBox()
        assertTrue(blades.max.y-blades.min.y in .75..1.02)
        assertTrue(blades.max.z-blades.min.z in .75..1.02)
        repeat(7) { i -> val lens=assertNotNull(model.getPart("Light$i")).boundingBox();assertTrue(lens.min.x>.47 && lens.max.x<.49) }
        assertNull(model.getPart("Light7"))
        val d=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==DecorativeLampStyle.FAN_AURA }
        assertEquals(7,d.bulbCapacity);assertTrue(DecorativeLampStyle.FAN_AURA.fan);assertTrue(DecorativeLampStyle.FAN_AURA.ceiling)
    }
    @Test fun oldPoleHeadAndTowerAreRaisedExactlyThreeMetresAndKeepTheirOriginalFootings() {
        for((folder,old,tall) in listOf(Triple("powerpole","utilitypole","utilitypoletall"),Triple("transmissiontower","transmissiontower","transmissiontowertall"))) {
            val before=Obj3D();val after=Obj3D();assertTrue(before.loadFile("$folder/$old.obj"));assertTrue(after.loadFile("$folder/$tall.obj"))
            assertEquals(before.yMin.toDouble(),after.yMin.toDouble(),1e-6);assertEquals(before.yMax.toDouble()+3,after.yMax.toDouble(),1e-5)
            val a=assertNotNull(before.getPart("p0")).boundingBox();val b=assertNotNull(after.getPart("p0")).boundingBox()
            assertEquals(a.min.y+3,b.min.y,1e-5)
        }
    }
    @Test fun utilityEquipmentAndHouseServiceMeshesActuallyContainTheirOperatingParts() {
        val pole=Obj3D();assertTrue(pole.loadFile("distributionpole/distributionpole.obj"))
        for(name in listOf("main","SecondaryRack","Transformer","Recloser","Handle","LampLens"))assertNotNull(pole.getPart(name))
        repeat(3) { i -> for(prefix in listOf("Pin","FuseDoor","Riser","Regulator","Capacitor","Knife"))assertNotNull(pole.getPart("$prefix$i")) }
        val meter=Obj3D();assertTrue(meter.loadFile("northamericanmeterbase/northamericanmeterbase.obj"))
        for(name in listOf("Case","Socket","Glass","MeterFace","Disk","SmartScreen","Disconnect"))assertNotNull(meter.getPart(name))
        val panel=Obj3D();assertTrue(panel.loadFile("twelvecircuitpanel/twelvecircuitpanel.obj"));repeat(12) { assertNotNull(panel.getPart("Breaker$it"));assertNotNull(panel.getPart("Toggle$it")) }
    }
    private class Supply(override val owner: Any,override val coordinate: Coordinate): WirelessPowerSupply {
        override val powerLoad=ElectricalLoad();override val range=32
        override fun getChannelState(id: Int)=true
        override fun addToRp(resistance: Double) {}
    }
    @Test fun panelChannelsAreDimensionScopedAndWithdrawingAnOwnerDisconnectsBridgesFirst() {
        val owner=Any();val source=Supply(owner,Coordinate(0,64,0,0));val h=WirelessPowerHandle(source,0)
        val channel="utility-registry-${System.nanoTime()}";WirelessPowerChannels.register(channel,h)
        assertSame(h,WirelessPowerChannels.channels(channel)!!.single());assertTrue(source.isInRange(Coordinate(32,64,0,0)));assertFalse(source.isInRange(Coordinate(33,64,0,0)));assertFalse(source.isInRange(Coordinate(0,64,0,1)))
        var removed=false;WirelessPowerChannels.trackBridge(Any(),owner) { removed=true }
        WirelessPowerChannels.disconnectOwner(owner)
        assertTrue(removed);assertNull(WirelessPowerChannels.channels(channel))
    }
    @Test fun replacingOneCircuitNameDoesNotUnregisterAnotherBreakersChannel() {
        val owner=Any();val s=Supply(owner,Coordinate(0,0,0,0));val a=WirelessPowerHandle(s,0);val b=WirelessPowerHandle(s,1)
        val name="utility-channel-${System.nanoTime()}";WirelessPowerChannels.register(name,a);WirelessPowerChannels.register(name,b)
        WirelessPowerChannels.unregister(a);assertSame(b,WirelessPowerChannels.channels(name)!!.single())
        WirelessPowerChannels.disconnectOwner(owner);assertNull(WirelessPowerChannels.channels(name))
    }
    @Test fun closingAnUnloadedFuseDoesNotArcButPickingUpRealCurrentDoes() {
        val f=CutoutFuse();f.operate();f.operate();assertFalse(f.pickup(.1,0.0));assertTrue(f.pickup(.1,2.0));assertFalse(f.pickup(.1,2.0))
        f.operate();f.operate();repeat(10) { assertFalse(f.pickup(.1,0.0)) };assertFalse(f.pickup(.1,2.0))
    }
    @Test fun poleItemsKeepBlownLinksAndSettingsWithoutCopyingWorldWiresOrInsertingAnUpgradeTwice() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.TRANSFORMER_THREE }
        val a=DistributionPoleElement(mods.eln.node.transparent.TransparentNode(),d)
        assertTrue(a.fuses[0].step(.1,60.0,20.0))
        val tag=a.getItemStackNBT()
        assertFalse(tag.contains("gridLinks"));assertFalse(tag.contains("inv"))
        val b=DistributionPoleElement(mods.eln.node.transparent.TransparentNode(),d);b.readItemStackNBT(tag)
        assertTrue(b.fuses[0].blown);assertFalse(b.fuses[0].closed);assertFalse(b.installed);assertTrue(b.gridLinkList.isEmpty())
        val streetD=Eln.transparentNodeItem.subItemList.values.filterIsInstance<mods.eln.transparentnode.utilitylight.UtilityStreetLightDescriptor>().first()
        val street=mods.eln.transparentnode.utilitylight.UtilityStreetLightElement(mods.eln.node.transparent.TransparentNode(),streetD)
        assertTrue(street.fuse.step(.1,60.0,20.0))
        val streetCopy=mods.eln.transparentnode.utilitylight.UtilityStreetLightElement(mods.eln.node.transparent.TransparentNode(),streetD);streetCopy.readItemStackNBT(street.getItemStackNBT())
        assertTrue(streetCopy.fuse.blown);assertFalse(streetCopy.installed)
    }
    @Test fun movedMeterBasesKeepTheirEnergyRegistersAndNeverCopyTheRemovableMeterItem() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.meter && !it.wide }
        val a=HouseServiceElement(mods.eln.node.transparent.TransparentNode(),d);a.energy.step(3600.0,1200.0)
        val b=HouseServiceElement(mods.eln.node.transparent.TransparentNode(),d);b.readItemStackNBT(a.getItemStackNBT())
        assertEquals(1.2,b.energy.importedKwh,1e-12);assertEquals(0,b.meterType);assertTrue(b.gridLinkList.isEmpty())
    }

}
