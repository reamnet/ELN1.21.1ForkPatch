package mods.eln.utility

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.BlockPos
import mods.eln.misc.Direction
import org.junit.Test
import java.io.*
import java.util.UUID
import kotlin.test.*

class HouseholdDistributionTest {
    private fun outlet(strip: Boolean=false,rating: CordRating=CordRating.V120)=OutletElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<OutletDescriptor>().single { it.strip==strip && it.rating==rating })
    @Test fun repeatedReportsForTwentyHalogensRemainOne5400WLoadOnAThirtyAmpBranch() {
        val desc=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single()
        val p=BreakerPanelElement(TransparentNode(),desc);val c=p.circuits[0]
        p.inventory.setItem(0,Eln.findItemStack("30A Electrical Breaker",1));c.use240=true;c.configure()
        val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        root.addComponent(VoltageSource("240 V",p.buses[2],null).apply { voltage=240.0 })
        val bulb=Eln.sharedItem.getDescriptor(Eln.findItemStack("240V Halogen Light Bulb",20)) as mods.eln.item.lampitem.LampDescriptor
        assertEquals(270.0,bulb.lampData.nominalP)
        val fixture=Any()
        try {
            repeat(1000) {
                repeat(3) { WirelessLoadDemand.report(fixture,c.handle,bulb.lampData.resistance/20) }
                c.refreshDemand();root.step()
                assertEquals(22.5,c.contacts[2].current,.01)
                assertFalse(c.breaker.step(.05,c.contacts[2].current,c.rating))
            }
            assertEquals(1,c.fixtureDemand.size)
            WirelessLoadDemand.report(fixture,null,Double.POSITIVE_INFINITY);c.refreshDemand();root.step()
            assertEquals(0.0,c.contacts[2].current,1e-6)
        } finally { WirelessLoadDemand.disconnectOwner(fixture) }
    }
    @Test fun outletReservationsUseBottomThenTopAndNeverAllowAThirdCord() {
        val o=outlet();val cords=List(3) { outlet().cord }
        assertEquals(0,o.freeSocket());assertTrue(o.reserve(cords[0],0));o.claim(cords[0],0)
        assertEquals(1,o.freeSocket());assertTrue(o.reserve(cords[1],1));o.claim(cords[1],1)
        assertNull(o.freeSocket());assertFalse(o.canClaim(cords[2],0));assertFalse(o.canClaim(cords[2],1));assertFalse(o.canClaim(cords[2],2))
        o.release(cords[0]);assertNull(o.freeSocket(),"A carried appliance keeps its reserved socket")
        o.release(cords[0],true);assertEquals(0,o.freeSocket());assertTrue(o.canClaim(cords[1],1))
    }
    @Test fun stripsHaveSixDistinctSocketsAndHorizontalAnchorsRoundTrip() {
        val o=outlet(true)
        repeat(6) { i -> assertEquals(i,o.freeSocket());o.claim(outlet().cord,i) }
        assertNull(o.freeSocket());assertEquals(6,(0..5).map { OutletGeometry.tipLocal(it,false,true) }.distinct().size)
        val a=CordAnchor(0,BlockPos(1,80,1),UUID.randomUUID(),Direction.ZP,CordRating.V240,1,true,false)
        assertEquals(a,CordAnchor.read(a.write()))
        assertNotEquals(OutletGeometry.tipLocal(0,true,false),OutletGeometry.tipLocal(1,true,false))
    }
    @Test fun outletsHaveNoInternalTripEvenAtFiftyAmps() {
        for(rating in CordRating.entries) {
            val o=outlet(rating=rating);val root=RootSystem(.01,1)
            o.electricalLoadList.forEach(root::addState);o.electricalComponentList.forEach(root::addComponent)
            root.addComponent(VoltageSource("feed",o.input,null).apply { voltage=rating.volts })
            root.addComponent(Resistor(o.output,null).apply { resistance=rating.volts/50 })
            repeat(10) { root.step() }
            assertFalse(o.tripped);assertTrue(o.output.voltage>rating.volts*.99);assertTrue(o.contact.current>49)
        }
    }
    @Test fun cartridgeRatingAndRemovalSurviveSavingAndDoNotProduceAReplacementFuse() {
        val fuse=CutoutFuse();assertFalse(fuse.removeCartridge().isEmpty);assertFalse(fuse.closed)
        assertTrue(fuse.insertCartridge(Eln.findItemStack("5 A Pole Fuse",1)));assertEquals(5.0,fuse.rating(100.0))
        assertTrue(fuse.step(.1,20.0,100.0));assertTrue(fuse.blown)
        assertEquals(PoleHardware.spentName,mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(fuse.removeCartridge())!!.name)
        val restored=CutoutFuse();restored.read(CompoundTag().also { fuse.write(it,"f") },"f")
        assertFalse(restored.hasCartridge);assertFalse(restored.closed);assertTrue(restored.removeCartridge().isEmpty)
    }
    @Test fun theRedHandleDisconnectOpensAllServiceOutputsAndKeepsItsPositionAfterReload() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.disconnect }
        val sw=HouseServiceElement(TransparentNode(),d)
        sw.readItemStackNBT(CompoundTag().apply { putInt("serviceCircuit",2) })
        val root=RootSystem(.01,1);sw.electricalLoadList.forEach(root::addState);sw.electricalComponentList.forEach(root::addComponent)
        sw.line.forEachIndexed { i,b -> root.addComponent(VoltageSource("line$i",b,null).apply { voltage=if(i==2)240.0 else 120.0 }) }
        sw.load.forEach { root.addComponent(Resistor(it,null).apply { resistance=120.0 }) }
        repeat(4) { root.step() };sw.load.forEachIndexed { i,b -> assertEquals(if(i==2)240.0 else 120.0,b.voltage,.1) }
        sw.readItemStackNBT(CompoundTag().apply { putBoolean("meterDisconnected",true);putInt("serviceCircuit",2) })
        repeat(4) { root.step() };sw.load.forEach { assertEquals(0.0,it.voltage,1e-7) }
        assertEquals(240.0,sw.line[2].voltage);assertFalse(sw.allowsNewGridLinks)
        val saved=sw.getItemStackNBT();val cold=HouseServiceElement(TransparentNode(),d);cold.readItemStackNBT(saved)
        assertTrue(cold.disconnected);assertTrue(cold.serviceDisconnect)
    }
    @Test fun installedTransformerItemsControlCapacityAndGuiPacketsCannotIncreaseIt() {
        val desc=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().first { it.kind.transformer }
        val p=DistributionPoleElement(TransparentNode(),desc)
        for((i,name) in PoleHardware.transformerNames.withIndex()) {
            p.inventory.setItem(0,Eln.findItemStack(name,1));p.inventoryChange(p.inventory)
            assertEquals(MainsStepDown.RATINGS[i],p.unit!!.ratedWatts)
        }
        p.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[0],1));p.inventoryChange(p.inventory)
        val bytes=ByteArrayOutputStream().also { DataOutputStream(it).writeInt(100_000) }
        assertTrue(p.unit!!.packet(86,DataInputStream(ByteArrayInputStream(bytes.toByteArray()))));assertEquals(10_000,p.unit.ratedWatts)
        assertEquals("120_V_Wall_Outlet.name",Eln.findItemStack("120 V Wall Outlet",1).item.descriptionId)
        assertEquals("eln:experimental_wall_outlet",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Eln.findItemStack("120 V Wall Outlet",1).item).toString())
    }
}
