package mods.eln.transparentnode.pump

import mods.eln.fluid.FluidTankInfo
import mods.eln.fluid.ISidedFluidHandler
import mods.eln.misc.McRegistries
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/** Separate raw and pumped reservoirs: inserting fluid can never bypass the motor. */
class PumpFluidPorts: ISidedFluidHandler {
    companion object { const val CAPACITY=40_000 }
    var intake=Direction.DOWN
    val input=FluidTank(CAPACITY)
    val output=FluidTank(CAPACITY)
    override fun canFill(from: Direction?,fluid: Fluid?)=(from==null || from==intake) && fluid!=null && fluid!==net.minecraft.world.level.material.Fluids.EMPTY && (input.isEmpty || input.fluid.fluid===fluid)
    override fun canDrain(from: Direction?,fluid: Fluid?)=(from==null || from!=intake) && fluid!=null && !output.isEmpty && output.fluid.fluid===fluid
    override fun fill(from: Direction?,resource: FluidStack?,doFill: Boolean): Int =
        if(resource==null || resource.isEmpty || !canFill(from,resource.fluid))0 else input.fill(resource,action(doFill))
    override fun drain(from: Direction?,resource: FluidStack?,doDrain: Boolean): FluidStack =
        if(resource==null || resource.isEmpty || (from!=null && from==intake))FluidStack.EMPTY else output.drain(resource,action(doDrain))
    override fun drain(from: Direction?,maxDrain: Int,doDrain: Boolean): FluidStack =
        if(maxDrain<=0 || (from!=null && from==intake))FluidStack.EMPTY else output.drain(maxDrain,action(doDrain))
    override fun getTankInfo(from: Direction?)=when(from) {
        null->arrayOf(FluidTankInfo(input.fluid.copy(),CAPACITY),FluidTankInfo(output.fluid.copy(),CAPACITY))
        intake->arrayOf(FluidTankInfo(input.fluid.copy(),CAPACITY))
        else->arrayOf(FluidTankInfo(output.fluid.copy(),CAPACITY))
    }
    private fun action(execute: Boolean)=if(execute)FluidAction.EXECUTE else FluidAction.SIMULATE
    private fun request(source: IFluidHandler,limit: Int): FluidStack = if(output.isEmpty)source.drain(limit,FluidAction.SIMULATE)
        else source.drain(output.fluid.copyWithAmount(limit),FluidAction.SIMULATE)
    fun available(source: IFluidHandler?,limit: Int): FluidStack {
        val actualSource=if(!input.isEmpty)input else source ?: return FluidStack.EMPTY
        if(limit<=0)return FluidStack.EMPTY
        val candidate=request(actualSource,minOf(limit,CAPACITY-output.fluidAmount))
        if(candidate.isEmpty || output.fill(candidate,FluidAction.SIMULATE)!=candidate.amount)return FluidStack.EMPTY
        return candidate
    }
    fun pump(source: IFluidHandler?,limit: Int): Int {
        val candidate=available(source,limit)
        if(candidate.isEmpty)return 0
        val actualSource=if(!input.isEmpty)input else source!!
        val moved=actualSource.drain(candidate,FluidAction.EXECUTE)
        if(moved.isEmpty)return 0
        // Providers must honor the simulated amount/type; never turn a mismatched liquid into another one.
        val accepted=output.fill(moved,FluidAction.EXECUTE)
        if(accepted<moved.amount)actualSource.fill(moved.copyWithAmount(moved.amount-accepted),FluidAction.EXECUTE)
        return accepted
    }
    fun save(tag: CompoundTag) {
        tag.putInt("pumpIntake",intake.ordinal)
        tag.put("pumpRaw",input.writeToNBT(McRegistries.access(),CompoundTag()))
        tag.put("pumpOutput",output.writeToNBT(McRegistries.access(),CompoundTag()))
    }
    fun load(tag: CompoundTag) {
        intake=Direction.entries.getOrNull(tag.getInt("pumpIntake")) ?: Direction.DOWN
        input.readFromNBT(McRegistries.access(),tag.getCompound("pumpRaw"))
        output.readFromNBT(McRegistries.access(),tag.getCompound("pumpOutput"))
        for(tank in listOf(input,output))if(tank.fluidAmount>CAPACITY)tank.fluid=tank.fluid.copyWithAmount(CAPACITY)
    }
}

/** Credit comes only from accepted electrical steps, never trial solves or offline time. */
class PumpMotor {
    companion object {
        const val VOLTS=480.0;const val WATTS=4000.0;const val MB_PER_SECOND=40_000.0
        const val RESISTANCE=VOLTS*VOLTS/WATTS
        fun ratio(volts: Double)=if(volts.isFinite() && kotlin.math.abs(volts) in 48.0..720.0)kotlin.math.abs(volts)/VOLTS else 0.0
    }
    private var credit=0.0
    val available get()=kotlin.math.floor(credit+1e-8).toInt()
    fun account(seconds: Double,volts: Double,watts: Double) {
        val speed=ratio(volts)
        if(!seconds.isFinite() || seconds<=0 || !watts.isFinite() || watts<=0 || speed==0.0)return
        val paid=watts*seconds/(WATTS/MB_PER_SECOND*speed)
        credit=minOf(10_000.0,credit+minOf(paid,MB_PER_SECOND*speed*seconds))
    }
    fun consume(amount: Int) { require(amount>=0 && amount<=available);credit=(credit-amount).coerceAtLeast(0.0) }
    fun clear() { credit=0.0 }
}
