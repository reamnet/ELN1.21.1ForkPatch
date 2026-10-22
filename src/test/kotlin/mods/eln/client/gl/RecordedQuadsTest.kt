package mods.eln.client.gl

import org.junit.Test
import kotlin.test.*

class RecordedQuadsTest {
    private fun vertex(x: Float,y: Float)=floatArrayOf(x,y,0f,x,y,0f,0f,1f,Float.NaN,1f,1f,1f)
    private fun quad()=listOf(vertex(0f,0f),vertex(1f,0f),vertex(1f,1f),vertex(0f,0f),vertex(1f,1f),vertex(0f,1f))
    private fun triangles(quads: List<FloatArray>)=quads.chunked(4).flatMap { listOf(it[0],it[1],it[2],it[2],it[3],it[0]) }
        .chunked(3).filter { tri -> !tri[0].contentEquals(tri[1]) && !tri[1].contentEquals(tri[2]) && !tri[0].contentEquals(tri[2]) }.flatten()
    private fun assertSameTriangles(original: List<FloatArray>,output: List<FloatArray>) {
        val expanded=triangles(output);assertEquals(original.size,expanded.size)
        original.chunked(3).zip(expanded.chunked(3)).forEach { (a,b) ->
            assertTrue((0..2).any { shift -> (0..2).all { a[it].contentEquals(b[(it+shift)%3]) } })
        }
    }
    @Test fun joiningPairsRetainsEveryTriangleAndAttributeInTheSameWinding() {
        val input=quad();val result=RecordedQuads.triangles(input)
        assertEquals(4,result.size);assertSameTriangles(input,result)
        val reversed=input.chunked(3).flatMap { listOf(it[0],it[2],it[1]) }
        val reversedOutput=RecordedQuads.triangles(reversed)
        assertSameTriangles(reversed,reversedOutput)
    }
    @Test fun signedZeroOnSharedNormalsAndUvsDoesNotSplitAnOtherwiseIdenticalFace() {
        val input=quad().map { it.copyOf() }
        input[3][3]=-0f;input[3][5]=-0f;input[4][6]=-0f
        val output=RecordedQuads.triangles(input)
        assertEquals(4,output.size)
        assertTrue(output.all { it[7]==1f && it[8].isNaN() })
        assertEquals(listOf(0f to 0f,1f to 0f,1f to 1f,0f to 1f),output.map { it[0] to it[1] })
    }
    @Test fun uvNormalAndColorSeamsNeverJoin() {
        for((index,attribute) in listOf(3 to 3,3 to 5,3 to 8,4 to 4,4 to 9)) {
            val input=quad().map { it.copyOf() };input[index][attribute]=.35f
            val result=RecordedQuads.triangles(input)
            assertEquals(8,result.size);assertSameTriangles(input,result)
        }
        val mixed=quad().map { it.copyOf() };mixed[5][5]=1f
        assertEquals(8,RecordedQuads.triangles(mixed).size)
    }
    @Test fun nonPlanarOverlappingAndDegenerateTrianglesAreKeptSeparate() {
        val warped=quad().map { it.copyOf() };warped[5][2]=.2f
        assertEquals(8,RecordedQuads.triangles(warped).size)
        val overlap=quad().map { it.copyOf() };overlap[5][0]=.75f;overlap[5][1]=.25f
        assertEquals(8,RecordedQuads.triangles(overlap).size)
        val degenerate=quad().map { it.copyOf() };degenerate[1][0]=0f;degenerate[1][1]=0f
        assertEquals(8,RecordedQuads.triangles(degenerate).size)
    }
    @Test fun mixedPairedAndUnpairedFacesRetainOrderAndIgnoreIncompleteTrailingVertices() {
        val input=quad()+listOf(vertex(3f,0f),vertex(4f,0f),vertex(4f,1f))+quad()
        val result=RecordedQuads.triangles(input+vertex(9f,9f))
        assertEquals(12,result.size);assertSameTriangles(input,result)
    }
    @Test fun explicitAndInheritedColorsRemainDifferentAndAlphaIsNotLost() {
        val explicit=quad().map { it.copyOf().apply { this[8]=.2f;this[9]=.4f;this[10]=.8f;this[11]=.16f } }
        val output=RecordedQuads.triangles(explicit);assertEquals(4,output.size)
        assertEquals(0x283366cc,VertexColors.pack(output[0]));assertSameTriangles(explicit,output)
        val mixed=quad().map { it.copyOf() };mixed[3][8]=1f
        assertEquals(8,RecordedQuads.triangles(mixed).size)
    }
    @Test fun texturedAndSolidReplayCachesStayIndependentAndRetainInheritedTint() {
        val mesh=FixedFunction.Batch(GL11.GL_TRIANGLES)
        mesh.vertices.addAll(quad())
        val compact=mesh.primitives(true);val textured=mesh.primitives(false)
        assertEquals(4,compact.size);assertEquals(8,textured.size)
        repeat(10) { assertSame(compact,mesh.primitives(true));assertSame(textured,mesh.primitives(false)) }
        assertTrue(compact.all { it[8].isNaN() } && textured.all { it[8].isNaN() })
        assertSame(mesh.colors(true),mesh.colors(true));assertSame(mesh.colors(false),mesh.colors(false))
        assertEquals(4,mesh.colors(true).size);assertEquals(8,mesh.colors(false).size)
    }

}
