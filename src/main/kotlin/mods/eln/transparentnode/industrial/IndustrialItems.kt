package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.BatteryItem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos

object IndustrialItems {
    val PACK=I18N.TR_NAME(I18N.Type.NONE,"48 V 20 kWh Traction Battery")
    val FRAME=I18N.TR_NAME(I18N.Type.NONE,"Welded Machine Frame")
    val FORKLIFT=I18N.TR_NAME(I18N.Type.NONE,"Electric Forklift")
    val PALLET=I18N.TR_NAME(I18N.Type.NONE,"Warehouse Equipment Pallet")
    fun register() {
        Eln.sharedItemStackOne.addElement(117 shl 6,object: BatteryItem(PACK,72000000.0,11520.0,50000.0,3) {
            init { creativeTab=Eln.creativeTabMachines;setDefaultIcon("upsindustrialbatterymodule") }
            override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
                super.addInformation(stack,player,list,advanced);list.add(tr("Removable vehicle pack; charge in the EV Pack Charger; crafted empty"))
            }
        })
        Eln.sharedItem.addElement(1+(117 shl 6),GenericItemUsingDamageDescriptor(FRAME,"weldedmachineframe").apply { creativeTab=Eln.creativeTabOresMaterials })
        Eln.sharedItemStackOne.addElement(2+(117 shl 6),TransportItem(FORKLIFT,false))
        Eln.sharedItemStackOne.addElement(3+(117 shl 6),TransportItem(PALLET,true))
    }
}
class TransportItem(name: String,val pallet: Boolean): GenericItemUsingDamageDescriptor(name) {
    init { creativeTab=Eln.creativeTabMachines }
    override fun onItemUse(stack: ItemStack?,player: Player?,world: Level?,x: Int,y: Int,z: Int,side: Int,vx: Float,vy: Float,vz: Float): Boolean {
        if(stack==null || stack.isEmpty || player==null || world==null)return false
        val at=BlockPos(x,y,z).relative(net.minecraft.core.Direction.from3DDataValue(side))
        val entity=if(pallet)PalletEntity(PalletEntity.TYPE.get(),world) else ForkliftEntity(ForkliftEntity.TYPE.get(),world)
        entity.moveTo(at.x+.5,at.y.toDouble(),at.z+.5,player.yRot,0f)
        if(!world.noCollision(entity,entity.boundingBox))return false
        if(!world.isClientSide) {
            if(entity is PalletEntity)entity.unpack(stack)
            if(!world.addFreshEntity(entity))return false
            if(!player.abilities.instabuild || pallet && stack.has(net.minecraft.core.component.DataComponents.CONTAINER))stack.shrink(1)
        }
        return true
    }
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        if(pallet)list.add(tr("27 equipment slots; right-click to open; sneak empty-handed to pack up"))
        else {
            list.add(tr("Sneak with a traction pack to install; sneak empty-handed to remove it while parked"))
            list.add(tr("Right-click to drive; movement keys steer; Page Up/Down raise/lower; G loads/unloads a pallet"))
        }
    }
}
