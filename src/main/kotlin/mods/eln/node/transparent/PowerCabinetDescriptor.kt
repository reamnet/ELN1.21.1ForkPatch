package mods.eln.node.transparent

import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

/** A separate wall-cabinet variant keeps existing floor machines and their saved wiring intact. */
open class PowerCabinetDescriptor(
    name: String, elementClass: Class<*>, renderClass: Class<*>,
    val wallMounted: Boolean, meta: EntityMetaTag = EntityMetaTag.Basic
) : TransparentNodeDescriptor(name, elementClass, renderClass, meta) {
    override val frontType get() = if (wallMounted) TransparentNode.FrontType.BlockSideInv else super.frontType
    override fun mustHaveFloor() = !wallMounted
    override fun mustHaveWallFrontInverse() = wallMounted

    override fun checkCanPlace(coord: Coordinate?, front: Direction): String? {
        if (wallMounted && front.isY) return tr("Place this cabinet against a vertical wall")
        return super.checkCanPlace(coord, front)
    }

    fun connectionPlane(front: Direction) = if (wallMounted) front.inverse else Direction.YN
    fun acceptsCable(front: Direction, side: Direction, lrdu: LRDU) =
        side.getLRDUGoingTo(connectionPlane(front)) == lrdu

    fun checkWallSupport(element: TransparentNodeElement) {
        if (!wallMounted) return
        val origin = element.node?.coordinate ?: return
        val world = origin.world()
        val support = origin.pos.relative(element.front.inverse.toFacing())
        if (world is net.minecraft.server.level.ServerLevel &&
            world.chunkSource.getChunkNow(support.x shr 4, support.z shr 4) == null) return
        // Legacy neighborOpaque caches mark every side opaque, including air.
        // Check the actual supporting block for these new wall variants.
        if (!mods.eln.node.SurfaceMounting.supports(world,origin.pos,element.front.inverse)) element.selfDestroy()
    }

    /** Local block coordinates: back plate touches the support; the case projects 0.33 blocks. */
    fun wallBox(front: Direction): AABB = when (front) {
        Direction.XN -> AABB(.67, .04, .04, 1.0, .96, .96)
        Direction.XP -> AABB(0.0, .04, .04, .33, .96, .96)
        Direction.ZN -> AABB(.04, .04, .67, .96, .96, 1.0)
        Direction.ZP -> AABB(.04, .04, 0.0, .96, .96, .33)
        else -> error("Wall cabinets need a horizontal front")
    }

    override fun addCollisionBoxesToList(par5AxisAlignedBB: AABB, list: MutableList<AABB?>, world: Level?, x: Int, y: Int, z: Int) {
        if (!wallMounted) { super.addCollisionBoxesToList(par5AxisAlignedBB, list, world, x, y, z); return }
        val tile = world?.getBlockEntity(BlockPos(x, y, z)) as? TransparentNodeEntity
        val front = tile?.elementRender?.front ?: (tile?.node as? TransparentNode)?.element?.front ?: return
        val box = wallBox(front).move(x.toDouble(), y.toDouble(), z.toDouble())
        if (par5AxisAlignedBB.intersects(box)) list.add(box)
    }
}
