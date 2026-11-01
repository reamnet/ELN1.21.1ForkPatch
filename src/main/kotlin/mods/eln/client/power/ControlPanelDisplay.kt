package mods.eln.client.power

import mods.eln.client.gl.GL11
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.Obj3D
import mods.eln.misc.UtilsClient

/** Static panel plates and legends bake with the case; only lamps, bars and digits stay live. */
object ControlPanelDisplay {
    val PARTS=(0..3).flatMap { listOf("StatusLed$it","ChannelLed$it") }.toSet()+"Indicator"
    fun body(model: Obj3D) {
        fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
        part("PanelFrame",.10f,.13f,.16f);part("PanelFace",.035f,.065f,.085f)
        part("PanelAccent",.12f,.58f,.65f);part("PanelInk",.75f,.85f,.83f)
        for(i in 0..2)part("Readout$i",.012f,.025f,.03f)
        part("LinkTrack",.08f,.16f,.19f)
        for(i in 0..3)part("BranchTrack$i",.08f,.16f,.19f)
    }
    fun bar(model: Obj3D,name: String,fraction: Double,r: Float=.2f,g: Float=.85f,b: Float=.7f)=TerrainModels.dynamic {
        val box=model.getPart(name)?.boundingBox() ?: return@dynamic
        val fill=if(fraction.isFinite())fraction.coerceIn(0.0,1.0) else 0.0
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);UtilsClient.disableLight();GL11.glColor3f(r,g,b)
            val left=box.min.x;val right=left+(box.max.x-left)*fill;val z=box.max.z+.001
            GL11.glBegin(GL11.GL_QUADS);GL11.glNormal3f(0f,0f,1f)
            GL11.glVertex3d(left,box.min.y,z);GL11.glVertex3d(right,box.min.y,z)
            GL11.glVertex3d(right,box.max.y,z);GL11.glVertex3d(left,box.max.y,z);GL11.glEnd()
        } finally { UtilsClient.enableLight();GL11.glPopAttrib() }
    }
}
