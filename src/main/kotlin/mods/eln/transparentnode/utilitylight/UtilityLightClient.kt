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

class StepDownContainer(player: Player, inventory: Container, pole: Boolean) : BasicContainer(player, inventory,
    if (pole) arrayOf(object : SlotWithSkinAndComment(inventory, 0, 9, 31, ISlotSkin.SlotSkin.medium,
        arrayOf(tr("Mains Step-down Unit"))) {
        override fun mayPlace(stack: ItemStack) = isStepDownUnit(stack)
        override fun getMaxStackSize() = 1
    }) else emptyArray<Slot>())

class StepDownDisplay {
    var voltage = 120
    var enabled = true
    var lampEnabled = true
    var automatic = false
    var installed = false
    var light = 0
    var heightStep = 0
    var inputVolts = 0.0
    var outputVolts = 0.0
    var watts = 0.0
    var status = "IDLE"
    val connections = LRDUMask()
    fun read(stream: DataInputStream) {
        voltage = stream.readInt(); enabled = stream.readBoolean(); lampEnabled = stream.readBoolean(); automatic = stream.readBoolean()
        installed = stream.readBoolean(); light = stream.readUnsignedByte()
        heightStep = stream.readByte().toInt().coerceIn(StreetLightMount.MIN_STEP, StreetLightMount.MAX_STEP)
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
        (transparentNodeDescriptor as UtilityStreetLightDescriptor).drawLamp(front ?: Direction.XN, display.light > 0, display.installed, display.heightStep)
        cable = drawCable(front!!.down(), Eln.instance.stdCableRender200V, display.connections, cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream); display.read(stream) }
    override fun newGuiDraw(side: Direction, player: Player) = StepDownGui(player, inventory, this, display, true)
}

class StepDownGui(player: Player, inventory: Container, private val render: TransparentNodeElementRender,
    private val display: StepDownDisplay, private val pole: Boolean) : GuiContainerEln(StepDownContainer(player, inventory, pole)) {
    private lateinit var voltage: GuiButtonEln
    private lateinit var enabled: GuiButtonEln
    private lateinit var lamp: GuiButtonEln
    private lateinit var automatic: GuiButtonEln
    private lateinit var heightDown: GuiButtonEln
    private lateinit var heightUp: GuiButtonEln
    override fun newHelper() = GuiHelperContainer(this, 176, if (pole) 232 else 208, 8, if (pole) 150 else 126)
    override fun initGui() {
        super.initGui()
        voltage = newGuiButton(8, 60, 102, "")
        enabled = newGuiButton(116, 60, 52, "")
        lamp = newGuiButton(8, 84, 75, "").apply { visible = pole }
        automatic = newGuiButton(87, 84, 81, "").apply { visible = pole }
        heightDown = newGuiButton(8, 108, 36, tr("Down")).apply { visible = pole }
        heightUp = newGuiButton(132, 108, 36, tr("Up")).apply { visible = pole }
        refresh()
    }
    private fun refresh() {
        voltage.displayString = tr("Output: %1$ V", display.voltage)
        enabled.displayString = if (display.enabled) tr("On") else tr("Off")
        automatic.displayString = if (display.automatic) tr("Dusk: Auto") else tr("Dusk: Manual")
        lamp.displayString = if (display.lampEnabled) tr("Lamp: On") else tr("Lamp: Off")
        heightDown.enabled = display.heightStep > StreetLightMount.MIN_STEP
        heightUp.enabled = display.heightStep < StreetLightMount.MAX_STEP
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when (obj) {
            voltage -> render.clientSendInt(80, if (display.voltage == 120) 240 else 120)
            enabled -> render.clientSendBoolean(81, !display.enabled)
            lamp -> render.clientSendBoolean(82, !display.lampEnabled)
            automatic -> render.clientSendBoolean(83, !display.automatic)
            heightDown -> render.clientSendInt(84, -1)
            heightUp -> render.clientSendInt(84, 1)
        }
    }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y); refresh()
        drawString(8, 8, if (pole) tr("Utility Pole Street Light") else tr("Mains Step-down Unit"))
        if (pole) drawString(32, 35, if (display.installed) tr("Unit installed") else tr("Insert step-down unit"))
        else {
            drawString(8, 30, tr("Input: %1$", Utils.plotVolt("", display.inputVolts).trim()))
            drawString(8, 43, tr("Output: %1$", Utils.plotVolt("", display.outputVolts).trim()))
        }
        if (pole) {
            val height = tr("Height: %1$ m", String.format(Locale.ROOT, "%.2f", StreetLightMount.height(display.heightStep)))
            drawString(88 - font.width(height) / 2, 114, height)
        }
        drawString(8, if (pole) 132 else 108, font.plainSubstrByWidth(if (pole && !display.installed) tr("Step-down unit required") else converterStateText(display.status), 160))
    }
}
