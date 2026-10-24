package mods.eln.compat.mixin;

import mods.eln.compat.DistantNodePolicy;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional DH 3.3 NeoForge wrapper. Detailed objects use DH's custom-object API. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge", remap=false)
public abstract class DistantHorizonsNodeMixin {
    @Inject(method="isAir(Lnet/minecraft/world/level/block/state/BlockState;)Z", at=@At("HEAD"), cancellable=true, require=0)
    private static void eln$skipPlaceholder(BlockState state, CallbackInfoReturnable<Boolean> result) {
        if(state != null && DistantNodePolicy.ignore(state)) result.setReturnValue(true);
    }
}
