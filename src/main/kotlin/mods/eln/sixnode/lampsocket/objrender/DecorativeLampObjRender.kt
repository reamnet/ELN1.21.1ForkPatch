package mods.eln.sixnode.lampsocket.objrender

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.misc.*
import mods.eln.sixnode.lampsocket.*

class DecorativeLampObjRender(val style: DecorativeLampStyle) : ILampSocketObjRender {
    private val model = Eln.obj.getObj(style.model)
    override fun mountingBounds() = net.minecraft.world.phys.AABB(model.xMin.toDouble(),model.yMin.toDouble(),model.zMin.toDouble(),model.xMax.toDouble(),model.yMax.toDouble(),model.zMax.toDouble())
    override fun connectionEdges(front: LRDU, offset: Double) = LampConnections.edges(model.getPart("Base"),front,offset-90)
    override fun draw(descriptor: LampSocketDescriptor, type: ItemRenderType, distanceToPlayer: Double) = preserveMatrix {
        if (!style.uprightItem) {
            GL11.glRotatef(-70f,0f,1f,0f)
        } else GL11.glRotatef(if(style.ceiling) -90f else 90f,0f,0f,1f)
        val scale = 1.35/if(style==DecorativeLampStyle.FAN_INDUSTRIAL)5.0 else style.depth
        GL11.glScaled(scale,scale,scale)
        GL11.glTranslated(-style.depth*.5,0.0,0.0)
        drawModel(0,style.bulbs,"led",style.newAim())
    }
    override fun draw(render: LampSocketRender, distanceToPlayer: Double) = preserveMatrix {
        render.front!!.glRotateOnX()
        GL11.glRotatef(-90f,1f,0f,0f)
        drawModel(render.lightValue,render.bulbCount,render.lampDescriptor?.lampData?.technology?.lampType ?: "led",render.aim,render.fanAngle)
    }
    private fun swivel(aim: LightAim,head: Int,draw: ()->Unit)=preserveMatrix {
        val p=style.aimPivot(head)
        GL11.glTranslated(p.x,p.y,p.z)
        if(aim.axialPan)GL11.glRotated(aim.pan(head),1.0,0.0,0.0) else GL11.glRotated(-aim.pan(head),0.0,1.0,0.0)
        GL11.glRotated(aim.tilt(head),0.0,0.0,1.0)
        GL11.glTranslated(-p.x,-p.y,-p.z);draw()
    }
    private fun lens(index: Int,aim: LightAim) {
        if(style.aimHeadCount>0)swivel(aim,style.aimHead(index)) { model.getPart("Light$index")?.drawNoBind() }
        else model.getPart("Light$index")?.drawNoBind()
    }
    private fun drawModel(light: Int, count: Int, technology: String,aim: LightAim,fanAngle: Float=0f) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            when(style) {
                DecorativeLampStyle.PORCH -> GL11.glColor3f(.20f,.14f,.09f)
                DecorativeLampStyle.HOLDER -> GL11.glColor3f(.88f,.86f,.80f)
                else -> GL11.glColor3f(.20f,.23f,.26f)
            }
            model.getPart("Base")?.drawNoBind()
            when(style) {
                DecorativeLampStyle.PORCH -> GL11.glColor3f(.28f,.20f,.12f)
                DecorativeLampStyle.CANDLE, DecorativeLampStyle.CRYSTAL, DecorativeLampStyle.SPUTNIK -> GL11.glColor3f(.66f,.45f,.16f)
                DecorativeLampStyle.WAGON, DecorativeLampStyle.LANTERN -> GL11.glColor3f(.12f,.13f,.14f)
                DecorativeLampStyle.SHOP, DecorativeLampStyle.FACTORY -> GL11.glColor3f(.75f,.77f,.78f)
                else -> GL11.glColor3f(.26f,.29f,.32f)
            }
            if(style.luxury)GL11.glColor3f(.77f,.58f,.28f)
            if(style == DecorativeLampStyle.RIG_CAGE || style == DecorativeLampStyle.RIG_LINEAR || style == DecorativeLampStyle.RIG_FLOOD)GL11.glColor3f(.82f,.64f,.12f)
            model.getPart("Metal")?.drawNoBind()
            GL11.glColor3f(.65f,.68f,.71f); model.getPart("Trim")?.drawNoBind()
            if(style.fan)preserveMatrix {
                GL11.glRotatef(fanAngle,1f,0f,0f)
                if(style==DecorativeLampStyle.FAN_AURA)GL11.glColor3f(.46f,.48f,.49f) else if(style==DecorativeLampStyle.FAN_CLASSIC)GL11.glColor3f(.34f,.20f,.10f) else GL11.glColor3f(.12f,.14f,.16f)
                model.getPart("Rotor")?.drawNoBind()
            }
            for(head in 0 until style.aimHeadCount)swivel(aim,head) {
                if(style==DecorativeLampStyle.RIG_FLOOD)GL11.glColor3f(.82f,.64f,.12f) else GL11.glColor3f(.22f,.25f,.28f)
                model.getPart("Head${head}Metal")?.drawNoBind()
                GL11.glColor3f(.65f,.68f,.71f);model.getPart("Head${head}Trim")?.drawNoBind()
            }
            if(style == DecorativeLampStyle.TABLE_BANKER) GL11.glColor3f(.08f,.38f,.20f)
            else if(style == DecorativeLampStyle.HALLOWEEN_GHOST) GL11.glColor3f(.93f,.94f,.96f)
            else GL11.glColor3f(.88f,.79f,.62f)
            model.getPart("Shade")?.let {
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
                GL11.glDisable(GL11.GL_CULL_FACE)
                it.drawNoBind()
                GL11.glPopAttrib()
            }
            GL11.glColor3f(.92f,.35f,.035f);model.getPart("Accent")?.drawNoBind()
            for (i in 0 until style.bulbs) {
                val installed = i < count
                if (!installed && style.exposedBulbs) continue
                if (installed && light >= LampSocketRender.MIN_LIGHT_ON_VALUE) {
                    UtilsClient.disableLight()
                    when(technology) {
                        "led", "fluorescent" -> GL11.glColor3f(.90f,.96f,1f)
                        "farming" -> GL11.glColor3f(1f,.78f,.92f)
                        else -> GL11.glColor3f(1f,.90f,.65f)
                    }
                    when(style) {
                        DecorativeLampStyle.CHRISTMAS_FAIRY,DecorativeLampStyle.PARTY_FESTOON,DecorativeLampStyle.PARTY_TIKI -> GL11.glColor3f(1f,.76f,.35f)
                        DecorativeLampStyle.CHRISTMAS_COLOR,DecorativeLampStyle.PARTY_WASH -> when(i%4) {
                            0->GL11.glColor3f(1f,.10f,.05f);1->GL11.glColor3f(.12f,1f,.20f);2->GL11.glColor3f(.15f,.35f,1f);else->GL11.glColor3f(1f,.72f,.08f)
                        }
                        DecorativeLampStyle.CHRISTMAS_ICICLE,DecorativeLampStyle.HALLOWEEN_GHOST -> GL11.glColor3f(.65f,.80f,1f)
                        DecorativeLampStyle.CHRISTMAS_STAR -> GL11.glColor3f(1f,.80f,.20f)
                        DecorativeLampStyle.HALLOWEEN_PUMPKIN,DecorativeLampStyle.HALLOWEEN_JACK -> GL11.glColor3f(1f,.40f,.035f)
                        DecorativeLampStyle.HALLOWEEN_BAT -> GL11.glColor3f(.68f,.15f,1f)
                        else -> Unit
                    }
                    lens(i,aim)
                    UtilsClient.enableLight()
                } else {
                    GL11.glColor3f(.18f,.21f,.24f)
                    lens(i,aim)
                }
            }
            model.getPart("Glass")?.let {
                GL11.glEnable(GL11.GL_BLEND)
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA)
                GL11.glDepthMask(false)
                GL11.glColor4f(.70f,.84f,.91f,.16f)
                it.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
}
