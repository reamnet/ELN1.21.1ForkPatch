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
        // All bulbs share their mounting surface and often trace the same cells.
        // Query each chunk/block only once in this update, never across ticks:
        // placement, removal and moving obstructions still take effect immediately.
        val queries = LampLightingQueries(
            { world.chunkSource.getChunkNow(it.x shr 4,it.z shr 4) != null },
            { world.getBlockState(it).isSolidRender(world,it) })
        val inset=LampMounting.inset(world,coordinate.pos,element.side,element.descriptor)
        val origin=coordinate.toVec3()
        val directions=HashMap<Int,List<Vec3>>()
        for (index in 0 until minOf(style.heads.size,element.installedBulbs)) {
            val head=style.heads[index]
            val aimHead=style.aimHead(index)
            val adjusted=if(style.aimHeadCount>0)element.aim.point(head,style.aimPivot(aimHead),aimHead) else head
            val dropped=if(style.hangingWarehouse)adjusted.add(element.suspension.drop,0.0,0.0) else adjusted
            val source = style.worldPoint(origin,element.side,element.front,dropped.add(-inset,0.0,0.0))
            val sourceCell = BlockPos.containing(source)
            if (queries.loaded(sourceCell)) cells.add(sourceCell)
            if (style.range == 0) continue
            val beams=directions.getOrPut(if(style.aimHeadCount>0)aimHead else -1) {
                val central=if(style.aimHeadCount>0)element.aim.rotate(if(element.aim.axialPan)Vec3(-1.0,0.0,0.0) else Vec3(1.0,0.0,0.0),aimHead) else style.beam
                val local=if(style.wideBeam || style==DecorativeLampStyle.WAREHOUSE) {
                    val u=central.cross(if(kotlin.math.abs(central.y)<.9)Vec3(0.0,1.0,0.0) else Vec3(1.0,0.0,0.0)).normalize()
                    val v=central.cross(u).normalize()
                    listOf(central,central.add(u.scale(.55)).normalize(),central.add(u.scale(-.55)).normalize(),
                        central.add(v.scale(.40)).normalize(),central.add(v.scale(-.40)).normalize())
                } else if(style==DecorativeLampStyle.SCONCE_CYLINDER)listOf(central,Vec3(.15,-1.0,0.0)) else listOf(central)
                local.map { style.worldDirection(element.side,element.front,it) }
            }
            for (direction in beams) {
                var last: BlockPos? = null
                for (step in 1..style.range) {
                    val at = BlockPos.containing(source.add(direction.scale(step.toDouble())))
                    if (!queries.open(at)) break
                    last = at
                    if(step%4==0)cells.add(at)
                }
                last?.let(cells::add)
            }
        }
        for (cell in cells) LightBlockEntity.addLight(world,cell.x,cell.y,cell.z,light,5)
    }
}

/** Lifetime is one lamp update; no world/chunk is retained. */
internal class LampLightingQueries(private val chunkLoaded: (BlockPos)->Boolean,
                                   private val solid: (BlockPos)->Boolean) {
    private val chunks=it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(2)
    private var blocks: it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap?=null
    private var lastChunk=Long.MIN_VALUE
    private var lastLoaded=false
    fun loaded(at: BlockPos): Boolean {
        val key=net.minecraft.world.level.ChunkPos.asLong(at.x shr 4,at.z shr 4)
        if(key==lastChunk)return lastLoaded
        val saved=chunks.get(key)
        val result=if(saved.toInt()!=0)saved.toInt()==1 else chunkLoaded(at).also { chunks.put(key,if(it)1.toByte() else 2.toByte()) }
        lastChunk=key;lastLoaded=result
        return result
    }
    fun open(at: BlockPos): Boolean {
        if(!loaded(at))return false
        val key=at.asLong()
        val cache=blocks ?: it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(16).also { blocks=it }
        val saved=cache.get(key)
        if(saved.toInt()!=0)return saved.toInt()==1
        return (!solid(at)).also { cache.put(key,if(it)1.toByte() else 2.toByte()) }
    }
}
