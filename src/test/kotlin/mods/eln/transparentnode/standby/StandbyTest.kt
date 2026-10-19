package mods.eln.transparentnode.standby

import mods.eln.Eln
import mods.eln.devtest.StandbyTestFluids
import mods.eln.misc.SidedFluidAdapter
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

class StandbyTest {
    private fun ats()=AtsElement(TransparentNode(),Eln.transparentNodeItem.subItemList[6+(124 shl 6)]!!)
    private fun generator()=StandbyGeneratorElement(TransparentNode(),Eln.transparentNodeItem.subItemList[7+(124 shl 6)]!!)
    private class Circuit(val nominal: Double=240.0,watts: Double=1000.0) {
        val a=StandbyTest().ats();val g=StandbyTest().generator();val root=RootSystem(.05,1)
        val grid=VoltageState()
        val source=SwitchableVoltageSource("mains").apply { connectTo(grid,null);voltage=nominal;enabled=true }
        val gridWire=Resistor(grid,a.unit.mains).apply { resistance=.01 }
        val generatorWire=Resistor(g.unit.output,a.unit.backup).apply { resistance=.01 }
        val load=Resistor(a.unit.output,null).apply { resistance=nominal*nominal/watts }
        val world=Any();var ticks=0L
        var produced=0.0;var peakWatts=0.0;var peakAmps=0.0
        init {
            a.unit.channel="Hospital";g.unit.channel="Hospital";a.unit.setVoltage(nominal);g.unit.setVoltage(nominal)
            assertEquals(20_000,g.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0),20_000),FluidAction.EXECUTE))
            for(e in listOf(a,g)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent) }
            root.addState(grid);listOf(source,gridWire,generatorWire,load).forEach(root::addComponent)
            g.unit.attach(root)
        }
        fun tick() {
            root.step();produced+=g.unit.source.power.coerceAtLeast(0.0)*.05
            peakWatts=maxOf(peakWatts,g.unit.source.power);peakAmps=maxOf(peakAmps,g.unit.source.current)
            g.unit.tick(.05);a.unit.tick(.05)
            check(!(a.unit.mainContact.closed && a.unit.backupContact.closed))
            StandbyLink.update(world,a,a.unit.channel,ticks,a.unit.control.request)
            g.unit.requested=StandbyLink.requested(world,g.unit.channel,ticks);ticks++
        }
        fun run(n: Int) { repeat(n) { tick() } }
    }
    @Test fun failedFeedStartsWirelesslyTransfersWithoutBackfeedAndReturnsToMains() {
        val c=Circuit();c.run(30)
        assertEquals(TransferFeed.MAINS,c.a.unit.control.selected);assertFalse(c.g.unit.running)
        assertTrue(c.load.power>990);val fuel=c.g.unit.fuel.availableMb
        c.source.enabled=false;c.run(180)
        assertEquals(TransferFeed.GENERATOR,c.a.unit.control.selected);assertTrue(c.g.unit.running)
        assertTrue(c.load.power>990);assertEquals(0.0,c.a.unit.mainContact.current)
        assertTrue(abs(c.a.unit.mains.voltage)<.001);assertTrue(c.g.unit.fuel.availableMb<fuel)
        c.source.enabled=true;c.run(60)
        assertEquals(TransferFeed.GENERATOR,c.a.unit.control.selected,"Must wait for five stable seconds")
        c.run(190)
        assertEquals(TransferFeed.MAINS,c.a.unit.control.selected);assertFalse(c.g.unit.running)
        assertEquals(0.0,c.a.unit.backupContact.current);assertTrue(c.load.power>990)
    }
    @Test fun contactSoundsFollowPhysicalTransfersOnceAndUnloadingIsSilent() {
        val sounds=mutableListOf<Boolean>()
        val unit=AtsUnit(ats()) { sounds+=it }
        unit.mains.state=240.0;unit.backup.state=240.0
        repeat(30) { unit.tick(.05) };assertEquals(listOf(true),sounds)
        repeat(100) { unit.tick(.05) };assertEquals(listOf(true),sounds)
        unit.mains.state=0.0;repeat(50) { unit.tick(.05) }
        assertEquals(TransferFeed.GENERATOR,unit.control.selected)
        assertEquals(listOf(true,false,true),sounds)
        unit.mains.state=240.0;repeat(120) { unit.tick(.05) }
        assertEquals(TransferFeed.MAINS,unit.control.selected)
        assertEquals(listOf(true,false,true,false,true),sounds)
        unit.reset(false);assertEquals(5,sounds.size)
        repeat(30) { unit.tick(.05) };assertEquals(6,sounds.size)
        unit.setEnabled(false);assertEquals(false,sounds.last())
        repeat(30) { unit.tick(.05) };assertEquals(7,sounds.size)
    }
    @Test fun everyTransferIncludesAnOpenIntervalAndVoltageDipsDoNotChatter() {
        val control=TransferControl();repeat(8) { control.step(.05,240.0,240.0,240.0) }
        assertEquals(TransferFeed.MAINS,control.selected)
        control.step(.05,0.0,240.0,240.0);assertEquals(TransferFeed.OPEN,control.selected)
        repeat(4) { control.step(.05,240.0,240.0,240.0) }
        assertFalse(control.request)
        repeat(30) { control.step(.05,0.0,240.0,240.0) }
        assertTrue(control.request);assertEquals(TransferFeed.GENERATOR,control.selected)
        repeat(99) { control.step(.05,240.0,240.0,240.0) };assertEquals(TransferFeed.GENERATOR,control.selected)
        assertEquals(TransferFeed.OPEN,control.step(.1,240.0,240.0,240.0))
        repeat(4) { control.step(.05,240.0,240.0,240.0);assertEquals(TransferFeed.OPEN,control.selected) }
        control.step(.06,240.0,240.0,240.0);assertEquals(TransferFeed.MAINS,control.selected)
    }
    @Test fun wrongVoltageNeverReachesTheLoadAndDisabledAtsOpensBothContacts() {
        val c=Circuit();c.source.enabled=false;c.g.unit.setVoltage(850.0);c.run(180)
        assertTrue(c.a.unit.control.request);assertEquals(TransferFeed.OPEN,c.a.unit.control.selected)
        assertTrue(c.load.power<.001)
        c.a.unit.setEnabled(false);c.tick()
        assertFalse(c.a.unit.control.request);assertFalse(c.a.unit.mainContact.closed);assertFalse(c.a.unit.backupContact.closed)
    }
    @Test fun wirelessChannelsAreCaseSensitiveDimensionScopedAndExpireWhenTheirOwnerDisappears() {
        val world=Any();val other=Any();val a=Any();val b=Any()
        StandbyLink.update(world,a,"Workshop",10,true)
        assertTrue(StandbyLink.requested(world,"Workshop",11));assertFalse(StandbyLink.requested(world,"workshop",11))
        assertFalse(StandbyLink.requested(other,"Workshop",11));assertFalse(StandbyLink.requested(world,"Workshop",111))
        StandbyLink.update(world,a,"Workshop",120,true);StandbyLink.update(world,b,"Workshop",120,true)
        StandbyLink.remove(world,a);assertTrue(StandbyLink.requested(world,"Workshop",121))
        StandbyLink.update(world,b,"Workshop",122,false);assertFalse(StandbyLink.requested(world,"Workshop",122))
        StandbyLink.update(world,b,"",123,true);assertFalse(StandbyLink.requested(world,"",123))
        assertEquals(64,StandbyLink.channel(" x".repeat(100)).length)
    }
    @Test fun allProgrammedVoltagesPowerRealLoadsAndFuelMatchesAcceptedElectricalEnergy() {
        for(v in listOf(12.0,120.0,240.0,850.0,3200.0,14_000.0,51_200.0)) {
            val c=Circuit(v,if(v==12.0)100.0 else 1000.0);c.source.enabled=false;c.run(160)
            assertTrue(c.g.unit.running,"$v: ${c.g.unit.status}");assertTrue(c.a.unit.backupContact.closed)
            // Include the alternator, cable and closed-contact resistance at every voltage.
            val expected=v*c.load.resistance/(c.load.resistance+.005+.01+.002)
            assertEquals(expected,c.a.unit.output.voltage,1e-5)
            val before=c.g.unit.fuel.availableJoules;var used=0.0
            repeat(40) { c.root.step();used+=maxOf(StandbyGeneratorUnit.IDLE_WATTS,c.g.unit.source.power)*.05;c.g.unit.tick(.05) }
            assertEquals(used,before-c.g.unit.fuel.availableJoules,1e-5)
        }
    }
    @Test fun overloadStopsOnceAndStaysLatchedRatherThanBlinkingTheEntireGrid() {
        val c=Circuit(watts=20_000.0);c.source.enabled=false;c.run(220)
        assertEquals("OVERLOAD",c.g.unit.fault);assertFalse(c.g.unit.source.enabled)
        val restored=generator();restored.unit.read(CompoundTag().also(c.g.unit::write));assertEquals("OVERLOAD",restored.unit.fault)
        val fuel=c.g.unit.fuel.availableMb;c.run(300)
        assertEquals(fuel,c.g.unit.fuel.availableMb);assertTrue(c.load.power<.001)
        c.load.resistance=240.0*240/1000;c.g.unit.reset();c.run(160)
        assertTrue(c.load.power>990);assertEquals("",c.g.unit.fault)
    }
    @Test fun emptyFuelAndOffModeCannotCreatePowerButRefuellingRestartsAutomaticBackup() {
        val c=Circuit();c.g.unit.fuel.tank.drain(20_000,FluidAction.EXECUTE);c.source.enabled=false;c.run(150)
        assertFalse(c.g.unit.source.enabled);assertTrue(c.load.power<.001)
        c.g.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(1),1000),FluidAction.EXECUTE);c.run(160)
        assertTrue(c.load.power>990)
        c.g.unit.mode=StandbyMode.OFF;c.run(40);assertFalse(c.g.unit.source.enabled);assertTrue(c.load.power<.001)
    }
    @Test fun manualModeRunsWithoutAnyWirelessRequest() {
        val c=Circuit()
        val controls=RootSystem::class.java.getDeclaredField("processPre").apply { isAccessible=true }.get(c.root) as List<*>
        assertFalse(controls.contains(c.g.unit.controller),"Stopped engines must not add solver-control sweeps")
        c.g.unit.mode=StandbyMode.MANUAL;c.run(90)
        assertTrue(c.g.unit.running);assertFalse(c.g.unit.requested);assertEquals(0.0,c.a.unit.backupContact.current)
        assertTrue(controls.contains(c.g.unit.controller))
        c.g.unit.mode=StandbyMode.OFF;c.run(2);assertFalse(controls.contains(c.g.unit.controller))
    }
    @Test fun anIncorrectParallelGridTripsReversePowerAndDisabledEngineNeverClampsThatGrid() {
        val c=Circuit();c.g.unit.mode=StandbyMode.MANUAL;c.source.voltage=250.0
        c.root.addComponent(Resistor(c.grid,c.g.unit.output).apply { resistance=.05 })
        c.run(150)
        assertEquals("BACKFEED",c.g.unit.fault);assertFalse(c.g.unit.source.enabled)
        assertEquals(250.0,c.g.unit.output.voltage,.001);assertEquals(0.0,c.g.unit.source.power)
    }
    @Test fun aHardShortCannotExceedElectricalOrFuelLimitsAndNeverAutoRestartsIntoTheFault() {
        val c=Circuit();c.source.enabled=false;c.load.resistance=.0001
        c.g.unit.fuel.tank.drain(20_000,FluidAction.EXECUTE)
        c.g.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0),2),FluidAction.EXECUTE)
        val before=c.g.unit.fuel.availableJoules;c.run(300)
        assertEquals("OVERLOAD",c.g.unit.fault)
        assertTrue(c.peakWatts<=10_000.01);assertTrue(c.peakAmps<=200.001)
        assertTrue(before-c.g.unit.fuel.availableJoules+1e-5>=c.produced)
        val energy=c.g.unit.fuel.availableJoules;val produced=c.produced;c.run(300)
        assertEquals(energy,c.g.unit.fuel.availableJoules);assertEquals(produced,c.produced)
    }
    @Test fun excessiveCableVoltageDropHoldsTheBackupContactOpenInsteadOfBlinking() {
        val c=Circuit();c.source.enabled=false;c.generatorWire.resistance=20.0;c.run(320)
        assertTrue(c.a.unit.control.backupFault);assertFalse(c.a.unit.backupContact.closed);assertTrue(c.g.unit.running)
        repeat(300) { c.tick();assertFalse(c.a.unit.backupContact.closed) }
        c.generatorWire.resistance=.01;c.a.unit.reset();c.run(60)
        assertFalse(c.a.unit.control.backupFault);assertTrue(c.load.power>990)
    }
    @Test fun standbySupplyKeepsMixedVoltagePoleConvertersStableOnALong14000VoltFeeder() {
        val descriptor=Eln.transparentNodeItem.subItemList.values
            .filterIsInstance<mods.eln.transparentnode.utilitylight.StepDownUnitDescriptor>().single()
        for(count in listOf(12,120)) {
            val c=Circuit(14_000.0);c.source.enabled=false;c.run(140)
            val poles=List(count) { i ->
                mods.eln.transparentnode.utilitylight.StepDownUnitElement(TransparentNode(),descriptor).also { e ->
                    e.unit.read(CompoundTag().apply { putInt("mainsOutputVoltage",if(i%2==0)120 else 240) })
                    e.electricalLoadList.forEach(c.root::addState);e.electricalComponentList.forEach(c.root::addComponent)
                    c.root.addProcess(e.unit.controller)
                    c.root.addComponent(Resistor(e.unit.secondary,null).apply { resistance=e.unit.outputVoltage.toDouble().let { it*it/40.0 } })
                }
            }
            c.root.addComponent(Resistor(c.a.unit.output,poles[0].unit.primary).apply { resistance=4.0 })
            for(i in 1 until count)c.root.addComponent(Resistor(poles[i-1].unit.primary,poles[i].unit.primary).apply { resistance=.2 })
            repeat(25) {
                c.tick()
                assertTrue(c.g.unit.running,"$count poles: ${c.g.unit.status}")
                poles.forEach { e ->
                    assertEquals(e.unit.outputVoltage.toDouble(),e.unit.secondary.voltage,.1,"$count poles: ${e.unit.controller.status}")
                    assertTrue(e.unit.output.power>39.5)
                }
                assertTrue(c.g.unit.source.power<=10_000.01)
            }
        }
    }
    @Test fun matchingRegistryPathsAcceptTwoNamespacesAndAnExplicitTagAcceptsUnusualNames() {
        assertTrue(StandbyFuel.accepts(StandbyTestFluids.fluid(0)));assertTrue(StandbyFuel.accepts(StandbyTestFluids.fluid(1)))
        assertFalse(StandbyFuel.accepts(Fluids.WATER));assertFalse(StandbyFuel.accepts(Fluids.LAVA))
        StandbyTestFluids.bindTestTag();assertTrue(StandbyFuel.accepts(StandbyTestFluids.fluid(2)))
        assertFalse(StandbyFuel.namedGasoline("other:diesel"));assertTrue(StandbyFuel.namedGasoline("other:fuels/petrol"))
    }
    @Test fun fluidCapabilitySimulatesFillsRejectsMixingAndPreservesFractionalFuelAcrossSaveLoad() {
        val tank=StandbyTank {};val adapter=SidedFluidAdapter(tank,null)
        val a=FluidStack(StandbyTestFluids.fluid(0),1000);val b=FluidStack(StandbyTestFluids.fluid(1),1000)
        assertEquals(1000,adapter.fill(a,FluidAction.SIMULATE));assertEquals(0,tank.tank.fluidAmount)
        assertEquals(1000,adapter.fill(a,FluidAction.EXECUTE));assertEquals(0,adapter.fill(b,FluidAction.EXECUTE))
        assertFalse(adapter.isFluidValid(0,FluidStack(Fluids.WATER,1000)))
        assertTrue(tank.consume(123.45));val saved=CompoundTag().also(tank::write);val restored=StandbyTank {};restored.read(saved)
        assertEquals(tank.availableMb,restored.availableMb,1e-10);assertTrue(restored.reserveJoules>0)
        val drained=adapter.drain(50,FluidAction.SIMULATE);assertEquals(50,drained.amount);assertEquals(999,tank.tank.fluidAmount)
        assertEquals(50,adapter.drain(50,FluidAction.EXECUTE).amount);assertEquals(949,tank.tank.fluidAmount)
        val reserve=tank.reserveJoules;adapter.drain(20_000,FluidAction.EXECUTE)
        assertEquals(reserve,tank.availableJoules,1e-10)
        assertTrue(tank.consume(reserve/2));assertEquals(reserve/2,tank.availableJoules,1e-10)
    }
    @Test fun moddedFluidComponentsSurviveSavingAndCannotBeMixedWithDifferentComponents() {
        val a=FluidStack(StandbyTestFluids.fluid(0),1000)
        a.set(DataComponents.CUSTOM_DATA,CustomData.of(CompoundTag().apply { putString("blend","premium") }))
        val tank=StandbyTank {};assertEquals(1000,tank.tank.fill(a,FluidAction.EXECUTE))
        assertEquals(0,tank.tank.fill(FluidStack(a.fluid,1000),FluidAction.EXECUTE))
        val restored=StandbyTank {};restored.read(CompoundTag().also(tank::write))
        assertTrue(FluidStack.isSameFluidSameComponents(a,restored.tank.fluid))
    }
    @Test fun settingsAndFuelReloadButContactsAndEngineAlwaysRevalidateBeforeReconnecting() {
        val c=Circuit();c.source.enabled=false;c.run(160)
        val at=CompoundTag().also(c.a.unit::write);val gt=CompoundTag().also(c.g.unit::write)
        val a=ats();val g=generator();a.unit.read(at);g.unit.read(gt)
        assertEquals("Hospital",g.unit.channel);assertEquals(c.g.unit.fuel.availableMb,g.unit.fuel.availableMb)
        assertEquals(240.0,g.unit.nominal);assertFalse(g.unit.running);assertFalse(g.unit.source.enabled)
        assertEquals(TransferFeed.OPEN,a.unit.control.selected);assertFalse(a.unit.backupContact.closed)
        assertFalse(g.unit.setVoltage(Double.NaN));assertFalse(a.unit.setVoltage(0.0))
        val original=g.unit.nominal;assertFalse(g.unit.setVoltage(Double.POSITIVE_INFINITY));assertEquals(original,g.unit.nominal)
    }
}
