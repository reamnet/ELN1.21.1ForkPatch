package mods.eln.transparentnode.festive

import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream

abstract class FestiveRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var powered=false
    var channel="xmas"
    var volts=120
    var mountOffset=0.0
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream);powered=stream.readBoolean();channel=stream.readUTF();volts=stream.readInt();mountOffset=stream.readDouble()
    }
    override fun newGuiDraw(side: Direction,player: Player)=FestiveGui(player,this)
}
class FestiveGui(player: Player,private val render: FestiveRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var channel: GuiTextFieldEln
    private lateinit var voltage: GuiButtonEln
    private lateinit var save: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,176,206,8,124)
    override fun initGui() {
        super.initGui();channel=newGuiTextField(8,40,160).apply { text=render.channel;setComment(0,tr("Wireless lamp supply channel")) }
        save=newGuiButton(8,62,160,tr("Set wireless channel"));voltage=newGuiButton(8,86,160,"")
    }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);when(obj) { save->render.clientSendString(81,channel.text);voltage->render.clientSendInt(80,if(render.volts==120)240 else 120) } }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);drawString(8,8,tr("Holiday lighting"));drawString(8,22,if(render.cord.endpoint!=null)tr("Powered by cord") else tr("Wireless lamp supply"))
        voltage.displayString=tr("Nominal: %1$ V",render.volts);voltage.enabled=render.cord.endpoint==null
    }
}
