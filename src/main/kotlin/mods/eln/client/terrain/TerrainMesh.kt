package mods.eln.client.terrain

import it.unimi.dsi.fastutil.ints.IntArrayList
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.MeshNormals
import mods.eln.client.gl.VertexColors
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.*

/** Raw local coordinates/UVs/normals and ARGB color; atlas placement is resolved on each rebuild. */
internal data class TerrainMaterial(val texture: ResourceLocation, val unlit: Boolean, val fullBright: Boolean,
    val blend: Boolean, val cull: Boolean)
internal data class TerrainBatch(val material: TerrainMaterial, val vertices: IntArray)
internal data class TerrainMesh(val batches: List<TerrainBatch>)
internal data class TerrainSlice(val batch: TerrainBatch, val quads: IntArray?, val x: Int, val y: Int, val z: Int)

internal class TerrainCapture : FixedFunction.MeshSink {
    var enabled = false
    var supported = true
    var unsupportedReason:String?=null
    private val batches = linkedMapOf<TerrainMaterial, IntArrayList>()
    override fun accepts() = enabled
    override fun unsupported(reason: String) { supported=false;unsupportedReason=reason }
    override fun capture(texture: ResourceLocation, unlit: Boolean, fullBright: Boolean, blend: Boolean, cull: Boolean,
        lines: Boolean, vertices: MutableList<FloatArray>, model: Matrix4f, normals: Matrix3f, color: Int) {
        if (lines || vertices.size % 4 != 0 || !TerrainAtlas.supported(texture) ||
            (texture!=FixedFunction.WHITE && (vertices.any { !it[3].isFinite() || !it[4].isFinite() || it[3] !in -4096f..4096f || it[4] !in -4096f..4096f } ||
                vertices.chunked(4).any { face -> (ceil(face.maxOf { it[3] })-floor(face.minOf { it[3] })+1)*(ceil(face.maxOf { it[4] })-floor(face.minOf { it[4] })+1)>1024 }))) {
            if(java.lang.Boolean.getBoolean("eln.terrainChecks"))mods.eln.Eln.logger.warn("Terrain unsupported texture={} available={} lines={} vertices={} uv={}..{} / {}..{}",texture,TerrainAtlas.supported(texture),lines,vertices.size,vertices.minOf { it[3] },vertices.maxOf { it[3] },vertices.minOf { it[4] },vertices.maxOf { it[4] })
            supported = false; return
        }
        val data = batches.getOrPut(TerrainMaterial(texture,unlit,fullBright,blend,cull)) { IntArrayList() }
        val p = Vector4f(); val n = Vector3f()
        for (v in vertices) {
            p.set(v[0],v[1],v[2],1f).mul(model)
            MeshNormals.transform(normals,v[5],v[6],v[7],n)
            for (f in floatArrayOf(p.x,p.y,p.z,v[3],v[4],n.x,n.y,n.z)) data.add(f.toRawBits())
            data.add(if (v[8].isNaN()) color else VertexColors.pack(v))
        }
    }
    fun finish() = TerrainMesh(batches.map { TerrainPartition.wrapTexture(TerrainBatch(it.key,it.value.toIntArray())) })
}

/** Faces are clipped to section boundaries, so neither culling nor packed vertex coordinates
 * depend on the visibility of a distant base block. UVs, normals and tint interpolate at cuts. */
internal object TerrainPartition {
    private const val STRIDE = 9
    private fun vertex(data: IntArray, offset: Int) = DoubleArray(12) { i ->
        if (i < 8) Float.fromBits(data[offset+i]).toDouble()
        else ((data[offset+8] ushr ((i-8)*8)) and 255).toDouble()
    }
    private fun clip(poly: List<DoubleArray>, axis: Int, edge: Double, above: Boolean): List<DoubleArray> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<DoubleArray>(); var a = poly.last(); var insideA = if (above) a[axis] >= edge else a[axis] <= edge
        for (b in poly) {
            val insideB = if (above) b[axis] >= edge else b[axis] <= edge
            if (insideA != insideB) {
                val t = (edge-a[axis])/(b[axis]-a[axis])
                out += DoubleArray(12) { a[it]+(b[it]-a[it])*t }
            }
            if (insideB) out += b
            a=b; insideA=insideB
        }
        return out
    }
    private fun append(data: IntArrayList, v: DoubleArray) {
        repeat(8) { data.add(v[it].toFloat().toRawBits()) }
        var color=0;repeat(4) { color = color or (v[8+it].roundToInt().coerceIn(0,255) shl (it*8)) };data.add(color)
    }
    private fun polygon(data: IntArrayList, p: List<DoubleArray>) {
        if (p.size < 3) return
        // Remove zero-area seam polygons. Every retained triangle uses the original winding.
        for (i in 1 until p.size-1) {
            val a=p[0];val b=p[i];val c=p[i+1]
            val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2]
            val vx=c[0]-a[0];val vy=c[1]-a[1];val vz=c[2]-a[2]
            if ((uy*vz-uz*vy).pow(2)+(uz*vx-ux*vz).pow(2)+(ux*vy-uy*vx).pow(2)<1e-20) continue
            append(data,a);append(data,b);append(data,c);append(data,c)
        }
    }
    /** Standalone legacy textures repeat; an atlas sprite must split at each UV tile first. */
    fun wrapTexture(batch: TerrainBatch): TerrainBatch {
        if(batch.material.texture==FixedFunction.WHITE)return batch
        val raw=batch.vertices
        if(raw.indices.step(9).all { Float.fromBits(raw[it+3]) in 0f..1f && Float.fromBits(raw[it+4]) in 0f..1f })return batch
        val out=IntArrayList()
        for(offset in raw.indices step 36) {
            val face=List(4) { vertex(raw,offset+it*9) }
            if(face.all { it[3] in 0.0..1.0 && it[4] in 0.0..1.0 }) {
                for(i in offset until offset+36)out.add(raw[i]);continue
            }
            val low=IntArray(2) { floor(face.minOf { v->v[it+3] }).toInt() }
            val high=IntArray(2) { a ->
                val lo=face.minOf { it[a+3] };val hi=face.maxOf { it[a+3] }
                max(low[a],floor(hi-if(hi>lo)1e-7 else 0.0).toInt())
            }
            for(u in low[0]..high[0])for(v in low[1]..high[1]) {
                var p=clip(clip(face,3,u.toDouble(),true),3,u+1.0,false)
                p=clip(clip(p,4,v.toDouble(),true),4,v+1.0,false)
                polygon(out,p.map { it.copyOf().apply { this[3]-=u;this[4]-=v } })
            }
        }
        return TerrainBatch(batch.material,out.toIntArray())
    }
    fun renderable(slice: TerrainSlice,offset: Int): Boolean {
        val d=slice.batch.vertices
        fun at(i:Int,a:Int)=Float.fromBits(d[offset+i*9+a])+when(a){0->slice.x;1->slice.y;else->slice.z}
        var edges=0
        for(i in 0..3)if((0..2).any { a->abs(at(i,a)-at((i+3)%4,a))>=1e-5f })edges++
        if(edges<3)return false
        return (1..2).any { i ->
            val ux=at(i,0)-at(0,0);val uy=at(i,1)-at(0,1);val uz=at(i,2)-at(0,2)
            val vx=at(i+1,0)-at(0,0);val vy=at(i+1,1)-at(0,1);val vz=at(i+1,2)-at(0,2)
            (uy*vz-uz*vy).pow(2)+(uz*vx-ux*vz).pow(2)+(ux*vy-uy*vx).pow(2)>1e-20f
        }
    }
    fun split(mesh: TerrainMesh, anchor: BlockPos): Map<Long,List<TerrainSlice>> {
        val result=linkedMapOf<Long,MutableList<TerrainSlice>>()
        for (batch in mesh.batches) {
            val complete=linkedMapOf<Long,IntArrayList>()
            val clipped=linkedMapOf<Long,IntArrayList>()
            for (offset in batch.vertices.indices step STRIDE*4) {
                val face=List(4) { vertex(batch.vertices,offset+it*STRIDE) }
                val min=IntArray(3) { axis -> floor((face.minOf { it[axis] }+when(axis){0->anchor.x;1->anchor.y;else->anchor.z})/16).toInt() }
                val max=IntArray(3) { axis ->
                    val low=face.minOf { it[axis] };val high=face.maxOf { it[axis] }
                    val origin=when(axis){0->anchor.x;1->anchor.y;else->anchor.z}
                    max(min[axis],floor((high+origin-if(high>low)1e-7 else 0.0)/16).toInt())
                }
                require((max[0]-min[0]+1L)*(max[1]-min[1]+1L)*(max[2]-min[2]+1L)<=512) { "Unbounded terrain face" }
                if (min.contentEquals(max)) {
                    complete.getOrPut(SectionPos.asLong(min[0],min[1],min[2])) { IntArrayList() }.add(offset)
                } else for (x in min[0]..max[0]) for (y in min[1]..max[1]) for (z in min[2]..max[2]) {
                    var poly:List<DoubleArray> = face
                    for (axis in 0..2) {
                        val origin=when(axis){0->x*16-anchor.x;1->y*16-anchor.y;else->z*16-anchor.z}.toDouble()
                        poly=clip(clip(poly,axis,origin,true),axis,origin+16,false)
                    }
                    polygon(clipped.getOrPut(SectionPos.asLong(x,y,z)) { IntArrayList() },poly)
                }
            }
            fun add(key:Long, b:TerrainBatch, indices:IntArray?) {
                val section=SectionPos.of(key)
                result.getOrPut(key) { mutableListOf() } += TerrainSlice(b,indices,
                    anchor.x-section.minBlockX(),anchor.y-section.minBlockY(),anchor.z-section.minBlockZ())
            }
            for ((key,indices) in complete) add(key,batch,if(indices.size*STRIDE*4==batch.vertices.size)null else indices.toIntArray())
            for ((key,data) in clipped) if (!data.isEmpty) add(key,TerrainBatch(batch.material,data.toIntArray()),null)
        }
        return result
    }
}

/** VertexConsumer.setColor(int) and the existing model recorder both use ARGB. */
internal object TerrainColors {
    fun shade(color: Int,factor: Float): Int = (color and 0xff000000.toInt()) or
        ((((color ushr 16) and 255)*factor).toInt() shl 16) or
        ((((color ushr 8) and 255)*factor).toInt() shl 8) or (((color and 255)*factor).toInt())
}
