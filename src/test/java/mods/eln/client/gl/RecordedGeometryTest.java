package mods.eln.client.gl;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RecordedGeometryTest {
    private float[] vertex(float x) { return new float[]{x,0,0,0,0,0,1,0,Float.NaN,1,1,1}; }
    @Test public void trianglesAndStripsKeepTheirWindingAndReuseTheRecordedTopology() {
        FixedFunction.Batch triangles = new FixedFunction.Batch(GL11.GL_TRIANGLES);
        for (int i=0;i<6;i++)triangles.vertices.add(vertex(i));
        List<float[]> quads=triangles.primitives();
        assertEquals(8,quads.size());assertSame(quads.get(2),quads.get(3));assertSame(quads.get(6),quads.get(7));
        for(int i=0;i<1000;i++)assertSame(quads,triangles.primitives());
        assertTrue(Float.isNaN(quads.getFirst()[8])); // Caller tint is still inherited at draw time.
        FixedFunction.Batch strip=new FixedFunction.Batch(GL11.GL_TRIANGLE_STRIP);
        for(int i=0;i<4;i++)strip.vertices.add(vertex(i));
        assertArrayEquals(new float[]{0,1,2,2,1,3,2,2},coordinates(strip.primitives()),0);
    }
    @Test public void lineLoopsCloseOnceAndIgnoreIncompleteLinePairs() {
        FixedFunction.Batch loop=new FixedFunction.Batch(GL11.GL_LINE_LOOP);
        for(int i=0;i<3;i++)loop.vertices.add(vertex(i));
        assertArrayEquals(new float[]{0,1,1,2,2,0},coordinates(loop.primitives()),0);
        assertSame(loop.primitives(),loop.primitives());
        FixedFunction.Batch lines=new FixedFunction.Batch(GL11.GL_LINES);
        lines.vertices.addAll(loop.vertices);
        assertEquals(2,lines.primitives().size());
    }
    private float[] coordinates(List<float[]> vertices) {
        float[] result=new float[vertices.size()];for(int i=0;i<result.length;i++)result[i]=vertices.get(i)[0];return result;
    }
}
