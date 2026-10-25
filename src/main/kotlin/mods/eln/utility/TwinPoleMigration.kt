package mods.eln.utility

import net.minecraft.nbt.CompoundTag

/** Preserve controls from the former transformer pole without interpreting its HV spans as LV. */
object TwinPoleMigration {
    fun legacy(kind: DistributionKind,tag: CompoundTag)=kind.twinLight && !tag.contains("utilityLamp") && tag.contains("mainsOutputVoltage")
    fun settings(kind: DistributionKind,tag: CompoundTag): CompoundTag {
        if(!legacy(kind,tag))return tag
        return tag.copy().apply {
            putInt("utilityLampVoltage",tag.getInt("mainsOutputVoltage"))
            putBoolean("utilityLamp",(!tag.contains("mainsEnabled") || tag.getBoolean("mainsEnabled")) && (!tag.contains("mainsLampEnabled") || tag.getBoolean("mainsLampEnabled")))
            putBoolean("utilityDusk",tag.getBoolean("mainsLampAutomatic"))
            putInt("utilityLampYaw",tag.getInt("mainsLampYaw"))
            putInt("utilityLampPitch",tag.getInt("mainsLampPitch"))
            putInt("utilityLampHeight",tag.getInt("mainsLampHeightStep"))
            putInt("utilityTerminal",tag.getInt("poleSelectedTerminal").takeIf { it==50 || it in 40..42 } ?: 50)
        }
    }
}
