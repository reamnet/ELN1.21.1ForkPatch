package mods.eln.transparentnode.ups

import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.BatteryItem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/** Same per-item energy contract as portable cells: capacity never grants free charge. */
class UpsLargeCell(name: String,val size: Int) : BatteryItem(name,
    doubleArrayOf(960_000.0,3_600_000.0,14_400_000.0)[size],
    doubleArrayOf(500.0,1000.0,2000.0)[size],doubleArrayOf(1000.0,2000.0,4000.0)[size],2) {
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        super.addInformation(itemStack,entityPlayer,list,par4)
        list.add(tr("Rated capacity: %1$ kWh",energyStorage/3_600_000))
        list.add(tr("UPS rack compatible; larger capacity does not raise the UPS output rating"))
        list.add(tr("Crafted empty; charge before use"))
    }
}
