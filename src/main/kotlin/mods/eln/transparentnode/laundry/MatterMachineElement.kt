package mods.eln.transparentnode.laundry

import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.fusion.FusionValues
import mods.eln.transparentnode.kitchen.KitchenRecipe
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** Passive electrical consumer: no per-iteration port probes or negative-impedance regulation. */
class MatterMachineElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    val kind=(descriptor as MatterMachineDescriptor).kind
    override val inventory=object: TransparentNodeElementInventory(2,64,this) {
        override fun getAccessibleSlotsFromSide(side: Int)=intArrayOf(0,1)
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot==0 && stack!=null && accepts(stack)
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot==1
    }
    val supply=NbtElectricalLoad("matterMachineSupply").apply { serialResistance=.002 }
    val resistor=Resistor(supply,null).apply { resistance=1e19 }
    override val cord=ApplianceCord(this,supply)
    override val cordVoltage get()=kind.volts
    override fun acceptsCord(rating: CordRating)=when(kind) {
        MatterMachineKind.DRYER -> rating==CordRating.V240_30
        // The existing 3.2 kV cord is rated 125 A, below this reactor's maximum 156 A.
        MatterMachineKind.FUSION -> false
        else -> rating.accepts(kind.volts)
    }
    val water=WasherWater()
    val work=MachineWork()
    var enabled=true
    var status="IDLE";private set
    var requiredWatts=kind.watts;private set
    var requiredJoules=kind.watts*kind.seconds;private set
    var waterReserved=false;private set
    private var requested=false
    private var template=ItemStack.EMPTY
    private var result=ItemStack.EMPTY
    private var valueRevision=-1
    private var ready=false
    init {
        electricalLoadList.add(supply);electricalComponentList.add(resistor)
        slowPreProcessList.add(IProcess { prepare() })
        electricalProcessList.add(IProcess { dt ->
            // This list runs after the solver accepts a step. Trials never advance production.
            if(requested && enabled && validVoltage() && (!isFusion || resistor.power>=requiredWatts*.8))
                work.account(dt,resistor.power.coerceIn(0.0,requiredWatts*1.44),requiredJoules)
        })
        slowProcessList.add(IProcess { complete() })
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    val isFusion get()=kind==MatterMachineKind.FUSION
    fun accepts(stack: ItemStack)=when(kind) {
        MatterMachineKind.WASHER -> WetEquipment.washable(stack)
        MatterMachineKind.DRYER -> !WetEquipment.unpack(stack).isEmpty
        MatterMachineKind.FUSION -> FusionValues.cost(stack)!=null
    }
    override fun initialize() { refreshJob();connect() }
    override fun inventoryChange(inventory: Container?) { refreshJob();prepare();needPublish() }
    private fun refreshJob() {
        val input=inventory.getItem(0)
        val same=ItemStack.isSameItemSameComponents(input,template) && (input.isEmpty==template.isEmpty)
        if(!same) { work.reset();waterReserved=false }
        template=input.copyWithCount(if(input.isEmpty)0 else 1)
        requiredWatts=kind.watts;requiredJoules=kind.watts*kind.seconds
        result=when(kind) {
            MatterMachineKind.WASHER -> if(WetEquipment.washable(input))WetEquipment.wash(input) else ItemStack.EMPTY
            MatterMachineKind.DRYER -> WetEquipment.unpack(input)
            MatterMachineKind.FUSION -> FusionValues.cost(input)?.let {
                requiredWatts=it.watts;requiredJoules=it.joules
                // Never clone storage, enchantments, inventories or arbitrary sample components.
                ItemStack(input.item)
            } ?: ItemStack.EMPTY
        }
        if(!result.isEmpty)work.load(work.joules,requiredJoules)
        valueRevision=FusionValues.revision;ready=true
    }
    private fun isLoaded(): Boolean {
        val coord=node?.coordinate ?: return false
        val world=coord.world() as? net.minecraft.server.level.ServerLevel ?: return false
        return world.hasChunkAt(coord.pos) && world.getBlockEntity(coord.pos)!=null
    }
    private fun validVoltage()=supply.voltage.isFinite() && abs(supply.voltage) in kind.volts*.1..kind.volts*1.2
    private fun prepare() {
        if(!ready || isFusion && valueRevision!=FusionValues.revision)refreshJob()
        // Hoppers can mutate counts without calling setChanged; do not run a cached job on a different item.
        val input=inventory.getItem(0)
        if(!ItemStack.isSameItemSameComponents(input,template) || input.isEmpty!=template.isEmpty)refreshJob()
        status=when {
            !enabled -> "OFF"
            !isLoaded() -> "UNLOADED"
            input.isEmpty -> "IDLE"
            result.isEmpty -> "INVALID"
            !KitchenRecipe.mergeFits(inventory.getItem(1),result) -> "BLOCKED"
            !supply.voltage.isFinite() || abs(supply.voltage)>kind.volts*1.2 -> "OVERVOLTAGE"
            kind==MatterMachineKind.WASHER && !waterReserved && water.tank.fluidAmount<WasherWater.PER_CYCLE -> "WATER"
            !validVoltage() || isFusion && resistor.power<requiredWatts*.8 -> "POWER"
            else -> "WORKING"
        }
        // Keep the passive load connected during brownouts so its voltage/power recovers naturally.
        requested=enabled && isLoaded() && !input.isEmpty && !result.isEmpty && KitchenRecipe.mergeFits(inventory.getItem(1),result) &&
            supply.voltage.isFinite() && abs(supply.voltage)<=kind.volts*1.2 &&
            (kind!=MatterMachineKind.WASHER || waterReserved || water.tank.fluidAmount>=WasherWater.PER_CYCLE)
        if(requested && kind==MatterMachineKind.WASHER && !waterReserved && validVoltage())waterReserved=water.reserve()
        if(kind==MatterMachineKind.WASHER && !waterReserved)requested=false
        resistor.resistance=if(requested)kind.volts*kind.volts/requiredWatts else 1e19
    }
    private fun complete() {
        if(!enabled || !requested || result.isEmpty || work.joules+1e-6<requiredJoules)return
        val input=inventory.getItem(0)
        if(input.isEmpty || !ItemStack.isSameItemSameComponents(input,template) || !KitchenRecipe.mergeFits(inventory.getItem(1),result))return
        val previous=inventory.getItem(1)
        if(previous.isEmpty)inventory.setItem(1,result.copy()) else previous.grow(result.count)
        if(!isFusion)input.shrink(1)
        work.reset();waterReserved=false
        inventory.setChanged();needPublish()
    }
    override fun getFluidHandler()=if(kind==MatterMachineKind.WASHER)water else null
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!cord.plugged && side.isNotY && lrdu==LRDU.Down)supply else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=MatterMachineContainer(player,inventory,kind)
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",supply.voltage)+Utils.plotPower("P:",resistor.power)
    override fun getWaila()=linkedMapOf(tr("State") to matterMachineStatus(status),tr("Supply") to multiMeterString(front))
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        if(id.toInt()==80) { enabled=!enabled;prepare();needPublish() }
        return id
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeBoolean(enabled);out.writeUTF(status)
        out.writeDouble(supply.voltage);out.writeDouble(resistor.power);out.writeDouble(work.joules)
        out.writeDouble(requiredJoules);out.writeDouble(requiredWatts);out.writeInt(water.tank.fluidAmount)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(out)
    }
    override fun writeToNBT(tag: CompoundTag) {
        super.writeToNBT(tag);tag.putBoolean("matterEnabled",enabled);tag.putDouble("matterJoules",work.joules)
        tag.putBoolean("washReserved",waterReserved);water.save(tag)
    }
    override fun readFromNBT(tag: CompoundTag) {
        super.readFromNBT(tag);enabled=!tag.contains("matterEnabled") || tag.getBoolean("matterEnabled");water.load(tag)
        // Establish the saved template before restoring progress; a restart earns no offline credit.
        refreshJob();work.load(tag.getDouble("matterJoules"),if(isFusion && result.isEmpty)900_000_000.0 else requiredJoules)
        waterReserved=kind==MatterMachineKind.WASHER && tag.getBoolean("washReserved") && !result.isEmpty
    }
}
