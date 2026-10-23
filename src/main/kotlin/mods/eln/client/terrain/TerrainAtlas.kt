package mods.eln.client.terrain

import com.mojang.serialization.MapCodec
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent

/** Preserve legacy OBJ resource-pack paths while making their textures available to terrain. */
class TerrainAtlas : SpriteSource {
    override fun run(resources: ResourceManager, output: SpriteSource.Output) {
        val found=HashSet<ResourceLocation>()
        resources.listResources("model") { it.namespace=="eln" && it.path.endsWith(".png") &&
            !it.path.endsWith("/render.png") }.forEach { (id,resource) ->
                // Do not reduce the whole terrain atlas's mip level for a legacy GUI/preview image.
                val dimensions=resource.open().use { stream ->
                    java.io.DataInputStream(stream).run {
                        if(readLong()!=0x89504e470d0a1a0aUL.toLong())null
                        else { readInt();readInt();readInt() to readInt() }
                    }
                }
                if(dimensions!=null && dimensions.first%16==0 && dimensions.second%16==0) {
                    output.add(sprite(id),resource);found.add(id)
                }
            }
        available=found.toSet()
        revision.incrementAndGet()
    }
    override fun type() = TYPE
    companion object {
        @Volatile private var available: Set<ResourceLocation> = emptySet()
        val revision=java.util.concurrent.atomic.AtomicLong()
        val textureCount get()=available.size
        fun supported(texture: ResourceLocation)=texture==FixedFunction.WHITE || texture in available
        val WHITE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("eln","block/terrain_white")
        val TYPE = SpriteSourceType(MapCodec.unit(::TerrainAtlas))
        fun sprite(texture: ResourceLocation): ResourceLocation = if (texture==FixedFunction.WHITE) WHITE else
            ResourceLocation.fromNamespaceAndPath("eln","terrain/${texture.namespace}/${texture.path.removeSuffix(".png")}")
    }
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object TerrainAtlasRegistration {
    @SubscribeEvent fun register(event: RegisterSpriteSourceTypesEvent) {
        event.register(ResourceLocation.fromNamespaceAndPath("eln","terrain_textures"),TerrainAtlas.TYPE)
    }
}
