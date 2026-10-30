package mods.eln.client.itemrender

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.utility.DistributionPoleDescriptor
import mods.eln.utility.TransmissionDescriptor
import mods.eln.transparentnode.utilitylight.UtilityStreetLightDescriptor
import net.minecraft.resources.ResourceLocation

/** The original ELN catalog uses shaded 32 px sprites, while held objects retain their models. */
object CatalogItemPresentation {
    @JvmStatic fun icon(descriptor: GenericItemBlockUsingDamageDescriptor): ResourceLocation? {
        if (java.lang.Boolean.getBoolean("eln.exportCatalogMeshes")) return null
        val catalog = descriptor is LampSocketDescriptor && descriptor.fixture != null ||
            descriptor is DistributionPoleDescriptor || descriptor is TransmissionDescriptor ||
            descriptor is UtilityStreetLightDescriptor
        return if (catalog) ResourceLocation.fromNamespaceAndPath("eln", "textures/blocks/catalog/${descriptor.iconName}.png") else null
    }
}
