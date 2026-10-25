package mods.eln.transparentnode.switchgear

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sound.SoundCommand
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

class SwitchgearElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    val kind=(descriptor as SwitchgearDescriptor).kind
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val commons=List(kind.poles) { NbtElectricalLoad("contactCommon$it").apply { serialResistance=.00001 } }
    val normallyOpen=List(kind.poles) { NbtElectricalLoad("contactNO$it").apply { serialResistance=.00001 } }
    val normallyClosed=if(kind.changeover)List(kind.poles) { NbtElectricalLoad("contactNC$it").apply { serialResistance=.00001 } } else emptyList()
    val noContacts=commons.indices.map { SwitchContact(commons[it],normallyOpen[it],kind.resistance) }
    val ncContacts=normallyClosed.indices.map { SwitchContact(commons[it],normallyClosed[it],kind.resistance) }
    val gate=NbtElectricalGateInput("controlSignal")
    val coilA=NbtElectricalLoad("coilA1").apply { serialResistance=.00001 }
    val coilB=NbtElectricalLoad("coilA2").apply { serialResistance=.00001 }
    val coil=Resistor(coilA,coilB).apply { resistance=1e19 }
    var control=RelayControl.SIGNAL;private set
    var enabled=true;private set
    var energized=false;private set
    var transitioning=false;private set
    var tripped=false;private set
    var temperature=20.0;private set
    private var target=false
    private var delay=0.0
    private var overload=0.0
    val contactLosses get()=noContacts.sumOf { it.power }+ncContacts.sumOf { it.power }
    init {
        electricalLoadList.addAll(commons+normallyOpen+normallyClosed+listOf(gate,coilA,coilB))
        electricalComponentList.addAll(noContacts+ncContacts);electricalComponentList.add(coil)
        electricalProcessList.add(IProcess(::step))
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,.25))
        contacts()
    }
    override fun initialize() { configureCoil();contacts();connect() }
    private fun configureCoil() { coil.resistance=if(control==RelayControl.SIGNAL)1e19 else control.nominal*control.nominal/kind.coilWatts }
    private fun contacts() {
        val ready=enabled && !tripped && !transitioning
        noContacts.forEach { it.closed=ready && energized }
        ncContacts.forEach { it.closed=ready && !energized }
    }
    fun setControl(value: RelayControl) {
        if(control==value)return
        control=value;energized=false;target=false;transitioning=true;delay=if(kind.solidState)0.0 else .025
        configureCoil();contacts();needPublish()
    }
    fun setEnabled(value: Boolean) { enabled=value;if(!value) { energized=false;transitioning=false;target=false };contacts();needPublish() }
    fun resetTrip() { if(temperature<80) { tripped=false;overload=0.0;energized=false;transitioning=false;contacts();needPublish() } }
    fun step(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        val heat=(contactLosses+coil.power).coerceAtLeast(0.0)
        val coolingResistance=if(kind.solidState)1.8 else 3.0
        val heatCapacity=if(kind.solidState)180.0 else 250.0
        temperature+=(heat-(temperature-20.0)/coolingResistance)*seconds/heatCapacity
        val ratio=maxOf(noContacts.maxOf { abs(it.current)/kind.amps },ncContacts.maxOfOrNull { abs(it.current)/kind.amps } ?: 0.0)
        overload=if(ratio>1.25)overload+seconds*(ratio*ratio-1) else (overload-seconds).coerceAtLeast(0.0)
        val overvoltage=control!=RelayControl.SIGNAL && abs(coil.voltage)>control.nominal*1.5
        if(!tripped && (overload>=5.0 || temperature>(if(kind.solidState)100 else 140) || overvoltage)) { tripped=true;contacts();needPublish() }
        val requested=enabled && !tripped && when(control) {
            RelayControl.SIGNAL->if(energized || target)gate.normalized>.2 else gate.normalized>=.6
            else->abs(coil.voltage)>=control.nominal*if(energized || target).60 else .85
        }
        if(kind.solidState) {
            if(energized!=requested || transitioning) { energized=requested;target=requested;transitioning=false;contacts();needPublish() }
        } else {
            if(requested!=target) { target=requested;delay=.025;transitioning=true;contacts() }
            if(transitioning) {
                delay-=seconds
                if(delay<=0) {
                    val changed=energized!=target
                    energized=target;transitioning=false;contacts()
                    if(changed && DimensionIds.serverLevel(coordinate().dimension)!=null)
                        play(SoundCommand("eln:click").smallRange().mulVolume(if(kind.changeover).16 else .32).mulBlockAttenuation(0.0))
                    needPublish()
                }
            }
        }
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=when(side) {
        front.left()->commons.getOrNull(lrdu.dir)
        front.right()->normallyOpen.getOrNull(lrdu.dir)
        front.inverse->normallyClosed.getOrNull(lrdu.dir)
        front->when(lrdu) { LRDU.Left->coilA;LRDU.Right->coilB;LRDU.Up->gate;else->null }
        else->null
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=when(getElectricalLoad(side,lrdu)) {
        null->0;gate->NodeBase.maskElectricalInputGate;else->NodeBase.maskElectricalPower
    }
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun multiMeterString(side: Direction)=commons.indices.joinToString("\n") { i->tr("Contact %1$: %2$; NO %3$; NC %4$",i+1,Utils.plotVolt("",commons[i].voltage),Utils.plotAmpere("",noContacts[i].current),Utils.plotAmpere("",ncContacts.getOrNull(i)?.current ?: 0.0)) }
    override fun thermoMeterString(side: Direction)=tr("Case temperature: %1$ C",String.format(java.util.Locale.ROOT,"%.1f",temperature))
    override fun getWaila()=linkedMapOf(tr("Control") to control.label(),tr("Position") to if(tripped)tr("Tripped") else if(!enabled)tr("Contacts isolated") else if(energized)tr("Energized") else tr("Released"))
    private fun writeSettings(tag: CompoundTag) {
        tag.putInt("switchgearControl",control.ordinal);tag.putBoolean("switchgearEnabled",enabled);tag.putBoolean("switchgearTrip",tripped);tag.putDouble("switchgearTemperature",temperature);tag.putDouble("switchgearOverload",overload)
    }
    private fun readSettings(tag: CompoundTag) {
        control=RelayControl.entries.getOrNull(tag.getInt("switchgearControl")) ?: RelayControl.SIGNAL
        enabled=!tag.contains("switchgearEnabled") || tag.getBoolean("switchgearEnabled");tripped=tag.getBoolean("switchgearTrip")
        temperature=tag.getDouble("switchgearTemperature").takeIf { tag.contains("switchgearTemperature") }?.takeIf { it.isFinite() && it in 0.0..1000.0 } ?: 20.0
        overload=tag.getDouble("switchgearOverload").takeIf { it.isFinite() && it>=0 } ?: 0.0
        energized=false;target=false;transitioning=false;delay=0.0;configureCoil();contacts()
    }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);readSettings(tag) }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);writeSettings(tag) }
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)readSettings(tag) }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeByte(control.ordinal);out.writeBoolean(enabled);out.writeBoolean(energized);out.writeBoolean(transitioning);out.writeBoolean(tripped)
        out.writeDouble(temperature);out.writeDouble(coil.voltage);out.writeDouble(coil.power);out.writeDouble(contactLosses)
        commons.indices.forEach { out.writeDouble(commons[it].voltage);out.writeDouble(noContacts[it].current);out.writeDouble(ncContacts.getOrNull(it)?.current ?: 0.0) }
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) { 80->RelayControl.entries.getOrNull(stream.readInt())?.let(::setControl);81->setEnabled(stream.readBoolean());82->resetTrip() }
        return id
    }
}
