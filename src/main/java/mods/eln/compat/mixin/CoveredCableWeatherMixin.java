package mods.eln.compat.mixin;

import mods.eln.node.six.SixNodeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic cable shapes have no legacy-solid cache. Weather heightmaps only see
 * BlockState, so the persisted cover bit must participate in their predicate. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CoveredCableWeatherMixin {
    @Inject(method = "blocksMotion", at = @At("HEAD"), cancellable = true, require = 1)
    private void eln$coveredRoof(CallbackInfoReturnable<Boolean> result) {
        BlockState state = (BlockState) (Object) this;
        if (state.getBlock() instanceof SixNodeBlock && state.getValue(SixNodeBlock.COVERED))
            result.setReturnValue(true);
    }
}
