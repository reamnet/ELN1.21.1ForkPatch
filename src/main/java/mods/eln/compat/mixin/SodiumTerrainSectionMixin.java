package mods.eln.compat.mixin;

import mods.eln.client.terrain.TerrainModels;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Sodium tests block occupancy before collecting NeoForge's additional section
 * geometry. Let an otherwise empty section reach that supported geometry hook.
 * This changes rendering setup only, never block data, lighting or simulation. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.world.LevelSlice", remap=false)
public abstract class SodiumTerrainSectionMixin {
    @Redirect(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z"), require=0)
    private static boolean eln$includeOverhangingGeometry(LevelChunkSection section, Level level, SectionPos pos, @Coerce Object cache) {
        return section.hasOnlyAir() && (Boolean.getBoolean("eln.disableSodiumTerrainFix") || !TerrainModels.hasSectionGeometry(level,pos.asLong()));
    }
}
