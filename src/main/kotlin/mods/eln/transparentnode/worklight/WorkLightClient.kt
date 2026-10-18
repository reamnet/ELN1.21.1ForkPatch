package mods.eln.transparentnode.worklight

import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.item.lampitem.LampItemSlot
import mods.eln.item.lampitem.LampLists
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sound.LoopedSound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream

object WorkLightMesh {
    fun draw(model: Obj3D,light: Int,count: Int,technology: String) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.13f,.15f,.17f);model.getPart("Base")?.drawNoBind()
            GL11.glColor3f(.93f,.62f,.08f);model.getPart("Metal")?.drawNoBind()
            GL11.glColor3f(.39f,.43f,.46f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.04f,.045f,.05f);model.getPart("Rubber")?.drawNoBind()
            for(i in 0..1) {
                val lit=i<count && light>=8
                if(lit)UtilsClient.disableLight()
                if(!lit)GL11.glColor3f(.17f,.20f,.22f)
                else if(technology=="farming")GL11.glColor3f(1f,.78f,.92f)
                else if(technology=="led" || technology=="fluorescent")GL11.glColor3f(.90f,.96f,1f)
                else GL11.glColor3f(1f,.9f,.65f)
                model.getPart("Light$i")?.drawNoBind()
                if(lit)UtilsClient.enableLight()
            }
        } finally { GL11.glPopAttrib() }
    }
}
class WorkLightContainer(player: Player,inventory: Container) : BasicContainer(player,inventory,arrayOf(
    LampItemSlot(inventory,0,17,60,2,LampLists.lampTechnologyList.toTypedArray())))

class WorkLightRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(1,2,this)
    private val fixture=descriptor as WorkLightDescriptor
    private val connections=LRDUMask()
    private var cable: CableRenderType?=null
    var running=false;private set
    var fuel=0.0;private set
    var wireless=false;private set
    var channel="";private set
    var voltage=120.0;private set
    private var light=0;private var count=0;private var technology="led"
    init { addLoopedSound(object : LoopedSound("eln:fuelgenerator",coordinate(),SoundInstance.Attenuation.LINEAR) {
        override fun getVolume()=if(running).18f else 0f
        override fun getPitch()=.9f
    }) }
    override fun draw() {
        cable=drawCable(Direction.YN,fixture.cableRenderDescriptor,connections,cable)
        preserveMatrix { front!!.inverse.glRotateZnRef();fixture.drawFixture(light,count,technology) }
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        connections.deserialize(stream);running=stream.readBoolean();stream.readFloat()
        fuel=stream.readDouble();wireless=stream.readBoolean();channel=stream.readUTF()
        light=stream.readInt();count=stream.readInt();technology=stream.readUTF();voltage=stream.readDouble();cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=WorkLightGui(player,this)
}
class WorkLightGui(player: Player,private val render: WorkLightRender) : GuiContainerEln(WorkLightContainer(player,render.inventory)) {
    private lateinit var engine: GuiButtonEln
    private lateinit var mode: GuiButtonEln
    private lateinit var channel: GuiTextFieldEln
    override fun newHelper()=GuiHelperContainer(this,176,211,8,130)
    override fun initGui() {
        super.initGui()
        engine=newGuiButton(8,82,76,"")
        mode=newGuiButton(88,82,80,"")
        channel=newGuiTextField(9,111,158).apply { text=render.channel;setComment(0,tr("Wireless lamp supply channel")) }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            engine -> render.clientSendId(80)
            mode -> render.clientSendId(81)
            channel -> render.clientSendString(82,channel.text)
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        engine.displayString=if(render.running)tr("Stop engine") else tr("Start engine")
        engine.enabled=!render.wireless && render.fuel>0
        mode.displayString=if(render.wireless)tr("Wireless") else tr("Gas / cable")
        channel.visible=render.wireless
        drawString(8,8,tr("Construction Work Light"))
        drawString(8,22,tr("Fuel: %1$% (2-bucket tank)",(render.fuel*100).toInt()))
        drawString(8,36,tr("Generator: 2000 W / %1$ V",render.voltage.toInt()))
        drawString(40,64,tr("Two matching bulbs"))
        if(!render.wireless)drawString(8,111,tr("Refuel with gasoline in hand"))
    }
}
