package mods.eln.sixnode.lampsocket

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.item.lampitem.LampDescriptor
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/** A batch of identical bulbs shares its lifetime tag; never merge differently aged bulbs. */
object FixtureBulbs {
    fun insert(inventory: Container, held: ItemStack, capacity: Int, free: Boolean): Boolean {
        if (held.isEmpty || GenericItemUsingDamageDescriptor.getDescriptor(held) !is LampDescriptor) return false
        val old = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)
        if (!old.isEmpty && !ItemStack.isSameItemSameComponents(old,held)) return false
        val count = minOf(held.count,capacity - old.count)
        if (count <= 0) return false
        inventory.setItem(LampSocketContainer.LAMP_SLOT_ID,held.copyWithCount(old.count+count))
        if (!free) held.shrink(count)
        return true
    }
}
