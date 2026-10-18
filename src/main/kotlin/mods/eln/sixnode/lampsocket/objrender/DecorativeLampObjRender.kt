package mods.eln.sixnode.lampsocket.objrender

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.misc.*
import mods.eln.sixnode.lampsocket.*

class DecorativeLampObjRender(val style: DecorativeLampStyle) : ILampSocketObjRender {
    private val model = Eln.obj.getObj(style.model)
    override fun connectionEdges(front: LRDU, offset: Double) = LampConnections.edges(model.getPart("Base"),front,offset-90)
    override fun draw(descriptor: LampSocketDescriptor, type: ItemRenderType, distanceToPlayer: Double) = preserveMatrix {
        if (style == DecorativeLampStyle.WAREHOUSE || style == DecorativeLampStyle.RECESSED) {
            GL11.glRotatef(-70f,0f,1f,0f)
        } else GL11.glRotatef(90f,0f,0f,1f)
        val scale = 1.35/style.depth
        GL11.glScaled(scale,scale,scale)
        GL11.glTranslated(-style.depth*.5,0.0,0.0)
        drawModel(0,style.bulbs,"led")
    }
    override fun draw(render: LampSocketRender, distanceToPlayer: Double) = preserveMatrix {
        render.front!!.glRotateOnX()
        GL11.glRotatef(-90f,1f,0f,0f)
        drawModel(render.lightValue,render.bulbCount,render.lampDescriptor?.lampData?.technology?.lampType ?: "led")
    }
    private fun drawModel(light: Int, count: Int, technology: String) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.20f,.23f,.26f); model.getPart("Base")?.drawNoBind()
            GL11.glColor3f(.26f,.29f,.32f); model.getPart("Metal")?.drawNoBind()
            GL11.glColor3f(.65f,.68f,.71f); model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.88f,.79f,.62f); model.getPart("Shade")?.drawNoBind()
            for (i in 0 until style.bulbs) {
                val installed = i < count
                if (!installed && (style == DecorativeLampStyle.DESK || style == DecorativeLampStyle.CHANDELIER)) continue
                if (installed && light >= LampSocketRender.MIN_LIGHT_ON_VALUE) {
                    UtilsClient.disableLight()
                    when(technology) {
                        "led", "fluorescent" -> GL11.glColor3f(.90f,.96f,1f)
                        "farming" -> GL11.glColor3f(1f,.78f,.92f)
                        else -> GL11.glColor3f(1f,.90f,.65f)
                    }
                    model.getPart("Light$i")?.drawNoBind()
                    UtilsClient.enableLight()
                } else {
                    GL11.glColor3f(.18f,.21f,.24f)
                    model.getPart("Light$i")?.drawNoBind()
                }
            }
        } finally { GL11.glPopAttrib() }
    }
}
