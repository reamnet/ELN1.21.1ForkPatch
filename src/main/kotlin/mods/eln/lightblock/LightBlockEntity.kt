package mods.eln.lightblock

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils
import mods.eln.node.LoadedBlockEntities
import mods.eln.node.LoadedBlockStates
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Supplier
import net.minecraft.world.level.Level
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

class LightBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(TYPE.get(), pos, state) {

    /** 1.7.10's `worldObj`. */
    val world: Level
        get() = level!!

    companion object {
        /** Registered by Eln through ElnRegistry.registerBlockEntity. */
        @JvmField
        var TYPE: Supplier<BlockEntityType<LightBlockEntity>> = Supplier { throw IllegalStateException("LightBlockEntity type not registered") }

        @JvmField
        val observers: MutableList<LightBlockObserver> = mutableListOf()

        @JvmStatic
        fun addLight(w: Level, x: Int, y: Int, z: Int, light: Int, timeout: Int) {
            if (w.isClientSide || w.isOutsideBuildHeight(y)) return
            val pos = BlockPos(x, y, z)
            val old = LoadedBlockStates.get(w, pos) ?: return
            if (old.block !== Eln.lightBlock) {
                val water=old.block===net.minecraft.world.level.block.Blocks.WATER && old.fluidState.isSource
                if (!old.isAir && !water) return
                if(light<=0)return
                if (!w.setBlock(pos,Eln.lightBlock.stateForMeta(light).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,water),2)) return
            }

            val t = LoadedBlockEntities.get(w, pos)

            if (t is LightBlockEntity) t.addLight(light, timeout)
            else Utils.println("Error in setting light at %d %d %d", x, y, z)
        }

        @JvmStatic
        fun addLight(coord: Coordinate, light: Int, timeout: Int) {
            addLight(coord.world(), coord.x, coord.y, coord.z, light, timeout)
        }
    }

    interface LightBlockObserver {

        fun lightBlockDestructor(coord: Coordinate)

    }

    private val leases = LightLeases()

    private fun addLight(light: Int, timeout: Int) {
        leases.refresh(light, timeout)
    }

    /** Ticked by the block's BlockEntityTicker (server only). */
    fun update() {
        if (world.isClientSide) return

        val light = leases.advance()
        if (light == null) {
            world.setBlock(blockPos,world.getBlockState(blockPos).fluidState.createLegacyBlock(),3)
            Utils.println("Destroy light at %d %d %d", xCoord, yCoord, zCoord)
            return
        }

        val state = world.getBlockState(blockPos)
        if (state.block === Eln.lightBlock && light != state.getValue(LightBlock.LIGHT)) {
            // The light level is a blockstate property, not a metadata nibble; the light engine follows the state change.
            world.setBlock(blockPos, state.setValue(LightBlock.LIGHT, light), 2)
        }
    }

}
