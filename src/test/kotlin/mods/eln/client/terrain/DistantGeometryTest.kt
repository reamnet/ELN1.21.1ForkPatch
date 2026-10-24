package mods.eln.client.terrain

import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.VertexColors
import mods.eln.compat.DistantNodePolicy
import mods.eln.Eln
import mods.eln.node.six.SixNodeBlock
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class DistantGeometryTest {
    @Test fun distantSurfacesKeepTheirTintAndNeverFillTheWholeObjectsBounds() {
        val color=VertexColors.pack(.4f,.25f,.1f,1f)
        val data=listOf(Vec3(0.0,0.0,0.0),Vec3(.04,0.0,0.0),Vec3(.04,8.0,0.0),Vec3(0.0,8.0,0.0)).flatMap { p->
            listOf(p.x.toFloat().toRawBits(),p.y.toFloat().toRawBits(),p.z.toFloat().toRawBits(),0,0,0,0,0,color)
        }.toIntArray()
        val mesh=TerrainMesh(listOf(TerrainBatch(TerrainMaterial(FixedFunction.WHITE,false,false,false,true),data)))
        val boxes=DistantGeometry.mesh(mesh) { -1 }
        assertTrue(boxes.isNotEmpty());assertTrue(boxes.size<512)
        assertTrue(boxes.all { it.color==color })
        assertTrue(boxes.all { it.bounds.xsize<=.126 && it.bounds.zsize<=.126 })
        assertTrue(boxes.maxOf { it.bounds.maxY }>=8.0)
    }
    @Test fun longOverheadSpanUsesThinSegmentsWithNoSolidRoomSizedBox() {
        val points=(0..32).map { Vec3(it*2.0,8.0-2.0*kotlin.math.sin(it*Math.PI/32),it*.5) }
        val boxes=DistantGeometry.wire(points)
        assertEquals(32,boxes.size)
        assertTrue(boxes.all { it.bounds.xsize<2.1 && it.bounds.zsize<.6 })
        assertTrue(boxes.all { it.color!=0xffffffff.toInt() })
        assertTrue(boxes.first().bounds.contains(points.first()))
        assertTrue(boxes.last().bounds.contains(points.last()))
    }
    @Test fun onlyCoveredCablesContributeToRainHeightmapsAndNodeVolumesUseCustomDistantSnapshots() {
        val open=Eln.sixNodeBlock.defaultBlockState()
        val covered=open.setValue(SixNodeBlock.COVERED,true)
        assertFalse(Heightmap.Types.MOTION_BLOCKING.isOpaque().test(open))
        assertTrue(Heightmap.Types.MOTION_BLOCKING.isOpaque().test(covered))
        assertTrue(DistantNodePolicy.ignore(open));assertTrue(DistantNodePolicy.ignore(covered))
        assertTrue(DistantNodePolicy.ignore(Eln.ghostBlock.defaultBlockState()))
        assertTrue(DistantNodePolicy.ignore(Eln.transparentNodeBlock.defaultBlockState()))
    }
}
