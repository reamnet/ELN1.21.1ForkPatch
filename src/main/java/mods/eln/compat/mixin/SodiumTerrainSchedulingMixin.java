package mods.eln.compat.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import mods.eln.client.terrain.TerrainModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Air-only sections were marked EMPTY before ELN's model arrived. Register
 * their first geometry as an initial rebuild so Sodium visits them at all. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap=false)
public abstract class SodiumTerrainSchedulingMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow @Final private Long2ReferenceMap<Object> sectionByPosition;
    @Shadow public abstract void onSectionAdded(int x,int y,int z);
    @Shadow public abstract void onSectionRemoved(int x,int y,int z);

    @Redirect(method="onSectionAdded", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z"), require=0)
    private boolean eln$includeOverhangOnLoad(LevelChunkSection section,int x,int y,int z) {
        return section.hasOnlyAir() && (Boolean.getBoolean("eln.disableSodiumTerrainFix") || !TerrainModels.hasSectionGeometry(level,SectionPos.asLong(x,y,z)));
    }

    @Inject(method="scheduleRebuild", at=@At("TAIL"), require=0)
    private void eln$scheduleFirstOverhang(int x,int y,int z,boolean important,CallbackInfo ci) {
        if(Boolean.getBoolean("eln.disableSodiumTerrainFix") || !TerrainModels.hasSectionGeometry(level,SectionPos.asLong(x,y,z)))return;
        Object value=sectionByPosition.get(SectionPos.asLong(x,y,z));
        if(value instanceof SodiumRenderSectionAccess section && section.eln$built() && section.eln$flags()==0) {
            onSectionRemoved(x,y,z);
            onSectionAdded(x,y,z);
        }
    }
}
