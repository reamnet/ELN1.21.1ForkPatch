package mods.eln.transparentnode.utilitylight

import mods.eln.utility.*
import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridElement
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.lightblock.LightBlockEntity
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
    GridElement(node, descriptor, (descriptor as GridDescriptor).connectRange), IConfigurable,mods.eln.transparentnode.cord.CordDevice {
    override val inventory = object : TransparentNodeElementInventory(1, 1, this@UtilityStreetLightElement) {
        override fun getAccessibleSlotsFromSide(side: Int) = intArrayOf(0)
        override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = slot == 0 && isStepDownUnit(stack)
        override fun canTakeItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = slot == 0
    }
    val fuse=CutoutFuse()
    val highBus=mods.eln.sim.nbt.NbtElectricalLoad("streetGridBus").apply { serialResistance=.00001 }
    val unit: MainsStepDown = MainsStepDown(this, poleTransformer = true) { installed && !cord.plugged && fuse.closed }
    val serviceSecondary=ServiceSecondary(this,unit.secondary) { unit.outputVoltage.toDouble() }
    val ports=LegacyPolePorts(this,highBus,serviceSecondary.loads)
    val fuseContact=mods.eln.transparentnode.standby.TransferContact(highBus,unit.primary)
    private var heightReady=false
    private var heightRetry=0.0
    override val cord=mods.eln.transparentnode.cord.ApplianceCord(this,unit.secondary)
    override val cordVoltage get()=unit.outputVoltage.toDouble()
    override fun cordChanged() { configureLamp() }
    val installed get() = isStepDownUnit(inventory.getItem(0))
    internal val lamp = Resistor(unit.secondary, null)
    private var darkOutside = false
    private val fixture get() = transparentNodeDescriptor as UtilityStreetLightDescriptor
    private val lampRequested get() = (installed || cord.plugged) && unit.enabled && unit.lampEnabled && (!unit.lampAutomatic || darkOutside)
    var lightLevel = 0
        private set
    init {
        electricalComponentList.add(lamp)
        electricalLoadList.add(highBus);electricalComponentList.add(fuseContact)
        electricalProcessList.add(IProcess { dt ->
            serviceSecondary.update()
            if(fuse.pickup(dt,fuseContact.current))play(mods.eln.sound.SoundCommand("eln:utility_arc_close").smallRange().mulVolume(.20))
            val limited=unit.outputOverloaded
            if(fuse.step(dt,fuseContact.current,unit.ratedWatts/MainsStepDown.NOMINAL_POLE_VOLTS,limited)) {
                play(mods.eln.sound.SoundCommand("eln:utility_fuse_blow").smallRange().mulVolume(.8));fuseContact.closed=false;needPublish()
            }
        })
        slowProcessList.add(IProcess { dt ->
            updateLight()
            if(!heightReady) { heightRetry-=dt;if(heightRetry<=0) { heightReady=PoleHeightUpgrade.update(this);heightRetry=1.0 } }
        })
        slowProcessList.add(NodePeriodicPublishProcess(node, 1.0, .5))
        configureLamp()
    }
    private fun configureLamp() {
        lamp.resistance = if (lampRequested) unit.outputVoltage.toDouble().let { it * it / fixture.lampWatts } else 1e19
    }
    internal fun updateAmbientDarkness(darkness: Int) {
        // Global sky darkness includes dusk and weather, but never this lamp's own light.
        darkOutside = if (darkOutside) darkness > 2 else darkness >= 4
        configureLamp()
    }
    private fun updateLight() {
        val world = node!!.coordinate.world() as? ServerLevel ?: return
        updateAmbientDarkness(if (world.dimensionType().hasSkyLight()) world.skyDarken else 15)
        val next = if (lampRequested) (15.0 * (lamp.power / fixture.lampWatts).coerceIn(0.0, 1.0)).toInt() else 0
        if (next != lightLevel) { lightLevel = next; needPublish() }
        if (lightLevel > 0) {
            // Light comes from the head, and must not load a remote chunk merely to refresh it.
            for(head in fixture.heads) {
                val position=front.rotateFromXN(head.add(0.0,StreetLightMount.offset(unit.lampHeightStep),0.0).yRot(Math.toRadians(unit.lampYaw.toDouble()).toFloat()))
                val offset=net.minecraft.core.BlockPos.containing(position.x+.5,position.y+.5,position.z+.5)
                val at = node!!.coordinate.newWithOffset(offset.x, offset.y, offset.z)
                if (world.chunkSource.getChunkNow(at.x shr 4, at.z shr 4) != null)
                    LightBlockEntity.addLight(at, lightLevel, 5)
            }
        }
    }
    override fun connectJob() { super.connectJob(); Eln.simulator.mna.addProcess(unit.controller);fuseContact.closed=fuse.closed }
    override fun disconnectJob() { Eln.simulator.mna.removeProcess(unit.controller); super.disconnectJob() }
    override fun getGridElectricalLoad(side: Direction) = highBus
    override fun aimedGridTerminal(side: Direction,localHit: net.minecraft.world.phys.Vec3)=if(localHit.y<5.65)50 else 32
    override fun selectedGridTerminal(side: Direction)=ports.selected
    override fun gridLoads(terminal: Int)=ports.loads(terminal)
    override fun gridCircuitKey(terminal: Int)=ports.key(terminal)
    override fun renderWireCount(terminal: Int)=if(terminal==50)3 else if(terminal<6)2 else 1
    override fun gridCablePoint(terminal: Int,wire: Int)=if(terminal<40)super.gridCablePoint(terminal,wire) else ports.point(terminal,wire)
    override fun getElectricalLoad(side: Direction, lrdu: LRDU) =
        if (!cord.plugged && side != Direction.YN && side != Direction.YP && lrdu == LRDU.Down) unit.secondary else null
    override fun getConnectionMask(side: Direction, lrdu: LRDU) =
        if (getElectricalLoad(side, lrdu) != null) NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction, lrdu: LRDU) = if (getElectricalLoad(side, lrdu) != null) unit.thermal else null
    override fun inventoryChange(inventory: Container?) { unit.installRating(mods.eln.utility.PoleHardware.transformerWatts(this.inventory.getItem(0)) ?: 10_000);configureLamp(); needPublish() }
    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        val held = player.mainHandItem
        if(mods.eln.utility.PoleHardware.extract(player,listOf(fuse))) { fuseContact.closed=fuse.closed;needPublish();return true }
        if(UtilityItems.isFuse(held) && fuse.insertCartridge(held)) {
            fuseContact.closed=true;if(!player.abilities.instabuild)held.shrink(1)
            player.inventory.setChanged();needPublish();return true
        }
        if (!isStepDownUnit(held)) return super.onBlockActivated(player, side, vx, vy, vz)
        if (!inventory.getItem(0).isEmpty) return false // Keep occupied slots available in the GUI.
        inventory.setItem(0, held.copyWithCount(1))
        val freeInsert = player.abilities.instabuild && Eln.config.getBooleanOrElse("gameplay.qol.creativeNoConsumeInsertedItems", false)
        if (!freeInsert) held.shrink(1)
        player.inventory.setChanged()
        inventoryChange(inventory)
        return true
    }
    override fun thermoMeterString(side: Direction) = plotAmbientCelsius("T", unit.thermal.temperatureCelsius)
    override fun newContainer(side: Direction, player: Player) = StepDownContainer(player, inventory, true)
    override fun hasGui() = true
    override fun getWaila() = unit.waila().apply {
        put(tr("Step-down unit"), if (installed) tr("Installed") else tr("Missing"))
        put(tr("Lamp"), if (lightLevel > 0) tr("On") else tr("Off"))
    }
    override fun multiMeterString(side: Direction) = tr("Grid: %1$; output: %2$", Utils.plotVolt("", highBus.voltage), Utils.plotVolt("", unit.secondary.voltage))
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt); unit.read(nbt);mods.eln.utility.PoleHardware.migrate(this,unit.ratedWatts);unit.installRating(mods.eln.utility.PoleHardware.transformerWatts(inventory.getItem(0)) ?: 10_000);ports.read(nbt);fuse.read(nbt,"poleFuse");fuseContact.closed=fuse.closed;serviceSecondary.update();configureLamp() }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt); unit.write(nbt);ports.write(nbt);fuse.write(nbt,"poleFuse") }
    override fun getItemStackNBT()=CompoundTag().also { unit.write(it);ports.write(it);fuse.write(it,"poleFuse") }
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null) { unit.read(nbt);mods.eln.utility.PoleHardware.migrate(this,unit.ratedWatts);unit.installRating(mods.eln.utility.PoleHardware.transformerWatts(inventory.getItem(0)) ?: 10_000);ports.read(nbt);fuse.read(nbt,"poleFuse");fuseContact.closed=fuse.closed;serviceSecondary.update();configureLamp() } }
    override fun readConfigTool(compound: CompoundTag, invoker: Player) { if (compound.contains("mainsOutputVoltage")) { unit.read(compound);unit.installRating(mods.eln.utility.PoleHardware.transformerWatts(inventory.getItem(0)) ?: 10_000); configureLamp(); needPublish() } }
    override fun writeConfigTool(compound: CompoundTag, invoker: Player) { unit.write(compound) }
    override fun networkSerialize(stream: DataOutputStream) { super.networkSerialize(stream); unit.publish(stream, installed, lightLevel)
        LegacyPoleDisplay().apply { terminal=ports.selected;fuseClosed=fuse.closed;fuseBlown=fuse.blown;rating=unit.ratedWatts;voltage=unit.outputVoltage }.write(stream)
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id = super.networkUnserialize(stream)
        if (unit.packet(id, stream)) { serviceSecondary.update();configureLamp();needPublish() }
        if(id.toInt()==87) { ports.select(stream.readInt());needPublish() }
        if(id.toInt()==89) {
            stream.readInt()
            val loaded=kotlin.math.abs(fuseContact.current)>.02
            if(fuse.operate()) {
                if(loaded && !fuse.closed)play(mods.eln.sound.SoundCommand("eln:utility_arc_open").smallRange().mulVolume(.55))
                fuseContact.closed=fuse.closed;needPublish()
            }
        }
        return id
    }
}

fun isStepDownUnit(stack: ItemStack?) = mods.eln.utility.PoleHardware.transformerWatts(stack)!=null
