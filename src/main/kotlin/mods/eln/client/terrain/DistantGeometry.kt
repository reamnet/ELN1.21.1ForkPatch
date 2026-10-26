package mods.eln.client.terrain

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.*

internal data class DistantBox(val bounds: AABB, val color: Int)

/** DH's custom object API uses boxes. Sample surfaces rather than replacing a
 * whole turbine or cable span with its solid bounding box. No world access. */
internal object DistantGeometry {
    private data class Cell(val x: Int,val y: Int,val z: Int,val color: Int)
    fun mesh(mesh: TerrainMesh,textureColor: (TerrainMaterial)->Int): List<DistantBox> {
        var size=.125
        repeat(7) {
            val cells=linkedSetOf<Cell>()
            for(batch in mesh.batches) {
                if(batch.material.blend)continue
                val tex=textureColor(batch.material)
                val data=batch.vertices
                for(base in data.indices step 36) {
                    if(base+35>=data.size)break
                    fun point(i: Int)=Vec3(Float.fromBits(data[base+i*9]).toDouble(),
                        Float.fromBits(data[base+i*9+1]).toDouble(),Float.fromBits(data[base+i*9+2]).toDouble())
                    val v=(0..3).map(::point)
                    if(v.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() || maxOf(abs(it.x),abs(it.y),abs(it.z))>1024 })continue
                    val tint=data[base+8]
                    fun channel(shift: Int)=(((tint ushr shift) and 255)*((tex ushr shift) and 255)/255) shl shift
                    val color=0xff000000.toInt() or channel(16) or channel(8) or channel(0)
                    for((a,b,c) in listOf(Triple(v[0],v[1],v[2]),Triple(v[0],v[2],v[3]))) {
                        val n=ceil(maxOf(a.distanceTo(b),b.distanceTo(c),c.distanceTo(a))/size).toInt().coerceIn(1,64)
                        for(i in 0..n)for(j in 0..n-i) {
                            val p=a.add(b.subtract(a).scale(i.toDouble()/n)).add(c.subtract(a).scale(j.toDouble()/n))
                            cells+=Cell(floor(p.x/size).toInt(),floor(p.y/size).toInt(),floor(p.z/size).toInt(),color)
                        }
                    }
                }
            }
            // Vertical runs preserve tall poles without hundreds of individual cubes.
            val boxes=ArrayList<DistantBox>()
            for((column,values) in cells.groupBy { Triple(it.x,it.z,it.color) }) {
                val ys=values.map { it.y }.sorted();var i=0
                while(i<ys.size) {
                    val start=ys[i];var end=start;i++
                    while(i<ys.size && ys[i]==end+1) { end=ys[i];i++ }
                    boxes+=DistantBox(AABB(column.first*size,start*size,column.second*size,
                        (column.first+1)*size,(end+1)*size,(column.second+1)*size),column.third)
                }
            }
            if(boxes.size<=512)return boxes
            size*=2
        }
        return emptyList()
    }
    /** DH boxes cannot rotate. A whole diagonal catenary segment's bounding box
     * becomes a several-block-wide slab, so subdivide before taking bounds. */
    fun wire(points: List<Vec3>)=points.zipWithNext().flatMap { (a,b)->
        val delta=b.subtract(a)
        val count=ceil(maxOf(abs(delta.x),abs(delta.y),abs(delta.z))/.25).toInt().coerceAtLeast(1)
        (0 until count).map { i->
            DistantBox(AABB(a.add(delta.scale(i.toDouble()/count)),a.add(delta.scale((i+1.0)/count))).inflate(.035),0xff393735.toInt())
        }
    }
}
