package mods.eln.transparentnode.kitchen

import mods.eln.Eln
import mods.eln.generic.CreativeTabPopulator
import mods.eln.i18n.I18N
import mods.eln.registration.ElnRegistry
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.UseAnim
import java.util.function.Supplier

object KitchenFoods {
    lateinit var toast: Supplier<Item>
        private set
    lateinit var coffee: Supplier<Item>
        private set
    fun register() {
        val toastName=I18N.TR_NAME(I18N.Type.NONE,"Toasted Bread")
        val coffeeName=I18N.TR_NAME(I18N.Type.NONE,"Fresh Coffee")
        toast=ElnRegistry.registerItem("toasted_bread", { object : Item(Properties().food(
            FoodProperties.Builder().nutrition(6).saturationModifier(.6f).build())) {
            override fun getDescriptionId()="Toasted_Bread.name"
        } })
        coffee=ElnRegistry.registerItem("fresh_coffee", { object : Item(Properties().stacksTo(16).food(
            FoodProperties.Builder().nutrition(1).saturationModifier(.1f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE).build())) {
            override fun getDescriptionId()="Fresh_Coffee.name"
            override fun getUseAnimation(stack: ItemStack)=UseAnim.DRINK
        } })
        ElnRegistry.registerCustomItemStack(toastName) { ItemStack(toast.get()) }
        ElnRegistry.registerCustomItemStack(coffeeName) { ItemStack(coffee.get()) }
        CreativeTabPopulator.register(Eln.creativeTabMachines) { ItemStack(toast.get()) }
        CreativeTabPopulator.register(Eln.creativeTabMachines) { ItemStack(coffee.get()) }
    }
}
