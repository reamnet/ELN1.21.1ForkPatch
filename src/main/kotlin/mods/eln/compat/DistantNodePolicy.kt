package mods.eln.compat

import mods.eln.ghost.GhostBlock
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.transparent.TransparentNodeBlock
import net.minecraft.world.level.block.state.BlockState

object DistantNodePolicy {
    /** Placeholder volume is not the object's mesh. Covers have position-specific snapshots. */
    @JvmStatic fun ignore(state: BlockState)=state.block is GhostBlock || state.block is TransparentNodeBlock ||
        state.block is SixNodeBlock
}
