package mods.eln.transparentnode.standby

import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.misc.BasicContainer
import mods.eln.sim.IProcess
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.fluids.FluidUtil
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

abstract class BackupElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor),IConfigurable {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    init { slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0)) }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun initialize() { connect() }
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    abstract fun settings(nbt: CompoundTag)
    abstract fun writeSettings(nbt: CompoundTag)
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);settings(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeSettings(nbt) }
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)settings(nbt) }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun writeConfigTool(compound: CompoundTag,invoker: Player) { writeSettings(compound) }
    override fun readConfigTool(compound: CompoundTag,invoker: Player) { settings(compound);needPublish() }
}

class AtsElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : BackupElement(node,descriptor) {
    val unit=AtsUnit(this)
    private fun serverWorld()=node?.coordinate?.world() as? ServerLevel
    private fun withdraw() { serverWorld()?.let { StandbyLink.remove(it,this) } }
    init { slowProcessList.add(IProcess { serverWorld()?.let { StandbyLink.update(it,this,unit.channel,it.gameTime,unit.control.request && unit.enabled) } }) }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(lrdu!=LRDU.Down)null else when(side) {
        front.left() -> unit.mains
        front.right() -> unit.output
        front.inverse -> unit.backup
        else -> null
    }
    override fun disconnectJob() { withdraw();unit.reset();super.disconnectJob() }
    override fun settings(nbt: CompoundTag) { withdraw();unit.read(nbt) }
    override fun writeSettings(nbt: CompoundTag)=unit.write(nbt)
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        try {
            when(id.toInt()) {
                80 -> { if(unit.setVoltage(stream.readDouble()))withdraw() }
                81 -> { withdraw();unit.channel=StandbyLink.channel(stream.readUTF());unit.reset() }
                82 -> { withdraw();unit.setEnabled(!unit.enabled) }
            }
        } catch(_: IOException) {}
        needPublish();return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        BackupDisplay().apply {
            target=unit.nominal;channel=unit.channel;status=unit.status;mode=if(unit.enabled)0 else 2
            mains=unit.mains.voltage;backup=unit.backup.voltage;output=unit.output.voltage;watts=unit.watts;request=unit.control.request
            connections=node!!.lrduCubeMask.getTranslate(Direction.YN)
        }.write(stream)
    }
    override fun multiMeterString(side: Direction)=tr("Mains: %1$; generator: %2$; output: %3$",Utils.plotVolt("",unit.mains.voltage),Utils.plotVolt("",unit.backup.voltage),Utils.plotVolt("",unit.output.voltage))
    override fun getWaila()=linkedMapOf(tr("Transfer switch") to backupStatus(unit.status),tr("Output voltage") to Utils.plotVolt("",unit.output.voltage),tr("Wireless channel") to unit.channel)
}

class StandbyGeneratorElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : BackupElement(node,descriptor) {
    val unit=StandbyGeneratorUnit(this)
    init {
        slowProcessList.add(IProcess {
            val world=node?.coordinate?.world() as? ServerLevel
            unit.requested=world!=null && StandbyLink.requested(world,unit.channel,world.gameTime)
        })
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(lrdu==LRDU.Down && (side==front.left() || side==front.right()))unit.output else null
    override fun getFluidHandler()=unit.fuel
    override fun connectJob() { super.connectJob();unit.attach(mods.eln.Eln.simulator.mna) }
    override fun disconnectJob() { unit.requested=false;unit.detach();super.disconnectJob() }
    override fun settings(nbt: CompoundTag)=unit.read(nbt)
    override fun writeSettings(nbt: CompoundTag)=unit.write(nbt)
    // Copying settings must never duplicate tank contents.
    override fun writeConfigTool(compound: CompoundTag,invoker: Player) {
        compound.putDouble("standbyVoltage",unit.nominal);compound.putInt("standbyMode",unit.mode.ordinal);compound.putString("standbyChannel",unit.channel)
    }
    override fun readConfigTool(compound: CompoundTag,invoker: Player) {
        if(compound.contains("standbyVoltage"))unit.setVoltage(compound.getDouble("standbyVoltage"))
        unit.channel=StandbyLink.channel(compound.getString("standbyChannel"));needPublish()
    }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        if(FluidUtil.getFluidHandler(player.mainHandItem).isPresent) {
            if(!player.level().isClientSide)return FluidUtil.interactWithFluidHandler(player,InteractionHand.MAIN_HAND,unit.fuel.tank)
            return true
        }
        return false
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        try {
            when(id.toInt()) {
                80 -> unit.setVoltage(stream.readDouble())
                81 -> { unit.channel=StandbyLink.channel(stream.readUTF());unit.requested=false;unit.stop() }
                82 -> { unit.mode=StandbyMode.entries[(unit.mode.ordinal+1)%StandbyMode.entries.size];if(unit.mode==StandbyMode.OFF)unit.stop() }
                83 -> unit.reset()
            }
        } catch(_: IOException) {}
        needPublish();return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        BackupDisplay().apply {
            target=unit.nominal;channel=unit.channel;status=unit.status;mode=unit.mode.ordinal;output=unit.output.voltage
            watts=unit.watts;fuel=unit.fuel.availableMb;request=unit.requested;connections=node!!.lrduCubeMask.getTranslate(Direction.YN)
        }.write(stream)
    }
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",unit.output.voltage)+Utils.plotPower("P:",unit.watts)
    override fun getWaila()=linkedMapOf(tr("Generator") to backupStatus(unit.status),tr("Fuel") to tr("%1$ / 20000 mB",unit.fuel.availableMb.toInt()),tr("Output power") to Utils.plotPower("",unit.watts))
}
