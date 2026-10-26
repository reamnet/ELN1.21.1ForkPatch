package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.misc.tagCompound
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.*

/** One replaceable kit protects the pole's primary conductors, without port probes or extra states. */
class PoleSurgeUpgrade(private val owner: TransparentNodeElement,lines: List<ElectricalLoad>,private val nominal: Double) {
    var fitted=false;private set
    var remaining=200000.0;private set
    private var hold=0.0
    private val shunts=lines.map { Resistor(it,null).apply { resistance=1e19 }.also(owner.electricalComponentList::add) }
    init {
        owner.slowPreProcessList.add(IProcess { configure() })
        owner.electricalProcessList.add(IProcess { dt->
            if(fitted && remaining>0 && dt.isFinite() && dt>0)remaining=max(0.0,remaining-shunts.sumOf { it.power.coerceAtLeast(0.0) }*dt)
            hold=max(0.0,hold-dt)
        })
        owner.slowProcessList.add(IProcess { configure() })
    }
    private fun configure() {
        val active=fitted && remaining>0 && (hold>0 || shunts.any { abs(it.voltage)>nominal*1.3 })
        shunts.forEach { it.resistance=if(active)nominal*nominal/(500000.0/shunts.size) else 1e19 }
    }
    fun lightning(): Boolean {
        if(!fitted || remaining<40000)return false
        remaining-=40000;hold=0.0;configure();owner.needPublish();return true
    }
    fun item()=Eln.findItemStack(IndustrialKind.ARRESTER.title,1).also { it.tagCompound=CompoundTag().apply { putDouble("industrialSurge",remaining) } }
    fun interact(player: Player): Boolean {
        val stack=player.mainHandItem
        if(player.isShiftKeyDown && stack.isEmpty && fitted) {
            val removed=item();fitted=false;remaining=200000.0;configure();player.inventory.placeItemBackInInventory(removed);owner.needPublish();return true
        }
        val d=GenericItemBlockUsingDamageDescriptor.getDescriptor(stack) as? IndustrialDescriptor ?: return false
        if(d.kind!=IndustrialKind.ARRESTER)return false
        val tag=stack.tagCompound
        val capacity=if(tag?.contains("industrialSurge")==true)tag.getDouble("industrialSurge").takeIf { it.isFinite() }?.coerceIn(0.0,200000.0) ?: 0.0 else 200000.0
        if(capacity<=0 || fitted && remaining>0)return true
        if(fitted)player.inventory.placeItemBackInInventory(item())
        fitted=true;remaining=capacity;hold=0.0;stack.shrink(1);configure();owner.needPublish();return true
    }
    fun write(tag: CompoundTag) { tag.putBoolean("poleArrester",fitted);tag.putDouble("poleArresterJoules",remaining) }
    fun read(tag: CompoundTag) { fitted=tag.getBoolean("poleArrester");remaining=tag.getDouble("poleArresterJoules").takeIf { it.isFinite() }?.coerceIn(0.0,200000.0) ?: 0.0;configure() }
    fun drop() { if(fitted) { owner.node?.dropItem(item());fitted=false;configure() } }
}
