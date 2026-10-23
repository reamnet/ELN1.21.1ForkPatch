package mods.eln.transparentnode.fusion

import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.transparentnode.fusion.value.SimpleGraphMapper
import mods.eln.transparentnode.fusion.value.arithmetic.FullBigFractionArithmetic
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.CraftingRecipe
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.apache.commons.math3.fraction.BigFraction
import org.slf4j.LoggerFactory
import kotlin.math.log2

/** Private EMC mapping: no ProjectE APIs, EMC currency, items or transmutation tables are exposed. */
@EventBusSubscriber(modid=Eln.MODID)
object FusionValues {
    private val log=LoggerFactory.getLogger("ELN/FusionValues")
    private val blacklist=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("eln","fusion_blacklist"))
    private var values: Map<String,Double> = emptyMap()
    var revision=0;private set
    @SubscribeEvent fun started(event: ServerStartedEvent) { rebuild(event.server) }
    @SubscribeEvent fun reloaded(event: OnDatapackSyncEvent) { if(event.player==null)rebuild(event.playerList.server) }
    @SubscribeEvent fun stopped(event: ServerStoppedEvent) { values=emptyMap();revision++ }
    fun cost(stack: ItemStack): FusionCost? {
        if(stack.isEmpty || stack.item !is BlockItem || stack.`is`(blacklist))return null
        val value=values[BuiltInRegistries.ITEM.getKey(stack.item).toString()] ?: return null
        return FusionCost.fromValue(value)
    }
    fun rebuild(server: MinecraftServer) {
        val graph=SimpleGraphMapper<String,BigFraction,FullBigFractionArithmetic>(FullBigFractionArithmetic())
        val seeds=linkedMapOf<String,Long>()
        val blocked=hashSetOf<String>()
        for((id,res) in server.resourceManager.listResources("fusion_values") { it.path.endsWith(".json") }.toSortedMap()) {
            try {
                res.openAsReader().use { reader ->
                    val json=JsonParser.parseReader(reader).asJsonObject
                    json.getAsJsonObject("values")?.entrySet()?.forEach { (key,value) ->
                        val n=value.asLong
                        if(n>0 && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(key)))seeds[key]=n
                    }
                    json.getAsJsonArray("blacklist")?.forEach { blocked+=it.asString }
                }
            } catch(e: Exception) { log.warn("Cannot read fusion values {}",id,e) }
        }
        for((key,value) in seeds)graph.setValueBefore(key,BigFraction(value))
        val groups=hashSetOf<String>()
        var recipeCount=0
        for(holder in server.recipeManager.recipes) {
            val recipe=holder.value()
            if(recipe !is CraftingRecipe && recipe !is AbstractCookingRecipe)continue
            if(recipe.isSpecial)continue
            try {
                val output=recipe.getResultItem(server.registryAccess())
                if(output.isEmpty || !output.componentsPatch.isEmpty)continue
                val ingredients=recipe.ingredients.filter { !it.isEmpty }
                if(ingredients.isEmpty() || ingredients.any { !it.isSimple })continue
                val inputs=ingredients.map { ingredient ->
                    val alternatives=ingredient.items.map { BuiltInRegistries.ITEM.getKey(it.item).toString() }.distinct().sorted()
                    if(alternatives.isEmpty())return@map null
                    if(alternatives.size==1)alternatives.single() else {
                        val key="ingredient:"+alternatives.joinToString("|")
                        if(groups.add(key))for(member in alternatives)graph.addConversion(1,key,listOf(member))
                        key
                    }
                }
                if(inputs.any { it==null })continue
                graph.addConversion(output.count,BuiltInRegistries.ITEM.getKey(output.item).toString(),inputs.filterNotNull())
                recipeCount++
            } catch(e: Exception) { log.debug("Skipping unsupported fusion mapping {}",holder.id(),e) }
        }
        values=graph.generateValues().mapNotNull { (key,value) ->
            val n=value.toDouble()
            if(!key.startsWith("ingredient:") && key !in blocked && n.isFinite() && n>0)key to n else null
        }.toMap()
        revision++
        log.info("Fusion reactor mapped {} item values from {} recipes",values.size,recipeCount)
    }
}

/** Common blocks cost 100 kW for ten minutes, escalating to 500 kW for thirty minutes.
 * Power and time both scale with value; this curve never uses EMC as electrical joules directly. */
data class FusionCost(val value: Double,val watts: Double,val seconds: Double) {
    val joules get()=watts*seconds
    companion object {
        fun fromValue(value: Double): FusionCost? {
            if(!value.isFinite() || value<=0)return null
            val tier=(log2(value.coerceAtLeast(1.0))/20.0).coerceIn(0.0,1.0)
            return FusionCost(value,100_000+400_000*tier,600+1200*tier)
        }
    }
}
