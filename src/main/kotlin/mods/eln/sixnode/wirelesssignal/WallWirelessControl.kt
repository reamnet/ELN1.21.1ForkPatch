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
import mods.eln.node.six.*
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceElement
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceRender
import mods.eln.sound.SoundCommand
import mods.eln.misc.UtilsClient
import kotlin.math.ceil

class WallControlModel(private val paddle: Boolean) : ElectricalGateSourceRenderObj(null) {
    private val model=Eln.obj.getObj(if(paddle)"wirelesspaddleswitch" else "wirelesstoggleswitch")
    init { speed=.025f }
    override fun draw(factor: Float,distance: Float,e: BlockEntity?): Unit = preserveMatrix {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.89f,.88f,.83f);model.getPart("Plate")?.drawNoBind()
            GL11.glColor3f(.52f,.54f,.56f);model.getPart("Trim")?.drawNoBind()
            val on=factor>.5f
            if(on)UtilsClient.disableLight()
            if(on)GL11.glColor3f(.22f,.64f,.30f) else GL11.glColor3f(.19f,.21f,.20f)
            model.getPart("Indicator")?.drawNoBind()
            if(on)UtilsClient.enableLight()
            GL11.glRotatef((factor-.5f)*(if(paddle)16f else 40f),0f,0f,1f)
            GL11.glColor3f(.97f,.96f,.92f);model.getPart("Lever")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
    }
}

class WallWirelessDescriptor(name: String,val paddle: Boolean,val momentary: Boolean) : WirelessSignalSourceDescriptor(name,
    WallControlModel(paddle),Eln.config.getIntOrElse("wireless.transmitter.maxRangeBlocks",32),momentary) {
    private val model=WallControlModel(paddle)
    init {
        setPlaceDirection(arrayOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
        ElementClass=WallWirelessElement::class.java;RenderClass=WallWirelessRender::class.java
    }
    override fun shouldUseRenderHelper(type: ItemRenderType,item: ItemStack,helper: ItemRendererHelper)=true
    override fun renderItem(type: ItemRenderType,item: ItemStack,vararg data: Any) { model.draw(0f,0f,null) }
    override fun addInformation(itemStack: ItemStack,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        super.addInformation(itemStack,entityPlayer,list,par4)
        list.add(tr("Right-click to operate; use a wrench to configure the wireless channel"))
        list.add(tr("Pairs with the existing Wireless Signal Receiver; connect its output to a Lamp Supply control"))
        list.add(tr("Small green marker: on; dark marker: off"))
    }
}

class WallWirelessElement(node: SixNode,side: Direction,descriptor: SixNodeDescriptor) : WirelessSignalSourceElement(node,side,descriptor) {
    override fun onStateChanged() {
        play(SoundCommand("eln:click").smallRange().apply { blockFactor=0f }.mulVolume(.28f,if(isOn)1.15f else .95f))
    }
}

class WallWirelessRender(entity: SixNodeEntity,side: Direction,descriptor: SixNodeDescriptor) : WirelessSignalSourceRender(entity,side,descriptor) {
    override fun draw() {
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front,indicatorOn()),setOf("Lever")) { super.draw() }
    }
    override fun refresh(deltaT: Float) {
        if(!deltaT.isFinite() || deltaT<=0)return
        val elapsed=deltaT.coerceAtMost(1f)
        val steps=ceil(elapsed/.01f).toInt().coerceAtLeast(1)
        repeat(steps) { super.refresh(elapsed/steps) }
    }
}
