package mods.eln.transparentnode.laundry

import mods.eln.fluid.FluidTankInfo
import mods.eln.fluid.ISidedFluidHandler
import mods.eln.misc.McRegistries
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/** Standard fill-only tank exposed by TransparentNodeEntityWithFluid on every face. */
class WasherWater: ISidedFluidHandler {
    companion object { const val PER_CYCLE=1000;const val CAPACITY=10_000 }
    val tank=FluidTank(CAPACITY) { it.fluid===Fluids.WATER }
    override fun canFill(from: Direction?,fluid: Fluid?)=fluid===Fluids.WATER
    override fun canDrain(from: Direction?,fluid: Fluid?)=false
    override fun fill(from: Direction?,resource: FluidStack?,doFill: Boolean)=
        if(resource==null)0 else tank.fill(resource,if(doFill)FluidAction.EXECUTE else FluidAction.SIMULATE)
    override fun drain(from: Direction?,resource: FluidStack?,doDrain: Boolean)=FluidStack.EMPTY
    override fun drain(from: Direction?,maxDrain: Int,doDrain: Boolean)=FluidStack.EMPTY
    override fun getTankInfo(from: Direction?)=arrayOf(FluidTankInfo(tank.fluid.copy(),CAPACITY))
    fun reserve(): Boolean {
        if(tank.fluidAmount<PER_CYCLE)return false
        tank.drain(PER_CYCLE,FluidAction.EXECUTE);return true
    }
    fun save(tag: CompoundTag) { tag.put("washerWater",tank.writeToNBT(McRegistries.access(),CompoundTag())) }
    fun load(tag: CompoundTag) {
        tank.readFromNBT(McRegistries.access(),tag.getCompound("washerWater"))
        if(!tank.isEmpty && tank.fluid.fluid!==Fluids.WATER)tank.fluid=FluidStack.EMPTY
        else if(tank.fluidAmount>CAPACITY)tank.fluid=tank.fluid.copyWithAmount(CAPACITY)
    }
}

/** Energy credit must come from committed electrical steps, never speculative network iterations. */
class MachineWork {
    var joules=0.0;private set
    fun account(seconds: Double,watts: Double,required: Double) {
        if(seconds.isFinite() && seconds>0 && watts.isFinite() && watts>0 && required.isFinite() && required>0)
            joules=(joules+seconds*watts).coerceAtMost(required)
    }
    fun load(value: Double,required: Double) { joules=if(value.isFinite())value.coerceIn(0.0,required.coerceAtLeast(0.0)) else 0.0 }
    fun reset() { joules=0.0 }
}
