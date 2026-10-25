package mods.eln.transparentnode.firealarm

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.DataOutputStream

class AlarmElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    val kind=(descriptor as AlarmDescriptor).kind
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val supply=NbtElectricalLoad("alarmSupply").apply { serialResistance=.001 }
    val consumer=Resistor(supply,null).apply { resistance=1e19 }
    val signal=NbtElectricalGateInput("alarmInput")
    val output=NbtElectricalGateOutput("alarmOutput")
    val outputProcess=NbtElectricalGateOutputProcess("alarmOutput",output)
    val resetInput=if(kind==AlarmKind.PANEL)NbtElectricalGateInput("alarmReset") else null
    val troubleOutput=if(kind==AlarmKind.PANEL)NbtElectricalGateOutput("alarmTrouble") else null
    val troubleProcess=troubleOutput?.let { NbtElectricalGateOutputProcess("alarmTrouble",it) }
    val panel=AlarmPanelState()
    var channel="Fire Alarm";private set
    var label="";private set
    var enabled=true;private set
    var pulled=false;private set
    var detected=false;private set
    var powered=false;private set
    var alarming=false;private set
    var silenced=false;private set
    var trouble=false;private set
    var temperature=20.0;private set
    var threshold=60.0;private set
    var range=if(kind==AlarmKind.SMOKE)6 else 4;private set
    var members=0;private set
    var activeSources="";private set
    private var deviceWatts=0.0
    private var resetHeld=false
    private var scanAt=Long.MIN_VALUE
    private var clock=0.0
    private var active=false
    private var lastSignature: List<Any?>?=null
    private val known=HashSet<Long>()
    private val identity get()=coordinate().pos
    init {
        electricalLoadList.addAll(listOf(supply,signal,output));electricalComponentList.addAll(listOf(consumer,outputProcess))
        resetInput?.let(electricalLoadList::add);troubleOutput?.let(electricalLoadList::add);troubleProcess?.let(electricalComponentList::add)
        electricalProcessList.add(IProcess { dt->if(kind==AlarmKind.PANEL && enabled)panel.account(dt,consumer.power,deviceWatts) })
        slowProcessList.add(IProcess { dt->clock+=dt;if(clock>=.25) { clock%=.25;tick() } })
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
    }
    override fun initialize() { connect() }
    fun changeChannel(value: String) {
        val next=AlarmNetwork.channel(value);if(next==channel)return
        DimensionIds.serverLevel(coordinate().dimension)?.let { AlarmNetwork.remove(it,channel,identity) }
        channel=next;known.clear();needPublish()
    }
    fun operatePull(): Boolean {
        if(kind!=AlarmKind.PULL)return false
        pulled=!pulled;needPublish();tick();return true
    }
    private fun loadedAlarmWorld(): ServerLevel? {
        val world=DimensionIds.serverLevel(coordinate().dimension) ?: return null
        return world.takeIf { it.chunkSource.getChunkNow(identity.x shr 4,identity.z shr 4)!=null }
    }
    private fun tick() {
        val world=loadedAlarmWorld() ?: return
        val now=world.gameTime
        if(kind==AlarmKind.PANEL) {
            var snapshot=AlarmNetwork.snapshot(world,channel,identity,now,panel.alarm,panel.silenced)
            panel.trigger(snapshot.active.isNotEmpty() || signal.normalized>=.6)
            if(resetInput!!.normalized>=.6 && !resetHeld)panel.reset(snapshot.active.isNotEmpty() || signal.normalized>=.6)
            resetHeld=resetInput.normalized>.2
            snapshot=AlarmNetwork.snapshot(world,channel,identity,now,panel.alarm,panel.silenced)
            known.addAll(snapshot.members.map { it.pos.asLong() });members=snapshot.members.size
            val present=snapshot.members.map { it.pos.asLong() }.toSet()
            val mains=kotlin.math.abs(supply.voltage) in 90.0..150.0
            trouble=!mains || known.any { it !in present }
            activeSources=snapshot.active.take(4).joinToString(", ") { it.label.ifBlank { it.pos.toShortString() } }
            deviceWatts=if(enabled)snapshot.watts else 0.0
            powered=enabled && panel.powered;alarming=powered && panel.alarm;silenced=panel.silenced
            val demand=AlarmPanelState.IDLE_WATTS+deviceWatts+if(panel.energy<AlarmPanelState.CAPACITY)AlarmPanelState.CHARGE_WATTS else 0.0
            consumer.resistance=if(enabled && kotlin.math.abs(supply.voltage)<=150)120.0*120/demand else 1e19
            AlarmNetwork.publishPanel(world,channel,AlarmNetwork.Panel(identity,now,powered,alarming,silenced))
            outputProcess.state(alarming);troubleProcess!!.state(trouble && powered)
        } else {
            val lease=AlarmNetwork.lease(world,channel,identity,now)
            val ownPower=kotlin.math.abs(supply.voltage) in 18.0..30.0
            powered=enabled && (ownPower || lease.powered)
            if(kind.sensor && powered && now>=scanAt) {
                scanAt=now+40+(identity.asLong().toInt() and 3)
                if(kind==AlarmKind.SMOKE)detected=FireEnvironment.smoke(world,identity,range)
                else { temperature=FireEnvironment.heat(world,identity,range);detected=temperature>=threshold-if(detected)5.0 else 0.0 }
            }
            active=powered && ((if(kind==AlarmKind.PULL)pulled else detected) || signal.normalized>=.6)
            alarming=powered && !kind.source && (lease.alarm || signal.normalized>=.6)
            silenced=lease.silenced && signal.normalized<.6
            val quietWatts=if(kind.flashing)3.0 else 0.0
            val watts=kind.idleWatts+if(alarming) { if(silenced)quietWatts else kind.alarmWatts } else 0.0
            consumer.resistance=if(enabled)24.0*24/watts.coerceAtLeast(.01) else 1e19
            AlarmNetwork.publishField(world,channel,AlarmNetwork.Field(identity,now,label,kind.source,if(kind.source)active else alarming,ownPower,lease.owner,kind.idleWatts,kind.alarmWatts,quietWatts,silenced))
            outputProcess.state(if(kind.source)active else alarming)
            trouble=false
        }
        val signature=listOf(powered,alarming,silenced,trouble,pulled,detected,members,activeSources)
        if(signature!=lastSignature) { lastSignature=signature;needPublish() }
    }
    override fun disconnectJob() {
        DimensionIds.serverLevel(coordinate().dimension)?.let { AlarmNetwork.remove(it,channel,identity) };super.disconnectJob()
    }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        if(kind==AlarmKind.PULL && !player.isShiftKeyDown && player.mainHandItem.isEmpty) { operatePull();return true }
        return super.onBlockActivated(player,side,vx,vy,vz)
    }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(side==front.inverse)when(lrdu) {
        LRDU.Left->signal;LRDU.Right->output;LRDU.Up->troubleOutput;LRDU.Down->resetInput
    } else if(lrdu==LRDU.Down)supply else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=when(getElectricalLoad(side,lrdu)) {
        null->0;signal,resetInput->NodeBase.maskElectricalInputGate;output,troubleOutput->NodeBase.maskElectricalOutputGate;else->NodeBase.maskElectricalPower
    }
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun multiMeterString(side: Direction)=tr("Supply: %1$; draw: %2$",Utils.plotVolt("",supply.voltage),Utils.plotPower("",consumer.power))
    override fun thermoMeterString(side: Direction)=tr("Measured temperature: %1$ C",String.format(java.util.Locale.ROOT,"%.1f",temperature))
    override fun getWaila()=linkedMapOf(tr("Channel") to channel,tr("State") to alarmStatus(powered,alarming,active,trouble))
    private fun write(tag: CompoundTag) {
        tag.putString("alarmChannel",channel);tag.putString("alarmLabel",label);tag.putBoolean("alarmEnabled",enabled);tag.putBoolean("alarmPulled",pulled)
        tag.putDouble("alarmHeatThreshold",threshold);tag.putInt("alarmRange",range);tag.putDouble("alarmBackupJoules",panel.energy)
        tag.putBoolean("alarmLatched",panel.latched);tag.putBoolean("alarmSilenced",panel.silenced);tag.putLongArray("alarmKnown",known.toLongArray())
    }
    private fun read(tag: CompoundTag) {
        channel=if(tag.contains("alarmChannel"))AlarmNetwork.channel(tag.getString("alarmChannel")) else "Fire Alarm"
        label=tag.getString("alarmLabel").trim().take(64);enabled=!tag.contains("alarmEnabled") || tag.getBoolean("alarmEnabled");pulled=tag.getBoolean("alarmPulled")
        threshold=tag.getDouble("alarmHeatThreshold").takeIf { it.isFinite() && it in 35.0..200.0 } ?: 60.0
        range=tag.getInt("alarmRange").takeIf { it in 1..8 } ?: if(kind==AlarmKind.SMOKE)6 else 4
        panel.energy=tag.getDouble("alarmBackupJoules").takeIf { it.isFinite() }?.coerceIn(0.0,AlarmPanelState.CAPACITY) ?: 0.0
        panel.latched=tag.getBoolean("alarmLatched");panel.silenced=tag.getBoolean("alarmSilenced");known.clear();known.addAll(tag.getLongArray("alarmKnown").toList())
    }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);read(tag) }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);write(tag) }
    override fun getItemStackNBT()=CompoundTag().also(::write)
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)read(tag) }
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeUTF(channel);out.writeUTF(label);out.writeBoolean(enabled)
        listOf(powered,alarming,silenced,trouble,pulled,detected).forEach(out::writeBoolean)
        out.writeDouble(temperature);out.writeDouble(threshold);out.writeByte(range);out.writeInt(members)
        out.writeDouble(supply.voltage);out.writeDouble(consumer.power);out.writeDouble(panel.energy);out.writeUTF(activeSources)
    }
    override fun networkUnserialize(input: DataInputStream): Byte {
        val id=super.networkUnserialize(input)
        when(id.toInt()) {
            80->changeChannel(input.readUTF())
            81->{ label=input.readUTF().trim().take(64);needPublish() }
            82->input.readDouble().takeIf { it.isFinite() && it in 35.0..200.0 }?.let { threshold=it }
            83->operatePull()
            84->if(kind==AlarmKind.PANEL)panel.silenced=!panel.silenced
            85->if(kind==AlarmKind.PANEL)loadedAlarmWorld()?.let { panel.reset(AlarmNetwork.snapshot(it,channel,identity,it.gameTime,panel.alarm,panel.silenced).active.isNotEmpty() || signal.normalized>=.6) }
            86->if(kind==AlarmKind.PANEL)panel.test()
            87->if(kind==AlarmKind.PANEL)known.clear()
            88->enabled=input.readBoolean()
            89->input.readInt().takeIf { it in 1..8 }?.let { range=it;scanAt=Long.MIN_VALUE }
        }
        tick();needPublish();return id
    }
}
