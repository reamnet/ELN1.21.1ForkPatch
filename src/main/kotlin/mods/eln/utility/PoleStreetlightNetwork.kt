package mods.eln.utility

import mods.eln.gridnode.GridElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.PayloadRegistrar

data class PoleStreetlightControl(val pos: BlockPos,val remove: Boolean,val on: Boolean,val dusk: Boolean,val supply: Int,val yaw: Int,val height: Int,val pitch: Int): CustomPacketPayload {
    override fun type()=TYPE
    companion object {
        val TYPE=CustomPacketPayload.Type<PoleStreetlightControl>(ResourceLocation.fromNamespaceAndPath("eln","pole_streetlight_control"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf,PoleStreetlightControl> = StreamCodec.of({ b,p->
            b.writeBlockPos(p.pos);b.writeBoolean(p.remove);b.writeBoolean(p.on);b.writeBoolean(p.dusk);b.writeInt(p.supply);b.writeInt(p.yaw);b.writeInt(p.height);b.writeInt(p.pitch)
        },{ b->PoleStreetlightControl(b.readBlockPos(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readInt(),b.readInt(),b.readInt(),b.readInt()) })
    }
}
data class PoleStreetlightOpen(val pos: BlockPos): CustomPacketPayload {
    override fun type()=TYPE
    companion object {
        val TYPE=CustomPacketPayload.Type<PoleStreetlightOpen>(ResourceLocation.fromNamespaceAndPath("eln","pole_streetlight_open"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf,PoleStreetlightOpen> = StreamCodec.of({ b,p->b.writeBlockPos(p.pos) },{ b->PoleStreetlightOpen(b.readBlockPos()) })
    }
}
object PoleStreetlightNetwork {
    fun open(player: ServerPlayer,owner: GridElement) { PacketDistributor.sendToPlayer(player,PoleStreetlightOpen(owner.coordinate().pos)) }
    fun register(registrar: PayloadRegistrar) {
        registrar.playToClient(PoleStreetlightOpen.TYPE,PoleStreetlightOpen.CODEC) { p,c->c.enqueueWork { PoleStreetlightScreen.open(p.pos) } }
        registrar.playToServer(PoleStreetlightControl.TYPE,PoleStreetlightControl.CODEC) { p,c->c.enqueueWork {
            val player=c.player() as? ServerPlayer ?: return@enqueueWork
            val world=player.serverLevel();val at=p.pos
            if(player.distanceToSqr(at.x+.5,at.y+.5,at.z+.5)>64 || world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)==null)return@enqueueWork
            val owner=((world.getBlockEntity(at) as? TransparentNodeEntity)?.node as? TransparentNode)?.element as? GridElement ?: return@enqueueWork
            val lamp=owner.poleStreetlight?.takeIf { it.installed } ?: return@enqueueWork
            if(p.remove) { val item=lamp.remove();if(!player.inventory.add(item))player.drop(item,false) }
            else lamp.settings(p.on,p.dusk,p.supply,p.yaw,p.height,p.pitch)
        } }
    }
}
