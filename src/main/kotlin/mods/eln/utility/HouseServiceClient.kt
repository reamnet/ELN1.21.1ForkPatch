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

class HouseServiceDescriptor(val meter: Boolean): ServiceWallDescriptor(
    if(meter)I18N.TR_NAME(I18N.Type.NONE,"North American Meter Base") else I18N.TR_NAME(I18N.Type.NONE,"120/240V Service Weatherhead"),
    if(meter)"northamericanmeterbase" else "serviceweatherhead",HouseServiceElement::class.java,HouseServiceRender::class.java) {
    init { creativeTab=Eln.creativeTabPowerElectronics;if(!meter)ghostGroup=GhostGroup().apply { addElement(0,1,0,GhostBlock.tClearance) } }
    fun drawService(front: Direction,meterType: Int=0,energy: Double=0.0,disconnect: Boolean=false,powered: Boolean=false,disk: Double=0.0)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);obj.getPart(name)?.drawNoBind() }
            part("Case",.55f,.57f,.57f)
            if(!meter) { part("Pipe",.49f,.51f,.52f);part("Hood",.40f,.43f,.45f);repeat(3) { part("Terminal$it",.61f,.64f,.58f) };return@preserveMatrix }
            part("Socket",.10f,.12f,.13f);repeat(3) { part("Terminal$it",.62f,.48f,.26f);part("TopTerminal$it",.62f,.48f,.26f) }
            if(meterType==0)return@preserveMatrix
            part("MeterFace",.85f,.86f,.80f)
            if(meterType==1) {
                part("Readout",.16f,.17f,.15f)
                repeat(5) { i ->
                    part("Dial$i",.91f,.91f,.83f)
                    val x=-.132+i*.066
                    preserveMatrix {
                        GL11.glTranslated(x,.05,-.12);GL11.glRotated((energy/10.0.pow(4-i)%10)*36*(if(i%2==0)-1 else 1),0.0,0.0,1.0);GL11.glTranslated(-x,-.05,.12)
                        part("Needle$i",.12f,.13f,.11f)
                    }
                }
                preserveMatrix { GL11.glTranslated(0.0,-.06,-.18);GL11.glRotated(disk,0.0,1.0,0.0);GL11.glTranslated(0.0,.06,.18);part("Disk",.68f,.69f,.66f);part("DiskMark",.06f,.07f,.06f) }
            } else {
                part("SmartScreen",.16f,.24f,.20f)
                if(powered)UtilsClient.disableLight()
                GL11.glColor3f(if(powered).42f else .06f,if(powered).84f else .12f,if(powered).52f else .08f)
                drawMeterDigits(energy)
                if(powered)UtilsClient.enableLight()
                part("Disconnect",if(disconnect).8f else .12f,if(disconnect).08f else .60f,.09f)
            }
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false)
            GL11.glColor4f(.62f,.79f,.85f,.20f);obj.getPart("Glass")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
    }
    private fun drawMeterDigits(energy: Double) {
        val text=String.format(Locale.ROOT,"%06d",(energy*100).toLong().coerceIn(0,999999))
        val masks=intArrayOf(0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f)
        fun rect(x: Double,y: Double,w: Double,h: Double) {
            GL11.glBegin(GL11.GL_QUADS);GL11.glNormal3f(0f,0f,1f)
            GL11.glVertex3d(x,y,-.120);GL11.glVertex3d(x+w,y,-.120);GL11.glVertex3d(x+w,y+h,-.120);GL11.glVertex3d(x,y+h,-.120);GL11.glEnd()
        }
        text.forEachIndexed { i,c ->
            val x=-.137+i*.044;val y=.028;val mask=masks[c-'0'];val w=.031;val h=.070;val t=.004
            val segments=arrayOf(doubleArrayOf(x,y+h-t,w,t),doubleArrayOf(x+w-t,y+h/2,t,h/2),doubleArrayOf(x+w-t,y,t,h/2),doubleArrayOf(x,y,w,t),doubleArrayOf(x,y,t,h/2),doubleArrayOf(x,y+h/2,t,h/2),doubleArrayOf(x,y+h/2-t/2,w,t))
            repeat(7) { k -> if(mask and (1 shl k)!=0)segments[k].let { rect(it[0],it[1],it[2],it[3]) } }
            if(i==3)rect(x+w+.003,y,.005,.005)
        }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=drawService(Direction.ZP,if(meter)1 else 0)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Three-wire service bundle: L1 120 V, L2 120 V and 240 V circuit equivalents"))
        if(meter) { list.add(tr("Insert an Analog Glass Service Meter or Smart Digital Service Meter"));list.add(tr("Placed wall cables: top service input, bottom service output; insert a meter to connect"));list.add(tr("Smart meter has a manual disconnect and a future remote-control hook")) }
        else list.add(tr("Terminate a pole service drop here and continue down the wall with placed cables"))
        list.add(tr("Top and bottom cable terminals use the circuit selected in the menu"))
    }
}
class HouseServiceRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    val meter=(descriptor as HouseServiceDescriptor).meter
    override val inventory=TransparentNodeElementInventory(if(meter)1 else 0,1,this)
    var terminal=50;var circuit=2;var type=0;var disconnected=false;var imported=0.0;var exported=0.0;var volts=listOf(0.0,0.0,0.0);var watts=0.0
    var disk=0.0
    private var powered=false
    private val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() {
        drawCables();(transparentNodeDescriptor as HouseServiceDescriptor).drawService(front ?: Direction.XN,type,imported,disconnected,powered,disk)
        cable=drawCable(front!!.inverse,Eln.instance.stdCableRender200V,connections,cable)
    }
    override fun refresh(deltaT: Float) { super.refresh(deltaT);disk=(disk+watts*deltaT.coerceIn(0f,.25f)*.01)%360 }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);terminal=stream.readInt();circuit=stream.readInt();type=stream.readInt();disconnected=stream.readBoolean();imported=stream.readDouble();exported=stream.readDouble();volts=List(3) { stream.readDouble() };watts=stream.readDouble();powered=stream.readBoolean();connections.deserialize(stream);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=HouseServiceGui(player,this)
}
class HouseServiceGui(player: Player,private val r: HouseServiceRender): GuiContainerEln(HouseServiceContainer(player,r.inventory,r.meter)) {
    private lateinit var terminal: GuiButtonEln;private lateinit var circuit: GuiButtonEln;private var disconnect: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,240,264,8,182)
    override fun initGui() {
        super.initGui();terminal=newGuiButton(8,58,224,"");circuit=newGuiButton(8,82,224,"")
        if(r.meter)disconnect=newGuiButton(8,106,224,"")
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
        drawString(8,8,if(r.meter)tr("North American Meter Base") else tr("120/240V Service Weatherhead"))
        if(r.meter)drawString(32,35,when(r.type) { 1->tr("Analog meter installed");2->tr("Smart meter installed");else->tr("Insert a meter to connect service") })
        terminal.enabled=!r.meter
        terminal.displayString=if(r.meter)tr("Placed wall cables only") else when(r.terminal) { 50->if(r.meter)tr("Cable: incoming service bundle") else tr("Cable: service drop bundle");51->tr("Cable: outgoing service bundle");else->tr("Cable: %1$",ServiceTerminals.label(r.terminal)) }
        circuit.displayString=tr("Top/bottom cable: %1$",ServiceTerminals.label(40+r.circuit))
        disconnect?.apply { enabled=r.type==2;displayString=if(r.disconnected)tr("Smart disconnect: Open") else tr("Smart disconnect: Closed") }
        if(r.meter) {
            drawString(8,136,tr("Import: %1$ kWh",String.format(Locale.ROOT,"%.3f",r.imported)))
            drawString(8,148,tr("Export: %1$ kWh",String.format(Locale.ROOT,"%.3f",r.exported)))
            drawString(8,160,tr("Power: %1$",Utils.plotPower("",r.watts).trim()))
        } else repeat(3) { i -> drawString(8,136+i*12,tr("%1$: %2$",ServiceTerminals.label(40+i),Utils.plotVolt("",r.volts[i]).trim())) }
    }
}
