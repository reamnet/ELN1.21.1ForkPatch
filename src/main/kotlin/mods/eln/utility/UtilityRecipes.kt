package mods.eln.utility

import mods.eln.Eln
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

object UtilityRecipes {
    fun register(shaped: (ItemStack,Array<out Any>)->Unit,shapeless: (ItemStack,Array<out Any>)->Unit) {
        fun addRecipe(output: ItemStack,vararg params: Any)=shaped(output,params)
        fun addShapelessRecipe(output: ItemStack,vararg params: Any)=shapeless(output,params)
        fun findItemStack(name: String,stackSize: Int=1)=Eln.findItemStack(name,stackSize)
        // Conversion recipes distinguish every variant and leave all old recipes/IDs intact.
        for(kind in DistributionKind.entries.filterNot { it.light && it.singlePhaseTap }) {
            val base=if(kind.lvOnly)"Direct Utility Pole" else if(kind.phases==3 && kind!=DistributionKind.THREE)"Three Phase Utility Pole" else "Utility Pole"
            val detail=when {
                kind.transformer->findItemStack("Pole Fuse Link")
                kind.cutout->findItemStack("Pole Fuse Link")
                kind.singlePhaseTap->findItemStack("Pole Fuse Link")
                kind.recloser->ItemStack(Items.COMPARATOR)
                kind.regulator->findItemStack("DC-DC Converter")
                kind.capacitor->findItemStack("Copper Cable")
                kind==DistributionKind.AIR_BREAK_THREE->ItemStack(Blocks.LEVER)
                kind==DistributionKind.FEEDER_THREE->ItemStack(Items.IRON_INGOT)
                kind.light->findItemStack("Basic Lamp Holder")
                else->ItemStack(if(kind.phases==1)Items.IRON_NUGGET else Items.COPPER_INGOT)
            }
            val marker=ItemStack(when(kind) {
                DistributionKind.STREET_SINGLE,DistributionKind.STREET_THREE,DistributionKind.SECONDARY_LIGHT->Items.GLOWSTONE_DUST
                DistributionKind.TRANSFORMER_THREE,DistributionKind.CUTOUT_THREE,DistributionKind.REGULATOR_THREE->Items.REDSTONE
                else->Items.IRON_NUGGET
            })
            val fuses=if(kind.cutout || kind.regulator)kind.phases else if(kind.transformer)1 else 0
            val extra=if(kind.cutout)fuses-1 else if(kind.transformer)0 else fuses
            addShapelessRecipe(findItemStack(kind.title),findItemStack(base),detail,marker,*Array(extra.coerceAtLeast(0)) { findItemStack("Pole Fuse Link") })
        }
        addShapelessRecipe(findItemStack("Six Socket Power Strip"),findItemStack("Experimental Wall Outlet"),findItemStack("Experimental Wall Outlet"),findItemStack("Experimental Wall Outlet"),findItemStack("Iron Plate"))
        PoleHardware.transformerNames.forEachIndexed { i,name ->
            // Each successive transformer consumes the previous one and more copper/iron.
            val previous=if(i==0)"Mains Step-down Unit" else PoleHardware.transformerNames[i-1]
            addRecipe(findItemStack(name),"ICI","CTC","ICI",'I',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'T',findItemStack(previous))
        }
        PoleHardware.fuseNames.forEachIndexed { i,name ->
            addShapelessRecipe(findItemStack(name),findItemStack("Pole Fuse Link"),*Array(1+i/3) { ItemStack(Items.COPPER_INGOT) },*Array(i%3) { ItemStack(Items.IRON_NUGGET) })
        }
        addRecipe(findItemStack("Pole Fuse Link",3)," C "," P "," C ",'C',findItemStack("Copper Cable"),'P',findItemStack("Iron Plate"))
        addRecipe(findItemStack("120/240V Service Weatherhead"),"PPP"," C "," C ",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"))
        addRecipe(findItemStack("North American Meter Base"),"P P","PCP","P P",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"))
        addRecipe(findItemStack("Analog Glass Service Meter")," G ","CMC"," P ",'G',ItemStack(Blocks.GLASS),'C',findItemStack("Copper Cable"),'M',findItemStack("Electrical Motor"),'P',findItemStack("Iron Plate"))
        addShapelessRecipe(findItemStack("Smart Digital Service Meter"),findItemStack("Analog Glass Service Meter"),ItemStack(Items.COMPARATOR),ItemStack(Items.REDSTONE))
        addRecipe(findItemStack("Red Handle Service Disconnect"),"PPP","CLC","PPP",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'L',ItemStack(Blocks.LEVER))
        addRecipe(findItemStack("Twelve Circuit American Breaker Panel"),"P P","CRC","PPP",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'R',ItemStack(Items.REDSTONE))
        addRecipe(findItemStack("Aura Five Blade Ceiling Fan"),"PMP","LLL","PPP",'P',findItemStack("Iron Plate"),'M',findItemStack("Electrical Motor"),'L',findItemStack("Basic Lamp Holder"))
    }
}
