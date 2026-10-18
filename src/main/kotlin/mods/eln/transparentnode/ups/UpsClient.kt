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

fun upsStatusText(status: String)=when(status) {
    "NEEDS_BATTERIES" -> tr("Needs a full rack (24 cells)")
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

class UpsContainer(player: Player, inventory: Container, private val pageState: IntArray=intArrayOf(0)) :
    BasicContainer(player,inventory,Array<net.minecraft.world.inventory.Slot>(48) { i ->
        object : SlotWithSkinAndComment(inventory,i,16+(i%8)*18,141+(i%24/8)*18,
            ISlotSkin.SlotSkin.medium,arrayOf(tr("Portable Battery or Portable Battery Pack"))) {
            override fun mayPlace(stack: ItemStack)=upsBattery(stack)!=null
            override fun getMaxStackSize()=1
            override fun isActive()=i/24==pageState[0]
            override fun getComment(list: MutableList<String>) { if(isActive)super.getComment(list) }
        }
    }) {
    val page get()=pageState[0]
    init {
        addDataSlot(object : DataSlot() { override fun get()=page;override fun set(value: Int) { selectPage(value) } })
    }
    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if(id !in 0..1)return false
        selectPage(id);return true
    }
    fun selectPage(value: Int) { pageState[0]=value.coerceIn(0,1) }
    override fun quickMoveStack(player: Player, slotId: Int): ItemStack {
        if(slotId<48)return super.quickMoveStack(player,slotId)
        val slot=slots.getOrNull(slotId) ?: return ItemStack.EMPTY
        val stack=slot.item
        if(upsBattery(stack)==null)return super.quickMoveStack(player,slotId)
        if(mergeItemStack(stack,page*24,(page+1)*24,false)) {
            if(stack.isEmpty)slot.set(ItemStack.EMPTY) else slot.setChanged()
        }
        return ItemStack.EMPTY
    }
}

class UpsDisplay {
    var target=120.0;var enabled=true;var installed=0;var active=0
    var energy=0.0;var capacity=0.0;var inputVolts=0.0;var outputVolts=0.0;var inputWatts=0.0;var outputWatts=0.0
    var status="NEEDS_BATTERIES"
    val connections=LRDUMask()
    fun read(stream: DataInputStream) {
        target=stream.readDouble();enabled=stream.readBoolean();installed=stream.readInt();active=stream.readInt()
        energy=stream.readDouble();capacity=stream.readDouble();inputVolts=stream.readDouble();outputVolts=stream.readDouble()
        inputWatts=stream.readDouble();outputWatts=stream.readDouble();status=stream.readUTF();connections.deserialize(stream)
    }
}
class UpsRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(48,1,this)
    val display=UpsDisplay()
    private val left=LRDUMask();private val right=LRDUMask();private var cable: CableRenderType?=null
    override fun draw() {
        (transparentNodeDescriptor as UpsDescriptor).draw(front ?: Direction.XN,display)
        val direction=front ?: return
        val a=direction.down().getLRDUGoingTo(direction.left())!!
        val b=direction.down().getLRDUGoingTo(direction.right())!!
        left.set(0);right.set(0);left[a]=display.connections[a];right[b]=display.connections[b]
        cable=drawCable(direction.down(),Eln.instance.stdCableRender3200V,left,cable)
        cable=drawCable(direction.down(),Eln.instance.stdCableRender3200V,right,cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream) }
    override fun newGuiDraw(side: Direction, player: Player)=UpsGui(player,inventory,this,display)
}
class UpsGui(player: Player, inventory: Container, private val render: UpsRender, private val display: UpsDisplay) :
    GuiContainerEln(UpsContainer(player,inventory)) {
    private lateinit var voltage: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    private lateinit var enabled: GuiButtonEln
    private lateinit var rack: GuiButtonEln
    private val bankMenu get()=menu as UpsContainer
    override fun newHelper()=GuiHelperContainer(this,176,290,8,205)
    override fun initGui() {
        super.initGui()
        voltage=newGuiTextField(8,99,72).apply { text=display.target.toString();setComment(0,tr("Output voltage: 1 to 3200 V")) }
        apply=newGuiButton(84,95,38,tr("Set V"))
        enabled=newGuiButton(126,95,42,"")
        rack=newGuiButton(8,117,160,"")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            apply -> voltage.text.toDoubleOrNull()?.takeIf(UpsPowerUnit::validVoltage)?.let { render.clientSendDouble(80,it) }
            enabled -> render.clientSendBoolean(81,!display.enabled)
            rack -> {
                val next=1-bankMenu.page
                bankMenu.selectPage(next)
                Minecraft.getInstance().gameMode!!.handleInventoryButtonClick(menu.containerId,next)
            }
        }
    }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f,x,y)
        apply.enabled=voltage.text.toDoubleOrNull()?.let(UpsPowerUnit::validVoltage)==true
        enabled.displayString=if(display.enabled)tr("On") else tr("Off")
        rack.displayString=tr("Rack %1$/2 - click to switch",bankMenu.page+1)
        drawString(8,8,tr("Programmable UPS"))
        drawString(8,22,font.plainSubstrByWidth(upsStatusText(display.status),160))
        val percent=if(display.capacity>0)100*display.energy/display.capacity else 0.0
        drawString(8,34,tr("Charge: %1$% / %2$ Wh",format(percent),format(display.capacity/3600)))
        drawString(8,46,tr("Batteries: %1$/48 (%2$ active)",display.installed,display.active))
        drawString(8,58,tr("In: %1$ / %2$",Utils.plotVolt("",display.inputVolts).trim(),Utils.plotPower("",display.inputWatts).trim()))
        drawString(8,70,tr("Out: %1$ / %2$",Utils.plotVolt("",display.outputVolts).trim(),Utils.plotPower("",display.outputWatts).trim()))
        drawString(8,82,if(display.outputWatts>1)tr("Backup: about %1$ min",format(display.energy*UpsPowerUnit.EFFICIENCY/display.outputWatts/60)) else tr("Backup: no load"))
    }
    private fun format(value: Double)=String.format(Locale.ROOT,"%.1f",value)
}
