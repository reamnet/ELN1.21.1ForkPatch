package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostBlock
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.GridRender
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale
import kotlin.math.pow

class HouseServiceDescriptor(val meter: Boolean,val disconnect: Boolean=false,val mast: ServiceMastStyle=ServiceMastStyle.STANDARD,val wide: Boolean=false): ServiceWallDescriptor(
    if(disconnect)I18N.TR_NAME(I18N.Type.NONE,"Red Handle Service Disconnect") else if(wide)I18N.TR_NAME(I18N.Type.NONE,"Wide Underground Meter Socket") else if(meter)I18N.TR_NAME(I18N.Type.NONE,"North American Meter Base") else mast.title,
    if(disconnect)"redhandleservicedisconnect" else if(wide)"wideundergroundmetersocket" else if(meter)"northamericanmeterbase" else mast.model,HouseServiceElement::class.java,HouseServiceRender::class.java) {
    override val serviceWidth get()=if(wide)2 else 1
    init {
        creativeTab=Eln.creativeTabPowerElectronics
        if(wide)ghostGroup=GhostGroup().apply { addElement(0,0,1,GhostBlock.tCube) }
        else if(!meter && !disconnect && mast==ServiceMastStyle.STANDARD)ghostGroup=GhostGroup().apply { addElement(0,1,0,GhostBlock.tClearance) }
    }
    override fun checkCanPlace(coord: Coordinate?,front: Direction): String? {
        val reason=super.checkCanPlace(coord,front);if(reason!=null)return reason
        if(wide && coord!=null) {
            val support=coord.pos.relative(front.right().toFacing()).relative(front.inverse.toFacing())
            if(!coord.world().getBlockState(support).isSolidRender(coord.world(),support))return tr("The wide socket needs a solid wall behind both halves")
        }
        return null
    }
    fun drawService(front: Direction,meterType: Int=0,energy: Double=0.0,disconnect: Boolean=false,powered: Boolean=false,disk: Double=0.0,exported: Double=0.0,watts: Double=0.0,volts: Double=240.0)=preserveMatrix {
        if(this.disconnect) { ServiceDisconnectVisual.draw(front,disconnect,obj);return@preserveMatrix }
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);obj.getPart(name)?.drawNoBind() }
            part("Case",.55f,.57f,.57f)
            if(!meter) { part("Pipe",.49f,.51f,.52f);part("Hood",.40f,.43f,.45f);repeat(3) { part("Terminal$it",.61f,.64f,.58f) };return@preserveMatrix }
            part("Socket",.10f,.12f,.13f);part("GutterCover",.55f,.57f,.57f)
            part("EntryLine",.13f,.27f,.48f);part("EntryLoad",.55f,.32f,.12f)
            part("Bond",.48f,.58f,.47f);part("Screws",.72f,.74f,.73f)
            repeat(3) { part("Terminal$it",.62f,.48f,.26f);part("TopTerminal$it",.62f,.48f,.26f) }
            if(meterType==0) { part("JawInsulator",.12f,.14f,.15f);part("Jaws",.75f,.52f,.29f);return@preserveMatrix }
            ServiceMeterVisual.draw(obj,meterType,energy,exported,watts,volts,disconnect,powered,disk)
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=drawService(Direction.ZP,if(meter)1 else 0)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        if(disconnect) { list.add(tr("Place between the meter and panel; top is input, bottom is output"));list.add(tr("Red handle manually opens all service circuits; use placed wall cables"));return }
        list.add(tr("Three-wire service bundle: L1 120 V, L2 120 V and 240 V circuit equivalents"))
        if(wide)list.add(tr("Two blocks wide; downward input under the left half, downward output under the right"))
        if(meter) { list.add(tr("Insert an Analog Glass Service Meter or Smart Digital Service Meter"));list.add(if(wide)tr("Bottom entries only: utility line on the left, customer load on the right") else tr("Placed wall cables: top service input, bottom service output; insert a meter to connect"));list.add(tr("Smart meter has a manual disconnect and a future remote-control hook")) }
        else if(mast.underground)list.add(tr("Buried service enters below; continue upward to the meter with placed cables"))
        else list.add(tr("Terminate a pole service drop here and continue down the wall with placed cables"))
        list.add(tr("Top and bottom cable terminals use the circuit selected in the menu"))
    }
}
class HouseServiceRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    val meter=(descriptor as HouseServiceDescriptor).meter
    val serviceDisconnect=(descriptor as HouseServiceDescriptor).disconnect
    override val inventory=TransparentNodeElementInventory(if(meter)1 else 0,1,this)
    var terminal=50;var circuit=2;var type=0;var disconnected=false;var imported=0.0;var exported=0.0;var volts=listOf(0.0,0.0,0.0);var watts=0.0
    var disk=0.0;var serial="";var lineVoltage=0.0
    private var powered=false
    private val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() {
        drawCables();mods.eln.client.terrain.TerrainModels.body(this,listOf(front,type,disconnected,powered),setOf("Disk","DiskMark","Glass")+(0..4).map { "Needle$it" }) {
            (transparentNodeDescriptor as HouseServiceDescriptor).drawService(front ?: Direction.XN,type,imported,disconnected,powered,disk,exported,watts,lineVoltage)
        }
        if(!(transparentNodeDescriptor as HouseServiceDescriptor).wide)cable=drawCable(front!!.inverse,Eln.instance.stdCableRender200V,connections,cable)
    }
    override fun refresh(deltaT: Float) { super.refresh(deltaT);disk=(disk+ServiceMeters.diskDegrees(watts*deltaT.coerceIn(0f,.25f)))%360 }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);terminal=stream.readInt();circuit=stream.readInt();type=stream.readInt();disconnected=stream.readBoolean();imported=stream.readDouble();exported=stream.readDouble();volts=List(3) { stream.readDouble() };watts=stream.readDouble();powered=stream.readBoolean();connections.deserialize(stream);serial=stream.readUTF();lineVoltage=stream.readDouble();disk=ServiceMeters.diskPosition(imported,exported);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=HouseServiceGui(player,this)
}
class HouseServiceGui(player: Player,private val r: HouseServiceRender): GuiContainerEln(HouseServiceContainer(player,r.inventory,r.meter)) {
    private lateinit var terminal: GuiButtonEln;private lateinit var circuit: GuiButtonEln;private var disconnect: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,240,264,8,182)
    override fun initGui() {
        super.initGui();terminal=newGuiButton(8,58,224,"");circuit=newGuiButton(8,82,224,"")
        if(r.meter || r.serviceDisconnect)disconnect=newGuiButton(8,106,224,"")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            terminal->{ val ts=if(r.meter)listOf(50,51,40,41,42) else listOf(50,40,41,42);r.clientSendInt(87,ts[(ts.indexOf(r.terminal)+1)%ts.size]) }
            circuit->r.clientSendInt(88,(r.circuit+1)%3)
            disconnect->r.clientSendBoolean(89,!r.disconnected)
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,if(r.serviceDisconnect)tr("Red Handle Service Disconnect") else if(r.meter)tr((r.transparentNodeDescriptor as HouseServiceDescriptor).name) else tr((r.transparentNodeDescriptor as HouseServiceDescriptor).mast.title))
        if(r.meter)drawString(32,35,when(r.type) { 1->tr("Analog meter installed");2->tr("Smart meter installed");else->tr("Insert a meter to connect service") })
        if(r.meter && r.serial.isNotBlank())drawString(32,46,tr("Meter serial: %1$",r.serial))
        terminal.enabled=!r.meter && !r.serviceDisconnect
        terminal.displayString=if(r.meter || r.serviceDisconnect)tr("Placed wall cables only") else when(r.terminal) { 50->if(r.meter)tr("Cable: incoming service bundle") else tr("Cable: service drop bundle");51->tr("Cable: outgoing service bundle");else->tr("Cable: %1$",ServiceTerminals.label(r.terminal)) }
        circuit.displayString=if((r.transparentNodeDescriptor as HouseServiceDescriptor).wide)tr("Bottom entries: %1$",ServiceTerminals.label(40+r.circuit)) else tr("Top/bottom cable: %1$",ServiceTerminals.label(40+r.circuit))
        disconnect?.apply { enabled=r.serviceDisconnect || r.type==2;displayString=if(r.serviceDisconnect) { if(r.disconnected)tr("Service: OFF") else tr("Service: ON") } else if(r.disconnected)tr("Smart disconnect: Open") else tr("Smart disconnect: Closed") }
        if(r.meter) {
            drawString(8,136,tr("Import: %1$ kWh",String.format(Locale.ROOT,"%.3f",r.imported)))
            drawString(8,148,tr("Export: %1$ kWh",String.format(Locale.ROOT,"%.3f",r.exported)))
            drawString(8,160,tr("Power: %1$",Utils.plotPower("",r.watts).trim()))
        } else repeat(3) { i -> drawString(8,136+i*12,tr("%1$: %2$",ServiceTerminals.label(40+i),Utils.plotVolt("",r.volts[i]).trim())) }
    }
}
