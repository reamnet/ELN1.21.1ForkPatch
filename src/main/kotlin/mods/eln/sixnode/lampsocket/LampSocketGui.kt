package mods.eln.sixnode.lampsocket

import mods.eln.gui.*
import mods.eln.i18n.I18N
import net.minecraft.world.entity.player.Player
import mods.eln.misc.isNothing

class LampSocketGui(player: Player, val render: LampSocketRender) :
    GuiContainerEln(LampSocketContainer(player, render.inventory, render.descriptor)) {

    companion object {
        const val TOGGLE_GROUNDED_EVENT: Byte = 0
        const val TOGGLE_POWER_SOURCE_EVENT: Byte = 1
        const val UPDATE_LAMP_SUPPLY_CHANNEL_EVENT: Byte = 2
        const val ADJUST_ROTATION_ANGLE_EVENT: Byte = 3
        const val MOTION_MODE_EVENT: Byte = 4

        const val MIN_ROTATION_ANGLE = -90.0
        const val MAX_ROTATION_ANGLE = 90.0
    }

    private var motionButton: GuiButtonEln? = null
    private var fanButton: GuiButtonEln?=null
    private var aiming: LightAimControls?=null
    private var raise: GuiButtonEln?=null
    private var lower: GuiButtonEln?=null
    private var dropLabel: GuiButtonEln?=null
    private lateinit var buttonGrounded: GuiButtonEln
    private lateinit var buttonPowerSource: GuiButtonEln
    private lateinit var textboxLampSupplyChannel: GuiTextFieldEln
    private lateinit var trackbarRotationAngle: GuiVerticalTrackBar

    override fun newHelper(): GuiHelperContainer {
        return if(render.descriptor.fixture?.hangingWarehouse==true)GuiHelperContainer(this,176,198,8,116) else if(render.aim.heads>0)GuiHelperContainer(this,176,242,8,160) else HelperStdContainer(this)
    }

    override fun initGui() {
        super.initGui()
        if(render.descriptor.fixture?.hangingWarehouse==true) {
            raise=newGuiButton(8,82,28,"-").apply { setComment(0,I18N.tr("Raise warehouse light")) }
            dropLabel=newGuiButton(40,82,92,"").apply { enabled=false }
            lower=newGuiButton(136,82,32,"+").apply { setComment(0,I18N.tr("Lower warehouse light")) }
        }
        if(render.aim.heads>0)aiming=LightAimControls(this,82,render.aim) { head,axis,value ->
            render.clientSetDouble((32+head*2+axis).toByte(),value)
        }
        if(render.descriptor.fixture?.fan==true)fanButton=newGuiButton(40,57,96,"")

        buttonPowerSource = newGuiButton(16, 7, 144, "")
        buttonPowerSource.enabled = !render.descriptor.wirelessOnly
        if (render.descriptor.wirelessOnly) buttonPowerSource.setComment(0,I18N.tr("This light uses a wireless lamp supply or appliance cord"))

        buttonGrounded = newGuiButton(16, 33, 144, "")
        buttonGrounded.setComment(0, "Toggle the internal ground connection")

        // TODO: Remove the following two lines when grounding is implemented
        buttonGrounded.setComment(1, "§4" + I18N.tr("This currently does nothing."))
        buttonGrounded.enabled = false

        textboxLampSupplyChannel = newGuiTextField(16+1, 36+1, 144-2)
        textboxLampSupplyChannel.setComment(0, I18N.tr("Specify the lamp supply channel"))
        textboxLampSupplyChannel.setText(render.lampSupplyChannel)

        trackbarRotationAngle = newGuiHorizontalTrackBar(43,61,90, 14)
        trackbarRotationAngle.setRange(MIN_ROTATION_ANGLE.toFloat(), MAX_ROTATION_ANGLE.toFloat())
        trackbarRotationAngle.setStepIdMax((MAX_ROTATION_ANGLE - MIN_ROTATION_ANGLE).toInt())
        trackbarRotationAngle.value = render.projectionRotationAngle.toFloat()
        trackbarRotationAngle.visible = render.descriptor.enableProjectionRotation
        if(render.descriptor.fixture == DecorativeLampStyle.MOTION) {
            motionButton = newGuiButton(40,57,96,"").apply {
                setComment(0,I18N.tr("Detects living entities within 8 blocks in front of the light"))
                setComment(1,I18N.tr("Stays on for 30 seconds after movement; walls block detection"))
            }
        }
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        aiming?.refresh()
        dropLabel?.displayString=I18N.tr("Drop: %1$ m",render.suspension.drop)
        raise?.enabled=render.suspension.drop>0.0
        lower?.enabled=render.suspension.drop<7.0
        fanButton?.displayString=if(render.fanEnabled)I18N.tr("Fan: On") else I18N.tr("Fan: Off")

        if (render.grounded) buttonGrounded.displayString = I18N.tr("Grounded")
        else buttonGrounded.displayString = I18N.tr("Ungrounded")

        buttonPowerSource.enabled=render.cord.endpoint==null && !render.descriptor.wirelessOnly
        if(render.cord.endpoint!=null) {
            textboxLampSupplyChannel.visible=false
            buttonGrounded.visible=false
            buttonPowerSource.displayString=I18N.tr("Powered by cord")
            buttonPowerSource.setComment(0,mods.eln.transparentnode.cord.cordState(render.cord.state))
        } else if (render.poweredByLampSupply) {
            textboxLampSupplyChannel.visible = true
            buttonGrounded.visible = false
            buttonPowerSource.displayString = I18N.tr("Powered by lamp supply")

            val lampStack = render.inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)
            val cableStack = render.inventory.getItem(LampSocketContainer.CABLE_SLOT_ID)

            when {
                cableStack.isNothing() -> textboxLampSupplyChannel.setComment(1, "§4" + I18N.tr("Cable slot empty"))
                lampStack.isNothing() -> textboxLampSupplyChannel.setComment(1, "§4" + I18N.tr("Lamp slot empty"))
                render.activeLampSupplyConnection -> textboxLampSupplyChannel.setComment(1, "§2" + I18N.tr("Connected to %1$", render.lampSupplyChannel))
                else -> textboxLampSupplyChannel.setComment(1, "§4" + I18N.tr("%1$ is not in range!", render.lampSupplyChannel))
            }
        } else {
            textboxLampSupplyChannel.visible = false
            buttonGrounded.visible = true
            buttonPowerSource.displayString = I18N.tr("Powered by cable")
        }

        motionButton?.displayString = when(render.motionMode) {
            0 -> I18N.tr("Motion: night")
            1 -> I18N.tr("Motion: all day")
            2 -> I18N.tr("Always on")
            else -> I18N.tr("Always off")
        }
        trackbarRotationAngle.setComment(0, I18N.tr("Beam orientation: %1$\u00B0", trackbarRotationAngle.value.toInt()))
    }

    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        aiming?.event(obj)

        when (obj) {
            raise -> render.clientSetDouble(10,render.suspension.drop-.25)
            lower -> render.clientSetDouble(10,render.suspension.drop+.25)
            fanButton -> render.clientSend(9)
            motionButton -> render.clientSend(MOTION_MODE_EVENT.toInt())
            buttonGrounded -> render.clientSend(TOGGLE_GROUNDED_EVENT.toInt())
            buttonPowerSource -> render.clientSend(TOGGLE_POWER_SOURCE_EVENT.toInt())
            textboxLampSupplyChannel -> render.clientSetString(UPDATE_LAMP_SUPPLY_CHANNEL_EVENT, textboxLampSupplyChannel.text)
            trackbarRotationAngle -> render.clientSetDouble(ADJUST_ROTATION_ANGLE_EVENT, trackbarRotationAngle.value.toDouble())
        }
    }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f,x,y)
        if (render.descriptor.fixture != null && motionButton == null && fanButton==null) drawString(46,65,I18N.tr("Bulbs: %1$/%2$",render.bulbCount,render.descriptor.bulbCapacity))
    }
}
