package mods.eln.gridnode

import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Visual exaggeration keeps small conductors readable, while preserving diameter ratios. */
internal data class OverheadCableAppearance(val radius: Double, val metal: Int, val insulated: Boolean) {
    val outsideRadius get()=if(insulated)radius*1.18+.002 else radius
    companion object {
        const val COPPER=0xffd96b2e.toInt()
        const val ALUMINUM=0xffc7ccd6.toInt()
        const val JACKET=0xff222629.toInt()
        fun of(cable: ElectricalCableDescriptor): OverheadCableAppearance = if(cable is UtilityCableDescriptor) {
            OverheadCableAppearance(sqrt(cable.conductorAreaMm2/PI)*.006,
                if(cable.material==UtilityCableMaterial.COPPER)COPPER else ALUMINUM,cable.insulated)
        } else {
            // Original power cables have a jacket but no gauge/material metadata.
            OverheadCableAppearance(maxOf(cable.render.width,cable.render.height).toDouble()*.35,COPPER,true)
        }
    }
}

internal data class OverheadWire(val points: List<Vec3>,val radius: Double,val color: Int)
internal data class OverheadSplice(val tangent: Vec3,val radius: Double)
internal data class OverheadSpan(val ends: List<Pair<Vec3,Vec3>>,val appearance: OverheadCableAppearance,
                                 val triplex: Boolean=false,val startSplice: OverheadSplice?=null,val endSplice: OverheadSplice?=null,
                                 val startPole: ServiceCableClearance?=null,val endPole: ServiceCableClearance?=null) {
    fun write(out: DataOutputStream) {
        out.writeByte(ends.size);out.writeFloat(appearance.radius.toFloat());out.writeInt(appearance.metal)
        out.writeBoolean(appearance.insulated);out.writeBoolean(triplex)
        for((a,b)in ends) { writeVec(out,a);writeVec(out,b) }
        for(splice in listOf(startSplice,endSplice)) {
            out.writeBoolean(splice!=null)
            if(splice!=null) { writeVec(out,splice.tangent);out.writeFloat(splice.radius.toFloat()) }
        }
        for(pole in listOf(startPole,endPole)) { out.writeBoolean(pole!=null);if(pole!=null) { writeVec(out,pole.center);out.writeFloat(pole.radius.toFloat()) } }
    }
    companion object {
        private fun writeVec(out: DataOutputStream,v: Vec3) { out.writeFloat(v.x.toFloat());out.writeFloat(v.y.toFloat());out.writeFloat(v.z.toFloat()) }
        private fun readVec(input: DataInputStream): Vec3=Vec3(input.readFloat().toDouble(),input.readFloat().toDouble(),input.readFloat().toDouble()).also {
            require(it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && maxOf(abs(it.x),abs(it.y),abs(it.z))<4096)
        }
        fun read(input: DataInputStream): OverheadSpan {
            val n=input.readUnsignedByte();require(n in 1..3)
            val r=input.readFloat().toDouble();require(r.isFinite() && r in .00001..2.0)
            val appearance=OverheadCableAppearance(r,input.readInt(),input.readBoolean())
            val triplex=input.readBoolean();require(!triplex || n==3 && appearance.insulated)
            val ends=List(n) { readVec(input) to readVec(input) }
            fun splice()=if(input.readBoolean()) {
                val tangent=readVec(input);val radius=input.readFloat().toDouble()
                require(tangent.lengthSqr()>1e-12 && radius.isFinite() && radius in .00001..2.0)
                OverheadSplice(tangent,radius)
            } else null
            val start=splice();val end=splice()
            fun pole()=if(input.readBoolean()) {
                val center=readVec(input);val radius=input.readFloat().toDouble();require(radius.isFinite() && radius in .01..4.0)
                ServiceCableClearance(center,radius)
            } else null
            return OverheadSpan(ends,appearance,triplex,start,end,pole(),pole())
        }
    }
}

/** Built only when a span changes, never during frame rendering. Electrical buses are unchanged. */
internal object OverheadSpanMesh {
    fun terminal(center: Vec3,wire: Int,radius: Double)=center.add(when(wire) {
        0->Vec3(0.0,-radius*1.7,-radius*2.5)
        1->Vec3(0.0,-radius*1.7,radius*2.5)
        else->Vec3(0.0,radius*1.3,0.0)
    })
    fun conductors(span: OverheadSpan): List<OverheadWire> {
        val style=span.appearance;val r=style.outsideRadius
        fun route(points: List<Vec3>,envelope: Double): List<Vec3> {
            return ServiceCablePath.route(points,listOfNotNull(span.startPole,span.endPole),envelope+.04)
        }
        if(!span.triplex)return span.ends.map { (a,b)->OverheadWire(route(OverheadCableMesh.points(a,b),sqrt(2.0)*r),r,
            if(style.insulated)OverheadCableAppearance.JACKET else style.metal) }
        val start=span.ends.map { it.first }.reduce(Vec3::add).scale(1.0/3)
        val end=span.ends.map { it.second }.reduce(Vec3::add).scale(1.0/3)
        val centers=route(OverheadCableMesh.points(start,end,ceil(start.distanceTo(end)*3).toInt().coerceIn(16,512)),max(3*r,maxOf(span.ends.maxOf { hypot(it.first.x-start.x,it.first.z-start.z) },span.ends.maxOf { hypot(it.second.x-end.x,it.second.z-end.z) }))+sqrt(2.0)*r)
        val distance=DoubleArray(centers.size)
        for(i in 1..centers.lastIndex)distance[i]=distance[i-1]+centers[i].distanceTo(centers[i-1])
        val length=distance.last()
        fun frame(i: Int): Pair<Vec3,Vec3> {
            val tangent=centers[min(i+1,centers.lastIndex)].subtract(centers[max(0,i-1)]).normalize()
            val right=Vec3(-tangent.z,0.0,tangent.x).let { if(it.lengthSqr()<1e-12)Vec3(1.0,0.0,0.0) else it.normalize() }
            return right to tangent.cross(right).normalize()
        }
        fun phase(offset: Vec3,i: Int)=frame(i).let { (right,up)->atan2(offset.dot(up),offset.dot(right)) }
        val startPhase=phase(span.ends[0].first.subtract(start),0)
        val expectedEnd=startPhase+length*PI
        val correction=phase(span.ends[0].second.subtract(end),centers.lastIndex)-expectedEnd
        val endPhase=expectedEnd+atan2(sin(correction),cos(correction))
        return (0..2).map { wire->
            val points=centers.mapIndexed { i,c->
                if(i==0)return@mapIndexed span.ends[wire].first
                if(i==centers.lastIndex)return@mapIndexed span.ends[wire].second
                val t=distance[i]/length
                // Match the terminal fan-out at both ends, so twisting cannot fold a hot
                // conductor back through the messenger as it enters a splice or weatherhead.
                val angle=startPhase+(endPhase-startPhase)*t
                val (right,up)=frame(i)
                val offset=if(wire==2)Vec3.ZERO else right.scale(cos(angle+wire*PI)*r*3).add(up.scale(sin(angle+wire*PI)*r*3))
                fun smooth(x: Double)=x.coerceIn(0.0,1.0).let { it*it*(3-2*it) }
                val fade=smooth(min(t,1-t)*length/.8)
                val terminalOffset=span.ends[wire].first.subtract(start).scale(1-t).add(span.ends[wire].second.subtract(end).scale(t))
                c.add(terminalOffset.scale(1-fade)).add(offset.scale(fade))
            }
            OverheadWire(points,if(wire==2)style.radius else r,if(wire==2)style.metal else OverheadCableAppearance.JACKET)
        }
    }
    fun geometry(span: OverheadSpan): List<OverheadWire> = buildList {
        val wires=conductors(span);addAll(wires)
        // Exposed conductor at jacketed single-wire terminations; triplex has a metallic messenger.
        if(span.appearance.insulated && !span.triplex)for(wire in wires)for(reverse in listOf(false,true)) {
            val p=if(reverse)wire.points.asReversed() else wire.points
            val tip=p[0].add(p[1].subtract(p[0]).normalize().scale(min(.075,p[0].distanceTo(p[1])*.4)))
            add(OverheadWire(listOf(p[0],tip),wire.radius*1.02,span.appearance.metal))
        }
        for((splice,start)in listOf(span.startSplice to true,span.endSplice to false))if(splice!=null)for(wire in wires) {
            val center=if(start)wire.points.first() else wire.points.last()
            val axis=splice.tangent.normalize()
            val r=max(wire.radius,splice.radius)*1.65
            add(OverheadWire(listOf(center.subtract(axis.scale(.09)),center.add(axis.scale(.09))),r,OverheadCableAppearance.JACKET))
            for(sign in listOf(-1,1)) {
                val band=center.add(axis.scale(sign*.065))
                add(OverheadWire(listOf(band.subtract(axis.scale(.014)),band.add(axis.scale(.014))),r*1.12,OverheadCableAppearance.ALUMINUM))
            }
        }
    }
}
