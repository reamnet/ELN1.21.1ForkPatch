package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridElement
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream

class StepDownUnitElement(node: TransparentNode, descriptor: TransparentNodeDescriptor) :
    TransparentNodeElement(node, descriptor), IConfigurable {
    override val inventory = TransparentNodeElementInventory(0, 1, this)
    val unit = MainsStepDown(this)
    init { slowProcessList.add(NodePeriodicPublishProcess(node, 1.0, .5)) }
    override fun initialize() { connect() }
    override fun connectJob() { super.connectJob(); Eln.simulator.mna.addProcess(unit.controller) }
    override fun disconnectJob() { Eln.simulator.mna.removeProcess(unit.controller); super.disconnectJob() }
    override fun getElectricalLoad(side: Direction, lrdu: LRDU) = if (lrdu != LRDU.Down) null else when (side) {
        front.left() -> unit.primary
        front.right() -> unit.secondary
        else -> null
    }
    override fun getConnectionMask(side: Direction, lrdu: LRDU) =
        if (getElectricalLoad(side, lrdu) != null) NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction, lrdu: LRDU) = if (getElectricalLoad(side, lrdu) != null) unit.thermal else null
    override fun newContainer(side: Direction, player: Player) = StepDownContainer(player, inventory, false)
    override fun hasGui() = true
    override fun multiMeterString(side: Direction) = tr("Input: %1$; output: %2$", Utils.plotVolt("", unit.primary.voltage), Utils.plotVolt("", unit.secondary.voltage))
    override fun getWaila() = unit.waila()
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt); unit.read(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt); unit.write(nbt) }
    override fun readItemStackNBT(nbt: CompoundTag?) { if (nbt != null) unit.read(nbt) }
    override fun getItemStackNBT() = CompoundTag().also(unit::write)
    override fun readConfigTool(compound: CompoundTag, invoker: Player) { if (compound.contains("mainsOutputVoltage")) { unit.read(compound); needPublish() } }
    override fun writeConfigTool(compound: CompoundTag, invoker: Player) { unit.write(compound) }
    override fun networkSerialize(stream: DataOutputStream) { super.networkSerialize(stream); unit.publish(stream, true, 0) }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id = super.networkUnserialize(stream)
        if (unit.packet(id, stream)) needPublish()
        return id
    }
}

class UtilityStreetLightElement(node: TransparentNode, descriptor: TransparentNodeDescriptor) :
    GridElement(node, descriptor, (descriptor as GridDescriptor).connectRange), IConfigurable {
    override val inventory = object : TransparentNodeElementInventory(1, 1, this@UtilityStreetLightElement) {
        override fun getAccessibleSlotsFromSide(side: Int) = intArrayOf(0)
        override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = slot == 0 && isStepDownUnit(stack)
        override fun canTakeItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = slot == 0
    }
    val unit = MainsStepDown(this) { installed }
    val installed get() = isStepDownUnit(inventory.getItem(0))
    private val lamp = Resistor(unit.secondary, null)
    var lightLevel = 0
        private set
    init {
        electricalComponentList.add(lamp)
        slowProcessList.add(IProcess { updateLight() })
        slowProcessList.add(NodePeriodicPublishProcess(node, 1.0, .5))
        configureLamp()
    }
    private fun configureLamp() {
        lamp.resistance = if (installed && unit.lampEnabled) unit.outputVoltage.toDouble().let { it * it / 60.0 } else 1e19
    }
    private fun updateLight() {
        configureLamp()
        val next = if (installed && unit.lampEnabled) (15.0 * (lamp.power / 60.0).coerceIn(0.0, 1.0)).toInt() else 0
        if (next != lightLevel) { lightLevel = next; needPublish() }
        if (lightLevel > 0) {
            // Light comes from the head, and must not load a remote chunk merely to refresh it.
            val world = node!!.coordinate.world() as? ServerLevel ?: return
            for (x in -2..-1) {
                val at = Coordinate(x, 2, 0, 0).apply { applyTransformation(front, node!!.coordinate) }
                if (world.chunkSource.getChunkNow(at.x shr 4, at.z shr 4) != null)
                    LightBlockEntity.addLight(at, lightLevel, 5)
            }
        }
    }
    override fun connectJob() { super.connectJob(); Eln.simulator.mna.addProcess(unit.controller) }
    override fun disconnectJob() { Eln.simulator.mna.removeProcess(unit.controller); super.disconnectJob() }
    override fun getGridElectricalLoad(side: Direction) = unit.primary
    override fun getElectricalLoad(side: Direction, lrdu: LRDU) =
        if (side != Direction.YN && side != Direction.YP && lrdu == LRDU.Down) unit.secondary else null
    override fun getConnectionMask(side: Direction, lrdu: LRDU) =
        if (getElectricalLoad(side, lrdu) != null) NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction, lrdu: LRDU) = if (getElectricalLoad(side, lrdu) != null) unit.thermal else null
    override fun inventoryChange(inventory: Container?) { configureLamp(); needPublish() }
    override fun newContainer(side: Direction, player: Player) = StepDownContainer(player, inventory, true)
    override fun hasGui() = true
    override fun getWaila() = unit.waila().apply {
        put(tr("Step-down unit"), if (installed) tr("Installed") else tr("Missing"))
        put(tr("Lamp"), if (lightLevel > 0) tr("On") else tr("Off"))
    }
    override fun multiMeterString(side: Direction) = tr("Grid: %1$; output: %2$", Utils.plotVolt("", unit.primary.voltage), Utils.plotVolt("", unit.secondary.voltage))
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt); unit.read(nbt); configureLamp() }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt); unit.write(nbt) }
    override fun readConfigTool(compound: CompoundTag, invoker: Player) { if (compound.contains("mainsOutputVoltage")) { unit.read(compound); configureLamp(); needPublish() } }
    override fun writeConfigTool(compound: CompoundTag, invoker: Player) { unit.write(compound) }
    override fun networkSerialize(stream: DataOutputStream) { super.networkSerialize(stream); unit.publish(stream, installed, lightLevel) }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id = super.networkUnserialize(stream)
        if (unit.packet(id, stream)) { configureLamp(); needPublish() }
        return id
    }
}

fun isStepDownUnit(stack: ItemStack?) = stack != null && !stack.isEmpty &&
    GenericItemBlockUsingDamageDescriptor.getDescriptor(stack) is StepDownUnitDescriptor
