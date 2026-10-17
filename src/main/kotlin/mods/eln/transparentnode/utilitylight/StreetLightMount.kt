package mods.eln.transparentnode.utilitylight

import kotlin.math.floor
import mods.eln.misc.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/** Placement math shared by the adjustable fixture and its world light source. */
object StreetLightMount {
    const val MIN_STEP = -4
    const val MAX_STEP = 1
    const val DEFAULT_HEIGHT = 2.30
    const val STEP_METRES = .25
    fun validStep(step: Int) = step in MIN_STEP..MAX_STEP
    fun offset(step: Int) = step.coerceIn(MIN_STEP, MAX_STEP) * STEP_METRES
    fun height(step: Int) = DEFAULT_HEIGHT + offset(step)
    fun lightY(step: Int) = floor(height(step)).toInt()
    fun validYaw(degrees: Int) = degrees in 0..359
    fun turn(degrees: Int, movement: Int) = Math.floorMod(degrees + movement, 360)
    fun headPosition(front: Direction, step: Int, yaw: Int): Vec3 =
        front.rotateFromXN(Vec3(-1.095, height(step), 0.0).yRot(Math.toRadians(yaw.toDouble()).toFloat()))
    fun lightOffset(front: Direction, step: Int, yaw: Int): BlockPos {
        val head = headPosition(front, step, yaw)
        // Rendering is centred on the block horizontally; light blocks use corner coordinates.
        return BlockPos.containing(head.x + .5, head.y, head.z + .5)
    }
}
