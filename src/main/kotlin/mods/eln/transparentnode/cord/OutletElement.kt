package mods.eln.transparentnode.cord

import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.*
import java.util.UUID

/** Passive receptacles: overcurrent protection belongs to the upstream branch breaker. */
class OutletElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    private val desc get()=transparentNodeDescriptor as OutletDescriptor
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val rating=(descriptor as OutletDescriptor).rating
    val capacity get()=desc.sockets
    var wallState=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
        private set
    val recessed get()=!wallState.isAir
    override fun readItemStackNBT(nbt: CompoundTag?) {
        super.readItemStackNBT(nbt)
        if(nbt?.contains("outletWall")==true)wallState=net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),nbt.getCompound("outletWall"))
    }
    override fun checkCanStay(onCreate: Boolean) { if(!recessed)super.checkCanStay(onCreate) }
    var horizontal=false
        private set
    var wireless=false
    var channel=mods.eln.sixnode.lampsocket.LampChannelMemory.DEFAULT_CHANNEL
    val wirelessPower=WirelessOutletPower(this)
    private var supplyTimer=0.0
    val input=NbtElectricalLoad("outletInput").apply { serialResistance=.002 }
    val output=NbtElectricalLoad("outletOutput").apply { serialResistance=.002 }
    val contact=Resistor(input,output).apply { resistance=.005 }
    // Retain the internal accessor for integrations; it no longer implements a breaker.
    val breaker get()=contact
    val tripped get()=false
    override val cord=ApplianceCord(this,input)
    override val cordVoltage get()=rating.volts
    override fun acceptsCord(rating: CordRating)=desc.strip && rating==this.rating
    override fun cordChanged() { wirelessPower.disconnect();reconnect();supplyTimer=0.0 }
    var id=UUID.randomUUID()
        private set
    private val revoked=HashSet<UUID>()
    private val attached=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ApplianceCord,Boolean>())
    private val occupants=arrayOfNulls<ApplianceCord>(capacity)
    private val reservations=arrayOfNulls<UUID>(capacity)
    var occupant: ApplianceCord?
        get()=occupants[0]
        set(value) { occupants[0]=value }
    var reserved: UUID?
        get()=reservations[0]
        set(value) { reservations[0]=value }
    fun isOccupied(slot: Int)=slot in occupants.indices && (occupants[slot]!=null || reservations[slot]!=null)
    fun freeSocket()=(0 until capacity).firstOrNull { !isOccupied(it) }
    fun canClaim(cord: ApplianceCord,slot: Int=cord.anchor?.socket ?: 0)=slot in occupants.indices && cord.token !in revoked &&
        (reservations[slot]==null || reservations[slot]==cord.token) && (occupants[slot]==null || occupants[slot]===cord)
    fun reserve(cord: ApplianceCord,slot: Int): Boolean {
        if(!canClaim(cord,slot))return false
        attached.removeIf { it!==cord && it.token==cord.token }
        attached.add(cord);reservations[slot]=cord.token;needPublish();return true
    }
    fun claim(cord: ApplianceCord,slot: Int) { check(reserve(cord,slot));occupants[slot]=cord;needPublish() }
    fun release(cord: ApplianceCord,forget: Boolean=false) {
        for(i in occupants.indices) {
            if(occupants[i]===cord)occupants[i]=null
            if(forget && reservations[i]==cord.token)reservations[i]=null
        };if(forget)attached.remove(cord);needPublish()
    }
    fun disconnectSocket(slot: Int,player: net.minecraft.server.level.ServerPlayer?=null) {
        if(slot !in occupants.indices)return
        attached.filter { it.anchor?.socket==slot }.toList().forEach { if(it.moving)revoked.add(it.token);it.detach(player) }
        reservations[slot]=null;needPublish()
    }
    override fun onBreakElement() { attached.toList().forEach { it.detach() };attached.clear();super.onBreakElement() }
    private fun releaseAll() { attached.toList().forEach { it.detach() };attached.clear();reservations.fill(null);id=UUID.randomUUID();needPublish() }
    init {
        electricalLoadList.add(input);electricalLoadList.add(output);electricalComponentList.add(contact)
        slowProcessList.add(IProcess { dt -> supplyTimer-=dt;if(supplyTimer<=0) { supplyTimer=.5;wirelessPower.refresh() } })
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    override fun initialize() { connect() }
    fun anchor(socket: Int=freeSocket() ?: 0)=CordAnchor(coordinate().dimension,coordinate().pos,id,front,rating,socket,horizontal,desc.strip,recessed)
    fun wouldLoop(upstream: OutletElement): Boolean {
        val visited=HashSet<UUID>();var next: OutletElement?=upstream
        while(next!=null) {
            if(next===this || !visited.add(next.id))return true
            next=next.cord.anchor?.let { ApplianceCord.findOutlet(it) }
        };return false
    }
    override fun disconnectJob() { occupants.filterNotNull().toList().forEach { it.disconnect() };cord.disconnect();wirelessPower.disconnect();super.disconnectJob() }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!wireless && !cord.plugged &&
        (desc.acceptsCable(front,side,lrdu) || recessed && side==front && OutletWiring.surfaceEndpoint(this,lrdu)!=null))input else null
    override fun getElectricalLoad(side: Direction,lrdu: LRDU,remote: mods.eln.node.NodeConnectionEndpoint): mods.eln.sim.ElectricalLoad? {
        // A machine immediately in front must still use its plug, not accidentally
        // acquire power through the new wall-face terminal mask.
        if(recessed && side==front && remote!=OutletWiring.surfaceEndpoint(this,lrdu))return null
        return getElectricalLoad(side,lrdu)
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();if(!recessed)desc.checkWallSupport(this) }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val b=super.networkUnserialize(stream)
        when(b.toInt()) {
            81 -> releaseAll()
            82 -> { wireless=!wireless;wirelessPower.disconnect();reconnect();supplyTimer=0.0;needPublish() }
            83 -> { channel=stream.readUTF().take(128);wirelessPower.disconnect();supplyTimer=0.0;needPublish() }
            84 -> { horizontal=!horizontal;needPublish() }
            85 -> disconnectSocket(stream.readInt())
        };return b
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeLong(id.mostSignificantBits);out.writeLong(id.leastSignificantBits)
        out.writeBoolean(wireless);out.writeUTF(channel);out.writeBoolean(wirelessPower.supply!=null)
        out.writeInt(net.minecraft.world.level.block.Block.getId(wallState));out.writeBoolean(horizontal);out.writeInt((0 until capacity).fold(0) { bits,i -> if(isOccupied(i))bits or (1 shl i) else bits })
        out.writeDouble(input.voltage);out.writeDouble(contact.current)
        (if(recessed)node!!.lrduCubeMask[front]!! else node!!.lrduCubeMask.getTranslate(front.inverse)).serialize(out)
    }
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt);wireless=nbt.getBoolean("outletWireless");channel=nbt.getString("outletChannel").ifBlank { mods.eln.sixnode.lampsocket.LampChannelMemory.DEFAULT_CHANNEL }
        if(nbt.hasUUID("outletId"))id=nbt.getUUID("outletId")
        if(nbt.contains("outletWall"))wallState=net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),nbt.getCompound("outletWall"))
        revoked.clear();nbt.getList("outletRevoked",net.minecraft.nbt.Tag.TAG_INT_ARRAY.toInt()).forEach { tag -> runCatching { net.minecraft.nbt.NbtUtils.loadUUID(tag) }.getOrNull()?.let(revoked::add) }
        horizontal=nbt.getBoolean("outletHorizontal")
        for(i in reservations.indices) { val key=if(i==0)"outletReservation" else "outletReservation$i";reservations[i]=if(nbt.hasUUID(key))nbt.getUUID(key) else null }
    }
    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt);nbt.putBoolean("outletWireless",wireless);nbt.putString("outletChannel",channel);nbt.putUUID("outletId",id);nbt.putBoolean("outletHorizontal",horizontal);if(recessed)nbt.put("outletWall",net.minecraft.nbt.NbtUtils.writeBlockState(wallState))
        nbt.put("outletRevoked",net.minecraft.nbt.ListTag().also { list -> revoked.forEach { list.add(net.minecraft.nbt.NbtUtils.createUUID(it)) } })
        reservations.forEachIndexed { i,r -> val key=if(i==0)"outletReservation" else "outletReservation$i";nbt.remove(key);if(r!=null)nbt.putUUID(key,r) }
    }
    override fun multiMeterString(side: Direction)=Utils.plotUIP(input.voltage,contact.current)
    override fun getWaila()=mapOf(tr("Sockets") to tr("%1$ / %2$ occupied",(0 until capacity).count(::isOccupied),capacity),tr("Voltage") to Utils.plotVolt("",input.voltage))
}
