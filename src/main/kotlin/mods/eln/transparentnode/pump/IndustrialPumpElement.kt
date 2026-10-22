package mods.eln.transparentnode.pump

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.cord.*
import net.minecraft.core.Direction as Face
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.io.DataInputStream
import java.io.DataOutputStream

class IndustrialPumpElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor),CordDevice {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val supply=NbtElectricalLoad("pumpSupply").apply { serialResistance=.002 }
    val motor=Resistor(supply,null).apply { resistance=1e19 }
    val ports=PumpFluidPorts()
    val work=PumpMotor()
    override val cord=ApplianceCord(this,supply)
    override val cordVoltage get()=PumpMotor.VOLTS
    var enabled=true
    var status="DRY";private set
    var flowMb=0.0;private set
    private var requested=false
    override fun initialize() { connect() }
    init {
        electricalLoadList.add(supply);electricalComponentList.add(motor)
        slowPreProcessList.add(IProcess { prepare() })
        electricalProcessList.add(IProcess { dt -> if(requested)work.account(dt,supply.voltage,motor.power) })
        slowProcessList.add(IProcess(::tick))
        slowProcessList.add(NodePeriodicPublishProcess(node,.25,0.0))
    }
    private fun loadedWorld(): ServerLevel? {
        val coord=node?.coordinate ?: return null
        val world=coord.world() as? ServerLevel ?: return null
        if(!world.hasChunkAt(coord.pos) || world.getBlockEntity(coord.pos)==null)return null
        return world
    }
    private fun source(world: ServerLevel): IFluidHandler? {
        val pos=coordinate().pos.relative(ports.intake)
        if(!world.hasChunkAt(pos))return null
        return world.getCapability(Capabilities.FluidHandler.BLOCK,pos,ports.intake.opposite) ?: PumpWorldIntake.source(world,pos)
    }
    private fun prepare() {
        val world=loadedWorld()
        val speed=PumpMotor.ratio(supply.voltage)
        status=when {
            !enabled->"OFF"
            world==null->"UNLOADED"
            !supply.voltage.isFinite() || kotlin.math.abs(supply.voltage)>720->"OVERVOLTAGE"
            speed==0.0->"NO_POWER"
            ports.output.fluidAmount>=PumpFluidPorts.CAPACITY->"BLOCKED"
            ports.available(source(world),PumpFluidPorts.CAPACITY).isEmpty->if(!ports.input.isEmpty || !ports.output.isEmpty)"BLOCKED" else "DRY"
            else->"RUNNING"
        }
        requested=status=="RUNNING"
        motor.resistance=if(requested)PumpMotor.RESISTANCE else 1e19
        if(!requested)work.clear()
    }
    private fun tick(seconds: Double) {
        val world=loadedWorld()
        if(world==null) { work.clear();flowMb=0.0;return }
        val amount=if(requested && enabled)ports.pump(source(world),work.available) else 0
        work.consume(amount)
        flowMb+=(amount/seconds-flowMb)*.25
        if(!requested)flowMb=0.0
        // Stored pumped fluid remains accessible after loss of power, like any normal fluid tank.
        var remaining=(PumpMotor.MB_PER_SECOND*maxOf(1.0,PumpMotor.ratio(supply.voltage))*seconds).toInt()
        for(face in Face.entries) {
            if(face==ports.intake || ports.output.isEmpty || remaining<=0)continue
            val at=coordinate().pos.relative(face)
            if(!world.hasChunkAt(at))continue
            val destination=world.getCapability(Capabilities.FluidHandler.BLOCK,at,face.opposite) ?: continue
            val stack=ports.output.drain(remaining,FluidAction.SIMULATE)
            val accepted=destination.fill(stack,FluidAction.EXECUTE).coerceIn(0,stack.amount)
            ports.output.drain(accepted,FluidAction.EXECUTE);remaining-=accepted
        }
    }
    override fun getFluidHandler()=ports
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(!cord.plugged && side.isNotY && lrdu==LRDU.Down)supply else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=IndustrialPumpContainer(player,inventory)
    override fun multiMeterString(side: Direction)=Utils.plotUIP(supply.voltage,supply.current)
    override fun thermoMeterString(side: Direction)=""
    override fun getWaila()=linkedMapOf(tr("Supply") to multiMeterString(front),tr("Flow") to tr("%1$ B/s",String.format(java.util.Locale.ROOT,"%.1f",flowMb/1000)))
    override fun networkSerialize(out: DataOutputStream) {
        super.networkSerialize(out);out.writeBoolean(enabled);out.writeByte(ports.intake.ordinal);out.writeUTF(status)
        out.writeDouble(supply.voltage);out.writeDouble(motor.power);out.writeDouble(flowMb)
        out.writeInt(ports.input.fluidAmount);out.writeInt(ports.output.fluidAmount)
        out.writeUTF((if(!ports.output.isEmpty)ports.output.fluid else ports.input.fluid).let { if(it.isEmpty)"" else it.hoverName.string })
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(out)
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        when(id.toInt()) {
            80->{ enabled=!enabled;work.clear();prepare() }
            81->{ ports.intake=Face.entries[(ports.intake.ordinal+1)%6];work.clear();prepare();coordinate().world().invalidateCapabilities(coordinate().pos) }
        }
        needPublish();return id
    }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);ports.save(tag);tag.putBoolean("pumpEnabled",enabled) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);ports.load(tag);enabled=!tag.contains("pumpEnabled") || tag.getBoolean("pumpEnabled");work.clear() }
    override fun getItemStackNBT()=CompoundTag().also { ports.save(it);it.putBoolean("pumpEnabled",enabled) }
    override fun readItemStackNBT(tag: CompoundTag?) { super.readItemStackNBT(tag);if(tag!=null) { ports.load(tag);enabled=!tag.contains("pumpEnabled") || tag.getBoolean("pumpEnabled") } }
}
