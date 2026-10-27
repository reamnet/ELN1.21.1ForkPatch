package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.industrial.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in real Chromium video/input/power campaign. Supply an HTTP test page with the documented fixture. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object TelevisionChecks {
    private val mc get()=Minecraft.getInstance()
    private val at=BlockPos(256,80,256)
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var task: CompletableFuture<Unit>?=null
    private lateinit var fixtures: NativeCampaignFixtures
    private lateinit var device: IndustrialElement
    private var render: IndustrialRender?=null
    @Volatile private var text=""
    private var identity: Any?=null
    private val passed=ArrayList<String>();private val values=linkedMapOf<String,Any>()
    private val output get()=mc.gameDirectory.toPath().resolve("television-checks")
    private fun server(action: ()->Unit) { ticks=0;task=mc.singleplayerServer!!.submit<Unit>(action) }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun setup() {
        val w=mc.singleplayerServer!!.overworld();val p=w.players().first();fixtures=NativeCampaignFixtures(w,p)
        for(x in 15..17)for(z in 15..17)w.setChunkForced(x,z,true)
        for(x in -5..5)for(z in -4..6)w.setBlockAndUpdate(at.offset(x,-1,z),Blocks.STONE.defaultBlockState())
        device=fixtures.placeMachine(Eln.findItemStack(IndustrialKind.TV.title,1),at) as IndustrialElement
        device.front=Direction.ZP;device.reconnect();fixtures.wire(at.west());fixtures.source(at.west(2),120.0)
        check(device.playMedia(System.getProperty("eln.tvTestUrl")))
        p.teleportTo(w,256.5,80.0,260.5,180f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(256.5,81.1,256.5))
        w.setDayTime(6000)
    }
    private fun state()=try { JsonParser.parseString(text.substringAfter("ELN_TV_STATE:").substringBefore(":END")).asJsonObject } catch(_: Exception) { null }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"measurements" to values,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.LOGGER.info("TELEVISION COMPLETE") else Eln.LOGGER.error("TELEVISION FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.televisionChecks") || done)return
        try {
            ticks++;if(ticks%200==0)Eln.LOGGER.info("TV phase={} position={} yaw={} pitch={} screen={}",phase,mc.player?.position(),mc.player?.yRot,mc.player?.xRot,mc.screen?.javaClass?.simpleName);check(++total<5000) { "TV campaign timeout phase $phase: $text" }
            render?.television?.browser?.let { if(ticks%20==0)it.getText { value->text=value } }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.renderDistance().set(6)
                    mc.createWorldOpenFlows().createFreshLevel("television-${System.currentTimeMillis()}",LevelSettings("Television",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(59059L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;server(::setup);phase=2 }
                2->if(task!!.isDone && ticks>80) {
                    task!!.join();render=(mc.level!!.getBlockEntity(at) as? TransparentNodeEntity)?.elementRender as? IndustrialRender
                    val r=render ?: return
                    mc.player!!.yRot=180f;mc.player!!.yRotO=180f;mc.player!!.xRot=7.4f;mc.player!!.xRotO=7.4f;mc.options.cameraType=net.minecraft.client.CameraType.FIRST_PERSON
                    val s=state() ?: return
                    if((s["time"]?.asDouble ?: 0.0)<1 || r.television.paintedFrames<10)return
                    check(!s["paused"].asBoolean && s["width"].asInt==320 && s["audioTracks"].asInt>0 && !s["muted"].asBoolean)
                    if(s["rms"].asDouble<=.001) { check(ticks<800) { "No decoded audio: $s" };return }
                    check(TerrainModels.meshFor(r)!=null)
                    values["browserPlayback"]=s;values["paintedFrames"]=r.television.paintedFrames
                    passed+="Real Chromium decodes moving WebM video and nonzero audio samples from an HTTP stream; static cabinet is baked"
                    photo("tv-playing-world");identity=r.television.browser
                    server {
                        check(device.working && device.resistor.power in 148.0..151.0);values["powerWatts"]=device.resistor.power
                        mods.eln.GuiHandler.open(fixtures.player,mods.eln.GuiHandler.nodeBaseOpen+Direction.ZP.int,fixtures.world,at.x,at.y,at.z)
                    };phase=3
                }
                3->if(task!!.isDone && mc.screen is TelevisionScreen && ticks>30) {
                    task!!.join();photo("tv-browser-controls")
                    val screen=mc.screen as TelevisionScreen
                    val scale=kotlin.math.min((screen.width-16)/16.0,(screen.height-92)/9.0).coerceAtLeast(1.0)
                    val w=(scale*16).toInt();val h=(scale*9).toInt();val x=(screen.width-w)/2
                    fun click(px: Int,py: Int) { val sx=x+px*w/960.0;val sy=36+py*h/540.0;screen.mouseClicked(sx,sy,0);screen.mouseReleased(sx,sy,0) }
                    click(380,316);click(70,316);"TV!".forEach { screen.charTyped(it,0) }
                    ticks=0;phase=4
                }
                4->if(ticks>30) {
                    val s=state() ?: return
                    if(s["clicks"].asInt<1 || s["input"].asString!="TV!")return
                    values["interactivePage"]=s;passed+="Real browser mouse clicks and typed characters pass through the TV screen to webpage controls"
                    (mc.screen as TelevisionScreen).onClose();ticks=0;phase=5
                }
                5->if(ticks>35) {
                    check(render!!.television.browser===identity && render!!.television.paintedFrames>10)
                    photo("tv-playing-world");passed+="Closing controls leaves the same browser and video playing on the in-world TV"
                    server { fixtures.voltage(at.west(2),0.0) };phase=6
                }
                6->if(task!!.isDone && ticks>55) {
                    task!!.join();check(!render!!.working && render!!.television.browser==null)
                    passed+="Loss of actual placed-cable power closes Chromium and stops playback"
                    photo("tv-power-off");server { fixtures.voltage(at.west(2),120.0) };phase=7
                }
                7->if(task!!.isDone && ticks>55 && render!!.television.paintedFrames>10) {
                    task!!.join();check(render!!.working && render!!.television.browser!=null)
                    passed+="Restoring electrical power recreates the browser and resumes the selected page"
                    server { fixtures.player.teleportTo(fixtures.world,256.5,80.0,300.5,180f,0f) };phase=8
                }
                8->if(task!!.isDone && ticks>35) {
                    task!!.join();check(render!!.television.browser==null)
                    passed+="Leaving the 32-block viewing range releases the browser and its texture/audio resources"
                    finish(null)
                }
            }
        } catch(e: Throwable) { finish(e) }
    }
}
