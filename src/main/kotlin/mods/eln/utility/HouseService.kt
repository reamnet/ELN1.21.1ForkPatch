package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.*
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.standby.TransferContact
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream

/** Wall service devices use the same independent service channels as the pole secondary rack. */
open class ServiceWallDescriptor(name: String,model: String,element: Class<*>,render: Class<*>): GridDescriptor(name,Eln.obj.getObj(model),element,render,
    "textures/wire.png",Eln.instance.veryHighVoltageCableDescriptor,64) {
    override val frontType get()=TransparentNode.FrontType.BlockSideInv
    override fun mustHaveFloor()=false
    override fun mustHaveWallFrontInverse()=true
    override fun rotationIsFixed()=true
    override fun checkCanPlace(coord: Coordinate?,front: Direction)=if(front.isY)tr("Place this cabinet against a vertical wall") else super.checkCanPlace(coord,front)
    fun acceptsCable(front: Direction,side: Direction,lrdu: LRDU)=side.getLRDUGoingTo(front.inverse)==lrdu
    fun checkSupport(element: TransparentNodeElement) {
        val origin=element.node?.coordinate ?: return
        val world=origin.world() as? ServerLevel ?: return
        val at=origin.pos.relative(element.front.inverse.toFacing())
        if(world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)==null)return
        if(!world.getBlockState(at).isSolidRender(world,at))element.selfDestroy()
    }
    override fun addCollisionBoxesToList(query: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {
        val tile=world?.getBlockEntity(BlockPos(x,y,z)) as? TransparentNodeEntity ?: return
        val front=tile.elementRender?.front ?: (tile.node as? TransparentNode)?.element?.front ?: return
        val box=when(front) {
            Direction.XN->AABB(.67,0.0,.08,1.0,1.0,.92);Direction.XP->AABB(0.0,0.0,.08,.33,1.0,.92)
            Direction.ZN->AABB(.08,0.0,.67,.92,1.0,1.0);else->AABB(.08,0.0,0.0,.92,1.0,.33)
        }.move(x.toDouble(),y.toDouble(),z.toDouble())
        if(query.intersects(box))list.add(box)
    }
}

/** Records both directions independently; reverse flow must not erase the import register. */
class ServiceEnergy {
    var importedKwh=0.0
        private set
    var exportedKwh=0.0
        private set
    fun step(seconds: Double,watts: Double) {
        if(!seconds.isFinite() || seconds<=0 || !watts.isFinite())return
        if(watts>=0)importedKwh+=watts*seconds/3_600_000 else exportedKwh-=watts*seconds/3_600_000
    }
    fun read(tag: CompoundTag) {
        importedKwh=tag.getDouble("meterImportKwh").takeIf { it.isFinite() && it>=0 } ?: 0.0
        exportedKwh=tag.getDouble("meterExportKwh").takeIf { it.isFinite() && it>=0 } ?: 0.0
    }
    fun write(tag: CompoundTag) { tag.putDouble("meterImportKwh",importedKwh);tag.putDouble("meterExportKwh",exportedKwh) }
}

class HouseServiceElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): GridElement(node,descriptor,64) {
    val meter=(descriptor as HouseServiceDescriptor).meter
    val serviceDisconnect=(descriptor as HouseServiceDescriptor).disconnect
    private val switched get()=meter || serviceDisconnect
    override val allowsNewGridLinks get()=!switched
    override fun aimedGridTerminal(side: Direction,localHit: Vec3)=ServiceTerminals.BUNDLE
    private val desc get()=transparentNodeDescriptor as HouseServiceDescriptor
    override val inventory=object: TransparentNodeElementInventory(if(meter)1 else 0,1,this@HouseServiceElement) {
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=meter && slot==0 && UtilityItems.part(stack) in 1..2
        override fun getAccessibleSlotsFromSide(side: Int)=if(meter)intArrayOf(0) else intArrayOf()
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=meter && slot==0
    }
    val line=List(3) { NbtElectricalLoad("serviceLine$it").apply { serialResistance=.00001 } }
    private val wiredService=if(meter || serviceDisconnect)WiredServiceSplit(this,line) else null
    val load=if(switched)List(3) { NbtElectricalLoad("serviceLoad$it").apply { serialResistance=.00001 } } else line
    private val contacts=if(switched)List(3) { TransferContact(line[it],load[it]) } else emptyList()
    val energy=ServiceEnergy()
    var selectedTerminal=50
        private set
    var selectedCircuit=2
        private set
    var disconnected=false
        private set
    val meterType get()=if(meter)UtilityItems.part(inventory.getItem(0))?.takeIf { it in 1..2 } ?: 0 else 0
    val terminals get()=if(meter)listOf(50,51,40,41,42) else listOf(50,40,41,42)
    init {
        electricalLoadList.addAll(line);if(switched)electricalLoadList.addAll(load);electricalComponentList.addAll(contacts)
        electricalProcessList.add(IProcess { dt ->
            wiredService?.update()
            if(meter)energy.step(dt,contacts.indices.sumOf { i -> line[i].voltage*contacts[i].current })
        })
        slowProcessList.add(IProcess { desc.checkSupport(this) })
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
    }
    private fun configure() { contacts.forEach { it.closed=if(serviceDisconnect)!disconnected else meterType!=0 && !(meterType==2 && disconnected) } }
    /** Stable hook for a future remote metering integration; analog meters have no disconnect. */
    fun setRemoteDisconnect(open: Boolean): Boolean {
        if(!serviceDisconnect && meterType!=2)return false
        if(disconnected!=open) { disconnected=open;configure();play(mods.eln.sound.SoundCommand("eln:circuit_breaker_close").smallRange().mulVolume(.25));needPublish() }
        return true
    }
    override fun initialize() { configure();super.initialize() }
    override fun inventoryChange(inventory: Container?) { if(meter && meterType!=2)disconnected=false;configure();needPublish() }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        if(serviceDisconnect && player.mainHandItem.isEmpty && !player.isShiftKeyDown) { setRemoteDisconnect(!disconnected);return true }
        if(meter && meterType==0 && UtilityItems.part(player.mainHandItem) in 1..2) {
            inventory.setItem(0,player.mainHandItem.copyWithCount(1));if(!player.abilities.instabuild)player.mainHandItem.shrink(1)
            player.inventory.setChanged();inventoryChange(inventory);return true
        }
        return super.onBlockActivated(player,side,vx,vy,vz)
    }
    override fun selectedGridTerminal(side: Direction)=selectedTerminal
    override fun getGridElectricalLoad(side: Direction)=line[selectedCircuit]
    override fun gridLoads(terminal: Int): List<ElectricalLoad> = when(terminal) {
        50->line;51->if(meter)load else emptyList();40,41,42->listOf(load[terminal-40]);in 0..5->listOf(line[selectedCircuit]);else->emptyList()
    }
    override fun gridCircuitKey(terminal: Int)=if(terminal<6)"LEGACY" else if(terminal==51)"SERVICE_BUNDLE" else ServiceTerminals.key(terminal)
    override fun renderWireCount(terminal: Int)=if(terminal in 50..51)3 else 1
    override fun gridCablePoint(terminal: Int,wire: Int): Vec3 {
        val i=if(terminal in 50..51)wire else (terminal-40).coerceIn(0,2)
        // Grid uses XN-reference; the cabinet model uses a separate Z-facing reference.
        return Vec3(if(!meter)desc.mast.headX else .36,if(!meter)desc.mast.headY else if(terminal==50).38 else -.40,if(!meter)desc.mast.headZ+(i-1)*desc.mast.spacing else (i-1)*.17)
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): ElectricalLoad? {
        if(!desc.acceptsCable(front,side,lrdu))return null
        return when(side) {
            Direction.YP->line[selectedCircuit]
            Direction.YN->load[selectedCircuit]
            front.left()->load[0]
            front.right()->load[1]
            else->null
        }
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=HouseServiceContainer(player,inventory,meter)
    override fun multiMeterString(side: Direction)=tr("Service: %1$ / %2$ / %3$",Utils.plotVolt("",load[0].voltage).trim(),Utils.plotVolt("",load[1].voltage).trim(),Utils.plotVolt("",load[2].voltage).trim())
    override fun thermoMeterString(side: Direction)=""
    override fun getWaila()=linkedMapOf(tr("Service") to multiMeterString(front)).apply {
        if(meter) {
            put(tr("Imported energy"),tr("%1$ kWh",String.format(java.util.Locale.ROOT,"%.3f",energy.importedKwh)))
            put(tr("Exported energy"),tr("%1$ kWh",String.format(java.util.Locale.ROOT,"%.3f",energy.exportedKwh)))
        }
    }
    private fun writeSettings(nbt: CompoundTag) { energy.write(nbt);nbt.putInt("serviceTerminal",selectedTerminal);nbt.putInt("serviceCircuit",selectedCircuit);nbt.putBoolean("meterDisconnected",disconnected) }
    private fun readSettings(nbt: CompoundTag) { energy.read(nbt);selectedTerminal=nbt.getInt("serviceTerminal").takeIf { it in terminals } ?: 50;selectedCircuit=nbt.getInt("serviceCircuit").takeIf { it in 0..2 } ?: 2;disconnected=nbt.getBoolean("meterDisconnected");configure() }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeSettings(nbt) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);readSettings(nbt) }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)readSettings(nbt) }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);stream.writeInt(selectedTerminal);stream.writeInt(selectedCircuit);stream.writeInt(meterType);stream.writeBoolean(disconnected);stream.writeDouble(energy.importedKwh);stream.writeDouble(energy.exportedKwh)
        load.forEach { stream.writeDouble(it.voltage) };stream.writeDouble(contacts.indices.sumOf { i->line[i].voltage*contacts[i].current });stream.writeBoolean(line.any { it.voltage>20 });node!!.lrduCubeMask.getTranslate(front.inverse).serialize(stream)
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            87->stream.readInt().takeIf { it in terminals }?.let { selectedTerminal=it }
            88->stream.readInt().takeIf { it in 0..2 }?.let { selectedCircuit=it;reconnect() }
            89->setRemoteDisconnect(stream.readBoolean())
        }
        needPublish();return id
    }
}
class HouseServiceContainer(player: Player,inventory: Container,meter: Boolean): BasicContainer(player,inventory,
    if(meter)arrayOf(object: SlotWithSkinAndComment(inventory,0,9,31,ISlotSkin.SlotSkin.medium,arrayOf(tr("Analog or smart service meter"))) {
        override fun mayPlace(stack: ItemStack)=UtilityItems.part(stack) in 1..2
        override fun getMaxStackSize()=1
    }) else emptyArray<Slot>())
