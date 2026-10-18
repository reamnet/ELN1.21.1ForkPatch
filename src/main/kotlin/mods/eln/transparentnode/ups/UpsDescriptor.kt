package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.UtilsClient
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.TransparentNodeDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class UpsDescriptor(name: String) : TransparentNodeDescriptor(name,UpsElement::class.java,UpsRender::class.java) {
    private val model=Eln.obj.getObj("programmableups")
    fun draw(front: Direction, state: UpsDisplay?=null)=preserveMatrix {
        front.glRotateXnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.14f,.16f,.18f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.28f,.31f,.34f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.04f,.05f,.06f);model.getPart("Vents")?.drawNoBind()
            GL11.glColor3f(.18f,.36f,.65f);model.getPart("Input")?.drawNoBind()
            GL11.glColor3f(.85f,.40f,.12f);model.getPart("Output")?.drawNoBind()
            val lit=state!=null && state.active>0 && state.energy>0
            if(lit)UtilsClient.disableLight()
            GL11.glColor3f(if(lit).08f else .015f,if(lit).45f else .04f,if(lit).52f else .05f)
            model.getPart("Display")?.drawNoBind()
            if(state?.status=="BACKUP")GL11.glColor3f(1f,.55f,.08f)
            else if(lit)GL11.glColor3f(.15f,.9f,.25f) else GL11.glColor3f(.15f,.17f,.18f)
            model.getPart("Indicator")?.drawNoBind()
            if(lit)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any) { draw(Direction.XN) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Programmable output: 1 to 3200 V; up to 10000 W"))
        list.add(tr("Install one or two complete racks of 24 portable batteries"))
        list.add(tr("Accepts Portable Battery and Portable Battery Pack"))
        list.add(tr("Blue terminal: input; orange terminal: output"))
        list.add(tr("Batteries retain their charge when removed"))
    }
}
