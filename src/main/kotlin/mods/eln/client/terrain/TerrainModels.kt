package mods.eln.client.terrain

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.misc.Direction
import mods.eln.node.NodeBlockEntity
import mods.eln.node.NodeRenderSupport
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent
import net.neoforged.neoforge.event.level.LevelEvent
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/** Static model bodies are captured on the main thread, then compiled into ordinary section GPU
 * buffers. Chunk workers see immutable meshes only; the GL emulator/renderer instances never escape. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object TerrainModels {
    private data class MeshKey(val descriptor: Any,val side: Any?,val appearance: Any)
    private class Body(val node: NodeBlockEntity,val key: MeshKey,val mesh: TerrainMesh,
        val sections: Map<Long,List<TerrainSlice>>,var light: Int)
    private data class Snapshot(val slices: List<TerrainSlice>,val light: Int)
    private val bodies=IdentityHashMap<Any,Body>()
    private val nodeBodies=IdentityHashMap<NodeBlockEntity,MutableSet<Any>>()
    private val lastUpdate=IdentityHashMap<NodeBlockEntity,Long>()
    private val sections=HashMap<Long,IdentityHashMap<Any,Body>>()
    private val pool=object: LinkedHashMap<MeshKey,TerrainMesh>(128,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MeshKey,TerrainMesh>?)=size>128
    }
    private val unsupported=linkedSetOf<MeshKey>()
    private var world: ClientLevel?=null
    private var discovering: NodeBlockEntity?=null
    private var capture: TerrainCapture?=null
    private val visited=java.util.Collections.newSetFromMap(IdentityHashMap<Any,Boolean>())
    private val mc get()=Minecraft.getInstance()
    private val enabled get()=!java.lang.Boolean.getBoolean("eln.disableTerrainModels")
    var capturedBodies=0L;private set
    private val compilations=java.util.concurrent.atomic.AtomicLong()
    val compiledSections get()=compilations.get()
    private var atlasRevision=-1L
    val isDiscovering get()=discovering!=null

    /** Wrap just the model body; cables, particles and sound remain in their original paths. */
    fun body(owner: Any, appearance: Any, animated: Set<String> = emptySet(), draw: () -> Unit) {
        if (!enabled) { draw(); return }
        val node=discovering
        if (node==null) {
            if (bodies.containsKey(owner)) {
                if (animated.isNotEmpty()) MeshPartSelection.with(animated,true,draw)
            } else draw()
            return
        }
        visited += owner
        val descriptor:Any
        val side:Any?
        when(owner) {
            is SixNodeElementRender -> { descriptor=owner.sixNodeDescriptor;side=owner.side }
            is TransparentNodeElementRender -> { descriptor=owner.transparentNodeDescriptor;side=null }
            else -> error("Unknown terrain model owner")
        }
        val key=MeshKey(descriptor,side,appearance)
        val previous=bodies[owner]
        if (previous?.key==key) return
        if(unsupported.isNotEmpty() && key in unsupported) { removeBody(owner);return }
        val mesh=pool[key] ?: run {
            val collector=capture!!
            collector.enabled=true
            try { MeshPartSelection.with(animated,false,draw) } finally { collector.enabled=false }
            if (!collector.supported) { unsupported+=key;if(unsupported.size>128)unsupported.remove(unsupported.first());removeBody(owner);capture=TerrainCapture();FixedFunction.setMeshSink(capture);return }
            collector.finish().also { pool[key]=it;capturedBodies++ }
        }
        // A fresh collector is required for every body (a six-node can have six separate fixtures).
        capture=TerrainCapture(); FixedFunction.setMeshSink(capture)
        removeBody(owner)
        val split=TerrainPartition.split(mesh,node.blockPos)
        val body=Body(node,key,mesh,split,NodeRenderSupport.neighbourLight(node))
        bodies[owner]=body
        nodeBodies.getOrPut(node) { java.util.Collections.newSetFromMap(IdentityHashMap()) }.add(owner)
        for (section in split.keys) { sections.getOrPut(section) { IdentityHashMap() }[owner]=body;dirty(section) }
    }

    /** Old renderers describe visual state in their draw commands. Fingerprint cached batches
     * and transforms, without expanding or transforming their vertices every client tick. */
    fun drawElement(owner: Any,draw: ()->Unit) {
        if(explicit(owner) || !enabled) { draw();return }
        val moving=LegacyMovingParts.names(owner)
        if(discovering==null) {
            if(bodies.containsKey(owner))MeshPartSelection.automatic(moving,true,draw) else draw()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val previous=bodies[owner]?.key?.appearance as? List<DrawSignature>
        val signature=SignatureCapture(previous ?: emptyList())
        val old=capture
        FixedFunction.setMeshSink(signature)
        GL11.glPushMatrix();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try { FixedFunction.preserveVertexState { MeshPartSelection.automatic(moving,false,draw) } }
        finally { GL11.glPopAttrib();GL11.glPopMatrix();FixedFunction.setMeshSink(old) }
        body(owner,signature.finish()) { MeshPartSelection.automatic(moving,false,draw) }
    }

    internal fun meshFor(owner: Any)=bodies[owner]?.mesh
    internal fun refreshNow(node: NodeBlockEntity) { lastUpdate.remove(node);update(node) }
    internal fun sectionsFor(owner: Any)=bodies[owner]?.sections?.keys ?: emptySet()

    fun dynamic(draw: () -> Unit) { MeshPartSelection.dynamic(draw) }

    fun update(node: NodeBlockEntity) {
        if (!enabled || node.level !is ClientLevel || node.isRemoved) return
        if (world !== node.level) { clear();world=node.level as ClientLevel }
        val revision=TerrainAtlas.revision.get()
        if(atlasRevision!=revision) {
            sections.keys.toList().forEach(::dirty)
            clear();world=node.level as ClientLevel;atlasRevision=revision
        }
        if (lastUpdate[node]==node.level!!.gameTime) return
        lastUpdate[node]=node.level!!.gameTime
        val renders=when(node) {
            is SixNodeEntity -> node.elementRenderList.filterNotNull()
            is TransparentNodeEntity -> listOfNotNull(node.elementRender)
            else -> emptyList()
        }
        if (renders.isEmpty()) { nodeBodies[node]?.toList()?.forEach(::removeBody);return }
        if (discovering!=null || FixedFunction.inWorld()) return
        discovering=node;visited.clear();capture=TerrainCapture()
        FixedFunction.setMeshSink(capture)
        val pose=PoseStack()
        FixedFunction.begin(pose,MultiBufferSource { error("Terrain discovery must not emit live vertices") },0,0)
        try {
            GL11.glTranslatef(.5f,.5f,.5f)
            when(node) {
                is SixNodeEntity -> for ((index,render) in node.elementRenderList.withIndex()) {
                    if(render==null)continue
                    GL11.glColor4f(1f,1f,1f,1f)
                    GL11.glPushMatrix()
                    try { Direction.fromInt(index)!!.glRotateXnRef();GL11.glTranslatef(-.5f,0f,0f);drawElement(render) { render.draw() } }
                    finally { GL11.glPopMatrix() }
                }
                is TransparentNodeEntity -> node.elementRender?.let { render->drawElement(render) { render.draw() } }
            }
            val owners=nodeBodies[node]?.toList() ?: emptyList()
            owners.filter { it !in visited }.forEach(::removeBody)
            val light=NodeRenderSupport.neighbourLight(node)
            for (owner in owners) bodies[owner]?.let { body -> if(body.light!=light) {
                body.light=light;body.sections.keys.forEach(::dirty)
            } }
        } finally {
            FixedFunction.setMeshSink(null);FixedFunction.finish();discovering=null;capture=null;visited.clear()
        }
    }
    fun remove(node: NodeBlockEntity) { nodeBodies[node]?.toList()?.forEach(::removeBody);lastUpdate.remove(node) }
    private fun removeBody(owner: Any) {
        val old=bodies.remove(owner) ?: return
        nodeBodies[old.node]?.let { it.remove(owner);if(it.isEmpty())nodeBodies.remove(old.node) }
        for(key in old.sections.keys) { sections[key]?.let { it.remove(owner);if(it.isEmpty())sections.remove(key) };dirty(key) }
    }
    private fun dirty(key: Long) {
        val s=SectionPos.of(key);mc.levelRenderer.setSectionDirty(s.x(),s.y(),s.z())
    }
    private fun clear() { bodies.clear();nodeBodies.clear();lastUpdate.clear();sections.clear();pool.clear();unsupported.clear();world=null }
    private fun explicit(render: Any): Boolean = if(render is mods.eln.sixnode.lampsocket.LampSocketRender)
        render.descriptor.fixture!=null else render.javaClass.simpleName in SUPPORTED || render is mods.eln.transparentnode.festive.FairyLightsRender
    private val SUPPORTED=setOf("MatterMachineRender","KitchenRender","FanRender","RenewableRender","IndustrialPumpRender","UpsRender",
        "ClimateRender","BackupRender","OutletRender","DistributionPoleRender","HouseServiceRender","BreakerPanelRender",
        "WorkLightRender","StepDownUnitRender","UtilityStreetLightRender","HolidayCandleRender","StringLightsRender","ChristmasTreeRender","WallWirelessRender")
    @SubscribeEvent fun unload(event: LevelEvent.Unload) { if(event.level===world)clear() }

    @SubscribeEvent fun geometry(event: AddSectionGeometryEvent) {
        if(event.level!==world)return
        val key=SectionPos.of(event.sectionOrigin).asLong()
        val snapshots=sections[key]?.values?.map { Snapshot(it.sections[key]!!,it.light) } ?: return
        if (snapshots.isEmpty()) return
        val nether=(event.level as ClientLevel).effects().constantAmbientLight()
        // Respect shader packs which supply their own normal lighting (Iris oldLighting=false).
        val directional=event.level.getShade(net.minecraft.core.Direction.DOWN,true)<.99f
        event.addRenderer { context ->
            for(snapshot in snapshots)for(slice in snapshot.slices)emit(context,slice,snapshot.light,nether,directional)
            compilations.incrementAndGet()
        }
    }
    private fun emit(context: AddSectionGeometryEvent.SectionRenderingContext,slice: TerrainSlice,light: Int,nether: Boolean,directional: Boolean) {
        val material=slice.batch.material
        val sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TerrainAtlas.sprite(material.texture))
        val consumer=context.getOrCreateChunkBuffer(if(material.blend)RenderType.translucent() else RenderType.cutout())
        val data=slice.batch.vertices
        fun face(offset: Int,reverse: Boolean) {
            for(i in 0..3) {
                val p=offset+(if(reverse)3-i else i)*9
                val nx=Float.fromBits(data[p+5]);val ny=Float.fromBits(data[p+6]);val nz=Float.fromBits(data[p+7])
                val shade=if(material.unlit || !directional)1f else diffuse(nx,ny,nz,nether)
                val color=data[p+8]
                consumer.addVertex(Float.fromBits(data[p])+slice.x,Float.fromBits(data[p+1])+slice.y,Float.fromBits(data[p+2])+slice.z)
                    .setColor(TerrainColors.shade(color,shade))
                    .setUv(TerrainAtlas.u(material.texture,sprite,Float.fromBits(data[p+3])),TerrainAtlas.v(material.texture,sprite,Float.fromBits(data[p+4])))
                    .setLight(if(material.fullBright)0x00f000f0 else light).setNormal(nx,ny,nz)
            }
        }
        fun quad(offset:Int) {
            // Sodium 0.8.13's translucent consumer leaves its index at four when a
            // collapsed face is rejected. Omit invisible/tiny collapsed geometry before submission.
            if(!TerrainPartition.renderable(slice,offset))return
            face(offset,false);if(!material.cull)face(offset,true)
        }
        if(slice.quads==null)for(offset in data.indices step 36)quad(offset) else for(offset in slice.quads)quad(offset)
    }
    internal fun diffuse(x: Float,y: Float,z: Float,nether: Boolean): Float {
        val a=max(0f,(.2f*x+y-.7f*z)/1.2369317f)
        val b=max(0f,(-.2f*x+(if(nether)-y else y)+.7f*z)/1.2369317f)
        return min(1f,(a+b)*.6f+.4f)
    }
}
