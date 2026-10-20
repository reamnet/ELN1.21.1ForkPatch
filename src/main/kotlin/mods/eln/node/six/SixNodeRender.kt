package mods.eln.node.six

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.UtilsClient.glDefaultColor
import mods.eln.node.NodeRenderSupport
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData

/** Draws the six faces of a six-node through the fixed-function emulator (see mods.eln.client.gl). */
class SixNodeRender : BlockEntityRenderer<SixNodeEntity> {
    override fun render(entity: SixNodeEntity, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, packedOverlay: Int) {
        Minecraft.getInstance().profiler.push("SixNode")
        renderCover(entity,poseStack,buffer,packedOverlay)
        FixedFunction.begin(poseStack, buffer, NodeRenderSupport.neighbourLight(entity), packedOverlay)
        try {
            GL11.glPushMatrix()
            GL11.glTranslatef(.5f, .5f, .5f)
            for ((idx, render) in entity.elementRenderList.withIndex()) {
                if (render != null) {
                    glDefaultColor()
                    GL11.glPushMatrix()
                    fromInt(idx)!!.glRotateXnRef()
                    GL11.glTranslatef(-0.5f, 0f, 0f)
                    render.draw()
                    GL11.glPopMatrix()
                    render.cord.draw(entity.level!!,entity.blockPos,fromInt(idx)!!,fromInt(idx)!!.rotateFromXN(Vec3(-.40,0.0,0.0)))
                }
            }
            GL11.glPopMatrix()
        } finally {
            FixedFunction.finish()
        }
        Minecraft.getInstance().profiler.pop()
    }

    internal fun renderCover(entity: SixNodeEntity,poseStack: PoseStack,buffer: MultiBufferSource,packedOverlay: Int) {
        // Use the world tessellator, including AO, biome tint, directional shade and
        // position-seeded textures. Item lighting makes otherwise identical covers stand out.
        val cover = entity.sixNodeCacheBlock.defaultBlockState()
        if (!cover.isAir) {
            val world=entity.level!!
            val view=object: BlockAndTintGetter by world {
                override fun getBlockState(pos: BlockPos): BlockState {
                    val block=(world.getBlockEntity(pos) as? SixNodeEntity)?.sixNodeCacheBlock
                    return if(block!=null && !block.defaultBlockState().isAir)block.defaultBlockState() else world.getBlockState(pos)
                }
            }
            val dispatcher=Minecraft.getInstance().blockRenderer
            val model=dispatcher.getBlockModel(cover)
            val random=RandomSource.create(cover.getSeed(entity.blockPos))
            val data=model.getModelData(view,entity.blockPos,cover,ModelData.EMPTY)
            for(layer in model.getRenderTypes(cover,random,data)) {
                poseStack.pushPose()
                dispatcher.modelRenderer.tesselateBlock(view,model,cover,entity.blockPos,poseStack,
                    buffer.getBuffer(layer),true,random,cover.getSeed(entity.blockPos),packedOverlay,data,layer)
                poseStack.popPose()
            }
        }
    }

    override fun getRenderBoundingBox(entity: SixNodeEntity): AABB = entity.getRenderBoundingBox()

    override fun getViewDistance(): Int = NodeRenderSupport.viewDistance()

    override fun shouldRender(entity: SixNodeEntity, cameraPos: Vec3): Boolean = NodeRenderSupport.shouldRender(entity, cameraPos, viewDistance)
}
