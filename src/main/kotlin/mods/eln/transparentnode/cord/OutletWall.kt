package mods.eln.transparentnode.cord

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.node.LoadedBlockEntities
import mods.eln.node.transparent.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.util.RandomSource
import net.neoforged.neoforge.client.model.data.ModelData

/** A recessed receptacle shares the wall cell, leaving the whole cell in front available. */
object OutletWall {
    fun state(world: BlockGetter,pos: BlockPos): BlockState {
        val entity=LoadedBlockEntities.get(world,pos) as? TransparentNodeEntity ?: return Blocks.AIR.defaultBlockState()
        return if(world is Level && !world.isClientSide)((entity.node as? TransparentNode)?.element as? OutletElement)?.wallState ?: Blocks.AIR.defaultBlockState()
        else (entity.elementRender as? OutletRender)?.wallState ?: Blocks.AIR.defaultBlockState()
    }
    fun draw(entity: TransparentNodeEntity,state: BlockState,pose: PoseStack,buffers: MultiBufferSource,overlay: Int) {
        if(state.isAir)return
        val world=entity.level!!
        val view=object: BlockAndTintGetter by world {
            override fun getBlockState(pos: BlockPos)=if(pos==entity.blockPos)state else world.getBlockState(pos)
        }
        val dispatcher=Minecraft.getInstance().blockRenderer;val model=dispatcher.getBlockModel(state)
        val random=RandomSource.create(state.getSeed(entity.blockPos))
        val data=model.getModelData(view,entity.blockPos,state,ModelData.EMPTY)
        for(layer in model.getRenderTypes(state,random,data)) {
            pose.pushPose()
            dispatcher.modelRenderer.tesselateBlock(view,model,state,entity.blockPos,pose,buffers.getBuffer(layer),true,random,state.getSeed(entity.blockPos),overlay,data,layer)
            pose.popPose()
        }
    }
}
