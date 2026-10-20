package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.gridnode.GridRender
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.BasicContainer
import mods.eln.misc.Direction
import mods.eln.misc.LRDUMask
import mods.eln.misc.Utils
import mods.eln.node.transparent.*
import mods.eln.transparentnode.converterStateText
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale
import net.minecraft.client.gui.screens.Screen

class StepDownContainer(player: Player, inventory: Container, pole: Boolean) : BasicContainer(player, inventory,
    if (pole) arrayOf(object : SlotWithSkinAndComment(inventory, 0, 9, 31, ISlotSkin.SlotSkin.medium,
        arrayOf(tr("Pole Transformer or Mains Step-down Unit"))) {
        override fun mayPlace(stack: ItemStack) = isStepDownUnit(stack)
        override fun getMaxStackSize() = 1
    }) else emptyArray<Slot>())

class StepDownDisplay {
    val pole=mods.eln.utility.LegacyPoleDisplay()
    var rating=10_000
    var voltage = 120
    var enabled = true
    var lampEnabled = true
    var automatic = false
    var installed = false
    var light = 0
    var heightStep = 0
    var yaw = 0
    var inputVolts = 0.0
    var outputVolts = 0.0
    var watts = 0.0
    var status = "IDLE"
    val connections = LRDUMask()
    fun read(stream: DataInputStream) {
        rating=stream.readInt();voltage = stream.readInt(); enabled = stream.readBoolean(); lampEnabled = stream.readBoolean(); automatic = stream.readBoolean()
        installed = stream.readBoolean(); light = stream.readUnsignedByte()
        heightStep = stream.readByte().toInt().coerceIn(StreetLightMount.MIN_STEP, StreetLightMount.MAX_STEP)
        yaw = stream.readUnsignedShort().takeIf(StreetLightMount::validYaw) ?: 0
        inputVolts = stream.readDouble(); outputVolts = stream.readDouble(); watts = stream.readDouble()
        status = stream.readUTF()
        connections.deserialize(stream)
    }
}

class StepDownUnitRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) :
    TransparentNodeElementRender(entity, descriptor) {
    override val inventory = TransparentNodeElementInventory(0, 1, this)
    val display = StepDownDisplay()
    private val primaryMask = LRDUMask()
    private val secondaryMask = LRDUMask()
    private var cable: CableRenderType? = null
    override fun draw() {
        (transparentNodeDescriptor as StepDownUnitDescriptor).draw(front ?: Direction.XN)
        // Faces follow the same left-input/right-output convention as variable converters.
        primaryMask.set(0); secondaryMask.set(0)
        val primary = front!!.down().getLRDUGoingTo(front!!.left())!!
        val secondary = front!!.down().getLRDUGoingTo(front!!.right())!!
        primaryMask[primary] = display.connections[primary]
        secondaryMask[secondary] = display.connections[secondary]
        cable = drawCable(front!!.down(), Eln.instance.stdCableRender3200V, primaryMask, cable)
        cable = drawCable(front!!.down(), Eln.instance.stdCableRender200V, secondaryMask, cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream); display.read(stream) }
    override fun newGuiDraw(side: Direction, player: Player) = StepDownGui(player, inventory, this, display, false)
}

class UtilityStreetLightRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : GridRender(entity, descriptor) {
    override val inventory = TransparentNodeElementInventory(1, 1, this)
    val display = StepDownDisplay()
    private var cable: CableRenderType? = null
    override fun draw() {
        super.draw()
        mods.eln.utility.PoleFuseVisual.draw(front ?: Direction.XN,display.pole.fuseClosed,display.pole.fuseBlown)
        mods.eln.utility.PoleSecondaryVisual.draw(front ?: Direction.XN)
        (transparentNodeDescriptor as UtilityStreetLightDescriptor).drawLamp(front ?: Direction.XN, display.light > 0, display.installed, display.heightStep, display.yaw)
        cable = drawCable(front!!.down(), Eln.instance.stdCableRender200V, display.connections, cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream); display.read(stream);display.pole.read(stream) }
    override fun newGuiDraw(side: Direction, player: Player) = StepDownGui(player, inventory, this, display, true)
}

class StepDownGui(player: Player, inventory: Container, private val render: TransparentNodeElementRender,
    private val display: StepDownDisplay, private val pole: Boolean) : GuiContainerEln(StepDownContainer(player, inventory, pole)) {
    private var terminal: GuiButtonEln?=null
    private var fuse: GuiButtonEln?=null
    private lateinit var rating: GuiButtonEln
    private lateinit var voltage: GuiButtonEln
    private lateinit var enabled: GuiButtonEln
    private lateinit var lamp: GuiButtonEln
    private lateinit var automatic: GuiButtonEln
    private lateinit var heightDown: GuiButtonEln
    private lateinit var heightUp: GuiButtonEln
    private lateinit var angleLeft: GuiButtonEln
    private lateinit var angleRight: GuiButtonEln
    override fun newHelper() = GuiHelperContainer(this, if(pole)332 else 176, if (pole) 280 else 232, 8, if (pole) 198 else 150)
    override fun initGui() {
        super.initGui()
        rating=newGuiButton(8,if(pole)156 else 108,160,"")
        if(pole) {
            terminal=newGuiButton(180,60,144,"")
            fuse=newGuiButton(180,84,144,"").apply { setComment(0,tr("Right-click the pole holding a Pole Fuse Link to replace a blown fuse")) }
        }
        voltage = newGuiButton(8, 60, 102, "").apply { if(pole)setComment(0,tr("Nominal output at 12.47 kV; follows grid voltage proportionally")) }
        enabled = newGuiButton(116, 60, 52, "")
        lamp = newGuiButton(8, 84, 75, "").apply { visible = pole }
        automatic = newGuiButton(87, 84, 81, "").apply { visible = pole }
        heightDown = newGuiButton(8, 108, 36, tr("Down")).apply { visible = pole }
        heightUp = newGuiButton(132, 108, 36, tr("Up")).apply { visible = pole }
        angleLeft = newGuiButton(8, 132, 36, tr("Left")).apply {
            visible = pole
            setComment(0, tr("Turn left by %1$°; hold Shift for %2$° steps", 5, 1))
        }
        angleRight = newGuiButton(132, 132, 36, tr("Right")).apply {
            visible = pole
            setComment(0, tr("Turn right by %1$°; hold Shift for %2$° steps", 5, 1))
        }
        refresh()
    }
    private fun refresh() {
        terminal?.displayString=tr("Cable: %1$",mods.eln.utility.ServiceTerminals.label(display.pole.terminal))
        fuse?.apply { displayString=if(display.pole.fuseBlown)tr("Fuse blown") else if(display.pole.fuseClosed)tr("Open fuse") else tr("Close fuse");enabled=!display.pole.fuseBlown }
        rating.enabled=!pole
        rating.displayString=tr("Transformer: %1$ kW",display.rating/1000)
        voltage.displayString = if(pole)tr("Nominal: %1$ V",display.voltage) else tr("Output: %1$ V",display.voltage)
        enabled.displayString = if (display.enabled) tr("On") else tr("Off")
        automatic.displayString = if (display.automatic) tr("Dusk: Auto") else tr("Dusk: Manual")
        lamp.displayString = if (display.lampEnabled) tr("Lamp: On") else tr("Lamp: Off")
        heightDown.enabled = display.heightStep > StreetLightMount.MIN_STEP
        heightUp.enabled = display.heightStep < StreetLightMount.MAX_STEP
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when (obj) {
            terminal -> { val ts=listOf(32,50,40,41,42);render.clientSendInt(87,ts[(ts.indexOf(display.pole.terminal)+1)%ts.size]) }
            fuse -> render.clientSendInt(89,0)
            rating -> render.clientSendInt(86,MainsStepDown.RATINGS[(MainsStepDown.RATINGS.indexOf(display.rating)+1)%MainsStepDown.RATINGS.size])
            voltage -> render.clientSendInt(80, if (display.voltage == 120) 240 else 120)
            enabled -> render.clientSendBoolean(81, !display.enabled)
            lamp -> render.clientSendBoolean(82, !display.lampEnabled)
            automatic -> render.clientSendBoolean(83, !display.automatic)
            heightDown -> render.clientSendInt(84, -1)
            heightUp -> render.clientSendInt(84, 1)
            angleLeft -> render.clientSendInt(85, if (Screen.hasShiftDown()) 1 else 5)
            angleRight -> render.clientSendInt(85, if (Screen.hasShiftDown()) -1 else -5)
        }
    }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y); refresh()
        drawString(8, 8, if (pole) tr("Utility Pole Street Light") else tr("Mains Step-down Unit"))
        if(pole) {
            drawString(180,113,tr("Secondary service rack"))
            drawString(180,127,tr("L1: 120 V; L2: 120 V"))
            drawString(180,141,tr("240 V service circuit"))
            drawString(180,155,tr("12.47 kV fixed ratio"))
            drawString(180,169,tr("Input: %1$",Utils.plotVolt("",display.inputVolts).trim()))
            drawString(180,183,tr("Output: %1$",Utils.plotVolt("",display.outputVolts).trim()))
        }
        if (pole) drawString(32, 35, if (display.installed) tr("Unit installed") else tr("Insert step-down unit"))
        else {
            drawString(8, 30, tr("Input: %1$", Utils.plotVolt("", display.inputVolts).trim()))
            drawString(8, 43, tr("Output: %1$", Utils.plotVolt("", display.outputVolts).trim()))
        }
        if (pole) {
            val height = tr("Height: %1$ m", String.format(Locale.ROOT, "%.2f", StreetLightMount.height(display.heightStep)))
            drawString(88 - font.width(height) / 2, 114, height)
            val angle = tr("Angle: %1$°", display.yaw)
            drawString(88 - font.width(angle) / 2, 138, angle)
        }
        drawString(8, if (pole) 180 else 132, font.plainSubstrByWidth(if (pole && !display.installed) tr("Step-down unit required") else converterStateText(display.status), 160))
    }
}
