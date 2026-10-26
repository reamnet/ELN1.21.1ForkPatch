package mods.eln.client.terrain

import com.mojang.blaze3d.platform.NativeImage
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.node.NodeBlock
import mods.eln.node.NodeBlockEntity
import mods.eln.node.six.SixNodeElementRender
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.LevelEvent
import java.awt.Color
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Optional DH 3.3 object API bridge. No mandatory DH classes or server work.
 * Visited device geometry is cached independently of vanilla chunk lifetime. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object DistantModels {
    private data class Key(val pos: BlockPos,val face: Int)
    private data class Entry(val boxes: List<DistantBox>,var group: Any?=null)
    private val entries=LinkedHashMap<Key,Entry>()
    private val colors=HashMap<net.minecraft.resources.ResourceLocation,Int>()
    private var world: ClientLevel?=null
    private var bridge: Bridge?=null
    private var register: Any?=null
    private var save: File?=null
    private var clock=0
    private var cameraChunk=Long.MIN_VALUE
    private var lastView=-1
    private var dirty=false
    private var failed=false
    private val revisions=LinkedHashMap<Key,Long>()
    private val wireSignatures=HashMap<Key,List<List<Vec3>>>()
    private var sequence=0L
    private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(2048),java.util.concurrent.ThreadFactory { task->Thread(task,"ELN distant geometry").apply { isDaemon=true;priority=Thread.MIN_PRIORITY } },java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
    private val mc get()=Minecraft.getInstance()
    val available get()=!failed && ModList.get().isLoaded("distanthorizons")

    internal fun capture(owner: Any,node: NodeBlockEntity,mesh: TerrainMesh) {
        if(!available || node.level !is ClientLevel)return
        selectWorld(node.level as ClientLevel)
        val key=Key(node.blockPos,if(owner is SixNodeElementRender)owner.side.int else -1)
        val level=world
        val revision=++sequence;revisions[key]=revision
        while(revisions.size>4096)revisions.remove(revisions.keys.first())
        val palette=mesh.batches.map { it.material }.distinct().associateWith(::textureColor)
        worker.execute {
            val boxes=DistantGeometry.mesh(mesh) { palette[it] ?: -1 }
            mc.execute { if(world===level && revisions[key]==revision)put(key,boxes) }
        }
    }
    fun cables(node: NodeBlockEntity,points: List<List<Vec3>>) {
        if(!available || node.level !is ClientLevel)return
        selectWorld(node.level as ClientLevel)
        val key=Key(node.blockPos,6)
        if(wireSignatures[key]==points)return
        wireSignatures[key]=points
        put(key,points.flatMap { DistantGeometry.wire(it.map { p->p.add(.5,.5,.5) }) })
    }
    fun cover(node: NodeBlockEntity,state: net.minecraft.world.level.block.state.BlockState) {
        if(!available || node.level !is ClientLevel)return
        selectWorld(node.level as ClientLevel)
        val key=Key(node.blockPos,7)
        val boxes=if(state.isAir)emptyList() else {
            val color=state.getMapColor(node.level!!,node.blockPos).col or 0xff000000.toInt()
            listOf(DistantBox(AABB(0.0,0.0,0.0,1.0,1.0,1.0),color))
        }
        put(key,boxes)
    }
    private fun put(key: Key,boxes: List<DistantBox>) {
        entries.remove(key)?.let(::remove)
        if(boxes.isNotEmpty())entries[key]=Entry(boxes)
        trim()
        dirty=true
    }
    private fun trim() {
        while(entries.size>2048 || entries.values.sumOf { it.boxes.size }>150_000) {
            val first=entries.entries.first();remove(first.value);entries.remove(first.key)
            revisions.remove(first.key);wireSignatures.remove(first.key)
        }
        dirty=true
    }
    internal fun forget(owner: Any,node: NodeBlockEntity) {
        val key=Key(node.blockPos,if(owner is SixNodeElementRender)owner.side.int else -1)
        revisions.remove(key);entries.remove(key)?.let(::remove);dirty=true
    }
    internal fun invalidatePalette() { colors.clear() }
    private fun textureColor(material: TerrainMaterial): Int=colors.getOrPut(material.texture) {
        if(material.texture==FixedFunction.WHITE)0xffffffff.toInt() else try {
            mc.resourceManager.getResource(material.texture).orElseThrow().open().use { stream->NativeImage.read(stream).use { image->
                var r=0L;var g=0L;var b=0L;var weight=0L
                for(y in 0 until image.height step maxOf(1,image.height/16))for(x in 0 until image.width step maxOf(1,image.width/16)) {
                    val c=image.getPixelRGBA(x,y);val a=(c ushr 24) and 255
                    r+=(c and 255)*a;g+=((c ushr 8) and 255)*a;b+=((c ushr 16) and 255)*a;weight+=a
                }
                if(weight==0L)0xff808080.toInt() else 0xff000000.toInt() or ((r/weight).toInt() shl 16) or ((g/weight).toInt() shl 8) or (b/weight).toInt()
            } }
        } catch(_: Exception) { 0xff808080.toInt() }
    }
    internal fun diagnostics()=mapOf("available" to available,"failed" to failed,"bound" to (register!=null),
        "cached" to entries.size,"groups" to entries.values.count { it.group!=null },
        "active" to entries.values.count { entry->entry.group?.let { bridge?.groupType?.getMethod("isActive")?.invoke(it)==true }==true },
        "pending" to worker.queue.size)
    private fun selectWorld(next: ClientLevel) {
        if(world===next)return
        clear();world=next
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!available)return
        val level=mc.level ?: return
        selectWorld(level)
        val center=mc.gameRenderer.mainCamera.position
        val chunk=net.minecraft.world.level.ChunkPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(center.x),net.minecraft.core.SectionPos.blockToSectionCoord(center.z))
        val view=mc.options.effectiveRenderDistance
        if(++clock%20!=0 && chunk==cameraChunk && view==lastView)return
        cameraChunk=chunk;lastView=view
        try {
            val api=bridge ?: Bridge().also { bridge=it }
            if(register==null) {
                val wrapper=api.level(level) ?: return
                val nextRegister=api.levelType.getMethod("getRenderRegister").invoke(wrapper) ?: return
                val folder=api.cacheFolder(wrapper,level)
                register=nextRegister
                save=folder?.let { File(it,"eln-objects-v2.dat") }
                load()
            }
            val radius=api.renderDistanceBlocks()
            var created=0
            val iterator=entries.entries.iterator()
            fun isDistant(key: Key)=DistantVisibility.useDistant(key.pos,center,view,
                level.chunkSource.getChunk(key.pos.x shr 4,key.pos.z shr 4,ChunkStatus.FULL,false)!=null)
            val nearby=entries.keys.filter { it.pos.distToCenterSqr(center)<radius*radius }.sortedBy { it.pos.distToCenterSqr(center) }
            val ordered=nearby.filter(::isDistant).take(256).toSet()
            val wanted=ordered+nearby.take(64)
            while(iterator.hasNext()) {
                val (key,entry)=iterator.next()
                val chunk=level.chunkSource.getChunk(key.pos.x shr 4,key.pos.z shr 4,ChunkStatus.FULL,false)
                if(chunk!=null && chunk.getBlockState(key.pos).block !is NodeBlock) {
                    remove(entry);iterator.remove();revisions.remove(key);wireSignatures.remove(key);dirty=true;continue
                }
                if(key !in wanted)remove(entry)
                if(entry.group==null && key in wanted && created<8) {
                    entry.group=api.create(key.pos,key.face,entry.boxes,register!!);created++
                }
                val distance=key.pos.distToCenterSqr(center)
                entry.group?.let { api.groupType.getMethod("setActive",Boolean::class.javaPrimitiveType).invoke(it,distance<radius*radius && key in ordered) }
            }
        } catch(error: Exception) {
            Eln.logger.warn("Distant Horizons object integration disabled after an API error",error)
            failed=true;clear()
        }
    }
    private fun remove(entry: Entry) {
        try { entry.group?.let { group->
            bridge?.let { api->register?.let { api.registerType.getMethod("remove",Long::class.javaPrimitiveType).invoke(it,api.groupType.getMethod("getId").invoke(group)) } }
            (group as? Closeable)?.close()
        }
        } catch(_: Exception) { /* A shutting-down DH level may already own disposal. */ }
        entry.group=null
    }
    private fun load() {
        val file=save?.takeIf { it.isFile } ?: return
        try { DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input->
            require(input.readInt()==2)
            var total=0
            repeat(input.readInt().also { require(it in 0..2048) }) {
                val key=Key(BlockPos.of(input.readLong()),input.readByte().toInt())
                // A junction can own several newly subdivided spans. Accept the
                // same total box budget that the writer keeps, even in one group.
                val count=input.readInt();require(count in 0..150_000);total+=count;require(total<=150_000)
                val boxes=List(count) {
                    val v=DoubleArray(6) { input.readFloat().toDouble().also { require(it.isFinite() && kotlin.math.abs(it)<4096) } }
                    DistantBox(AABB(v[0],v[1],v[2],v[3],v[4],v[5]),input.readInt())
                }
                entries.putIfAbsent(key,Entry(boxes))
            }
        } } catch(error: Exception) { Eln.logger.warn("Ignoring unreadable ELN distant-object cache: {}",error.message) }
        trim()
    }
    private fun store() {
        val file=save ?: return
        if(!dirty)return
        try {
            file.parentFile.mkdirs();val temp=File(file.path+".tmp")
            DataOutputStream(GZIPOutputStream(temp.outputStream().buffered())).use { out->
                out.writeInt(2);out.writeInt(entries.size)
                for((key,entry) in entries) {
                    out.writeLong(key.pos.asLong());out.writeByte(key.face);out.writeInt(entry.boxes.size)
                    for(box in entry.boxes) {
                        val a=box.bounds
                        for(value in listOf(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ))out.writeFloat(value.toFloat())
                        out.writeInt(box.color)
                    }
                }
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)
        } catch(error: Exception) { Eln.logger.warn("Could not save ELN distant objects: {}",error.message) }
    }
    private fun clear() {
        store();entries.values.forEach(::remove);entries.clear();colors.clear();revisions.clear();wireSignatures.clear();worker.queue.clear();register=null;save=null;world=null;dirty=false;cameraChunk=Long.MIN_VALUE;lastView=-1
    }
    @SubscribeEvent fun unload(event: LevelEvent.Unload) { if(event.level===world)clear() }

    private class Bridge {
        private val prefix="com.seibel.distanthorizons.api."
        private val delayed=Class.forName(prefix+"DhApi\$Delayed")
        val levelType=Class.forName(prefix+"interfaces.world.IDhApiLevelWrapper")
        val groupType=Class.forName(prefix+"interfaces.render.IDhApiRenderableBoxGroup")
        val registerType=Class.forName(prefix+"interfaces.render.IDhApiCustomRenderRegister")
        private val vector=Class.forName(prefix+"objects.math.DhApiVec3d")
        private val material=Class.forName(prefix+"enums.rendering.EDhApiBlockMaterial")
        private val metal=material.getField("METAL").get(null)
        private val boxType=Class.forName(prefix+"objects.render.DhApiRenderableBox")
        private fun vec(x: Double,y: Double,z: Double)=vector.getConstructor(Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType).newInstance(x,y,z)
        fun level(level: ClientLevel): Any? {
            val proxy=delayed.getField("worldProxy").get(null) ?: return null
            val proxyType=Class.forName(prefix+"interfaces.world.IDhApiWorldProxy")
            if(proxyType.getMethod("worldLoaded").invoke(proxy)!=true)return null
            val unsafe=Class.forName(prefix+"interfaces.IDhApiUnsafeWrapper")
            // In an integrated world getAllLoadedLevelWrappers returns the
            // server dimensions. This accessor exposes the current client level.
            val client=proxyType.getMethod("getSinglePlayerLevel").invoke(proxy)
            if(client!=null && unsafe.getMethod("getWrappedMcObject").invoke(client)===level)return client
            val levels=proxyType.getMethod("getAllLoadedLevelWrappers").invoke(proxy) as Iterable<*>
            return levels.firstOrNull { it!=null && unsafe.getMethod("getWrappedMcObject").invoke(it)===level }
        }
        fun cacheFolder(wrapper: Any,level: ClientLevel): File? {
            // DH 3.3.3's integrated-world save structure casts a client wrapper
            // to a server wrapper. Cache failure must never disable rendering.
            val direct=runCatching { levelType.getMethod("getDhSaveFolder").invoke(wrapper) as? File }.getOrNull()
            if(direct!=null)return direct
            val id=level.dimension().location().toString().replace(Regex("[^a-zA-Z0-9_-]"),"_")
            return mc.singleplayerServer?.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                ?.resolve("data/eln-distant")?.resolve(id)?.toFile()
        }
        fun renderDistanceBlocks(): Double {
            val config=delayed.getField("configs").get(null) ?: return 1024.0
            val graphics=Class.forName(prefix+"interfaces.config.IDhApiConfig").getMethod("graphics").invoke(config)
            val distance=Class.forName(prefix+"interfaces.config.client.IDhApiGraphicsConfig").getMethod("chunkRenderDistance").invoke(graphics)
            val chunks=Class.forName(prefix+"interfaces.config.IDhApiConfigValue").getMethod("getValue").invoke(distance) as Number
            return chunks.toDouble().coerceIn(2.0,4096.0)*16.0
        }
        fun create(pos: BlockPos,face: Int,boxes: List<DistantBox>,register: Any): Any {
            val factory=delayed.getField("customRenderObjectFactory").get(null) ?: error("DH object factory unavailable")
            val constructor=boxType.getConstructor(vector,vector,Color::class.java,material)
            val data=boxes.map { val a=it.bounds;constructor.newInstance(vec(a.minX,a.minY,a.minZ),vec(a.maxX,a.maxY,a.maxZ),Color(it.color,true),metal) }
            val group=Class.forName(prefix+"interfaces.render.IDhApiCustomRenderObjectFactory").getMethod("createRelativePositionedGroup",String::class.java,vector,List::class.java)
                .invoke(factory,"eln:object_${pos.asLong()}_$face",vec(pos.x.toDouble(),pos.y.toDouble(),pos.z.toDouble()),data)
            groupType.getMethod("setActive",Boolean::class.javaPrimitiveType).invoke(group,false)
            groupType.getMethod("setSsaoEnabled",Boolean::class.javaPrimitiveType).invoke(group,false)
            registerType.getMethod("add",groupType).invoke(register,group)
            return group
        }
    }
}
