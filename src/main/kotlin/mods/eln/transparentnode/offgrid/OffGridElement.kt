package mods.eln.transparentnode.offgrid

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.item.IConfigurable
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.transparentnode.renewable.SolarPowerUnit
import mods.eln.utility.PoleHardware
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream

class OffGridElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),IConfigurable {
    val cabinet=descriptor as OffGridDescriptor
    val type=cabinet.type
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val bank=if(type.kwh>0)LifepoBank(this,type.kwh) else null
    val ac=if(type==OffGridType.INVERTER || type==OffGridType.AC_CHARGER)InverterCharger(this,type==OffGridType.INVERTER) else null
    val solar=if(type==OffGridType.MPPT)SolarPowerUnit(this,false,true) else null
    val protection=if(type==OffGridType.COMBINER || type==OffGridType.DISCONNECT)DcProtection(this,type==OffGridType.COMBINER) else null
    private val controllers: List<ConservativePowerProcess> get()=bank?.let { listOf(it.controller) } ?: ac?.controllers ?: solar?.let { listOf(it.controller) } ?: emptyList()
    init {
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
        slowProcessList.add(IProcess { cabinet.checkSupport(this) })
    }
    override fun initialize() { connect() }
    override fun connectJob() { super.connectJob();controllers.forEach(Eln.simulator.mna::addProcess) }
    override fun disconnectJob() { controllers.forEach(Eln.simulator.mna::removeProcess);super.disconnectJob() }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun onNeighborBlockChange() { super.onNeighborBlockChange();cabinet.checkSupport(this) }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): mods.eln.sim.ElectricalLoad? {
        if(!cabinet.acceptsCable(front,side,lrdu))return null
        bank?.let { return when(side) { front.left()->it.positive;front.right()->it.negative;else->null } }
        ac?.let { return when(side) { front.left()->it.dc;front.right()->if(it.inverter)it.load else null;front->it.grid;front.inverse->it.negative;else->null } }
        solar?.let { return when(side) { front.left()->it.solar;front.right()->it.output;front.inverse->it.negative;front->it.batteryReturn;else->null } }
        protection?.let { return when(side) { front.left()->it.first;front.right()->it.output;front->if(it.combiner)it.second else null;front.inverse->if(it.combiner)it.negative else null;else->null } }
        return null
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        val p=protection?.takeIf { it.combiner } ?: return false
        if(PoleHardware.extract(player,p.fuses)) { p.update();needPublish();return true }
        if(!mods.eln.utility.UtilityItems.isFuse(player.mainHandItem))return false
        val fuse=PoleHardware.fuse(player.mainHandItem)
        if(fuse==null || !fuse.spent && fuse.amps<=30) {
            p.fuses.firstOrNull { it.blown || !it.hasCartridge }?.let {
                if(it.insertCartridge(player.mainHandItem) && !player.abilities.instabuild)player.mainHandItem.shrink(1)
                p.update();needPublish()
            }
        } else player.displayClientMessage(net.minecraft.network.chat.Component.literal(tr("PV combiner accepts fuse cartridges rated at 30 A or less")),true)
        return true
    }
    private fun readState(t: CompoundTag) { bank?.state?.read(t);ac?.read(t);solar?.read(t);protection?.read(t) }
    private fun writeState(t: CompoundTag) { bank?.state?.write(t);ac?.write(t);solar?.write(t);protection?.write(t) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);readState(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeState(nbt) }
    override fun getItemStackNBT()=CompoundTag().also { writeState(it);if(solar!=null)it.putDouble("solarJoules",0.0) }
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null)readState(nbt.copy().also { if(solar!=null)it.putDouble("solarJoules",0.0) }) }
    override fun writeConfigTool(t: CompoundTag,invoker: Player) {
        t.putBoolean("offGridEnabled",isEnabled())
        ac?.let { t.putDouble("islandTarget",it.target);t.putDouble("islandChargeAmps",it.chargeAmps);t.putInt("islandMode",it.mode.ordinal) }
    }
    override fun readConfigTool(t: CompoundTag,invoker: Player) { configure(t) }
    internal fun configure(t: CompoundTag) {
        if(t.contains("offGridEnabled"))enable(t.getBoolean("offGridEnabled"))
        ac?.let {
            if(t.contains("islandTarget"))it.setVoltage(t.getDouble("islandTarget"))
            if(t.contains("islandChargeAmps"))it.setChargeAmps(t.getDouble("islandChargeAmps"))
            if(t.contains("islandMode"))it.mode=IslandMode.entries.getOrNull(t.getInt("islandMode")) ?: it.mode
        };needPublish()
    }
    fun isEnabled()=bank?.state?.enabled ?: ac?.enabled ?: solar?.enabled ?: protection?.enabled ?: false
    fun enable(value: Boolean) { bank?.state?.enabled=value;ac?.enabled=value;solar?.enabled=value;protection?.let { it.enabled=value;it.update() } }
    override fun networkUnserialize(input: DataInputStream): Byte {
        val id=super.networkUnserialize(input)
        try { when(id.toInt()) {
            81->enable(!isEnabled())
            82->ac?.let { it.mode=IslandMode.entries[(it.mode.ordinal+1)%IslandMode.entries.size];it.reset() }
            83->{ bank?.let { if(it.volts in 0.0..58.4 && it.temperature.celsius<60)it.state.fault="" };ac?.reset();protection?.let { it.fault="";it.update() } }
            84->ac?.setChargeAmps(input.readDouble())
            85->ac?.setVoltage(input.readDouble())
        } } catch(_: java.io.IOException) {}
        needPublish();return id
    }
    fun display()=OffGridDisplay().apply {
        enabled=isEnabled()
        bank?.let { energy=it.state.energy;capacity=it.state.capacity;dc=it.volts;watts=it.watts;status=it.status;amps=it.source.current;temperature=it.temperature.celsius }
        ac?.let { dc=it.batteryVolts;grid=it.grid.voltage;output=it.load.voltage;watts=it.outputWatts;inputWatts=it.gridWatts;chargeWatts=it.chargeWatts;status=it.status;target=it.target;mode=it.mode.ordinal;chargeAmps=it.chargeAmps;temperature=it.temperature.celsius }
        solar?.let { dc=it.output.voltage-it.batteryReturn.voltage;inputWatts=it.pvWatts;chargeWatts=it.outputWatts;grid=it.solar.voltage-it.negative.voltage;status=if(it.displayStatus=="BACKFEED")"CHARGE_TARGET" else it.displayStatus }
        protection?.let { dc=it.volts;amps=it.amps;watts=dc*amps;status=it.status }
        connections=node!!.lrduCubeMask.getTranslate(cabinet.connectionPlane(front))
    }
    override fun networkSerialize(out: DataOutputStream) { super.networkSerialize(out);display().write(out) }
    override fun multiMeterString(side: Direction)=display().let { tr("DC: %1$; input: %2$; output: %3$",Utils.plotVolt("",it.dc),Utils.plotPower("",it.inputWatts),Utils.plotPower("",it.watts)) }
    override fun getWaila()=display().let { linkedMapOf(tr("Status") to offGridStatus(it.status),tr("DC battery bus") to Utils.plotVolt("",it.dc),tr("Stored energy") to Utils.plotEnergy("",it.energy),tr("Output power") to Utils.plotPower("",it.watts)) }
}
