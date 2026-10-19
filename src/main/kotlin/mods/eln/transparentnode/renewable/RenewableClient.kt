package mods.eln.transparentnode.renewable

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostGroup
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream

enum class RenewableKind { MPPT, HYBRID, WIND, WATER }
fun solarStatus(s: String)=when(s) {
    "DISABLED" -> tr("Switched off")
    "NO_POWER" -> tr("Waiting for solar or grid power")
    "GRID" -> tr("Grid supplies the load")
    "SOLAR" -> tr("Solar supplies the load")
    "ASSIST" -> tr("Solar and grid supply the load")
    "LIMITED" -> tr("Available power or current limit")
    "BACKFEED" -> tr("Output already powered; reverse feed blocked")
    "PROTECTED" -> tr("Protected; checking the network")
    else -> tr("Starting solar controller")
}
fun priorityText(p: Int)=when(p) {
    1 -> tr("Grid first")
    2 -> tr("Solar only")
    3 -> tr("Grid only")
    else -> tr("Solar first + grid assist")
}

class SolarDisplay {
    var target=120.0;var enabled=true;var priority=0;var status="IDLE"
    var pv=0.0;var pvWatts=0.0;var grid=0.0;var gridWatts=0.0;var output=0.0;var watts=0.0;var energy=0.0
    var connections=LRDUMask()
    fun write(s: DataOutputStream) {
        s.writeDouble(target);s.writeBoolean(enabled);s.writeInt(priority);s.writeUTF(status)
        for(v in listOf(pv,pvWatts,grid,gridWatts,output,watts,energy))s.writeDouble(v)
        connections.serialize(s)
    }
    fun read(s: DataInputStream) {
        target=s.readDouble();enabled=s.readBoolean();priority=s.readInt();status=s.readUTF()
        pv=s.readDouble();pvWatts=s.readDouble();grid=s.readDouble();gridWatts=s.readDouble();output=s.readDouble();watts=s.readDouble();energy=s.readDouble()
        connections.deserialize(s)
    }
}

class RenewableDescriptor(name: String,val kind: RenewableKind,wallMounted: Boolean=false) : PowerCabinetDescriptor(name,
    if(kind==RenewableKind.WIND || kind==RenewableKind.WATER)LargeRenewableElement::class.java else SolarElement::class.java,RenewableRender::class.java,wallMounted) {
    val large get()=kind==RenewableKind.WIND || kind==RenewableKind.WATER
    private val model=Eln.obj.getObj(when(kind) { RenewableKind.MPPT->if(wallMounted)"wallmppt" else "solarmppt";RenewableKind.HYBRID->if(wallMounted)"wallhybrid" else "solarhybrid";RenewableKind.WIND->"utilitywind";RenewableKind.WATER->"utilitywatermill" })
    init {
        require(!large || !wallMounted)
        if(large)ghostGroup=GhostGroup().apply {
            if(kind==RenewableKind.WIND) {
                addRectangle(0,0,1,10,0,0)
                addRectangle(-1,-1,5,14,-5,5)
                addElement(1,9,0)
            } else {
                addRectangle(1,1,0,8,-4,4)
                addRectangle(0,0,1,5,-1,1)
            }
        }
    }
    fun draw(front: Direction,angle: Float=0f)=preserveMatrix {
        // The source mesh faces +Z. Ghosts and sampling use the node's canonical -X front.
        front.inverse.glRotateZnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.77f,.80f,.82f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.17f,.20f,.23f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.04f,.07f,.09f);model.getPart("Display")?.drawNoBind()
            GL11.glColor3f(.12f,.55f,.92f);model.getPart("Solar")?.drawNoBind()
            GL11.glColor3f(.08f,.24f,.55f);model.getPart("Grid")?.drawNoBind()
            GL11.glColor3f(.97f,.37f,.05f);model.getPart("Output")?.drawNoBind()
            GL11.glColor3f(.1f,.12f,.14f);model.getPart("Return")?.drawNoBind()
            if(large)preserveMatrix {
                GL11.glTranslatef(0f,if(kind==RenewableKind.WIND)9.5f else 3.5f,if(kind==RenewableKind.WIND).8f else -.65f)
                GL11.glRotatef(angle,0f,0f,1f)
                GL11.glColor3f(if(kind==RenewableKind.WIND).88f else .37f,if(kind==RenewableKind.WIND).9f else .26f,if(kind==RenewableKind.WIND).91f else .14f)
                model.getPart("Rotor")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        if(large) { val scale=if(kind==RenewableKind.WIND).065f else .1f;GL11.glScalef(scale,scale,scale);GL11.glTranslatef(0f,-3f,0f) }
        draw(Direction.ZP)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        when(kind) {
            RenewableKind.MPPT -> {
                list.add(tr("10000 W MPPT solar controller; programmable 12 to 3200 V output"))
                list.add(tr("Automatically matches solar input impedance for maximum available power"))
            }
            RenewableKind.HYBRID -> {
                list.add(tr("20000 W hybrid solar inverter; 120 or 240 V output"))
                list.add(tr("Solar first with grid assist, grid first, solar only or grid only"))
            }
            RenewableKind.WIND -> {
                list.add(tr("3200 V regulated wind generator; up to 20000 W at 12 m/s"))
                list.add(tr("Nine-metre rotor; fifteen-block clearance; wind cut-in 3 m/s, cut-out 25 m/s"))
                list.add(tr("Orange front terminal: output; place in open sky"))
            }
            RenewableKind.WATER -> {
                list.add(tr("3200 V regulated watermill; up to 10000 W"))
                list.add(tr("Eight-metre wheel; nine-block clearance; moving water required"))
                list.add(tr("Run flowing water behind the wheel at base level, across its width"))
                list.add(tr("Orange front terminal: output; still water produces no power"))
            }
        }
        if(!large) {
            list.add(if(wallMounted)tr("Wall cables: blue left PV+; black bottom PV-; orange right output")
                else tr("Blue left: solar positive; black rear: solar return; orange right: output"))
            if(kind==RenewableKind.HYBRID)list.add(if(wallMounted)tr("Dark blue top: optional grid input") else tr("Dark blue front: optional grid input"))
            list.add(tr("Solar input: 6 to 51200 V; 200 A maximum; output never feeds back into inputs"))
            list.add(tr("Internal 5000 J DC link; use a UPS or batteries for sustained storage"))
            if(wallMounted)list.add(tr("Place against a vertical wall; connect cables on the same wall face"))
        }
    }
}

class RenewableRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val display=SolarDisplay()
    private var cable: CableRenderType?=null
    private var available=0.0
    private var angle=0f
    private val renewable get()=transparentNodeDescriptor as RenewableDescriptor
    override fun draw() {
        cable=drawCable(renewable.connectionPlane(front ?: Direction.ZP),Eln.instance.stdCableRender3200V,display.connections,cable)
        renewable.draw(front ?: Direction.ZP,angle)
    }
    override fun refresh(deltaT: Float) { super.refresh(deltaT);if(renewable.large)angle=(angle+deltaT*if(available>.01)35f else 0f)%360f }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        if(renewable.large) { stream.readDouble();available=stream.readDouble();stream.readDouble();display.connections.deserialize(stream) }
        else display.read(stream)
        cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=SolarGui(player,this)
}

class SolarGui(player: Player,private val render: RenewableRender) : GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private val hybrid=(render.transparentNodeDescriptor as RenewableDescriptor).kind==RenewableKind.HYBRID
    private val d get()=render.display
    private lateinit var voltage: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    private lateinit var enable: GuiButtonEln
    private var priority: GuiButtonEln?=null
    private var v120: GuiButtonEln?=null
    private var v240: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,220,286,30,203)
    override fun initGui() {
        super.initGui()
        enable=newGuiButton(8,88,204,"")
        if(hybrid) {
            priority=newGuiButton(8,112,204,"")
            v120=newGuiButton(8,138,98,tr("120 V output"));v240=newGuiButton(114,138,98,tr("240 V output"))
        }
        voltage=newGuiTextField(8,if(hybrid)170 else 116,110).apply { text=d.target.toString();setComment(0,if(hybrid)tr("Output must be 120 or 240 V") else tr("Output voltage: 12 to 3200 V")) }
        apply=newGuiButton(124,if(hybrid)166 else 112,88,tr("Set voltage"))
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            enable -> render.clientSendId(81)
            priority -> render.clientSendId(82)
            v120 -> { voltage.text="120";render.clientSendDouble(80,120.0) }
            v240 -> { voltage.text="240";render.clientSendDouble(80,240.0) }
            apply -> voltage.text.toDoubleOrNull()?.let { render.clientSendDouble(80,it) }
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,if(hybrid)tr("Hybrid Solar Inverter") else tr("MPPT Solar Controller"))
        drawString(8,22,font.plainSubstrByWidth(solarStatus(d.status),204))
        drawString(8,36,tr("Solar: %1$ / %2$",Utils.plotVolt("",d.pv).trim(),Utils.plotPower("",d.pvWatts).trim()))
        drawString(8,48,tr("Grid: %1$ / %2$",Utils.plotVolt("",d.grid).trim(),Utils.plotPower("",d.gridWatts).trim()))
        drawString(8,60,tr("Out: %1$ / %2$",Utils.plotVolt("",d.output).trim(),Utils.plotPower("",d.watts).trim()))
        drawString(8,74,tr("DC link: %1$ / 5000 J",d.energy.toInt()))
        enable.displayString=if(d.enabled)tr("Controller: On") else tr("Controller: Off")
        priority?.displayString=priorityText(d.priority)
        drawString(8,if(hybrid)190 else 150,tr("Blue: PV+; black: PV-; orange: out"))
        if(!hybrid)drawString(8,164,tr("No load: solar harvest is curtailed"))
        apply.enabled=voltage.text.toDoubleOrNull()?.let { it.isFinite() && if(hybrid)it==120.0 || it==240.0 else it in 12.0..3200.0 }==true
    }
}
