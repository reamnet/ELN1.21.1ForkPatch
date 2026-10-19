package mods.eln.transparentnode.kitchen

import mods.eln.misc.Utils
import mods.eln.item.electricalitem.BatteryItem
import mods.eln.transparentnode.battery.BatteryDescriptor
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack

/** The warning fuse advances in powered physical time, never during solver retries. */
class MicrowaveBatteryHeat {
    var joules=0.0
        private set
    var warningSeconds=0.0
        private set
    val hissing get()=joules>=HISS_JOULES
    fun reset() { joules=0.0;warningSeconds=0.0 }
    fun advance(seconds: Double,watts: Double): Boolean {
        if(!seconds.isFinite() || seconds<=0 || !watts.isFinite() || watts<=0)return false
        val before=joules
        joules=(joules+seconds*watts).coerceAtMost(BLAST_JOULES)
        if(hissing)warningSeconds+=(seconds-(HISS_JOULES-before).coerceAtLeast(0.0)/watts).coerceAtLeast(0.0)
        return joules>=BLAST_JOULES && warningSeconds>=MIN_WARNING_SECONDS-1e-9
    }
    fun read(tag: CompoundTag) {
        joules=tag.getDouble("microwaveBatteryJoules").takeIf { it.isFinite() }?.coerceIn(0.0,BLAST_JOULES) ?: 0.0
        warningSeconds=tag.getDouble("microwaveBatteryWarning").takeIf { it.isFinite() }?.coerceIn(0.0,MIN_WARNING_SECONDS) ?: 0.0
        if(!hissing)warningSeconds=0.0
    }
    fun write(tag: CompoundTag) { tag.putDouble("microwaveBatteryJoules",joules);tag.putDouble("microwaveBatteryWarning",warningSeconds) }
    companion object {
        const val HISS_JOULES=6000.0
        const val BLAST_JOULES=8000.0
        const val MIN_WARNING_SECONDS=2.0
        val BATTERIES: TagKey<net.minecraft.world.item.Item> = TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","batteries"))
        fun accepts(stack: ItemStack): Boolean {
            if(stack.isEmpty)return false
            val d=Utils.getItemObject(stack)
            return d is BatteryDescriptor || d is BatteryItem && d.getPriority(stack)==2 || stack.`is`(BATTERIES)
        }
    }
}
