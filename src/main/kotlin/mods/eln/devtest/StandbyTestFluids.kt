package mods.eln.devtest

import mods.eln.Eln
import mods.eln.transparentnode.standby.StandbyFuel
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluid
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.neoforge.registries.RegisterEvent

/** Opt-in fixtures only: ordinary gameplay never registers these fluids or buckets. */
@EventBusSubscriber(modid=Eln.MODID,bus=EventBusSubscriber.Bus.MOD)
object StandbyTestFluids {
    val ids=listOf("eln_test_a:gasoline","eln_test_b:gasoline","eln_test_c:refined_motor_fuel").map(ResourceLocation::parse)
    private lateinit var type: FluidType
    private val fluids=mutableMapOf<ResourceLocation,Fluid>()
    private val buckets=mutableMapOf<ResourceLocation,Item>()
    @SubscribeEvent fun register(event: RegisterEvent) {
        if(!java.lang.Boolean.getBoolean("eln.standbyChecks") && !java.lang.Boolean.getBoolean("eln.lightingExpansionChecks") && System.getProperty("fml.junit.argsfile")==null)return
        when(event.registryKey) {
            NeoForgeRegistries.Keys.FLUID_TYPES -> event.register(NeoForgeRegistries.Keys.FLUID_TYPES) { h ->
                type=FluidType(FluidType.Properties.create().density(750).viscosity(1000));h.register(ResourceLocation.parse("eln_test_a:motor_fuel"),type)
            }
            Registries.FLUID -> event.register(Registries.FLUID) { h -> ids.forEach { id ->
                val properties=BaseFlowingFluid.Properties({ type },{ fluids.getValue(id) },{ fluids.getValue(id) }).bucket { buckets.getValue(id) }
                val fluid=BaseFlowingFluid.Source(properties);fluids[id]=fluid;h.register(id,fluid)
            } }
            Registries.ITEM -> event.register(Registries.ITEM) { h -> ids.forEach { id ->
                val item=BucketItem(fluids.getValue(id),Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));buckets[id]=item
                h.register(ResourceLocation.fromNamespaceAndPath(id.namespace,id.path+"_bucket"),item)
            } }
        }
    }
    fun fluid(index: Int)=BuiltInRegistries.FLUID.get(ids[index])
    fun bucket(index: Int)=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ids[index].namespace,ids[index].path+"_bucket"))
    /** Install the unusual-name fixture as a datapack tag would; preserve every other tag. */
    fun bindTestTag() {
        val registry=BuiltInRegistries.FLUID
        val tags=registry.tags.toList().associate { it.first to it.second.toList() }.toMutableMap()
        tags[StandbyFuel.tags.first()]=listOf<Holder<Fluid>>(fluid(2).builtInRegistryHolder())
        registry.bindTags(tags)
    }
}

@EventBusSubscriber(modid=Eln.MODID)
object StandbyTestTags {
    @SubscribeEvent fun tags(event: net.neoforged.neoforge.event.TagsUpdatedEvent) {
        if(java.lang.Boolean.getBoolean("eln.standbyChecks"))StandbyTestFluids.bindTestTag()
    }
}
