package mods.eln.vehicle

import mods.eln.Eln
import mods.eln.api.media.RemoteSwitchableDevice
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataOutputStream
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.math.*

object VehicleChargers {
    private val loaded=HashMap<UUID,WeakReference<VehicleChargerElement>>()
    fun register(charger: VehicleChargerElement) { loaded.entries.removeIf { it.value.get()==null };loaded[charger.identity]=WeakReference(charger) }
    fun find(id: UUID)=loaded[id]?.get()?.takeIf { it.identity==id && it.isLoaded() }
    fun connected(vehicle: RoadVehicleEntity)=vehicle.charger?.let(::find)?.let { it.vehicle()===vehicle }==true
}

class VehicleLeadItem: GenericItemUsingDamageDescriptor(TITLE) {
    companion object { val TITLE=I18N.TR_NAME(I18N.Type.NONE,"100 A Vehicle Charging Lead") }
    init { creativeTab=Eln.creativeTabPowerElectronics;setDefaultIcon("flexibleappliancecord") }
    fun bind(stack: ItemStack,charger: VehicleChargerElement) { updateNbt(stack) { it.putUUID("vehicleCharger",charger.identity) } }
    fun connect(stack: ItemStack,player: Player,vehicle: RoadVehicleEntity) {
        if(player.isShiftKeyDown && vehicle.parked) { vehicle.charger?.let(VehicleChargers::find)?.unplug();vehicle.charger=null;return }
        val tag=getNbt(stack);val c=if(tag.hasUUID("vehicleCharger"))VehicleChargers.find(tag.getUUID("vehicleCharger")) else null
        val ok=c!=null && c.attach(vehicle)
        player.displayClientMessage(Component.literal(if(ok)tr("Charging lead connected") else tr("Select a loaded vehicle charger first, then a parked vehicle within six blocks")),true)
    }
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        list.add(tr("Use on a 240 V 100 A Vehicle Charger, then right-click a parked vehicle within six blocks"))
        list.add(tr("Sneak-right-click the vehicle with the lead to disconnect"))
    }
}

class VehicleChargerElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),RemoteSwitchableDevice {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val input=NbtElectricalLoad("vehicleChargerInput").apply { serialResistance=.0001 }
    private val sink=Resistor(input,null).apply { resistance=1e19 }
    var identity=UUID.randomUUID();private set
    private var target: UUID?=null
    var enabled=true;private set
    var watts=0.0;private set
    var stored=0.0;private set
    var state="IDLE";private set
    init {
        electricalLoadList.add(input);electricalComponentList.add(sink)
        slowPreProcessList.add(IProcess { prepare() });electricalProcessList.add(IProcess(::account))
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
    }
    private fun loadedWorld(): ServerLevel? { val c=node?.coordinate ?: return null;return DimensionIds.serverLevel(c.dimension)?.takeIf { it.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)!=null } }
    fun isLoaded(): Boolean { val w=loadedWorld() ?: return false;val c=node!!.coordinate;return (w.getBlockEntity(net.minecraft.core.BlockPos(c.x,c.y,c.z)) as? TransparentNodeEntity)?.node.let { (it as? TransparentNode)?.element }===this }
    fun vehicle(): RoadVehicleEntity? {
        val e=target?.let { loadedWorld()?.getEntity(it) as? RoadVehicleEntity } ?: return null
        val c=node?.coordinate ?: return null
        return e.takeIf { it.isAlive && it.parked && it.charger==identity && it.position().distanceToSqr(net.minecraft.world.phys.Vec3(c.x+.5,c.y+.5,c.z+.5))<=36 }
    }
    fun attach(e: RoadVehicleEntity): Boolean {
        if(loadedWorld()!==e.level() || !e.parked || e.isVehicle || e.charger!=null && e.charger!=identity)return false
        val c=node!!.coordinate
        if(e.position().distanceToSqr(net.minecraft.world.phys.Vec3(c.x+.5,c.y+.5,c.z+.5))>36)return false
        unplug();target=e.uuid;e.charger=identity;prepare();needPublish();return true
    }
    fun unplug() { target?.let { loadedWorld()?.getEntity(it) as? RoadVehicleEntity }?.takeIf { it.charger==identity }?.charger=null;target=null;sink.resistance=1e19;watts=0.0;needPublish() }
    override fun initialize() { VehicleChargers.register(this);connect() }
    internal fun prepare() {
        VehicleChargers.register(this)
        val e=vehicle();val v=abs(input.voltage);val demand=e?.chargeDemand() ?: 0.0
        state=when { !enabled->"OFF";e==null->"IDLE";demand<.01->"FULL";!v.isFinite() || v>264->"OVERVOLTAGE";v<200->"WAIT_POWER";else->"CHARGING" }
        sink.resistance=if(enabled && e!=null && demand>.01 && v.isFinite() && v<=264)maxOf(2.4,240.0*240.0/demand,v/100.0,v*v/24000.0) else 1e19
        needPublish()
    }
    internal fun account(dt: Double) {
        if(!dt.isFinite() || dt<=0)return
        val e=vehicle();val v=abs(input.voltage)
        watts=if(e!=null && enabled && v in 200.0..264.0)sink.power.takeIf { it.isFinite() }?.coerceIn(0.0,min(24000.0,v*100)) ?: 0.0 else 0.0
        if(watts>0 && e!=null)stored+=e.acceptCharge(watts*dt,dt)
    }
    override fun setRemoteEnabled(enabled: Boolean) { this.enabled=enabled;prepare();needPublish() }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        val lead=GenericItemUsingDamageDescriptor.getDescriptor(player.mainHandItem) as? VehicleLeadItem
        if(lead!=null) { lead.bind(player.mainHandItem,this);player.displayClientMessage(Component.literal(tr("Vehicle charger selected; use the lead on a parked vehicle")),true);return true }
        player.displayClientMessage(Component.literal(tr("Vehicle charger: %1$ V, %2$ A, %3$ W",abs(input.voltage).toInt(),abs(input.current).toInt(),watts.toInt())),true);return true
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if((side==front.left() || side==Direction.YN) && lrdu==LRDU.Down)input else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun multiMeterString(side: Direction)=Utils.plotUIP(input.voltage,input.current)
    override fun getWaila()=linkedMapOf(tr("Supply") to Utils.plotUIP(input.voltage,input.current),tr("Power") to Utils.plotPower("",watts),tr("Status") to chargerStatus(state),tr("Stored this session") to Utils.plotEnergy("",stored))
    override fun networkSerialize(out: DataOutputStream) { super.networkSerialize(out);out.writeDouble(input.voltage);out.writeDouble(watts);out.writeDouble(stored);out.writeUTF(state);out.writeLong(target?.let { loadedWorld()?.getEntity(it)?.id }?.toLong() ?: -1) }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);tag.putUUID("vehicleChargerIdentity",identity);tag.putBoolean("vehicleChargerEnabled",enabled) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);if(tag.hasUUID("vehicleChargerIdentity"))identity=tag.getUUID("vehicleChargerIdentity");enabled=!tag.contains("vehicleChargerEnabled") || tag.getBoolean("vehicleChargerEnabled");VehicleChargers.register(this) }
    override fun onBreakElement() { unplug();super.onBreakElement() }
}
fun chargerStatus(state: String)=when(state) { "CHARGING"->tr("Charging");"FULL"->tr("Fully charged");"OFF"->tr("Disabled");"OVERVOLTAGE"->tr("Overvoltage disconnected");"WAIT_POWER"->tr("Waiting for 240 V supply");else->tr("Connect a parked vehicle") }
