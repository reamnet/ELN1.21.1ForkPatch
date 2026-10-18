package mods.eln.transparentnode.standby

import mods.eln.fluid.FluidRegistry
import mods.eln.fluid.FuelRegistry
import mods.eln.fluid.FluidTankInfo
import mods.eln.fluid.ISidedFluidHandler
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank
import kotlin.math.ceil

object StandbyFuel {
    val tags=listOf("eln:standby_gasoline","c:gasoline","c:fuels/gasoline","forge:gasoline","forge:fuels/gasoline")
        .map { TagKey.create(Registries.FLUID,ResourceLocation.parse(it)) }
    fun namedGasoline(id: String)=id.substringAfter(':').substringAfterLast('/') in
        setOf("gasoline","flowing_gasoline","petrol","flowing_petrol")
    fun accepts(fluid: Fluid): Boolean {
        val id=FluidRegistry.getFluidName(fluid) ?: return false
        return namedGasoline(id) || tags.any { fluid.builtInRegistryHolder().`is`(it) } ||
            FuelRegistry.fluidListToFluids(FuelRegistry.gasolineList).any { it===fluid }
    }
    fun joulesPerMb(fluid: Fluid): Double =
        FuelRegistry.heatEnergyPerMilliBucket(fluid).takeIf { it.isFinite() && it>0 } ?: 2173.5
}

/** Standard fluid capability supports pipes and arbitrary modded fluid containers. */
class StandbyTank(private val changed: ()->Unit) : ISidedFluidHandler {
    companion object { const val CAPACITY=20_000;const val EFFICIENCY=.25 }
    // Pay for a whole mB before using it; its fractional remainder stays in the engine.
    // Pipes can never extract fuel that has already produced electricity.
    var reserveJoules=0.0
        private set
    private var reserveCost=2173.5*EFFICIENCY
    val tank=object : FluidTank(CAPACITY,{ StandbyFuel.accepts(it.fluid) }) {
        override fun onContentsChanged() { changed() }
    }
    private val lookup get()=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
    val availableMb get()=tank.fluidAmount+reserveJoules/reserveCost
    val availableJoules get()=reserveJoules+if(tank.isEmpty)0.0 else tank.fluidAmount*StandbyFuel.joulesPerMb(tank.fluid.fluid)*EFFICIENCY
    fun consume(joules: Double): Boolean {
        if(!joules.isFinite() || joules<0)return false
        if(joules==0.0)return true
        if(joules>availableJoules)return false
        if(joules>reserveJoules) {
            reserveCost=StandbyFuel.joulesPerMb(tank.fluid.fluid)*EFFICIENCY
            val whole=ceil((joules-reserveJoules)/reserveCost).toInt().coerceAtMost(tank.fluidAmount)
            reserveJoules+=tank.drain(whole,FluidAction.EXECUTE).amount*reserveCost
        }
        reserveJoules=(reserveJoules-joules).coerceAtLeast(0.0)
        return true
    }
    fun read(nbt: CompoundTag) {
        tank.readFromNBT(lookup,nbt.getCompound("standbyTank"))
        if(!tank.isEmpty && !StandbyFuel.accepts(tank.fluid.fluid))tank.fluid=FluidStack.EMPTY
        if(tank.fluidAmount>CAPACITY)tank.fluid.amount=CAPACITY
        reserveCost=nbt.getDouble("standbyReserveCost").takeIf { it.isFinite() && it>0 } ?: 2173.5*EFFICIENCY
        reserveJoules=nbt.getDouble("standbyReserveJoules").takeIf { it.isFinite() && it>=0 && it<=reserveCost } ?: 0.0
    }
    fun write(nbt: CompoundTag) {
        nbt.put("standbyTank",tank.writeToNBT(lookup,CompoundTag()));nbt.putDouble("standbyReserveCost",reserveCost);nbt.putDouble("standbyReserveJoules",reserveJoules)
    }
    override fun fill(from: Direction?,resource: FluidStack?,doFill: Boolean)=if(resource==null)0 else tank.fill(resource,if(doFill)FluidAction.EXECUTE else FluidAction.SIMULATE)
    override fun drain(from: Direction?,resource: FluidStack?,doDrain: Boolean)=if(resource==null)null else tank.drain(resource,if(doDrain)FluidAction.EXECUTE else FluidAction.SIMULATE)
    override fun drain(from: Direction?,maxDrain: Int,doDrain: Boolean)=tank.drain(maxDrain,if(doDrain)FluidAction.EXECUTE else FluidAction.SIMULATE)
    override fun canFill(from: Direction?,fluid: Fluid?)=fluid!=null && StandbyFuel.accepts(fluid)
    override fun canDrain(from: Direction?,fluid: Fluid?)=!tank.isEmpty && tank.fluid.fluid===fluid
    override fun getTankInfo(from: Direction?)=arrayOf(FluidTankInfo(tank.fluid.copy(),CAPACITY))
}
