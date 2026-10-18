package mods.eln.sixnode.lampsocket.objrender

import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketRender
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType

interface ILampSocketObjRender {

    fun mountingBounds() = net.minecraft.world.phys.AABB(0.0,-.4,-.4,.8,.4,.4)

    fun connectionEdges(front: mods.eln.misc.LRDU, offset: Double): FloatArray

    fun draw(descriptor: LampSocketDescriptor, type: ItemRenderType, distanceToPlayer: Double)

    fun draw(render: LampSocketRender, distanceToPlayer: Double)

}
