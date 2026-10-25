package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class PoleRefreshTest {
    private fun pole()=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind.twinLight })
    @Test fun topInsulatorUsesTheSameEnvelopeAsBothOuterPhaseInsulators() {
        val model=Obj3D().apply { assertTrue(loadFile("distributionpole/distributionpole.obj")) }
        val top=model.getPart("TopPin").boundingBox()
        for(name in listOf("Pin0","Pin2")) {
            val outer=model.getPart(name).boundingBox()
            assertEquals(outer.max.x-outer.min.x,top.max.x-top.min.x,1e-6)
            assertEquals(outer.max.y-outer.min.y,top.max.y-top.min.y,1e-6)
            assertEquals(outer.max.z-outer.min.z,top.max.z-top.min.z,1e-6)
        }
        assertTrue(top.min.y>=model.getPart("main").boundingBox().max.y-1e-6)
        for(kind in listOf(DistributionKind.SINGLE,DistributionKind.THREE)) {
            val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind }
            val p=DistributionPoleElement(TransparentNode(),descriptor)
            assertEquals(top.max.y,p.gridCablePoint(if(kind.phases==1)32 else 33,0).y,1e-6)
        }
    }
    @Test fun obsoletePolesStayRegisteredAndHiddenWhileTwinRetainsItsSaveIdentityInPowerTab() {
        for(name in listOf("Utility Pole Street Light","Utility Pole Cobrahead Street Light")) {
            val d=Eln.transparentNodeItem.getDescriptor(Eln.findItemStack(name,1))
            assertTrue(d is UtilityStreetLightDescriptor);assertTrue(d.isHidden)
        }
        val d=Eln.transparentNodeItem.getDescriptor(Eln.findItemStack("Utility Pole Twin LED Street Light",1))
        assertTrue(d is DistributionPoleDescriptor);d as DistributionPoleDescriptor;assertTrue(d.kind.twinLight);assertFalse(d.isHidden)
        assertSame(d,Eln.transparentNodeItem.subItemList[13+(124 shl 6)])
        assertSame(Eln.creativeTabPowerElectronics,d.creativeTab)
        val lighting=ArrayList<net.minecraft.world.item.ItemStack>();val power=ArrayList<net.minecraft.world.item.ItemStack>()
        mods.eln.generic.CreativeTabPopulator.addEntries(Eln.creativeTabLighting,lighting::add)
        mods.eln.generic.CreativeTabPopulator.addEntries(Eln.creativeTabPowerElectronics,power::add)
        val obsolete=listOf("Utility Pole Street Light","Utility Pole Cobrahead Street Light").map { Eln.transparentNodeItem.getDescriptor(Eln.findItemStack(it,1)) }
        assertTrue(lighting.none { Eln.transparentNodeItem.getDescriptor(it) in obsolete || Eln.transparentNodeItem.getDescriptor(it)===d })
        assertTrue(power.any { Eln.transparentNodeItem.getDescriptor(it)===d })
    }
    @Test fun bothLampSupplySelectionsDrawReal120WattsAndFollowLineSagWithoutConversion() {
        for(volts in listOf(120,240)) {
            val p=pole();p.readItemStackNBT(CompoundTag().apply { putInt("utilityLampVoltage",volts) })
            assertNull(p.unit);assertTrue(p.fuses.isEmpty());assertEquals(3,p.electricalLoadList.size)
            for(terminal in 0..39) { assertFalse(p.acceptsSavedGridTerminal(terminal));assertTrue(p.gridLoads(terminal).isEmpty()) }
            assertEquals(listOf(50,40,41,42),p.terminals)
            assertEquals(3,p.gridLoads(50).size)
            val root=RootSystem(.01,1)
            p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
            val sources=p.services.mapIndexed { i,pin->VoltageSource("upstream $i",pin,null).apply { voltage=if(i==2)240.0 else 120.0 }.also(root::addComponent) }
            fun step() { p.electricalProcessList.forEach { it.process(.01) };root.step() }
            repeat(4) { step() };assertEquals(120.0,p.lampPower,1e-8)
            val selected=if(volts==240)2 else 0
            assertEquals(120.0,sources[selected].power,1e-8)
            sources.forEach { it.voltage*=.95 };repeat(4) { step() }
            assertEquals(120*.95*.95,p.lampPower,1e-8)
            assertEquals(volts*.95,p.services[selected].voltage,1e-8)
            assertEquals(2,p.kind.lampHeads.size)
        }
    }
    @Test fun migrationPreservesSettingsAndReturnsHardwareOnlyOnce() {
        val old=UtilityStreetLightElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
            UtilityStreetLightDescriptor("Utility Pole Twin LED Street Light",PoleLampStyle.TWIN_LED))
        old.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1))
        old.fuse.removeCartridge();assertTrue(old.fuse.insertCartridge(Eln.findItemStack("20 A Pole Fuse",1)))
        val tag=CompoundTag().also(old::writeToNBT).apply {
            putInt("mainsOutputVoltage",240);putInt("mainsRatedWatts",25_000)
            putInt("mainsLampYaw",135);putInt("mainsLampPitch",-20);putInt("mainsLampHeightStep",-4)
            putBoolean("mainsLampAutomatic",true);putBoolean("mainsLampEnabled",false)
        }
        val item=pole();item.readItemStackNBT(tag)
        assertEquals(20.0,PoleHardware.fuse(item.inventory.getItem(1))!!.amps,0.0)
        assertTrue(item.inventory.getItem(0).isEmpty,"An item carrying settings must not create another transformer")
        val p=pole();p.readFromNBT(tag)
        assertEquals(240,p.lampVoltage);assertEquals(135,p.yaw);assertEquals(-20,p.pitch);assertEquals(-4,p.heightStep)
        assertTrue(p.lampAutomatic);assertFalse(p.lampEnabled);assertEquals(50,p.selectedTerminal)
        assertEquals(25_000,PoleHardware.transformerWatts(p.inventory.getItem(0)))
        assertEquals(20.0,PoleHardware.fuse(p.inventory.getItem(1))!!.amps,0.0)
        p.inventory.removeItemNoUpdate(0);p.inventory.removeItemNoUpdate(1)
        val again=pole();again.readFromNBT(CompoundTag().also(p::writeToNBT))
        assertTrue(again.inventory.isEmpty);assertEquals(240,again.lampVoltage);assertEquals(135,again.yaw)
    }
}
