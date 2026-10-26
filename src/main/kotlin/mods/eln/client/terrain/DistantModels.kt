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
    private val gridKeys=HashSet<Key>()
    private val colors=HashMap<net.minecraft.resources.ResourceLocation,Int>()
    private var world: ClientLevel?=null
    private var bridge: Bridge?=null
    private val renderBinding=DistantRenderBinding()
    private val register get()=renderBinding.register
    private var save: File?=null
    private var clock=0
    private var cameraChunk=Long.MIN_VALUE
    private var lastView=-1
    private var dirty=false
    private var failed=false
    private var unsupportedApi=false
    private var retryTicks=0
    private var lastError: String?=null
    private var genericRendering: Boolean?=null
    private val revisions=LinkedHashMap<Key,Long>()
    private val wireSignatures=HashMap<Key,List<mods.eln.gridnode.OverheadWire>>()
    private var sequence=0L
    private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(2048),java.util.concurrent.ThreadFactory { task->Thread(task,"ELN distant geometry").apply { isDaemon=true;priority=Thread.MIN_PRIORITY } },java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
    private val mc get()=Minecraft.getInstance()
    val available get()=!unsupportedApi && ModList.get().isLoaded("distanthorizons")

    internal fun capture(owner: Any,node: NodeBlockEntity,mesh: TerrainMesh) {
        if(!available || node.level !is ClientLevel)return
        selectWorld(node.level as ClientLevel)
        val key=Key(node.blockPos,if(owner is SixNodeElementRender)owner.side.int else -1)
        if(owner is mods.eln.gridnode.GridRender)gridKeys+=key else gridKeys.remove(key)
        val level=world
        val revision=++sequence;revisions[key]=revision
        while(revisions.size>4096)revisions.remove(revisions.keys.first())
        val palette=mesh.batches.map { it.material }.distinct().associateWith(::textureColor)
        worker.execute {
            val boxes=DistantGeometry.mesh(mesh) { palette[it] ?: -1 }
            mc.execute { if(world===level && revisions[key]==revision)put(key,boxes) }
        }
    }
    internal fun cables(node: NodeBlockEntity,points: List<mods.eln.gridnode.OverheadWire>) {
        if(!available || node.level !is ClientLevel)return
        selectWorld(node.level as ClientLevel)
        val key=Key(node.blockPos,6)
        gridKeys+=key;gridKeys+=Key(node.blockPos,-1)
        if(wireSignatures[key]==points)return
        wireSignatures[key]=points
        put(key,points.flatMap { DistantGeometry.wire(it.points.map { p->p.add(.5,.5,.5) },it.radius,it.color) })
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
            val key=DistantRetention.victim(entries.keys,gridKeys)
            entries.remove(key)?.let(::remove)
            revisions.remove(key);wireSignatures.remove(key);gridKeys.remove(key)
        }
        dirty=true
    }
    internal fun forget(owner: Any,node: NodeBlockEntity) {
        if(owner is TerrainModels.CableOwner)return // dedicated wire snapshots have their own lifetime/key
        val key=Key(node.blockPos,if(owner is SixNodeElementRender)owner.side.int else -1)
        revisions.remove(key);entries.remove(key)?.let(::remove);gridKeys.remove(key)
        if(owner is mods.eln.gridnode.GridRender) {
            val wire=Key(node.blockPos,6);entries.remove(wire)?.let(::remove);wireSignatures.remove(wire);gridKeys.remove(wire)
        }
        dirty=true
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
        "genericRenderingEnabled" to genericRendering,"bindingGeneration" to renderBinding.generation,"lastError" to lastError,"retryTicks" to retryTicks,"cacheFile" to save?.name,
        "cached" to entries.size,"groups" to entries.values.count { it.group!=null },
        "gridSnapshots" to entries.keys.count { it in gridKeys },
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
        ++clock
        if(retryTicks>0) { retryTicks--;return }
        val center=mc.gameRenderer.mainCamera.position
        val chunk=net.minecraft.world.level.ChunkPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(center.x),net.minecraft.core.SectionPos.blockToSectionCoord(center.z))
        val view=mc.options.effectiveRenderDistance
        if(clock%20!=0 && chunk==cameraChunk && view==lastView)return
        cameraChunk=chunk;lastView=view
        try {
            val api=bridge ?: Bridge().also { bridge=it }
            // Server keys, DH reloads and world setup can replace this registry
            // without replacing Minecraft's ClientLevel. Never retain a dead one.
            if(register==null || !java.lang.Boolean.getBoolean("eln.disableDistantRebind")) {
                val wrapper=api.level(level) ?: return
                val nextRegister=api.levelType.getMethod("getRenderRegister").invoke(wrapper) ?: return
                if(renderBinding.bind(nextRegister) { store();entries.values.forEach(::remove) }) {
                    val folder=api.cacheFolder(wrapper,level)
                    save=folder?.let { File(it,"eln-objects-v2.dat") }
                    load()
                    Eln.logger.info("Distant Horizons object integration bound to {} (generation {}, {} snapshots)",wrapper.javaClass.simpleName,renderBinding.generation,entries.size)
                }
            }
            failed=false
            val enabled=api.genericRenderingEnabled()
            if(enabled==false && genericRendering!=false)Eln.logger.warn("ELN distant poles and wires cannot draw: Distant Horizons generic rendering is disabled. Enable DH Advanced / Graphics / Generic Rendering.")
            genericRendering=enabled
            val radius=api.renderDistanceBlocks()
            var created=0
            val iterator=entries.entries.iterator()
            fun isDistant(key: Key)=DistantVisibility.useDistant(key.pos,center,view,
                level.chunkSource.getChunk(key.pos.x shr 4,key.pos.z shr 4,ChunkStatus.FULL,false)!=null)
            val nearby=entries.keys.filter { it.pos.distToCenterSqr(center)<radius*radius }.sortedBy { it.pos.distToCenterSqr(center) }
            val ordered=DistantRetention.renderOrder(nearby.filter(::isDistant),gridKeys).take(256).toSet()
            val wanted=ordered+nearby.take(64)
            while(iterator.hasNext()) {
                val (key,entry)=iterator.next()
                val chunk=level.chunkSource.getChunk(key.pos.x shr 4,key.pos.z shr 4,ChunkStatus.FULL,false)
                if(chunk!=null && chunk.getBlockState(key.pos).block !is NodeBlock) {
                    remove(entry);iterator.remove();revisions.remove(key);wireSignatures.remove(key);gridKeys.remove(key);dirty=true;continue
                }
                if(key !in wanted)remove(entry)
                if(entry.group==null && key in wanted && created<8) {
                    entry.group=api.create(key.pos,key.face,entry.boxes,register!!);created++
                }
                val distance=key.pos.distToCenterSqr(center)
                entry.group?.let { api.groupType.getMethod("setActive",Boolean::class.javaPrimitiveType).invoke(it,distance<radius*radius && key in ordered) }
            }
        } catch(error: Exception) {
            val message="${error.javaClass.simpleName}: ${error.cause?.message ?: error.message}"
            unsupportedApi=error is ClassNotFoundException || error is NoSuchMethodException || error is NoSuchFieldException
            if(lastError!=message)Eln.logger.warn(if(unsupportedApi)"Distant Horizons object integration cannot use this API version" else "Distant Horizons object integration will retry after an API error",error)
            lastError=message;failed=true;retryTicks=100
            renderBinding.clear { entries.values.forEach(::remove) }
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
            // Optional metadata trailer: older v2 readers simply stop after the
            // boxes; older caches without the trailer remain readable here.
            val marker=runCatching { input.readInt() }.getOrNull()
            if(marker!=null) {
                require(marker==0x454c4e47)
                repeat(input.readInt().also { require(it in 0..2048) }) {
                    val key=Key(BlockPos.of(input.readLong()),input.readByte().toInt())
                    if(key in entries)gridKeys+=key
                }
            }
            entries.keys.filter { it.face==6 }.forEach { gridKeys+=it;gridKeys+=Key(it.pos,-1) }
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
                val grid=entries.keys.filter { it in gridKeys }
                out.writeInt(0x454c4e47);out.writeInt(grid.size)
                for(key in grid) { out.writeLong(key.pos.asLong());out.writeByte(key.face) }
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)
        } catch(error: Exception) { Eln.logger.warn("Could not save ELN distant objects: {}",error.message) }
    }
    private fun clear() {
        store();renderBinding.clear { entries.values.forEach(::remove) };entries.clear();gridKeys.clear();colors.clear();revisions.clear();wireSignatures.clear();worker.queue.clear();save=null;world=null;dirty=false;cameraChunk=Long.MIN_VALUE;lastView=-1;retryTicks=0;failed=false
    }
    @SubscribeEvent fun unload(event: LevelEvent.Unload) { if(event.level===world)clear() }
    @SubscribeEvent fun logout(event: net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut) { clear() }

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
            val levels=proxyType.getMethod("getAllLoadedLevelWrappers").invoke(proxy) as Iterable<*>
            return (listOfNotNull(client)+levels.filterNotNull()).firstOrNull {
                unsafe.getMethod("getWrappedMcObject").invoke(it)===level && levelType.getMethod("getRenderRegister").invoke(it)!=null
            }
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
        fun genericRenderingEnabled(): Boolean? = runCatching {
            val config=delayed.getField("configs").get(null) ?: return null
            val graphics=Class.forName(prefix+"interfaces.config.IDhApiConfig").getMethod("graphics").invoke(config)
            val generic=Class.forName(prefix+"interfaces.config.client.IDhApiGraphicsConfig").getMethod("genericRendering").invoke(graphics)
            val enabled=Class.forName(prefix+"interfaces.config.client.IDhApiGenericRenderingConfig").getMethod("renderingEnabled").invoke(generic)
            Class.forName(prefix+"interfaces.config.IDhApiConfigValue").getMethod("getValue").invoke(enabled) as? Boolean
        }.getOrNull()
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
