package mods.eln.utility

import mods.eln.misc.tagCompound
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Meter registers and identity belong to the removable meter, not to its socket. */
object ServiceMeters {
    const val KH_WH=7.2
    fun diskDegrees(joules: Double)=if(joules.isFinite())joules/(KH_WH*10.0) else 0.0
    fun diskPosition(imported: Double,exported: Double)=((imported-exported)*1000.0%KH_WH)/KH_WH*360.0
    fun data(stack: ItemStack): CompoundTag?=stack.tagCompound?.takeIf { it.contains("serviceMeter") }?.getCompound("serviceMeter")
    fun store(stack: ItemStack,energy: ServiceEnergy,disconnected: Boolean) {
        if(UtilityItems.part(stack) !in 1..2)return
        val previous=data(stack)
        val open=UtilityItems.part(stack)==2 && disconnected
        if(previous!=null && previous.getString("serial").isNotBlank() && previous.getDouble("meterImportKwh")==energy.importedKwh && previous.getDouble("meterExportKwh")==energy.exportedKwh && previous.getBoolean("meterDisconnected")==open)return
        val root=stack.tagCompound?.copy() ?: CompoundTag()
        val data=previous?.copy() ?: CompoundTag()
        if(data.getString("serial").isBlank())data.putString("serial",UUID.randomUUID().toString().replace("-","").take(10).uppercase(java.util.Locale.ROOT))
        energy.write(data);data.putBoolean("meterDisconnected",UtilityItems.part(stack)==2 && disconnected)
        root.put("serviceMeter",data);stack.tagCompound=root
    }
}
