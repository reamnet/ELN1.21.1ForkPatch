package mods.eln.compat

import mods.eln.transparentnode.kitchen.KitchenElement
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Stack identities are registered by fridge inventory reads, including capability/pipe reads.
 * No world scans, hard dependencies, or permanent 'never spoils' item flags are required. */
object RefrigeratorPreservation {
    private val integrations by lazy { net.neoforged.fml.ModList.get().mods.any { it.modId in setOf("spoiled","freshness","fffoodspoilageandpreservation") } }
    private val owners=java.util.Collections.synchronizedMap(WeakHashMap<ItemStack,WeakReference<KitchenElement>>())
    private val productionId=ResourceLocation.parse("freshness:production_time")
    private val ffId=ResourceLocation.parse("fffoodspoilageandpreservation:spoilage")
    fun touch(stack: ItemStack,owner: KitchenElement) {
        if(!integrations || stack.isEmpty)return
        if(owners[stack]?.get()!==owner) {
            // Settle any age accumulated in a previous container before protecting this stack.
            val type=BuiltInRegistries.DATA_COMPONENT_TYPE.get(ffId)
            if(type!=null && stack.has(type))owner.node?.coordinate?.world()?.let { world ->
                ffAdvance?.invoke(null,stack,world,1.0,1.0)
            }
            owners[stack]=WeakReference(owner)
        }
    }
    fun release(stack: ItemStack) { owners.remove(stack) }
    private val ffAdvance by lazy {
        if(BuiltInRegistries.DATA_COMPONENT_TYPE.get(ffId)==null)null else
            Class.forName("com.fyxe.fffoodspoilageandpreservation.util.SpoilageHelper").getMethod("advance",
                ItemStack::class.java,net.minecraft.world.level.Level::class.java,java.lang.Double.TYPE,java.lang.Double.TYPE)
    }
    @JvmStatic fun isCold(stack: ItemStack): Boolean {
        val owner=owners[stack]?.get() ?: return false
        if(!owner.coldStorage || owner.node?.isDestructing!=false)return false
        val coord=owner.node?.coordinate ?: return false
        if(!coord.world().hasChunkAt(coord.pos))return false
        // Removal or inventory replacement immediately ends protection, even before the next tick.
        return (3 until owner.inventory.containerSize).any { owner.inventory.getItem(it)===stack }
    }
    @Suppress("UNCHECKED_CAST")
    fun pause(stack: ItemStack,elapsed: Long,now: Long) {
        if(stack.isEmpty)return
        val production=BuiltInRegistries.DATA_COMPONENT_TYPE.get(productionId) as? DataComponentType<Long>
        if(production!=null && elapsed>0)stack.get(production)?.let { before ->
            // Move its production timestamp forward only by time actually spent cold.
            if(before<=now)stack.set(production,minOf(now,if(before>Long.MAX_VALUE-elapsed)Long.MAX_VALUE else before+elapsed))
        }
        val type=BuiltInRegistries.DATA_COMPONENT_TYPE.get(ffId) as? DataComponentType<Any> ?: return
        val component=stack.get(type) ?: return
        val methods=ffMethods.get(component.javaClass)
        val updated=methods.clock.invoke(component,now)
        stack.set(type,methods.rate.invoke(updated,0.0))
    }
    private data class FfMethods(val clock: java.lang.reflect.Method,val rate: java.lang.reflect.Method)
    private val ffMethods=object: ClassValue<FfMethods>() {
        override fun computeValue(type: Class<*>)=FfMethods(type.getMethod("withLastGameTime",java.lang.Long.TYPE),type.getMethod("withStorageRate",java.lang.Double.TYPE))
    }
}
