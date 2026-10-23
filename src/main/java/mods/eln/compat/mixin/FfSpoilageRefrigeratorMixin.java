package mods.eln.compat.mixin;

import mods.eln.compat.RefrigeratorPreservation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.fyxe.fffoodspoilageandpreservation.util.SpoilageHelper", remap = false)
public abstract class FfSpoilageRefrigeratorMixin {
    @Inject(method = "advance(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;DD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void eln$coldStorage(ItemStack stack, Level level, double storage, double temperature, CallbackInfoReturnable<Boolean> cir) {
        if (RefrigeratorPreservation.isCold(stack)) cir.setReturnValue(false);
    }
}
