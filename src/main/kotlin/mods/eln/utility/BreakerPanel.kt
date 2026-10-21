package mods.eln.utility

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.gridnode.GridElement
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalbreaker.ElectricalBreakerDescriptor
import mods.eln.sixnode.lampsupply.LampSupplyRange
import mods.eln.sixnode.wirelesssignal.*
import mods.eln.sixnode.wirelesssignal.aggregator.*
import mods.eln.transparentnode.standby.TransferContact
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** Persisted trip latch. Removing the fault alone never silently recloses a tripped branch. */
class PanelBreaker {
    companion object { private const val TRIP_HEAT=198.0 } // (10² - 1) × 2 seconds
    var enabled=true
        private set
    var tripped=false
        private set
    private var heat=0.0
    fun operate() { if(tripped) { tripped=false;heat=0.0;enabled=true } else enabled=!enabled }
    fun removed() { tripped=false;heat=0.0 }
    fun step(seconds: Double,amps: Double,rating: Double): Boolean {
        if(!seconds.isFinite() || seconds<=0 || !rating.isFinite() || rating<=0 || !amps.isFinite())return false
        if(!enabled || tripped) { heat=(heat-seconds*6.6).coerceAtLeast(0.0);return false }
        val ratio=abs(amps)/rating
        heat=if(ratio>1.05)heat+seconds*(ratio*ratio-1) else (heat-seconds*6.6).coerceAtLeast(0.0)
        if(heat<TRIP_HEAT)return false
        tripped=true;heat=0.0;return true
    }
    fun write(tag: CompoundTag,prefix: String) { tag.putBoolean(prefix+"Enabled",enabled);tag.putBoolean(prefix+"Tripped",tripped);tag.putDouble(prefix+"Heat",heat) }
    fun read(tag: CompoundTag,prefix: String) { enabled=!tag.contains(prefix+"Enabled") || tag.getBoolean(prefix+"Enabled");tripped=tag.getBoolean(prefix+"Tripped");heat=tag.getDouble(prefix+"Heat").takeIf { it.isFinite() && it>=0 }?.coerceAtMost(TRIP_HEAT) ?: 0.0 }
}

class BreakerPanelElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): GridElement(node,descriptor,64) {
    override val allowsNewGridLinks=false
    override val inventory=object: TransparentNodeElementInventory(12,1,this@BreakerPanelElement) {
        override fun getAccessibleSlotsFromSide(side: Int)=IntArray(12) { it }
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=slot in 0..11 && breakerRating(stack)>0
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=true
    }
    val buses=List(3) { NbtElectricalLoad("panelBus$it").apply { serialResistance=.00001 } }
    private val wiredService=WiredServiceSplit(this,buses)
    inner class Circuit(val id: Int) {
        val load=NbtElectricalLoad("panelCircuit$id").apply { serialResistance=.00001 }
        val contacts=buses.map { TransferContact(it,load) }
        val demand=Resistor(load,null).apply { resistance=1e19 }
        val breaker=PanelBreaker()
        var powerChannel=""
        var controlChannel=""
        var aggregator=0
        var use240=false
        var dusk=false
        var accumulator=0.0
        val fixtureDemand=java.util.IdentityHashMap<Any,Double>()
        var wirelessOn=true
        var previousRating=0.0
        val aggregators=arrayOf<IWirelessSignalAggregator>(BiggerAggregator(),SmallerAggregator(),ToogleAggregator())
        val bus get()=if(use240)2 else id%2
        val rating get()=breakerRating(inventory.getItem(id))
        val handle=WirelessPowerHandle(object: WirelessPowerSupply {
            override val owner get()=this@BreakerPanelElement
            override val coordinate get()=this@BreakerPanelElement.coordinate()
            override val powerLoad get()=load
            override val range get()=transmissionRange
            override fun getChannelState(id: Int)=contacts[bus].closed
            override fun setDemand(consumer: Any,resistance: Double) { if(resistance.isFinite() && resistance>0)fixtureDemand[consumer]=1/resistance }
            override fun removeDemand(consumer: Any) { fixtureDemand.remove(consumer) }
            override fun addToRp(resistance: Double) { if(resistance.isFinite() && resistance>0)accumulator+=1/resistance }
        },id)
        fun refreshDemand() {
            val conductance=accumulator+fixtureDemand.values.sum()
            demand.resistance=if(conductance>0)1/conductance else 1e19;accumulator=0.0
        }
        fun configure() { contacts.forEachIndexed { i,c -> c.closed=i==bus && rating>0 && breaker.enabled && !breaker.tripped && wirelessOn } }
        fun write(tag: CompoundTag) {
            val p="panel$id";breaker.write(tag,p);tag.putString(p+"Power",powerChannel);tag.putString(p+"Control",controlChannel);tag.putInt(p+"Aggregator",aggregator);tag.putBoolean(p+"240",use240);tag.putBoolean(p+"Dusk",dusk)
            (aggregators[2] as ToogleAggregator).writeToNBT(tag,p+"Toggle")
        }
        fun read(tag: CompoundTag) {
            val p="panel$id";breaker.read(tag,p);powerChannel=tag.getString(p+"Power").take(64);controlChannel=tag.getString(p+"Control").take(64);aggregator=tag.getInt(p+"Aggregator").coerceIn(0,2);use240=tag.getBoolean(p+"240");dusk=tag.getBoolean(p+"Dusk")
            if(tag.contains(p+"Togglestate"))(aggregators[2] as ToogleAggregator).readFromNBT(tag,p+"Toggle")
            wirelessOn=!dusk && (controlChannel.isBlank() || controlChannel.equals("true",true))
            previousRating=rating;configure()
        }
    }
    val circuits=List(12) { Circuit(it) }
    var selectedCircuit=0
        private set
    var selectedTerminal=50
        private set
    var transmissionRange=32
        private set
    private val txSet=HashMap<String,HashSet<IWirelessSignalTx>>()
    private val txStrength=HashMap<IWirelessSignalTx,Double>()
    private var wirelessClock=0.0
    private var registered=false
    var darkOutside=false
        private set
    internal fun updateDarkness(darkness: Int) { darkOutside=if(darkOutside)darkness>2 else darkness>=4 }
    init {
        electricalLoadList.addAll(buses)
        electricalProcessList.add(IProcess { wiredService.update() })
        circuits.forEach { c -> electricalLoadList.add(c.load);electricalComponentList.addAll(c.contacts);electricalComponentList.add(c.demand) }
        slowProcessList.add(IProcess(::step))
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
    }
    private fun step(dt: Double) {
        (transparentNodeDescriptor as BreakerPanelDescriptor).checkSupport(this)
        (coordinate().world() as? net.minecraft.server.level.ServerLevel)?.let { updateDarkness(if(it.dimensionType().hasSkyLight())it.skyDarken else 15) }
        wirelessClock-=dt
        if(wirelessClock<=0) { wirelessClock=1.0;txSet.clear();txStrength.clear();WirelessUtils.getTx(WirelessUtils.buildSpot(coordinate(),null,0),txSet,txStrength) }
        circuits.forEach { c ->
            c.refreshDemand()
            c.wirelessOn=when { c.controlChannel.isBlank() || c.controlChannel.equals("true",true)->true;c.controlChannel.equals("false",true)->false;else->txSet[c.controlChannel]?.let { c.aggregators[c.aggregator].aggregate(it)>=.5 } ?: false }
            if(c.dusk)c.wirelessOn=c.wirelessOn && darkOutside
            if(c.breaker.step(dt,c.contacts[c.bus].current,c.rating)) { play(mods.eln.sound.SoundCommand("eln:circuit_breaker_close").smallRange().mulVolume(.22));needPublish() }
            c.configure()
        }
    }
    private fun register() { circuits.forEach { WirelessPowerChannels.register(it.powerChannel,it.handle) };registered=true }
    override fun connectJob() { super.connectJob();circuits.forEach { it.configure() };register() }
    override fun disconnectJob() { registered=false;super.disconnectJob() }
    override fun inventoryChange(inventory: Container?) {
        circuits.forEach { c -> val next=c.rating;if(next!=c.previousRating) { c.breaker.removed();c.previousRating=next };c.configure() }
        needPublish()
    }
    fun setPowerChannel(index: Int,value: String) {
        val c=circuits.getOrNull(index) ?: return
        WirelessPowerChannels.unregister(c.handle);c.powerChannel=value.trim().take(64)
        if(registered)WirelessPowerChannels.register(c.powerChannel,c.handle)
        needPublish()
    }
    override fun selectedGridTerminal(side: Direction)=selectedTerminal
    override fun getGridElectricalLoad(side: Direction)=buses[2]
    override fun gridLoads(terminal: Int): List<ElectricalLoad> = when(terminal) { in 0..5->listOf(buses[2]);50->buses;40,41,42->listOf(buses[terminal-40]);in 64..75->listOf(circuits[terminal-64].load);else->emptyList() }
    override fun gridCircuitKey(terminal: Int)=if(terminal<6)"LEGACY" else if(terminal in 64..75)ServiceTerminals.key(40+circuits[terminal-64].bus) else ServiceTerminals.key(terminal)
    override fun renderWireCount(terminal: Int)=if(terminal==50)3 else 1
    override fun gridCablePoint(terminal: Int,wire: Int)=Vec3(.36,if(terminal>=64)-.43 else .40,if(terminal==50)(wire-1)*.22 else 0.0)
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): ElectricalLoad? {
        if(!(transparentNodeDescriptor as BreakerPanelDescriptor).acceptsCable(front,side,lrdu))return null
        return when(side) { front.left()->buses[0];front.right()->buses[1];Direction.YP->buses[2];Direction.YN->circuits[selectedCircuit].load;else->null }
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BreakerPanelContainer(player,inventory)
    override fun multiMeterString(side: Direction)=tr("Circuit %1$: %2$",selectedCircuit+1,Utils.plotUIP(circuits[selectedCircuit].load.voltage,circuits[selectedCircuit].load.current))
    override fun thermoMeterString(side: Direction)=""
    override fun getWaila()=linkedMapOf(tr("Circuits") to tr("%1$ installed; %2$ tripped",circuits.count { it.rating>0 },circuits.count { it.breaker.tripped }),tr("Selected circuit") to multiMeterString(front))
    private fun writeSettings(nbt: CompoundTag) { circuits.forEach { it.write(nbt) };nbt.putInt("panelSelected",selectedCircuit);nbt.putInt("panelTerminal",selectedTerminal);nbt.putInt("panelRange",transmissionRange) }
    private fun readSettings(nbt: CompoundTag) { circuits.forEach { it.read(nbt) };selectedCircuit=nbt.getInt("panelSelected").coerceIn(0,11);selectedTerminal=nbt.getInt("panelTerminal").takeIf { it==50 || it in 40..42 || it in 64..75 } ?: 50;transmissionRange=nbt.getInt("panelRange").takeIf(LampSupplyRange::isSelectable) ?: 32 }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeSettings(nbt) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);readSettings(nbt) }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)readSettings(nbt) }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);stream.writeInt(selectedCircuit);stream.writeInt(selectedTerminal);stream.writeInt(transmissionRange)
        circuits.forEach { c -> stream.writeDouble(c.rating);stream.writeBoolean(c.breaker.enabled);stream.writeBoolean(c.breaker.tripped);stream.writeBoolean(c.contacts[c.bus].closed);stream.writeBoolean(c.use240);stream.writeBoolean(c.dusk);stream.writeUTF(c.powerChannel);stream.writeUTF(c.controlChannel);stream.writeInt(c.aggregator);stream.writeDouble(c.load.voltage);stream.writeDouble(c.contacts[c.bus].current) }
        node!!.lrduCubeMask.getTranslate(front.inverse).serialize(stream)
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        fun channel(): Pair<Int,String>? { val text=stream.readUTF();val split=text.indexOf('\n');if(split<1)return null;val i=text.substring(0,split).toIntOrNull()?.takeIf { it in 0..11 } ?: return null;return i to text.substring(split+1).take(64) }
        when(id.toInt()) {
            80->stream.readInt().takeIf { it in 0..11 }?.let { if(it!=selectedCircuit) { selectedCircuit=it;reconnect() } }
            81->channel()?.let { setPowerChannel(it.first,it.second) }
            82->channel()?.let { circuits[it.first].controlChannel=it.second }
            83->stream.readInt().takeIf { it in 0..11 }?.let { circuits[it].breaker.operate();circuits[it].configure();play(mods.eln.sound.SoundCommand("eln:circuit_breaker_close").smallRange().mulVolume(.18)) }
            84->stream.readInt().takeIf { it in 0..11 }?.let { val c=circuits[it];c.use240=!c.use240;c.configure() }
            85->stream.readInt().takeIf { it in 0..11 }?.let { circuits[it].aggregator=(circuits[it].aggregator+1)%3 }
            86->stream.readInt().takeIf(LampSupplyRange::isSelectable)?.let { transmissionRange=it }
            88->stream.readInt().takeIf { it in 0..11 }?.let { circuits[it].dusk=!circuits[it].dusk }
            87->stream.readInt().takeIf { it==50 || it in 40..42 || it in 64..75 }?.let { selectedTerminal=it }
        }
        needPublish();return id
    }
    companion object {
        fun breakerRating(stack: ItemStack?): Double {
            val d=if(stack==null || stack.isEmpty)null else GenericItemBlockUsingDamageDescriptor.getDescriptor(stack) as? ElectricalBreakerDescriptor
            return d?.currentLimit?.let { if(it.isFinite() && it>0)it else 400.0 } ?: 0.0
        }
    }
}
