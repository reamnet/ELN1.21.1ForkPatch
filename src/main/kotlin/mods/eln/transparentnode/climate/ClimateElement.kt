package mods.eln.transparentnode.climate

import mods.eln.environment.RoomThermalManager
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs

class ClimateElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor),IConfigurable,mods.eln.transparentnode.cord.CordDevice {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val type=(descriptor as ClimateDescriptor).type
    private val cabinet get()=transparentNodeDescriptor as ClimateDescriptor
    val control=ClimateControl(type.cooling)
    val load=NbtElectricalLoad("climateInput").apply { serialResistance=.002 }
    override val cord=mods.eln.transparentnode.cord.ApplianceCord(this,load)
    override val cordVoltage get()=type.volts
    override val cordPortable get()=type==ClimateType.PORTABLE || type==ClimateType.PATIO
    val resistor=Resistor(load,null).apply { resistance=1e19 }
    val indoor=NbtThermalLoad("climateIndoor").apply { set(.2,.012,4000.0);setAsSlow() }
    val outdoor=NbtThermalLoad("climateOutdoor").apply { set(.2,.006,6000.0);setAsSlow() }
    var roomTemperature=20.0
        private set
    var roomDetected=false
        private set
    var active=false
        private set
    var watts=0.0
        private set
    var status="NO_POWER"
        private set
    private val heatProcess=IProcess {
        watts=if(resistor.resistance<1e15)resistor.power.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0 else 0.0
        val heat=control.heat(watts)
        indoor.movePowerTo(heat.first)
        if(type.cooling)outdoor.movePowerTo(heat.second)
    }
    init {
        electricalLoadList.add(load);electricalComponentList.add(resistor)
        thermalLoadList.add(indoor);if(type.cooling)thermalLoadList.add(outdoor)
        slowProcessList.add(IProcess(::tick))
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    override fun initialize() { connect() }
    override fun connectJob() {
        super.connectJob()
        mods.eln.Eln.simulator.addThermalSlowProcess(heatProcess)
        // Put the AC evaporator in front of its supporting wall, and reject heat
        // behind it. The two coils must never heat and cool the same room anchor.
        val c=coordinate()
        if(type.cooling) {
            val inside=c.pos.relative(front.toFacing())
            val outside=c.pos.relative(front.inverse.toFacing(),2)
            indoor.setSimCoordinate(c.dimension,inside.x,inside.y,inside.z)
            outdoor.setSimCoordinate(c.dimension,outside.x,outside.y,outside.z)
        }
    }
    override fun disconnectJob() {
        mods.eln.Eln.simulator.removeThermalSlowProcess(heatProcess)
        super.disconnectJob()
    }
    private fun tick(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        val c=coordinate();val pos=if(type.cooling)c.pos.relative(front.toFacing()) else c.pos.above()
        val room=RoomThermalManager.getRoomAt(c.world(),pos.x,pos.y,pos.z)
        roomDetected=room!=null
        roomTemperature=getAmbientTemperatureCelsius()+(room?.temperatureCelsius ?: 0.0)
        val requested=control.update(roomTemperature)
        val voltage=abs(load.voltage)
        val protected=voltage>type.volts*1.2 || abs(indoor.temperatureCelsius)>110.0 || outdoor.temperatureCelsius>140.0
        resistor.resistance=if(requested && !protected)type.volts*type.volts/type.watts else 1e19
        active=requested && !protected && voltage>=type.volts*.8
        // Power telemetry comes from the accepted thermal-step sample above,
        // including the real (reduced) heating power during a brownout.
        val next=when {
            !control.enabled -> "OFF"
            protected -> "PROTECTED"
            requested && !active -> "NO_POWER"
            !requested -> "SATISFIED"
            type.cooling -> "COOLING"
            else -> "HEATING"
        }
        if(next!=status) { status=next;needPublish() }
    }
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();cabinet.checkWallSupport(this) }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!cord.plugged && cabinet.acceptsCable(front,side,lrdu))load else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=if(side==front && getElectricalLoad(side,lrdu)!=null)indoor else null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        try {
            when(id.toInt()) { 80 -> control.setTarget(stream.readDouble());81 -> control.enabled=!control.enabled }
            if(id.toInt() in 80..81)needPublish()
        } catch(_: IOException) {}
        return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeDouble(control.target);stream.writeBoolean(control.enabled);stream.writeBoolean(active)
        stream.writeDouble(roomTemperature);stream.writeDouble(watts);stream.writeUTF(status);stream.writeBoolean(roomDetected)
        stream.writeDouble(indoor.temperatureCelsius+getAmbientTemperatureCelsius())
        stream.writeDouble(outdoor.temperatureCelsius+getAmbientTemperatureCelsius())
        node!!.lrduCubeMask.getTranslate(cabinet.connectionPlane(front)).serialize(stream)
    }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);control.read(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);control.write(nbt) }
    override fun readItemStackNBT(nbt: CompoundTag?) {
        if(nbt==null)return
        if(nbt.contains("carriedAppliance")) { val facing=front;readFromNBT(nbt.getCompound("carriedAppliance"));front=facing } else control.read(nbt)
    }
    override fun onBreakElement() { if(cord.moving) { if(useUuid())stop(getUuid());return };super.onBreakElement() }
    override fun getItemStackNBT()=CompoundTag().also(control::write)
    override fun readConfigTool(compound: CompoundTag,invoker: Player) { control.read(compound);needPublish() }
    override fun writeConfigTool(compound: CompoundTag,invoker: Player)=control.write(compound)
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",load.voltage)+Utils.plotPower("P:",watts)
    override fun thermoMeterString(side: Direction)=plotAmbientCelsius("T:",indoor.temperatureCelsius)
    override fun getWaila()=linkedMapOf(tr("State") to climateStatus(status),tr("Power") to Utils.plotPower("",watts),tr("Thermostat") to tr("%1$ C",control.target.toInt()))
}
