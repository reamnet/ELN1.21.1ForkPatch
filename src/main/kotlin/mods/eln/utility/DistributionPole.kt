package mods.eln.utility

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.gridnode.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sound.SoundCommand
import mods.eln.transparentnode.standby.TransferContact
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** Stable enum order: IDs 7970 onward are persisted by worlds. Append new variants only. */
enum class DistributionKind(val title: String,val phases: Int=3,val transformer: Boolean=false,val light: Boolean=false,
    val cutout: Boolean=false,val recloser: Boolean=false,val regulator: Boolean=false,val lvOnly: Boolean=false,val singlePhaseTap: Boolean=false) {
    SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Utility Pole"),1),
    THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Utility Pole")),
    TRANSFORMER_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Transformer Pole"),1,true),
    TRANSFORMER_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Transformer Pole"),3,true),
    FEEDER_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Underground Feeder Pole")),
    CUTOUT_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Fused Cutout Pole"),1,cutout=true),
    CUTOUT_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Fused Cutout Pole"),cutout=true),
    RECLOSER_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Recloser Pole"),recloser=true),
    SECONDARY(I18N.TR_NAME(I18N.Type.NONE,"120/240V Secondary Utility Pole"),lvOnly=true),
    SECONDARY_LIGHT(I18N.TR_NAME(I18N.Type.NONE,"120/240V Secondary Street Light Pole"),light=true,lvOnly=true),
    CAPACITOR_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Pole Mounted Capacitor Bank")),
    REGULATOR_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Automatic Voltage Regulator Pole"),1,regulator=true),
    REGULATOR_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Automatic Voltage Regulator Pole"),regulator=true),
    AIR_BREAK_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Gang Operated Air Break Switch Pole")),
    STREET_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Transformer Street Light Pole"),1,true,true),
    STREET_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Transformer Street Light Pole"),3,true,true),
    SECONDARY_FED_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"Single Phase Secondary Fed Street Light Pole"),1,light=true),
    SECONDARY_FED_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Secondary Fed Street Light Pole"),3,light=true),
    FUSED_TAP_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Pole with Fused Single Phase Tap"),singlePhaseTap=true),
    FUSED_TAP_LIGHT_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Fused Tap Secondary Fed Street Light Pole"),light=true,singlePhaseTap=true),
    FUSED_TAP_TRANSFORMER_LIGHT_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Phase Transformer Street Light Pole with Fused Single Phase Tap"),transformer=true,light=true,singlePhaseTap=true),
    SERVICE_PEDESTAL(I18N.TR_NAME(I18N.Type.NONE,"Fused Underground 120/240V Service Pedestal"),1,cutout=true,lvOnly=true),
    UNDERGROUND_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"12.5kV Single Phase Fused Underground Feeder"),1,cutout=true),
    UNDERGROUND_THREE(I18N.TR_NAME(I18N.Type.NONE,"12.5kV Three Phase Fused Underground Feeder"),cutout=true),
    UNDERGROUND_LIGHT_SINGLE(I18N.TR_NAME(I18N.Type.NONE,"12.5kV Single Phase Fused Underground Feeder with Street Light"),1,light=true,cutout=true),
    UNDERGROUND_LIGHT_THREE(I18N.TR_NAME(I18N.Type.NONE,"12.5kV Three Phase Fused Underground Feeder with Street Light"),light=true,cutout=true),
    TWIN_SECONDARY_LIGHT(I18N.TR_NAME(I18N.Type.NONE,"Utility Pole Twin LED Street Light"),light=true,lvOnly=true);
    val twinLight get()=this==TWIN_SECONDARY_LIGHT
    val lampWatts get()=if(twinLight)120.0 else 70.0
    val lampHeads get()=if(twinLight)listOf(Vec3(-1.15,5.2,-.62),Vec3(-1.15,5.2,.62)) else listOf(Vec3(-1.15,5.2,0.0))
    val underground get()=this in SERVICE_PEDESTAL..UNDERGROUND_LIGHT_THREE
    val pedestal get()=this==SERVICE_PEDESTAL
    val fuseCount get()=if(transformer && singlePhaseTap)2 else if(transformer || singlePhaseTap)1 else if(cutout || regulator)phases else 0
    val tapFuseIndex get()=if(transformer)1 else 0
    val protectedLine get()=cutout || recloser || this==AIR_BREAK_THREE || regulator || singlePhaseTap
    val outputPhases get()=if(singlePhaseTap)1 else phases
    val capacitor get()=this==CAPACITOR_THREE
}

class DistributionPoleElement(node: TransparentNode,descriptor: TransparentNodeDescriptor):
    GridElement(node,descriptor,(descriptor as GridDescriptor).connectRange) {
    override val alignsPoleSpans get()=!kind.underground
    override val allowsNewGridLinks get()=!kind.underground
    override val alignsPrimaryPhaseOrder get()=kind.phases==3 && !kind.lvOnly
    override fun contributesToPoleAlignment(terminal: Int)=!kind.singlePhaseTap || terminal!=36
    val kind=(descriptor as DistributionPoleDescriptor).kind
    override val inventory=object: TransparentNodeElementInventory(if(kind.twinLight)2 else if(kind.transformer)1 else 0,1,this@DistributionPoleElement) {
        override fun canPlaceItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=kind.transformer && slot==0 && isStepDownUnit(stack)
        override fun getAccessibleSlotsFromSide(side: Int)=if(kind.twinLight)intArrayOf(0,1) else if(kind.transformer)intArrayOf(0) else intArrayOf()
        override fun canTakeItemThroughFace(slot: Int,stack: ItemStack?,side: Int)=kind.twinLight && slot in 0..1 || kind.transformer && slot==0
    }
    val installed get()=kind.transformer && isStepDownUnit(inventory.getItem(0))
    val lines=List(kind.phases) { NbtElectricalLoad("phase$it").apply { serialResistance=.00001 } }
    val outs=if(kind.protectedLine)List(kind.outputPhases) { NbtElectricalLoad("phaseOut$it").apply { serialResistance=.00001 } } else lines
    val fuses=List(kind.fuseCount) { CutoutFuse() }
    val recloser=RecloserControl()
    var selectedTerminal=if(kind.lvOnly)ServiceTerminals.BUNDLE else 32
        private set
    var transformerPhase=0
        private set
    var fuseAmps=100.0
        private set
    var gangClosed=true
        private set
    var regulatorTarget=12_470.0
        private set
    var regulatorAutomatic=true
        private set
    val unit=if(kind.transformer)MainsStepDown(this, poleTransformer = true) { installed && fuses[0].closed } else null
    val secondary=unit?.let { ServiceSecondary(this,it.secondary) { it.outputVoltage.toDouble() } }
    val services: List<NbtElectricalLoad> = secondary?.loads ?: List(3) { NbtElectricalLoad("secondaryBus$it").apply { serialResistance=.00001 } }
    val undergroundInput=if(kind.pedestal)List(3) { NbtElectricalLoad("undergroundServiceIn$it").apply { serialResistance=.00001 } } else emptyList()
    private val undergroundSplit=if(kind.pedestal)WiredServiceSplit(this,undergroundInput) else null
    private val regulatorInputs=if(kind.regulator)List(kind.phases) { NbtElectricalLoad("regulatorInput$it").apply { serialResistance=.00001 } } else emptyList()
    private val contacts=when {
        kind.pedestal -> undergroundInput.indices.map { TransferContact(undergroundInput[it],services[it]) }
        kind.singlePhaseTap -> lines.map { TransferContact(it,outs[0]) }
        kind.transformer -> lines.map { TransferContact(it,unit!!.primary) }
        kind.regulator -> lines.indices.map { TransferContact(lines[it],regulatorInputs[it]) }
        kind.protectedLine && !kind.regulator -> lines.indices.map { TransferContact(lines[it],outs[it]) }
        else -> emptyList()
    }
    private val transformerContacts=if(kind.transformer && kind.singlePhaseTap)lines.map { TransferContact(it,unit!!.primary) } else emptyList()
    private val transformerFeed get()=if(transformerContacts.isNotEmpty())transformerContacts else contacts
    private fun fuseCurrent(index: Int)=if(kind.pedestal)abs(contacts[2].current)+.5*(abs(contacts[0].current)+abs(contacts[1].current)) else if(kind.transformer && index==0)transformerFeed.getOrNull(transformerPhase)?.current ?: 0.0
        else contacts.getOrNull(if(kind.singlePhaseTap)transformerPhase else index)?.current ?: 0.0
    private val regulatorTaps=if(kind.regulator)lines.indices.map { i ->
        val pin=NbtElectricalLoad("regulatorTap$i")
        electricalLoadList.add(pin)
        electricalComponentList.add(Resistor(pin,outs[i]).apply { resistance=.005 })
        ServiceTap(regulatorInputs[i],pin,1.0).also(electricalComponentList::add)
    } else emptyList()
    // Keep migrated transformer hardware recoverable, but there is no transformer in this LV pole.
    var lampVoltage=120
        private set
    val lamp=Resistor(services[0],null)
    private val lamp240=if(kind.twinLight)Resistor(services[2],null) else null
    val lampPower get()=lamp.power+(lamp240?.power ?: 0.0)
    var lampEnabled=true
        private set
    var lampAutomatic=false
        private set
    var lampYaw=0
        private set
    var lampHeightStep=0
        private set
    var lampPitch=0
        private set
    val yaw get()=unit?.lampYaw ?: lampYaw
    val heightStep get()=unit?.lampHeightStep ?: lampHeightStep
    val pitch get()=unit?.lampPitch ?: lampPitch
    var lightLevel=0
        private set
    private var regulatorClock=0.0
    private var clearancesReady=false
    private val lightRequested get()=kind.light && lampEnabled && (unit?.lampEnabled!=false) &&
        (!(lampAutomatic || unit?.lampAutomatic==true) || (node?.coordinate?.world() as? ServerLevel)?.skyDarken?.let { it>=4 }!=false)
    val terminals get()=buildList {
        if(!kind.lvOnly) { repeat(kind.phases) { add(32+it) };if(kind.protectedLine)repeat(kind.outputPhases) { add(36+it) } }
        add(ServiceTerminals.BUNDLE);addAll(40..42)
    }
    init {
        electricalLoadList.addAll(undergroundInput)
        if(!kind.lvOnly)electricalLoadList.addAll(lines);electricalLoadList.addAll(regulatorInputs)
        if(outs!==lines && !kind.pedestal)electricalLoadList.addAll(outs)
        if(secondary==null)electricalLoadList.addAll(services)
        electricalComponentList.addAll(contacts);electricalComponentList.addAll(transformerContacts)
        if(kind.capacitor)lines.forEachIndexed { i,line ->
            val pin=NbtElectricalLoad("capacitor$i");electricalLoadList.add(pin)
            electricalComponentList.add(Resistor(line,pin).apply { resistance=100.0 })
            electricalComponentList.add(Capacitor(pin,null).apply { setCoulombs(.0001) })
            electricalComponentList.add(Resistor(pin,null).apply { resistance=1e9 })
        }
        if(kind.light)electricalComponentList.add(lamp)
        lamp240?.let(electricalComponentList::add)
        electricalProcessList.add(IProcess(::step))
        slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5))
        slowProcessList.add(IProcess { if(!clearancesReady)clearancesReady=PoleHeightUpgrade.update(this) })
        applyContacts()
    }
    private fun applyContacts() {
        contacts.forEachIndexed { i,c -> c.closed=when {
            kind.pedestal -> fuses[0].closed
            kind.singlePhaseTap -> transformerPhase==i && fuses[kind.tapFuseIndex].closed
            kind.transformer -> installed && transformerPhase==i && fuses[0].closed
            kind.cutout || kind.regulator -> fuses[i].closed
            kind.recloser -> recloser.closed
            else -> gangClosed
        } }
        transformerContacts.forEachIndexed { i,c -> c.closed=installed && transformerPhase==i && fuses[0].closed }
    }
    private fun step(dt: Double) {
        secondary?.update();undergroundSplit?.update()
        if(kind.transformer) {
            val limited=unit!!.outputOverloaded
            val amps=fuseCurrent(0)
            val rating=unit.ratedWatts/MainsStepDown.NOMINAL_POLE_VOLTS
            if(fuses[0].step(dt,amps,rating,limited)) { sound("utility_fuse_blow",.8);applyContacts();needPublish() }
        }
        fuses.forEachIndexed { i,fuse ->
            val amps=fuseCurrent(i)
            if(fuse.pickup(dt,amps))sound("utility_arc_close",.20)
        }
        if(kind.cutout || kind.regulator)fuses.forEachIndexed { i,fuse -> if(fuse.step(dt,fuseCurrent(i),fuseAmps)) { sound("utility_fuse_blow",.8);applyContacts();needPublish() } }
        if(kind.singlePhaseTap && fuses[kind.tapFuseIndex].step(dt,fuseCurrent(kind.tapFuseIndex),fuseAmps)) { sound("utility_fuse_blow",.8);applyContacts();needPublish() }
        if(kind.recloser && recloser.step(dt,contacts.map { it.current })) { applyContacts();sound("utility_recloser_bump",.7);needPublish() }
        if(kind.regulator && regulatorAutomatic) {
            regulatorClock+=dt
            if(regulatorClock>=1) {
                regulatorClock%=1.0
                regulatorTaps.forEachIndexed { i,tap ->
                    val volts=abs(regulatorInputs[i].voltage)
                    if(volts>1) {
                        val desired=(regulatorTarget/volts).coerceIn(.90,1.10)
                        val next=(tap.ratio+(desired-tap.ratio).coerceIn(-.01,.01))
                        if(abs(next-tap.ratio)>.001) { tap.ratio=next;needPublish() }
                    }
                }
            }
        }
        if(kind.light) {
            lamp.resistance=if(lightRequested && lampVoltage==120)120.0*120/kind.lampWatts else 1e19
            lamp240?.resistance=if(lightRequested && lampVoltage==240)240.0*240/kind.lampWatts else 1e19
            val next=if(lightRequested)(15*(lampPower/kind.lampWatts).coerceIn(0.0,1.0)).toInt() else 0
            if(next!=lightLevel) { lightLevel=next;needPublish() }
            if(next>0) {
                for(head in kind.lampHeads) {
                val at=StreetLightMount.adjust(head,front,heightStep,yaw,pitch);val c=node!!.coordinate.newWithOffset(kotlin.math.floor(at.x+.5).toInt(),kotlin.math.floor(at.y+.5).toInt(),kotlin.math.floor(at.z+.5).toInt())
                val world=mods.eln.misc.DimensionIds.serverLevel(c.dimension)
                if(world?.chunkSource?.getChunkNow(c.x shr 4,c.z shr 4)!=null)mods.eln.lightblock.LightBlockEntity.addLight(c,next,5)
                }
            }
        }
    }
    private fun sound(name: String,volume: Double) { if(node?.coordinate?.let { mods.eln.misc.DimensionIds.serverLevel(it.dimension) }!=null)play(SoundCommand("eln:$name").smallRange().mulVolume(volume)) }
    fun operateFuse(index: Int) {
        val fuse=fuses.getOrNull(index) ?: return
        val loaded=abs(fuseCurrent(index))>.02
        if(fuse.operate()) {
            if(loaded && !fuse.closed)sound("utility_arc_open",.55)
            applyContacts();needPublish()
        }
    }
    fun replaceFuse(player: Player,index: Int): Boolean {
        val fuse=fuses.getOrNull(index) ?: return false
        if(fuse.hasCartridge && !fuse.blown)return false
        val cartridge=player.mainHandItem
        if(!UtilityItems.isFuse(cartridge) || !fuse.insertCartridge(cartridge))return false
        if(!player.abilities.instabuild)cartridge.shrink(1)
        applyContacts();player.inventory.setChanged();needPublish();return true
    }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        if(PoleHardware.extract(player,fuses)) { applyContacts();needPublish();return true }
        if(UtilityItems.isFuse(player.mainHandItem))return fuses.indices.firstOrNull { fuses[it].blown || !fuses[it].hasCartridge }?.let { replaceFuse(player,it) } ?: true
        if(kind.transformer && isStepDownUnit(player.mainHandItem) && !installed) {
            inventory.setItem(0,player.mainHandItem.copyWithCount(1));if(!player.abilities.instabuild)player.mainHandItem.shrink(1)
            inventoryChange(inventory);player.inventory.setChanged();return true
        }
        return super.onBlockActivated(player,side,vx,vy,vz)
    }
    override fun inventoryChange(inventory: Container?) { unit?.installRating(PoleHardware.transformerWatts(this.inventory.getItem(0)) ?: 10_000);applyContacts();needPublish() }
    override fun connectJob() { super.connectJob();unit?.let { Eln.simulator.mna.addProcess(it.controller) };applyContacts() }
    override fun disconnectJob() { unit?.let { Eln.simulator.mna.removeProcess(it.controller) };super.disconnectJob() }
    override fun selectedGridTerminal(side: Direction)=selectedTerminal
    override fun aimedSingleGridTerminal(side: Direction,localHit: Vec3): Int {
        val choices=buildList {
            add(50)
            if(!kind.lvOnly) {
                repeat(kind.phases) { add(32+it) }
                if(kind.protectedLine)repeat(kind.outputPhases) { add(36+it) }
            }
        }
        return choices.minBy { terminal ->
            (0 until renderWireCount(terminal)).minOf { renderedGridCablePoint(terminal,it).distanceToSqr(localHit) }
        }
    }
    override fun aimedGridTerminal(side: Direction,localHit: Vec3): Int {
        val nearest=aimedSingleGridTerminal(side,localHit)
        return if(kind.phases==3 && nearest in 32..38 && !(kind.singlePhaseTap && nearest==36)) {
            if(nearest<36)ServiceTerminals.HV_LINE_BUNDLE else ServiceTerminals.HV_LOAD_BUNDLE
        } else nearest
    }
    override fun getGridElectricalLoad(side: Direction)=if(kind.lvOnly)services[0] else lines[0]
    override fun acceptsSavedGridTerminal(terminal: Int)=!kind.twinLight || terminal==50 || terminal in 40..42
    override fun gridLoads(terminal: Int): List<ElectricalLoad> = when {
        !acceptsSavedGridTerminal(terminal) -> emptyList()
        terminal in 0..5 -> listOf(getGridElectricalLoad(Direction.XN))
        terminal in 32 until 32+kind.phases && !kind.lvOnly -> listOf(lines[terminal-32])
        terminal in 36 until 36+kind.outputPhases && kind.protectedLine && !kind.lvOnly -> listOf(outs[terminal-36])
        terminal in 40..42 -> listOf(services[terminal-40])
        terminal==50 -> services
        terminal==ServiceTerminals.HV_LINE_BUNDLE && kind.phases==3 && !kind.lvOnly -> lines
        terminal==ServiceTerminals.HV_LOAD_BUNDLE && kind.phases==3 && kind.protectedLine && !kind.singlePhaseTap -> outs
        else -> emptyList()
    }
    override fun gridCircuitKey(terminal: Int)=when {
        terminal<6->"LEGACY"
        terminal in listOf(32,36) && kind.phases==1->"HV_SINGLE"
        terminal==36 && kind.singlePhaseTap->"HV$transformerPhase"
        else->ServiceTerminals.key(terminal)
    }
    override fun gridPhase(terminal: Int): Int?=when {
        kind.phases!=3 || kind.lvOnly->null
        terminal in 32..34->terminal-32
        terminal==36 && kind.singlePhaseTap->transformerPhase
        terminal in 36..38 && kind.protectedLine->terminal-36
        else->null
    }
    override fun phaseBusTerminals(terminal: Int)=if(kind.phases==1 && terminal in listOf(32,36))listOf(32,36).filter { gridLoads(it).isNotEmpty() } else listOf(terminal)
    override fun renderWireCount(terminal: Int)=if(terminal==50 || terminal in ServiceTerminals.HV_LINE_BUNDLE..ServiceTerminals.HV_LOAD_BUNDLE)3 else 1
    override fun gridCablePoint(terminal: Int,wire: Int): Vec3=when {
        terminal==ServiceTerminals.HV_LINE_BUNDLE -> gridCablePoint(32+wire,0)
        terminal==ServiceTerminals.HV_LOAD_BUNDLE -> gridCablePoint(36+wire,0)
        terminal==50 -> Vec3(.405,4.65+wire*.32,0.0)
        terminal in 40..42 -> Vec3(.405,4.65+(terminal-40)*.32,0.0)
        (terminal==32 && kind.phases==1 || terminal==33 && kind.phases==3) -> Vec3(0.0,7.15,0.0)
        terminal==36 && kind.singlePhaseTap -> Vec3(-.68,5.85,if(kind.transformer).65 else 0.0)
        terminal in 36..38 && kind.cutout -> Vec3(-.68,5.85,phaseZ(terminal-36))
        terminal in 36..38 && kind.regulator -> Vec3(.65,5.50,phaseZ(terminal-36)*.73)
        terminal in 36..38 -> Vec3(.35,6.53,phaseZ(terminal-36))
        else -> Vec3(0.0,6.63,phaseZ((terminal-32).coerceIn(0,kind.phases-1)))
    }
    private fun phaseZ(index: Int)=if(kind.phases==1)0.0 else (index-1)*.95
    override fun getElectricalLoad(side: Direction,lrdu: LRDU): ElectricalLoad? {
        if(side==Direction.YN)return (if(kind.pedestal)undergroundInput else services)[UndergroundService.circuit(lrdu)]
        if(kind.underground && !kind.pedestal) {
            if(side==front)return when(lrdu) { LRDU.Left->services[0];LRDU.Right->services[1];else->services[2] }
            val phase=when(side) { front.left()->0;front.right()->1;front.inverse->2;else->return null }
            if(phase>=kind.phases)return null
            return if(lrdu==LRDU.Up)outs[phase] else if(lrdu==LRDU.Down)lines[phase] else null
        }
        if(lrdu!=LRDU.Down || side.isY)return null
        val i=when(side) { front.left()->0;front.right()->1;front.inverse->2;else->return null }
        return if(kind==DistributionKind.FEEDER_THREE)lines[i] else services[i]
    }
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=DistributionContainer(player,inventory,kind.transformer,kind.twinLight)
    override fun multiMeterString(side: Direction)=PoleMeter.readings(if(kind.lvOnly)emptyList() else lines,services,
        if(kind.protectedLine && !kind.pedestal)outs else emptyList(),if(kind.singlePhaseTap)transformerPhase else 0).joinToString("\n")
    override fun thermoMeterString(side: Direction)=unit?.let { plotAmbientCelsius("T",it.thermal.temperatureCelsius) } ?: ""
    override fun getWaila()=linkedMapOf(tr("Cable terminal") to ServiceTerminals.label(selectedTerminal),(if(kind.singlePhaseTap)tr("Tap phase") else tr("Transformer phase")) to listOf("A","B","C")[transformerPhase],tr("Protection") to when { recloser.locked->tr("Recloser locked out");fuses.any { it.blown }->tr("Fuse blown");else->tr("Ready") })
    private fun writeSettings(nbt: CompoundTag) {
        unit?.write(nbt);nbt.putInt("utilityTerminal",selectedTerminal);nbt.putInt("utilityPhase",transformerPhase);nbt.putDouble("utilityFuseAmps",fuseAmps);nbt.putBoolean("utilityGangClosed",gangClosed)
        fuses.forEachIndexed { i,f -> f.write(nbt,"utilityFuse$i") };recloser.write(nbt)
        nbt.putDouble("regulatorTarget",regulatorTarget);nbt.putBoolean("regulatorAuto",regulatorAutomatic);regulatorTaps.forEachIndexed { i,t -> nbt.putDouble("regulatorRatio$i",t.ratio) }
        nbt.putInt("utilityLampYaw",lampYaw);nbt.putInt("utilityLampHeight",lampHeightStep);nbt.putInt("utilityLampPitch",lampPitch)
        nbt.putInt("utilityLampVoltage",lampVoltage);nbt.putBoolean("utilityLamp",lampEnabled);nbt.putBoolean("utilityDusk",lampAutomatic)
    }
    private fun readSettings(nbt: CompoundTag) {
        unit?.read(nbt);if(unit!=null) { PoleHardware.migrate(this,unit.ratedWatts);unit.installRating(PoleHardware.transformerWatts(inventory.getItem(0)) ?: 10_000) };selectedTerminal=nbt.getInt("utilityTerminal").takeIf { it in terminals } ?: terminals.first()
        transformerPhase=nbt.getInt("utilityPhase").coerceIn(0,kind.phases-1);fuseAmps=nbt.getDouble("utilityFuseAmps").takeIf { it.isFinite() && it in 1.0..2000.0 } ?: 100.0
        gangClosed=!nbt.contains("utilityGangClosed") || nbt.getBoolean("utilityGangClosed");fuses.forEachIndexed { i,f -> f.read(nbt,"utilityFuse$i") };recloser.read(nbt)
        regulatorTarget=nbt.getDouble("regulatorTarget").takeIf { it.isFinite() && it in 120.0..51_200.0 } ?: 12_470.0;regulatorAutomatic=!nbt.contains("regulatorAuto") || nbt.getBoolean("regulatorAuto")
        regulatorTaps.forEachIndexed { i,t -> t.ratio=nbt.getDouble("regulatorRatio$i").takeIf { it.isFinite() && it in .9..1.1 } ?: 1.0 }
        lampVoltage=nbt.getInt("utilityLampVoltage").takeIf { kind.twinLight && it==240 } ?: 120
        lampYaw=nbt.getInt("utilityLampYaw").takeIf(StreetLightMount::validYaw) ?: 0
        lampHeightStep=nbt.getInt("utilityLampHeight").takeIf(StreetLightMount::validStep) ?: 0
        lampPitch=nbt.getInt("utilityLampPitch").takeIf(StreetLightMount::validPitch) ?: 0
        lampEnabled=!nbt.contains("utilityLamp") || nbt.getBoolean("utilityLamp");lampAutomatic=nbt.getBoolean("utilityDusk");secondary?.update();applyContacts()
    }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);writeSettings(nbt) }
    private fun recoverTwinHardware(nbt: CompoundTag) {
        if(!TwinPoleMigration.legacy(kind,nbt))return
        PoleHardware.migrate(this,nbt.getInt("mainsRatedWatts"))
        val oldFuse=CutoutFuse().apply { read(nbt,"poleFuse") }
        if(oldFuse.hasCartridge && inventory.getItem(1).isEmpty)inventory.setItem(1,oldFuse.removeCartridge())
    }
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt);recoverTwinHardware(nbt);readSettings(TwinPoleMigration.settings(kind,nbt))
    }
    override fun getItemStackNBT()=CompoundTag().also(::writeSettings)
    override fun readItemStackNBT(nbt: CompoundTag?) { if(nbt!=null) { recoverTwinHardware(nbt);readSettings(TwinPoleMigration.settings(kind,nbt)) } }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        DistributionDisplay().apply {
            terminal=selectedTerminal;phase=transformerPhase;rating=unit?.ratedWatts ?: 10_000;voltage=unit?.outputVoltage ?: lampVoltage
            enabled=unit?.enabled ?: gangClosed;installed=this@DistributionPoleElement.installed
            fuseClosed=fuses.map { it.closed };fuseBlown=fuses.map { it.blown };fuseRating=fuses.firstOrNull()?.rating(fuseAmps) ?: fuseAmps
            locked=recloser.locked;closed=recloser.closed;failures=recloser.failures;remaining=recloser.remaining;pickup=recloser.pickupAmps;delay=recloser.retrySeconds;maxFailures=recloser.maxFailures
            target=regulatorTarget;automatic=regulatorAutomatic;ratios=regulatorTaps.map { it.ratio };light=lightLevel;lampOn=unit?.lampEnabled ?: lampEnabled;dusk=unit?.lampAutomatic ?: lampAutomatic
            yaw=this@DistributionPoleElement.yaw;heightStep=this@DistributionPoleElement.heightStep;pitch=this@DistributionPoleElement.pitch
            input=if(kind.pedestal)undergroundInput[2].voltage else if(kind.lvOnly)services[0].voltage else lines[transformerPhase].voltage;output=if(kind.lvOnly)services[if(kind.twinLight && lampVoltage==240)2 else if(kind.pedestal)2 else 0].voltage else unit?.secondary?.voltage ?: outs[0].voltage;watts=if(kind.pedestal)contacts.indices.sumOf { undergroundInput[it].voltage*contacts[it].current } else unit?.output?.power ?: lampPower
        }.write(stream)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream)
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        if(unit?.packet(id,stream)==true) { secondary?.update();needPublish();return id }
        when(id.toInt()) {
            80 -> { val volts=stream.readInt();if(kind.twinLight && volts in listOf(120,240))lampVoltage=volts }
            82 -> lampEnabled=stream.readBoolean()
            83 -> lampAutomatic=stream.readBoolean()
            84 -> { val move=stream.readInt();if(kind.light && move in listOf(-1,1))lampHeightStep=(lampHeightStep+move).coerceIn(StreetLightMount.MIN_STEP,StreetLightMount.MAX_STEP) }
            85 -> { val move=stream.readInt();if(kind.light && move in listOf(-5,-1,1,5))lampYaw=StreetLightMount.turn(lampYaw,move) }
            98 -> { val move=stream.readInt();if(kind.light && move in listOf(-5,-1,1,5))lampPitch=(lampPitch+move).coerceIn(-60,60) }
            87 -> stream.readInt().takeIf { it in terminals }?.let { selectedTerminal=it }
            88 -> stream.readInt().takeIf { it in lines.indices }?.let { transformerPhase=it }
            89 -> operateFuse(stream.readInt())
            91 -> stream.readDouble().takeIf { it.isFinite() && it in 1.0..2000.0 }?.let { if(kind.recloser)recloser.pickupAmps=it }
            92 -> stream.readDouble().takeIf { it.isFinite() && it in 1.0..60.0 }?.let { recloser.retrySeconds=it }
            93 -> stream.readInt().takeIf { it in 1..5 }?.let { recloser.maxFailures=it }
            94 -> { recloser.reset();sound("utility_recloser_bump",.7) }
            95 -> { if(kind.recloser)recloser.operate() else gangClosed=!gangClosed;sound(if(kind.recloser)"utility_recloser_bump" else "utility_arc_open",.6) }
            96 -> stream.readDouble().takeIf { it.isFinite() && it in 120.0..51_200.0 }?.let { regulatorTarget=it }
            97 -> regulatorAutomatic=stream.readBoolean()
        }
        applyContacts();needPublish();return id
    }
}
