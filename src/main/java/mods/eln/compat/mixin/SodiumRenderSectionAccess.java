package mods.eln.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Primitive-only access keeps Sodium optional at compile time and on servers. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSection", remap=false)
public interface SodiumRenderSectionAccess {
    @Invoker("getFlags") int eln$flags();
    @Invoker("isBuilt") boolean eln$built();
}
