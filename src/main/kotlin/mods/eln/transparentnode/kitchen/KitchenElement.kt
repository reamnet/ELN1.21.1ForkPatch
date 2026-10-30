package mods.eln.transparentnode.kitchen

import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sound.SoundCommand
import mods.eln.transparentnode.cord.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

class KitchenElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor),CordDevice {
    val type=(descriptor as KitchenDescriptor).type
    override val inventory=object : TransparentNodeElementInventory(type.slotCount,64,this) {
        override fun getItem(slot: Int): ItemStack = super.getItem(slot).also {
            if(type.refrigerator && slot>=3)mods.eln.compat.RefrigeratorPreservation.touch(it,this@KitchenElement)
        }
        override fun removeItem(slot: Int,amt: Int): ItemStack { preserveFood();return super.removeItem(slot,amt).also(mods.eln.compat.RefrigeratorPreservation::release) }
        override fun removeItemNoUpdate(slot: Int): ItemStack { preserveFood();return super.removeItemNoUpdate(slot).also(mods.eln.compat.RefrigeratorPreservation::release) }
        override fun startOpen(player: Player) {
            if(type.refrigerator && player is net.minecraft.server.level.ServerPlayer && viewers.add(player.uuid))needPublish()
        }
        override fun stopOpen(player: Player) {
            if(player is net.minecraft.server.level.ServerPlayer && viewers.remove(player.uuid))needPublish()
        }
        override fun getAccessibleSlotsFromSide(side: Int)=IntArray(containerSize) { it }
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=stack!=null && KitchenRecipe.allowed(type,slot,stack)
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot==2 || slot==1 && (stack?.`is`(net.minecraft.world.item.Items.BUCKET)==true || stack?.`is`(net.minecraft.world.item.Items.GLASS_BOTTLE)==true) || slot>=3
    }
    val load=NbtElectricalLoad("applianceInput")
    val resistor=Resistor(load,null)
    override val cord=ApplianceCord(this,load)
    override val cordVoltage get()=type.volts
    override val cordPortable get()=type!=KitchenType.FRIDGE
    val work=KitchenWork()
    val batteryHeat=MicrowaveBatteryHeat()
    val batteryJob get()=type==KitchenType.MICROWAVE && MicrowaveBatteryHeat.accepts(inventory.getItem(0))
    var detonating=false
        private set
    private var cachedInputReference: ItemStack?=null
    var enabled=true
    var active=false
        private set
    var temperature=20.0
        private set
    private val viewers=HashSet<java.util.UUID>()
    val doorOpen get()=type.refrigerator && viewers.isNotEmpty()
    private var ambient=20.0
    private var roomClock=0.0
    var status="IDLE"
        private set
    val coldStorage get()=type.refrigerator && temperature<=5.0
    private var foodClock=-1L
    private var foodWasCold=false
    private fun preserveFood() {
        if(!type.refrigerator || net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()==null)return
        val world=node?.coordinate?.world() as? ServerLevel ?: return
        val now=world.gameTime
        val elapsed=if(foodWasCold && foodClock>=0)(now-foodClock).coerceAtLeast(0) else 0
        for(slot in 3 until inventory.containerSize) {
            val stack=inventory.getItem(slot)
            if(coldStorage || elapsed>0)mods.eln.compat.RefrigeratorPreservation.pause(stack,elapsed,now)
        }
        foodClock=now;foodWasCold=coldStorage
    }
    private var recipe: KitchenRecipe?=null
    private var cachedInput=ItemStack.EMPTY
    private var cacheReady=false
    private var publishedStatus=""
    init {
        load.serialResistance=.002
        electricalLoadList.add(load);electricalComponentList.add(resistor)
        slowProcessList.add(IProcess(::tick))
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
    }
    override fun initialize() { rebuildRecipe(false);configure();connect() }
    private fun rebuildRecipe(reset: Boolean) {
        val input=inventory.getItem(0)
        if(reset && (input!==cachedInputReference || input.count!=cachedInput.count || !ItemStack.isSameItemSameComponents(input,cachedInput))) {
            work.joules=0.0;batteryHeat.reset()
        }
        recipe=(coordinate().world() as? ServerLevel)?.let { KitchenRecipe.find(it,type,inventory) }
        cachedInput=input.copy();cachedInputReference=input;cacheReady=true
    }
    override fun inventoryChange(inventory: Container?) { rebuildRecipe(true);configure();needPublish() }
    private val requested get()=enabled && !detonating && (batteryJob || recipe?.canFit(inventory)==true || type.refrigerator && temperature>4.5)
    private fun configure() {
        val connected=requested && abs(load.voltage)<=type.volts*1.2
        active=connected && abs(load.voltage)>=type.volts*.8
        resistor.resistance=if(connected)type.volts*type.volts/type.watts else 1e9
    }
    private fun tick(seconds: Double) {
        if(!cacheReady)rebuildRecipe(false)
        configure()
        val power=if(active)resistor.power.coerceIn(0.0,type.watts*1.1) else 0.0
        preserveFood()
        if(type.refrigerator) {
            roomClock-=seconds
            if(roomClock<=0.0) {
                roomClock=1.0
                val world=node?.coordinate?.world() as? ServerLevel
                if(world!=null) {
                    if(viewers.removeIf { id ->
                        val p=world.server.playerList.getPlayer(id)
                        p==null || p.level()!==world || (p.containerMenu as? KitchenContainer)?.deviceInventory!==inventory
                    })needPublish()
                    ambient=getAmbientTemperatureCelsius()
                }
            }
            temperature=RefrigeratorThermal.advance(temperature,ambient,power/type.watts,doorOpen,seconds)
        }
        if(batteryJob && active && power>0 && batteryHeat.advance(seconds,power))detonateBattery()
        val job=recipe
        status=when {
            detonating -> "EXPLODING"
            !enabled -> "OFF"
            abs(load.voltage)>type.volts*1.2 -> "OVERVOLTAGE"
            requested && abs(load.voltage)<type.volts*.8 -> "NO_POWER"
            doorOpen -> "DOOR_OPEN"
            batteryJob && batteryHeat.hissing -> "HISSING"
            batteryJob -> "HEATING_BATTERY"
            job!=null && !job.canFit(inventory) -> "BLOCKED"
            type.refrigerator && temperature>5.0 -> "COOLING"
            job!=null && power>1 -> "WORKING"
            else -> "IDLE"
        }
        if(job!=null && job.canFit(inventory) && enabled && power>0 && (!type.refrigerator || temperature<=5.0)) {
            if(work.advance(seconds,power,type.watts*type.seconds)) {
                work.joules=0.0;job.complete(inventory)
                play(SoundCommand(if(type==KitchenType.TOASTER)"eln:appliance_toaster_pop" else "eln:appliance_done").verySmallRange().mulVolume(.35f,1f))
            }
        }
        if(publishedStatus!=status) { publishedStatus=status;needPublish() }
    }
    private fun detonateBattery() {
        if(detonating)return
        detonating=true;active=false;resistor.resistance=1e9;needPublish()
        // Slow-process lists are snapshotted; defer world removal until the next tick.
        mods.eln.sim.process.destruct.DelayedDestruction(object : mods.eln.sim.process.destruct.IDestructible {
            override fun describe()="Microwaved battery"
            override fun destructImpl() {
                val n=node ?: return
                if(n.isDestructing)return
                val c=coordinate();val world=c.world() as? ServerLevel ?: return
                inventory.getItem(0).shrink(1)
                n.physicalSelfDestruction(0f)
                world.explode(null as net.minecraft.world.entity.Entity?,c.x+.5,c.y+.5,c.z+.5,2f,false,
                    if(mods.eln.Eln.config.getBooleanOrElse("gameplay.hazards.explosionsEnabled",false))
                        net.minecraft.world.level.Level.ExplosionInteraction.BLOCK else net.minecraft.world.level.Level.ExplosionInteraction.NONE)
            }
        },0.0)
    }
    override fun readItemStackNBT(nbt: CompoundTag?) {
        nbt?.takeIf { it.contains("carriedAppliance") }?.let {
            val placedFront=front;readFromNBT(it.getCompound("carriedAppliance"));front=placedFront
        }
    }
    override fun onBreakElement() {
        if(cord.moving) { if(useUuid())stop(getUuid());return }
        cord.onBroken()
        if(!detonating) { super.onBreakElement();return }
        // A ruptured microwave is destroyed; spare contents drop once, not the appliance.
        if(useUuid())stop(getUuid())
        node!!.dropInventory(inventory)
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!cord.plugged && side.isNotY && lrdu==LRDU.Down)load else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=KitchenContainer(player,inventory,type)
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        if(id.toInt()==80) { enabled=!enabled;configure();needPublish() }
        return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeBoolean(enabled);stream.writeBoolean(active);stream.writeDouble(if(batteryJob)batteryHeat.joules/MicrowaveBatteryHeat.BLAST_JOULES else work.joules/(type.watts*type.seconds))
        stream.writeDouble(load.voltage);stream.writeDouble(resistor.power);stream.writeDouble(temperature);stream.writeUTF(status)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream);
        stream.writeBoolean(doorOpen)
    }
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        enabled=!nbt.contains("kitchenEnabled") || nbt.getBoolean("kitchenEnabled")
        work.joules=nbt.getDouble("kitchenJoules").takeIf { it.isFinite() }?.coerceIn(0.0,type.watts*type.seconds) ?: 0.0
        temperature=if(nbt.contains("kitchenTemperature"))nbt.getDouble("kitchenTemperature").takeIf { it.isFinite() }?.coerceIn(3.5,80.0) ?: 20.0 else 20.0
        viewers.clear();roomClock=0.0
        batteryHeat.read(nbt);cacheReady=false
        foodClock=if(nbt.contains("fridgeFoodClock"))nbt.getLong("fridgeFoodClock") else -1L
        foodWasCold=nbt.getBoolean("fridgeFoodWasCold")
        preserveFood()
    }
    override fun writeToNBT(nbt: CompoundTag) {
        preserveFood()
        super.writeToNBT(nbt);nbt.putLong("fridgeFoodClock",foodClock);nbt.putBoolean("fridgeFoodWasCold",foodWasCold);nbt.putBoolean("kitchenEnabled",enabled);nbt.putDouble("kitchenJoules",work.joules);nbt.putDouble("kitchenTemperature",temperature);batteryHeat.write(nbt)
    }
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",load.voltage)+Utils.plotPower("P:",resistor.power)
    override fun getWaila()=linkedMapOf(tr("State") to kitchenStatus(status),tr("Voltage") to Utils.plotVolt("",load.voltage),tr("Power") to Utils.plotPower("",resistor.power))
}
