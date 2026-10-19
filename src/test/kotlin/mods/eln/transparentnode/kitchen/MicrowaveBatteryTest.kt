package mods.eln.transparentnode.kitchen

import mods.eln.Eln
import mods.eln.transparentnode.climate.ClimateType
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.Test
import kotlin.test.*

class MicrowaveBatteryTest {
    @Test fun batteryHissesBeforeExplodingWithPaidEnergy() {
        val heat=MicrowaveBatteryHeat()
        assertFalse(heat.advance(5.9,1000.0));assertFalse(heat.hissing)
        assertFalse(heat.advance(.1,1000.0));assertTrue(heat.hissing)
        assertFalse(heat.advance(1.9,1000.0))
        assertTrue(heat.advance(.1,1000.0))
    }
    @Test fun largePhysicalStepsCannotSkipTheAudibleWarningFuse() {
        val heat=MicrowaveBatteryHeat()
        assertFalse(heat.advance(.05,1e9));assertTrue(heat.hissing)
        assertFalse(heat.advance(1.9,1000.0));assertTrue(heat.advance(.1,1000.0))
    }
    @Test fun outageAndBadInputsNeverAdvanceTheFuse() {
        val heat=MicrowaveBatteryHeat();heat.advance(6.0,1000.0)
        for(time in listOf(-1.0,0.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(heat.advance(time,1000.0))
        for(power in listOf(-1.0,0.0,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(heat.advance(100.0,power))
        assertEquals(6000.0,heat.joules);assertEquals(0.0,heat.warningSeconds)
        assertTrue(heat.advance(2.0,1000.0))
    }
    @Test fun reloadPreservesHeatedBatteryAndWarningTime() {
        val heat=MicrowaveBatteryHeat();heat.advance(6.75,1000.0)
        val copy=MicrowaveBatteryHeat().also { it.read(CompoundTag().also(heat::write)) }
        assertTrue(copy.hissing);assertEquals(6750.0,copy.joules);assertEquals(.75,copy.warningSeconds)
        assertFalse(copy.advance(1.0,1000.0));assertTrue(copy.advance(.25,1000.0))
        copy.reset();assertFalse(copy.hissing);assertEquals(0.0,copy.joules);assertEquals(0.0,copy.warningSeconds)
    }
    @Test fun malformedSavedHeatCannotCreateInvalidState() {
        for(value in listOf(Double.NaN,Double.NEGATIVE_INFINITY,-10.0)) {
            val heat=MicrowaveBatteryHeat().also { it.read(CompoundTag().apply {
                putDouble("microwaveBatteryJoules",value);putDouble("microwaveBatteryWarning",value)
            }) }
            assertEquals(0.0,heat.joules);assertEquals(0.0,heat.warningSeconds)
        }
        val heat=MicrowaveBatteryHeat().also { it.read(CompoundTag().apply {
            putDouble("microwaveBatteryJoules",500.0);putDouble("microwaveBatteryWarning",100.0)
        }) }
        assertFalse(heat.hissing);assertEquals(0.0,heat.warningSeconds)
    }
    @Test fun microwaveAcceptsAllBatteryFamiliesWithoutAcceptingCapacitorsOrFoodAsBatteries() {
        for(name in listOf("Portable Battery","Portable Battery Pack","UPS AGM Battery Cell",
            "UPS LiFePO4 Battery Cell","UPS Industrial Battery Module","Capacity Oriented Battery")) {
            val stack=Eln.findItemStack(name,1)
            assertFalse(stack.isEmpty,name);assertTrue(MicrowaveBatteryHeat.accepts(stack),name)
            assertTrue(KitchenRecipe.allowed(KitchenType.MICROWAVE,0,stack),name)
            for(t in KitchenType.entries.filter { it!=KitchenType.MICROWAVE })assertFalse(KitchenRecipe.allowed(t,0,stack),t.title)
        }
        for(stack in listOf(ItemStack.EMPTY,ItemStack(Items.BEEF),Eln.findItemStack("Portable Condensator",1),Eln.findItemStack("Portable Condensator Pack",1)))assertFalse(MicrowaveBatteryHeat.accepts(stack))
    }
    @Test fun everyRunningApplianceHasAnActualOggAsset() {
        val root=java.io.File(System.getProperty("eln.projectDir"),"src/main/resources/assets/eln")
        val metadata=com.google.gson.JsonParser.parseString(root.resolve("sounds.json").readText()).asJsonObject
        val names=KitchenType.entries.map { it.sound }+ClimateType.entries.map { it.sound }+
            listOf("appliance_ceiling_fan","appliance_battery_hiss","appliance_done","appliance_toaster_pop")
        for(name in names) {
            assertTrue(metadata.has(name),name)
            val sound=metadata[name].asJsonObject["sounds"].asJsonArray[0].asJsonObject["name"].asString.removePrefix("eln:")
            val bytes=root.resolve("sounds/$sound.ogg").readBytes()
            assertEquals("OggS",String(bytes.copyOfRange(0,4)),name)
            assertTrue(bytes.size>1000,name)
        }
    }
}
