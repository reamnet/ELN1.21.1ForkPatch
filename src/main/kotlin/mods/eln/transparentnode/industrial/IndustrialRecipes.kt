package mods.eln.transparentnode.industrial

import mods.eln.Eln
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object IndustrialRecipes {
    fun register(shaped: (ItemStack,Array<out Any>)->Unit) {
        fun find(name: String)=Eln.findItemStack(name,1)
        val centers=listOf(Items.BLAZE_ROD,Items.COMPARATOR,Items.REPEATER,Items.LIGHTNING_ROD,Items.IRON_BLOCK,Items.REDSTONE_BLOCK,Items.OBSERVER,Items.QUARTZ,Items.FLINT_AND_STEEL)
        IndustrialKind.entries.forEachIndexed { i,kind->
            shaped(find(kind.title),arrayOf("IPI","WCW","IMI",'I',find("Iron Plate"),'P',ItemStack(Items.REDSTONE),'W',find("Copper Cable"),'C',ItemStack(centers[i]),'M',find("Machine Block")))
        }
        shaped(find(IndustrialItems.PACK),arrayOf("IBI","BBB","CBC",'I',find("Iron Plate"),'B',find("UPS Industrial Battery Module"),'C',find("Copper Cable")))
        shaped(find(IndustrialItems.FORKLIFT),arrayOf("FFF","CMC","III",'F',find(IndustrialItems.FRAME),'C',find("Copper Cable"),'M',find("Electrical Motor"),'I',ItemStack(Blocks.IRON_BLOCK)))
        shaped(find(IndustrialItems.PALLET),arrayOf("PPP","S S","PPP",'P',ItemStack(Blocks.OAK_PLANKS),'S',ItemStack(Items.STICK)))
    }
}
