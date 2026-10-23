package mods.eln.compat.mixin;

import mods.eln.compat.RefrigeratorPreservation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.hanyu.freshness.ModEvents", remap = false)
public abstract class FreshnessRefrigeratorMixin {
    @Inject(method = "checkAndRot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void eln$coldStorage(ItemStack stack, Level level, CallbackInfo ci) {
        if (RefrigeratorPreservation.isCold(stack)) ci.cancel();
    }
}
