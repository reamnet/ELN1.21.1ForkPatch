package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.*
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class SubstationPhaseTest {
    private fun station(kind: TransmissionKind,front: Direction)=TransmissionElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },
        Eln.transparentNodeItem.subItemList[kind.subId+(125 shl 6)] as TransmissionDescriptor).apply { this.front=front;updateIdealRenderAngle() }
    private fun pole(x: Int,z: Int,front: Direction)=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(x,64,z,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.THREE }).apply { this.front=front;updateIdealRenderAngle() }
    @Test fun rotatedStationsAimAtTheActualVoltageBankAndPhase() {
        for(kind in TransmissionKind.entries)for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
            val e=station(kind,front)
            for(base in if(kind.station)listOf(32,36) else listOf(32))repeat(3) { phase->
                val hit=e.renderedGridCablePoint(base+phase,0)
                assertEquals(base+phase,e.aimedSingleGridTerminal(Direction.YP,hit),"$kind $front $base $phase")
                assertEquals(if(base==36)53 else 52,e.aimedGridTerminal(Direction.YP,hit))
            }
        }
    }
    @Test fun stationaryVoltageBanksAnchorPolePhaseOrderWithoutMovingOrReassigningCircuits() {
        for(kind in listOf(TransmissionKind.STEP_69,TransmissionKind.STEP_230))for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
            val s=station(kind,front);val angle=s.highVoltageRenderAngle
            val endpoints=listOf(Vec3(-40.0,0.0,0.0),Vec3(40.0,0.0,0.0)).map { it.yRot(Math.toRadians(angle.toDouble()).toFloat()) }
            val poles=endpoints.mapIndexed { i,p->pole(kotlin.math.round(p.x).toInt(),kotlin.math.round(p.z).toInt(),if(i==0)front else front.inverse) }
            val all=listOf<GridElement>(s)+poles
            val before=(32..34).map { s.renderedGridCablePoint(it,0) }+(36..38).map { s.renderedGridCablePoint(it,0) }
            val links=poles.mapIndexed { i,p->GridLink(s.coordinate(),p.coordinate(),if(i==0)52 else 53,52,ItemStack.EMPTY,1.0).also { s.gridLinkList+=it;p.gridLinkList+=it } }
            val resolve=all.associateBy { it.coordinate() }
            all.forEach { it.updateIdealRenderAngle() };poles[0].alignPrimaryPhaseOrder { resolve[it] }
            assertEquals(angle,s.highVoltageRenderAngle)
            assertEquals(before,(32..34).map { s.renderedGridCablePoint(it,0) }+(36..38).map { s.renderedGridCablePoint(it,0) })
            for((i,p)in poles.withIndex()) {
                val d=p.coordinate().subtract(s.coordinate());val across=Vec3(-d.z.toDouble(),0.0,d.x.toDouble())
                val base=if(i==0)32 else 36
                val a=s.renderedGridCablePoint(base+2,0).subtract(s.renderedGridCablePoint(base,0)).dot(across)
                val b=p.renderedGridCablePoint(34,0).subtract(p.renderedGridCablePoint(32,0)).dot(across)
                assertTrue(a*b>0,"Crossed phases at $kind $front bank $base")
                repeat(3) { phase->assertSame(if(i==0)s.inputs[phase] else s.outputs[phase],s.gridLoads(if(i==0)52 else 53)[phase]) }
                assertEquals(if(i==0)52 else 53,links[i].getTerminal(s))
            }
        }
    }
}
