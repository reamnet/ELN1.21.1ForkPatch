package mods.eln.transparentnode.pump

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper

/** Adjacent sources only; bounded constant work, no chunk loading or remote lake scans. */
object PumpWorldIntake {
    fun source(world: ServerLevel,pos: BlockPos): IFluidHandler? {
        if(!world.hasChunkAt(pos))return null
        val block=world.getBlockState(pos)
        val fluid=block.fluidState
        if(!fluid.isSource || block.block !is BucketPickup)return null
        // Do not treat waterlogged decorative blocks, isolated water, or lava as an infinite tank.
        val renewable=block.block is LiquidBlock && fluid.type===Fluids.WATER &&
            world.gameRules.getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION) &&
            net.neoforged.neoforge.event.EventHooks.canCreateFluidSource(world,pos,block) &&
            Direction.Plane.HORIZONTAL.count { side ->
                val at=pos.relative(side)
                world.hasChunkAt(at) && world.getFluidState(at).let { it.isSource && it.type===Fluids.WATER }
            }>=2 && pos.below().let { below ->
                world.hasChunkAt(below) && (world.getBlockState(below).isFaceSturdy(world,below,Direction.UP) ||
                    world.getFluidState(below).let { it.isSource && it.type===Fluids.WATER })
            }
        if(!renewable)return BucketPickupHandlerWrapper(null,block.block as BucketPickup,world,pos)
        return object: IFluidHandler {
            override fun getTanks()=1
            override fun getFluidInTank(tank: Int)=if(tank==0)FluidStack(Fluids.WATER,40_000) else FluidStack.EMPTY
            override fun getTankCapacity(tank: Int)=if(tank==0)40_000 else 0
            override fun isFluidValid(tank: Int,stack: FluidStack)=false
            override fun fill(resource: FluidStack,action: IFluidHandler.FluidAction)=0
            override fun drain(resource: FluidStack,action: IFluidHandler.FluidAction)=
                if(FluidStack.isSameFluidSameComponents(resource,FluidStack(Fluids.WATER,1)))drain(resource.amount,action) else FluidStack.EMPTY
            override fun drain(maxDrain: Int,action: IFluidHandler.FluidAction)=
                if(maxDrain>0)FluidStack(Fluids.WATER,maxDrain.coerceAtMost(40_000)) else FluidStack.EMPTY
        }
    }
}
