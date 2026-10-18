package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.transparentnode.worklight.WorkLightElement
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opens the successful work-light test world in a new JVM and captures its restored models. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object KitchenPhotoChecks {
    private val mc get()=Minecraft.getInstance()
    private var worldName="";private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var index=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun finish(error: Throwable?) {
        done=true;mc.options.hideGui=false
        Files.writeString(mc.gameDirectory.toPath().resolve("kitchen-photo-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.logger.info("KITCHEN_COLD COMPLETE {} checks",passed.size) else Eln.logger.error("KITCHEN_COLD FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(5000);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.kitchenPhotoChecks") || done)return
        try {
            check(++total<6000);ticks++
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val report=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("work-light-checks.json"))).asJsonObject
                    check(report.get("failure")==null || report.get("failure").isJsonNull)
                    check(report.getAsJsonArray("passed").size()>=8)
                    worldName=report.get("world").asString
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot open successful fixture world") };step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        for(x in 15..19)for(z in 15..21)p.serverLevel().setChunkForced(x,z,true)
                        p.serverLevel().dayTime=6000;p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
                        p.teleportTo(p.serverLevel(),259.5,66.5,260.5,143f,15f)
                    };ready=mc.singleplayerServer!!.tickCount+100;step(2)
                }
                2 -> if(settled()) {
                    server { p ->
                        KitchenNativeFixtures.verify(p)
                        val e=NativeCampaignFixtures(p.serverLevel(),p).machine(BlockPos(256,66,256)) as WorkLightElement
                        check(!e.on && e.bulbCount==2 && e.tankLevel in .01.. .5 && e.brightness>=13)
                        check(abs(e.positiveLoad.voltage-120)<.1)
                        passed+="A fresh JVM restores all five kitchen inventories/outputs, refrigerator cooling and the fueled cart's wired light without replacing a cable"
                    };step(3)
                }
                3 -> if(settled()) { mc.options.hideGui=true;Screenshot.grab(mc.gameDirectory,"work-light-cart-front.png",mc.mainRenderTarget) {};step(4) }
                4 -> {
                    server { p -> val pos=BlockPos(256+index*4,66,268);p.teleportTo(p.serverLevel(),pos.x+2.5,pos.y+1.0,pos.z+3.0,140f,24f) }
                    ready=mc.singleplayerServer!!.tickCount+30;step(5)
                }
                5 -> if(settled()) {
                    Screenshot.grab(mc.gameDirectory,"work-light-kitchen-$index-model.png",mc.mainRenderTarget) {}
                    index++;if(index<5)step(4) else { passed+="All five restored appliance models and the front of the twin work light render with closed exterior faces";step(6) }
                }
                6 -> finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
}
