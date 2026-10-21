package mods.eln.devtest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.node.six.SixNodeEntity
import mods.eln.client.SixNodeCoverModel
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.neoforged.neoforge.client.model.data.ModelData

/** Compare production camouflage against vanilla's world tessellation with real loaded textures/light. */
internal object CoverRenderingChecks {
    private class Vertices: VertexConsumer {
        val values=ArrayList<String>()
        override fun addVertex(x: Float,y: Float,z: Float): VertexConsumer=apply { values.add("v:$x,$y,$z") }
        override fun setColor(r: Int,g: Int,b: Int,a: Int): VertexConsumer=apply { values.add("c:$r,$g,$b,$a") }
        override fun setUv(u: Float,v: Float): VertexConsumer=apply { values.add("uv:$u,$v") }
        override fun setUv1(u: Int,v: Int): VertexConsumer=apply { values.add("overlay:$u,$v") }
        override fun setUv2(u: Int,v: Int): VertexConsumer=apply { values.add("light:$u,$v") }
        override fun setNormal(x: Float,y: Float,z: Float): VertexConsumer=apply { values.add("normal:$x,$y,$z") }
    }
    fun verify(world: ClientLevel,covered: BlockPos,ordinary: BlockPos) {
        val actual=Vertices();val expected=Vertices()
        val tile=world.getBlockEntity(covered) as SixNodeEntity
        check(!tile.sixNodeCacheBlock.defaultBlockState().isAir)
        val state=world.getBlockState(ordinary)
        check(state.block===tile.sixNodeCacheBlock)
        check(tile.blockState.isSolidRender(world,covered)==state.isSolidRender(world,ordinary))
        check(tile.blockState.isViewBlocking(world,covered)==state.isViewBlocking(world,ordinary))
        for(side in net.minecraft.core.Direction.entries)check(tile.blockState.getAppearance(world,covered,side,null,null)==state) {
            "Cover must expose the original block's shader material"
        }
        val renderer=Minecraft.getInstance().blockRenderer;val model=renderer.getBlockModel(state)
        // Vanilla randomly rotates stone textures by block position. Compare both models
        // with the covered position's seed while retaining the ordinary cube's world light.
        val coverModel=renderer.getBlockModel(tile.blockState)
        check(coverModel is SixNodeCoverModel) { "Cover must render as chunk terrain" }
        val snapshot=world.getModelData(covered)
        check(snapshot.get(mods.eln.node.six.SixNodeCoverData.COVER)==state) { "Chunk workers must receive the current cover snapshot" }
        val data=coverModel.getModelData(world,covered,tile.blockState,snapshot)
        val actualRandom=RandomSource.create(state.getSeed(covered))
        for(layer in coverModel.getRenderTypes(tile.blockState,actualRandom,data))renderer.modelRenderer.tesselateBlock(
            world,coverModel,tile.blockState,covered,PoseStack(),actual,true,actualRandom,state.getSeed(covered),0,data,layer)
        val seed=state.getSeed(covered)
        val random=RandomSource.create(seed)
        for(layer in model.getRenderTypes(state,random,ModelData.EMPTY))renderer.modelRenderer.tesselateBlock(
            world,model,state,ordinary,PoseStack(),expected,true,random,seed,0,ModelData.EMPTY,layer)
        check(actual.values.isNotEmpty() && actual.values==expected.values) {
            "Camouflage shading differs from its normal block: ${actual.values.zip(expected.values).filter { it.first!=it.second }.take(8)} (${actual.values.size}/${expected.values.size})"
        }
    }
}
