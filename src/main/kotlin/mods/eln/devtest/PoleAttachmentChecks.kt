package mods.eln.devtest

import com.google.gson.Gson
import mods.eln.Eln
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.misc.Direction
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.utility.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** Opt-in localhost multiplayer fixture, invoked only by the media verification campaign. */
object PoleAttachmentChecks {
    private val poles=linkedMapOf<String,GridElement>()
    @JvmStatic fun install(world: ServerLevel,player: ServerPlayer) {
        val f=NativeCampaignFixtures(world,player)
        for((index,kind) in listOf(DistributionKind.FEEDER_SINGLE,DistributionKind.FEEDER_SINGLE,DistributionKind.UNDERGROUND_SECONDARY_TRANSFORMER_SINGLE).withIndex()) {
            val at=BlockPos(264+index*10,80,256)
            val p=f.placeMachine(Eln.findItemStack(kind.title,1),at) as DistributionPoleElement
            p.front=Direction.ZP
            if(kind.transformer) { p.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[1],1));p.inventoryChange(p.inventory) }
            p.reconnect()
            // Production placed cable feeds the primary riser at the base.
            if(kind.transformer) {
                val from=poles.getValue("pole0");val cable=Eln.sixNodeItem.getDescriptor(f.spool()) as mods.eln.sixnode.electricalcable.UtilityCableDescriptor
                GridLink.addLink(from,p,32,32,cable,20)
            } else {
                val side=p.front.left();val wire=at.relative(side.toFacing())
                f.wire(wire);f.source(wire.relative(side.toFacing()),12470.0)
            }
            check(p.poleStreetlight!!.install(Eln.findItemStack(PoleStreetlightAttachment.NAME,1),true))
            if(index==1)Eln.simulator.mna.addComponent(VoltageSource("external secondary fixture",p.services[2],null).apply { voltage=240.0 })
            p.needPublish();poles["pole$index"]=p
        }
    }
    @JvmStatic fun report(): String=Gson().toJson(poles.mapValues { (_,p)->mapOf(
        "primary" to p.gridLoads(32).map { it.voltage },"secondary" to p.gridLoads(50).map { it.voltage },
        "installed" to p.poleStreetlight!!.installed,"lampWatts" to p.poleStreetlight!!.power,"light" to p.poleStreetlight!!.light
    ) })
}
