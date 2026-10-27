package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.BatteryItem
import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.*
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.standby.TransferContact
import mods.eln.utility.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Passive machine circuits. No per-converter Thevenin probes or fixed-power negative impedance. */
class IndustrialElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    val kind=(descriptor as IndustrialDescriptor).kind
    var settings=kind.defaults.copyOf();private set
    var enabled=true;private set
    val protection=Protection()
    val shed=ShedControl()
    val input=NbtElectricalLoad("industrialInput").apply { serialResistance=.0001 }
    val outputs=List(4) { NbtElectricalLoad("industrialOutput$it").apply { serialResistance=.0001 } }
    val returnIn=NbtElectricalLoad("industrialReturnIn").apply { serialResistance=.0001 }
    val returnOut=NbtElectricalLoad("industrialReturnOut").apply { serialResistance=.0001 }
    val returnContact=TransferContact(returnIn,returnOut)
    val contacts=outputs.map { TransferContact(input,it) }
    val resistor=Resistor(input,null).apply { resistance=1e19 }
    val tapPin=NbtElectricalLoad("industrialTap").apply { serialResistance=.0001 }
    val tap=ServiceTap(input,tapPin,1.0).apply { enabled=false }
    val winding=Resistor(tapPin,outputs[0]).apply { resistance=.01 }
    val signal=NbtElectricalGateInput("industrialControl")
    val tripSignal=NbtElectricalGateOutput("industrialTrip")
    val tripSource=NbtElectricalGateOutputProcess("industrialTrip",tripSignal)
    override val cord=ApplianceCord(this,input)
    override val cordVoltage get()=nominal
    override fun acceptsCord(rating: CordRating)=kind in listOf(IndustrialKind.LOAD_BANK,IndustrialKind.CHARGER,IndustrialKind.WELDER,IndustrialKind.DRIVE) && rating.accepts(nominal) && (when(kind) { IndustrialKind.DRIVE->settings[1]*settings[4];else->settings[1] })/nominal<=rating.amps
    val nominal get()=if(kind==IndustrialKind.SCADA)120.0 else settings[0]
    override val inventory=object: TransparentNodeElementInventory(kind.slots,64,this) {
        override fun canPlaceItem(slot: Int,stack: ItemStack)=accepts(slot,stack)
        override fun getMaxStackSize()=if(kind==IndustrialKind.PAD || kind==IndustrialKind.CHARGER)1 else 64
        override fun getAccessibleSlotsFromSide(side: Int)=IntArray(kind.slots) { it }
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=stack!=null && accepts(slot,stack)
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=kind!=IndustrialKind.WELDER || slot==2
    }
    var temperature=20.0;private set
    var hertz=0.0;private set
    var lifetimeJoules=0.0;private set
    var sessionJoules=0.0;private set
    var workJoules=0.0;private set
    var surgeRemaining=200000.0;private set
    var surgeSeconds=0.0;private set
    var selectedTarget=0;private set
    val targets=LongArray(8) { Long.MIN_VALUE }
    val targetCircuits=IntArray(8)
    var telemetry="";private set
    var working=false;private set
    private var chargePack: ItemStack?=null
    private var welderTemplate=ItemStack.EMPTY
    private var elapsedSound=0.0
    private var consoleClock=0.0
    val outputCurrent get()=if(kind==IndustrialKind.PAD || kind==IndustrialKind.DRIVE)abs(tap.currentState.state) else contacts.sumOf { abs(it.current) }
    val inputWatts get()=max(0.0,input.voltage*(if(tap.enabled)tap.current else contacts.sumOf { it.current })+resistor.power)
    init {
        electricalLoadList.addAll(listOf(input,tapPin,returnIn,returnOut,signal,tripSignal)+outputs)
        electricalComponentList.addAll(contacts+listOf(returnContact,resistor,tap,winding,tripSource))
        slowPreProcessList.add(IProcess { prepare() })
        electricalProcessList.add(IProcess(::account))
        slowProcessList.add(IProcess { dt->if(loadedWorld()!=null)tick(dt) })
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
    }
    override fun initialize() { configure();connect() }
    fun accepts(slot: Int,stack: ItemStack)=when(kind) {
        IndustrialKind.PAD->if(slot==0)PoleHardware.transformerWatts(stack)!=null else slot==1 && PoleHardware.fuse(stack)?.spent==false
        IndustrialKind.CHARGER->slot==0 && battery(stack)!=null && stack.count==1
        IndustrialKind.WELDER->when(slot) { 0->stack.`is`(Items.IRON_INGOT) || stack.isDamageableItem;1->stack.`is`(Items.IRON_INGOT);else->false }
        else->false
    }
    fun applySettings(values: DoubleArray): Boolean {
        if(!kind.valid(values) || cord.plugged && values[0]!=settings[0])return false
        settings=values.copyOf();configure();prepare();needPublish();return true
    }
    fun setEnabled(value: Boolean) { enabled=value;configure();needPublish() }
    fun resetTrip() { if(temperature<80) { protection.reset();configure();needPublish() } }
    private fun configure() {
        contacts.forEach { it.closed=false };returnContact.closed=false
        tap.enabled=false
        when(kind) {
            IndustrialKind.RELAY->{ contacts[0].closed=enabled && !protection.tripped;returnContact.closed=contacts[0].closed }
            IndustrialKind.PAD->{ tap.ratio=settings[1]/nominal;tap.enabled=enabled && !protection.tripped && PoleHardware.transformerWatts(inventory.getItem(0))!=null && PoleHardware.fuse(inventory.getItem(1))?.spent==false;winding.resistance=.015 }
            IndustrialKind.DRIVE->{ tap.ratio=max(.000001,settings[1]/nominal*hertz/60.0);tap.enabled=!protection.tripped && hertz>.01;winding.resistance=settings[1]/settings[4] }
            IndustrialKind.SHEDDER->contacts.forEachIndexed { i,c->c.closed=enabled && shed.closed[i] }
            else->Unit
        }
        tripSource.state(protection.tripped || kind==IndustrialKind.ARRESTER && surgeRemaining<=0)
    }
    override fun inventoryChange(inventory: Container?) { configure();prepare();needPublish() }
    private fun loadedWorld(): ServerLevel? {
        val c=node?.coordinate ?: return null
        return DimensionIds.serverLevel(c.dimension)?.takeIf { it.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)!=null }
    }
    internal fun prepare(loaded: Boolean=loadedWorld()!=null,dayTime: Long=loadedWorld()?.dayTime ?: 0) {
        var demand=0.0
        working=enabled && !protection.tripped && loaded && signal.normalized<.6
        when(kind) {
            IndustrialKind.LOAD_BANK->{ working=working && temperature<120;if(working)demand=settings[1] }
            IndustrialKind.SCADA->if(working)demand=20.0
            IndustrialKind.ARRESTER->{ working=working && surgeRemaining>0 && (surgeSeconds>0 || abs(input.voltage)>nominal*1.3);if(working)demand=500000.0 }
            IndustrialKind.CHARGER->{
                val stack=inventory.getItem(0);if(chargePack!==stack) { if(chargePack!=null)sessionJoules=0.0;chargePack=stack };val battery=battery(stack)
                working=working && battery!=null && battery.getEnergy(stack)<battery.getEnergyMax(stack)-.01 && IndustrialEnergy.schedule(dayTime,settings[2].toInt(),settings[3].toInt())
                if(working && battery!=null)demand=min(settings[1],battery.getChargePower(stack))
            }
            IndustrialKind.WELDER->{
                val item=inventory.getItem(0)
                if(!ItemStack.isSameItemSameComponents(item,welderTemplate)) { workJoules=0.0;welderTemplate=item.copyWithCount(1) }
                val result=weldResult()
                working=working && !result.isEmpty && inventory.getItem(2).isEmpty
                if(working)demand=settings[1]
            }
            else->working=false
        }
        // Overvoltage disconnects appliances rather than creating excess battery/crafting energy.
        if(kind!=IndustrialKind.ARRESTER && (!input.voltage.isFinite() || abs(input.voltage)>nominal*1.3)) { working=false;demand=0.0 }
        resistor.resistance=IndustrialEnergy.resistance(nominal,demand)
    }
    fun absorbLightning() {
        if(kind!=IndustrialKind.ARRESTER || !enabled || surgeRemaining<=0)return
        // The strike supplies this energy externally; do not also shunt a healthy grid.
        surgeRemaining=max(0.0,surgeRemaining-40000);surgeSeconds=0.0
        if(loadedWorld()!=null)play(mods.eln.sound.SoundCommand("eln:utility_arc_close").smallRange().mulVolume(.35));needPublish()
    }
    internal fun account(dt: Double) {
        if(!dt.isFinite() || dt<=0)return
        val joules=resistor.power.coerceAtLeast(0.0)*dt
        if(!joules.isFinite())return
        lifetimeJoules+=max(0.0,inputWatts)*dt
        when(kind) {
            IndustrialKind.LOAD_BANK->{ temperature+=(resistor.power-(temperature-20)*max(100.0,settings[1]/45))*dt/12000 }
            IndustrialKind.ARRESTER->{ if(working)surgeRemaining=max(0.0,surgeRemaining-joules);surgeSeconds=max(0.0,surgeSeconds-dt) }
            IndustrialKind.CHARGER->if(working) {
                val stack=inventory.getItem(0);battery(stack)?.let { b->
                    val stored=min(IndustrialEnergy.charge(b.getEnergy(stack),b.getEnergyMax(stack),joules),b.getChargePower(stack)*dt)
                    b.setEnergy(stack,b.getEnergy(stack)+stored);sessionJoules+=stored
                }
            }
            IndustrialKind.WELDER->if(working)workJoules=min(120000.0,workJoules+joules)
            else->Unit
        }
    }
    internal fun tick(dt: Double) {
        if(!dt.isFinite() || dt<=0)return
        when(kind) {
            IndustrialKind.RELAY->{
                if(signal.normalized>=.6) { protection.tripped=true;protection.reason=5 }
                if(enabled)protection.step(dt,input.voltage,contacts[0].current,returnContact.current,nominal,settings[1],settings[2],settings[3],settings[4],settings[5])
                configure()
            }
            IndustrialKind.PAD->{
                if(tap.enabled) {
                    val fuse=PoleHardware.fuse(inventory.getItem(1))
                    val rating=PoleHardware.transformerWatts(inventory.getItem(0)) ?: 0
                    val limit=min(fuse?.amps ?: 0.0,rating/nominal*1.25)
                    if(protection.step(dt,input.voltage,tap.current,0.0,nominal,limit,0.0,1.5,2.0)) {
                        inventory.setItem(1,Eln.findItemStack(PoleHardware.spentName,1));if(loadedWorld()!=null)play(mods.eln.sound.SoundCommand("eln:utility_fuse_blow").smallRange().mulVolume(.5));configure()
                    }
                }
            }
            IndustrialKind.DRIVE->{
                hertz=IndustrialEnergy.ramp(hertz,if(enabled && !protection.tripped && signal.normalized<.6)settings[2] else 0.0,dt,settings[3])
                temperature+=(winding.power-(temperature-20)*12)*dt/15000
                if(temperature>120) { protection.tripped=true;protection.reason=6 }
                configure()
            }
            IndustrialKind.SHEDDER->{ shed.step(dt,input.voltage,nominal,inputWatts,settings[1],enabled && signal.normalized<.6);configure() }
            IndustrialKind.WELDER->{ if(working && workJoules>=120000 && !weldResult().isEmpty && inventory.getItem(2).isEmpty) {
                val result=weldResult();inventory.getItem(0).shrink(1);inventory.getItem(1).shrink(1);inventory.setItem(2,result);workJoules=0.0;welderTemplate=ItemStack.EMPTY;needPublish()
            } }
            IndustrialKind.ARRESTER->configure()
            IndustrialKind.SCADA->{ consoleClock+=dt;if(consoleClock>=1) { consoleClock=0.0;telemetry=ScadaLink.read(this,targets[selectedTarget],targetCircuits[selectedTarget]);needPublish() } }
            else->Unit
        }
        elapsedSound+=dt
        if(elapsedSound>=1) {
            elapsedSound=0.0
            if(working && resistor.power>50 && kind==IndustrialKind.WELDER && loadedWorld()!=null) {
                play(mods.eln.sound.SoundCommand("eln:utility_arc_close").smallRange().mulVolume(.18))
                val w=loadedWorld()!!;val c=coordinate();w.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,c.x+.5,c.y+.9,c.z+.5,5,.2,.1,.2,.03)
            }
        }
        prepare()
    }
    private fun weldResult(): ItemStack {
        val input=inventory.getItem(0);val material=inventory.getItem(1)
        if(!material.`is`(Items.IRON_INGOT))return ItemStack.EMPTY
        if(input.`is`(Items.IRON_INGOT) && material.count>=1)return Eln.findItemStack(IndustrialItems.FRAME,1)
        if(input.isDamageableItem && input.isDamaged)return input.copyWithCount(1).also { it.damageValue=max(0,it.damageValue-max(1,it.maxDamage/4)) }
        return ItemStack.EMPTY
    }
    fun bindTarget() {
        if(kind!=IndustrialKind.SCADA || !kind.valid(settings))return
        val target=BlockPos(settings[0].toInt(),settings[1].toInt(),settings[2].toInt())
        if(target.distSqr(coordinate().pos)>256.0*256.0)return
        targets[selectedTarget]=target.asLong();targetCircuits[selectedTarget]=settings[3].toInt()-1;needPublish()
    }
    fun selectTarget(index: Int) {
        selectedTarget=index.coerceIn(0,7)
        if(targets[selectedTarget]!=Long.MIN_VALUE) { val at=BlockPos.of(targets[selectedTarget]);settings=doubleArrayOf(at.x.toDouble(),at.y.toDouble(),at.z.toDouble(),targetCircuits[selectedTarget]+1.0) }
        needPublish()
    }
    fun commandTarget(closed: Boolean): Boolean {
        val accepted=ScadaLink.command(this,targets[selectedTarget],targetCircuits[selectedTarget],closed)
        telemetry=if(accepted)tr("Command accepted") else tr("Command rejected: check console power, target type, chunk or trip latch")
        needPublish();return accepted
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): mods.eln.sim.ElectricalLoad? = when {
        side==front && lrdu==LRDU.Up->tripSignal
        side==front && lrdu==LRDU.Down->signal
        kind==IndustrialKind.RELAY && lrdu==LRDU.Right && side==front.left()->returnIn
        kind==IndustrialKind.RELAY && lrdu==LRDU.Right && side==front.right()->returnOut
        (side==front.left() || side==Direction.YN) && lrdu==LRDU.Down && !cord.plugged->input
        side==front.right() && kind==IndustrialKind.SHEDDER->outputs[lrdu.dir]
        side==front.right() && lrdu==LRDU.Down && kind in listOf(IndustrialKind.PAD,IndustrialKind.DRIVE,IndustrialKind.RELAY)->outputs[0]
        else->null
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=when(getElectricalLoad(side,lrdu)) { null->0;signal->NodeBase.maskElectricalInputGate;tripSignal->NodeBase.maskElectricalOutputGate;else->NodeBase.maskElectricalPower }
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=IndustrialContainer(player,inventory,kind)
    override fun multiMeterString(side: Direction)=tr("Input: %1$; output: %2$",Utils.plotUIP(input.voltage,input.current),Utils.plotUIP(outputs[0].voltage,outputCurrent))
    override fun thermoMeterString(side: Direction)=tr("Case temperature: %1$ C",temperature.toInt())
    override fun getWaila()=linkedMapOf(tr("Supply") to Utils.plotVolt("",input.voltage),tr("Power") to Utils.plotPower("",inputWatts),tr("Status") to if(protection.tripped)tr("Tripped") else if(enabled)tr("Enabled") else tr("Disabled"))
    private fun save(tag: CompoundTag) {
        settings.forEachIndexed { i,v->tag.putDouble("industrialSetting$i",v) }
        tag.putBoolean("industrialEnabled",enabled);tag.putBoolean("industrialTrip",protection.tripped);tag.putInt("industrialTripReason",protection.reason);tag.putDouble("industrialTemperature",temperature)
        tag.putDouble("industrialLifetime",lifetimeJoules);tag.putDouble("industrialSession",sessionJoules);tag.putDouble("industrialWork",workJoules);tag.putDouble("industrialSurge",surgeRemaining)
        tag.putInt("industrialTarget",selectedTarget);tag.putLongArray("industrialTargets",targets);tag.putIntArray("industrialCircuits",targetCircuits)
        if(!welderTemplate.isEmpty)tag.put("industrialTemplate",welderTemplate.writeToNBT(CompoundTag()))
        shed.closed.forEachIndexed { i,v->tag.putBoolean("industrialShed$i",v) };tag.putBoolean("industrialArmed",protection.armed);tag.putDouble("industrialTripTime",protection.elapsed)
        cord.write(tag)
    }
    private fun load(tag: CompoundTag) {
        val p=DoubleArray(settings.size) { if(tag.contains("industrialSetting$it"))tag.getDouble("industrialSetting$it") else kind.defaults[it] };if(kind.valid(p))settings=p
        enabled=!tag.contains("industrialEnabled") || tag.getBoolean("industrialEnabled");protection.tripped=tag.getBoolean("industrialTrip");protection.reason=tag.getInt("industrialTripReason").coerceIn(0,6)
        fun finite(key: String,default: Double,min: Double,max: Double)=tag.getDouble(key).takeIf { tag.contains(key) && it.isFinite() }?.coerceIn(min,max) ?: default
        temperature=finite("industrialTemperature",20.0,-50.0,1000.0);lifetimeJoules=finite("industrialLifetime",0.0,0.0,1e18);sessionJoules=finite("industrialSession",0.0,0.0,1e18);workJoules=finite("industrialWork",0.0,0.0,120000.0);surgeRemaining=finite("industrialSurge",200000.0,0.0,200000.0)
        selectedTarget=tag.getInt("industrialTarget").coerceIn(0,7);tag.getLongArray("industrialTargets").takeIf { it.size==8 }?.copyInto(targets);tag.getIntArray("industrialCircuits").takeIf { it.size==8 }?.let { a->a.forEachIndexed { i,v->targetCircuits[i]=v.coerceIn(0,11) } }
        welderTemplate=if(tag.contains("industrialTemplate"))stackFromNbt(tag.getCompound("industrialTemplate")) else ItemStack.EMPTY
        shed.closed.indices.forEach { i->shed.closed[i]=!tag.contains("industrialShed$i") || tag.getBoolean("industrialShed$i") };protection.armed=tag.getBoolean("industrialArmed");protection.elapsed=finite("industrialTripTime",0.0,0.0,60.0)
        cord.read(tag);configure()
    }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);load(tag) }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);save(tag) }
    override fun getItemStackNBT()=CompoundTag().also { save(it);it.remove("applianceCord");it.remove("industrialWork");it.remove("industrialTemplate") }
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)load(tag) }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);settings.forEach(out::writeDouble);out.writeBoolean(enabled);out.writeBoolean(protection.tripped);out.writeBoolean(working)
        listOf(input.voltage,inputWatts,outputs[0].voltage,outputCurrent,temperature,hertz,lifetimeJoules,sessionJoules,workJoules,surgeRemaining,protection.reason.toDouble()).forEach(out::writeDouble)
        out.writeByte(selectedTarget);out.writeUTF(telemetry.take(2000));shed.closed.forEach(out::writeBoolean);
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            80->applySettings(DoubleArray(settings.size) { stream.readDouble() })
            81->setEnabled(stream.readBoolean())
            82->resetTrip()
            83->bindTarget()
            84->selectTarget(stream.readInt())
            85->commandTarget(stream.readBoolean())
        }
        needPublish();return id
    }
    companion object {
        fun battery(stack: ItemStack): BatteryItem?=if(stack.isEmpty || stack.count!=1)null else GenericItemUsingDamageDescriptor.getDescriptor(stack) as? BatteryItem
    }
}
