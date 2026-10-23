package mods.eln.compat.mixin;

import mods.eln.compat.RefrigeratorPreservation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional integration. Prevents both timer advancement and rotten-item replacement at source. */
@Pseudo
@Mixin(targets = "com.mrbysco.spoiled.util.SpoilHelper", remap = false)
public abstract class SpoiledRefrigeratorMixin {
    @Inject(method = "getSpoilRecipe", at = @At("HEAD"), cancellable = true, require = 0)
    private static void eln$coldStorage(Level level, ItemStack stack, CallbackInfoReturnable<RecipeHolder<?>> cir) {
        if (RefrigeratorPreservation.isCold(stack)) cir.setReturnValue(null);
    }
}
