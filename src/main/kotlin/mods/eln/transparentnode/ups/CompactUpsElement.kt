package mods.eln.transparentnode.ups

import mods.eln.node.transparent.*
import mods.eln.transparentnode.cord.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/** Four individually upgradeable cells and four real, independently occupied receptacles. */
class CompactUpsElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): UpsElement(node,descriptor),CordSocketSource {
    override val socketOwner get()=this
    override var id=UUID.randomUUID();private set
    override val capacity=4
    override val output get()=unit.secondary
    override val rating get()=if(unit.outputVoltage==240.0)CordRating.V240 else CordRating.V120
    private val occupants=arrayOfNulls<ApplianceCord>(4)
    private val reservations=arrayOfNulls<UUID>(4)
    private val attached=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ApplianceCord,Boolean>())
    private val revoked=HashSet<UUID>()
    override fun isOccupied(slot: Int)=slot in 0..3 && (occupants[slot]!=null || reservations[slot]!=null)
    override fun canClaim(cord: ApplianceCord,slot: Int)=slot in 0..3 && cord.token !in revoked &&
        (reservations[slot]==null || reservations[slot]==cord.token) && (occupants[slot]==null || occupants[slot]===cord)
    override fun reserve(cord: ApplianceCord,slot: Int): Boolean {
        if(!canClaim(cord,slot))return false
        attached.removeIf { it!==cord && it.token==cord.token };attached.add(cord)
        reservations[slot]=cord.token;needPublish();return true
    }
    override fun claim(cord: ApplianceCord,slot: Int) { check(reserve(cord,slot));occupants[slot]=cord;needPublish() }
    override fun release(cord: ApplianceCord,forget: Boolean) {
        for(i in 0..3) { if(occupants[i]===cord)occupants[i]=null;if(forget && reservations[i]==cord.token)reservations[i]=null }
        if(forget)attached.remove(cord);needPublish()
    }
    override fun disconnectSocket(slot: Int,player: ServerPlayer?) {
        if(slot !in 0..3)return
        attached.filter { it.anchor?.socket==slot }.toList().forEach { if(it.moving)revoked.add(it.token);it.detach(player) }
        reservations[slot]=null;needPublish()
    }
    override fun anchor(socket: Int)=CordAnchor(coordinate().dimension,coordinate().pos,id,front,rating,socket,cabinet=true)
    override fun disconnectJob() { occupants.filterNotNull().toList().forEach { it.disconnect() };super.disconnectJob() }
    override fun onBreakElement() { attached.toList().forEach { it.detach() };super.onBreakElement() }
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        if(nbt.hasUUID("upsSocketId"))id=nbt.getUUID("upsSocketId")
        for(i in 0..3)reservations[i]=if(nbt.hasUUID("upsSocket$i"))nbt.getUUID("upsSocket$i") else null
        revoked.clear();nbt.getList("upsRevoked",11).forEach { runCatching { net.minecraft.nbt.NbtUtils.loadUUID(it) }.getOrNull()?.let(revoked::add) }
    }
    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt);nbt.putUUID("upsSocketId",id)
        reservations.forEachIndexed { i,r -> nbt.remove("upsSocket$i");if(r!=null)nbt.putUUID("upsSocket$i",r) }
        nbt.put("upsRevoked",net.minecraft.nbt.ListTag().also { l -> revoked.forEach { l.add(net.minecraft.nbt.NbtUtils.createUUID(it)) } })
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeInt((0..3).fold(0) { bits,i -> if(isOccupied(i))bits or (1 shl i) else bits })
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        if(id.toInt()==83)cord.detach()
        return id
    }
    init {
        unit.allowVoltageChange={ (0..3).none(::isOccupied) }
        unit.allowBypass={ (cord.anchor?.rating?.volts ?: unit.primary.voltage.takeIf { it>1 }?.let { if(it<180)120.0 else 240.0 } ?: unit.outputVoltage)==unit.outputVoltage }
    }
}
