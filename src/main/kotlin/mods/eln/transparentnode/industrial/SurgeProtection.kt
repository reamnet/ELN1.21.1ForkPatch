package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LightningBolt
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

@EventBusSubscriber(modid=Eln.MODID)
object SurgeProtection {
    @SubscribeEvent fun lightning(event: EntityJoinLevelEvent) {
        val bolt=event.entity as? LightningBolt ?: return
        val world=event.level as? ServerLevel ?: return
        // Lightning is rare. Visit existing nodes only; never load/generate terrain to find a protector.
        val nodes=NodeManager.instance?.nodes?.asSequence()?.filterIsInstance<TransparentNode>() ?: return
        val candidates=nodes.mapNotNull { node->
            val e=node.element ?: return@mapNotNull null
            val c=e.coordinate();if(c.world()!==world || world.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)==null)return@mapNotNull null
            when(e) {
                is IndustrialElement->if(e.kind==IndustrialKind.ARRESTER && e.enabled && e.surgeRemaining>=40000)Pair(c.pos.distSqr(bolt.blockPosition()),{ e.absorbLightning();true }) else null
                is mods.eln.utility.DistributionPoleElement->if(e.arrester.fitted && e.arrester.remaining>=40000)Pair(c.pos.above(7).distSqr(bolt.blockPosition()),{ e.arrester.lightning() }) else null
                else->null
            }
        }.filter { it.first<=64 }.minByOrNull { it.first } ?: return
        if(candidates.second())bolt.setVisualOnly(true)
    }
}
