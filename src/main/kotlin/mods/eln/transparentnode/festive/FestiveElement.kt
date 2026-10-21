package mods.eln.transparentnode.festive

import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.cord.*
import mods.eln.utility.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import java.io.*

open class FestiveElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val electricalLoad=NbtElectricalLoad("electricalLoad").apply { serialResistance=.002 }
    override val cord=ApplianceCord(this,electricalLoad)
    override val cordVoltage get()=nominalVolts.toDouble()
    override fun acceptsCord(rating: CordRating)=rating==CordRating.V120 || rating==CordRating.V240
    override val cordPortable get()=transparentNodeDescriptor is HolidayCandleDescriptor
    override fun cordChanged() { cord.anchor?.let { nominalVolts=it.rating.volts.toInt() };configureLoad() }
    val loadResistor=Resistor(electricalLoad,null)
    var powerChannel="xmas"
    var nominalVolts=120
        private set
    private val watts get()=when(transparentNodeDescriptor) { is ChristmasTreeDescriptor->35.0;is HolidayCandleDescriptor->5.0;else->15.0 }
    val mountOffset get(): Double {
        if(transparentNodeDescriptor !is HolidayCandleDescriptor)return 0.0
        val c=coordinate();val world=c.world();val below=c.pos.below()
        val height=mods.eln.sixnode.lampsocket.LampMounting.surface(world.getBlockState(below).getCollisionShape(world,below).toAabbs(),Direction.YN) ?: 1.0
        return height-1.0
    }
    private var cached: WirelessPowerHandle?=null
    init { slowProcessList.add(IProcess(::tick));slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0));configureLoad() }
    private fun configureLoad() { loadResistor.resistance=nominalVolts.toDouble()*nominalVolts/watts }
    override fun connectJob() {
        if(cord.plugged) { electricalLoadList.add(electricalLoad);electricalComponentList.add(loadResistor) }
        super.connectJob()
    }
    override fun disconnectJob() { super.disconnectJob();electricalLoadList.remove(electricalLoad);electricalComponentList.remove(loadResistor);cached=null }
    private fun tick(dt: Double) {
        if(cord.plugged)WirelessLoadDemand.report(this,null,Double.POSITIVE_INFINITY)
        else {
            val channels=WirelessPowerChannels.channels(powerChannel)
            val here=coordinate()
            if(cached?.let { channels?.contains(it)==true && it.element.isInRange(here) }!=true)
                cached=channels?.filter { it.element.isInRange(here) }?.minByOrNull { it.element.coordinate.trueDistanceTo(here) }
            val supply=cached?.takeIf { it.element.getChannelState(it.id) }
            WirelessLoadDemand.report(this,supply,loadResistor.resistance)
            electricalLoad.state=supply?.element?.powerLoad?.voltage ?: 0.0
        }
        val light=mods.eln.sixnode.lampsocket.LampBrightness.level(electricalLoad.voltage,nominalVolts.toDouble(),.5,15)
        if(node!!.lightValue!=light) { node!!.lightValue=light;needPublish() }
    }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            80->stream.readInt().takeIf { it==120 || it==240 }?.let { if(!cord.plugged) { nominalVolts=it;configureLoad() } }
            81->{ powerChannel=stream.readUTF().trim().take(128);cached=null }
        };needPublish();return id
    }
    override fun initialize() { connect() }
    override fun multiMeterString(side: Direction)=Utils.plotUIP(electricalLoad.voltage,if(cord.plugged)loadResistor.current else electricalLoad.voltage/loadResistor.resistance)
    override fun thermoMeterString(side: Direction)=""
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt);powerChannel=nbt.getString("festiveChannel").ifBlank { "xmas" }
        nominalVolts=nbt.getInt("festiveVolts").takeIf { it==120 || it==240 } ?: 240;configureLoad()
    }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);nbt.putString("festiveChannel",powerChannel);nbt.putInt("festiveVolts",nominalVolts) }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);stream.writeBoolean(node!!.lightValue>0);stream.writeUTF(powerChannel);stream.writeInt(nominalVolts);stream.writeDouble(mountOffset)
    }
}
