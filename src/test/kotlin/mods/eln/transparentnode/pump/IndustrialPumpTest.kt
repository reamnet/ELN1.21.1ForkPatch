package mods.eln.transparentnode.pump

import mods.eln.misc.SidedFluidAdapter
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank
import org.junit.Test
import kotlin.test.*

class IndustrialPumpTest {
    @Test fun realResistiveMotorPumps40BucketsAt480VoltsAndTracksSupplyVariation() {
        for(volts in listOf(432.0,480.0,528.0)) {
            val root=RootSystem(.05,1);val pin=VoltageState();root.addState(pin)
            val resistor=Resistor(pin,null).apply { resistance=PumpMotor.RESISTANCE }
            root.addComponent(resistor);root.addComponent(VoltageSource("grid",pin,null).apply { voltage=volts })
            val motor=PumpMotor();val ports=PumpFluidPorts()
            val source=FluidTank(1_000_000).apply { fill(FluidStack(Fluids.WATER,1_000_000),FluidAction.EXECUTE) }
            val pipe=SidedFluidAdapter(ports,Direction.UP)
            var delivered=0
            repeat(100) {
                root.step();motor.account(.05,pin.voltage,resistor.power)
                val moved=ports.pump(source,motor.available);motor.consume(moved)
                delivered+=pipe.drain(Int.MAX_VALUE,FluidAction.EXECUTE).amount
            }
            assertEquals(40_000*volts/480*5,delivered.toDouble(),1.0,"Supply $volts V")
            assertEquals(1_000_000-delivered,source.fluidAmount)
            assertEquals(volts*volts/PumpMotor.RESISTANCE,resistor.power,1e-5)
        }
    }
    @Test fun inputPipesCannotBypassTheMotorAndSimulationNeverMutatesTheBuffers() {
        val ports=PumpFluidPorts();val inlet=SidedFluidAdapter(ports,Direction.DOWN);val outlet=SidedFluidAdapter(ports,Direction.UP)
        val water=FluidStack(Fluids.WATER,10_000)
        assertEquals(10_000,inlet.fill(water,FluidAction.SIMULATE));assertTrue(ports.input.isEmpty)
        assertEquals(10_000,inlet.fill(water,FluidAction.EXECUTE))
        assertTrue(outlet.drain(2000,FluidAction.EXECUTE).isEmpty)
        assertTrue(inlet.drain(1000,FluidAction.EXECUTE).isEmpty)
        assertEquals(0,outlet.fill(water,FluidAction.EXECUTE))
        assertEquals(2000,ports.pump(null,2000))
        assertEquals(2000,outlet.drain(2000,FluidAction.SIMULATE).amount)
        assertEquals(2000,outlet.drain(2000,FluidAction.SIMULATE).amount)
        assertTrue(outlet.drain(FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE).isEmpty)
        assertEquals(2000,outlet.drain(2000,FluidAction.EXECUTE).amount)
        assertEquals(8000,ports.input.fluidAmount)
    }
    @Test fun otherLiquidsMoveAtTheSameRateWithoutMixingOrVoiding() {
        val ports=PumpFluidPorts();val source=FluidTank(80_000).apply { fill(FluidStack(Fluids.LAVA,80_000),FluidAction.EXECUTE) }
        assertEquals(2000,ports.pump(source,2000));assertSame(Fluids.LAVA,ports.output.fluid.fluid)
        ports.input.fill(FluidStack(Fluids.WATER,3000),FluidAction.EXECUTE)
        assertEquals(0,ports.pump(source,2000));assertEquals(3000,ports.input.fluidAmount);assertEquals(78_000,source.fluidAmount)
        ports.output.drain(2000,FluidAction.EXECUTE)
        assertEquals(2000,ports.pump(source,2000));assertSame(Fluids.WATER,ports.output.fluid.fluid)
        assertEquals(78_000,source.fluidAmount)
    }
    @Test fun fullOutputStopsDrawingLiquidAndSavedBuffersKeepTheirContents() {
        val ports=PumpFluidPorts();ports.intake=Direction.WEST
        ports.input.fill(FluidStack(Fluids.WATER,9000),FluidAction.EXECUTE)
        ports.output.fill(FluidStack(Fluids.WATER,40_000),FluidAction.EXECUTE)
        assertEquals(0,ports.pump(null,2000));assertEquals(9000,ports.input.fluidAmount)
        val tag=CompoundTag();ports.save(tag)
        val copy=PumpFluidPorts().apply { load(tag) }
        assertEquals(Direction.WEST,copy.intake);assertEquals(9000,copy.input.fluidAmount);assertEquals(40_000,copy.output.fluidAmount)
        assertEquals(0,copy.fill(Direction.EAST,FluidStack(Fluids.WATER,1000),true))
        assertEquals(1000,copy.drain(Direction.EAST,1000,true).amount)
        assertEquals(1000,copy.pump(null,1000));assertEquals(8000,copy.input.fluidAmount)
    }
    @Test fun disabledUnpoweredAndUnsafeVoltagesCannotAccumulateFreePumping() {
        val motor=PumpMotor()
        for(volts in listOf(0.0,10.0,721.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            motor.account(.05,volts,4000.0);assertEquals(0,motor.available)
        }
        motor.account(.05,480.0,0.0);assertEquals(0,motor.available)
        motor.account(.05,480.0,2000.0);assertEquals(1000,motor.available)
        motor.consume(1000);assertEquals(0,motor.available)
        motor.account(.05,480.0,4000.0);motor.clear();assertEquals(0,motor.available)
    }
    @Test fun consumersCannotMutateTanksThroughCapabilityInspection() {
        val ports=PumpFluidPorts();ports.output.fill(FluidStack(Fluids.WATER,5000),FluidAction.EXECUTE)
        val view=SidedFluidAdapter(ports,Direction.EAST)
        view.getFluidInTank(0).amount=1
        assertEquals(5000,ports.output.fluidAmount)
    }
}
