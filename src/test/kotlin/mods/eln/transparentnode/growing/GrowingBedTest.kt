package mods.eln.transparentnode.growing

import mods.eln.Eln
import mods.eln.misc.SidedFluidAdapter
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.cord.CordRating
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.test.*

class GrowingBedTest {
    private fun bed()=GrowingBedElement(TransparentNode(),Eln.transparentNodeItem.subItemList[(125 shl 6)+13]!!)
    @Test fun physicalSizeAndNewIdDoNotReplaceTheDryerOutlet() {
        val descriptor=Eln.transparentNodeItem.subItemList[(125 shl 6)+13] as GrowingBedDescriptor
        val cells=descriptor.ghostGroup!!.elementList.map { Triple(it.x,it.y,it.z) }+Triple(0,0,0)
        assertEquals(20,cells.toSet().size)
        assertEquals(setOf(0),cells.map { it.first }.toSet())
        assertEquals((0..3).toSet(),cells.map { it.second }.toSet())
        assertEquals((-2..2).toSet(),cells.map { it.third }.toSet())
        assertTrue(Eln.transparentNodeItem.subItemList[(125 shl 6)+12]!!.name.contains("Dryer Outlet"))
        assertTrue(bed().acceptsCord(CordRating.V240));assertTrue(bed().acceptsCord(CordRating.V240_30))
        assertFalse(bed().acceptsCord(CordRating.V120))
    }
    @Test fun supportedSeedsBulbsAndHeatingElementsHaveSeparateServiceSlots() {
        for(item in listOf(Items.WHEAT_SEEDS,Items.BEETROOT_SEEDS,Items.CARROT,Items.POTATO))
            assertNotNull(GrowingCycle.crop(ItemStack(item)))
        assertNull(GrowingCycle.crop(ItemStack(Items.DIAMOND)))
        assertNull(GrowingCycle.crop(ItemStack(Items.OAK_SAPLING)))
        for(name in listOf("120V LED Light Bulb","240V Halogen Light Bulb"))
            assertTrue(GrowingBedElement.accepts(1,Eln.findItemStack(name,1)))
        assertTrue(GrowingBedElement.accepts(2,Eln.findItemStack("240V Copper Heating Element",1)))
        assertFalse(GrowingBedElement.accepts(3,ItemStack(Items.WHEAT)))
        val inv=bed().inventory
        for(side in 0..5) {
            assertContentEquals(intArrayOf(0,3,4,5,6,7,8,9,10,11),inv.getAccessibleSlotsFromSide(side))
            assertTrue(inv.canPlaceItemThroughFace(0,ItemStack(Items.WHEAT_SEEDS),side))
            assertFalse(inv.canTakeItemThroughFace(0,ItemStack(Items.WHEAT_SEEDS),side))
            assertFalse(inv.canTakeItemThroughFace(1,Eln.findItemStack("120V LED Light Bulb",1),side))
            assertTrue(inv.canTakeItemThroughFace(3,ItemStack(Items.WHEAT),side))
        }
    }
    @Test fun waterInputUsesStandardFluidCapabilitiesAndRejectsOtherFluidsAndExtraction() {
        val bed=bed()
        for(side in Direction.entries) {
            val pipe=SidedFluidAdapter(bed.water,side)
            assertEquals(1000,pipe.fill(FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE))
            assertEquals(1000,pipe.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE))
            assertEquals(0,pipe.fill(FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE))
            assertTrue(pipe.drain(1000,FluidAction.EXECUTE).isEmpty)
        }
        assertEquals(6000,bed.water.tank.fluidAmount)
        val saved=CompoundTag();bed.water.save(saved)
        val restored=bed();restored.water.load(saved)
        assertEquals(6000,restored.water.tank.fluidAmount)
    }
    @Test fun darknessPowerLossOrInvalidTimeCannotEarnGrowth() {
        val cycle=GrowingCycle()
        cycle.account(180.0,20.0,0.0,0.0,1.0,true)
        cycle.account(180.0,20.0,500.0,0.0,1.0,false)
        cycle.account(Double.NaN,20.0,500.0,0.0,1.0,true)
        assertEquals(0.0,cycle.progress)
        cycle.account(90.0,20.0,500.0,0.0,.5,true)
        assertEquals(45.0,cycle.progress,1e-8)
        cycle.account(90.0,20.0,500.0,0.0,1.0,true)
        assertEquals(135.0,cycle.progress,1e-8)
        val restored=GrowingCycle();restored.restore(cycle.progress,cycle.temperature)
        restored.account(45.0,20.0,500.0,0.0,1.0,true)
        assertEquals(GrowingCycle.SECONDS,restored.progress)
        restored.restore(Double.POSITIVE_INFINITY,Double.NaN)
        assertEquals(0.0,restored.progress);assertEquals(20.0,restored.temperature)
    }
    @Test fun heatHasInertiaAndOnlyPoweredHeatingCanWarmAColdRack() {
        val cold=GrowingCycle();val heated=GrowingCycle()
        cold.restore(0.0,10.0);heated.restore(0.0,10.0)
        repeat(400) {
            cold.account(.5,10.0,100.0,0.0,1.0,true)
            heated.account(.5,10.0,100.0,1500.0,1.0,true)
        }
        assertTrue(cold.temperature<11);assertTrue(heated.temperature in 22.0..24.0)
        assertTrue(heated.progress>cold.progress)
        val before=heated.temperature
        heated.account(.05,10.0,0.0,0.0,1.0,false)
        assertTrue(heated.temperature<before && heated.temperature>before-.1)
        assertEquals(0.0,GrowingCycle.temperatureFactor(40.0))
        assertEquals(0.0,GrowingCycle.temperatureFactor(5.0))
    }
    @Test fun aBlockedHarvestDoesNotPartiallyInsertOrMutateItsLoot() {
        val full=List(9) { ItemStack(Items.WHEAT,64) }
        val drops=listOf(ItemStack(Items.WHEAT,2),ItemStack(Items.WHEAT_SEEDS,3))
        assertNull(mergeHarvest(full,drops))
        assertTrue(full.all { it.count==64 });assertEquals(2,drops[0].count)
        val almost=List(9) { if(it<7)ItemStack(Items.WHEAT,64) else ItemStack.EMPTY }
        val result=mergeHarvest(almost,drops)!!
        assertEquals(2,result[7].count);assertEquals(3,result[8].count)
        assertTrue(almost[7].isEmpty);assertTrue(almost[8].isEmpty)
    }
    @Test fun aSavedPaidBatchRetainsItsCropWaterAndProgressWithoutOfflineWork() {
        val initial=bed()
        initial.inventory.setItem(0,ItemStack(Items.WHEAT_SEEDS,20))
        initial.inventory.setItem(1,Eln.findItemStack("120V LED Light Bulb",48))
        initial.water.fill(null,FluidStack(Fluids.WATER,3000),true)
        val saved=CompoundTag();initial.writeToNBT(saved)
        saved.putDouble("growProgress",90.0);saved.putDouble("growTemperature",22.0)
        saved.putBoolean("growWaterReserved",true)
        val resumed=bed();resumed.readFromNBT(saved)
        assertEquals(90.0,resumed.cycle.progress);assertEquals(22.0,resumed.cycle.temperature)
        assertEquals(20,resumed.planted);assertEquals(3000,resumed.water.tank.fluidAmount)
        val again=CompoundTag();resumed.writeToNBT(again)
        assertTrue(again.getBoolean("growWaterReserved"))
        assertEquals(90.0,again.getDouble("growProgress"))
    }
}
