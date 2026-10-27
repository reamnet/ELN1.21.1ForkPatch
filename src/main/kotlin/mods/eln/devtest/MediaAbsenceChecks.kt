package mods.eln.devtest

import mods.eln.Eln
import mods.eln.api.media.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files

/** Core-only client boot proves that the optional addon does not initialize a browser. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object MediaAbsenceChecks {
    private var done=false
    private var ticks=0
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.mediaAbsenceChecks") || done)return
        val mc=Minecraft.getInstance()
        if(mc.screen !is TitleScreen || mc.overlay!=null || ++ticks<20)return
        done=true
        var failure: Throwable?=null
        try {
            check(!MediaAddons.available)
            for(type in ReservedMediaDevice.entries)check((Eln.transparentNodeItem.subItemList[type.legacyId] as MissingMediaDescriptor).isHidden())
            try { Class.forName("com.cinemamod.mcef.MCEF");error("MCEF is present in a core-only client") } catch(_: ClassNotFoundException) { }
            check(MediaAbsenceChecks::class.java.getResourceAsStream("/assets/eln/media-runtime/manifest.json")==null)
        } catch(e: Throwable) { failure=e }
        val output=mc.gameDirectory.toPath().resolve("media-absence-checks/report.json");Files.createDirectories(output.parent)
        Files.writeString(output,com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mapOf("coreClientStarted" to (failure==null),"mediaAddonPresent" to MediaAddons.available,"failure" to failure?.stackTraceToString())))
        if(failure==null)Eln.LOGGER.info("MEDIA_ABSENCE COMPLETE") else Eln.LOGGER.error("MEDIA_ABSENCE FAIL",failure)
        val status=if(failure==null)0 else 1
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(status) } }
    }
}
