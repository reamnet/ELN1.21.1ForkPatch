package mods.eln.integration.electroenergetics

import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.energy.IEnergyStorage
import java.io.DataInputStream
import java.io.DataOutputStream

class CeeBridgeElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val input=NbtElectricalLoad("ceeBridgeInput").apply { serialResistance=.00001 }
    val consumer=Resistor(input,null).apply { resistance=1e19 }
    val budget=CeeEnergyBudget()
    var nominalVolts=3200.0;private set
    var maxWatts=100_000.0;private set
    var nominalAmps=40.0;private set
    var enabled=true;private set
    var scale: CeeConfig.Scale?=null;private set
    var status="MISSING";private set
    private var apiClock=1.0
    private var filling=true
    private val limit get()=minOf(maxWatts*CeeEnergyBudget.EFFICIENCY,scale?.maxWatts ?: 0.0)
    val storage=object: IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int,simulate: Boolean)=0
        override fun extractEnergy(maxExtract: Int,simulate: Boolean): Int {
            val rate=scale ?: return 0
            val world=DimensionIds.serverLevel(coordinate().dimension) ?: return 0
            if(!enabled || world.chunkSource.getChunkNow(coordinate().x shr 4,coordinate().z shr 4)==null)return 0
            val extracted=budget.extract(maxExtract,simulate,world.gameTime,rate.joulesPerFe,limit)
            return extracted
        }
        override fun getEnergyStored()=scale?.let { budget.available(it.joulesPerFe) } ?: 0
        override fun getMaxEnergyStored()=scale?.let { (CeeEnergyBudget.CAPACITY/it.joulesPerFe).coerceIn(0.0,Int.MAX_VALUE.toDouble()).toInt() } ?: 0
        override fun canExtract()=enabled && scale!=null
        override fun canReceive()=false
    }
    init {
        electricalLoadList.add(input);electricalComponentList.add(consumer)
        electricalProcessList.add(IProcess { dt->
            budget.charge(consumer.power,dt)
            if(budget.joules>=CeeEnergyBudget.CAPACITY-maxWatts*.025)filling=false
            if(budget.joules<CeeEnergyBudget.CAPACITY*.80)filling=true
            val ready=enabled && scale!=null && filling && kotlin.math.abs(input.voltage)<=nominalVolts*1.25
            // Fixed impedance is stable on long grids; real input power follows V squared.
            consumer.resistance=if(ready)maxOf(nominalVolts*nominalVolts/maxWatts,nominalVolts/nominalAmps) else 1e19
        })
        slowProcessList.add(IProcess { dt->apiClock+=dt;if(apiClock>=1) { apiClock%=1.0;scale=CeeConfig.read() }
            status=when { !enabled->"OFF";!CeeConfig.present()->"MISSING";scale==null->"API";kotlin.math.abs(input.voltage)>nominalVolts*1.25->"OVERVOLTAGE";!filling->"FULL";consumer.power<1->"NO_INPUT";else->"READY" }
        })
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,.25))
    }
    override fun initialize() { connect() }
    override fun getEnergyStorage(side: net.minecraft.core.Direction?)=if(side==null || side==front.inverse.toFacing())storage else null
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(side!=front.inverse && lrdu==LRDU.Down)input else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun multiMeterString(side: Direction)=Utils.plotUIP(input.voltage,input.current)
    override fun thermoMeterString(side: Direction)=""
    override fun getWaila()=linkedMapOf(tr("State") to ceeStatus(status),tr("Input") to multiMeterString(front),tr("Stored energy") to tr("%1$ J",budget.joules.toInt()))
    fun settings(volts: Double,watts: Double,amps: Double): Boolean {
        if(!volts.isFinite() || volts !in 12.0..230_000.0 || !watts.isFinite() || watts !in 100.0..1_000_000.0 || !amps.isFinite() || amps !in .1..2000.0)return false
        nominalVolts=volts;maxWatts=watts;nominalAmps=amps;needPublish();return true
    }
    private fun write(tag: CompoundTag) { tag.putDouble("ceeNominalVolts",nominalVolts);tag.putDouble("ceeInputWatts",maxWatts);tag.putDouble("ceeNominalAmps",nominalAmps);tag.putDouble("ceeJoules",budget.joules);tag.putBoolean("ceeEnabled",enabled) }
    private fun read(tag: CompoundTag) {
        settings(tag.getDouble("ceeNominalVolts").takeIf { it.isFinite() && it in 12.0..230_000.0 } ?: 3200.0,
            tag.getDouble("ceeInputWatts").takeIf { it.isFinite() && it in 100.0..1_000_000.0 } ?: 100_000.0,
            tag.getDouble("ceeNominalAmps").takeIf { it.isFinite() && it in .1..2000.0 } ?: 40.0)
        budget.restore(tag.getDouble("ceeJoules"));enabled=!tag.contains("ceeEnabled") || tag.getBoolean("ceeEnabled")
    }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);write(tag) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);read(tag) }
    override fun getItemStackNBT()=CompoundTag().also(::write)
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)read(tag) }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeDouble(nominalVolts);out.writeDouble(maxWatts);out.writeDouble(nominalAmps);out.writeBoolean(enabled)
        out.writeDouble(input.voltage);out.writeDouble(consumer.power);out.writeDouble(budget.joules);out.writeDouble(scale?.wattsPerFeTick ?: 0.0);out.writeDouble(limit);out.writeUTF(status)
    }
    override fun networkUnserialize(input: DataInputStream): Byte {
        val id=super.networkUnserialize(input)
        when(id.toInt()) { 80->settings(input.readDouble(),input.readDouble(),input.readDouble());81->enabled=input.readBoolean() }
        needPublish();return id
    }
}
