package mods.eln.transparentnode.industrial

import io.netty.buffer.ByteBuf
import mods.eln.network.*

class ForkliftControls(var forward: Int=0,var turn: Int=0,var lift: Int=0,var pickup: Boolean=false): IMessage {
    override fun fromBytes(buf: ByteBuf) { forward=buf.readByte().toInt().coerceIn(-1,1);turn=buf.readByte().toInt().coerceIn(-1,1);lift=buf.readByte().toInt().coerceIn(-1,1);pickup=buf.readBoolean() }
    override fun toBytes(buf: ByteBuf) { buf.writeByte(forward);buf.writeByte(turn);buf.writeByte(lift);buf.writeBoolean(pickup) }
    class Handler: IMessageHandler<ForkliftControls,IMessage?> {
        override fun onMessage(message: ForkliftControls,ctx: MessageContext?): IMessage? {
            val player=ctx?.serverHandler?.player ?: return null
            (player.vehicle as? ForkliftEntity)?.controls(player,message.forward.toDouble(),message.turn.toDouble(),message.lift.toDouble(),message.pickup)
            return null
        }
    }
    companion object {
        @JvmStatic fun register() { ElnNetwork.register("forklift_controls",ForkliftControls::class.java,java.util.function.Supplier { ForkliftControls() },Handler(),true) }
    }
}
