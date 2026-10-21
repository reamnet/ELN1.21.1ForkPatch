package mods.eln.node.six

import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelProperty

/** Immutable state passed to chunk workers; device renderers stay in the BER. */
object SixNodeCoverData {
    val COVER = ModelProperty<BlockState>()
}
