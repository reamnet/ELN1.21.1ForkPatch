package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.*
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class PoleAlignmentAndTapTest {
    private fun pole(kind: DistributionKind)=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    @Test fun straightRunsAndCornersBisectWithoutUsingVerticalOrSecondarySpans() {
        assertEquals(-45f,PoleSpanAlignment.angle(listOf(Vec3(20.0,0.0,0.0),Vec3(0.0,0.0,20.0)),180f))
        assertEquals(0f,PoleSpanAlignment.angle(listOf(Vec3(-20.0,2.0,0.0),Vec3(20.0,-2.0,0.0)),90f))
        assertEquals(90f,PoleSpanAlignment.angle(listOf(Vec3(0.0,20.0,0.0)),90f))
        val p=pole(DistributionKind.THREE)
        fun link(terminal: Int,x: Int,z: Int)=GridLink(p.coordinate(),Coordinate(x,64,z,0),terminal,terminal,net.minecraft.world.item.ItemStack.EMPTY,1.0)
        p.gridLinkList.addAll(listOf(link(52,20,0),link(52,0,20),link(50,-20,0),link(50,20,0)))
        p.updateIdealRenderAngle()
        assertEquals(-45f,p.highVoltageRenderAngle);assertEquals(0f,p.lowVoltageRenderAngle)
        assertEquals(p.gridCablePoint(32,0).yRot(Math.toRadians(-45.0).toFloat()),p.renderedGridCablePoint(32,0))
        assertEquals(p.gridCablePoint(50,1),p.renderedGridCablePoint(50,1))
        assertEquals(52,p.aimedGridTerminal(Direction.XP,p.renderedGridCablePoint(32,0)))
        assertEquals(50,p.aimedGridTerminal(Direction.XP,p.renderedGridCablePoint(50,1)))
        // A saved, unloaded endpoint must not require resolving a live tile entity to get its terminal.
        val saved=CompoundTag();p.gridLinkList.first().writeToNBT(saved,"")
        val restored=GridLink(saved,"");assertTrue(restored.getTerminal(p) in listOf(50,52))
    }
    @Test fun lowVoltageOnlyPolesAndFusedTapsKeepIndependentAlignment() {
        val p=pole(DistributionKind.SECONDARY)
        p.gridLinkList.add(GridLink(p.coordinate(),Coordinate(0,64,20,0),50,50,net.minecraft.world.item.ItemStack.EMPTY,1.0))
        p.updateIdealRenderAngle();assertEquals(-90f,p.lowVoltageRenderAngle)
        val tap=pole(DistributionKind.FUSED_TAP_THREE)
        tap.gridLinkList.add(GridLink(tap.coordinate(),Coordinate(20,64,0,0),52,52,net.minecraft.world.item.ItemStack.EMPTY,1.0))
        tap.gridLinkList.add(GridLink(tap.coordinate(),Coordinate(0,64,20,0),36,32,net.minecraft.world.item.ItemStack.EMPTY,1.0))
        tap.updateIdealRenderAngle();assertEquals(0f,tap.highVoltageRenderAngle)
        assertEquals(1,tap.gridLoads(36).size);assertTrue(tap.gridLoads(37).isEmpty());assertTrue(tap.gridLoads(53).isEmpty())
        assertEquals(3,tap.gridLoads(52).size);assertEquals(36,tap.aimedGridTerminal(Direction.ZP,tap.renderedGridCablePoint(36,0)))
    }
    @Test fun tapSelectsOnePhaseAndItsFuseNeverOpensTheThreePhaseMain() {
        for(phase in 0..2) {
            val p=pole(DistributionKind.FUSED_TAP_THREE)
            p.readItemStackNBT(CompoundTag().apply { putInt("utilityPhase",phase);putDouble("utilityFuseAmps",100.0) })
            val root=RootSystem(.01,1)
            p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
            p.lines.forEachIndexed { i,line -> root.addComponent(VoltageSource("phase$i",line,null).apply { voltage=1000.0+i*100 }) }
            val load=Resistor(p.outs.single(),null).apply { resistance=100.0 };root.addComponent(load)
            repeat(3) { root.step() };assertEquals(1000.0+phase*100,p.outs[0].voltage,1.0)
            p.lines.forEachIndexed { i,line -> assertEquals(if(i==phase)load.current else 0.0,line.current,1e-6) }
            load.resistance=.1;repeat(10) { root.step();p.electricalProcessList.forEach { it.process(.05) } }
            assertTrue(p.fuses.single().blown);assertEquals(0.0,p.outs[0].voltage,1e-5)
            p.lines.forEachIndexed { i,line -> assertEquals(1000.0+i*100,line.voltage,1e-6) }
            val copy=pole(DistributionKind.FUSED_TAP_THREE).also { it.readItemStackNBT(p.getItemStackNBT()) }
            assertEquals(phase,copy.transformerPhase);assertTrue(copy.fuses.single().blown)
        }
    }
    @Test fun eachAimedInsulatorHasASeparateSinglePhaseChoiceAndTheMeterIncludesEveryCircuit() {
        val p=pole(DistributionKind.THREE)
        repeat(3) { phase ->
            assertEquals(32+phase,p.aimedSingleGridTerminal(Direction.ZP,p.renderedGridCablePoint(32+phase,0)))
            assertEquals(52,p.aimedGridTerminal(Direction.ZP,p.renderedGridCablePoint(32+phase,0)))
            assertEquals(setOf(phase),GridPhaseRouting.phases(p,32+phase))
        }
        val other=pole(DistributionKind.THREE)
        assertTrue(GridPhaseRouting.compatible(p,33,other,33));assertFalse(GridPhaseRouting.compatible(p,32,other,34))
        val meter=p.multiMeterString(Direction.XP)
        assertTrue(listOf("HV A","HV B","HV C","LV L1","LV L2","240 V").all { it in meter })
    }

}
