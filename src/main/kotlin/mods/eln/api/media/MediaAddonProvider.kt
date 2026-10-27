package mods.eln.api.media

import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.node.transparent.TransparentNodeDescriptor
import net.neoforged.fml.ModList

/** Stable save identifiers, not playable media implementations. Never renumber these slots. */
enum class ReservedMediaDevice(val title: String,val subId: Int) {
    JUKEBOX(I18N.TR_NAME(I18N.Type.NONE,"Powered Web Jukebox"),9),
    TV(I18N.TR_NAME(I18N.Type.NONE,"Powered Browser Television"),10);
    val legacyId get()=(126 shl 6)+subId
}

/** The optional addon owns its descriptors, power circuits, GUIs, rendering and backends. */
interface MediaAddonProvider {
    fun descriptor(device: ReservedMediaDevice): TransparentNodeDescriptor
}

/** Optional devices may retain the grid console's normal remote enable/disable behavior. */
interface RemoteSwitchableDevice {
    fun setRemoteEnabled(enabled: Boolean)
}

/** Nearby, UUID-addressed node controls from a screen that has no inventory container. */
interface StandaloneDeviceControls

object MediaAddons {
    private val provider: MediaAddonProvider? by lazy {
        if(ModList.get()?.isLoaded("eamedia")!=true)null else {
            // All resolved mod classes are available before constructor callbacks. Loading the
            // addon's common factory here avoids relying on cross-mod constructor ordering.
            val type=Class.forName("mods.eamedia.MediaAddon",true,MediaAddons::class.java.classLoader)
            type.getDeclaredConstructor().newInstance() as MediaAddonProvider
        }
    }
    val available get()=provider!=null
    fun registerReservedDevices() {
        for(device in ReservedMediaDevice.entries) {
            val descriptor=provider?.descriptor(device) ?: MissingMediaDescriptor(device)
            if(provider==null)Eln.transparentNodeItem.addWithoutRegistry(device.legacyId,descriptor)
            else Eln.transparentNodeItem.addDescriptor(device.legacyId,descriptor)
        }
    }
}
