package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

class IndustrialDescriptor(val kind: IndustrialKind): TransparentNodeDescriptor(kind.title,IndustrialElement::class.java,IndustrialRender::class.java) {
    private val model by lazy { Eln.obj.getObj(kind.model) }
    init { creativeTab=if(kind==IndustrialKind.WELDER || kind==IndustrialKind.LOAD_BANK || kind==IndustrialKind.JUKEBOX || kind==IndustrialKind.TV)Eln.creativeTabMachines else Eln.creativeTabPowerElectronics }
    override fun mustHaveFloor()=false
    fun draw(front: Direction=Direction.ZP,on: Boolean=false,fault: Boolean=false,open: Boolean=false)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            p("Base",.18f,.20f,.22f);p("Case",if(kind==IndustrialKind.PAD).18f else .58f,if(kind==IndustrialKind.PAD).37f else .62f,if(kind==IndustrialKind.PAD).23f else .65f)
            p("Trim",.12f,.14f,.16f);p("Screen",.025f,.10f,.12f);p("Input",.13f,.38f,.8f);p("Output",.96f,.48f,.07f);p("Terminal",.66f,.46f,.20f)
            p("Ceramic",.67f,.53f,.39f);p("Red",.74f,.06f,.04f)
            preserveMatrix { if(open) { GL11.glTranslated(-.40,0.0,.37);GL11.glRotated(-100.0,0.0,1.0,0.0);GL11.glTranslated(.40,0.0,-.37) };p("Door",.17f,.35f,.22f) }
            if(on || fault)UtilsClient.disableLight()
            p("Indicator",if(fault).95f else .06f,if(on && !fault).8f else .14f,.06f)
            if(on || fault)UtilsClient.enableLight()
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
            IndustrialKind.PAD->list.add(tr("Install a pole transformer and fuse; fixed ratio follows primary sag"))
            IndustrialKind.DRIVE->{ list.add(tr("Ramped 0-60 Hz motor voltage control; passive current-limited output"));list.add(tr("Output impedance limits short-circuit current; loaded voltage falls with current")) }
            IndustrialKind.SHEDDER->list.add(tr("Right terminals: left priority 1, right 2, down 3, up 4; restore every five healthy seconds"))
            IndustrialKind.CHARGER->list.add(tr("Charges removable vehicle and UPS packs using real input energy; 94 percent efficiency"))
            IndustrialKind.JUKEBOX->{ list.add(tr("120/240 V; 25 W playing, 2 W standby; cords or fixed cables"));list.add(tr("Direct HTTPS Ogg Vorbis audio works without extra tools"));list.add(tr("YouTube and other audio formats use bundled media tools; no separate installation")) }
            IndustrialKind.TV->{ list.add(tr("Interactive Chromium TV: webpages, video and stereo audio"));list.add(tr("120/240 V; 150 W on, 2 W standby; cords or fixed cables"));list.add(tr("Browser bridge included; Chromium installs on first client launch")) }
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
    var mediaUrl="";var mediaRequested=false;var mediaRevision=0
    val webAudio by lazy { WebJukeboxAudio(this,tileEntity) }
    val television by lazy { TelevisionBrowser(this) }
    override fun refresh(deltaT: Float) { super.refresh(deltaT);if(kind==IndustrialKind.JUKEBOX)webAudio.update();if(kind==IndustrialKind.TV)television.update() }
    override fun destructor() { if(kind==IndustrialKind.JUKEBOX)webAudio.close();if(kind==IndustrialKind.TV)television.close();super.destructor() }
    private fun drawBody()=mods.eln.client.terrain.TerrainModels.body(this,listOf(front,enabled),setOf("Indicator")) {
        (transparentNodeDescriptor as IndustrialDescriptor).draw(front ?: Direction.ZP,working,trip || kind==IndustrialKind.ARRESTER && stats[9]<=0,!enabled && kind==IndustrialKind.PAD)
    }
    override fun draw() { drawBody();if(kind==IndustrialKind.TV)television.drawWorld() }
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);settings.indices.forEach { settings[it]=input.readDouble() };enabled=input.readBoolean();trip=input.readBoolean();working=input.readBoolean();stats.indices.forEach { stats[it]=input.readDouble() }
        target=input.readUnsignedByte().coerceIn(0,7);telemetry=input.readUTF();closed.indices.forEach { closed[it]=input.readBoolean() };if(kind==IndustrialKind.JUKEBOX || kind==IndustrialKind.TV) { mediaUrl=input.readUTF();mediaRequested=input.readBoolean();mediaRevision=input.readInt() }
    }
    override fun newGuiDraw(side: Direction,player: Player)=if(kind==IndustrialKind.TV)TelevisionScreen(this) else IndustrialGui(player,this)
}
class IndustrialGui(player: Player,private val render: IndustrialRender): GuiContainerEln(IndustrialContainer(player,render.inventory,render.kind)) {
    private val fields=ArrayList<GuiTextFieldEln>()
    private lateinit var apply: GuiButtonEln;private lateinit var enabled: GuiButtonEln;private lateinit var reset: GuiButtonEln
    private var url: GuiTextFieldEln?=null
    private var play: GuiButtonEln?=null;private var stop: GuiButtonEln?=null
    private var displayedTarget=render.target
    private var select: GuiButtonEln?=null;private var bind: GuiButtonEln?=null;private var close: GuiButtonEln?=null;private var open: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,344,240,8,158)
    override fun initGui() {
        super.initGui();fields.clear()
        render.settings.forEachIndexed { i,v->fields.add(newGuiTextField(110+(i%2)*168,32+(i/2)*18,56).apply { text=v.toString();setComment(0,render.kind.labels()[i]) }) }
        apply=newGuiButton(8,88,105,tr("Apply settings"));enabled=newGuiButton(119,88,105,"");reset=newGuiButton(230,88,104,tr("Reset trip"))
        if(render.kind==IndustrialKind.JUKEBOX) {
            url=newGuiTextField(8,50,328,2048).apply { text=render.mediaUrl;setComment(0,tr("Direct HTTPS audio URL or YouTube video link")) }
            play=newGuiButton(8,66,160,tr("Play / restart"));stop=newGuiButton(174,66,160,tr("Stop"))
        }
        if(render.kind==IndustrialKind.SCADA) {
            select=newGuiButton(8,66,102,"");bind=newGuiButton(116,66,102,tr("Bind address"));open=newGuiButton(224,66,54,tr("Open"));close=newGuiButton(282,66,52,tr("Close"))
        }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            apply->sendSettings()
            enabled->render.clientSendBoolean(81,!render.enabled)
            reset->render.clientSendId(82)
            select->render.clientSendInt(84,(render.target+1)%8)
            bind->{ if(sendSettings())render.clientSendId(83) }
            open->render.clientSendBoolean(85,false)
            close->render.clientSendBoolean(85,true)
            play->{ val value=url?.text ?: return;if(WebAudioUrl.parse(value)!=null)render.clientSendString(86,value) }
            stop->render.clientSendId(87)
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
        drawString(8,22,if(render.trip)tr("Tripped; inspect circuit before reset") else if(!render.enabled)tr("Disabled") else tr("Enabled"))
        render.kind.labels().forEachIndexed { i,label->drawString(8+(i%2)*168,35+(i/2)*18,font.plainSubstrByWidth(label,98)) }
        enabled.displayString=if(render.enabled)tr("Disable / open") else tr("Enable / close");reset.enabled=render.trip
        if(render.kind!=IndustrialKind.SCADA)drawString(8,110,tr("Input: %1$; %2$",Utils.plotVolt("",render.stats[0]).trim(),Utils.plotPower("",render.stats[1]).trim()))
        if(render.kind!=IndustrialKind.SCADA)drawString(8,122,tr("Output: %1$; %2$",Utils.plotVolt("",render.stats[2]).trim(),Utils.plotAmpere("",render.stats[3]).trim()))
        when(render.kind) {
            IndustrialKind.JUKEBOX->drawString(8,140,font.plainSubstrByWidth(render.webAudio.status(),328))
            IndustrialKind.SCADA->{ select?.displayString=tr("Target %1$",render.target+1);render.telemetry.lines().take(3).forEachIndexed { i,line->drawString(8,110+i*12,font.plainSubstrByWidth(line,328)) } }
            IndustrialKind.RELAY->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,when(render.stats[10].toInt()) {
                1->tr("Voltage outside protection settings");2->tr("Overcurrent trip");3->tr("Differential current trip");4->tr("Invalid electrical readings");5->tr("Remote signal trip");else->tr("Protection ready")
            })
            IndustrialKind.ARRESTER->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Surge capacity remaining: %1$ kJ",(render.stats[9]/1000).toInt()))
            IndustrialKind.CHARGER->drawString(8,140,tr("Session stored: %1$ kJ",(render.stats[7]/1000).toInt()))
            IndustrialKind.WELDER->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Welding: %1$ / 120 kJ",(render.stats[8]/1000).toInt()))
            IndustrialKind.DRIVE->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Actual frequency: %1$ Hz; case %2$ C",render.stats[5].toInt(),render.stats[4].toInt()))
            IndustrialKind.SHEDDER->drawString(if(render.kind.slots>0)8+render.kind.slots*24 else 8,140,tr("Priorities 1-4: %1$",render.closed.joinToString(" / ") { if(it)tr("On") else tr("Shed") }))
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
