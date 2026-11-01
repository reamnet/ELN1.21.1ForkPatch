package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.power.ControlPanelDisplay
import mods.eln.client.power.PowerPanelDisplay
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

fun shedStatusText(status: String)=when(status) {
    "DISABLED"->tr("All outputs open: disabled or control signal")
    "WAIT_POWER"->tr("Waiting for input power")
    "INVALID"->tr("Invalid readings; outputs open")
    "UNDER_VOLTAGE"->tr("Low voltage: shedding lowest priority")
    "OVER_BUDGET"->tr("Over budget: shedding lowest priority")
    "RESTORING"->tr("Healthy: waiting to restore next priority")
    "HEADROOM"->tr("Next load exceeds spare power budget")
    "HOLD"->tr("Waiting for voltage and 20% spare budget")
    else->tr("All priority outputs connected")
}

class IndustrialDescriptor(val kind: IndustrialKind): TransparentNodeDescriptor(kind.title,IndustrialElement::class.java,IndustrialRender::class.java) {
    private val model by lazy { Eln.obj.getObj(kind.model) }
    init { creativeTab=if(kind==IndustrialKind.WELDER || kind==IndustrialKind.LOAD_BANK)Eln.creativeTabMachines else Eln.creativeTabPowerElectronics }
    override fun mustHaveFloor()=false
    fun draw(front: Direction=Direction.ZP,on: Boolean=false,fault: Boolean=false,open: Boolean=false,volts: Double?=null,display: IndustrialRender?=null)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            p("Base",.18f,.20f,.22f);p("Case",if(kind==IndustrialKind.PAD).18f else .58f,if(kind==IndustrialKind.PAD).37f else .62f,if(kind==IndustrialKind.PAD).23f else .65f)
            if(kind==IndustrialKind.PAD) { p("Pad",.42f,.44f,.41f);p("Lugs",.65f,.48f,.25f);p("Elbows",.10f,.12f,.13f);p("Ground",.35f,.49f,.32f) }
            p("Trim",.12f,.14f,.16f);p("Screen",.025f,.10f,.12f);p("Input",.13f,.38f,.8f);p("Output",.96f,.48f,.07f);p("Terminal",.66f,.46f,.20f)
            p("Ceramic",.67f,.53f,.39f);p("Red",.74f,.06f,.04f)
            preserveMatrix {
                if(open) { GL11.glTranslated(-.40,0.0,.37);GL11.glRotated(-100.0,0.0,1.0,0.0);GL11.glTranslated(.40,0.0,-.37) }
                p("Door",.17f,.35f,.22f);p("DoorHardware",.12f,.14f,.16f);p("Nameplate",.75f,.78f,.71f);p("Warning",.95f,.65f,.08f);p("WarningMark",.05f,.06f,.04f)
            }
            if(on || fault)UtilsClient.disableLight()
            p("Indicator",if(fault).95f else .06f,if(on && !fault).8f else .14f,.06f)
            if(on || fault)UtilsClient.enableLight()
            if(kind==IndustrialKind.RELAY || kind==IndustrialKind.SHEDDER) {
                ControlPanelDisplay.body(model)
                val powered=volts!=null && volts>1
                if(display!=null) {
                    PowerPanelDisplay.equipmentNumber(model,display.stats[0],powered,"Readout0")
                    PowerPanelDisplay.equipmentNumber(model,display.stats[if(kind==IndustrialKind.SHEDDER)1 else 3],powered,"Readout1")
                }
                for(i in 0..3) {
                    val active=powered && display!=null && if(kind==IndustrialKind.SHEDDER)display.closed[i] else
                        (if(display.trip)display.stats[10].toInt() else display.pendingReason)==when(i) { 0,1->1;2->2;else->3 } &&
                        (i>1 || if(i==0)display.stats[0]<display.settings[0]*display.settings[2] else display.stats[0]>display.settings[0]*display.settings[3])
                    PowerPanelDisplay.lamp(model,"ChannelLed$i",active,if(kind==IndustrialKind.SHEDDER).15f else 1f,if(kind==IndustrialKind.SHEDDER).9f else .2f,.08f)
                    if(display!=null && powered)ControlPanelDisplay.bar(model,"BranchTrack$i",if(kind==IndustrialKind.SHEDDER)
                        (if(display.closed[i])display.branchWatts[i] else display.nominalDemand[i])/display.settings[1]
                        else if(active)1.0 else 0.0,if(kind==IndustrialKind.SHEDDER && display.closed[i]).2f else 1f,.65f,.25f)
                }
            } else if(volts!=null)PowerPanelDisplay.equipmentNumber(model,volts,volts>1,"Screen")
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw(Direction.ZN)
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        list.add(tr("Blue left or bottom: input; orange right: output"))
        list.add(tr("Front top: trip signal; front bottom: control signal"))
        when(kind) {
            IndustrialKind.LOAD_BANK->list.add(tr("Programmable resistive load, 0-1000 kW; real voltage response and fan cooling"))
            IndustrialKind.SCADA->{ list.add(tr("Eight explicit targets within 256 blocks; loaded chunks only"));list.add(tr("Control smart meters, service disconnects, breakers, reclosers and industrial devices"));list.add(tr("Console supply: 120 V / 20 W; remote closing never resets a trip")) }
            IndustrialKind.RELAY->{ list.add(tr("Line contacts: left/right bottom; return contacts: left/right right"));list.add(tr("Differential protection requires both forward and return conductors; 0 disables it")) }
            IndustrialKind.ARRESTER->{ list.add(tr("Finite-energy shunt protection; 200 kJ cartridge; replace when spent"));list.add(tr("Right-click an overhead distribution pole to install; sneak empty-handed to remove")) }
            IndustrialKind.PAD->{
                list.add(tr("Single-phase underground transformer; install a transformer and physical primary fuse"))
                list.add(tr("Fixed ratio follows primary sag; 120/240 V service shares one transformer rating"))
                list.add(tr("Fuse amperes are primary current; transformer overload trips separately"))
                list.add(tr("Bottom entries: Down primary, Up full secondary, Left L1, Right L2"))
                list.add(tr("Isolate before opening the front cable compartment"))
            }
            IndustrialKind.DRIVE->{ list.add(tr("Ramped 0-60 Hz motor voltage control; passive current-limited output"));list.add(tr("Output impedance limits short-circuit current; loaded voltage falls with current")) }
            IndustrialKind.SHEDDER->{
                list.add(tr("Right terminals: left P1, right P2, down P3, up P4"))
                list.add(tr("P1 is highest priority; shed P4 first, restore P1 first"))
                list.add(tr("Restoration needs healthy voltage, 20% spare budget and room for the next load"))
            }
            IndustrialKind.CHARGER->list.add(tr("Charges removable vehicle and UPS packs using real input energy; 94 percent efficiency"))
            IndustrialKind.WELDER->{ list.add(tr("Left: iron ingot or damaged tool; middle: iron ingot; right: output"));list.add(tr("120 kJ per welded frame or 25 percent durability repair")) }
        }
    }
}
class IndustrialContainer(player: Player,val deviceInventory: Container,kind: IndustrialKind): BasicContainer(player,deviceInventory,Array(kind.slots) { i->object: SlotWithSkinAndComment(deviceInventory,i,8+i*24,if(kind==IndustrialKind.CHARGER)70 else 138,
    ISlotSkin.SlotSkin.medium,arrayOf(when(kind) {
        IndustrialKind.CHARGER->tr("Battery / pack (one)")
        IndustrialKind.PAD->if(i==0)tr("Transformer") else tr("Fuse")
        IndustrialKind.WELDER->when(i) { 0->tr("Workpiece");1->tr("Iron ingot");else->tr("Output") }
        else->tr("Input")
    })) {
    override fun mayPlace(stack: ItemStack)=if(deviceInventory is TransparentNodeElementInventory)deviceInventory.canPlaceItemThroughFace(i,stack,0) else false
    override fun getMaxStackSize()=if(kind==IndustrialKind.CHARGER || kind==IndustrialKind.PAD)1 else 64
} })
class IndustrialRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    val kind=(descriptor as IndustrialDescriptor).kind
    override val inventory=TransparentNodeElementInventory(kind.slots,64,this)
    init {
        if(kind==IndustrialKind.LOAD_BANK)addLoopedSound(object: mods.eln.sound.LoopedSound("eln:appliance_industrial_fan",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
            override fun getVolume()=if(stats[1]>50).25f else 0f
            override fun getPitch()=.8f
        })
    }
    var settings=kind.defaults.copyOf();var enabled=true;var trip=false;var working=false
    val stats=DoubleArray(11)
    var target=0;var telemetry="";val closed=BooleanArray(4)
    var shedStatus="NORMAL";var remaining=0.0;var next=-1
    val branchWatts=DoubleArray(4);val nominalDemand=DoubleArray(4)
    var differential=0.0;var pendingReason=0;var tripElapsed=0.0
    var primaryAmps=0.0;var fuseAmps=0.0;var transformerWatts=0.0


    override fun draw()=mods.eln.client.terrain.TerrainModels.body(this,listOf(front,enabled),if(kind==IndustrialKind.RELAY || kind==IndustrialKind.SHEDDER)ControlPanelDisplay.PARTS else setOf("Indicator")) {
        (transparentNodeDescriptor as IndustrialDescriptor).draw(front ?: Direction.ZP,if(kind==IndustrialKind.RELAY || kind==IndustrialKind.SHEDDER)enabled && stats[0]>1 && !trip else working,trip || kind==IndustrialKind.ARRESTER && stats[9]<=0,!enabled && kind==IndustrialKind.PAD,stats[0],this)
    }
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);settings.indices.forEach { settings[it]=input.readDouble() };enabled=input.readBoolean();trip=input.readBoolean();working=input.readBoolean();stats.indices.forEach { stats[it]=input.readDouble() }
        target=input.readUnsignedByte().coerceIn(0,7);telemetry=input.readUTF();closed.indices.forEach { closed[it]=input.readBoolean() }
        if(kind==IndustrialKind.SHEDDER) {
            shedStatus=input.readUTF();remaining=input.readDouble();next=input.readInt()
            branchWatts.indices.forEach { branchWatts[it]=input.readDouble() };nominalDemand.indices.forEach { nominalDemand[it]=input.readDouble() }
        }
        if(kind==IndustrialKind.RELAY) { differential=input.readDouble();pendingReason=input.readInt();tripElapsed=input.readDouble() }
        if(kind==IndustrialKind.PAD) { primaryAmps=input.readDouble();fuseAmps=input.readDouble();transformerWatts=input.readDouble() }
    }
    override fun newGuiDraw(side: Direction,player: Player)=IndustrialGui(player,this)
}
class IndustrialGui(player: Player,private val render: IndustrialRender): GuiContainerEln(IndustrialContainer(player,render.inventory,render.kind)) {
    private val fields=ArrayList<GuiTextFieldEln>()
    private lateinit var apply: GuiButtonEln;private lateinit var enabled: GuiButtonEln;private lateinit var reset: GuiButtonEln
    private var displayedTarget=render.target
    private var select: GuiButtonEln?=null;private var bind: GuiButtonEln?=null;private var close: GuiButtonEln?=null;private var open: GuiButtonEln?=null
    private val controls get()=render.kind in listOf(IndustrialKind.SHEDDER,IndustrialKind.RELAY,IndustrialKind.PAD)
    override fun newHelper()=GuiHelperContainer(this,344,if(controls)344 else 240,8,if(controls)262 else 158)
    override fun initGui() {
        super.initGui();fields.clear()
        render.settings.forEachIndexed { i,v->fields.add(newGuiTextField(110+(i%2)*168,32+(i/2)*18,56).apply { text=v.toString();setComment(0,render.kind.labels()[i]) }) }
        apply=newGuiButton(8,88,105,tr("Apply settings"));enabled=newGuiButton(119,88,105,"");reset=newGuiButton(230,88,104,if(render.kind==IndustrialKind.SHEDDER)tr("Retry shed loads") else tr("Reset trip"))
        if(render.kind==IndustrialKind.SCADA) {
            select=newGuiButton(8,66,102,"");bind=newGuiButton(116,66,102,tr("Bind address"));open=newGuiButton(224,66,54,tr("Open"));close=newGuiButton(282,66,52,tr("Close"))
        }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            apply->sendSettings()
            enabled->render.clientSendBoolean(81,!render.enabled)
            reset->render.clientSendId(if(render.kind==IndustrialKind.SHEDDER)86 else 82)
            select->render.clientSendInt(84,(render.target+1)%8)
            bind->{ if(sendSettings())render.clientSendId(83) }
            open->render.clientSendBoolean(85,false)
            close->render.clientSendBoolean(85,true)
        }
    }
    private fun sendSettings(): Boolean {
        val p=fields.map { it.text.toDoubleOrNull() ?: return false }.toDoubleArray();if(!render.kind.valid(p))return false
        val bytes=java.io.ByteArrayOutputStream();val out=java.io.DataOutputStream(bytes);render.preparePacketForServer(out);out.writeByte(80);p.forEach(out::writeDouble);render.sendPacketToServer(bytes);return true
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        if(render.target!=displayedTarget) { displayedTarget=render.target;fields.forEachIndexed { i,field->field.text=render.settings[i].toString() } }
        drawString(8,8,font.plainSubstrByWidth(tr(render.kind.title),328))
        drawString(8,22,if(render.trip)tr("Tripped; inspect circuit before reset") else if(!render.enabled)tr("Disabled") else if(render.kind==IndustrialKind.SHEDDER)shedStatusText(render.shedStatus) else if(controls && render.pendingReason!=0)tr("Fault detected; trip delay running") else tr("Enabled"))
        render.kind.labels().forEachIndexed { i,label->drawString(8+(i%2)*168,35+(i/2)*18,font.plainSubstrByWidth(label,98)) }
        enabled.displayString=if(render.enabled)tr("Disable / open") else tr("Enable / close");reset.enabled=if(render.kind==IndustrialKind.SHEDDER)render.enabled && render.closed.any { !it } else render.trip
        if(render.kind!=IndustrialKind.SCADA)drawString(8,110,tr("Input: %1$; %2$",Utils.plotVolt("",render.stats[0]).trim(),Utils.plotPower("",render.stats[1]).trim()))
        if(render.kind!=IndustrialKind.SCADA)drawString(8,122,tr("Output: %1$; %2$",Utils.plotVolt("",render.stats[2]).trim(),Utils.plotAmpere("",render.stats[3]).trim()))
        when(render.kind) {
            IndustrialKind.SCADA->{ select?.displayString=tr("Target %1$",render.target+1);render.telemetry.lines().take(3).forEachIndexed { i,line->drawString(8,110+i*12,font.plainSubstrByWidth(line,328)) } }
            IndustrialKind.RELAY->{
                drawString(8,158,tr("Residual current: %1$; trip timer: %2$ s",Utils.plotAmpere("",render.differential).trim(),"%.1f".format(java.util.Locale.ROOT,render.tripElapsed)))
                drawString(8,174,tr("UV %1$ V / OV %2$ V / OC %3$ A",(render.settings[0]*render.settings[2]).toInt(),(render.settings[0]*render.settings[3]).toInt(),render.settings[1]))
                drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,when(render.stats[10].toInt()) {
                1->tr("Voltage outside protection settings");2->tr("Overcurrent trip");3->tr("Differential current trip");4->tr("Invalid electrical readings");5->tr("Remote signal trip");else->tr("Protection ready")
            })
            }
            IndustrialKind.PAD->{
                drawString(8,158,tr("Primary: %1$; fuse: %2$ A",Utils.plotAmpere("",render.primaryAmps).trim(),render.fuseAmps.toInt()))
                drawString(8,174,tr("Transformer capacity: %1$ kW",(render.transformerWatts/1000).toInt()))
                drawString(8,190,when(render.stats[10].toInt()) {
                    7->tr("Transformer overload trip; primary fuse intact")
                    2->tr("Primary overcurrent: replace the blown fuse")
                    else->tr("Fuse amperes refer to the primary, not the 240 V load")
                })
            }
            IndustrialKind.ARRESTER->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Surge capacity remaining: %1$ kJ",(render.stats[9]/1000).toInt()))
            IndustrialKind.CHARGER->drawString(8,140,tr("Session stored: %1$ kJ",(render.stats[7]/1000).toInt()))
            IndustrialKind.WELDER->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Welding: %1$ / 120 kJ",(render.stats[8]/1000).toInt()))
            IndustrialKind.DRIVE->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Actual frequency: %1$ Hz; case %2$ C",render.stats[5].toInt(),render.stats[4].toInt()))
            IndustrialKind.SHEDDER->{
                drawString(8,138,tr("Right outputs: P1 highest; P4 sheds first."))
                for(i in 0..3)drawString(8,154+i*15,tr("P%1$ (%2$): %3$; %4$",i+1,
                    when(i) { 0->tr("left");1->tr("right");2->tr("down");else->tr("up") },
                    if(render.closed[i])tr("On") else tr("Shed"),Utils.plotPower("",if(render.closed[i])render.branchWatts[i] else render.nominalDemand[i]).trim()))
                if(render.next>=0)drawString(8,218,if(render.remaining>0)tr("Next: P%1$; delay remaining: %2$ s",render.next+1,"%.1f".format(java.util.Locale.ROOT,render.remaining)) else tr("Next priority: P%1$",render.next+1))
                drawString(8,234,tr("Shed load readings are last demand at nominal V."))
                drawString(8,246,tr("Shed below %1$ V; restore above %2$ V",(render.settings[0]*render.settings[4]).toInt(),(render.settings[0]*render.settings[5]).toInt()))
            }
            IndustrialKind.LOAD_BANK->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Case: %1$ C; consumed: %2$ kJ",render.stats[4].toInt(),(render.stats[6]/1000).toInt()))
            else->Unit
        }
        if(render.kind==IndustrialKind.CHARGER)drawString(32,74,tr("Battery / pack"))
        if(render.kind==IndustrialKind.PAD)drawString(64,140,tr("Transformer / fuse"))
    }
}

/** Baked optional pole kit, on the side opposite the transformer can. */
object PoleArresterVisual {
    private val mesh by lazy { Eln.obj.getObj("industrial_arrester") }
    fun draw(angle: Float,phases: Int,spent: Boolean)=preserveMatrix {
        GL11.glRotatef(angle,0f,1f,0f);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            for(i in 0 until phases)preserveMatrix {
                val z=if(phases==1)0.0 else (i-1)*.95
                mods.eln.utility.PoleLeadVisual.draw(net.minecraft.world.phys.Vec3(0.0,if(phases==1 || i==1)7.15 else 6.63,z),net.minecraft.world.phys.Vec3(.46,6.44,z))
                GL11.glTranslated(.46,6.25,z);GL11.glScaled(.45,.45,.45)
                GL11.glColor3f(.30f,.32f,.34f);mesh.getPart("Base")?.drawNoBind();mesh.getPart("Terminal")?.drawNoBind()
                GL11.glColor3f(if(spent).20f else .67f,if(spent).19f else .53f,if(spent).18f else .39f);mesh.getPart("Ceramic")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
}
