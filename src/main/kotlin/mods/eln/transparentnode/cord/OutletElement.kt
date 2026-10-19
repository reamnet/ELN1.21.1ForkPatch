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
            overload=if(abs(breaker.current)>20.0)overload+dt else 0.0
            if(!tripped && (abs(input.voltage)>264 || overload>=.25)) { tripped=true;breaker.resistance=1e12;needPublish() }
        })
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    override fun initialize() { breaker.resistance=if(tripped)1e12 else .025;connect() }
    fun anchor()=CordAnchor(coordinate().dimension,coordinate().pos,id,front)
    override fun disconnectJob() { occupant?.disconnect();super.disconnectJob() }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if((transparentNodeDescriptor as OutletDescriptor).acceptsCable(front,side,lrdu))input else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();(transparentNodeDescriptor as OutletDescriptor).checkWallSupport(this) }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val b=super.networkUnserialize(stream)
        if(b.toInt()==80) { tripped=false;overload=0.0;breaker.resistance=.025;needPublish() }
        if(b.toInt()==81) { occupant?.disconnect();reserved=null;id=UUID.randomUUID();needPublish() }
        return b
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeLong(id.mostSignificantBits);out.writeLong(id.leastSignificantBits)
        out.writeBoolean(tripped);out.writeDouble(input.voltage);out.writeDouble(breaker.current)
        node!!.lrduCubeMask.getTranslate(front.inverse).serialize(out)
    }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);if(nbt.hasUUID("outletId"))id=nbt.getUUID("outletId");tripped=nbt.getBoolean("outletTripped");reserved=if(nbt.hasUUID("outletReservation"))nbt.getUUID("outletReservation") else null }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);nbt.putUUID("outletId",id);nbt.putBoolean("outletTripped",tripped);reserved?.let { nbt.putUUID("outletReservation",it) } }
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",input.voltage)+Utils.plotAmpere("I:",breaker.current)
    override fun getWaila()=mapOf(tr("State") to if(tripped)tr("Breaker tripped") else tr("Ready"),tr("Voltage") to Utils.plotVolt("",input.voltage))
}
