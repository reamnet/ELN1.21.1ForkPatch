package mods.eln.transparentnode.offgrid

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.power.PowerPanelDisplay
import mods.eln.ghost.GhostBlock
import mods.eln.ghost.GhostGroup
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.SurfaceMounting
import mods.eln.node.transparent.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.io.DataInputStream
import java.io.DataOutputStream

fun offGridStatus(s: String)=when(s) {
    "DISABLED"->tr("Disconnected")
    "BATTERY"->tr("Supplying battery power")
    "CHARGING"->tr("Charging batteries")
    "CHARGE_TARGET"->tr("Battery charge target reached; solar curtailed")
    "GRID_CHARGING"->tr("Grid supplies loads and charges batteries")
    "GRID"->tr("Grid bypass supplies the load")
    "EMPTY"->tr("Battery empty; charging required")
    "FULL"->tr("Battery fully charged")
    "TRANSFER"->tr("Transfer contacts open")
    "WAIT_GRID"->tr("Waiting for 120 or 240 V input")
    "OVERVOLTAGE"->tr("Overvoltage; disconnected")
    "REVERSE"->tr("Reverse polarity; disconnected")
    "TEMPERATURE"->tr("Temperature protection")
    "BACKFEED"->tr("External power on backup output; disconnected")
    "OVERLOAD"->tr("Overcurrent; disconnected")
    "FUSE"->tr("PV fuse blown; replace the cartridge")
    "LIMITED"->tr("Power or current limit")
    "READY"->tr("Ready")
    else->mods.eln.transparentnode.renewable.solarStatus(s)
}
fun islandMode(mode: Int)=when(mode) { 1->tr("Solar/battery first; grid backup");2->tr("Off-grid; AC input disabled");else->tr("Backup reserve; grid charging") }

class OffGridDisplay {
    var enabled=true;var status="READY";var mode=0
    var energy=0.0;var capacity=0.0;var dc=0.0;var grid=0.0;var output=0.0;var watts=0.0
    var inputWatts=0.0;var chargeWatts=0.0;var amps=0.0;var temperature=20.0;var target=240.0;var chargeAmps=100.0
    var connections=LRDUMask()
    fun write(s: DataOutputStream) {
        s.writeBoolean(enabled);s.writeUTF(status);s.writeInt(mode)
        listOf(energy,capacity,dc,grid,output,watts,inputWatts,chargeWatts,amps,temperature,target,chargeAmps).forEach(s::writeDouble);connections.serialize(s)
    }
    fun read(s: DataInputStream) {
        enabled=s.readBoolean();status=s.readUTF();mode=s.readInt()
        energy=s.readDouble();capacity=s.readDouble();dc=s.readDouble();grid=s.readDouble();output=s.readDouble();watts=s.readDouble()
        inputWatts=s.readDouble();chargeWatts=s.readDouble();amps=s.readDouble();temperature=s.readDouble();target=s.readDouble();chargeAmps=s.readDouble();connections.deserialize(s)
    }
}

class OffGridDescriptor(val type: OffGridType): PowerCabinetDescriptor(type.title,OffGridElement::class.java,OffGridRender::class.java,type.wall) {
    private val model=Eln.obj.getObj(type.model)
    init { creativeTab=Eln.creativeTabPowerElectronics;if(type.bank)ghostGroup=GhostGroup().apply { addElement(0,1,0) } }
    override fun getGhostGroupAt(coord: Coordinate,front: Direction)=super.getGhostGroupAt(coord,front)?.also { if(type.wall)it.elementList.forEach { cell->cell.meta=GhostBlock.wallServiceMeta(front) } }
    override fun checkCanPlace(coord: Coordinate?,front: Direction): String? {
        val error=super.checkCanPlace(coord,front);if(error!=null)return error
        if(type.wall && coord!=null && !SurfaceMounting.supports(coord.world(),coord.pos.above(),front.inverse))return tr("Both battery cabinet blocks need a supporting wall")
        return null
    }
    fun checkSupport(e: OffGridElement) {
        checkWallSupport(e)
        if(!type.wall)return
        val at=e.node?.coordinate ?: return
        val world=at.world() as? ServerLevel ?: return
        val support=at.pos.above().relative(e.front.inverse.toFacing())
        if(world.chunkSource.getChunkNow(support.x shr 4,support.z shr 4)!=null && !SurfaceMounting.supports(world,at.pos.above(),e.front.inverse))e.selfDestroy()
    }
    override fun addCollisionBoxesToList(query: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {
        val tile=world?.getBlockEntity(BlockPos(x,y,z)) as? TransparentNodeEntity
        val front=tile?.elementRender?.front ?: (tile?.node as? TransparentNode)?.element?.front ?: Direction.ZP
        val box=if(type.wall)wallBox(front).let { AABB(it.minX,0.0,it.minZ,it.maxX,2.0,it.maxZ) }
            else AABB(.06,0.0,.08,.94,if(type.bank)2.0 else .96,.92)
        val placed=box.move(x.toDouble(),y.toDouble(),z.toDouble());if(query.intersects(placed))list.add(placed)
    }
    fun draw(front: Direction,d: OffGridDisplay?=null)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            part("Case",.82f,.85f,.86f);part("Trim",.09f,.13f,.16f);part("Vents",.035f,.045f,.05f)
            part("Solar",.1f,.5f,.9f);part("Grid",.1f,.28f,.75f);part("Positive",.85f,.15f,.08f);part("Negative",.08f,.09f,.1f);part("Output",.95f,.44f,.07f)
            val lit=d?.enabled==true
            val fault=d?.status in setOf("OVERVOLTAGE","REVERSE","TEMPERATURE","OVERLOAD","BACKFEED","FUSE","PROTECTED")
            PowerPanelDisplay.lamp(model,"StatusLed",lit && !fault && d?.status !in setOf("EMPTY","TRANSFER","WAIT_GRID","NO_POWER"),.1f,.8f,.3f)
            PowerPanelDisplay.lamp(model,"ChargeLed",d?.chargeWatts?.let { it>1 }==true || d?.watts?.let { it< -1 }==true,.1f,.5f,1f)
            PowerPanelDisplay.lamp(model,"FaultLed",fault,1f,.06f,.025f)
            PowerPanelDisplay.equipmentNumber(model,d?.dc ?: 0.0,lit,"Voltage")
            PowerPanelDisplay.equipmentNumber(model,if(type.bank && d!=null && d.capacity>0)d.energy/d.capacity*100 else d?.chargeWatts ?: 0.0,lit,"Charge")
            PowerPanelDisplay.equipmentNumber(model,d?.watts ?: 0.0,lit,"Power")
            if(type.bank)PowerPanelDisplay.equipmentNumber(model,type.kwh.toDouble(),true,"Capacity")
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        if(this.type.bank) { GL11.glScalef(.48f,.48f,.48f);GL11.glTranslatef(0f,-.5f,0f) };draw(Direction.ZP)
    }
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        if(type.bank) {
            list.add(tr("%1$ kWh LiFePO4; 51.2 V nominal DC; %2$ A BMS",type.kwh,type.kwh/16*100))
            list.add(tr("Two blocks tall; integrated BMS and DC disconnect; crafted empty"))
            list.add(tr("DC storage only: a separate inverter is required for 120 or 240 V loads"))
            list.add(tr("Left red: battery positive; right black: battery negative"))
            if(type.wall)list.add(tr("Mount against a two-block-tall wall; the cabinet projects 0.33 blocks"))
        } else when(type) {
            OffGridType.INVERTER->{ list.add(tr("120 or 240 V isolated backup output; 12000 W maximum"));list.add(tr("120/240 V grid or generator input; 56.8 V battery charging; no grid export"));list.add(tr("Left: DC+; rear: DC-; front blue: AC input; right orange: backed-up loads")) }
            OffGridType.AC_CHARGER->{ list.add(tr("120/240 V AC input to 56.8 V DC; adjustable 0 to 200 A charging"));list.add(tr("Left: battery DC+; rear: DC-; front blue: grid or generator input")) }
            OffGridType.MPPT->{ list.add(tr("10000 W solar battery charger; 56.8 V output; 200 A maximum"));list.add(tr("Left blue: PV+; rear black: PV-; right red: battery+; front black: battery-")) }
            OffGridType.COMBINER->{ list.add(tr("Two PV string inputs; included 30 A fuses; 1000 V DC maximum"));list.add(tr("Left and front: PV positives; right: combined positive; rear: common return"));list.add(tr("The handle opens both positives; replace blown fuses with 30 A or smaller cartridges")) }
            else->{ list.add(tr("Manual 400 A battery DC breaker; left input and right output"));list.add(tr("Trips on overcurrent; clear the fault before resetting")) }
        }
    }
}

class OffGridRender(tile: TransparentNodeEntity,desc: TransparentNodeDescriptor): TransparentNodeElementRender(tile,desc),mods.eln.client.terrain.BakedNodeRenderer {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val display=OffGridDisplay()
    private var cable: CableRenderType?=null
    override fun draw() {
        val desc=transparentNodeDescriptor as OffGridDescriptor
        cable=drawCable(desc.connectionPlane(front ?: Direction.ZP),Eln.instance.stdCableRender50V,display.connections,cable)
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("StatusLed","ChargeLed","FaultLed","Voltage","Charge","Power")) { desc.draw(front ?: Direction.ZP,display) }
    }
    override fun networkUnserialize(s: DataInputStream) { super.networkUnserialize(s);display.read(s);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=OffGridGui(player,this)
}
class OffGridGui(player: Player,private val r: OffGridRender): GuiContainerEln(BasicContainer(player,r.inventory,emptyArray())) {
    private val d get()=r.display
    private val type=(r.transparentNodeDescriptor as OffGridDescriptor).type
    private lateinit var toggle: GuiButtonEln
    private var mode: GuiButtonEln?=null
    private var amps: GuiTextFieldEln?=null
    private var apply: GuiButtonEln?=null
    private var v120: GuiButtonEln?=null
    private var v240: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,320,310,80,227)
    override fun initGui() {
        super.initGui();toggle=newGuiButton(8,128,148,"");newGuiButton(164,128,148,tr("Reset protection")).also { reset=it }
        if(type==OffGridType.INVERTER) {
            mode=newGuiButton(8,152,304,"");v120=newGuiButton(8,178,148,tr("120 V output"));v240=newGuiButton(164,178,148,tr("240 V output"))
        }
        if(type==OffGridType.INVERTER || type==OffGridType.AC_CHARGER) {
            amps=newGuiTextField(8,209,148).apply { text=d.chargeAmps.toString();setComment(0,tr("Battery charging current: 0 to 200 A")) }
            apply=newGuiButton(164,205,148,tr("Set charge amps"))
        }
    }
    private var reset: GuiButtonEln?=null
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) { toggle->r.clientSendId(81);mode->r.clientSendId(82);reset->r.clientSendId(83);v120->r.clientSendDouble(85,120.0);v240->r.clientSendDouble(85,240.0);apply->amps?.text?.toDoubleOrNull()?.let { r.clientSendDouble(84,it) } }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,font.plainSubstrByWidth(tr(type.title),304));drawString(8,24,font.plainSubstrByWidth(offGridStatus(d.status),304))
        drawString(8,40,tr("Battery bus: %1$",Utils.plotVolt("",d.dc).trim()))
        if(type.bank) {
            drawString(8,56,tr("Stored: %1$ / %2$ kWh",String.format(java.util.Locale.ROOT,"%.2f",d.energy/3.6e6),type.kwh))
            drawString(8,72,tr("Battery current: %1$",Utils.plotAmpere("",d.amps).trim()))
            drawString(8,88,tr("Cell temperature: %1$ C",d.temperature.toInt()))
            drawString(8,106,tr("A separate inverter supplies household voltage."))
        } else {
            drawString(8,56,tr("Input: %1$ / %2$",Utils.plotVolt("",d.grid).trim(),Utils.plotPower("",d.inputWatts).trim()))
            drawString(8,72,tr("Battery charging: %1$",Utils.plotPower("",d.chargeWatts).trim()))
            drawString(8,88,tr("Load: %1$ / %2$",Utils.plotVolt("",d.output).trim(),Utils.plotPower("",d.watts).trim()))
            drawString(8,106,tr("Grid input and backed-up output are separate."))
        }
        toggle.displayString=if(d.enabled)tr("Disconnect") else tr("Connect")
        mode?.displayString=islandMode(d.mode)
        apply?.enabled=amps?.text?.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..200.0 }==true
        if(type==OffGridType.COMBINER)drawString(8,156,tr("Use a 30 A or smaller fuse item to replace a blown link."))
    }
}
