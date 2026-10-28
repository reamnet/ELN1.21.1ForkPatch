package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.electricalpole.ElectricalPoleElement
import mods.eln.misc.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.utilitylight.StreetLightMount
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** Removable, linear secondary load. Never connects a pole's primary to its secondary. */
class PoleStreetlightAttachment private constructor(private val owner: GridElement) {
    var installed=false;private set
    var enabled=true;private set
    var automatic=false;private set
    var supply=0;private set // 0 chooses an available secondary, 120/240 pin a circuit.
    var yaw=0;private set
    var height=0;private set
    var pitch=0;private set
    var light=0;private set
    private var selected=240
    private val low=owner.gridLoads(40).first()
    private val high=owner.gridLoads(42).first()
    private val r120=Resistor(low,null).apply { resistance=1e19 }
    private val r240=Resistor(high,null).apply { resistance=1e19 }
    val power get()=r120.power+r240.power
    val volts get()=if(selected==120)low.voltage else high.voltage
    init { owner.electricalComponentList.addAll(listOf(r120,r240));owner.electricalProcessList.add(IProcess(::step)) }
    fun install(stack: ItemStack,creative: Boolean): Boolean {
        if(installed || UtilityItems.part(stack)!=3)return false
        installed=true;if(!creative)stack.shrink(1);owner.needPublish();return true
    }
    fun remove(): ItemStack {
        if(!installed)return ItemStack.EMPTY
        installed=false;step(0.0);owner.needPublish();return Eln.findItemStack(NAME,1)
    }
    fun settings(on: Boolean,dusk: Boolean,voltage: Int,yaw: Int,height: Int,pitch: Int): Boolean {
        if(voltage !in listOf(0,120,240) || !StreetLightMount.validYaw(yaw) || !StreetLightMount.validStep(height) || !StreetLightMount.validPitch(pitch))return false
        enabled=on;automatic=dusk;supply=voltage;this.yaw=yaw;this.height=height;this.pitch=pitch;owner.needPublish();return true
    }
    private fun step(dt: Double) {
        if(!installed && r120.resistance>1e18 && r240.resistance>1e18)return
        val world=DimensionIds.serverLevel(owner.coordinate().dimension)
        if(supply!=0)selected=supply else {
            val a=abs(low.voltage)/120;val b=abs(high.voltage)/240
            if(a>b*1.25 && a>.02)selected=120 else if(b>a*1.25 && b>.02)selected=240
        }
        val on=installed && enabled && (!automatic || world?.skyDarken?.let { it>=4 }!=false)
        r120.resistance=if(on && selected==120)120.0*120/70 else 1e19
        r240.resistance=if(on && selected==240)240.0*240/70 else 1e19
        val next=if(on)(15*(power/70).coerceIn(0.0,1.0)).toInt() else 0
        if(next!=light) { light=next;owner.needPublish() }
        if(next>0 && world!=null) {
            val p=StreetLightMount.adjust(net.minecraft.world.phys.Vec3(-1.15,5.2,0.0),owner.front,height,yaw,pitch)
            val c=owner.coordinate().newWithOffset(kotlin.math.floor(p.x+.5).toInt(),kotlin.math.floor(p.y+.5).toInt(),kotlin.math.floor(p.z+.5).toInt())
            if(world.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)!=null)mods.eln.lightblock.LightBlockEntity.addLight(c,next,5)
        }
    }
    fun interact(player: Player): Boolean {
        val item=player.mainHandItem
        if(UtilityItems.part(item)==3) {
            install(item,player.abilities.instabuild)
            if(player is ServerPlayer)PoleStreetlightNetwork.open(player,owner)
            return true
        }
        if(installed && item.isEmpty && player.isShiftKeyDown) {
            if(player is ServerPlayer)PoleStreetlightNetwork.open(player,owner)
            return true
        }
        return false
    }
    fun write(tag: CompoundTag) {
        val d=CompoundTag();d.putBoolean("installed",installed);d.putBoolean("enabled",enabled);d.putBoolean("automatic",automatic);d.putInt("supply",supply)
        d.putInt("yaw",yaw);d.putInt("height",height);d.putInt("pitch",pitch);tag.put("poleStreetlightAttachment",d)
    }
    fun read(tag: CompoundTag) {
        val d=tag.getCompound("poleStreetlightAttachment");installed=d.getBoolean("installed")
        settings(!d.contains("enabled") || d.getBoolean("enabled"),d.getBoolean("automatic"),d.getInt("supply"),d.getInt("yaw"),d.getInt("height"),d.getInt("pitch"))
    }
    fun display()=PoleStreetlightDisplay(installed,enabled,automatic,supply,yaw,height,pitch,light,volts,power)
    companion object {
        const val NAME="Pole Streetlight Attachment"
        fun supports(owner: GridElement)=when(owner) {
            is DistributionPoleElement->!owner.kind.underground && !owner.kind.light
            is TransmissionElement->!owner.kind.station
            is ElectricalPoleElement->true
            else->false
        }
        fun create(owner: GridElement)=if(supports(owner) && owner.gridLoads(40).isNotEmpty() && owner.gridLoads(42).isNotEmpty())PoleStreetlightAttachment(owner) else null
    }
}

data class PoleStreetlightDisplay(val installed: Boolean=false,val enabled: Boolean=true,val automatic: Boolean=false,val supply: Int=0,
                                 val yaw: Int=0,val height: Int=0,val pitch: Int=0,val light: Int=0,val volts: Double=0.0,val power: Double=0.0) {
    fun write(out: DataOutputStream) {
        out.writeBoolean(installed);out.writeBoolean(enabled);out.writeBoolean(automatic);out.writeInt(supply);out.writeInt(yaw);out.writeInt(height);out.writeInt(pitch);out.writeByte(light);out.writeDouble(volts);out.writeDouble(power)
    }
    companion object { fun read(input: DataInputStream)=PoleStreetlightDisplay(input.readBoolean(),input.readBoolean(),input.readBoolean(),input.readInt(),input.readInt(),input.readInt(),input.readInt(),input.readUnsignedByte(),input.readDouble(),input.readDouble()) }
}
