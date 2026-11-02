package mods.eln.vehicle
import io.netty.buffer.ByteBuf
import mods.eln.network.*
class VehicleControls(var forward: Int=0,var turn: Int=0): IMessage {
    override fun fromBytes(buf: ByteBuf) { forward=buf.readByte().toInt().coerceIn(-1,1);turn=buf.readByte().toInt().coerceIn(-1,1) }
    override fun toBytes(buf: ByteBuf) { buf.writeByte(forward);buf.writeByte(turn) }
    class Handler: IMessageHandler<VehicleControls,IMessage?> {
        override fun onMessage(message: VehicleControls,ctx: MessageContext?): IMessage? { val p=ctx?.serverHandler?.player ?: return null;(p.vehicle as? RoadVehicleEntity)?.controls(p,message.forward.toDouble(),message.turn.toDouble());return null }
    }
    companion object { @JvmStatic fun register() { ElnNetwork.register("road_vehicle_controls",VehicleControls::class.java,java.util.function.Supplier { VehicleControls() },Handler(),true) } }
}
