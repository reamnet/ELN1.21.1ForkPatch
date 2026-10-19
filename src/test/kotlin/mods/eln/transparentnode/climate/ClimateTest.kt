package mods.eln.transparentnode.climate

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.kitchen.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import org.junit.Test
import kotlin.test.*

class ClimateTest {
    @Test fun thermostatHasHysteresisForHeatingAndCoolingAndAnOffState() {
        for(cool in listOf(false,true)) {
            val c=ClimateControl(cool);c.setTarget(24.0)
            assertTrue(c.update(if(cool)25.0 else 23.0));assertTrue(c.update(24.0))
            assertFalse(c.update(if(cool)23.0 else 25.0));assertFalse(c.update(24.0))
            assertTrue(c.update(if(cool)25.0 else 23.0));c.enabled=false;assertFalse(c.update(24.0))
            c.enabled=true;assertFalse(c.update(Double.NaN))
        }
    }
    @Test fun realHeatAndExhaustConserveElectricalPower() {
        for(cool in listOf(false,true))for(watts in listOf(0.0,1.0,900.0,1500.0)) {
            val h=ClimateControl(cool).heat(watts)
            assertEquals(watts,h.first+h.second,1e-10)
            if(cool) { assertEquals(-3*watts,h.first);assertEquals(4*watts,h.second) }
            else assertEquals(watts,h.first)
        }
        for(bad in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0))assertEquals(0.0,ClimateControl(true).heat(bad).first,1e-12)
    }
    @Test fun settingsSurviveReloadAndRejectUnsafeTargets() {
        val c=ClimateControl(true);assertTrue(c.setTarget(19.0));c.enabled=false
        for(v in listOf(0.0,15.0,33.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(c.setTarget(v))
        val copy=ClimateControl(true).also { it.read(CompoundTag().also(c::write)) }
        assertEquals(19.0,copy.target);assertFalse(copy.enabled)
    }
    @Test fun newApplianceIdsCannotOverwriteBackupAndSolarMachines() {
        KitchenType.entries.forEachIndexed { i,t ->
            val id=listOf(1,2,3,4,5,18,19,20,27)[i]
            assertEquals(t,(Eln.transparentNodeItem.subItemList[id+(124 shl 6)] as KitchenDescriptor).type)
        }
        for(t in ClimateType.entries) {
            val d=Eln.transparentNodeItem.subItemList[21+t.ordinal+(124 shl 6)] as ClimateDescriptor
            assertEquals(t,d.type);assertEquals(t.cooling,d.wallMounted)
            val e=ClimateElement(TransparentNode(),d);e.front=Direction.ZP
            var ports=0
            for(side in Direction.entries)for(lrdu in LRDU.entries)if(e.getElectricalLoad(side,lrdu)!=null)ports++
            assertEquals(4,ports)
            assertTrue(e.inventory.isEmpty)
        }
        assertTrue(Eln.transparentNodeItem.subItemList[6+(124 shl 6)] is mods.eln.transparentnode.standby.BackupDescriptor)
        assertTrue(Eln.transparentNodeItem.subItemList[8+(124 shl 6)] is mods.eln.transparentnode.renewable.RenewableDescriptor)
    }
    @Test fun grainCookingReturnsTheBottleWithoutBlockingOrDuplicatingIt() {
        val inv=SimpleContainer(3);inv.setItem(0,ItemStack(Items.WHEAT,2))
        inv.setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER))
        val job=KitchenRecipe(ItemStack(KitchenFoods.grains.get()),true,ItemStack(Items.GLASS_BOTTLE))
        assertTrue(job.canFit(inv));job.complete(inv)
        assertEquals(1,inv.getItem(0).count);assertTrue(inv.getItem(1).`is`(Items.GLASS_BOTTLE))
        assertEquals(1,inv.getItem(1).count);assertEquals(1,inv.getItem(2).count)
        assertFalse(job.canFit(inv))
    }
    @Test fun newRecipesAcceptOnlyTheirInputsAndMakeEdibleOrDrinkableOutputs() {
        val water=PotionContents.createItemStack(Items.POTION,Potions.WATER)
        assertTrue(KitchenRecipe.allowed(KitchenType.KETTLE,0,water))
        assertFalse(KitchenRecipe.allowed(KitchenType.KETTLE,0,ItemStack(Items.APPLE)))
        for(item in listOf(Items.APPLE,Items.MELON_SLICE,Items.SWEET_BERRIES))assertTrue(KitchenRecipe.allowed(KitchenType.BLENDER,0,ItemStack(item)))
        assertTrue(KitchenRecipe.allowed(KitchenType.RICE,0,ItemStack(Items.WHEAT)))
        for(t in listOf(KitchenType.BLENDER,KitchenType.RICE))assertTrue(KitchenRecipe.allowed(t,1,water))
        for(item in listOf(KitchenFoods.hotWater.get(),KitchenFoods.smoothie.get(),KitchenFoods.grains.get()))assertNotNull(ItemStack(item).get(net.minecraft.core.component.DataComponents.FOOD))
    }
}
