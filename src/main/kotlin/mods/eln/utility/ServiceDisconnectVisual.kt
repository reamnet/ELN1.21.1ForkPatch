package mods.eln.utility

import mods.eln.client.gl.GL11
import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.misc.preserveMatrix

/** The blades and external red handle follow the real contact state. */
object ServiceDisconnectVisual {
    fun draw(front: Direction,open: Boolean,model: Obj3D)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.55f,.58f,.58f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.12f,.14f,.14f);model.getPart("Insulator")?.drawNoBind()
            GL11.glColor3f(.70f,.48f,.20f);model.getPart("Lugs")?.drawNoBind()
            repeat(3) { i -> preserveMatrix {
                GL11.glTranslated(0.0,-.10,-.28);if(open)GL11.glRotated(58.0,1.0,0.0,0.0);GL11.glTranslated(0.0,.10,.28)
                GL11.glColor3f(.78f,.53f,.25f);model.getPart("Blade$i")?.drawNoBind()
            } }
            preserveMatrix {
                GL11.glTranslated(.34,0.0,-.24);if(open)GL11.glRotated(130.0,0.0,0.0,1.0);GL11.glTranslated(-.34,0.0,.24)
                GL11.glColor3f(.82f,.035f,.025f);model.getPart("Handle")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
        Unit
    }
}
