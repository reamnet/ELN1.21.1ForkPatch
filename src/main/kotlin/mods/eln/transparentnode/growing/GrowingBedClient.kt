package mods.eln.transparentnode.growing

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.terrain.TerrainModels
import mods.eln.ghost.GhostGroup
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale

fun growingStatus(status: String)=when(status) {
    "OFF" -> tr("Switched off")
    "UNLOADED" -> tr("Growing bed chunk unloaded")
    "BULBS" -> tr("Insert working light bulbs")
    "BLOCKED" -> tr("Harvest output full")
    "WATER" -> tr("Waiting for water")
    "POWER" -> tr("Waiting for electrical power")
    "OVERVOLTAGE" -> tr("Overvoltage protection")
    "TEMPERATURE" -> tr("Temperature outside growing range")
    "GROWING" -> tr("Growing plants")
    else -> tr("Insert crop seeds or planting items")
}
class GrowingBedDescriptor: TransparentNodeDescriptor(I18N.TR_NAME(I18N.Type.NONE,"Climate Controlled Growing Bed"),
    GrowingBedElement::class.java,GrowingBedRender::class.java,EntityMetaTag.Fluid) {
    private val model=Eln.obj.getObj("growingbed")
    init {
        ghostGroup=GhostGroup().apply { addRectangle(0,0,0,3,-2,2);removeElement(0,0,0) }
    }
    fun draw(front: Direction=Direction.XN,stage: Int=2,lit: Boolean=false,planted: Boolean=true)=preserveMatrix {
        front.glRotateXnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.58f,.65f,.66f);model.getPart("Frame")?.drawNoBind()
            GL11.glColor3f(.13f,.17f,.18f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.26f,.16f,.08f);model.getPart("Soil")?.drawNoBind()
            GL11.glColor3f(.09f,.34f,.57f);model.getPart("Water")?.drawNoBind()
            GL11.glColor3f(.35f,.69f,.15f);model.getPart("Output")?.drawNoBind()
            GL11.glColor3f(.13f,.39f,.23f);model.getPart("Display")?.drawNoBind()
            if(planted) {
                GL11.glColor3f(.17f,.49f,.13f);model.getPart("Plants${stage.coerceIn(0,3)}")?.drawNoBind()
            }
            if(lit)UtilsClient.disableLight()
            GL11.glColor3f(if(lit).95f else .35f,if(lit).93f else .36f,if(lit).76f else .33f)
            model.getPart("Bulbs")?.drawNoBind()
            if(lit)UtilsClient.enableLight()
            Unit
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        GL11.glScalef(.19f,.19f,.19f);GL11.glTranslatef(0f,-1.1f,0f);draw()
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("5 blocks wide, 4 high, 1 deep; four trays for up to 20 plants"))
        list.add(tr("240 V; fixed cable or 240 V appliance/dryer cord"))
        list.add(tr("48 bulbs of one type; internal drivers match all bulb voltages"))
        list.add(tr("Install a heating element for thermostat control; heater limited to 1500 W"))
        list.add(tr("Connect water and item pipes to the bottom-center controller"))
        list.add(tr("10-bucket water tank; 50 mB per plant per harvest"))
        list.add(tr("Full lighting and 18-28 C: one harvest every 180 seconds"))
        list.add(tr("Supports standard crop-block seeds; planting stock is retained"))
        list.add(tr("Bulb power adds up; check your supply and cord rating"))
    }
}
class GrowingBedContainer(player: Player,inventory: Container): BasicContainer(player,inventory,
    Array<Slot>(GrowingBedElement.SLOTS) { i ->
        val x=if(i<3)16+i*38 else 158+(i-3)%3*18
        val y=if(i<3)106 else 94+(i-3)/3*18
        object: SlotWithSkinAndComment(inventory,i,x,y,ISlotSkin.SlotSkin.medium,arrayOf(when(i) {
            0 -> tr("Planting stock: up to 20 plants; kept for replanting")
            1 -> tr("Light bulbs: up to 48 of one type")
            2 -> tr("Heating element: thermostat limits heating to 1500 W")
            else -> tr("Harvest output; item pipes extract from these slots")
        })) {
            override fun mayPlace(stack: ItemStack)=GrowingBedElement.accepts(i,stack)
            override fun getMaxStackSize()=when(i) { 0 -> 20;1 -> 48;2 -> 1;else -> 64 }
        }
    })
class GrowingBedRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(GrowingBedElement.SLOTS,64,this)
    var enabled=true;var status="IDLE";var volts=0.0;var watts=0.0;var progress=0.0
    var temperature=20.0;var target=23.0;var water=0;var bulbs=0;var planted=0;var heating=false
    private val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() {
        cable=drawCable(Direction.YN,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        val stage=(progress/GrowingCycle.SECONDS*4).toInt().coerceIn(0,3)
        val lit=enabled && bulbs>0 && watts>1 && absVoltage()>24
        TerrainModels.body(this,listOf(front,stage,lit,planted>0)) {
            (transparentNodeDescriptor as GrowingBedDescriptor).draw(front ?: Direction.XN,stage,lit,planted>0)
        }
    }
    private fun absVoltage()=kotlin.math.abs(volts)
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream);enabled=stream.readBoolean();status=stream.readUTF()
        volts=stream.readDouble();watts=stream.readDouble();progress=stream.readDouble()
        temperature=stream.readDouble();target=stream.readDouble();water=stream.readInt()
        bulbs=stream.readInt();planted=stream.readInt();heating=stream.readBoolean();connections.deserialize(stream);cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=GrowingBedGui(player,this)
}
class GrowingBedGui(player: Player,private val bed: GrowingBedRender): GuiContainerEln(GrowingBedContainer(player,bed.inventory)) {
    private lateinit var enabled: GuiButtonEln
    private lateinit var colder: GuiButtonEln
    private lateinit var warmer: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,240,310,8,228)
    override fun initGui() {
        super.initGui();enabled=newGuiButton(184,23,48,"")
        colder=newGuiButton(8,182,36,tr("Down"));warmer=newGuiButton(196,182,36,tr("Up"))
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) { enabled -> bed.clientSendId(80);colder -> bed.clientSendInt(81,-1);warmer -> bed.clientSendInt(81,1) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        enabled.displayString=if(bed.enabled)tr("On") else tr("Off")
        drawString(8,8,font.plainSubstrByWidth(tr("Climate Controlled Growing Bed"),224))
        drawString(8,29,font.plainSubstrByWidth(growingStatus(bed.status),170))
        drawString(8,46,tr("Supply: %1$ V / %2$ W",bed.volts.toInt(),bed.watts.toInt()))
        drawString(8,60,tr("Water: %1$ / 10000 mB",bed.water))
        drawString(8,74,tr("Plants: %1$ / 20; bulbs: %2$ / 48",bed.planted,bed.bulbs))
        drawString(8,94,tr("Seeds"));drawString(46,94,tr("Bulbs"));drawString(84,94,tr("Heater"))
        drawString(156,82,tr("Harvest"))
        drawString(8,150,tr("Progress: %1$%",(100*bed.progress/GrowingCycle.SECONDS).toInt()))
        drawString(8,166,tr("Bed temperature: %1$ C",String.format(Locale.ROOT,"%.1f",bed.temperature)))
        val target=tr("Target: %1$ C",bed.target.toInt())
        drawString(120-font.width(target)/2,188,target)
        drawString(8,210,if(bed.heating)tr("Heating element: On") else tr("Heating element: Off"))
    }
}
