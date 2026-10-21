package mods.eln.transparentnode.renewable

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.power.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.tags.FluidTags
import java.io.DataOutputStream
import kotlin.math.*

/** Regulated renewable alternator with bounded environmental power, including under a short. */
class RenewableSupply(val source: SwitchableVoltageSource,private val available: () -> Double) : ConservativePowerProcess {
    var watts=0.0
        private set
    private var command=0.0
    private var blocked=false
    private var region="OPEN"
    override fun beginStep(seconds: Double) { blocked=false;watts=available().takeIf { it.isFinite() }?.coerceIn(0.0,20_000.0) ?: 0.0 }
    override fun prepareStep() { source.enabled=watts>.01;if(source.enabled) { command=3200.0;source.voltage=command } }
    override fun rootSystemPreStepProcess() {
        if(blocked || watts<=.01) { source.enabled=false;region="OPEN";return }
        val t=source.subSystem?.sourceThevenin(source) ?: return
        // A speculative coupled voltage can temporarily backfeed this port. Open
        // for that trial, then re-probe if another controller changes the network.
        if(!t.voltage.isFinite() || t.resistance.isNaN() || t.resistance<=0 || t.voltage>3200.0) { source.enabled=false;region="OPEN";return }
        val disc=hypot(t.voltage,2*sqrt(watts)*sqrt(t.resistance))
        val voltage=if(t.resistance>=1e18)3200.0 else if(t.voltage>=0)(t.voltage+disc)/2 else 2*watts*t.resistance/(disc-t.voltage)
        val currentVoltage=t.voltage+50*t.resistance
        command=minOf(3200.0,voltage,currentVoltage).coerceAtLeast(0.0);source.voltage=command;source.enabled=true
        region=when(command) { 3200.0 -> "VOLTAGE";currentVoltage -> "CURRENT";else -> "POWER" }
    }
    override fun acceptsCandidate(): Boolean {
        if(!source.enabled)return true
        val power=pendingSourcePower(source)
        val amps=-(source.subSystem?.pendingValue(source.currentState) ?: Double.NaN)
        return commandMatches(source.voltage,command) && power.isFinite() && power in -1e-6..(watts*1.000001+1e-5) && amps in -1e-7..50.00005 &&
            supplyLimitReached(source.voltage,amps,3200.0,watts,50.0)
    }
    override fun failClosed() { blocked=true;source.enabled=false }
    override fun iterationRegion()=region
    override fun trialSources()=listOf(source)
    override fun connectedSystems()=setOfNotNull(source.subSystem)
    override fun voltageSearchHints()=mapOf(source to doubleArrayOf(3200.0))
}

class LargeRenewableElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : TransparentNodeElement(node,descriptor) {
    private val water=(descriptor as RenewableDescriptor).kind==RenewableKind.WATER
    val output=NbtElectricalLoad("renewableOutput").apply { serialResistance=.000001 }
    private val internal=NbtElectricalLoad("renewableInternal").apply { serialResistance=.000001 }
    val source=SwitchableVoltageSource("renewableGenerator").apply { connectTo(internal,null) }
    var availableWatts=0.0
        private set
    var environment=0.0
        private set
    val controller=RenewableSupply(source) { availableWatts }
    private var timeout=0.0
    override val inventory=TransparentNodeElementInventory(0,1,this)
    init {
        electricalLoadList.addAll(listOf(output,internal))
        electricalComponentList.addAll(listOf(source,Resistor(internal,output).apply { resistance=.02 },Resistor(output,null).pullDown()))
        slowProcessList.add(NodePeriodicPublishProcess(node,.5,0.0))
        slowProcessList.add(IProcess { seconds -> timeout-=seconds;if(timeout<=0) { timeout=2.0;sampleEnvironment() } })
    }
    private fun offset(x: Int,y: Int,z: Int): BlockPos {
        val c=Coordinate(x,y,z,0);c.applyTransformation(front,node!!.coordinate);return BlockPos(c.x,c.y,c.z)
    }
    fun sampleEnvironment() {
        val world=node?.coordinate?.world() ?: return
        if(water) {
            var moving=0
            // Canonical -X front; the wheel is behind the control pedestal. These
            // water cells are outside ghost clearance, so flowing water remains visible.
            for(z in listOf(-3,0,3)) {
                val p=offset(2,0,z)
                if(world.hasChunkAt(p)) {
                    val fluid=world.getFluidState(p)
                    if(fluid.`is`(FluidTags.WATER) && !fluid.isSource && !fluid.isEmpty)moving++
                }
            }
            environment=moving/3.0;availableWatts=10_000.0*environment
        } else {
            var clear=0
            // Sparse open-air samples, not a volume scan and never a chunk load.
            for(y in listOf(6,9,12))for(z in listOf(-3,0,3)) {
                val p=offset(-3,y,z)
                if(world.hasChunkAt(p) && world.canSeeSky(p) && world.getBlockState(p).isAir)clear++
            }
            val heightFactor=((node!!.coordinate.y+74)/128.0).coerceIn(.5,1.5)
            environment=(Eln.wind.windHit*heightFactor+world.getRainLevel(0f)+2*world.getThunderLevel(0f)).coerceAtLeast(0.0)
            availableWatts=windPower(environment,clear/9.0)
        }
        needPublish()
    }
    override fun initialize() { sampleEnvironment();connect() }
    override fun connectJob() { super.connectJob();Eln.simulator.mna.addProcess(controller) }
    override fun disconnectJob() { Eln.simulator.mna.removeProcess(controller);source.enabled=false;super.disconnectJob() }
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(side==front && lrdu==LRDU.Down)output else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=false
    override fun newContainer(side: Direction,player: net.minecraft.world.entity.player.Player)=null
    override fun multiMeterString(side: Direction)=Utils.plotUIP(output.voltage,source.current)
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream);stream.writeDouble(environment);stream.writeDouble(availableWatts);stream.writeDouble(source.power.coerceAtLeast(0.0))
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream)
    }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);availableWatts=0.0;timeout=0.0;source.enabled=false }
    override fun getWaila()=linkedMapOf(tr("Rated voltage") to tr("3200 V"),tr("Available power") to Utils.plotPower("",availableWatts),tr("Produced power") to Utils.plotPower("",source.power.coerceAtLeast(0.0)))
    companion object {
        fun windPower(speed: Double,exposure: Double)=if(!speed.isFinite() || speed<3 || speed>=25)0.0 else
            (20_000.0*(speed/12).pow(3)*exposure.coerceIn(0.0,1.0)).coerceIn(0.0,20_000.0)
    }
}
