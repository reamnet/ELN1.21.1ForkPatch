package mods.eln.utility

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class UtilityPartDescriptor(name: String,val part: Int): GenericItemUsingDamageDescriptor(name) {
    init { creativeTab=Eln.creativeTabPowerElectronics;setDefaultIcon(when(part) { 0->"polefuselink";1->"analogservicemeter";else->"smartservicemeter" }) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        if(part==0)list.add(tr("Right-click a fused pole to replace its first blown link"))
        else list.add(tr("Fits the North American Meter Base; records imported and exported energy"))
    }
}
object UtilityItems {
    const val FUSE_ID=4+(123 shl 6)
    const val ANALOG_ID=5+(123 shl 6)
    const val SMART_ID=6+(123 shl 6)
    fun part(stack: ItemStack?): Int?=if(stack==null || stack.isEmpty)null else (GenericItemUsingDamageDescriptor.getDescriptor(stack) as? UtilityPartDescriptor)?.part
    fun isFuse(stack: ItemStack?)=part(stack)==0
    fun register() {
        listOf(I18N.TR_NAME(I18N.Type.NONE,"Pole Fuse Link"),I18N.TR_NAME(I18N.Type.NONE,"Analog Glass Service Meter"),I18N.TR_NAME(I18N.Type.NONE,"Smart Digital Service Meter")).forEachIndexed { i,name -> Eln.sharedItem.addElement(FUSE_ID+i,UtilityPartDescriptor(name,i)) }
    }
}
