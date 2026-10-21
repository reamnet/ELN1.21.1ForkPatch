package mods.eln.transparentnode.kitchen

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.DecorativeLampStyle
import mods.eln.transparentnode.climate.*
import mods.eln.transparentnode.cord.CordRating
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.Test
import kotlin.test.*

class MiniFridgeDeckTest {
    @Test fun miniFridgeIsCompactPortableAndKeepsItsOwnStorageWithoutReplacingExistingIds() {
        val d=Eln.transparentNodeItem.subItemList[(124 shl 6)+60] as KitchenDescriptor
        assertEquals(KitchenType.MINI_FRIDGE,d.type);assertNull(d.ghostGroup)
        val e=KitchenElement(TransparentNode(),d)
        assertEquals(12,e.inventory.containerSize);assertTrue(e.cordPortable)
        assertTrue(e.acceptsCord(CordRating.V120));assertFalse(e.acceptsCord(CordRating.V240))
        assertTrue(KitchenRecipe.allowed(d.type,0,ItemStack(Items.WATER_BUCKET)))
        assertTrue(KitchenRecipe.allowed(d.type,3,ItemStack(Items.APPLE)))
        assertFalse(KitchenRecipe.allowed(d.type,3,ItemStack(Items.COBBLESTONE)))
        assertEquals(30,KitchenType.FRIDGE.slotCount);assertEquals(4,KitchenType.FRIDGE.subId)
        assertEquals("appliance_fridge",d.type.sound)
    }
    @Test fun deckEquipmentHasDistinctSurvivalItemsAndAppropriatePowerAndMounts() {
        assertEquals(KitchenType.GRILL,(Eln.transparentNodeItem.subItemList[(124 shl 6)+61] as KitchenDescriptor).type)
        assertEquals(ClimateType.PATIO,(Eln.transparentNodeItem.subItemList[(124 shl 6)+62] as ClimateDescriptor).type)
        assertEquals(1800.0,KitchenType.GRILL.watts);assertEquals(1500.0,ClimateType.PATIO.watts)
        val lights=listOf(DecorativeLampStyle.DECK_POST_CAP,DecorativeLampStyle.DECK_STEP,DecorativeLampStyle.FAN_PATIO)
        assertEquals(3,lights.map(DecorativeLightRegistration::idFor).distinct().size)
        lights.forEach { assertFalse(Eln.findItemStack(it.itemName,1).isEmpty) }
        assertTrue(lights[0].tableLamp);assertTrue(lights[1].wallLamp);assertTrue(lights[2].ceiling && lights[2].fan)
        assertEquals(38.0,lights[2].fanWatts)
    }
}
