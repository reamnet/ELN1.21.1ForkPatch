package mods.eln.gridnode

import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.utility.ServiceTerminals
import net.minecraft.world.phys.Vec3

/** Packet geometry follows real installed cable stacks. Taps are visual only: no added circuit. */
internal object OverheadSpanPublication {
    private val mainGeometry=java.util.WeakHashMap<GridLink,Pair<OverheadSpan,List<OverheadWire>>>()
    private fun conductors(link: GridLink,span: OverheadSpan): List<OverheadWire> {
        mainGeometry[link]?.let { if(it.first==span)return it.second }
        return OverheadSpanMesh.conductors(span).also { mainGeometry[link]=span to it }
    }
    private fun style(link: GridLink)=OverheadCableAppearance.of(checkNotNull(ElectricalCableDescriptor.getDescriptor(link.cable) as? ElectricalCableDescriptor))
    private fun offset(from: GridElement,to: GridElement): Vec3 {
        val d=to.coordinate().subtract(from.coordinate())
        return Vec3(d.x.toDouble(),d.y.toDouble(),d.z.toDouble())
    }
    private fun anchor(owner: GridElement,terminal: Int,wire: Int,style: OverheadCableAppearance): Vec3 {
        if(owner.suspendsServiceCable && ServiceTerminals.isService(terminal)) {
            val center=(0..2).map { owner.renderedGridCablePoint(50,it) }.reduce(Vec3::add).scale(1.0/3)
            val i=if(terminal==50)wire else terminal-40
            return OverheadSpanMesh.terminal(Vec3.ZERO,i,style.outsideRadius)
                .yRot(Math.toRadians(owner.gridRenderAngle(terminal).toDouble()).toFloat()).add(center)
        }
        return owner.renderedGridCablePoint(terminal,wire)
    }
    private fun basic(link: GridLink): OverheadSpan {
        val a=link.elementA();val b=link.elementB();val from=link.getTerminal(a);val to=link.getTerminal(b)
        val appearance=style(link);val delta=offset(a,b)
        val n=minOf(a.renderWireCount(from),b.renderWireCount(to))
        return OverheadSpan(List(n) { i->anchor(a,from,i,appearance) to anchor(b,to,i,appearance).add(delta) },appearance,
            n==3 && ServiceTerminals.isService(from) && ServiceTerminals.isService(to) && appearance.insulated)
    }
    fun span(link: GridLink): OverheadSpan {
        var result=basic(link)
        for(start in listOf(true,false)) {
            val host=if(start)link.elementA() else link.elementB()
            val target=link.getOtherElement(host)
            val terminal=link.getTerminal(host)
            if(!host.suspendsServiceCable || !ServiceTerminals.isService(terminal))continue
            if(terminal==50 && target.suspendsServiceCable)continue
            if(!target.serviceDropEndpoint && terminal==50)continue
            val main=host.gridLinkList.filter { it!==link && it.getTerminal(host)==50 && it.getOtherElement(host).suspendsServiceCable }
                .maxByOrNull { offset(host,it.getOtherElement(host)).normalize().dot(offset(host,target).normalize()) } ?: continue
            val mainSpec=basic(main);val strands=conductors(main,mainSpec)
            val hostAtStart=main.a==host.coordinate()
            val first=if(hostAtStart)strands[0].points else strands[0].points.asReversed()
            val index=(1 until first.lastIndex).firstOrNull { first[it].distanceTo(first.first())>=.75 } ?: 1
            val path=if(hostAtStart)strands[2].points else strands[2].points.asReversed()
            val tangent=path[minOf(index+1,path.lastIndex)].subtract(path[index-1]).normalize()
            val translation=offset(link.elementA(),main.elementA())
            result=result.copy(ends=result.ends.mapIndexed { i,(a,b)->
                val circuit=if(terminal==50)i else terminal-40
                val points=if(hostAtStart)strands[circuit].points else strands[circuit].points.asReversed()
                val point=points[index].add(translation)
                if(start)point to b else a to point
            },startSplice=if(start)OverheadSplice(tangent,mainSpec.appearance.outsideRadius) else result.startSplice,
                endSplice=if(!start)OverheadSplice(tangent,mainSpec.appearance.outsideRadius) else result.endSplice)
        }
        return result
    }
}
