package mods.eln.client.terrain

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.serialization.MapCodec
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection
import net.minecraft.client.resources.metadata.animation.FrameSize
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent

/** Preserve legacy resource-pack paths. Small/odd textures receive a repeating pixel border,
 * rather than reducing the whole terrain atlas mip level or rescaling the original pixels. */
class TerrainAtlas: SpriteSource {
    override fun run(resources: ResourceManager,output: SpriteSource.Output) {
        val found=HashMap<ResourceLocation,AtlasRegion>()
        for(prefix in listOf("model","sprites","textures/blocks","textures/items","textures/voltages","textures/block","textures/item")) {
            resources.listResources(prefix) { it.namespace=="eln" && it.path.endsWith(".png") && !it.path.endsWith("/render.png") }.forEach { (id,resource) ->
                val (width,height)=resource.open().use { java.io.DataInputStream(it).run {
                    require(readLong()==0x89504e470d0a1a0aUL.toLong());readInt();readInt();readInt() to readInt()
                } }
                val padded=width%16!=0 || height%16!=0
                // Raw legacy textures did not use atlas animation metadata. Keep those exceptional
                // resource-pack overrides on their original renderer rather than changing the layout.
                if(resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).isPresent)return@forEach
                val canonical=id.path.startsWith("textures/") && !padded
                val sprite=if(canonical)ResourceLocation.fromNamespaceAndPath(id.namespace,id.path.removePrefix("textures/").removeSuffix(".png")) else customSprite(id)
                if(!padded) {
                    output.add(sprite,resource);found[id]=AtlasRegion(sprite)
                } else {
                    val pw=AtlasPadding.size(width);val ph=AtlasPadding.size(height)
                    output.add(sprite,SpriteSource.SpriteSupplier { _ ->
                        val image=NativeImage(pw,ph,false)
                        resource.open().use { stream -> NativeImage.read(stream).use { original ->
                            for(y in 0 until ph)for(x in 0 until pw)image.setPixelRGBA(x,y,original.getPixelRGBA(AtlasPadding.source(x,width),AtlasPadding.source(y,height)))
                        } }
                        SpriteContents(sprite,FrameSize(pw,ph),image,resource.metadata())
                    })
                    found[id]=AtlasRegion(sprite,width.toFloat()/pw,height.toFloat()/ph,1f/pw,1f/ph,true)
                }
            }
        }
        available=found.toMap();revision.incrementAndGet()
    }
    override fun type()=TYPE
    companion object {
        @Volatile private var available: Map<ResourceLocation,AtlasRegion> = emptyMap()
        val revision=java.util.concurrent.atomic.AtomicLong()
        val textureCount get()=available.size
        fun supported(texture:ResourceLocation)=texture==FixedFunction.WHITE || texture in available
        val WHITE:ResourceLocation=ResourceLocation.fromNamespaceAndPath("eln","block/terrain_white")
        val TYPE=SpriteSourceType(MapCodec.unit(::TerrainAtlas))
        private fun customSprite(texture:ResourceLocation)=ResourceLocation.fromNamespaceAndPath("eln","terrain/${texture.namespace}/${texture.path.removeSuffix(".png")}")
        fun sprite(texture:ResourceLocation)=if(texture==FixedFunction.WHITE)WHITE else available[texture]?.sprite ?: customSprite(texture)
        fun u(texture:ResourceLocation,sprite:TextureAtlasSprite,u:Float):Float {
            if(texture==FixedFunction.WHITE)return sprite.getU(.5f)
            val r=available[texture] ?: AtlasRegion(sprite(texture))
            val v=if(r.padded)r.offsetU+u*r.scaleU else .5f+(u-.5f)*(1f-sprite.uvShrinkRatio())
            return sprite.getU(v)
        }
        fun v(texture:ResourceLocation,sprite:TextureAtlasSprite,v:Float):Float {
            if(texture==FixedFunction.WHITE)return sprite.getV(.5f)
            val r=available[texture] ?: AtlasRegion(sprite(texture))
            val value=if(r.padded)r.offsetV+v*r.scaleV else .5f+(v-.5f)*(1f-sprite.uvShrinkRatio())
            return sprite.getV(value)
        }
    }
}
internal data class AtlasRegion(val sprite:ResourceLocation,val scaleU:Float=1f,val scaleV:Float=1f,
    val offsetU:Float=0f,val offsetV:Float=0f,val padded:Boolean=false)
internal object AtlasPadding {
    fun size(original:Int)=((original+2+15)/16)*16
    fun source(pixel:Int,original:Int)=Math.floorMod(pixel-1,original)
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object TerrainAtlasRegistration {
    @SubscribeEvent fun register(event:RegisterSpriteSourceTypesEvent) {
        event.register(ResourceLocation.fromNamespaceAndPath("eln","terrain_textures"),TerrainAtlas.TYPE)
    }
}
