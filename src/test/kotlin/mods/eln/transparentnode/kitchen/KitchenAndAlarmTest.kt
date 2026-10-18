package mods.eln.transparentnode.kitchen

import mods.eln.transparentnode.ups.UpsAlarm
import net.minecraft.core.component.DataComponents
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import org.junit.Test
import kotlin.test.*

class KitchenAndAlarmTest {
    @Test fun energyProgressStopsDuringAnOutageAndRejectsInvalidWork() {
        val work=KitchenWork()
        assertFalse(work.advance(2.0,1000.0,6000.0));assertEquals(2000.0,work.joules)
        repeat(100) { assertFalse(work.advance(.05,0.0,6000.0)) }
        assertEquals(2000.0,work.joules)
        assertFalse(work.advance(Double.NaN,1000.0,6000.0))
        assertTrue(work.advance(4.0,1000.0,6000.0));assertEquals(6000.0,work.joules)
    }
    @Test fun fullOutputNeverConsumesFoodAndSameItemOutputStacks() {
        val inv=SimpleContainer(3);inv.setItem(0,ItemStack(Items.BREAD,2));inv.setItem(2,ItemStack(Items.STONE,64))
        val job=KitchenRecipe(ItemStack(KitchenFoods.toast.get()))
        assertFalse(job.canFit(inv));org.junit.Assert.assertThrows(IllegalStateException::class.java) { job.complete(inv) }
        assertEquals(2,inv.getItem(0).count)
        inv.setItem(2,ItemStack.EMPTY);job.complete(inv)
        assertEquals(1,inv.getItem(0).count);assertEquals(1,inv.getItem(2).count)
        job.complete(inv);assertTrue(inv.getItem(0).isEmpty);assertEquals(2,inv.getItem(2).count)
        assertNotNull(inv.getItem(2).get(DataComponents.FOOD))
    }
    @Test fun iceReturnsExactlyOneBucketAndCoffeeUsesOneWaterBottle() {
        val inv=SimpleContainer(3);inv.setItem(0,ItemStack(Items.WATER_BUCKET))
        val ice=KitchenRecipe(ItemStack(Items.ICE),container=ItemStack(Items.BUCKET));ice.complete(inv)
        assertTrue(inv.getItem(0).isEmpty);assertTrue(inv.getItem(1).`is`(Items.BUCKET));assertTrue(inv.getItem(2).`is`(Items.ICE))
        inv.clearContent();inv.setItem(0,ItemStack(Items.COCOA_BEANS,2))
        val coffee=KitchenRecipe(ItemStack(KitchenFoods.coffee.get()),water=true)
        assertFalse(coffee.canFit(inv))
        inv.setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER))
        assertTrue(coffee.canFit(inv));coffee.complete(inv)
        assertEquals(1,inv.getItem(0).count);assertTrue(inv.getItem(1).isEmpty)
        assertTrue(inv.getItem(2).`is`(KitchenFoods.coffee.get()))
        assertFalse(KitchenRecipe.waterBottle(PotionContents.createItemStack(Items.POTION,Potions.AWKWARD)))
    }
    @Test fun kitchenSlotsRejectOreAndAutomationCannotInsertIntoTheOutput() {
        for(type in KitchenType.entries)assertFalse(KitchenRecipe.allowed(type,2,ItemStack(Items.BREAD)))
        assertFalse(KitchenRecipe.allowed(KitchenType.MICROWAVE,0,ItemStack(Items.IRON_ORE)))
        assertTrue(KitchenRecipe.allowed(KitchenType.FRIDGE,3,ItemStack(Items.APPLE)))
        assertFalse(KitchenRecipe.allowed(KitchenType.FRIDGE,3,ItemStack(Items.STONE)))
    }
    @Test fun applianceCasesKeepOutwardWindingAfterConvertingToWorldAxes() {
        for(type in KitchenType.entries) {
            val file=java.io.File(System.getProperty("eln.projectDir"),"src/main/resources/assets/eln/model/${type.model}/${type.model}.obj")
            val points=mutableListOf<net.minecraft.world.phys.Vec3>();var part="";var volume=0.0
            file.forEachLine { line ->
                when {
                    line.startsWith("o ") -> part=line.substring(2)
                    line.startsWith("v ") -> line.split(' ').drop(1).map(String::toDouble).let { points+=net.minecraft.world.phys.Vec3(it[0],it[1],it[2]) }
                    line.startsWith("f ") && part=="Case" -> {
                        val face=line.split(' ').drop(1).map { points[it.substringBefore('/').toInt()-1] }
                        for(i in 1 until face.size-1)volume+=face[0].dot(face[i].cross(face[i+1]))/6.0
                    }
                }
            }
            assertTrue(volume>0.0,"${type.title}: exterior faces are inverted")
        }
    }

    @Test fun upsBeepsImmediatelyOnLossAndFasterOnOverloadButStopsOnRecovery() {
        val alarm=UpsAlarm()
        assertFalse(alarm.step(.05,"ONLINE",true))
        assertTrue(alarm.step(.05,"BACKUP",true))
        assertFalse(alarm.step(4.9,"BACKUP",true))
        assertTrue(alarm.step(.11,"BACKUP",true))
        assertTrue(alarm.step(.05,"LIMITED",true))
        assertFalse(alarm.step(.9,"LIMITED",true))
        assertTrue(alarm.step(.11,"PROTECTED",true))
        assertFalse(alarm.step(.05,"ONLINE",true))
        assertTrue(alarm.step(.05,"BACKUP",true))
        assertFalse(alarm.step(10.0,"BACKUP",false))
    }
}
