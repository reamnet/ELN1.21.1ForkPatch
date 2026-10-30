package mods.eln.node

import mods.eln.misc.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** A mounting face needs a physical surface, rather than an opaque full-cube model.
 * Directions point from the device toward its support. Positions remain on the electrical
 * grid; only the mounting plane moves into the unused part of the supporting cell. */
object SurfaceMounting {
    @JvmStatic fun surface(boxes: List<AABB>, side: Direction): Double? {
        val axis=side.toFacing().axis
        val central=boxes.filter { box ->
            net.minecraft.core.Direction.Axis.entries.filter { it!=axis }.all { a ->
                box.min(a)<=.5 && box.max(a)>=.5
            }
        }
        return if(side.toFacing().axisDirection==net.minecraft.core.Direction.AxisDirection.NEGATIVE)
            central.maxOfOrNull { it.max(axis) } else central.minOfOrNull { it.min(axis) }
    }

    @JvmStatic fun supports(world: BlockGetter, target: BlockPos, side: Direction): Boolean {
        val at=target.relative(side.toFacing())
        // A neighboring chunk is unknown during loading, not evidence of missing support.
        if(world is Level && !world.hasChunkAt(at))return true
        val state=world.getBlockState(at)
        if(state.isAir)return false
        if(state.isSolidRender(world,at))return true
        return surface(state.getCollisionShape(world,at).toAabbs(),side)!=null
    }

    @JvmStatic fun inset(world: BlockGetter, target: BlockPos, side: Direction): Double {
        val at=target.relative(side.toFacing())
        if(world is Level && !world.hasChunkAt(at))return 0.0
        val state=world.getBlockState(at)
        if(state.block is NodeBlock || state.block is mods.eln.ghost.GhostBlock || state.isSolidRender(world,at))return 0.0
        val edge=surface(state.getCollisionShape(world,at).toAabbs(),side) ?: return 0.0
        val negative=side.toFacing().axisDirection==net.minecraft.core.Direction.AxisDirection.NEGATIVE
        return (if(negative)1-edge else edge).coerceIn(-.5,1.0)
    }

    @JvmStatic fun offset(world: BlockGetter, target: BlockPos, side: Direction): Vec3 {
        val inset=inset(world,target,side)
        return if(inset==0.0)Vec3.ZERO else Vec3.atLowerCornerOf(side.toFacing().normal).scale(inset)
    }

    /** Both cable halves meet above the higher surface, so a ramp never cuts into a slab. */
    @JvmStatic fun cableRise(ownInset: Double, otherInset: Double)=maxOf(0.0,ownInset-otherInset).toFloat()
}
