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
import kotlin.math.abs

class OutletElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val rating=(descriptor as OutletDescriptor).rating
    var wireless=false
    var channel=mods.eln.sixnode.lampsocket.LampChannelMemory.DEFAULT_CHANNEL
    val wirelessPower=WirelessOutletPower(this)
    private var supplyTimer=0.0
    val input=NbtElectricalLoad("outletInput").apply { serialResistance=.002 }
    val output=NbtElectricalLoad("outletOutput")
    val breaker=Resistor(input,output).apply { resistance=.025 }
    var id=UUID.randomUUID()
        private set
    var occupant: ApplianceCord?=null
    var reserved: UUID?=null
    fun canClaim(cord: ApplianceCord)=(reserved==null || reserved==cord.token) && (occupant==null || occupant===cord)
    var tripped=false
        private set
    private var overload=0.0
    init {
        electricalLoadList.add(input);electricalLoadList.add(output);electricalComponentList.add(breaker)
        slowProcessList.add(IProcess { dt ->
            supplyTimer-=dt;if(supplyTimer<=0) { supplyTimer=.5;wirelessPower.refresh() }
            overload=if(abs(breaker.current)>20.0)overload+dt else 0.0
            if(!tripped && (abs(input.voltage)>rating.volts*1.2 || overload>=.25)) { tripped=true;breaker.resistance=1e12;needPublish() }
        })
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    override fun initialize() { breaker.resistance=if(tripped)1e12 else .025;connect() }
    fun anchor()=CordAnchor(coordinate().dimension,coordinate().pos,id,front,rating)
    override fun disconnectJob() { occupant?.disconnect();wirelessPower.disconnect();super.disconnectJob() }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!wireless && (transparentNodeDescriptor as OutletDescriptor).acceptsCable(front,side,lrdu))input else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();(transparentNodeDescriptor as OutletDescriptor).checkWallSupport(this) }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val b=super.networkUnserialize(stream)
        if(b.toInt()==80) { tripped=false;overload=0.0;breaker.resistance=.025;needPublish() }
        if(b.toInt()==81) { occupant?.disconnect();reserved=null;id=UUID.randomUUID();needPublish() }
        if(b.toInt()==82) { wireless=!wireless;wirelessPower.disconnect();reconnect();supplyTimer=0.0;needPublish() }
        if(b.toInt()==83) { channel=stream.readUTF().take(128);wirelessPower.disconnect();supplyTimer=0.0;needPublish() }
        return b
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeLong(id.mostSignificantBits);out.writeLong(id.leastSignificantBits)
        out.writeBoolean(wireless);out.writeUTF(channel);out.writeBoolean(wirelessPower.supply!=null);out.writeBoolean(occupant!=null);out.writeBoolean(tripped);out.writeDouble(input.voltage);out.writeDouble(breaker.current)
        node!!.lrduCubeMask.getTranslate(front.inverse).serialize(out)
    }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);wireless=nbt.getBoolean("outletWireless");channel=nbt.getString("outletChannel").ifBlank { mods.eln.sixnode.lampsocket.LampChannelMemory.DEFAULT_CHANNEL };if(nbt.hasUUID("outletId"))id=nbt.getUUID("outletId");tripped=nbt.getBoolean("outletTripped");reserved=if(nbt.hasUUID("outletReservation"))nbt.getUUID("outletReservation") else null }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);nbt.putBoolean("outletWireless",wireless);nbt.putString("outletChannel",channel);nbt.putUUID("outletId",id);nbt.putBoolean("outletTripped",tripped);reserved?.let { nbt.putUUID("outletReservation",it) } }
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",input.voltage)+Utils.plotAmpere("I:",breaker.current)
    override fun getWaila()=mapOf(tr("State") to if(tripped)tr("Breaker tripped") else tr("Ready"),tr("Voltage") to Utils.plotVolt("",input.voltage))
}
