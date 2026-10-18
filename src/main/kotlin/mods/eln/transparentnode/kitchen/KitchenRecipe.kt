package mods.eln.transparentnode.kitchen

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput

class KitchenRecipe(val output: ItemStack,val water: Boolean=false,val container: ItemStack=ItemStack.EMPTY) {
    fun canFit(inventory: Container): Boolean = mergeFits(inventory.getItem(2),output) &&
        (container.isEmpty || mergeFits(inventory.getItem(1),container)) && (!water || waterBottle(inventory.getItem(1)))
    fun complete(inventory: Container) {
        check(canFit(inventory))
        inventory.getItem(0).shrink(1)
        if(water)inventory.getItem(1).shrink(1)
        val previous=inventory.getItem(2)
        if(previous.isEmpty)inventory.setItem(2,output.copy()) else previous.grow(output.count)
        if(!container.isEmpty) {
            val old=inventory.getItem(1)
            if(old.isEmpty)inventory.setItem(1,container.copy()) else old.grow(container.count)
        }
        inventory.setChanged()
    }
    companion object {
        fun mergeFits(existing: ItemStack,added: ItemStack)=existing.isEmpty ||
            ItemStack.isSameItemSameComponents(existing,added) && existing.count+added.count<=existing.maxStackSize
        fun waterBottle(stack: ItemStack)=stack.`is`(Items.POTION) &&
            stack.get(DataComponents.POTION_CONTENTS)?.potion()?.orElse(null)==Potions.WATER
        fun find(world: ServerLevel,type: KitchenType,inventory: Container): KitchenRecipe? {
            val input=inventory.getItem(0)
            if(input.isEmpty)return null
            return when(type) {
                KitchenType.TOASTER -> if(input.`is`(Items.BREAD)) KitchenRecipe(ItemStack(KitchenFoods.toast.get())) else null
                KitchenType.COFFEE -> if(input.`is`(Items.COCOA_BEANS)) KitchenRecipe(ItemStack(KitchenFoods.coffee.get()),water=true) else null
                KitchenType.FRIDGE -> if(input.`is`(Items.WATER_BUCKET)) KitchenRecipe(ItemStack(Items.ICE),container=ItemStack(Items.BUCKET)) else null
                else -> {
                    val recipe=world.recipeManager.getRecipeFor(RecipeType.SMELTING,SingleRecipeInput(input),world).orElse(null) ?: return null
                    val output=recipe.value().assemble(SingleRecipeInput(input),world.registryAccess())
                    if(output.has(DataComponents.FOOD))KitchenRecipe(output) else null
                }
            }
        }
        fun allowed(type: KitchenType,slot: Int,stack: ItemStack): Boolean = when {
            slot>=3 -> type==KitchenType.FRIDGE && (stack.has(DataComponents.FOOD) || stack.`is`(Items.POTION))
            slot==2 -> false
            slot==1 -> type==KitchenType.COFFEE && waterBottle(stack)
            type==KitchenType.TOASTER -> stack.`is`(Items.BREAD)
            type==KitchenType.COFFEE -> stack.`is`(Items.COCOA_BEANS)
            type==KitchenType.FRIDGE -> stack.`is`(Items.WATER_BUCKET)
            else -> stack.has(DataComponents.FOOD)
        }
    }
}
