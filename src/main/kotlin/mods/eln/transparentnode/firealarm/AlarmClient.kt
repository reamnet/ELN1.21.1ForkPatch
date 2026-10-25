package mods.eln.transparentnode.firealarm

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sound.LoopedSound
import mods.eln.sound.SoundCommand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import java.io.DataInputStream
import java.util.Locale

fun alarmStatus(powered: Boolean,alarm: Boolean,source: Boolean,trouble: Boolean)=when {
    !powered->tr("No power");alarm || source->tr("Alarm");trouble->tr("Trouble");else->tr("Normal")
}
class AlarmDescriptor(val kind: AlarmKind): TransparentNodeDescriptor(kind.title,AlarmElement::class.java,AlarmRender::class.java) {
    private val model by lazy { Eln.obj.getObj(kind.model) }
    override val frontType get()=TransparentNode.FrontType.BlockSide
    override fun mustHaveFloor()=false
    init { creativeTab=Eln.creativeTabPowerElectronics }
    fun draw(front: Direction,powered: Boolean=false,alarm: Boolean=false,trouble: Boolean=false,pulled: Boolean=false,flash: Boolean=false)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            part("Base",.25f,.27f,.28f)
            if(kind.sensor)part("Case",.88f,.88f,.84f) else part("Case",.64f,.045f,.035f)
            part("Vents",.10f,.11f,.12f);part("Label",.91f,.91f,.85f);part("Screen",.04f,.09f,.10f)
            preserveMatrix { if(pulled)GL11.glRotated(-22.0,0.0,0.0,1.0);part("Handle",.88f,.88f,.82f) }
            if(powered)UtilsClient.disableLight()
            part("LED",if(alarm).95f else if(trouble).90f else .06f,if(alarm).04f else if(trouble).55f else if(powered).78f else .12f,.04f)
            if(powered)UtilsClient.enableLight()
            if(flash)UtilsClient.disableLight()
            part("Flash",if(flash)1f else .54f,if(flash)1f else .57f,if(flash)1f else .58f)
            if(flash)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix { GL11.glTranslated(.25,0.0,0.0);draw(Direction.XN) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Shared channel within 128 m, or use ELN signal wires"))
        if(kind==AlarmKind.PANEL) {
            list.add(tr("120 V panel with 24 Wh rechargeable backup; starts empty"))
            list.add(tr("Powers linked devices; latching alarm, silence, reset and test controls"))
            list.add(tr("Side power terminals; outer face: left trigger, right alarm, top trouble, bottom reset"))
        } else {
            list.add(tr("Powered by its linked panel, or an optional wired 24 V supply"))
            list.add(tr("Outer face: left signal input, right signal output; side terminals accept 24 V"))
            if(kind==AlarmKind.PULL)list.add(tr("Right-click empty-handed to pull; sneak-right-click for setup and reset"))
            if(kind==AlarmKind.SMOKE)list.add(tr("Detects fire and lit campfire smoke through connected air, not solid walls"))
            if(kind==AlarmKind.HEAT)list.add(tr("Adjustable heat threshold; detects nearby radiant heat and room temperature"))
            if(kind.audible)list.add(tr("Panel silence stops sound; strobes remain active until reset"))
        }
    }
    override fun addCollisionBoxesToList(query: AABB,list: MutableList<AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {}
}
class AlarmRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val kind=(descriptor as AlarmDescriptor).kind
    var channel="Fire Alarm";var label="";var enabled=true
    var powered=false;var alarm=false;var silenced=false;var trouble=false;var pulled=false;var detected=false
    var temperature=20.0;var threshold=60.0;var range=6;var members=0
    var volts=0.0;var watts=0.0;var energy=0.0;var sources=""
    private var chirp=0f
    init {
        if(kind.audible)addLoopedSound(object: LoopedSound(if(kind==AlarmKind.BELL)"eln:fire_alarm_bell" else "eln:fire_alarm_horn",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
            override fun getVolume()=if(powered && alarm && !silenced).65f else 0f
        })
    }
    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        if(kind==AlarmKind.PANEL && powered && trouble && !alarm) {
            chirp-=deltaT;if(chirp<=0) { chirp=30f;play(SoundCommand("eln:fire_alarm_trouble").smallRange().mulVolume(.18)) }
        } else chirp=0f
    }
    override fun draw()=mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("LED","Flash","Handle")) {
        val flash=kind.flashing && alarm && (tileEntity.level?.gameTime ?: 0L)%20<4
        (transparentNodeDescriptor as AlarmDescriptor).draw(front ?: Direction.XN,powered,alarm || detected,trouble,pulled,flash)
    }
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);channel=input.readUTF();label=input.readUTF();enabled=input.readBoolean()
        powered=input.readBoolean();alarm=input.readBoolean();silenced=input.readBoolean();trouble=input.readBoolean();pulled=input.readBoolean();detected=input.readBoolean()
        temperature=input.readDouble();threshold=input.readDouble();range=input.readUnsignedByte();members=input.readInt()
        volts=input.readDouble();watts=input.readDouble();energy=input.readDouble();sources=input.readUTF()
    }
    override fun newGuiDraw(side: Direction,player: Player)=AlarmGui(player,this)
}
class AlarmGui(player: Player,private val render: AlarmRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var channel: GuiTextFieldEln;private lateinit var label: GuiTextFieldEln
    private lateinit var save: GuiButtonEln;private lateinit var enable: GuiButtonEln
    private var reset: GuiButtonEln?=null;private var silence: GuiButtonEln?=null;private var test: GuiButtonEln?=null;private var missing: GuiButtonEln?=null
    private var threshold: GuiTextFieldEln?=null;private var applyHeat: GuiButtonEln?=null;private var range: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,300,382,8,300)
    override fun initGui() {
        super.initGui();channel=newGuiTextField(8,43,180).apply { text=render.channel };save=newGuiButton(198,39,94,tr("Save names"))
        label=newGuiTextField(8,82,180).apply { text=render.label };enable=newGuiButton(198,78,94,"")
        if(render.kind==AlarmKind.PANEL) {
            silence=newGuiButton(8,112,138,tr("Silence / resound"));reset=newGuiButton(154,112,138,tr("Reset alarm"))
            test=newGuiButton(8,138,138,tr("Test for 10 s"));missing=newGuiButton(154,138,138,tr("Forget missing"))
        } else if(render.kind==AlarmKind.PULL)reset=newGuiButton(8,112,284,tr("Pull / reset station"))
        if(render.kind.sensor) {
            range=newGuiButton(8,112,284,"")
            if(render.kind==AlarmKind.HEAT) {
                threshold=newGuiTextField(8,144,100).apply { text=render.threshold.toInt().toString() };applyHeat=newGuiButton(118,140,174,tr("Set heat threshold"))
            }
        }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            save->{ render.clientSendString(80,channel.text);render.clientSendString(81,label.text) }
            enable->render.clientSendBoolean(88,!render.enabled)
            silence->render.clientSendId(84)
            reset->render.clientSendId(if(render.kind==AlarmKind.PANEL)85 else 83)
            test->render.clientSendId(86)
            missing->render.clientSendId(87)
            range->render.clientSendInt(89,render.range%8+1)
            applyHeat->threshold?.text?.toDoubleOrNull()?.let { render.clientSendDouble(82,it) }
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,font.plainSubstrByWidth(tr(render.kind.title),284));drawString(8,27,tr("Channel (blank for wired-only)"));drawString(8,66,tr("Zone / device label"))
        enable.displayString=if(render.enabled)tr("Enabled") else tr("Disabled")
        range?.displayString=tr("Detection radius: %1$ m",render.range)
        drawString(8,176,alarmStatus(render.powered,render.alarm,render.pulled || render.detected,render.trouble))
        drawString(8,192,tr("Supply: %1$; draw: %2$",Utils.plotVolt("",render.volts).trim(),Utils.plotPower("",render.watts).trim()))
        if(render.kind==AlarmKind.PANEL) {
            drawString(8,210,tr("Backup: %1$ Wh; linked devices: %2$",String.format(Locale.ROOT,"%.1f",render.energy/3600),render.members))
            drawString(8,228,font.plainSubstrByWidth(tr("Active: %1$",render.sources.ifBlank { tr("None") }),284))
            drawString(8,246,tr("Clear active detectors and pulled stations before reset"))
        } else if(render.kind==AlarmKind.HEAT)drawString(8,210,tr("Heat: %1$ C; threshold: %2$ C",render.temperature.toInt(),render.threshold.toInt()))
        else drawString(8,210,tr("Channel power from panel, or wired 24 V"))
    }
}
