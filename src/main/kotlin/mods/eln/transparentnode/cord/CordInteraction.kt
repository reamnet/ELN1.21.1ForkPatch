package mods.eln.transparentnode.cord

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

class CordDescriptor(name: String): GenericItemUsingDamageDescriptor(name,"flexibleappliancecord") {
    init { creativeTab=Eln.creativeTabCables }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Experimental flexible cord: 8 block reach"))
        list.add(tr("Use on a wall outlet, then on a small appliance"))
        list.add(tr("Sneak with an empty hand to carry a plugged appliance"))
        list.add(tr("Sneak-use a cord on the appliance to unplug it"))
    }
}

@EventBusSubscriber(modid=Eln.MODID)
object CordInteraction {
    @SubscribeEvent fun use(event: PlayerInteractEvent.RightClickBlock) {
        val player=event.entity
        if(event.hand!=InteractionHand.MAIN_HAND)return
        val held=player.mainHandItem
        val tool=Utils.getItemObject(held) is CordDescriptor
        if(player.level().isClientSide) {
            if(tool) { event.isCanceled=true;event.cancellationResult=InteractionResult.SUCCESS }
            return
        }
        val p=player as? ServerPlayer ?: return
        val c=Coordinate(event.pos.x,event.pos.y,event.pos.z,p.level())
        val machine=(NodeManager.instance?.getNodeFromCoordonate(c) as? TransparentNode)?.element
        val device=machine as? CordDevice
        if(!tool) {
            if(player.isShiftKeyDown && held.isEmpty && device?.cordPortable==true && device.cord.plugged) {
                device.cord.carry(p);event.isCanceled=true;event.cancellationResult=InteractionResult.SUCCESS
            }
            return
        }
        event.isCanceled=true;event.cancellationResult=InteractionResult.SUCCESS
        if(device!=null && device.cord.plugged && player.isShiftKeyDown) { device.cord.unplug(p);return }
        if(machine is OutletElement) {
            held.tagCompound=CompoundTag().apply { put("cordSelection",machine.anchor().write()) }
            Utils.sendMessage(player,tr("Cord plugged into outlet; select a small appliance"));return
        }
        val selected=held.tagCompound?.getCompound("cordSelection")?.let(CordAnchor::read)
        if(device?.cordPortable!=true) { Utils.sendMessage(player,tr("Connect this cord to a small 120 V appliance or box fan"));return }
        if(selected==null) { Utils.sendMessage(player,tr("Select a wall outlet first"));return }
        val consume=!player.abilities.instabuild
        if(device.cord.attach(selected,consume)) {
            held.tagCompound=null
            if(consume)held.shrink(1)
            Utils.sendMessage(player,tr("Cord connected; sneak with an empty hand to move the appliance"))
        } else Utils.sendMessage(player,tr("Could not connect: check cord reach, outlet occupancy and the existing plug"))
    }
    fun heldAnchor(stack: ItemStack): CordAnchor? {
        val n=stack.tagCompound ?: return null
        return if(n.contains("carriedAppliance"))CordAnchor.read(n.getCompound("carriedAppliance").getCompound("applianceCord"))
        else CordAnchor.read(n.getCompound("cordSelection"))
    }
}
