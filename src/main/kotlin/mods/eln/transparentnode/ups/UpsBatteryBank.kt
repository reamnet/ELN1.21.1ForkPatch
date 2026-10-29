package mods.eln.transparentnode.ups

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.item.electricalitem.BatteryItem
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.node.transparent.TransparentNodeElementInventory
import net.minecraft.world.item.ItemStack

fun upsBattery(stack: ItemStack): BatteryItem? =
    if (stack.isEmpty) null else (GenericItemUsingDamageDescriptor.getDescriptor(stack) as? BatteryItem)
        ?.takeIf { it.getPriority(stack) == 2 && it.getEnergyMax(stack).isFinite() && it.getEnergyMax(stack) > 0 }

/** Flush cached cell energy before reading, removing, saving or replacing an actual item. */
class UpsInventory(owner: TransparentNodeElement,val cells: Int=48,val rackSize: Int=24) : TransparentNodeElementInventory(cells,1,owner) {
    var bank: UpsBatteryBank? = null
    fun rawItem(slot: Int) = super.getItem(slot)
    override fun getItem(slot: Int): ItemStack { bank?.flushSlot(slot); return rawItem(slot) }
    override fun setItem(slot: Int, stack: ItemStack) {
        bank?.flush(); super.setItem(slot,stack); bank?.invalidate()
    }
    override fun clearContent() { bank?.flush(); super.clearContent(); bank?.invalidate() }
    override fun getAccessibleSlotsFromSide(side: Int) = IntArray(cells) { it }
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = stack != null && upsBattery(stack) != null
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack?, side: Int) = true
}

/** Physical energy belongs to individual removable batteries, never to an abstract capacity bonus. */
class UpsBatteryBank(private val inventory: UpsInventory) {
    private data class Cell(val slot: Int,val stack: ItemStack,val type: BatteryItem,var energy: Double,
        val capacity: Double,val chargeWatts: Double,val dischargeWatts: Double,var dirty: Boolean=false)
    private var cells = emptyList<Cell>()
    private var active = emptyList<Cell>()
    private var invalid = true
    init { inventory.bank=this }
    fun invalidate() { invalid=true }
    private fun refresh() {
        if (!invalid) return
        flush()
        cells=(0 until inventory.cells).mapNotNull { slot ->
            val stack=inventory.rawItem(slot)
            val type=upsBattery(stack) ?: return@mapNotNull null
            val capacity=type.getEnergyMax(stack)
            val raw=type.getEnergy(stack)
            Cell(slot,stack,type,raw.takeIf { it.isFinite() }?.coerceIn(0.0,capacity) ?: 0.0,capacity,
                type.getChargePower(stack).coerceAtLeast(0.0),type.getDischagePower(stack).coerceAtLeast(0.0),
                !raw.isFinite() || raw !in 0.0..capacity)
        }
        active=cells.groupBy { it.slot/inventory.rackSize }.values.filter { it.size==inventory.rackSize }.flatten()
        invalid=false
    }
    val installed: Int get() { refresh();return cells.size }
    val activeCount: Int get() { refresh();return active.size }
    val energy: Double get() { refresh();return active.sumOf { it.energy } }
    val capacity: Double get() { refresh();return active.sumOf { it.capacity } }
    val chargeWatts: Double get() { refresh();return active.sumOf { it.chargeWatts } }
    val dischargeWatts: Double get() { refresh();return active.sumOf { it.dischargeWatts } }
    fun availableChargeWatts(seconds: Double): Double { refresh();return active.sumOf { minOf(it.chargeWatts,(it.capacity-it.energy)/seconds) } }
    fun availableDischargeWatts(seconds: Double): Double { refresh();return active.sumOf { minOf(it.dischargeWatts,it.energy/seconds) } }
    fun flushSlot(slot: Int) {
        cells.firstOrNull { it.slot==slot }?.let { cell ->
            if (cell.dirty && inventory.rawItem(slot)===cell.stack) {
                cell.type.setEnergy(cell.stack,cell.energy);cell.dirty=false
            }
        }
    }
    fun flush() { cells.filter { it.dirty }.forEach { flushSlot(it.slot) } }
    /** Returns signed energy actually accepted, bounded by individual cell ratings and remaining charge. */
    fun transfer(joules: Double, seconds: Double): Double {
        refresh()
        if (!joules.isFinite() || !seconds.isFinite() || seconds<=0.0 || active.isEmpty()) return 0.0
        val charging=joules>=0
        val limits=active.map { if(charging) minOf(it.capacity-it.energy,it.chargeWatts*seconds)
            else minOf(it.energy,it.dischargeWatts*seconds) }
        val total=limits.sum()
        if(total<=0) return 0.0
        val amount=minOf(kotlin.math.abs(joules),total)
        active.forEachIndexed { index,cell ->
            val delta=amount*(limits[index]/total)*(if(charging)1 else -1)
            cell.energy=(cell.energy+delta).coerceIn(0.0,cell.capacity);cell.dirty=true
        }
        return if(charging)amount else -amount
    }
}
