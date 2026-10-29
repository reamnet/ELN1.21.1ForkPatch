package mods.eln.utility

import mods.eln.Eln
import mods.eln.devtest.StandbyTestFluids
import mods.eln.misc.*
import mods.eln.node.NodeConnection
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sixnode.electricalcable.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.standby.*
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.test.*

class OutdoorExpansionTest {
    private fun node(x: Int=0)=TransparentNode().apply { coordinate=Coordinate(x,80,0,0) }
    private fun pole(kind: DistributionKind)=DistributionPoleElement(node(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    private fun tray(x: Int=0): ColorCableTrayElement {
        val n=node(x);return ColorCableTrayElement(n,Eln.transparentNodeItem.subItemList.values.filterIsInstance<ColorCableTrayDescriptor>().single()).also { n.element=it }
    }
    private val cable get()=UtilityCableDescriptor.allDescriptors().first { !it.melted && !it.insulated }
    @Test fun generatorTiersSupplyTheirLargerLoadsConsumeRealFuelAndKeepTheirFootprints() {
        for(tier in GeneratorTier.entries) {
            val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BackupDescriptor>().single { it.generator && it.tier==tier }
            val g=StandbyGeneratorElement(node(),d);g.unit.mode=StandbyMode.MANUAL
            g.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0),tier.tankMb),FluidAction.EXECUTE)
            assertEquals(tier.tankMb,g.unit.fuel.tank.capacity)
            val root=RootSystem(.05,1)
            g.electricalLoadList.forEach(root::addState);g.electricalComponentList.forEach(root::addComponent);g.unit.attach(root)
            val load=Resistor(g.unit.output,null).apply { resistance=240.0*240/(tier.watts*.8) };root.addComponent(load)
            val fuel=g.unit.fuel.availableJoules
            repeat(180) { root.step();g.unit.tick(.05) }
            assertTrue(g.unit.running,"${tier.name}: ${g.unit.status}")
            assertTrue(load.power>tier.watts*.79,"${tier.name}: ${load.power}")
            assertTrue(g.unit.fuel.availableJoules<fuel)
            val saved=CompoundTag().also(g.unit::write);val copy=StandbyGeneratorElement(node(),d).unit.also { it.read(saved) }
            assertEquals(g.unit.fuel.availableJoules,copy.fuel.availableJoules,1e-6)
            if(tier!=GeneratorTier.SMALL) {
                val mesh=Obj3D().apply { assertTrue(loadFile("${tier.model}/${tier.model}.obj")) }
                assertEquals(tier.width.toDouble(),mesh.xDim.toDouble(),.12)
                assertEquals(tier.height.toDouble(),mesh.yDim.toDouble(),.12)
                assertEquals(tier.depth.toDouble(),mesh.zDim.toDouble(),.12)
                assertEquals(tier.width*tier.height*tier.depth-1,d.ghostGroup!!.size())
            }
            g.unit.detach()
        }
    }
    @Test fun undergroundFeedersFuseEachPhaseIndependentlyAndNeverPowerTheirLightFromHighVoltage() {
        for(kind in DistributionKind.entries.filter { it.underground && !it.pedestal }) {
            val p=pole(kind);val root=RootSystem(.01,1)
            p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
            p.lines.forEachIndexed { i,load->root.addComponent(VoltageSource("phase$i",load,null).apply { voltage=12_500.0+i*100 }) }
            p.outs.forEach { root.addComponent(Resistor(it,null).apply { resistance=50_000.0 }) }
            p.electricalProcessList.forEach { it.process(.01) };repeat(4) { root.step() }
            assertFalse(p.allowsNewGridLinks);assertTrue(p.outs.all { it.voltage>12_400 })
            assertEquals(0.0,p.lamp.power,1e-8)
            p.operateFuse(0);root.step();assertEquals(0.0,p.outs[0].voltage,1e-7)
            assertTrue(p.outs.drop(1).all { it.voltage>12_400 })
            assertSame(p.services[2],p.getElectricalLoad(Direction.YN,LRDU.Down))
            if(kind.light) {
                root.addComponent(VoltageSource("upstream LV",p.services[0],null).apply { voltage=120.0 });root.step()
                assertEquals(70.0,p.lamp.power,1e-6)
            }
        }
    }
    @Test fun servicePedestalFollowsIncomingVoltageAndItsOneFuseOpensAllServiceOutputs() {
        val p=pole(DistributionKind.SERVICE_PEDESTAL);val root=RootSystem(.01,1)
        p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        val sourcePin=ElectricalLoad().apply { serialResistance=.00001 };root.addState(sourcePin)
        val source=VoltageSource("service",sourcePin,null).apply { voltage=240.0 };root.addComponent(source)
        root.addComponent(ElectricalConnection(sourcePin,p.undergroundInput[2]))
        p.services.forEachIndexed { i,load->root.addComponent(Resistor(load,null).apply { resistance=if(i==2)100.0 else 200.0 }) }
        repeat(4) { p.electricalProcessList.forEach { it.process(.01) };root.step() }
        assertEquals(120.0,p.services[0].voltage,.01);assertEquals(240.0,p.services[2].voltage,.01)
        source.voltage=228.0;repeat(4) { root.step() }
        assertEquals(114.0,p.services[0].voltage,.01)
        p.operateFuse(0);root.step();assertTrue(p.services.all { kotlin.math.abs(it.voltage)<1e-7 })
        assertEquals(228.0,p.undergroundInput[2].voltage,.01)
    }
    @Test fun trayJoinsOnlyMatchingInstalledColorsAndRetainsCableResistanceAndSettings() {
        val a=tray();val b=tray(1)
        for(t in listOf(a,b)) { for(color in listOf(0,5))t.inventory.setItem(color,cable.newItemStack().also { cable.setRemainingLengthMeters(it,1.0) });t.inventory.setChanged() }
        val c=NodeConnection(a.node!!,Direction.XP,LRDU.Up,b.node!!,Direction.XN,LRDU.Up)
        val aPort=a.getElectricalLoad(Direction.XP,LRDU.Up,c.otherEndpoint(true));val bPort=b.getElectricalLoad(Direction.XN,LRDU.Up,c.endpoint(true))
        assertSame(a.loads[0],aPort);assertSame(b.loads[0],bPort)
        a.newConnectionAt(c,true);assertEquals(1,c.EC.size)
        val root=RootSystem(.01,1)
        (a.electricalLoadList+b.electricalLoadList).forEach(root::addState)
        c.EC.forEach(root::addComponent);root.addComponent(ElectricalConnection(aPort,bPort))
        root.addComponent(VoltageSource("white",a.loads[0],null).apply { voltage=120.0 })
        root.addComponent(VoltageSource("lime",a.loads[5],null).apply { voltage=240.0 })
        for(i in listOf(0,5))root.addComponent(Resistor(b.loads[i],null).apply { resistance=100.0 })
        root.step();for(i in listOf(0,5)) { val input=if(i==0)120.0 else 240.0;val expected=input*100/(100+a.loads[i].serialResistance+b.loads[i].serialResistance);assertEquals(expected,b.loads[i].voltage,1e-8) }
        assertTrue(b.loads.filterIndexed { i,_->i!=0 && i!=5 }.all { kotlin.math.abs(it.voltage)<1e-8 })
        assertTrue(a.loads[5].serialResistance>0)
        assertTrue(a.setPort(Direction.YN.int,5));assertFalse(a.setPort(9,90))
        val copy=tray(3).also { it.readFromNBT(CompoundTag().also(a::writeToNBT)) }
        assertEquals(5,copy.ports[Direction.YN.int]);assertEquals(listOf(0,5),copy.installedColors)
        c.EC.forEach(Eln.simulator::removeElectricalComponent)
    }
    @Test fun allFifteenFixturesAndThreeMastsUseDistinctOriginalAssetsWithoutChangingOldIds() {
        val fixtures=DecorativeLampStyle.entries.drop(88).take(15)
        assertEquals(15,fixtures.size);assertEquals(15,fixtures.map { it.model }.toSet().size)
        for(style in fixtures) {
            assertTrue(style.heads.all { it.x>0 && it.x<=style.depth },style.name)
            val mesh=Obj3D().apply { assertTrue(loadFile("${style.model}/${style.model}.obj")) }
            assertNotNull(mesh.getPart("Base"));assertNotNull(mesh.getPart("Light0"))
            // Repeated OBJ object names replace earlier geometry in the runtime loader.
            // Keep the stem in the same Metal object as its head.
            val text=javaClass.getResource("/assets/eln/model/${style.model}/${style.model}.obj")!!.readText()
            val objects=text.lineSequence().filter { it.startsWith("o ") }.toList()
            assertEquals(objects.distinct().size,objects.size,"Duplicate objects hide fixture geometry: ${style.name}")
        }
        ServiceMastStyle.entries.drop(1).forEach { mast->
            val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { !it.meter && !it.disconnect && it.mast==mast }
            val e=HouseServiceElement(node(),d)
            assertEquals(mast.headY,e.gridCablePoint(50,1).y,1e-8)
            if(mast==ServiceMastStyle.THROUGH_ROOF)assertNull(d.ghostGroup,"Existing roof blocks must remain intact")
        }
    }
}
