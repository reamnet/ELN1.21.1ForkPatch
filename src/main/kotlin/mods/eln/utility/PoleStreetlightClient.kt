package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.terrain.TerrainModels
import mods.eln.gridnode.GridRender
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.utilitylight.StreetLightMount
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

object PoleStreetlightVisual {
    private val model by lazy { Eln.obj.getObj("distributionpole") }
    fun draw(render: GridRender) {
        val d=render.poleStreetlight
        val rack=(render.transparentNodeDescriptor as? TransmissionDescriptor)?.kind?.station==false
        if(!d.installed && !rack)return
        TerrainModels.body(render.streetlightOwner,listOf(render.front,d.installed,d.yaw,d.height,d.pitch,d.light>0,render.serviceMountReach)) {
            preserveMatrix {
                (render.front ?: Direction.XN).glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
                try {
                    GL11.glDisable(GL11.GL_TEXTURE_2D)
                    if(rack)PoleSecondaryVisual.suspension(render.serviceMountReach.toDouble())
                    if(d.installed) {
                        GL11.glTranslated(0.0,StreetLightMount.offset(d.height),0.0);GL11.glRotated(d.yaw.toDouble(),0.0,1.0,0.0)
                        fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
                        part("LampBracket",.48f,.50f,.51f)
                        val p=StreetLightMount.pivot;GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated(d.pitch.toDouble(),0.0,0.0,1.0);GL11.glTranslated(-p.x,-p.y,-p.z)
                        part("LampHousing",.26f,.29f,.31f)
                        if(d.light>0) { UtilsClient.disableLight();part("LampLens",1f,.94f,.77f);UtilsClient.enableLight() } else part("LampLens",.14f,.17f,.19f)
                    }
                } finally { GL11.glPopAttrib() }
            }
        }
    }
}

class PoleStreetlightScreen(private val render: GridRender): Screen(Component.literal(tr("Pole streetlight attachment"))) {
    private var d=render.poleStreetlight
    override fun isPauseScreen()=false
    override fun init() {
        val left=(width-308)/2;val top=(height-210)/2
        fun button(x: Int,y: Int,w: Int,text: String,action: ()->Unit)=addRenderableWidget(Button.builder(Component.literal(text)) { action() }.bounds(left+x,top+y,w,20).build())
        button(8,36,142,if(d.enabled)tr("Light: ON") else tr("Light: OFF")) { change(d.copy(enabled=!d.enabled)) }
        button(158,36,142,if(d.automatic)tr("Dusk / night") else tr("Always enabled")) { change(d.copy(automatic=!d.automatic)) }
        button(8,62,292,if(d.supply==0)tr("Supply: automatic 120/240 V") else tr("Supply: %1$ V",d.supply)) { change(d.copy(supply=when(d.supply) { 0->120;120->240;else->0 })) }
        button(8,90,142,tr("Rotate left")) { change(d.copy(yaw=StreetLightMount.turn(d.yaw,-15))) }
        button(158,90,142,tr("Rotate right")) { change(d.copy(yaw=StreetLightMount.turn(d.yaw,15))) }
        button(8,116,142,tr("Lower")) { change(d.copy(height=(d.height-1).coerceAtLeast(StreetLightMount.MIN_STEP))) }
        button(158,116,142,tr("Raise")) { change(d.copy(height=(d.height+1).coerceAtMost(StreetLightMount.MAX_STEP))) }
        button(8,142,142,tr("Aim down")) { change(d.copy(pitch=(d.pitch-5).coerceAtLeast(-60))) }
        button(158,142,142,tr("Aim up")) { change(d.copy(pitch=(d.pitch+5).coerceAtMost(60))) }
        button(8,174,142,tr("Remove attachment")) { send(true);onClose() }
        button(158,174,142,tr("Done")) { onClose() }
    }
    private fun change(next: PoleStreetlightDisplay) { d=next;send(false);rebuildWidgets() }
    private fun send(remove: Boolean) { PacketDistributor.sendToServer(PoleStreetlightControl(render.tileEntity.blockPos,remove,d.enabled,d.automatic,d.supply,d.yaw,d.height,d.pitch)) }
    override fun tick() {
        val p=render.tileEntity.blockPos
        if(minecraft?.player==null || render.tileEntity.isRemoved || minecraft!!.player!!.distanceToSqr(p.x+.5,p.y+.5,p.z+.5)>64)onClose()
    }
    override fun render(g: GuiGraphics,x: Int,y: Int,partial: Float) {
        val left=(width-308)/2;val top=(height-210)/2;g.fill(left,top,left+308,top+210,0xee23272b.toInt())
        g.drawString(font,tr("Pole streetlight attachment"),left+8,top+8,-1,false)
        g.drawString(font,tr("Secondary: %1$; %2$",Utils.plotVolt("",render.poleStreetlight.volts).trim(),Utils.plotPower("",render.poleStreetlight.power).trim()),left+8,top+22,0xffcccccc.toInt(),false)
        super.render(g,x,y,partial)
    }
    companion object {
        fun open(pos: BlockPos) {
            val mc=Minecraft.getInstance();val r=(mc.level?.getBlockEntity(pos) as? TransparentNodeEntity)?.elementRender as? GridRender ?: return
            mc.setScreen(PoleStreetlightScreen(r))
        }
    }
}
