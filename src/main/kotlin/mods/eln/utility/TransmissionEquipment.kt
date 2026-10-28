package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sound.SoundCommand
import mods.eln.transparentnode.standby.TransferContact
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream

/** Explicit free descriptor IDs; no existing pole ordinal or saved ID changes. */
enum class TransmissionKind(val title: String,val model: String,val inputVolts: Double,val outputVolts: Double,
    val height: Int,val spread: Double,val subId: Int,val transformer: Boolean=false,val disconnect: Boolean=false,val riser: Boolean=false) {
    POLE_69(I18N.TR_NAME(I18N.Type.NONE,"69 kV Steel Transmission Pole"),"steeltransmission69",69_000.0,69_000.0,16,2.7,14),
    POLE_230(I18N.TR_NAME(I18N.Type.NONE,"230 kV Steel Transmission Tower"),"steeltransmission230",230_000.0,230_000.0,24,4.8,15),
    STEP_69(I18N.TR_NAME(I18N.Type.NONE,"69 kV to 12.5 kV Substation Transformer"),"substation69125",69_000.0,12_500.0,5,1.8,16,transformer=true),
    STEP_230(I18N.TR_NAME(I18N.Type.NONE,"230 kV to 69 kV Substation Transformer"),"substation23069",230_000.0,69_000.0,7,2.7,17,transformer=true),
    SWITCH_69(I18N.TR_NAME(I18N.Type.NONE,"69 kV Three Phase Substation Disconnect"),"substationdisconnect69",69_000.0,69_000.0,4,1.8,18,disconnect=true),
    SWITCH_230(I18N.TR_NAME(I18N.Type.NONE,"230 kV Three Phase Substation Disconnect"),"substationdisconnect230",230_000.0,230_000.0,6,2.7,19,disconnect=true),
    RISER_69(I18N.TR_NAME(I18N.Type.NONE,"69 kV Three Phase Cable Riser Pole"),"steelriser69",69_000.0,69_000.0,16,2.7,20,riser=true),
    RISER_230(I18N.TR_NAME(I18N.Type.NONE,"230 kV Three Phase Cable Riser Tower"),"steelriser230",230_000.0,230_000.0,24,4.8,21,riser=true);
    val station get()=transformer || disconnect
    val wattsPerPhase get()=if(inputVolts>100_000)30_000_000.0 else 5_000_000.0
    val ratio get()=outputVolts/inputVolts
}

class TransmissionElement(node: TransparentNode,descriptor: TransparentNodeDescriptor):
    GridElement(node,descriptor,(descriptor as GridDescriptor).connectRange) {
    val kind=(descriptor as TransmissionDescriptor).kind
    override val inventory=TransparentNodeElementInventory(0,1,this)
    override val alignsPoleSpans get()=!kind.station
    override val alignsPrimaryPhaseOrder=true
    override val primaryPhaseOrientationIsFixed get()=kind.station
    val inputs=List(3) { NbtElectricalLoad("transmissionInput$it").apply { serialResistance=.00001 } }
    val outputs=if(kind.station)List(3) { NbtElectricalLoad("transmissionOutput$it").apply { serialResistance=.00001 } } else inputs
    val services=if(!kind.station)List(3) { NbtElectricalLoad("transmissionService$it").apply { serialResistance=.00001 } } else emptyList()
    var primaryClosed=true;private set
    var secondaryClosed=true;private set
    private var selected=ServiceTerminals.HV_LINE_BUNDLE
    private val primaryContacts=ArrayList<TransferContact>()
    private val secondaryContacts=ArrayList<TransferContact>()
    val taps=ArrayList<ServiceTap>()
    init {
        electricalLoadList.addAll(inputs)
        electricalLoadList.addAll(services)
        if(outputs!==inputs)electricalLoadList.addAll(outputs)
        if(kind.transformer)repeat(3) { phase->
            val primary=NbtElectricalLoad("windingPrimary$phase")
            val secondary=NbtElectricalLoad("windingSecondary$phase")
            val output=NbtElectricalLoad("windingOutput$phase")
            electricalLoadList.addAll(listOf(primary,secondary,output))
            primaryContacts+=TransferContact(inputs[phase],primary)
            taps+=ServiceTap(primary,secondary,kind.ratio)
            // 0.5% copper loss at nominal design loading. Pure passive linear
            // physics: fluctuations and reverse power are not hidden by a regulator.
            electricalComponentList+=Resistor(secondary,output).apply {
                resistance=kind.outputVolts*kind.outputVolts/kind.wattsPerPhase*.005
            }
            secondaryContacts+=TransferContact(output,outputs[phase])
        } else if(kind.disconnect)repeat(3) { primaryContacts+=TransferContact(inputs[it],outputs[it]) }
        electricalComponentList.addAll(taps);electricalComponentList.addAll(primaryContacts);electricalComponentList.addAll(secondaryContacts)
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
        applyContacts()
    }
    private fun applyContacts() {
        primaryContacts.forEach { it.closed=primaryClosed }
        secondaryContacts.forEach { it.closed=secondaryClosed }
    }
    fun operate(primary: Boolean) {
        if(!kind.station || !primary && !kind.transformer)return
        if(primary)primaryClosed=!primaryClosed else secondaryClosed=!secondaryClosed
        applyContacts();needPublish()
        if(mods.eln.misc.DimensionIds.serverLevel(coordinate().dimension)!=null)
            play(SoundCommand("eln:utility_recloser_bump").smallRange().mulVolume(.65))
    }
    override fun getGridElectricalLoad(side: Direction): ElectricalLoad?=inputs.first()
    override fun selectedGridTerminal(side: Direction)=selected
    override fun gridLoads(terminal: Int): List<ElectricalLoad> = when {
        !kind.station && terminal==50->services
        !kind.station && terminal in 40..42->listOf(services[terminal-40])
        // Keep the direction-based GridElement API consistent with its exposed
        // single-load adapter. Interactive links use the explicit phase terminals.
        terminal in 0..5->listOf(inputs.first())
        terminal in 32..34->listOf(inputs[terminal-32])
        terminal in 36..38 && kind.station->listOf(outputs[terminal-36])
        terminal==ServiceTerminals.HV_LINE_BUNDLE->inputs
        terminal==ServiceTerminals.HV_LOAD_BUNDLE && kind.station->outputs
        else->emptyList()
    }
    override fun renderWireCount(terminal: Int)=if(terminal==50 || terminal in ServiceTerminals.HV_LINE_BUNDLE..ServiceTerminals.HV_LOAD_BUNDLE)3 else 1
    override fun gridPhase(terminal: Int): Int?=when(terminal) { in 32..34->terminal-32;in 36..38->terminal-36;else->null }
    override fun gridCircuitKey(terminal: Int)=when(terminal) { in 32..34->"HV${terminal-32}";in 36..38->"HV${terminal-36}";else->ServiceTerminals.key(terminal) }
    override fun gridCablePoint(terminal: Int,wire: Int): Vec3 {
        if(!kind.station && ServiceTerminals.isService(terminal))return ServiceCableMount.point(terminal,wire,serviceMountReach)
        if(terminal==ServiceTerminals.HV_LINE_BUNDLE)return gridCablePoint(32+wire,0)
        if(terminal==ServiceTerminals.HV_LOAD_BUNDLE)return gridCablePoint(36+wire,0)
        val phase=gridPhase(terminal) ?: 0
        return if(kind.station)Vec3(if(terminal in 36..38)2.5 else -2.5,kind.height-.65,(phase-1)*kind.spread)
            else Vec3(0.0,kind.height-.75,(phase-1)*kind.spread)
    }
    private fun modelHit(hit: Vec3)=hit.yRot(-Math.toRadians(highVoltageRenderAngle.toDouble()).toFloat())
    override fun aimedSingleGridTerminal(side: Direction,localHit: Vec3): Int {
        if(!kind.station && localHit.y<8)return (40..42).minBy { renderedGridCablePoint(it,0).distanceToSqr(localHit) }
        val hit=modelHit(localHit)
        return (if(kind.station && hit.x>0)36 else 32)+(if(hit.z<-.16)0 else if(hit.z>.16)2 else 1)
    }
    override fun aimedGridTerminal(side: Direction,localHit: Vec3)=
        if(!kind.station && localHit.y<8)ServiceTerminals.BUNDLE else if(kind.station && modelHit(localHit).x>0)ServiceTerminals.HV_LOAD_BUNDLE else ServiceTerminals.HV_LINE_BUNDLE
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): ElectricalLoad? {
        if(!kind.station && side==Direction.YN)return services[UndergroundService.circuit(lrdu)]
        if(!kind.riser || lrdu!=LRDU.Down || side.isY)return null
        return when(side) { front.left()->inputs[0];front.right()->inputs[1];front.inverse->inputs[2];else->null }
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun newContainer(side: Direction,player: Player)=mods.eln.misc.BasicContainer(player,inventory,arrayOf())
    override fun multiMeterString(side: Direction)=inputs.mapIndexed { i,p->"${listOf("A","B","C")[i]} ${Utils.plotVolt("",p.voltage)} ${Utils.plotAmpere("",p.current)}" }.joinToString("\n")+
        if(kind.station)outputs.mapIndexed { i,p->"\n${tr("Output")} ${listOf("A","B","C")[i]} ${Utils.plotVolt("",p.voltage)} ${Utils.plotAmpere("",p.current)}" }.joinToString("") else services.mapIndexed { i,p->"\n${ServiceTerminals.label(40+i)} ${Utils.plotVolt("",p.voltage)} ${Utils.plotAmpere("",p.current)}" }.joinToString("")
    override fun thermoMeterString(side: Direction)=plotAmbientCelsius("T",0.0)
    private fun writeSettings(tag: CompoundTag) { tag.putBoolean("transmissionPrimaryClosed",primaryClosed);tag.putBoolean("transmissionSecondaryClosed",secondaryClosed);tag.putInt("transmissionTerminal",selected) }
    private fun readSettings(tag: CompoundTag) {
        primaryClosed=!tag.contains("transmissionPrimaryClosed") || tag.getBoolean("transmissionPrimaryClosed")
        secondaryClosed=!tag.contains("transmissionSecondaryClosed") || tag.getBoolean("transmissionSecondaryClosed")
        val terminal=tag.getInt("transmissionTerminal");selected=if(gridLoads(terminal).isNotEmpty())terminal else ServiceTerminals.HV_LINE_BUNDLE
        applyContacts()
    }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);writeSettings(tag) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);readSettings(tag) }
    override fun getItemStackNBT(): CompoundTag=CompoundTag().also(::writeSettings)
    override fun readItemStackNBT(tag: CompoundTag?) { super.readItemStackNBT(tag);if(tag!=null)readSettings(tag) }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);stream.writeBoolean(primaryClosed);stream.writeBoolean(secondaryClosed);stream.writeInt(selected)
        inputs.forEach { stream.writeDouble(it.voltage) };outputs.forEach { stream.writeDouble(it.voltage) }
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val event=super.networkUnserialize(stream)
        when(event.toInt()) {
            80->operate(true);81->operate(false)
            82->{ val next=stream.readInt();if(gridLoads(next).isNotEmpty()) { selected=next;needPublish() } }
        }
        return event
    }
}
