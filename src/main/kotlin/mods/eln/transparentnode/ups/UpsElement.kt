package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream

class UpsElement(node: TransparentNode, descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor), IConfigurable {
    override val inventory=UpsInventory(this)
    val bank=UpsBatteryBank(inventory)
    val unit=UpsPowerUnit(this,bank)
    private val alarm=UpsAlarm()
    init {
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
        slowProcessList.add(mods.eln.sim.IProcess { seconds ->
            if(alarm.step(seconds,unit.status,unit.enabled && bank.activeCount>0))
                play(mods.eln.sound.SoundCommand("eln:beep_error").smallRange().mulVolume(.45f,if(unit.status=="BACKUP")1.5f else 1f))
        })
    }
    override fun initialize() { bank.invalidate();connect() }
    override fun connectJob() { super.connectJob();unit.controllers.forEach(Eln.simulator.mna::addProcess) }
    override fun disconnectJob() { unit.controllers.forEach(Eln.simulator.mna::removeProcess);super.disconnectJob() }
    private val cabinet get()=transparentNodeDescriptor as UpsDescriptor
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();cabinet.checkWallSupport(this) }
    override fun getElectricalLoad(side: Direction, lrdu: LRDU)=if(!cabinet.acceptsCable(front,side,lrdu))null else when(side) {
        front.left() -> unit.primary
        front.right() -> unit.secondary
        else -> null
    }
    override fun getConnectionMask(side: Direction, lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction, lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)unit.thermal else null
    override fun newContainer(side: Direction, player: Player)=UpsContainer(player,inventory)
    override fun hasGui()=true
    override fun inventoryChange(inventory: Container?) { bank.invalidate();needPublish() }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);bank.invalidate();unit.settings(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { bank.flush();super.writeToNBT(nbt);unit.write(nbt) }
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)unit.settings(nbt) }
    override fun getItemStackNBT()=CompoundTag().also(unit::write)
    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        if(compound.contains("upsOutputVoltage")) { unit.settings(compound);needPublish() }
    }
    override fun writeConfigTool(compound: CompoundTag, invoker: Player) { unit.write(compound) }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        if(unit.packet(id,stream))needPublish()
        return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeDouble(unit.outputVoltage);stream.writeBoolean(unit.enabled)
        stream.writeInt(bank.installed);stream.writeInt(bank.activeCount)
        stream.writeDouble(bank.energy);stream.writeDouble(bank.capacity)
        stream.writeDouble(unit.primary.voltage);stream.writeDouble(unit.secondary.voltage)
        stream.writeDouble(unit.inputWatts);stream.writeDouble(unit.outputWatts);stream.writeUTF(unit.status)
        stream.writeBoolean(unit.bypass)
        node!!.lrduCubeMask.getTranslate(cabinet.connectionPlane(front)).serialize(stream)
    }
    override fun multiMeterString(side: Direction)=tr("Input: %1$; output: %2$",Utils.plotVolt("",unit.primary.voltage),Utils.plotVolt("",unit.secondary.voltage))
    override fun getWaila()=linkedMapOf(
        tr("UPS status") to upsStatusText(unit.status),
        tr("Installed batteries") to tr("%1$/48 (%2$ active)",bank.installed,bank.activeCount),
        tr("Stored energy") to Utils.plotEnergy("",bank.energy),
        tr("Output voltage") to Utils.plotVolt("",unit.secondary.voltage),
        tr("Output power") to Utils.plotPower("",unit.outputWatts))
}
