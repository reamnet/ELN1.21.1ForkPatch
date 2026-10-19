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
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

class KitchenElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor) {
    val type=(descriptor as KitchenDescriptor).type
    override val inventory=object : TransparentNodeElementInventory(if(type==KitchenType.FRIDGE)30 else 3,64,this) {
        override fun getAccessibleSlotsFromSide(side: Int)=IntArray(containerSize) { it }
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=stack!=null && KitchenRecipe.allowed(type,slot,stack)
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot==2 || slot==1 && (stack?.`is`(net.minecraft.world.item.Items.BUCKET)==true || stack?.`is`(net.minecraft.world.item.Items.GLASS_BOTTLE)==true) || slot>=3
    }
    val load=NbtElectricalLoad("applianceInput")
    val resistor=Resistor(load,null)
    val work=KitchenWork()
    var enabled=true
    var active=false
        private set
    var temperature=20.0
        private set
    var status="IDLE"
        private set
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
        if(reset && !ItemStack.isSameItemSameComponents(input,cachedInput))work.joules=0.0
        recipe=(coordinate().world() as? ServerLevel)?.let { KitchenRecipe.find(it,type,inventory) }
        cachedInput=input.copy();cacheReady=true
    }
    override fun inventoryChange(inventory: Container?) { rebuildRecipe(true);configure();needPublish() }
    private val requested get()=enabled && (recipe?.canFit(inventory)==true || type==KitchenType.FRIDGE && temperature>4.5)
    private fun configure() {
        val connected=requested && abs(load.voltage)<=type.volts*1.2
        active=connected && abs(load.voltage)>=type.volts*.8
        resistor.resistance=if(connected)type.volts*type.volts/type.watts else 1e9
    }
    private fun tick(seconds: Double) {
        if(!cacheReady)rebuildRecipe(false)
        configure()
        val power=if(active)resistor.power.coerceIn(0.0,type.watts*1.1) else 0.0
        if(type==KitchenType.FRIDGE) {
            val cooling=(power/type.watts).coerceIn(0.0,1.0)*.85
            temperature=(temperature+seconds*(.025-cooling)).coerceIn(3.5,20.0)
        }
        val job=recipe
        status=when {
            !enabled -> "OFF"
            abs(load.voltage)>type.volts*1.2 -> "OVERVOLTAGE"
            requested && abs(load.voltage)<type.volts*.8 -> "NO_POWER"
            job!=null && !job.canFit(inventory) -> "BLOCKED"
            type==KitchenType.FRIDGE && temperature>5.0 -> "COOLING"
            job!=null && power>1 -> "WORKING"
            else -> "IDLE"
        }
        if(job!=null && job.canFit(inventory) && enabled && power>0 && (type!=KitchenType.FRIDGE || temperature<=5.0)) {
            if(work.advance(seconds,power,type.watts*type.seconds)) {
                work.joules=0.0;job.complete(inventory)
                play(SoundCommand("eln:beep_accept").verySmallRange().mulVolume(.25f,1.3f))
            }
        }
        if(publishedStatus!=status) { publishedStatus=status;needPublish() }
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(side.isNotY && lrdu==LRDU.Down)load else null
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
        stream.writeBoolean(enabled);stream.writeBoolean(active);stream.writeDouble(work.joules/(type.watts*type.seconds))
        stream.writeDouble(load.voltage);stream.writeDouble(resistor.power);stream.writeDouble(temperature);stream.writeUTF(status)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream)
    }
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        enabled=!nbt.contains("kitchenEnabled") || nbt.getBoolean("kitchenEnabled")
        work.joules=nbt.getDouble("kitchenJoules").takeIf { it.isFinite() }?.coerceIn(0.0,type.watts*type.seconds) ?: 0.0
        temperature=if(nbt.contains("kitchenTemperature"))nbt.getDouble("kitchenTemperature").takeIf { it.isFinite() }?.coerceIn(3.5,20.0) ?: 20.0 else 20.0
        cacheReady=false
    }
    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt);nbt.putBoolean("kitchenEnabled",enabled);nbt.putDouble("kitchenJoules",work.joules);nbt.putDouble("kitchenTemperature",temperature)
    }
    override fun multiMeterString(side: Direction)=Utils.plotVolt("U:",load.voltage)+Utils.plotPower("P:",resistor.power)
    override fun getWaila()=linkedMapOf(tr("State") to kitchenStatus(status),tr("Voltage") to Utils.plotVolt("",load.voltage),tr("Power") to Utils.plotPower("",resistor.power))
}
