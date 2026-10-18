package mods.eln.sixnode.lampsocket

import mods.eln.misc.Direction
import mods.eln.node.six.SixNodeDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

/** Surface height belongs to the supporting collision shape, not its full-cube render flag. */
object LampMounting {
    fun partialAllowed(descriptor: SixNodeDescriptor?, side: Direction) = descriptor is LampSocketDescriptor &&
        (side==Direction.YP || side==Direction.YN && descriptor.fixture?.tableLamp==true)
    fun surface(boxes: List<AABB>, side: Direction): Double? {
        val central=boxes.filter { it.minX <= .5 && it.maxX >= .5 && it.minZ <= .5 && it.maxZ >= .5 }
        return when(side) {
            Direction.YP -> central.minOfOrNull { it.minY }
            Direction.YN -> central.maxOfOrNull { it.maxY }
            else -> null
        }
    }
    fun supports(world: BlockGetter,target: BlockPos,side: Direction,descriptor: SixNodeDescriptor?): Boolean {
        if(!partialAllowed(descriptor,side))return false
        val at=target.relative(side.toFacing())
        return surface(world.getBlockState(at).getCollisionShape(world,at).toAabbs(),side)!=null
    }
    fun inset(world: BlockGetter,target: BlockPos,side: Direction,descriptor: SixNodeDescriptor?): Double {
        if(!partialAllowed(descriptor,side))return 0.0
        val at=target.relative(side.toFacing())
        val height=surface(world.getBlockState(at).getCollisionShape(world,at).toAabbs(),side) ?: return 0.0
        return (if(side==Direction.YP) height else 1.0-height).coerceIn(-.5,1.0)
    }
}
