package mods.eln.transparentnode.pump

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sound.LoopedSound
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

fun pumpStatus(value: String)=when(value) {
    "OFF"->tr("Switched off")
    "UNLOADED"->tr("Pump chunk unloaded")
    "NO_POWER"->tr("Waiting for electrical power")
    "OVERVOLTAGE"->tr("Overvoltage protection")
    "BLOCKED"->tr("Output full or another liquid remains")
    "RUNNING"->tr("Pumping")
    else->tr("Intake dry")
}
fun pumpSide(side: net.minecraft.core.Direction)=when(side) {
    net.minecraft.core.Direction.DOWN->tr("Down")
    net.minecraft.core.Direction.UP->tr("Up")
    net.minecraft.core.Direction.NORTH->tr("North")
    net.minecraft.core.Direction.SOUTH->tr("South")
    net.minecraft.core.Direction.WEST->tr("West")
    net.minecraft.core.Direction.EAST->tr("East")
}
class IndustrialPumpDescriptor: TransparentNodeDescriptor(I18N.TR_NAME(I18N.Type.NONE,"480 V Industrial Fluid Pump"),
    IndustrialPumpElement::class.java,IndustrialPumpRender::class.java,EntityMetaTag.Fluid) {
    private val model=Eln.obj.getObj("industrialpump")
    override fun mustHaveFloor()=false
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    fun draw(intake: net.minecraft.core.Direction=net.minecraft.core.Direction.DOWN,active: Boolean=false,angle: Float=0f)=preserveMatrix {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.13f,.16f,.18f);model.getPart("Base")?.drawNoBind()
            GL11.glColor3f(.17f,.38f,.56f);model.getPart("Motor")?.drawNoBind()
            GL11.glColor3f(.39f,.44f,.47f);model.getPart("Pump")?.drawNoBind()
            GL11.glColor3f(.07f,.08f,.09f);model.getPart("Vents")?.drawNoBind()
            for(side in net.minecraft.core.Direction.entries) {
                if(side==intake)GL11.glColor3f(.08f,.46f,.86f) else GL11.glColor3f(.95f,.48f,.08f)
                model.getPart("Port${side.ordinal}")?.drawNoBind()
            }
            GL11.glColor3f(.66f,.69f,.7f)
            preserveMatrix { GL11.glTranslated(-.18,.02,.34);GL11.glRotatef(angle,0f,0f,1f);model.getPart("Rotor")?.drawNoBind() }
            if(active)UtilsClient.disableLight()
            GL11.glColor3f(.06f,if(active).85f else .16f,.10f);model.getPart("Indicator")?.drawNoBind()
            if(active)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw()
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("480 V / 4000 W; nominal flow 40 buckets per second"))
        list.add(tr("Flow follows supply voltage: 432 V = 36 B/s; 528 V = 44 B/s"))
        list.add(tr("Blue port: selectable intake; orange ports: fluid output"))
        list.add(tr("Draws from adjacent tanks, pipes or source blocks; valid infinite water stays renewable"))
        list.add(tr("40-bucket input and output buffers; compatible with standard NeoForge fluid pipes"))
        list.add(tr("Power with fixed cables or a 480 V Industrial Cord"))
    }
}
class IndustrialPumpContainer(player: Player,inventory: Container): BasicContainer(player,inventory,emptyArray())
class IndustrialPumpRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var enabled=true;var intake=net.minecraft.core.Direction.DOWN;var status="DRY"
    var volts=0.0;var watts=0.0;var flow=0.0;var raw=0;var output=0;var liquid=""
    val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    private var angle=0f
    val pumpSound=object: LoopedSound("eln:industrial_pump",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
        override fun getVolume()=if(enabled && status=="RUNNING" && watts>1).32f else 0f
        override fun getPitch()=(kotlin.math.abs(volts)/480).coerceIn(.25,1.5).toFloat()
    }
    init { addLoopedSound(pumpSound) }
    override fun refresh(deltaT: Float) { super.refresh(deltaT);if(status=="RUNNING")angle=(angle+deltaT*720*(kotlin.math.abs(volts)/480).toFloat())%360 }
    override fun draw() {
        cable=drawCable(Direction.YN,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        mods.eln.client.terrain.TerrainModels.body(this,listOf(intake,status=="RUNNING" && watts>1),setOf("Rotor")) {
            (transparentNodeDescriptor as IndustrialPumpDescriptor).draw(intake,status=="RUNNING" && watts>1,angle)
        }
    }
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream);enabled=stream.readBoolean();intake=net.minecraft.core.Direction.entries[stream.readUnsignedByte().coerceIn(0,5)]
        status=stream.readUTF();volts=stream.readDouble();watts=stream.readDouble();flow=stream.readDouble()
        raw=stream.readInt();output=stream.readInt();liquid=stream.readUTF();connections.deserialize(stream);cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=IndustrialPumpGui(player,this)
}
class IndustrialPumpGui(player: Player,private val pump: IndustrialPumpRender): GuiContainerEln(IndustrialPumpContainer(player,pump.inventory)) {
    private lateinit var enabled: GuiButtonEln
    private lateinit var intake: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,228,242,26,160)
    override fun initGui() {
        super.initGui();enabled=newGuiButton(8,25,65,"");intake=newGuiButton(78,25,142,"")
        intake.setComment(0,tr("Place this blue intake against water or a tank; all other faces are outputs"))
    }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);if(obj==enabled)pump.clientSendId(80);if(obj==intake)pump.clientSendId(81) }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        enabled.displayString=if(pump.enabled)tr("On") else tr("Off");intake.displayString=tr("Intake: %1$",pumpSide(pump.intake))
        drawString(8,8,tr("480 V Industrial Fluid Pump"));drawString(8,51,pumpStatus(pump.status))
        drawString(8,65,tr("Supply: %1$ V / %2$ W",pump.volts.toInt(),pump.watts.toInt()))
        drawString(8,79,tr("Flow: %1$ B/s",String.format(java.util.Locale.ROOT,"%.1f",pump.flow/1000)))
        drawString(8,93,tr("Nominal: 40 B/s at 480 V"))
        drawString(8,108,tr("Input buffer: %1$ / 40 B",String.format(java.util.Locale.ROOT,"%.1f",pump.raw/1000.0)))
        drawString(8,122,tr("Output buffer: %1$ / 40 B",String.format(java.util.Locale.ROOT,"%.1f",pump.output/1000.0)))
        drawString(8,137,font.plainSubstrByWidth(if(pump.liquid.isBlank())tr("No liquid") else pump.liquid,212))
    }
}
