package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gridnode.GridCablePolicy
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.io.DataInputStream
import java.io.DataOutputStream

object TrayColors {
    fun label(i: Int)=listOf(tr("White"),tr("Orange"),tr("Magenta"),tr("Light blue"),tr("Yellow"),tr("Lime"),tr("Pink"),tr("Gray"),tr("Light gray"),tr("Cyan"),tr("Purple"),tr("Blue"),tr("Brown"),tr("Green"),tr("Red"),tr("Black"))[i.coerceIn(0,15)]
    fun side(side: Direction)=when(side) { Direction.XN->tr("West");Direction.XP->tr("East");Direction.YN->tr("Down");Direction.YP->tr("Up");Direction.ZN->tr("North");Direction.ZP->tr("South") }
    val ports=listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP,Direction.YN)
}

/** Adjacent trays join only installed matching colors. Ordinary cables use one
 * explicit colored breakout, or their painted color in auto mode. */
class ColorCableTrayElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    val loads=List(16) { NbtElectricalLoad("trayColor$it") }
    val ports=IntArray(6) { 16 }
    private val resolved=IntArray(6)
    private var attached=false
    override val inventory=object: TransparentNodeElementInventory(16,1,this@ColorCableTrayElement) {
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=stack!=null && (ElectricalCableDescriptor.getDescriptor(stack) as? ElectricalCableDescriptor)?.let { GridCablePolicy.accepts(it) }==true
    }
    private fun installed(i: Int)=(ElectricalCableDescriptor.getDescriptor(inventory.getItem(i)) as? ElectricalCableDescriptor)?.let { GridCablePolicy.accepts(it) }==true
    val installedColors get()=(0..15).filter(::installed)
    init { slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5)) }
    private fun configure() {
        electricalLoadList.clear();electricalLoadList.addAll(installedColors.map { loads[it] })
        loads.forEachIndexed { i,p->(ElectricalCableDescriptor.getDescriptor(inventory.getItem(i)) as? ElectricalCableDescriptor)?.applyTo(p) ?: run { p.serialResistance=1e10 } }
    }
    override fun initialize() { configure();connect() }
    override fun connectJob() { super.connectJob();attached=true }
    override fun disconnectJob() { attached=false;super.disconnectJob() }
    override fun onNeighborBlockChange() {
        super.onNeighborBlockChange()
        val world=node?.coordinate?.world() as? net.minecraft.server.level.ServerLevel ?: return
        val above=node!!.coordinate.pos.above()
        if(world.chunkSource.getChunkNow(above.x shr 4,above.z shr 4)!=null && !world.getBlockState(above).isSolidRender(world,above))selfDestroy()
    }
    override fun inventoryChange(inventory: Container?) { val live=attached;if(live)disconnect();configure();if(live)connect();needPublish() }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(installedColors.isNotEmpty() && (side==Direction.YN || !side.isY && side.getLRDUGoingTo(Direction.YP)==lrdu))NodeBase.maskElectricalPower else 0
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): ElectricalLoad?=if(getConnectionMask(side,lrdu)==0)null else loads[(ports[side.int].takeIf { it<16 } ?: 0)].takeIf { installed(ports[side.int].takeIf { it<16 } ?: 0) }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU,remoteEndpoint: NodeConnectionEndpoint): ElectricalLoad? {
        if(getConnectionMask(side,lrdu)==0)return null
        val other=remoteEndpoint.element as? ColorCableTrayElement
        val color=if(other!=null)installedColors.firstOrNull { it in other.installedColors } ?: return null else {
            val mask=remoteEndpoint.node.getSideConnectionMask(remoteEndpoint.side,remoteEndpoint.lrdu)
            ports[side.int].takeIf { it<16 } ?: ((mask ushr NodeBase.maskColorShift) and 15)
        }
        resolved[side.int]=color
        return loads[color].takeIf { installed(color) }
    }
    override fun newConnectionAt(connection: NodeConnection,isA: Boolean) {
        if(!isA)return
        val other=connection.otherEndpoint(true).element as? ColorCableTrayElement ?: return
        val common=installedColors.filter { it in other.installedColors }
        for(i in common.drop(1))ElectricalConnection(loads[i],other.loads[i]).also {
            Eln.simulator.addElectricalComponent(it);connection.addConnection(it)
        }
    }
    fun setPort(side: Int,color: Int): Boolean {
        if(side !in ports.indices || color !in 0..16)return false
        if(ports[side]==color)return false
        ports[side]=color;if(attached)reconnect();needPublish();return true
    }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=ColorTrayContainer(player,inventory)
    override fun multiMeterString(side: Direction)=installedColors.joinToString("\n") { tr("%1$: %2$ %3$",TrayColors.label(it),Utils.plotVolt("",loads[it].voltage),Utils.plotAmpere("",loads[it].current)) }
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);tag.putIntArray("trayPorts",ports) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);val saved=tag.getIntArray("trayPorts");for(i in ports.indices)ports[i]=saved.getOrNull(i)?.takeIf { it in 0..16 } ?: 16;configure() }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);ports.forEach(stream::writeByte);resolved.forEach(stream::writeByte)
        stream.writeShort(installedColors.fold(0) { mask,i->mask or (1 shl i) })
        val peers=node!!.nodeConnectionList.fold(0) { bits,c->
            val isA=c.N1===node
            if(c.otherEndpoint(isA).element is ColorCableTrayElement)bits or (1 shl c.endpoint(isA).side.int) else bits
        }
        stream.writeByte(peers)
        loads.forEach { stream.writeDouble(it.voltage) }
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val event=super.networkUnserialize(stream)
        if(event.toInt()==80) { val value=stream.readInt();if(value in 0 until 6*17)setPort(value/17,value%17) }
        return event
    }
}
class ColorTrayContainer(player: Player,inventory: Container): BasicContainer(player,inventory,
    Array(16) { i->object: mods.eln.node.six.SixNodeItemSlot(inventory,i,8+(i%4)*18,80+(i/4)*18,1,arrayOf(ElectricalCableDescriptor::class.java),ISlotSkin.SlotSkin.medium,arrayOf(tr("Independent circuit: %1$; install one metre of intact power cable",TrayColors.label(i)))) {
        override fun mayPlace(stack: ItemStack): Boolean {
            val d=ElectricalCableDescriptor.getDescriptor(stack) as? ElectricalCableDescriptor ?: return false
            return GridCablePolicy.accepts(d) && (d !is mods.eln.sixnode.electricalcable.UtilityCableDescriptor || d.getRemainingLengthMeters(stack)>=1.0)
        }
    } })

class ColorCableTrayDescriptor: TransparentNodeDescriptor(I18N.TR_NAME(I18N.Type.NONE,"Ceiling Color Segregated Cable Tray"),ColorCableTrayElement::class.java,ColorCableTrayRender::class.java) {
    private val model by lazy { Eln.obj.getObj("ceilingcolorcabletray") }
    init { creativeTab=Eln.creativeTabCables }
    override fun mustHaveFloor()=false
    override fun mustHaveCeiling()=true
    override fun addCollisionBoxesToList(query: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {}
    fun draw(mask: Int,ports: IntArray,peers: Int=0)=preserveMatrix {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.54f,.57f,.59f);model.getPart("Case")?.drawNoBind()
            for(i in 0..15)if(mask and (1 shl i)!=0) {
                val c=DyeColor.byId(i).textureDiffuseColor
                GL11.glColor3f(((c ushr 16) and 255)/255f,((c ushr 8) and 255)/255f,(c and 255)/255f)
                model.getPart("Color$i")?.drawNoBind()
                for(side in TrayColors.ports)if(ports[side.int]==i || peers and (1 shl side.int)!=0)model.getPart("Branch${side.int}_$i")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw(0xffff,IntArray(6) { it })
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Hang below a ceiling; sixteen independent colored power circuits"))
        list.add(tr("Install one metre of cable per color in each section; matching colors join adjacent sections"))
        list.add(tr("Select a breakout color per side, or automatically follow the connected cable's paint color"))
        list.add(tr("Cable resistance is retained; tray circuits require upstream overcurrent protection"))
    }
}
class ColorCableTrayRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(16,1,this)
    var mask=0;var peers=0;val ports=IntArray(6) { 16 };val resolved=IntArray(6);val volts=DoubleArray(16)
    override fun draw()=mods.eln.client.terrain.TerrainModels.body(this,listOf(mask,resolved.toList(),peers)) { (transparentNodeDescriptor as ColorCableTrayDescriptor).draw(mask,resolved,peers) }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);repeat(6) { ports[it]=stream.readUnsignedByte() };repeat(6) { resolved[it]=stream.readUnsignedByte() };mask=stream.readUnsignedShort();peers=stream.readUnsignedByte();repeat(16) { volts[it]=stream.readDouble() } }
    override fun newGuiDraw(side: Direction,player: Player)=ColorCableTrayGui(player,this)
}
class ColorCableTrayGui(player: Player,private val render: ColorCableTrayRender): GuiContainerEln(ColorTrayContainer(player,render.inventory)) {
    private val buttons=ArrayList<GuiButtonEln>()
    override fun newHelper()=GuiHelperContainer(this,246,268,8,186)
    override fun initGui() {
        super.initGui();buttons.clear()
        TrayColors.ports.forEachIndexed { i,_->buttons+=newGuiButton(88,40+i*25,150,"") }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj);val i=buttons.indexOf(obj)
        if(i>=0) { val side=TrayColors.ports[i];render.clientSendInt(80,side.int*17+(render.ports[side.int]+1)%17) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,tr("Ceiling Cable Tray"));drawString(8,23,tr("One metre per colored circuit"))
        TrayColors.ports.forEachIndexed { i,side->buttons[i].displayString=tr("%1$: %2$",TrayColors.side(side),if(render.ports[side.int]==16)tr("Auto (wire color)") else TrayColors.label(render.ports[side.int])) }
        drawString(8,166,tr("Matching colors remain electrically separate"))
    }
}
