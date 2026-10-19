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

class CordDescriptor(name: String,val rating: CordRating=CordRating.V120): GenericItemUsingDamageDescriptor(name,"flexibleappliancecord") {
    override fun handleRenderType(item: ItemStack?,type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?)=true
    override fun shouldUseRenderHelper(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?,item: ItemStack?,helper: mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper?)=true
    override fun renderItem(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?,item: ItemStack?,vararg data: Any?)=preserveMatrix {
        mods.eln.client.gl.GL11.glRotatef(155f,0f,1f,0f)
        mods.eln.client.gl.GL11.glScalef(2f,2f,2f)
        mods.eln.client.gl.GL11.glTranslatef(0f,.1f,.32f)
        NemaPlug.drawLocal(rating)
        CordPath.draw(listOf(net.minecraft.world.phys.Vec3(0.0,-.10,-.224),net.minecraft.world.phys.Vec3(.08,-.22,-.14),net.minecraft.world.phys.Vec3(.10,-.35,-.12)),net.minecraft.world.phys.Vec3.ZERO)
    }
    init { creativeTab=Eln.creativeTabCables }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Experimental flexible cord: 8 block reach"))
        list.add(tr("%1$: %2$ V, 20 A",rating.plug,rating.volts.toInt()))
        list.add(tr("Use on a matching wall outlet, then on a lamp or appliance"))
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
        val descriptor=Utils.getItemObject(held) as? CordDescriptor
        val tool=descriptor!=null
        if(player.level().isClientSide) {
            if(tool) { event.isCanceled=true;event.cancellationResult=InteractionResult.SUCCESS }
            return
        }
        val p=player as? ServerPlayer ?: return
        val c=Coordinate(event.pos.x,event.pos.y,event.pos.z,p.level())
        val node=NodeManager.instance?.getNodeFromCoordonate(c)
        val machine=when(node) {
            is TransparentNode -> node.element
            is mods.eln.node.six.SixNode -> {
                val hit=event.hitVec.location
                val direction=Eln.sixNodeBlock.resolveElementSide(p.level(),event.pos,Direction.fromFacing(event.face),
                    (hit.x-event.pos.x).toFloat(),(hit.y-event.pos.y).toFloat(),(hit.z-event.pos.z).toFloat())
                (node.getElement(direction) as? CordDevice) ?: node.sideElementList.filterIsInstance<CordDevice>().singleOrNull()
            }
            else -> null
        }
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
            if(machine.rating!=descriptor!!.rating) { Utils.sendMessage(player,tr("This cord requires a %1$ outlet",descriptor.rating.socket));return }
            held.tagCompound=CompoundTag().apply { put("cordSelection",machine.anchor().write()) }
            Utils.sendMessage(player,tr("Cord plugged into outlet; select a lamp or appliance"));return
        }
        val selected=held.tagCompound?.getCompound("cordSelection")?.let(CordAnchor::read)
        if(device==null) { Utils.sendMessage(player,tr("Connect this cord to a lamp or appliance"));return }
        if(selected!=null && selected.rating!=descriptor!!.rating) { Utils.sendMessage(player,tr("Select a matching outlet first"));return }
        if(selected==null) { Utils.sendMessage(player,tr("Select a wall outlet first"));return }
        val consume=!player.abilities.instabuild
        if(device.cord.attach(selected,consume)) {
            held.tagCompound=null
            if(consume)held.shrink(1)
            Utils.sendMessage(player,tr("Cord connected; sneak with an empty hand to move the appliance"))
        } else Utils.sendMessage(player,tr("Could not connect: check matching voltage, cord reach, outlet occupancy and the existing plug"))
    }
    fun heldAnchor(stack: ItemStack): CordAnchor? {
        val n=stack.tagCompound ?: return null
        return if(n.contains("carriedAppliance"))CordAnchor.read(n.getCompound("carriedAppliance").getCompound("applianceCord"))
        else CordAnchor.read(n.getCompound("cordSelection"))
    }
}
