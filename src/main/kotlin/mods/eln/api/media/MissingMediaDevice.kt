package mods.eln.api.media

import mods.eln.client.terrain.BakedNodeRenderer
import mods.eln.client.terrain.TerrainModels
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.node.transparent.*
import mods.eln.transparentnode.industrial.IndustrialDescriptor
import mods.eln.transparentnode.industrial.IndustrialKind
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/** Hidden registry reservations keep existing item stacks and placed nodes recoverable. */
class MissingMediaDescriptor(val reserved: ReservedMediaDevice): TransparentNodeDescriptor(reserved.title,MissingMediaElement::class.java,MissingMediaRender::class.java) {
    private val cabinet by lazy { IndustrialDescriptor(IndustrialKind.SCADA) }
    init { hideFromCreative();setDefaultIcon("machineblock") }
    override fun mustHaveFloor()=false
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=cabinet.draw(Direction.ZN)
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        list.add(tr("Install Electrical Age Media to use this saved device"))
    }
}

/** No loads, processes, browser classes, native dependencies or media downloads. */
class MissingMediaElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): TransparentNodeElement(node,descriptor) {
    private var saved=CompoundTag()
    override fun initialize() { connect() }
    override fun readFromNBT(tag: CompoundTag) { saved=tag.copy();super.readFromNBT(tag) }
    override fun writeToNBT(tag: CompoundTag) { tag.merge(saved.copy());super.writeToNBT(tag) }
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)saved=tag.copy() }
    override fun getItemStackNBT()=saved.copy().apply { remove("applianceCord") }
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        player.displayClientMessage(Component.literal(tr("Install Electrical Age Media to use this saved device")),true)
        return true
    }
}

class MissingMediaRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor),BakedNodeRenderer {
    private val cabinet by lazy { IndustrialDescriptor(IndustrialKind.SCADA) }
    override fun draw()=TerrainModels.body(this,listOf(front)) { cabinet.draw(front ?: Direction.ZP) }
}
