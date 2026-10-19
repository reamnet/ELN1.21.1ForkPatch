package mods.eln.transparentnode.standby

import mods.eln.misc.Direction
import net.minecraft.world.phys.Vec3

object GeneratorEffects {
    fun running(status: String)=status=="RUNNING" || status=="COOLDOWN"
    fun volume(status: String,watts: Double)=if(running(status))(.60+.18*(watts/10_000).coerceIn(0.0,1.0)).toFloat() else 0f
    /** OBJ +Z faces front. Rotate the pipe outlet with the cabinet, not the world axes. */
    fun exhaust(front: Direction)=front.rotateFromXN(Vec3(.21,.475,.30))
}
