package mods.eln.sixnode.powersocket

import mods.eln.generic.GenericItemUsingDamageDescriptor.Companion.getDescriptor
import mods.eln.item.BrushDescriptor
import mods.eln.item.IConfigurable
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils.plotUIP
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sixnode.lampsupply.LampSupplyElement.PowerSupplyChannelHandle
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import mods.eln.misc.writeToNBT

class PowerSocketElement(sixNode: SixNode?, side: Direction?, descriptor: SixNodeDescriptor?) : SixNodeElement(
    sixNode!!, side!!, descriptor!!
), IConfigurable {
    var descriptor: PowerSocketDescriptor?
    var outputLoad = NbtElectricalLoad("outputLoad")
    var loadResistor = Resistor(null, null) // Connected in process()
    private var powerSocketSlowProcess: IProcess = PowerSocketSlowProcess()
    var channel = "Default channel"
    var paintColor = 0
    var voltageWatchdog = VoltageStateWatchDog(outputLoad)

    init {
        electricalLoadList.add(outputLoad)
        electricalComponentList.add(loadResistor)
        slowProcessList.add(powerSocketSlowProcess)
        loadResistor.highImpedance()
        this.descriptor = descriptor as PowerSocketDescriptor?
        slowProcessList.add(voltageWatchdog)
        voltageWatchdog.setDestroys(WorldExplosion(this).cableExplosion())
        voltageWatchdog.setNominalVoltage(300.0)
    }

    private var connectedSupply: mods.eln.utility.WirelessPowerHandle?=null
    private fun disconnectSupply() {
        if(connectedSupply==null)return
        mods.eln.Eln.simulator.removeElectricalComponent(loadResistor)
        loadResistor.breakConnection();loadResistor.highImpedance()
        connectedSupply=null
        mods.eln.Eln.simulator.addElectricalComponent(loadResistor)
        mods.eln.utility.WirelessPowerChannels.untrackBridge(this)
    }
    internal inner class PowerSocketSlowProcess : IProcess {
        override fun process(time: Double) {
            val local=sixNode!!.coordinate
            val next=mods.eln.utility.WirelessPowerChannels.channels(channel)?.asSequence()
                ?.filter { it.element.isInRange(local) && it.element.getChannelState(it.id) }
                ?.minByOrNull { it.element.coordinate.trueDistanceTo(local) }
            if(next===connectedSupply)return
            disconnectSupply()
            if(next!=null) {
                mods.eln.Eln.simulator.removeElectricalComponent(loadResistor)
                loadResistor.connectTo(next.element.powerLoad,outputLoad);loadResistor.resistance=.1
                mods.eln.Eln.simulator.addElectricalComponent(loadResistor)
                connectedSupply=next
                mods.eln.utility.WirelessPowerChannels.trackBridge(this@PowerSocketElement,next.element.owner) { disconnectSupply() }
            }
        }
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad {
        return outputLoad
    }

    override fun getThermalLoad(lrdu: LRDU, mask: Int): ThermalLoad? {
        return null
    }

    override fun getConnectionMask(lrdu: LRDU): Int {
        return NodeBase.maskElectricalPower + (1 shl NodeBase.maskColorCareShift) + (paintColor shl NodeBase.maskColorShift)
    }

    override fun multiMeterString(): String {
        return plotUIP(outputLoad.voltage, outputLoad.getCurrent())
    }

    override fun thermoMeterString(): String {
        return ""
    }

    override fun initialize() {
        outputLoad.serialResistance = 0.1
    }

    override fun inventoryChanged() {
        super.inventoryChanged()
        sixNode!!.disconnect()
        sixNode!!.connect()
        needPublish()
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putString("channel", channel)
        nbt.putInt("color", paintColor)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        channel = nbt.getString("channel")
        paintColor = nbt.getInt("color")
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            when (stream.readByte()) {
                setChannelId -> {
                    channel = stream.readUTF()
                    needPublish()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun hasGui(): Boolean {
        return true
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeUTF(channel)
            stream.writeInt(paintColor)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onBlockActivated(
        entityPlayer: Player,
        side: Direction,
        vx: Float,
        vy: Float,
        vz: Float
    ): Boolean {
        val used = entityPlayer.mainHandItem
        if (used != null) {
            val desc = getDescriptor(used)
            if (desc != null && desc is BrushDescriptor) {
                val color: Int = desc.getColor(used)
                if (color != paintColor && desc.use(used, entityPlayer)) {
                    paintColor = color
                    sixNode!!.reconnect()
                }
                return true
            }
        }
        return false
    }

    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        if (compound.contains("powerChannels")) {
            val newChannel = compound.getList("powerChannels", 8).getString(0)
            if (!newChannel.isEmpty()) {
                channel = newChannel
                needPublish()
            }
        }
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        val list = ListTag()
        list.add(StringTag.valueOf(channel))
        compound.put("powerChannels", list)
    }

    companion object {
        const val setChannelId: Byte = 1
    }
}
