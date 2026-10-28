package mods.eln.utility

import mods.eln.Eln
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

object UtilityRecipes {
    fun register(shaped: (ItemStack,Array<out Any>)->Unit,shapeless: (ItemStack,Array<out Any>)->Unit) {
        mods.eln.transparentnode.industrial.IndustrialRecipes.register(shaped)
        fun addRecipe(output: ItemStack,vararg params: Any)=shaped(output,params)
        fun addShapelessRecipe(output: ItemStack,vararg params: Any)=shapeless(output,params)
        fun findItemStack(name: String,stackSize: Int=1)=Eln.findItemStack(name,stackSize)
        // Conversion recipes distinguish every variant and leave all old recipes/IDs intact.
        for(kind in DistributionKind.entries.filterNot { it.underground || it.light || it.singlePrimaryRiser || it.buriedSecondary }) {
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
        addShapelessRecipe(findItemStack(DistributionKind.FEEDER_SINGLE.title),findItemStack(DistributionKind.CUTOUT_SINGLE.title),findItemStack("Copper Cable"),findItemStack("Iron Plate"))
        addRecipe(findItemStack(PoleStreetlightAttachment.NAME)," II","CPG"," I ",'I',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'P',findItemStack("Basic Lamp Holder"),'G',ItemStack(Blocks.GLASS))
        addShapelessRecipe(findItemStack(DistributionKind.UNDERGROUND_SECONDARY_TRANSFORMER_SINGLE.title),findItemStack(DistributionKind.TRANSFORMER_SINGLE.title),findItemStack("Underground Service Entrance Riser"),findItemStack("Iron Plate"))
        mods.eln.transparentnode.standby.GeneratorTier.entries.drop(1).forEachIndexed { i,tier ->
            val previous=mods.eln.transparentnode.standby.GeneratorTier.entries[i].title
            if(tier==mods.eln.transparentnode.standby.GeneratorTier.EXTREME)
                addRecipe(findItemStack(tier.title),"GGG","PMP","CCC",'G',findItemStack(previous),'P',ItemStack(Blocks.IRON_BLOCK),'M',findItemStack("Electrical Motor"),'C',findItemStack("Copper Cable"))
            else addRecipe(findItemStack(tier.title),"GPG","CMC","PPP",'G',findItemStack(previous),'P',findItemStack("Iron Plate"),'M',findItemStack("Electrical Motor"),'C',findItemStack("Copper Cable"))
        }
        for(kind in DistributionKind.entries.filter { it.underground && !it.retiredGroundCabinet && !it.light }) {
            val base=when {
                kind.light->findItemStack(if(kind.phases==1)DistributionKind.UNDERGROUND_SINGLE.title else DistributionKind.UNDERGROUND_THREE.title)
                kind.pedestal->findItemStack("120/240V Service Weatherhead")
                else->findItemStack(if(kind.phases==1)DistributionKind.CUTOUT_SINGLE.title else DistributionKind.CUTOUT_THREE.title)
            }
            if(kind.light)addShapelessRecipe(findItemStack(kind.title),base,findItemStack("Basic Lamp Holder"),ItemStack(Items.IRON_INGOT))
            else addRecipe(findItemStack(kind.title),"PFP","CBC","PPP",'P',findItemStack("Iron Plate"),'F',findItemStack("Pole Fuse Link"),'C',findItemStack("Copper Cable"),'B',base)
        }
        val alarmComponents=listOf(Items.COMPARATOR,Items.LEVER,Items.NOTE_BLOCK,Items.BELL,Items.GLOWSTONE_DUST,Items.OBSERVER,Items.BLAZE_POWDER)
        mods.eln.transparentnode.firealarm.AlarmKind.entries.forEachIndexed { i,kind->
            addRecipe(findItemStack(kind.title),"IRI","RCR","IWI",'I',findItemStack("Iron Plate"),'R',ItemStack(Items.REDSTONE),'C',ItemStack(alarmComponents[i]),'W',findItemStack("Signal Cable"))
        }
        addRecipe(findItemStack("ELN to CEE Power Bridge"),"ICI","VSV","IMI",'I',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),
            'V',findItemStack("Variable DC-DC Converter"),'S',findItemStack("Silicon Wafer"),'M',findItemStack("Machine Block"))
        mods.eln.utility.insulator.InsulatorVoltage.entries.forEachIndexed { i,v->
            if(i==0)addRecipe(findItemStack(v.lineName)," C ","CIC"," P ",'C',ItemStack(Blocks.WHITE_TERRACOTTA),'I',ItemStack(Items.IRON_NUGGET),'P',findItemStack("Iron Plate"))
            else addRecipe(findItemStack(v.lineName),"ICI","PBP",'I',findItemStack("Iron Plate"),'C',ItemStack(if(v.volts>=69000)Blocks.QUARTZ_BLOCK else Blocks.WHITE_TERRACOTTA),
                'P',ItemStack(Items.IRON_INGOT),'B',findItemStack(mods.eln.utility.insulator.InsulatorVoltage.entries[i-1].lineName))
            addShapelessRecipe(findItemStack(v.feedName),findItemStack(v.lineName),findItemStack("Copper Cable"),ItemStack(Items.IRON_NUGGET))
        }
        mods.eln.transparentnode.switchgear.SwitchgearKind.entries.forEach { kind->
            val previous=when(kind) {
                mods.eln.transparentnode.switchgear.SwitchgearKind.CONTACTOR_THREE->"Single Pole Contactor"
                mods.eln.transparentnode.switchgear.SwitchgearKind.RELAY_FOUR->"Two Pole Changeover Relay"
                mods.eln.transparentnode.switchgear.SwitchgearKind.SSR_THREE->"Solid State Relay"
                else->null
            }
            if(previous!=null)addRecipe(findItemStack(kind.title),if(kind.poles==4)"R R" else "RRR","PWP",'R',findItemStack(previous),'P',findItemStack("Iron Plate"),'W',findItemStack("Copper Cable"))
            else {
                val core=if(kind.solidState)"Silicon Wafer" else "Cheap Ferromagnetic Core"
                val relay=if(kind.changeover)"12V Control Relay 20A" else if(kind.solidState)"5V Control Relay 10A" else "12V Control Relay 100A"
                addRecipe(findItemStack(kind.title)," C ","PRP","IWI",'C',findItemStack(core),'P',ItemStack(Items.IRON_NUGGET),
                    'R',findItemStack(relay),'I',findItemStack("Iron Plate"),'W',findItemStack("Copper Cable"))
            }
        }
        ServiceMastStyle.entries.drop(1).filterNot { it.underground }.forEachIndexed { i,mast ->
            addShapelessRecipe(findItemStack(mast.title),findItemStack("120/240V Service Weatherhead"),*Array(i+1) { ItemStack(Items.IRON_INGOT) })
        }
        addRecipe(findItemStack("Ceiling Color Segregated Cable Tray",4),"C C","BBB","PPP",'C',ItemStack(Items.CHAIN),'B',ItemStack(Blocks.IRON_BARS),'P',findItemStack("Iron Plate"))
        // Each style has a distinct dye marker; no ambiguous identical recipes.
        val dyes=listOf(Items.WHITE_DYE,Items.ORANGE_DYE,Items.MAGENTA_DYE,Items.LIGHT_BLUE_DYE,Items.YELLOW_DYE,Items.LIME_DYE,Items.PINK_DYE,Items.GRAY_DYE,Items.LIGHT_GRAY_DYE,Items.CYAN_DYE,Items.PURPLE_DYE,Items.BLUE_DYE,Items.BROWN_DYE,Items.GREEN_DYE,Items.RED_DYE)
        mods.eln.sixnode.lampsocket.DecorativeLampStyle.entries.drop(88).forEachIndexed { i,lamp ->
            addRecipe(findItemStack(lamp.itemName)," D ","GLG"," P ",'D',ItemStack(dyes[i]),'G',ItemStack(Blocks.GLASS_PANE),'L',findItemStack("Basic Lamp Holder"),'P',ItemStack(if(lamp.stoneWall)Blocks.STONE_BRICKS else Blocks.IRON_BLOCK))
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
        addRecipe(findItemStack("Underground Service Entrance Riser")," P "," C ","ICI",'P',findItemStack("Iron Plate"),'I',ItemStack(Items.IRON_INGOT),'C',findItemStack("Copper Cable"))
        addRecipe(findItemStack("120/240V Underground Service Handhole"),"IPI","CBC","III",'I',ItemStack(Blocks.STONE_BRICKS),'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'B',ItemStack(Blocks.IRON_BARS))
        addRecipe(findItemStack("Wide Underground Meter Socket"),"PPP","MCC","P P",'P',findItemStack("Iron Plate"),'M',findItemStack("North American Meter Base"),'C',findItemStack("Copper Cable"))
        addRecipe(findItemStack("North American Meter Base"),"P P","PCP","P P",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"))
        addRecipe(findItemStack("Analog Glass Service Meter")," G ","CMC"," P ",'G',ItemStack(Blocks.GLASS),'C',findItemStack("Copper Cable"),'M',findItemStack("Electrical Motor"),'P',findItemStack("Iron Plate"))
        addShapelessRecipe(findItemStack("Smart Digital Service Meter"),findItemStack("Analog Glass Service Meter"),ItemStack(Items.COMPARATOR),ItemStack(Items.REDSTONE))
        addRecipe(findItemStack("Red Handle Service Disconnect"),"PPP","CLC","PPP",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'L',ItemStack(Blocks.LEVER))
        addRecipe(findItemStack("Twelve Circuit American Breaker Panel"),"P P","CRC","PPP",'P',findItemStack("Iron Plate"),'C',findItemStack("Copper Cable"),'R',ItemStack(Items.REDSTONE))
        addRecipe(findItemStack("Aura Five Blade Ceiling Fan"),"PMP","LLL","PPP",'P',findItemStack("Iron Plate"),'M',findItemStack("Electrical Motor"),'L',findItemStack("Basic Lamp Holder"))
    }
}
