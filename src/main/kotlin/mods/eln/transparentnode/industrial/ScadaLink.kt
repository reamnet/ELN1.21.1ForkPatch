package mods.eln.transparentnode.industrial

import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.utility.*
import mods.eln.transparentnode.switchgear.SwitchgearElement
import net.minecraft.core.BlockPos

/** Explicit addresses, same world, 256-block range. Never resolve an unloaded chunk. */
object ScadaLink {
    private fun target(owner: IndustrialElement,address: Long): TransparentNodeElement? {
        if(address==Long.MIN_VALUE)return null
        val at=BlockPos.of(address);val c=owner.coordinate()
        if(at.distSqr(c.pos)>256.0*256.0)return null
        val world=mods.eln.misc.DimensionIds.serverLevel(c.dimension) ?: return null
        if(world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)==null)return null
        return NodeManager.instance?.getTransparentNodeFromCoordinate(Coordinate(at.x,at.y,at.z,world))
    }
    fun read(owner: IndustrialElement,address: Long,circuit: Int): String {
        val device=target(owner,address) ?: return tr("Target unavailable or chunk unloaded")
        if(device is BreakerPanelElement) {
            val c=device.circuits.getOrNull(circuit) ?: return tr("Circuit unavailable")
            return tr("Circuit %1$: %2$ V / %3$ A\nBreaker: %4$ A; %5$",circuit+1,c.load.voltage.toInt(),String.format(java.util.Locale.ROOT,"%.2f",c.contacts[c.bus].current),c.rating.toInt(),if(c.breaker.tripped)tr("Tripped") else if(c.breaker.enabled)tr("Enabled") else tr("Disabled"))
        }
        val readings=device.getWaila().entries.take(4).joinToString("\n") { "${it.key}: ${it.value}" }
        return tr("%1$\n%2$",device.transparentNodeDescriptor.name,readings)
    }
    fun command(owner: IndustrialElement,address: Long,circuit: Int,closed: Boolean): Boolean {
        if(owner.kind!=IndustrialKind.SCADA || !owner.enabled || kotlin.math.abs(owner.input.voltage)<90)return false
        val device=target(owner,address) ?: return false
        val success=when(device) {
            is HouseServiceElement->device.setRemoteDisconnect(!closed)
            is BreakerPanelElement->device.remoteCircuit(circuit,closed)
            is DistributionPoleElement->device.remoteSwitch(closed)
            is SwitchgearElement->{ device.setEnabled(closed);true }
            is IndustrialElement->if(device.kind==IndustrialKind.SCADA)false else { device.setEnabled(closed);true }
            is mods.eln.api.media.RemoteSwitchableDevice->{ device.setRemoteEnabled(closed);true }
            else->false
        }
        owner.needPublish();return success
    }
}
