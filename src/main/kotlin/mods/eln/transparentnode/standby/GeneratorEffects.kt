package mods.eln.transparentnode.standby

import mods.eln.misc.Direction
import net.minecraft.world.phys.Vec3

object GeneratorEffects {
    fun running(status: String)=status=="RUNNING" || status=="COOLDOWN"
    fun volume(status: String,watts: Double)=if(running(status))(.60+.18*(watts/10_000).coerceIn(0.0,1.0)).toFloat() else 0f
    /** OBJ +Z faces front. Rotate the pipe outlet with the cabinet, not the world axes. */
    fun exhaust(front: Direction,tier: GeneratorTier=GeneratorTier.SMALL)=if(tier==GeneratorTier.SMALL)front.rotateFromXN(Vec3(.21,.475,.30)) else
        mods.eln.transparentnode.worklight.WorkLightOptics.world(front,Vec3((tier.width-1)/2+.28,tier.height-.50,(tier.depth-1)/2.0+.30))
}
