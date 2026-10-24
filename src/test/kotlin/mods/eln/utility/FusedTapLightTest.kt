package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class FusedTapLightTest {
    private fun pole(kind: DistributionKind)=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    @Test fun secondaryFedLightDoesNotBorrowHighVoltageAndItsTapFuseLeavesTheMainIntact() {
        val p=pole(DistributionKind.FUSED_TAP_LIGHT_THREE)
        p.electricalProcessList.forEach { it.process(.01) }
        val root=RootSystem(.01,1)
        p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        p.lines.forEachIndexed { i,line->root.addComponent(VoltageSource("phase$i",line,null).apply { voltage=12_470.0 }) }
        root.addComponent(Resistor(p.outs.single(),null).apply { resistance=20_000.0 })
        repeat(3) { root.step() }
        assertEquals(0.0,p.lamp.power,1e-9)
        val lv=VoltageSource("upstream secondary",p.services[0],null).apply { voltage=120.0 };root.addComponent(lv)
        repeat(3) { root.step() };assertEquals(70.0,p.lamp.power,1e-6)
        p.operateFuse(0);root.step()
        assertEquals(0.0,p.outs.single().voltage,1e-6)
        assertTrue(p.lines.all { it.voltage==12_470.0 });assertEquals(70.0,p.lamp.power,1e-6)
    }
    @Test fun transformerAndTapHaveIndependentFusesAndPhaseSettingsSurviveReload() {
        val p=pole(DistributionKind.FUSED_TAP_TRANSFORMER_LIGHT_THREE)
        p.readItemStackNBT(CompoundTag().apply { putInt("utilityPhase",2) })
        assertEquals(2,p.fuses.size)
        assertEquals(1,p.kind.tapFuseIndex)
        p.inventory.setItem(0,Eln.findItemStack("10 kW Pole Transformer",1));p.inventoryChange(p.inventory)
        val root=RootSystem(.01,20)
        p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        p.lines.forEachIndexed { i,line->root.addComponent(VoltageSource("phase$i",line,null).apply { voltage=12_470.0+i*100 }) }
        root.addComponent(Resistor(p.outs.single(),null).apply { resistance=20_000.0 })
        root.addComponent(Resistor(p.services[2],null).apply { resistance=100.0 })
        root.addProcess(p.unit!!.controller)
        repeat(20) { root.step();p.secondary!!.update() }
        assertEquals(12_670.0,p.outs.single().voltage,.1)
        assertTrue(p.services[2].voltage>100)
        p.operateFuse(1);repeat(10) { root.step();p.secondary!!.update() }
        assertEquals(0.0,p.outs.single().voltage,1e-6)
        assertTrue(p.services[2].voltage>100,"Tap fuse must not disconnect the transformer")
        p.operateFuse(1);p.operateFuse(0);repeat(10) { root.step();p.secondary!!.update() }
        assertTrue(p.outs.single().voltage>12_000,"Transformer fuse must not disconnect the tap")
        assertEquals(0.0,p.services[2].voltage,1e-5)
        val copy=pole(p.kind).also { it.readItemStackNBT(p.getItemStackNBT()) }
        assertEquals(2,copy.transformerPhase);assertFalse(copy.fuses[0].closed);assertTrue(copy.fuses[1].closed)
    }
}
