package mods.eln.client

import mods.eln.Eln
import mods.eln.node.six.SixNodeCoverData.COVER
import mods.eln.node.six.SixNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockModelShaper
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.client.model.data.ModelProperty

/** Camouflage belongs to terrain, including Sodium's chunk mesher and Iris's terrain pass.
 * Drawing it as a block entity gives shaders different lighting and material treatment. */
class SixNodeCoverModel(original: BakedModel): BakedModelWrapper<BakedModel>(original) {
    companion object { private val DELEGATE = ModelProperty<ModelData>() }
    private fun cover(data: ModelData) = data.get(COVER)?.takeUnless { it.isAir }
    private fun model(state: BlockState) = Minecraft.getInstance().blockRenderer.getBlockModel(state)
    private fun delegated(data: ModelData) = data.get(DELEGATE) ?: ModelData.EMPTY

    override fun getModelData(level: BlockAndTintGetter,pos: BlockPos,state: BlockState,modelData: ModelData): ModelData {
        val cover=cover(modelData) ?: return modelData
        return modelData.derive().with(DELEGATE,model(cover).getModelData(coverView(level),pos,cover,ModelData.EMPTY)).build()
    }
    override fun getQuads(state: BlockState?,side: Direction?,rand: RandomSource,data: ModelData,renderType: RenderType?) =
        cover(data)?.let { model(it).getQuads(it,side,rand,delegated(data),renderType) } ?: emptyList()
    override fun getRenderTypes(state: BlockState,rand: RandomSource,data: ModelData) =
        cover(data)?.let { model(it).getRenderTypes(it,rand,delegated(data)) } ?: net.neoforged.neoforge.client.ChunkRenderTypeSet.none()
    override fun useAmbientOcclusion(state: BlockState,data: ModelData,renderType: RenderType) =
        cover(data)?.let { model(it).useAmbientOcclusion(it,delegated(data),renderType) } ?: super.useAmbientOcclusion(state,data,renderType)
    override fun getParticleIcon(data: ModelData) = cover(data)?.let { model(it).getParticleIcon(delegated(data)) } ?: super.getParticleIcon(data)
}

/** Cover-aware neighbors for model-specific data and biome tint sampling. */
private fun coverState(level: BlockAndTintGetter,pos: BlockPos): BlockState? =
    level.getModelData(pos).get(COVER) ?: (level.getBlockEntity(pos) as? SixNodeEntity)?.sixNodeCacheBlock?.defaultBlockState()

internal fun coverView(level: BlockAndTintGetter): BlockAndTintGetter = object: BlockAndTintGetter by level {
    override fun getBlockState(pos: BlockPos): BlockState =
        coverState(level,pos)?.takeUnless { it.isAir } ?: level.getBlockState(pos)
}

@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object SixNodeCoverModels {
    @SubscribeEvent fun bake(event: ModelEvent.ModifyBakingResult) {
        for(state in Eln.sixNodeBlock.stateDefinition.possibleStates) {
            val key=BlockModelShaper.stateToModelLocation(state)
            event.models[key]?.let { event.models[key]=SixNodeCoverModel(it) }
        }
    }
    @SubscribeEvent fun colors(event: RegisterColorHandlersEvent.Block) {
        event.register({ _,level,pos,index ->
            val cover=if(level!=null && pos!=null)coverState(level,pos) else null
            if(cover==null || cover.isAir)-1 else Minecraft.getInstance().blockColors.getColor(cover,coverView(level!!),pos,index)
        },Eln.sixNodeBlock)
    }
}
