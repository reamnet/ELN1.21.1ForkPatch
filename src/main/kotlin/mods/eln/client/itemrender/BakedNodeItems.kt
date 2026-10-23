package mods.eln.client.itemrender

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.client.gl.ElnRenderTypes
import mods.eln.client.gl.FixedFunction
import mods.eln.client.terrain.*
import mods.eln.generic.DescriptorBlockItem
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import kotlin.math.roundToInt

/** Legacy previews become ordinary atlas-backed item quads. Resource-pack models and exceptional
 * previews (fonts, nested item rendering, non-quad effects) retain their original custom renderer. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object BakedNodeItems {
    private data class Key(val item:Item,val components:DataComponentPatch,val type:IItemRenderer.ItemRenderType)
    private data class Result(val passes:List<BakedModel>?,val reason:String?)
    private val cache=LinkedHashMap<Key,Result>(128,.75f,true)
    private var quadCount=0
    private var capturing=false
    private var revision=-1L
    internal var captures=0L;private set
    private val mc get()=Minecraft.getInstance()

    @SubscribeEvent fun bake(event:ModelEvent.ModifyBakingResult) {
        for(item in BuiltInRegistries.ITEM) {
            if(item !is DescriptorBlockItem<*> || item.descriptor !is IItemRenderer)continue
            val key=ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item))
            val original=event.models[key] ?: continue
            if(original.isCustomRenderer)event.models[key]=Preview(original,ItemStack(item))
        }
    }
    /** ModifyBakingResult runs on a worker. Invalidate render-thread caches only after the
     * completed model/atlas set has been installed, including cached sprite UV coordinates. */
    @SubscribeEvent fun completed(event:ModelEvent.BakingCompleted) {
        cache.clear();quadCount=0;revision=-1;NodeItemRenderer.clearCache()
    }
    private class Preview(private val source:BakedModel,private val stack:ItemStack):BakedModelWrapper<BakedModel>(source) {
        private val variants=object:ItemOverrides() {
            override fun resolve(model:BakedModel,item:ItemStack,level:ClientLevel?,entity:LivingEntity?,seed:Int):BakedModel {
                val selected=source.overrides.resolve(source,item,level,entity,seed)
                return if(selected!=null && selected!==source)selected else Preview(source,item)
            }
        }
        override fun getOverrides()=variants
        override fun applyTransform(context:ItemDisplayContext,pose:PoseStack,leftHand:Boolean):BakedModel {
            val transformed=source.applyTransform(context,pose,leftHand)
            if(transformed!==source)return transformed
            if(java.lang.Boolean.getBoolean("eln.disableBakedItems") || capturing)return source
            val result=resolve(source,stack,context)
            return result.passes?.let { Composite(source,it) } ?: source
        }
    }
    private class Composite(source:BakedModel,private val passes:List<BakedModel>):BakedModelWrapper<BakedModel>(source) {
        override fun isCustomRenderer()=false
        override fun getRenderPasses(stack:ItemStack,fabulous:Boolean)=passes
    }
    private class Pass(source:BakedModel,private val quads:List<BakedQuad>,private val layer:RenderType):BakedModelWrapper<BakedModel>(source) {
        override fun isCustomRenderer()=false
        override fun getQuads(state:BlockState?,side:Direction?,rand:RandomSource)=if(side==null)quads else emptyList()
        override fun getRenderTypes(stack:ItemStack,fabulous:Boolean)=listOf(layer)
        override fun getRenderPasses(stack:ItemStack,fabulous:Boolean)=listOf(this)
    }
    private fun resolve(source:BakedModel,stack:ItemStack,context:ItemDisplayContext):Result {
        if(revision!=TerrainAtlas.revision.get()) { cache.clear();quadCount=0;revision=TerrainAtlas.revision.get() }
        val key=Key(stack.item,stack.componentsPatch,IItemRenderer.ItemRenderType.of(context))
        cache[key]?.let { return it }
        var mesh:TerrainMesh?=null
        var reason:String?=null
        capturing=true
        try {
            FixedFunction.isolated {
                val capture=TerrainCapture().apply { enabled=true }
                FixedFunction.setMeshSink(capture)
                try { NodeItemRenderer.get().renderByItem(stack,context,PoseStack(),MultiBufferSource { error("Item capture escaped its mesh sink") },LightTexture.FULL_BRIGHT,0) }
                finally { FixedFunction.setMeshSink(null) }
                if(capture.supported)mesh=capture.finish() else reason=capture.unsupportedReason ?: "non-quad or unavailable texture"
            }
        } catch(t:Exception) { reason=t.javaClass.simpleName+": "+t.message }
        finally { capturing=false }
        val passes=mesh?.batches?.map { batch ->
            val m=batch.material
            val sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TerrainAtlas.sprite(m.texture))
            val quads=batch.vertices.indices.step(36).map { offset ->
                val data=IntArray(32)
                for(i in 0..3) {
                    val from=offset+i*9;val to=i*8;val v=batch.vertices
                    repeat(3) { data[to+it]=v[from+it] }
                    data[to+3]=ItemQuadPacking.abgr(v[from+8])
                    data[to+4]=TerrainAtlas.u(m.texture,sprite,Float.fromBits(v[from+3])).toRawBits()
                    data[to+5]=TerrainAtlas.v(m.texture,sprite,Float.fromBits(v[from+4])).toRawBits()
                    data[to+6]=if(m.fullBright)LightTexture.FULL_BRIGHT else 0
                    data[to+7]=ItemQuadPacking.normal(Float.fromBits(v[from+5]),Float.fromBits(v[from+6]),Float.fromBits(v[from+7]))
                }
                BakedQuad(data,-1,Direction.getNearest(Float.fromBits(batch.vertices[offset+5]),Float.fromBits(batch.vertices[offset+6]),Float.fromBits(batch.vertices[offset+7])),sprite,false,false)
            }
            val atlas=TextureAtlas.LOCATION_BLOCKS
            val layer=when {
                m.unlit -> ElnRenderTypes.unlit(atlas,m.blend,m.cull)
                m.blend && m.cull -> RenderType.entityTranslucentCull(atlas)
                m.blend -> RenderType.entityTranslucent(atlas)
                m.cull -> RenderType.entityCutout(atlas)
                else -> RenderType.entityCutoutNoCull(atlas)
            }
            Pass(source,quads,layer)
        }
        val result=Result(passes,reason)
        cache[key]=result;captures++
        quadCount+=passes?.sumOf { it.getQuads(null,null,RandomSource.create(0)).size } ?: 0
        while(cache.size>256 || (quadCount>200_000 && cache.size>1)) {
            val oldest=cache.entries.iterator();val removed=oldest.next().value;oldest.remove()
            quadCount-=removed.passes?.sumOf { it.getQuads(null,null,RandomSource.create(0)).size } ?: 0
        }
        return result
    }
    internal fun audit(stack:ItemStack,context:ItemDisplayContext):Map<String,Any?> {
        val source=mc.itemRenderer.itemModelShaper.getItemModel(stack)
        val outer=PoseStack().apply { translate(37.0,81.0,-15.0) }
        FixedFunction.begin(outer,MultiBufferSource { error("Preview capture leaked into outer renderer") },0x00800090,0)
        val model=try {
            FixedFunction.bindTexture(FixedFunction.WHITE)
            MeshPartSelection.automatic(emptySet(),true) {
                source.applyTransform(context,PoseStack(),false).also {
                    check(FixedFunction.pose()===outer && FixedFunction.light()==0x00800090 && FixedFunction.texture()==FixedFunction.WHITE) { "Item capture changed outer render state" }
                }
            }
        } finally { FixedFunction.finish() }
        val result=cache[Key(stack.item,stack.componentsPatch,IItemRenderer.ItemRenderType.of(context))]
        if(result?.passes!=null) {
            val expected=TerrainCapture().apply { enabled=true }
            FixedFunction.isolated {
                FixedFunction.setMeshSink(expected)
                try { NodeItemRenderer.get().renderByItem(stack,context,PoseStack(),MultiBufferSource { error("Reference preview escaped capture") },LightTexture.FULL_BRIGHT,0) }
                finally { FixedFunction.setMeshSink(null) }
            }
            check(expected.supported)
            val reference=expected.finish().batches
            val passes=model.getRenderPasses(stack,false)
            check(reference.size==passes.size) { "Item material passes differ" }
            for((batch,pass) in reference.zip(passes)) {
                val sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TerrainAtlas.sprite(batch.material.texture))
                val quads=pass.getQuads(null,null,RandomSource.create(0))
                check(quads.size*36==batch.vertices.size) { "Preview quad count differs for ${BuiltInRegistries.ITEM.getKey(stack.item)} $context: ${quads.size} vs ${batch.vertices.size/36}" }
                for((qi,q) in quads.withIndex())for(i in 0..3) {
                    val a=qi*36+i*9;val b=i*8
                    repeat(3) { check(q.vertices[b+it]==batch.vertices[a+it]) { "Baked preview position differs" } }
                    check(ItemQuadPacking.abgr(q.vertices[b+3])==batch.vertices[a+8]) { "Baked preview color differs" }
                    check(q.vertices[b+4]==TerrainAtlas.u(batch.material.texture,sprite,Float.fromBits(batch.vertices[a+3])).toRawBits())
                    check(q.vertices[b+5]==TerrainAtlas.v(batch.material.texture,sprite,Float.fromBits(batch.vertices[a+4])).toRawBits())
                    check(q.vertices[b+6]==if(batch.material.fullBright)LightTexture.FULL_BRIGHT else 0)
                    repeat(3) { axis ->
                        val packed=((q.vertices[b+7] ushr (axis*8)) and 255).toByte().toFloat()/127f
                        check(kotlin.math.abs(packed-Float.fromBits(batch.vertices[a+5+axis]))<.005f) { "Baked preview normal differs" }
                    }
                }
            }
        }
        return mapOf("item" to BuiltInRegistries.ITEM.getKey(stack.item).toString(),"context" to context.name,
            "baked" to !model.isCustomRenderer,"converted" to (result?.passes!=null),"alreadyBaked" to (!model.isCustomRenderer && result==null),"quads" to model.getRenderPasses(stack,false).sumOf { it.getQuads(null,null,RandomSource.create(0)).size },"fallback" to result?.reason)
    }
}
internal object ItemQuadPacking {
    fun abgr(argb:Int)=(argb and -0x1000000) or ((argb and 255) shl 16) or (argb and 0xff00) or ((argb ushr 16) and 255)
    fun normal(x:Float,y:Float,z:Float)=pack(x) or (pack(y) shl 8) or (pack(z) shl 16)
    private fun pack(n:Float)=(n.coerceIn(-1f,1f)*127f).roundToInt() and 255
}
