package mods.eln.sixnode.lampsocket

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.six.SixNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.InputEvent

/** Slab-mounted fixtures can extend into the unused half of their support's block cell. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object PartialLampPicking {
    fun bounds(render: LampSocketRender,pos: BlockPos,inset: Double): AABB {
        val local=render.descriptor.renderType.mountingBounds()
        val origin=Vec3.atCenterOf(pos)
        val style=render.descriptor.fixture
        val points=buildList {
            for(x in listOf(local.minX,local.maxX))for(y in listOf(local.minY,local.maxY))for(z in listOf(local.minZ,local.maxZ)) {
                val vertex=Vec3(x-inset,y,z)
                add(if(style!=null)style.worldPoint(origin,render.side,render.front ?: LRDU.Up,vertex)
                else origin.add(render.side.rotateFromXN((render.front ?: LRDU.Up).rotateOnXnLeft(vertex).add(-.5,0.0,0.0))))
            }
        }
        return AABB(points.minOf { it.x },points.minOf { it.y },points.minOf { it.z },points.maxOf { it.x },points.maxOf { it.y },points.maxOf { it.z }).inflate(.015)
    }
    fun pick(mc: Minecraft): BlockHitResult? {
        val world=mc.level ?: return null;val player=mc.player ?: return null
        val start=player.eyePosition
        val end=start.add(player.getViewVector(1f).scale(player.blockInteractionRange()))
        var nearest=if(mc.hitResult?.type!=HitResult.Type.MISS)mc.hitResult?.location?.distanceToSqr(start) ?: start.distanceToSqr(end) else start.distanceToSqr(end)
        var result: BlockHitResult?=null
        val checked=HashSet<BlockPos>()
        for(step in 0..24) {
            val at=BlockPos.containing(start.lerp(end,step/24.0))
            val candidates=listOf(at)+net.minecraft.core.Direction.entries.map { at.relative(it) }
            for(pos in candidates) {
                if(!checked.add(pos))continue
                val entity=world.getBlockEntity(pos)
                fun accept(box: AABB,side: Direction) {
                    val hit=box.clip(start,end).orElse(null) ?: return
                    val distance=hit.distanceToSqr(start)
                    if(distance>=nearest)return
                    nearest=distance
                    result=BlockHitResult(hit,side.inverse.toFacing(),pos,false)
                }
                if(entity is SixNodeEntity)for(side in Direction.entries) {
                    val render=entity.elementRenderList[side.int] ?: continue
                    val inset=mods.eln.node.SurfaceMounting.inset(world,pos,side)
                    if(inset==0.0)continue
                    if(render is LampSocketRender)accept(bounds(render,pos,inset),side)
                    else {
                        val offset=mods.eln.node.SurfaceMounting.offset(world,pos,side)
                        accept(mods.eln.node.six.SixNodeBlock.faceSlab(side).bounds().move(Vec3.atLowerCornerOf(pos)).move(offset),side)
                    }
                }
                if(entity is mods.eln.node.transparent.TransparentNodeEntity) {
                    val render=entity.elementRender ?: continue
                    val offset=render.transparentNodeDescriptor.mountingOffset(world,pos,render.front ?: Direction.ZP)
                    val pitched=render is mods.eln.transparentnode.solarpanel.SolarPanelRender && mods.eln.transparentnode.solarpanel.SolarRoofMount.at(world,pos).pitched
                    if(offset==Vec3.ZERO && !pitched)continue
                    val mountSide=when {
                        render.transparentNodeDescriptor.mustHaveCeiling()->Direction.YP
                        render.transparentNodeDescriptor.mustHaveWallFrontInverse()->(render.front ?: Direction.ZP).inverse
                        else->Direction.YN
                    }
                    val boxes=mutableListOf<AABB?>()
                    entity.addCollisionBoxesToList(AABB(pos).inflate(4.0),boxes,null)
                    for(box in boxes)if(box!=null)accept(box,mountSide)
                }
            }
        }
        return result
    }
    @SubscribeEvent fun interaction(event: InputEvent.InteractionKeyMappingTriggered) {
        updateTarget()
    }
    @SubscribeEvent fun rendered(event: RenderFrameEvent.Post) { updateTarget() }
    private fun updateTarget() {
        val mc=Minecraft.getInstance()
        if(mc.screen!=null || mc.player?.isSpectator==true)return
        pick(mc)?.let { mc.hitResult=it;mc.crosshairPickEntity=null }
    }
}
