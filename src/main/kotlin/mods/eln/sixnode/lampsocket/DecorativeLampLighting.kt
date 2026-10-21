package mods.eln.sixnode.lampsocket

import mods.eln.lightblock.LightBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object DecorativeLampLighting {
    fun update(element: LampSocketElement, style: DecorativeLampStyle, light: Int) {
        val coordinate = element.sixNode!!.coordinate
        val world = coordinate.world() as? ServerLevel ?: return
        val cells = linkedSetOf<BlockPos>()
        for ((index,head) in style.heads.take(element.installedBulbs).withIndex()) {
            val aimHead=style.aimHead(index)
            val adjusted=if(style.aimHeadCount>0)element.aim.point(head,style.aimPivot(aimHead),aimHead) else head
            val inset=LampMounting.inset(world,coordinate.pos,element.side,element.descriptor)
            val source = style.worldPoint(coordinate.toVec3(),element.side,element.front,adjusted.add(-inset,0.0,0.0))
            val sourceCell = BlockPos.containing(source)
            if (world.chunkSource.getChunkNow(sourceCell.x shr 4,sourceCell.z shr 4) != null) cells.add(sourceCell)
            if (style.range == 0) continue
            val central=if(style.aimHeadCount>0)element.aim.rotate(if(element.aim.axialPan)Vec3(-1.0,0.0,0.0) else Vec3(1.0,0.0,0.0),aimHead) else style.beam
            val beams=if(style.wideBeam || style==DecorativeLampStyle.WAREHOUSE) {
                val u=central.cross(if(kotlin.math.abs(central.y)<.9)Vec3(0.0,1.0,0.0) else Vec3(1.0,0.0,0.0)).normalize()
                val v=central.cross(u).normalize()
                listOf(central,central.add(u.scale(.55)).normalize(),central.add(u.scale(-.55)).normalize(),
                    central.add(v.scale(.40)).normalize(),central.add(v.scale(-.40)).normalize())
            } else if(style==DecorativeLampStyle.SCONCE_CYLINDER)listOf(central,Vec3(.15,-1.0,0.0)) else listOf(central)
            for (beam in beams) {
                val direction = style.worldDirection(element.side,element.front,beam)
                var last: BlockPos? = null
                for (step in 1..style.range) {
                    val at = BlockPos.containing(source.add(direction.scale(step.toDouble())))
                    if (world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4) == null) break
                    val state = world.getBlockState(at)
                    if (state.isSolidRender(world,at)) break
                    last = at
                    if(step%4==0)cells.add(at)
                }
                last?.let(cells::add)
            }
        }
        for (cell in cells) LightBlockEntity.addLight(world,cell.x,cell.y,cell.z,light,5)
    }
}
