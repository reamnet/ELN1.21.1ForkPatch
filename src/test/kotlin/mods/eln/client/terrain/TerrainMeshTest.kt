package mods.eln.client.terrain

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceLocation
import org.junit.Test
import kotlin.test.*
import kotlin.math.abs

class TerrainMeshTest {
    private val material=TerrainMaterial(ResourceLocation.fromNamespaceAndPath("eln","white"),false,false,false,true)
    private fun quad(x0:Float,y0:Float,x1:Float,y1:Float,z:Float=0f): TerrainBatch {
        val data=ArrayList<Int>()
        for((x,y) in listOf(x0 to y0,x1 to y0,x1 to y1,x0 to y1)) {
            listOf(x,y,z,x/64f,y/64f,0f,0f,1f).forEach { data+=it.toRawBits() };data+=0xff804020.toInt()
        }
        return TerrainBatch(material,data.toIntArray())
    }
    private fun faces(s:TerrainSlice): List<List<DoubleArray>> {
        val v=s.batch.vertices
        return (s.quads?.toList() ?: v.indices.step(36).toList()).map { offset ->
            (0..3).map { i -> DoubleArray(3) { a -> Float.fromBits(v[offset+i*9+a]).toDouble()+when(a){0->s.x;1->s.y;else->s.z} } }
        }
    }
    private fun area(p:List<DoubleArray>):Double = (1..2).sumOf { i ->
        val a=p[0];val b=p[i];val c=p[i+1]
        abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))*.5
    }
    @Test fun clippingPreservesAreaWindingAndAllVertexAttributesAcrossManySections() {
        val batch=quad(-20f,-9f,29f,27f,2f)
        for(anchor in listOf(BlockPos.ZERO,BlockPos(-23,71,18),BlockPos(29_999_980,119,-29_999_980))) {
            val result=TerrainPartition.split(TerrainMesh(listOf(batch)),anchor)
            assertTrue(result.size>4)
            assertEquals(49.0*36,result.values.flatten().sumOf { s->faces(s).sumOf(::area) },1e-6)
            for(s in result.values.flatten()) {
                for(face in faces(s))for(p in face)for(v in p)assertTrue(v in -1e-6..16.000001)
                val data=s.batch.vertices
                for(offset in data.indices step 9) {
                    assertEquals(Float.fromBits(data[offset])/64f,Float.fromBits(data[offset+3]),1e-6f)
                    assertEquals(Float.fromBits(data[offset+1])/64f,Float.fromBits(data[offset+4]),1e-6f)
                    assertEquals(1f,Float.fromBits(data[offset+7]));assertEquals(0xff804020.toInt(),data[offset+8])
                }
            }
        }
    }
    @Test fun localBodiesShareTheirOriginalArraysAndUseExactSectionRelativeCoordinates() {
        val b=quad(0f,0f,1f,1f)
        val s=TerrainPartition.split(TerrainMesh(listOf(b)),BlockPos(-31,70,29)).values.single().single()
        assertSame(b,s.batch);assertNull(s.quads);assertEquals(listOf(1,6,13),listOf(s.x,s.y,s.z))
    }
    @Test fun flatFacesOnASectionBoundaryAreNotDuplicated() {
        val b=quad(0f,0f,16f,16f,16f)
        val r=TerrainPartition.split(TerrainMesh(listOf(b)),BlockPos.ZERO)
        assertEquals(setOf(SectionPos.asLong(0,0,1)),r.keys)
        assertSame(b,r.values.single().single().batch)
        assertEquals(256.0,r.values.flatten().sumOf { s->faces(s).sumOf(::area) })
    }
    @Test fun clippedTrianglesNeverReverseTheirWinding() {
        val r=TerrainPartition.split(TerrainMesh(listOf(quad(-1f,-1f,33f,33f))),BlockPos.ZERO)
        for(s in r.values.flatten())for(p in faces(s)) {
            val a=p[0];val b=p[1];val c=p[2]
            assertTrue((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])>0)
        }
    }
    @Test fun repeatedTextureUvsSplitIntoTilesWithoutChangingAreaOrInterpolatedColors() {
        val b=quad(0f,0f,64f,64f)
        for(p in b.vertices.indices step 9) {
            b.vertices[p+3]=(Float.fromBits(b.vertices[p+3])*3f-1.5f).toRawBits()
            b.vertices[p+4]=(Float.fromBits(b.vertices[p+4])*2f-1f).toRawBits()
        }
        val wrapped=TerrainPartition.wrapTexture(b)
        val s=TerrainSlice(wrapped,null,0,0,0)
        assertEquals(4096.0,faces(s).sumOf(::area),1e-3)
        for(p in wrapped.vertices.indices step 9) {
            assertTrue(Float.fromBits(wrapped.vertices[p+3]) in 0f..1f)
            assertTrue(Float.fromBits(wrapped.vertices[p+4]) in 0f..1f)
            assertEquals(0xff804020.toInt(),wrapped.vertices[p+8])
        }
    }
    @Test fun terrainTintKeepsOriginalArgbChannelsAndAlpha() {
        val original=mods.eln.client.gl.VertexColors.pack(.93f,.62f,.08f,.5f)
        assertEquals(0x7fed9e14,original)
        assertEquals(original,TerrainColors.shade(original,1f))
        assertEquals(0x7f764f0a,TerrainColors.shade(original,.5f))
    }
    @Test fun collapsedFacesNeverEnterTheTranslucentConsumerButRealTrianglesSurvive() {
        fun slice(b:TerrainBatch)=TerrainSlice(b,null,0,0,0)
        assertFalse(TerrainPartition.renderable(slice(quad(0f,0f,0f,1f)),0))
        assertFalse(TerrainPartition.renderable(slice(quad(0f,0f,0.000001f,0.000001f)),0))
        val b=quad(0f,0f,1f,1f)
        b.vertices.copyInto(b.vertices,27,18,27)
        assertTrue(TerrainPartition.renderable(slice(b),0))
        assertTrue(TerrainPartition.renderable(slice(quad(0f,0f,1f,1f)),0))
    }
    @Test fun partFiltersRestoreAfterNestedCallsExceptionsAndStayThreadLocal() {
        assertTrue(MeshPartSelection.allows("Rotor"))
        MeshPartSelection.with(setOf("Rotor"),false) {
            assertFalse(MeshPartSelection.allows("Rotor"));assertTrue(MeshPartSelection.allows("Case"))
            MeshPartSelection.with(setOf("Rotor"),true) {
                assertTrue(MeshPartSelection.allows("Rotor"));assertFalse(MeshPartSelection.allows("Case"))
            }
            assertFalse(MeshPartSelection.allows("Rotor"))
            var other=false
            Thread { other=MeshPartSelection.allows("Rotor") }.apply { start();join() }
            assertTrue(other)
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                MeshPartSelection.with(emptySet(),true) { error("test") }
            }
            assertFalse(MeshPartSelection.allows("Rotor"))
        }
        assertTrue(MeshPartSelection.allows("Rotor"));assertFalse(MeshPartSelection.staticPass())
    }
}
