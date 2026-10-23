package mods.eln.client.terrain

import mods.eln.client.itemrender.ItemQuadPacking
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.junit.Test
import kotlin.test.*

class LegacyMeshTest {
    @Test fun automaticSelectionKeepsCachedPartsAndDynamicRegionsSeparate() {
        MeshPartSelection.automatic(setOf("Rotor"),false) {
            assertTrue(MeshPartSelection.beginPart("Case"));MeshPartSelection.endPart()
            assertFalse(MeshPartSelection.beginPart("Rotor"))
            assertFalse(MeshPartSelection.beginAnimated())
            assertFalse(MeshPartSelection.beginDynamic())
        }
        MeshPartSelection.automatic(setOf("Rotor"),true) {
            assertTrue(MeshPartSelection.suppressDirectGeometry())
            assertFalse(MeshPartSelection.beginPart("Case"))
            assertTrue(MeshPartSelection.beginPart("Rotor"))
            assertFalse(MeshPartSelection.suppressDirectGeometry());MeshPartSelection.endPart()
            assertTrue(MeshPartSelection.beginAnimated())
            assertTrue(MeshPartSelection.beginPart("Case"));MeshPartSelection.endPart()
            assertFalse(MeshPartSelection.suppressDirectGeometry());MeshPartSelection.endDynamic()
            MeshPartSelection.unfiltered {
                assertTrue(MeshPartSelection.allows("Case"));assertFalse(MeshPartSelection.suppressDirectGeometry())
            }
            assertFalse(MeshPartSelection.allows("Case"));assertTrue(MeshPartSelection.suppressDirectGeometry())
        }
        assertTrue(MeshPartSelection.allows("Case"));assertFalse(MeshPartSelection.suppressDirectGeometry())
    }
    @Test fun unchangedBatchSignaturesReuseTheWholeListButVisualChangesInvalidateIt() {
        val id=Any();val tex=ResourceLocation.fromNamespaceAndPath("eln","test")
        fun record(old:List<DrawSignature>,x:Float=0f,color:Int=-1,blend:Boolean=false):List<DrawSignature> {
            val sink=SignatureCapture(old)
            assertFalse(sink.batch(id,tex,false,false,blend,true,Matrix4f().translate(x,0f,0f),Matrix3f(),color))
            return sink.finish()
        }
        val original=record(emptyList())
        assertSame(original,record(original))
        assertNotEquals(original,record(original,x=1f))
        assertNotEquals(original,record(original,color=0xff008080.toInt()))
        assertNotEquals(original,record(original,blend=true))
        assertTrue(SignatureCapture(original).finish().isEmpty())
        val contents=listOf(floatArrayOf(0f,1f,2f))
        assertEquals(GeometrySignature(7,contents),GeometrySignature(7,contents.map { it.copyOf() }))
        assertNotEquals(GeometrySignature(7,contents),GeometrySignature(7,listOf(floatArrayOf(0f,1f,3f))))
    }
    @Test fun paddedSpritesRepeatOriginalPixelsAtEveryBorderWithoutRescaling() {
        for(size in listOf(1,3,8,15,17,33)) {
            val padded=AtlasPadding.size(size)
            assertEquals(0,padded%16);assertTrue(padded>=size+2)
            assertEquals(size-1,AtlasPadding.source(0,size))
            repeat(size) { assertEquals(it,AtlasPadding.source(it+1,size)) }
            assertEquals(0,AtlasPadding.source(size+1,size))
        }
    }
    @Test fun itemQuadsKeepAlphaAndColorChannelsAndSignedNormals() {
        assertEquals(0x7f204080,ItemQuadPacking.abgr(0x7f804020))
        assertEquals(0xffeeddcc.toInt(),ItemQuadPacking.abgr(0xffccddee.toInt()))
        assertEquals(0x7f0081,ItemQuadPacking.normal(-1f,0f,1f))
        assertEquals(0x008100,ItemQuadPacking.normal(0f,-1f,0f))
    }
}
