package mods.eln.transparentnode.renewable

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class SolarElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor),IConfigurable {
    val unit=SolarPowerUnit(this,(descriptor as RenewableDescriptor).kind==RenewableKind.HYBRID)
    override val inventory=TransparentNodeElementInventory(0,1,this)
    init { slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0)) }
    override fun initialize() { connect() }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun connectJob() { super.connectJob();Eln.simulator.mna.addProcess(unit.controller) }
    override fun disconnectJob() { Eln.simulator.mna.removeProcess(unit.controller);super.disconnectJob() }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(lrdu!=LRDU.Down)null else when(side) {
        front.left() -> unit.solar
        front.inverse -> unit.negative
        front.right() -> unit.output
        front -> if(unit.hybrid)unit.grid else null
        else -> null
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);unit.read(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);unit.write(nbt) }
    // A dropped item can retain settings, but cannot copy stored electrical energy.
    override fun getItemStackNBT()=CompoundTag().also { unit.write(it);it.putDouble("solarJoules",0.0) }
    override fun readItemStackNBT(nbt: CompoundTag?) { nbt?.copy()?.also { it.putDouble("solarJoules",0.0);unit.read(it) } }
    override fun writeConfigTool(compound: CompoundTag,invoker: Player) {
        compound.putDouble("solarTarget",unit.target);compound.putBoolean("solarEnabled",unit.enabled);compound.putInt("solarPriority",unit.priority.ordinal)
    }
    override fun readConfigTool(compound: CompoundTag,invoker: Player) {
        unit.setVoltage(compound.getDouble("solarTarget"));unit.enabled=compound.getBoolean("solarEnabled")
        unit.priority=SolarPriority.entries.getOrNull(compound.getInt("solarPriority")) ?: SolarPriority.SOLAR_FIRST;needPublish()
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        try {
            when(id.toInt()) {
                80 -> unit.setVoltage(stream.readDouble())
                81 -> unit.enabled=!unit.enabled
                82 -> if(unit.hybrid)unit.priority=SolarPriority.entries[(unit.priority.ordinal+1)%SolarPriority.entries.size]
            }
        } catch(_: IOException) {}
        needPublish();return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        SolarDisplay().apply {
            target=unit.target;enabled=unit.enabled;priority=unit.priority.ordinal;status=unit.status
            pv=unit.solar.voltage-unit.negative.voltage;pvWatts=unit.pvWatts;grid=unit.grid.voltage;gridWatts=unit.gridWatts
            output=unit.output.voltage;watts=unit.outputWatts;energy=unit.energy
            connections=node!!.lrduCubeMask.getTranslate(Direction.YN)
        }.write(stream)
    }
    override fun multiMeterString(side: Direction)=tr("Solar: %1$; grid: %2$; output: %3$",Utils.plotPower("",unit.pvWatts),Utils.plotPower("",unit.gridWatts),Utils.plotUIP(unit.output.voltage,unit.outputSource.current))
    override fun getWaila()=linkedMapOf(tr("Solar power") to Utils.plotPower("",unit.pvWatts),tr("Grid power") to Utils.plotPower("",unit.gridWatts),tr("Output voltage") to Utils.plotVolt("",unit.output.voltage),tr("Operating state") to solarStatus(unit.status))
}
