package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.UtilsClient
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.PowerCabinetDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class UpsDescriptor(name: String,wallMounted: Boolean=false,val compact: Boolean=false) : PowerCabinetDescriptor(name,if(compact)CompactUpsElement::class.java else UpsElement::class.java,UpsRender::class.java,wallMounted) {
    private val model=Eln.obj.getObj(if(compact)"compactups" else if(wallMounted)"wallups" else "programmableups")
    fun draw(front: Direction, state: UpsDisplay?=null)=preserveMatrix {
        front.inverse.glRotateZnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.14f,.16f,.18f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.28f,.31f,.34f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.04f,.05f,.06f);model.getPart("Vents")?.drawNoBind()
            GL11.glColor3f(.18f,.36f,.65f);model.getPart("Input")?.drawNoBind()
            GL11.glColor3f(.85f,.40f,.12f);model.getPart("Output")?.drawNoBind()
            GL11.glColor3f(.015f,.045f,.055f);model.getPart("Display")?.drawNoBind()
            if(compact) { GL11.glColor3f(.035f,.04f,.045f);model.getPart(if(state?.target==240.0)"Socket240" else "Socket120")?.drawNoBind() }
            val powered=state!=null && (state.inputVolts>10 || state.energy>0)
            val panel=mods.eln.client.power.PowerPanelDisplay
            panel.lamp(model,"MainsLed",state?.inputVolts?.let { it>10 }==true,.1f,.8f,.2f)
            panel.lamp(model,"BackupLed",state?.status=="BACKUP" || state?.status=="ASSIST",1f,.6f,.05f)
            panel.lamp(model,"ChargeLed",state?.status=="CHARGING" || state?.status=="CHARGING_OFF",.1f,.5f,1f)
            panel.lamp(model,"FaultLed",state?.status in listOf("PROTECTED","LIMITED","EMPTY","OVERTEMPERATURE"),1f,.08f,.04f)
            panel.lamp(model,"BypassLed",state?.bypass==true,.8f,.3f,1f)
            if(powered) {
                val z=if(wallMounted)-.169 else if(compact).388 else .358
                panel.number(state!!.outputVolts,-.23,.285,z)
                panel.number(if(state.capacity>0)100*state.energy/state.capacity else 0.0,.035,.285,z,3)
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any) { draw(Direction.XN) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        if(compact) {
            list.add(tr("Four battery cells; four backed-up outlets; 120 or 240 V output"))
            list.add(tr("Charging input limited to 3000 W and 20 A; output up to 3000 W total"))
            list.add(tr("Use a 120 V or 240 V cord on the input; select matching output cords"))
        } else {
        list.add(tr("Programmable output: 1 to 3200 V; up to 10000 W"))
        list.add(tr("Install one or two complete racks of 24 portable batteries"))
        }
        list.add(tr("Accepts portable batteries and AGM, LiFePO4 or industrial UPS cells"))
        list.add(tr("Blue terminal: input; orange terminal: output"))
        list.add(tr("Batteries retain their charge when removed"))
        list.add(tr("Manual bypass passes input voltage straight through; no regulation or battery backup"))
        if(wallMounted)list.add(tr("Place against a vertical wall; connect cables on the same wall face"))
    }
}
