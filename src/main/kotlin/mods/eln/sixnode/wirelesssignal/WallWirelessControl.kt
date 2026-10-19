package mods.eln.sixnode.wirelesssignal

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.preserveMatrix
import mods.eln.sixnode.electricalgatesource.ElectricalGateSourceRenderObj
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class WallControlModel(private val paddle: Boolean) : ElectricalGateSourceRenderObj(null) {
    private val model=Eln.obj.getObj(if(paddle)"wirelesspaddleswitch" else "wirelesstoggleswitch")
    init { speed=8f }
    override fun draw(factor: Float,distance: Float,e: BlockEntity?): Unit = preserveMatrix {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.89f,.88f,.83f);model.getPart("Plate")?.drawNoBind()
            GL11.glColor3f(.52f,.54f,.56f);model.getPart("Trim")?.drawNoBind()
            GL11.glRotatef((factor-.5f)*(if(paddle)16f else 40f),0f,0f,1f)
            GL11.glColor3f(.97f,.96f,.92f);model.getPart("Lever")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
    }
}

class WallWirelessDescriptor(name: String,val paddle: Boolean,val momentary: Boolean) : WirelessSignalSourceDescriptor(name,
    WallControlModel(paddle),Eln.config.getIntOrElse("wireless.transmitter.maxRangeBlocks",32),momentary) {
    private val model=WallControlModel(paddle)
    init { setPlaceDirection(arrayOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) }
    override fun shouldUseRenderHelper(type: ItemRenderType,item: ItemStack,helper: ItemRendererHelper)=true
    override fun renderItem(type: ItemRenderType,item: ItemStack,vararg data: Any) { model.draw(0f,0f,null) }
    override fun addInformation(itemStack: ItemStack,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        super.addInformation(itemStack,entityPlayer,list,par4)
        list.add(tr("Right-click to operate; use a wrench to configure the wireless channel"))
        list.add(tr("Pairs with the existing Wireless Signal Receiver; connect its output to a Lamp Supply control"))
    }
}
