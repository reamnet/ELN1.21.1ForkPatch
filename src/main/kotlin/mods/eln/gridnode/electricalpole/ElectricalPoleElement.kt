package mods.eln.gridnode.electricalpole

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.utility.*
import mods.eln.i18n.I18N.tr
import mods.eln.sim.IProcess
import mods.eln.transparentnode.standby.TransferContact
import mods.eln.transparentnode.utilitylight.MainsStepDown
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import mods.eln.Eln
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridElement
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.power.SafeTransformerProcess
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.ThermalLoadWatchDog
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sim.process.heater.ElectricalLoadHeatThermalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor

import java.io.DataOutputStream
import java.io.IOException

data class Transformer(
    val secondaryLoad: NbtElectricalLoad,
    val primaryVoltageSource: VoltageSource,
    val secondaryVoltageSource: VoltageSource,
    val interSystemProcess: TransformerInterSystemProcess,
    val voltageSecondaryWatchdog: VoltageStateWatchDog
)

class ElectricalPoleElement(node: TransparentNode, descriptor: TransparentNodeDescriptor)
    : GridElement(node, descriptor, (descriptor as GridDescriptor).connectRange) {
    override val alignsPoleSpans=true
    internal override fun serviceClearance()=if((transparentNodeDescriptor as ElectricalPoleDescriptor).includeTransformer)
        PoleTransformerShape.of(ratedWatts).clearance(highVoltageRenderAngle) else super.serviceClearance()
    companion object { val OUTPUT_VOLTAGES=intArrayOf(120,240,3200) }
    private val desc = descriptor as ElectricalPoleDescriptor
    override val inventory=object: mods.eln.node.transparent.TransparentNodeElementInventory(if(desc.includeTransformer)1 else 0,1,this@ElectricalPoleElement) {
        override fun canPlaceItemThroughFace(slot: Int,stack: net.minecraft.world.item.ItemStack?,side: Int)=desc.includeTransformer && mods.eln.utility.PoleHardware.transformerWatts(stack)!=null
    }

    var electricalLoad = NbtElectricalLoad("electricalLoad")
    val fuse=CutoutFuse()
    val ports=LegacyPolePorts(this,electricalLoad)
    var ratedWatts=10_000
        private set
    var outputVoltage=3200
        private set
    private fun updateRatio() {
        trafo?.interSystemProcess?.apply {
            ratio=outputVoltage/MainsStepDown.NOMINAL_POLE_VOLTS
            (this as? SafeTransformerProcess)?.resetFault()
        }
    }
    val fusedPrimary=NbtElectricalLoad("fusedPrimary").apply { serialResistance=.00001 }
    val fuseContact=TransferContact(electricalLoad,fusedPrimary)
    private var heightReady=false
    private var heightRetry=0.0
    var thermalLoad = NbtThermalLoad("thermalLoad")
    internal var heater = ElectricalLoadHeatThermalLoad(electricalLoad, thermalLoad)
    internal var thermalWatchdog = ambientAwareThermalWatchdog(ThermalLoadWatchDog(thermalLoad))
    internal var voltageWatchdog = VoltageStateWatchDog(electricalLoad)
    internal var secondaryMaxCurrent = 0f

    val trafo: Transformer?

    init {
        electricalLoad.setCanBeSimplifiedByLine(true)
        // Most of the resistance is in the cable, which is handled in GridLink.
        // We put some of it here, thereby allowing the thermal watchdog to work.
        desc.cableDescriptor.applyTo(electricalLoad)
        desc.cableDescriptor.applyTo(thermalLoad)
        heater.limitTemperatureRate(desc.cableDescriptor.thermalSelfHeatingRateLimit)
        electricalLoadList.add(electricalLoad)

        thermalLoadList.add(thermalLoad)
        slowProcessList.add(heater)
        thermalLoad.setAsSlow()
        slowProcessList.add(thermalWatchdog)
        thermalWatchdog
                .setTemperatureLimits(desc.cableDescriptor.thermalWarmLimit, desc.cableDescriptor.thermalCoolLimit)
                .setDestroys(WorldExplosion(this).cableExplosion())
        thermalWatchdog.dumpMatrixOnTrip("ElectricalPoleElement thermal trip") { this }

        slowProcessList.add(voltageWatchdog)
        // Electrical poles can handle higher voltages, due to air insulation.
        // This puts utility poles at 4 * Very High Voltage.
        val exp: WorldExplosion = if (desc.includeTransformer) {
            WorldExplosion(this).machineExplosion()
        } else {
            WorldExplosion(this).cableExplosion()
        }
        voltageWatchdog
                .setNominalVoltage(desc.voltageLimit)
                .setDestroys(exp)

        slowProcessList.add(IProcess { dt -> if(!heightReady) { heightRetry-=dt;if(heightRetry<=0) { heightReady=PoleHeightUpgrade.update(this);heightRetry=1.0 } } })
        if (desc.includeTransformer) {
            electricalLoadList.add(fusedPrimary);electricalComponentList.add(fuseContact);fuseContact.closed=fuse.closed
            val secondaryLoad = NbtElectricalLoad("secondaryLoad")
            val primaryVoltageSource = SwitchableVoltageSource("primaryVoltageSource").apply { connectTo(fusedPrimary, null) }
            val secondaryVoltageSource = SwitchableVoltageSource("secondaryVoltageSource").apply { connectTo(secondaryLoad, null) }
            val interSystemProcess = SafeTransformerProcess(fusedPrimary, secondaryLoad, primaryVoltageSource, secondaryVoltageSource) { fuse.closed }.apply { ratio=outputVoltage/MainsStepDown.NOMINAL_POLE_VOLTS }
            val voltageSecondaryWatchdog = VoltageStateWatchDog(secondaryLoad)

            trafo = Transformer(
                secondaryLoad,
                primaryVoltageSource,
                secondaryVoltageSource,
                interSystemProcess,
                voltageSecondaryWatchdog
            )

            desc.cableDescriptor.applyTo(secondaryLoad, 4.0)

            electricalLoadList.add(secondaryLoad)
            electricalComponentList.add(primaryVoltageSource)
            electricalComponentList.add(secondaryVoltageSource)
            slowProcessList.add(voltageSecondaryWatchdog.setDestroys(exp))

            electricalProcessList.add(IProcess { dt ->
                if(fuse.pickup(dt,fuseContact.current))play(mods.eln.sound.SoundCommand("eln:utility_arc_close").smallRange().mulVolume(.20))
                if(fuse.step(dt,fuseContact.current,ratedWatts/MainsStepDown.NOMINAL_POLE_VOLTS)) {
                    fuseContact.closed=false;play(mods.eln.sound.SoundCommand("eln:utility_fuse_blow").smallRange().mulVolume(.8));needPublish()
                }
            })
            // Publish load from time to time.
            slowProcessList.add(NodePeriodicPublishProcess(node, 1.0, 0.5))
        } else {
            trafo = null
        }
    }

    override fun disconnectJob() {
        super.disconnectJob()
        trafo?.apply {
            Eln.simulator.mna.removeProcess(interSystemProcess)
        }
    }

    override fun connectJob() {
        trafo?.apply {
            Eln.simulator.mna.addProcess(interSystemProcess)
        }
        super.connectJob()
    }

    override fun multiMeterString(side: Direction): String {
        val electricalInfo=mods.eln.utility.PoleMeter.readings(listOf(electricalLoad),ports.services).joinToString("\n")

        val connectionInfo = connectionLines(includeCurrentLimit = false).joinToString("\n")

        return listOf(electricalInfo, connectionInfo)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    override fun getWaila(): Map<String, String> {
        val info = linkedMapOf<String, String>()
        val meterInfo = if (trafo != null) {
            Utils.plotVolt("GridU:", electricalLoad.voltage) + Utils.plotAmpere("GridP:", electricalLoad.current) +
                Utils.plotVolt("  GroundU:", trafo.secondaryLoad.voltage) + Utils.plotAmpere("GroundP:", trafo.secondaryLoad.current)
        } else {
            super.multiMeterString(front)
        }
        if (meterInfo.isNotBlank()) {
            info["Info"] = meterInfo
        }
        connectionLines(includeCurrentLimit = true).forEach { (directionLabel, line) ->
            info[directionLabel] = line
        }
        return info
    }

    private fun connectionLines(includeCurrentLimit: Boolean): List<Pair<String, String>> {
        return gridLinkList
            .map { link ->
                val other = link.getOtherElement(this)
                val dx = other.coordinate().x - coordinate().x
                val dy = other.coordinate().y - coordinate().y
                val dz = other.coordinate().z - coordinate().z
                val descriptor = GenericItemBlockUsingDamageDescriptor.getDescriptor(link.cable)
                val cableName = descriptor?.getName(link.cable) ?: "Unknown cable"
                val cableLabel = if (includeCurrentLimit && descriptor is ElectricalCableDescriptor) {
                    "$cableName (${Utils.plotAmpere("", descriptor.electricalMaximalCurrent).trim()})"
                } else {
                    cableName
                }
                val direction = describeDirection(dx, dy, dz)
                direction.replaceFirstChar { it.uppercase() } to cableLabel
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun describeDirection(dx: Int, dy: Int, dz: Int): String {
        val ax = kotlin.math.abs(dx)
        val ay = kotlin.math.abs(dy)
        val az = kotlin.math.abs(dz)
        return when {
            ay > ax && ay > az -> if (dy > 0) "up" else "down"
            ax >= az -> if (dx > 0) "east" else "west"
            else -> if (dz > 0) "south" else "north"
        }
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? {
        if(side==Direction.YN && (desc.kind==Kind.OVERHEAD || ports.selected>=40))return ports.services[mods.eln.utility.UndergroundService.circuit(lrdu)]
        return when(desc.kind) {
            Kind.OVERHEAD -> null
            Kind.TRANSFORMER_TO_GROUND -> trafo?.secondaryLoad
            Kind.SHUNT_TO_GROUND -> electricalLoad
        }
    }

    override fun aimedGridTerminal(side: Direction,localHit: net.minecraft.world.phys.Vec3)=if(localHit.y<5.65)50 else 32
    override fun selectedGridTerminal(side: Direction)=ports.selected
    override fun gridLoads(terminal: Int)=ports.loads(terminal)
    override fun gridCircuitKey(terminal: Int)=ports.key(terminal)
    override fun renderWireCount(terminal: Int)=if(terminal==50)3 else if(terminal<6)2 else 1
    override fun gridCablePoint(terminal: Int,wire: Int)=if(terminal<40)super.gridCablePoint(terminal,wire) else ports.point(terminal,wire)
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=mods.eln.transparentnode.utilitylight.StepDownContainer(player,inventory,desc.includeTransformer)
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        if(desc.includeTransformer && mods.eln.utility.PoleHardware.extract(player,listOf(fuse))) { fuseContact.closed=fuse.closed;needPublish();return true }
        if(desc.includeTransformer && mods.eln.utility.PoleHardware.transformerWatts(player.mainHandItem)!=null && inventory.getItem(0).isEmpty) {
            inventory.setItem(0,player.mainHandItem.copyWithCount(1));if(!player.abilities.instabuild)player.mainHandItem.shrink(1);inventoryChange(inventory);return true
        }
        if(UtilityItems.isFuse(player.mainHandItem) && desc.includeTransformer && fuse.insertCartridge(player.mainHandItem)) {
            fuseContact.closed=true
            if(!player.abilities.instabuild)player.mainHandItem.shrink(1)
            player.inventory.setChanged();needPublish();return true
        }
        return super.onBlockActivated(player,side,vx,vy,vz)
    }
    override fun inventoryChange(inventory: net.minecraft.world.Container?) { ratedWatts=mods.eln.utility.PoleHardware.transformerWatts(this.inventory.getItem(0)) ?: 10_000;publishServiceGeometry() }
    private fun writeSettings(nbt: CompoundTag) {
        ports.write(nbt);fuse.write(nbt,"legacyFuse")
        nbt.putBoolean("legacyHardwareV1",true);nbt.putInt("legacyRatedWatts",ratedWatts);nbt.putInt("legacyOutputVoltage",outputVoltage)
    }
    private fun readSettings(nbt: CompoundTag) {
        ports.read(nbt);fuse.read(nbt,"legacyFuse");fuseContact.closed=fuse.closed
        val oldWatts=nbt.getInt("legacyRatedWatts").takeIf { it in MainsStepDown.RATINGS } ?: 10_000
        if(desc.includeTransformer && !nbt.getBoolean("legacyHardwareV1") && oldWatts>10_000 && inventory.getItem(0).isEmpty)
            inventory.setItem(0,Eln.findItemStack(mods.eln.utility.PoleHardware.transformerNames[MainsStepDown.RATINGS.indexOf(oldWatts)],1))
        ratedWatts=if(desc.includeTransformer)mods.eln.utility.PoleHardware.transformerWatts(inventory.getItem(0)) ?: 10_000 else 10_000
        outputVoltage=nbt.getInt("legacyOutputVoltage").takeIf { it in OUTPUT_VOLTAGES } ?: 3200
        updateRatio()
    }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeSettings(nbt) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);readSettings(nbt) }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)readSettings(nbt) }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            80->stream.readInt().takeIf { it in OUTPUT_VOLTAGES }?.let { outputVoltage=it;updateRatio() }
            87->ports.select(stream.readInt())
            86->{ stream.readInt() } // The installed transformer item determines capacity.
            89->{
                stream.readInt();val loaded=kotlin.math.abs(fuseContact.current)>.02
                if(desc.includeTransformer && fuse.operate()) {
                    if(loaded && !fuse.closed)play(mods.eln.sound.SoundCommand("eln:utility_arc_open").smallRange().mulVolume(.55))
                    fuseContact.closed=fuse.closed
                }
            }
        }
        needPublish();return id
    }

    override fun getGridElectricalLoad(side: Direction): ElectricalLoad {
        return electricalLoad
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad {
        return thermalLoad
    }

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int = if(desc.kind == Kind.OVERHEAD && side!=Direction.YN) {
        0
    } else {
        NodeBase.maskElectricalPower
    }

    override fun initialize() {
        trafo?.apply {
            voltageSecondaryWatchdog.setNominalVoltage(Eln.instance.veryHighVoltageCableDescriptor.electricalNominalVoltage)
            secondaryMaxCurrent = desc.cableDescriptor.electricalMaximalCurrent.toFloat()
            interSystemProcess.setRatio(outputVoltage/MainsStepDown.NOMINAL_POLE_VOLTS)
        }
        super.initialize()
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        node!!.lrduCubeMask.getTranslate(front.down()).serialize(stream)
        try {
            if (trafo != null && secondaryMaxCurrent != 0f) {
                stream.writeFloat((trafo.secondaryLoad.current / secondaryMaxCurrent).toFloat())
            } else {
                stream.writeFloat(0f)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        LegacyPoleDisplay().apply { terminal=ports.selected;fuseClosed=fuse.closed;fuseBlown=fuse.blown;rating=ratedWatts;voltage=outputVoltage }.write(stream)
    }
}
