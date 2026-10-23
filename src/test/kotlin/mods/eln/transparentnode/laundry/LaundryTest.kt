package mods.eln.transparentnode.laundry

import mods.eln.Eln
import mods.eln.misc.SidedFluidAdapter
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.cord.CordRating
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.test.*

class LaundryTest {
    @Test fun washingRepairsAndSealsTheFullItemUntilDrying() {
        for(item in listOf(Items.DIAMOND_PICKAXE,Items.IRON_CHESTPLATE,Items.SHIELD)) {
            val original=ItemStack(item).apply {
                damageValue=50
                set(DataComponents.CUSTOM_NAME,Component.literal("My equipment"))
                set(DataComponents.CUSTOM_DATA,CustomData.of(CompoundTag().apply { putString("otherMod","keep me") }))
            }
            val wet=WetEquipment.wash(original)
            assertFalse(wet.isDamageableItem);assertFalse(wet.item is net.minecraft.world.item.ArmorItem)
            assertEquals(50,original.damageValue,"Input must not change during recipe preview")
            val dry=WetEquipment.unpack(wet)
            assertEquals(0,dry.damageValue);assertSame(item,dry.item)
            assertEquals(original.get(DataComponents.CUSTOM_NAME),dry.get(DataComponents.CUSTOM_NAME))
            assertEquals(original.get(DataComponents.CUSTOM_DATA),dry.get(DataComponents.CUSTOM_DATA))
            dry.damageValue=20
            assertEquals(0,WetEquipment.unpack(wet).damageValue,"Unpacking must return an independent copy")
        }
    }
    @Test fun undamagedOrNonEquipmentCannotBeWashedAndArbitraryContainersCannotBeDried() {
        assertFalse(WetEquipment.washable(ItemStack(Items.DIAMOND_SWORD)))
        assertFalse(WetEquipment.washable(ItemStack(Items.APPLE)))
        assertTrue(WetEquipment.unpack(ItemStack(Items.SHULKER_BOX)).isEmpty)
        val invalid=ItemStack(WetEquipment.item.get())
        assertTrue(WetEquipment.unpack(invalid).isEmpty)
    }
    @Test fun waterPipesFillFromEverySideWithoutExtractingOrBypassingTheCycle() {
        val water=WasherWater()
        for(side in Direction.entries) {
            val pipe=SidedFluidAdapter(water,side)
            assertEquals(1000,pipe.fill(FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE))
            assertEquals(1000,pipe.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE))
            assertEquals(0,pipe.fill(FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE))
            assertTrue(pipe.drain(1000,FluidAction.EXECUTE).isEmpty)
        }
        assertEquals(6000,water.tank.fluidAmount)
        repeat(6) { assertTrue(water.reserve()) }
        assertFalse(water.reserve());assertTrue(water.tank.isEmpty)
    }
    @Test fun powerAndWaterAndSavedDataCannotCreateFreeProgress() {
        val work=MachineWork()
        work.account(60.0,0.0,48000.0);assertEquals(0.0,work.joules)
        work.account(Double.NaN,800.0,48000.0);assertEquals(0.0,work.joules)
        work.account(30.0,800.0,48000.0);assertEquals(24000.0,work.joules)
        val resumed=MachineWork();resumed.load(work.joules,48000.0)
        resumed.account(30.0,800.0,48000.0);assertEquals(48000.0,resumed.joules)
        resumed.load(Double.POSITIVE_INFINITY,48000.0);assertEquals(0.0,resumed.joules)
        val water=WasherWater();water.fill(null,FluidStack(Fluids.WATER,7000),true)
        val tag=CompoundTag();water.save(tag)
        val restored=WasherWater();restored.load(tag);assertEquals(7000,restored.tank.fluidAmount)
    }
    @Test fun savedReactorWorkSurvivesBootBeforeRecipeValuesAreAvailable() {
        val d=Eln.transparentNodeItem.subItemList[(125 shl 6)+11] as MatterMachineDescriptor
        val e=MatterMachineElement(TransparentNode(),d)
        e.inventory.setItem(0,ItemStack(Items.DIAMOND_BLOCK))
        val tag=CompoundTag();e.writeToNBT(tag);tag.putDouble("matterJoules",500_000_000.0)
        e.readFromNBT(tag)
        assertEquals(500_000_000.0,e.work.joules)
        val saved=CompoundTag();e.writeToNBT(saved)
        assertEquals(500_000_000.0,saved.getDouble("matterJoules"))
    }
    @Test fun reservedWashWaterAndPartlyPaidWorkSurviveReloadTogether() {
        val d=Eln.transparentNodeItem.subItemList[(125 shl 6)+9] as MatterMachineDescriptor
        val e=MatterMachineElement(TransparentNode(),d)
        e.inventory.setItem(0,ItemStack(Items.IRON_HELMET).apply { damageValue=20 })
        e.water.fill(null,FluidStack(Fluids.WATER,3000),true)
        val tag=CompoundTag();e.writeToNBT(tag)
        tag.putDouble("matterJoules",24000.0);tag.putBoolean("washReserved",true)
        e.readFromNBT(tag)
        assertEquals(24000.0,e.work.joules);assertTrue(e.waterReserved)
        assertEquals(3000,e.water.tank.fluidAmount)
    }
    @Test fun newAppliancesKeepOldIdsAndHaveCorrectInputsAndReactorFootprint() {
        val washer=MatterMachineElement(TransparentNode(),Eln.transparentNodeItem.subItemList[(125 shl 6)+9] as MatterMachineDescriptor)
        val dryer=MatterMachineElement(TransparentNode(),Eln.transparentNodeItem.subItemList[(125 shl 6)+10] as MatterMachineDescriptor)
        assertEquals(120.0,washer.cordVoltage);assertEquals(800.0,washer.requiredWatts)
        assertTrue(washer.acceptsCord(CordRating.V120));assertFalse(washer.acceptsCord(CordRating.V240))
        assertEquals(240.0,dryer.cordVoltage);assertEquals(6000.0,dryer.requiredWatts)
        assertTrue(dryer.acceptsCord(CordRating.V240_30));assertFalse(dryer.acceptsCord(CordRating.V240))
        val d=Eln.transparentNodeItem.subItemList[(125 shl 6)+11] as MatterMachineDescriptor
        val reactor=MatterMachineElement(TransparentNode(),d)
        assertTrue(CordRating.entries.none(reactor::acceptsCord),"Maximum 500 kW draw exceeds the existing 125 A industrial cord")
        assertEquals(23,d.ghostGroup!!.size())
        val cells=d.ghostGroup!!.elementList.map { Triple(it.x,it.y,it.z) }.toSet()+Triple(0,0,0)
        assertEquals(24,cells.size);assertEquals(2,cells.map { it.first }.distinct().size)
        assertEquals(2,cells.map { it.second }.distinct().size);assertEquals(6,cells.map { it.third }.distinct().size)
        assertNotNull(Eln.findItemStack("480 V Industrial Fluid Pump",1))
        assertNotNull(Eln.findItemStack("Warm White Deck Globe String Lights",1))
    }
}
