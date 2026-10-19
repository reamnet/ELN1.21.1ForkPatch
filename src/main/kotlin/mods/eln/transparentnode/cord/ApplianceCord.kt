package mods.eln.transparentnode.cord

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.INodeElement
import mods.eln.node.six.SixNodeElement
import mods.eln.node.NodeManager
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.*
import java.util.UUID

interface CordDevice {
    val cord: ApplianceCord
    val cordPortable: Boolean get() = false
    val cordVoltage: Double
    fun acceptsCord(rating: CordRating)=rating.accepts(cordVoltage)
    fun cordChanged() {}
}

data class CordAnchor(val dimension: Int,val pos: BlockPos,val id: UUID,val front: Direction,val rating: CordRating = CordRating.V120) {
    fun tip()=Vec3.atCenterOf(pos).add(mods.eln.transparentnode.worklight.WorkLightOptics.world(front,Vec3(0.0,-.1,-.224)))
    fun write(): CompoundTag=CompoundTag().apply { putInt("dimension",dimension);putLong("position",pos.asLong());putUUID("outletId",this@CordAnchor.id);putInt("facing",front.int);putString("rating",rating.name) }
    companion object {
        fun read(tag: CompoundTag): CordAnchor? {
            if(!tag.hasUUID("outletId"))return null
            val front=Direction.fromInt(tag.getInt("facing"))?.takeIf { it.isNotY } ?: return null
            return CordAnchor(tag.getInt("dimension"),BlockPos.of(tag.getLong("position")),tag.getUUID("outletId"),front,CordRating.read(tag.getString("rating")))
        }
    }
}

/** A passive conductor joins two real loads only on attachment changes, never on each solver pass. */
class ApplianceCord(val owner: INodeElement,val input: NbtElectricalLoad) {
    var anchor: CordAnchor?=null
        private set
    val plugged get()=anchor!=null
    var token=UUID.randomUUID()
        private set
    var paid=false
        private set
    var moving=false
    var connected=false
        private set
    var state="UNPLUGGED"
        private set
    val conductor=Resistor(null,null)
    private var outlet: OutletElement?=null
    private var timer=0.0
    private fun coordinate()=when(owner) { is TransparentNodeElement -> owner.coordinate();is SixNodeElement -> owner.coordinate!!;else -> error("Unsupported cord owner") }
    private val device get()=owner as CordDevice
    init { (when(owner) { is TransparentNodeElement -> owner.slowProcessList;is SixNodeElement -> owner.slowProcessList;else -> error("Unsupported cord owner") }).add(IProcess { dt -> timer-=dt;if(timer<=0) { timer=.5;poll() } }) }
    fun inReach(a: CordAnchor): Boolean {
        val c=coordinate()
        return c.dimension==a.dimension && Vec3.atCenterOf(c.pos).distanceTo(Vec3.atCenterOf(a.pos))<=MAX_REACH
    }
    fun attach(a: CordAnchor,consumed: Boolean): Boolean {
        if(!device.acceptsCord(a.rating) || plugged || !inReach(a))return false
        val target=find(a) ?: return false
        if(!target.canClaim(this))return false
        target.reserved=token
        anchor=a;paid=consumed;device.cordChanged();owner.reconnect();timer=0.0;poll();owner.needPublish();return true
    }
    private fun find(a: CordAnchor): OutletElement? {
        val node=NodeManager.instance?.getNodeFromCoordonate(Coordinate(a.pos.x,a.pos.y,a.pos.z,a.dimension)) as? TransparentNode
        return (node?.element as? OutletElement)?.takeIf { it.id==a.id && it.rating==a.rating && node.isDestructing==false }
    }
    fun poll() {
        val a=anchor
        val target=if(a!=null && inReach(a))find(a) else null
        val next=when {
            a==null -> "UNPLUGGED"
            !inReach(a) -> "TOO_FAR"
            target==null -> "MISSING"
            !target.canClaim(this) -> "OCCUPIED"
            !device.acceptsCord(a.rating) -> "VOLTAGE"
            target.tripped -> "TRIPPED"
            else -> "CONNECTED"
        }
        if(target!==outlet || next !in listOf("CONNECTED","TRIPPED")) {
            disconnect()
            if(target!=null && target.canClaim(this) && a!=null && inReach(a) && device.acceptsCord(a.rating)) {
                target.reserved=token;target.occupant=this;outlet=target
                conductor.connectTo(target.output,input)
                conductor.resistance=.08+.015*Vec3.atCenterOf(coordinate().pos).distanceTo(Vec3.atCenterOf(a.pos))
                conductor.setOwner("ApplianceCord@${coordinate()}")
                Eln.simulator.addElectricalComponent(conductor);connected=true
            }
        }
        if(state!=next) { state=next;owner.needPublish() }
    }
    fun disconnect() {
        if(connected) { Eln.simulator.removeElectricalComponent(conductor);conductor.connectTo(null,null) }
        if(outlet?.occupant===this)outlet!!.occupant=null
        outlet=null;connected=false
    }
    fun unplug(player: ServerPlayer) {
        val refund=paid;val item=anchor?.rating?.cordName ?: CordRating.V120.cordName
        anchor?.let(::find)?.takeIf { it.reserved==token }?.reserved=null
        disconnect();anchor=null;paid=false;state="UNPLUGGED";device.cordChanged();owner.reconnect();owner.needPublish()
        if(refund)player.inventory.placeItemBackInInventory(Eln.findItemStack(item,1))
    }
    fun onBroken() {
        val refund=paid;val item=anchor?.rating?.cordName ?: CordRating.V120.cordName
        anchor?.let(::find)?.takeIf { it.reserved==token }?.reserved=null
        disconnect();anchor=null;paid=false
        if(refund)when(owner) { is TransparentNodeElement -> owner.node?.dropItem(Eln.findItemStack(item,1));is SixNodeElement -> owner.sixNode?.dropItem(Eln.findItemStack(item,1)) }
    }
    fun carry(player: ServerPlayer): Boolean {
        if(!plugged || !device.cordPortable || !player.mainHandItem.isEmpty)return false
        if(owner is mods.eln.transparentnode.kitchen.KitchenElement && owner.detonating)return false
        val snapshot=CompoundTag()
        val stack=when(owner) {
            is TransparentNodeElement -> { owner.writeToNBT(snapshot);owner.descriptor!!.newItemStack(1) }
            is SixNodeElement -> { owner.writeToNBT(snapshot);owner.sixNodeElementDescriptor.newItemStack(1) }
            else -> return false
        }
        stack.tagCompound=CompoundTag().apply { put("carriedAppliance",snapshot);putLong("carriedAt",coordinate().world().gameTime) }
        moving=true
        when(owner) {
            is TransparentNodeElement -> owner.node!!.physicalSelfDestruction(0f)
            is SixNodeElement -> {
                val node=owner.sixNode!!
                node.deleteSubBlock(player,owner.side)
                if(!node.ifSideRemain)coordinate().world().removeBlock(coordinate().pos,false)
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        return true
    }
    fun read(tag: CompoundTag) {
        val n=tag.getCompound("applianceCord")
        if(n.hasUUID("cordId"))token=n.getUUID("cordId")
        anchor=CordAnchor.read(n);paid=anchor!=null && n.getBoolean("paid")
    }
    fun write(tag: CompoundTag) { anchor?.let { tag.put("applianceCord",it.write().apply { putBoolean("paid",paid);putUUID("cordId",token) }) } }
    fun writeNetwork(out: DataOutputStream) {
        val a=anchor;out.writeBoolean(a!=null)
        if(a!=null) { out.writeInt(a.pos.x);out.writeInt(a.pos.y);out.writeInt(a.pos.z);out.writeByte(a.front.int);out.writeByte(a.rating.ordinal) }
        out.writeUTF(state)
    }
    companion object { const val MAX_REACH=8.0 }
}

fun cordState(state: String)=when(state) {
    "CONNECTED" -> tr("Cord connected")
    "TOO_FAR" -> tr("Cord out of reach (8 blocks)")
    "MISSING" -> tr("Cord outlet missing")
    "OCCUPIED" -> tr("Outlet already occupied")
    "VOLTAGE" -> tr("Cord voltage does not match this device")
    "TRIPPED" -> tr("Outlet breaker tripped")
    else -> tr("No cord connected")
}
