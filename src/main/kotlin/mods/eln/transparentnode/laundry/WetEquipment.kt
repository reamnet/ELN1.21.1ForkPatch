package mods.eln.transparentnode.laundry

import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.registration.ElnRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.ItemContainerContents
import java.util.function.Supplier

/** A sealed carrier is deliberately not a tool or armor item: even automated equippers cannot use it.
 * The full repaired stack is a vanilla serialized component, retaining other mods' components too. */
object WetEquipment {
    lateinit var item: Supplier<Item>;private set
    fun register() {
        I18N.TR_NAME(I18N.Type.NONE,"Wet Equipment")
        item=ElnRegistry.registerItem("wet_equipment", { object: Item(Properties().stacksTo(1)) {
            override fun getDescriptionId()="Wet_Equipment.name"
            override fun getName(stack: ItemStack)=Component.literal(tr("Wet: %1$",unpack(stack).hoverName.string))
            override fun appendHoverText(stack: ItemStack,context: TooltipContext,lines: MutableList<Component>,flag: TooltipFlag) {
                super.appendHoverText(stack,context,lines,flag)
                lines.add(Component.literal(tr("Unusable while wet; put it in an Electric Dryer")))
            }
        } })
    }
    fun washable(stack: ItemStack)=!stack.isEmpty && stack.count==1 && stack.isDamageableItem && stack.isDamaged
    fun wash(stack: ItemStack): ItemStack {
        require(washable(stack))
        val repaired=stack.copyWithCount(1).apply { damageValue=0 }
        return ItemStack(item.get()).apply { set(DataComponents.CONTAINER,ItemContainerContents.fromItems(listOf(repaired))) }
    }
    fun unpack(stack: ItemStack): ItemStack {
        if(stack.isEmpty || !::item.isInitialized || !stack.`is`(item.get()))return ItemStack.EMPTY
        val contents=stack.get(DataComponents.CONTAINER)?.stream()?.toList() ?: return ItemStack.EMPTY
        return contents.singleOrNull()?.takeIf { it.count==1 && it.isDamageableItem }?.copy() ?: ItemStack.EMPTY
    }
}
