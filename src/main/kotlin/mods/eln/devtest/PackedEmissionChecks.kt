package mods.eln.devtest

import com.mojang.blaze3d.vertex.*
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture

/** Compare every populated vertex attribute, including Iris extensions, against the old public API. */
internal object PackedEmissionChecks {
    fun verify() {
        val colors=listOf(floatArrayOf(0f,.4f,.8f,.16f),floatArrayOf(1f,.9f,.65f,1f),floatArrayOf(.12f,1f,.2f,.5f),floatArrayOf(.15f,.35f,1f,0f))
        val points=listOf(floatArrayOf(0f,0f,0f),floatArrayOf(1f,0f,0f),floatArrayOf(1f,1f,0f),floatArrayOf(0f,1f,0f))
        for(format in listOf(DefaultVertexFormat.NEW_ENTITY,DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)) {
            fun snapshot(legacy: Boolean): List<Byte> {
                ByteBufferBuilder(8192).use { arena ->
                    val buffer=BufferBuilder(arena,VertexFormat.Mode.QUADS,format)
                    if(legacy) {
                        for(i in 0..3) { val p=points[i];val c=colors[i]
                            buffer.addVertex(p[0],p[1],p[2]).setColor(c[0],c[1],c[2],c[3])
                                .setUv(if(i==1 || i==2)1f else 0f,if(i>=2)1f else 0f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00e000c0).setNormal(0f,0f,1f)
                        }
                    } else {
                        FixedFunction.begin(PoseStack(),MultiBufferSource { buffer },0x00e000c0,OverlayTexture.NO_OVERLAY)
                        try {
                            if(format!=DefaultVertexFormat.NEW_ENTITY)GL11.glDisable(GL11.GL_LIGHTING)
                            GL11.glBegin(GL11.GL_QUADS)
                            for(i in 0..3) { val p=points[i];val c=colors[i]
                                GL11.glColor4f(c[0],c[1],c[2],c[3]);GL11.glTexCoord2f(if(i==1 || i==2)1f else 0f,if(i>=2)1f else 0f)
                                GL11.glNormal3f(0f,0f,1f);GL11.glVertex3f(p[0],p[1],p[2])
                            };GL11.glEnd()
                        } finally { FixedFunction.finish() }
                    }
                    buffer.buildOrThrow().use { mesh ->
                        val actual=mesh.drawState().format;val bytes=mesh.vertexBuffer()
                        check(mesh.drawState().vertexCount==4)
                        // Padding bytes are deliberately unspecified by Minecraft.
                        return (0..3).flatMap { vertex -> actual.elements.flatMap { element ->
                            val start=vertex*actual.vertexSize+actual.getOffset(element)
                            (0 until element.byteSize()).map { bytes.get(start+it) }
                        } }
                    }
                }
            }
            check(snapshot(true)==snapshot(false)) { "Packed emission differs from the original consumer API ($format)" }
        }
    }
}
