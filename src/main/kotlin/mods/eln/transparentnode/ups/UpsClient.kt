package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.client.Minecraft
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.DataSlot
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale

fun upsStatusText(status: String,compact: Boolean=false)=when(status) {
    "BYPASS" -> tr("Bypass: direct input voltage")
    "BYPASS_NO_INPUT" -> tr("Bypass: no input; no battery backup")
    "NEEDS_BATTERIES" -> if(compact)tr("Insert a battery cell (up to four)") else tr("Needs a full rack (24 cells)")
    "OVERTEMPERATURE" -> tr("Cooling down")
    "CHARGING_OFF" -> tr("Charging; output off")
    "DISABLED" -> tr("Output off")
    "EMPTY" -> tr("Battery empty")
    "PROTECTED" -> tr("Output protection; retrying")
    "LIMITED" -> tr("Output power limited")
    "BACKUP" -> tr("On battery backup")
    "ASSIST" -> tr("Online; battery assisting")
    "CHARGING" -> tr("Online; charging")
    else -> tr("Online")
}

class UpsContainer(player: Player, inventory: Container, private val pageState: IntArray=intArrayOf(0),val compact: Boolean=false) :
    BasicContainer(player,inventory,Array<net.minecraft.world.inventory.Slot>(if(compact)4 else 48) { i ->
        object : SlotWithSkinAndComment(inventory,i,if(compact)52+i*18 else 16+(i%8)*18,141+(i%24/8)*18,
            ISlotSkin.SlotSkin.medium,arrayOf(tr("Portable battery or larger UPS cell"))) {
            override fun mayPlace(stack: ItemStack)=upsBattery(stack)!=null
            override fun getMaxStackSize()=1
            override fun isActive()=compact || i/24==pageState[0]
            override fun getComment(list: MutableList<String>) { if(isActive)super.getComment(list) }
        }
    }) {
    val page get()=pageState[0]
    init {
        addDataSlot(object : DataSlot() { override fun get()=page;override fun set(value: Int) { selectPage(value) } })
    }
    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if(compact || id !in 0..1)return false
        selectPage(id);return true
    }
    fun selectPage(value: Int) { pageState[0]=value.coerceIn(0,1) }
    override fun quickMoveStack(player: Player, slotId: Int): ItemStack {
        if(slotId<(if(compact)4 else 48))return super.quickMoveStack(player,slotId)
        val slot=slots.getOrNull(slotId) ?: return ItemStack.EMPTY
        val stack=slot.item
        if(upsBattery(stack)==null)return super.quickMoveStack(player,slotId)
        if(mergeItemStack(stack,if(compact)0 else page*24,if(compact)4 else (page+1)*24,false)) {
            if(stack.isEmpty)slot.set(ItemStack.EMPTY) else slot.setChanged()
        }
        return ItemStack.EMPTY
    }
}

class UpsDisplay {
    var target=120.0;var enabled=true;var installed=0;var active=0
    var energy=0.0;var capacity=0.0;var inputVolts=0.0;var outputVolts=0.0;var inputWatts=0.0;var outputWatts=0.0
    var status="NEEDS_BATTERIES"
    var bypass=false
    val connections=LRDUMask()
    fun read(stream: DataInputStream) {
        target=stream.readDouble();enabled=stream.readBoolean();installed=stream.readInt();active=stream.readInt()
        energy=stream.readDouble();capacity=stream.readDouble();inputVolts=stream.readDouble();outputVolts=stream.readDouble()
        inputWatts=stream.readDouble();outputWatts=stream.readDouble();status=stream.readUTF();bypass=stream.readBoolean();connections.deserialize(stream)
    }
}
class UpsRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(if((descriptor as UpsDescriptor).compact)4 else 48,1,this)
    val display=UpsDisplay()
    val compact=(descriptor as UpsDescriptor).compact
    var sockets=0
    val outletRating get()=if(display.target==240.0)mods.eln.transparentnode.cord.CordRating.V240 else mods.eln.transparentnode.cord.CordRating.V120
    private val left=LRDUMask();private val right=LRDUMask();private var cable: CableRenderType?=null
    override fun draw() {
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front,sockets,if(compact)display.target else 0.0),mods.eln.client.power.PowerPanelDisplay.PARTS) {
            (transparentNodeDescriptor as UpsDescriptor).draw(front ?: Direction.XN,display)
            if(compact)repeat(4) { if(sockets and (1 shl it)!=0)mods.eln.transparentnode.cord.CompactSocketGeometry.plug(front ?: Direction.ZP,outletRating,it) }
        }
        val direction=front ?: return
        val plane=(transparentNodeDescriptor as UpsDescriptor).connectionPlane(direction)
        val a=plane.getLRDUGoingTo(direction.left())!!
        val b=plane.getLRDUGoingTo(direction.right())!!
        left.set(0);right.set(0);left[a]=display.connections[a];right[b]=display.connections[b]
        cable=drawCable(plane,Eln.instance.stdCableRender3200V,left,cable)
        cable=drawCable(plane,Eln.instance.stdCableRender3200V,right,cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream);if(compact)sockets=stream.readInt() }
    override fun newGuiDraw(side: Direction, player: Player)=UpsGui(player,inventory,this,display)
}
class UpsGui(player: Player, inventory: Container, private val render: UpsRender, private val display: UpsDisplay) :
    GuiContainerEln(UpsContainer(player,inventory,compact=render.compact)) {
    private lateinit var voltage: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    private lateinit var enabled: GuiButtonEln
    private lateinit var rack: GuiButtonEln
    private lateinit var bypass: GuiButtonEln
    private var unplug: GuiButtonEln?=null
    private val bankMenu get()=menu as UpsContainer
    override fun newHelper()=GuiHelperContainer(this,176,290,8,205)
    override fun initGui() {
        super.initGui()
        voltage=newGuiTextField(8,99,72).apply { text=display.target.toString();setComment(0,if(render.compact)tr("Output voltage: 120 or 240 V; unplug all outputs before changing") else tr("Output voltage: 1 to 3200 V")) }
        apply=newGuiButton(84,95,38,tr("Set V"))
        enabled=newGuiButton(126,95,42,"")
        bypass=newGuiButton(8,117,76,"").apply { setComment(0,tr("Passes input voltage unchanged; the inverter and battery backup are disconnected")) }
        if(render.compact)unplug=newGuiButton(88,117,80,tr("Unplug input"))
        rack=newGuiButton(88,117,80,"").apply { setComment(0,tr("Click to switch battery racks")) }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            apply -> voltage.text.toDoubleOrNull()?.takeIf { if(render.compact)it==120.0 || it==240.0 else UpsPowerUnit.validVoltage(it) }?.let { render.clientSendDouble(80,it) }
            enabled -> render.clientSendBoolean(81,!display.enabled)
            bypass -> render.clientSendBoolean(82,!display.bypass)
            unplug -> render.clientSendId(83)
            rack -> {
                val next=1-bankMenu.page
                bankMenu.selectPage(next)
                Minecraft.getInstance().gameMode!!.handleInventoryButtonClick(menu.containerId,next)
            }
        }
    }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f,x,y)
        apply.enabled=!display.bypass && voltage.text.toDoubleOrNull()?.let { if(render.compact)it==120.0 || it==240.0 else UpsPowerUnit.validVoltage(it) }==true
        voltage.enabled=!display.bypass
        bypass.displayString=if(display.bypass)tr("Bypass: On") else tr("Bypass: Off")
        enabled.displayString=if(display.enabled)tr("On") else tr("Off")
        rack.visible=!render.compact
        rack.displayString=tr("Rack %1$/2",bankMenu.page+1)
        drawString(8,8,if(render.compact)tr("Four Cell Plug-in UPS") else tr("Programmable UPS"))
        drawString(8,22,font.plainSubstrByWidth(upsStatusText(display.status,render.compact),160))
        val percent=if(display.capacity>0)100*display.energy/display.capacity else 0.0
        drawString(8,34,tr("Charge: %1$% / %2$ Wh",format(percent),format(display.capacity/3600)))
        drawString(8,46,tr("Batteries: %1$/%2$ (%3$ active)",display.installed,if(render.compact)4 else 48,display.active))
        drawString(8,58,tr("In: %1$ / %2$",Utils.plotVolt("",display.inputVolts).trim(),Utils.plotPower("",display.inputWatts).trim()))
        drawString(8,70,tr("Out: %1$ / %2$",Utils.plotVolt("",display.outputVolts).trim(),Utils.plotPower("",display.outputWatts).trim()))
        drawString(8,82,if(display.bypass)tr("Bypass: no battery backup") else if(display.outputWatts>1)tr("Backup: about %1$ min",format(display.energy*UpsPowerUnit.EFFICIENCY/display.outputWatts/60)) else tr("Backup: no load"))
    }
    private fun format(value: Double)=String.format(Locale.ROOT,"%.1f",value)
}
