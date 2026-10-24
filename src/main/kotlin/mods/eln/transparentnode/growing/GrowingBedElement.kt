package mods.eln.transparentnode.growing

import mods.eln.environment.BiomeClimateService
import mods.eln.i18n.I18N.tr
import mods.eln.item.HeatingCorpElement
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.laundry.WasherWater
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** Four soil trays with a shared 48-lamp bank and a thermostatic heater. */
class GrowingBedElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    companion object {
        const val VOLTS=240.0
        const val BULBS=48
        const val FIRST_OUTPUT=3
        const val SLOTS=12
        fun accepts(slot: Int,stack: ItemStack)=when(slot) {
            0 -> GrowingCycle.crop(stack)!=null
            1 -> Utils.getItemObject(stack) is LampDescriptor
            2 -> Utils.getItemObject(stack) is HeatingCorpElement
            else -> false
        }
    }
    override val inventory=object: TransparentNodeElementInventory(SLOTS,64,this) {
        override fun canPlaceItem(slot: Int,stack: ItemStack)=accepts(slot,stack)
        override fun getAccessibleSlotsFromSide(side: Int)=intArrayOf(0,3,4,5,6,7,8,9,10,11)
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot==0 && stack!=null && accepts(slot,stack)
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot>=FIRST_OUTPUT
    }
    val supply=NbtElectricalLoad("growingBedSupply").apply { serialResistance=.002 }
    val lights=Resistor(supply,null).apply { resistance=1e19 }
    val heater=Resistor(supply,null).apply { resistance=1e19 }
    override val cord=ApplianceCord(this,supply)
    override val cordVoltage get()=VOLTS
    override fun acceptsCord(rating: CordRating)=rating==CordRating.V240 || rating==CordRating.V240_30
    val water=WasherWater()
    val cycle=GrowingCycle()
    var enabled=true
    var target=23.0
    var status="IDLE";private set
    var planted=0;private set
    var installedBulbs=0;private set
    var heaterOn=false;private set
    private var ambient=20.0
    private var lightFactor=0.0
    private var requested=false
    private var reserved=false
    private var seed=ItemStack.EMPTY
    private var pending: List<ItemStack>?=null
    private var bulbAgeSeconds=0.0
    private var lightRefresh=0.0
    private var batching=false
    init {
        electricalLoadList.add(supply);electricalComponentList.addAll(listOf(lights,heater))
        slowPreProcessList.add(IProcess { prepare() })
        electricalProcessList.add(IProcess { dt ->
            if(loadedWorld()!=null)cycle.account(dt,ambient,lights.power,heater.power,
                lightFactor*(abs(supply.voltage)/VOLTS).let { it*it },requested && reserved && abs(supply.voltage) in 24.0..288.0)
        })
        slowProcessList.add(IProcess(::tick))
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    private fun loadedWorld(): ServerLevel? {
        val c=node?.coordinate ?: return null
        val world=c.world() as? ServerLevel ?: return null
        return world.takeIf { it.hasChunkAt(c.pos) && it.getBlockEntity(c.pos)!=null }
    }
    private fun refreshSeed() {
        val input=inventory.getItem(0)
        val count=if(GrowingCycle.crop(input)!=null)minOf(GrowingCycle.PLANTS,input.count) else 0
        if(!ItemStack.isSameItemSameComponents(input,seed) || count!=planted) {
            cycle.reset();reserved=false;pending=null
        }
        seed=input.copyWithCount(if(input.isEmpty)0 else 1);planted=count
    }
    override fun initialize() { refreshSeed();connect() }
    override fun inventoryChange(inventory: Container?) { if(!batching) { refreshSeed();prepare();needPublish() } }
    private fun prepare() {
        refreshSeed()
        val world=loadedWorld()
        if(world!=null)ambient=BiomeClimateService.sample(world,coordinate().x,coordinate().y,coordinate().z).temperatureCelsius
        val bulbs=inventory.getItem(1)
        val lamp=Utils.getItemObject(bulbs) as? LampDescriptor
        installedBulbs=if(lamp!=null && lamp.getLifeInTag(bulbs)>0)minOf(BULBS,bulbs.count) else 0
        val lampWatts=(lamp?.lampData?.nominalP ?: 0.0)*installedBulbs
        lightFactor=installedBulbs.toDouble()/BULBS*(lamp?.lampData?.nominalLightValue ?: 0)/14.0
        val element=Utils.getItemObject(inventory.getItem(2)) as? HeatingCorpElement
        val waterNeeded=planted*50
        val healthy=supply.voltage.isFinite() && abs(supply.voltage)<=VOLTS*1.2
        requested=enabled && world!=null && planted>0 && installedBulbs>0 && pending==null && healthy &&
            (reserved || water.tank.fluidAmount>=waterNeeded)
        if(requested && !reserved) {
            water.tank.drain(waterNeeded,FluidAction.EXECUTE);reserved=true
        }
        heaterOn=requested && element!=null && when {
            cycle.temperature<target-.5 -> true
            cycle.temperature>target+.5 -> false
            else -> heaterOn
        }
        lights.resistance=if(requested && lampWatts>0)VOLTS*VOLTS/lampWatts else 1e19
        val heatWatts=element?.nominalPower?.coerceIn(0.0,1500.0) ?: 0.0
        heater.resistance=if(heaterOn && heatWatts>0)VOLTS*VOLTS/heatWatts else 1e19
        status=when {
            !enabled -> "OFF"
            world==null -> "UNLOADED"
            planted==0 -> "IDLE"
            installedBulbs==0 -> "BULBS"
            !healthy -> "OVERVOLTAGE"
            pending!=null -> "BLOCKED"
            !reserved -> "WATER"
            abs(supply.voltage)<24 -> "POWER"
            GrowingCycle.temperatureFactor(cycle.temperature)<=0 -> "TEMPERATURE"
            else -> "GROWING"
        }
    }
    private fun tick(seconds: Double) {
        val world=loadedWorld() ?: return
        lightRefresh-=seconds
        if(lightRefresh<=0) {
            lightRefresh=1.0
            if(enabled && requested && lights.power>1 && abs(supply.voltage)>24) {
                val at=coordinate().pos.relative(front.toFacing()).above(2)
                if(world.hasChunkAt(at))mods.eln.lightblock.LightBlockEntity.addLight(
                    Coordinate(at.x,at.y,at.z,coordinate().dimension),(15*lightFactor*(abs(supply.voltage)/VOLTS).let { it*it }).toInt().coerceIn(0,15),5)
            }
        }
        if(enabled && requested && lights.power>0 && abs(supply.voltage) in 24.0..288.0) {
            bulbAgeSeconds+=seconds
            if(bulbAgeSeconds>=1) {
                val lamp=Utils.getItemObject(inventory.getItem(1)) as? LampDescriptor
                repeat(bulbAgeSeconds.toInt().coerceAtMost(10)) {
                    lamp?.decreaseLampLife(inventory.getItem(1),lamp.lampData.nominalU*abs(supply.voltage)/VOLTS)
                }
                bulbAgeSeconds%=1.0
            }
        }
        if(!enabled || !reserved || cycle.progress<GrowingCycle.SECONDS)return
        val crop=GrowingCycle.crop(seed) ?: return
        if(pending==null)pending=buildList {
            repeat(planted) {
                val drops=Block.getDrops(crop.getStateForAge(crop.maxAge),world,coordinate().pos,null)
                // One planting item stays in the tray. Export only the surplus harvest.
                var replant=1
                for(drop in drops) {
                    val result=drop.copy()
                    if(result.item===seed.item && replant>0) {
                        val used=minOf(replant,result.count);result.shrink(used);replant-=used
                    }
                    if(!result.isEmpty)add(result)
                }
            }
        }
        val merged=mergeHarvest((FIRST_OUTPUT until SLOTS).map { inventory.getItem(it) },pending!!) ?: return
        batching=true
        try { merged.forEachIndexed { i,stack -> inventory.setItem(FIRST_OUTPUT+i,stack) } }
        finally { batching=false }
        pending=null;reserved=false;cycle.reset();inventory.setChanged();needPublish()
    }
    override fun getFluidHandler()=water
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!cord.plugged && side.isNotY && lrdu==LRDU.Down)supply else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=GrowingBedContainer(player,inventory)
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",supply.voltage)+Utils.plotPower("P:",lights.power+heater.power)
    override fun getWaila()=linkedMapOf(tr("State") to growingStatus(status),tr("Supply") to multiMeterString(front))
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            80 -> enabled=!enabled
            81 -> target=(target+stream.readInt().coerceIn(-1,1)).coerceIn(10.0,35.0)
        }
        prepare();needPublish();return id
    }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeBoolean(enabled);out.writeUTF(status)
        out.writeDouble(supply.voltage);out.writeDouble(lights.power+heater.power);out.writeDouble(cycle.progress)
        out.writeDouble(cycle.temperature);out.writeDouble(target);out.writeInt(water.tank.fluidAmount)
        out.writeInt(installedBulbs);out.writeInt(planted);out.writeBoolean(heaterOn)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(out)
    }
    override fun writeToNBT(tag: CompoundTag) {
        super.writeToNBT(tag);water.save(tag)
        tag.putBoolean("growEnabled",enabled);tag.putDouble("growTarget",target)
        tag.putDouble("growProgress",cycle.progress);tag.putDouble("growTemperature",cycle.temperature)
        tag.putBoolean("growWaterReserved",reserved)
        tag.remove("growHarvest")
        pending?.let { drops -> tag.put("growHarvest",ListTag().apply { drops.forEach { add(it.save(McRegistries.access())) } }) }
    }
    override fun readFromNBT(tag: CompoundTag) {
        super.readFromNBT(tag);water.load(tag);refreshSeed()
        enabled=!tag.contains("growEnabled") || tag.getBoolean("growEnabled")
        target=tag.getDouble("growTarget").takeIf { it.isFinite() && it in 10.0..35.0 } ?: 23.0
        cycle.restore(tag.getDouble("growProgress"),if(tag.contains("growTemperature"))tag.getDouble("growTemperature") else 20.0)
        reserved=planted>0 && tag.getBoolean("growWaterReserved")
        pending=if(reserved && tag.contains("growHarvest"))tag.getList("growHarvest",10).map { ItemStack.parseOptional(McRegistries.access(),it as CompoundTag) }.filterNot { it.isEmpty } else null
    }
}

/** Copy-first insertion makes full-output backpressure atomic, including modded crop loot. */
fun mergeHarvest(slots: List<ItemStack>,drops: List<ItemStack>): List<ItemStack>? {
    val result=slots.map { it.copy() }.toMutableList()
    for(drop in drops) {
        var left=drop.count
        for(i in result.indices)if(ItemStack.isSameItemSameComponents(result[i],drop)) {
            val n=minOf(left,(result[i].maxStackSize-result[i].count).coerceAtLeast(0))
            result[i].grow(n);left-=n
        }
        for(i in result.indices)if(left>0 && result[i].isEmpty) {
            val n=minOf(left,drop.maxStackSize);result[i]=drop.copyWithCount(n);left-=n
        }
        if(left>0)return null
    }
    return result
}
