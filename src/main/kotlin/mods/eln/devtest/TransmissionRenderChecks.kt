package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.*
import mods.eln.gridnode.GridLink
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
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

/** Real renderer regression: rotated insulators overhang entirely air-only sections. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object TransmissionRenderChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val passed=ArrayList<String>();private val measurements=linkedMapOf<String,Any>()
    private val poles=listOf(BlockPos(511,80,511),BlockPos(559,80,559),BlockPos(543,80,511),BlockPos(591,80,559))
    private val emptySections=linkedSetOf<Long>()
    private var originalView=0
    private val output get()=mc.gameDirectory.toPath().resolve(System.getProperty("eln.transmissionRenderOutput","transmission-render-checks"))
    private fun server(action: ()->Unit) { work=mc.singleplayerServer!!.submit<Unit> { action() };ticks=0 }
    private fun camera(x: Double,y: Double,z: Double,at: Vec3) {
        val p=mc.singleplayerServer!!.playerList.players.first();p.teleportTo(p.serverLevel(),x,y,z,0f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,at)
    }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        w.setWeatherParameters(12000,0,false,false);w.setDayTime(6000)
        val elements=poles.mapIndexed { i,pos->
            for(x in -1..1)for(z in -1..1)w.setChunkForced((pos.x shr 4)+x,(pos.z shr 4)+z,true)
            w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            f.placeMachine(Eln.findItemStack(if(i<2)TransmissionKind.POLE_69.title else TransmissionKind.POLE_230.title,1),pos) as TransmissionElement
        }
        val wire=UtilityCableDescriptor.allDescriptors().first { !it.insulated && !it.melted }
        GridLink.addLink(elements[0],elements[1],52,52,wire,204)
        GridLink.addLink(elements[2],elements[3],52,52,wire,204)
        camera(552.0,112.0,610.0,Vec3(552.0,97.0,535.0))
    }
    private fun renderSections(): Map<*,*> {
        val cls=Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
        val renderer=cls.getMethod("instance").invoke(null)
        val manager=cls.getDeclaredField("renderSectionManager").apply { isAccessible=true }.get(renderer)
        return manager.javaClass.getDeclaredField("sectionByPosition").apply { isAccessible=true }.get(manager) as Map<*,*>
    }
    private fun checkEmptySections() {
        val sections=renderSections()
        for(key in emptySections) {
            val section=sections[key] ?: error("Sodium section missing: ${SectionPos.of(key)}")
            val flags=section.javaClass.getMethod("getFlags").invoke(section) as Int
            check(flags and 1!=0) { "Air-only overhang has no uploaded terrain: ${SectionPos.of(key)}, flags=$flags" }
        }
    }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"measurements" to measurements,"distant" to DistantModels.diagnostics())))
        mc.options.renderDistance().set(originalView)
        if(error==null)Eln.logger.info("TRANSMISSION_RENDER COMPLETE") else Eln.logger.error("TRANSMISSION_RENDER FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.transmissionRenderChecks") || done)return
        try {
            ticks++;check(++total<3000) { "Timed out at phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    originalView=mc.options.renderDistance().get();mc.options.renderDistance().set(8)
                    mc.createWorldOpenFlows().createFreshLevel("transmission-render-${System.currentTimeMillis()}",LevelSettings("Transmission render",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(53053L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;server { setup() };phase=2 }
                2->if(work!!.isDone && ticks>240) {
                    work!!.join()
                    for(pos in poles) {
                        val render=(mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as TransmissionRender
                        check(TerrainModels.meshFor(render)!=null)
                        check(kotlin.math.abs(render.highVoltageRenderAngle)%90f>1f)
                        for(key in TerrainModels.sectionsFor(render)) {
                            val s=SectionPos.of(key);val chunk=mc.level!!.chunkSource.getChunk(s.x(),s.z(),net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false) ?: continue
                            if(chunk.getSection(mc.level!!.getSectionIndexFromSectionY(s.y())).hasOnlyAir())emptySections+=key
                        }
                    }
                    check(emptySections.isNotEmpty()) { "Fixture did not overhang any air-only sections" }
                    measurements["airOnlyOverhangSections"]=emptySections.map { SectionPos.of(it).toString() }
                    checkEmptySections();passed+="69/230 kV rotated porcelain uploads real Sodium terrain in air-only sections without filler blocks"
                    check(DistantModels.diagnostics()["failed"]==false)
                    check(DistantModels.diagnostics()["active"]==0) { "Distant proxies overlap detailed nearby geometry" }
                    passed+="Nearby loaded transmission poles and wires have no active DH proxy"
                    photo("near");server { camera(543.0,105.0,521.0,Vec3(543.0,103.0,511.0)) };phase=3
                }
                3->if(work!!.isDone && ticks>80) {
                    work!!.join();checkEmptySections();photo("insulators");passed+="Air-only insulator geometry remains uploaded after flying close"
                    server { camera(900.0,120.0,800.0,Vec3(552.0,97.0,535.0)) };phase=4
                }
                4->if(work!!.isDone && ticks>180) {
                    work!!.join();check((DistantModels.diagnostics()["active"] as Int)>0);check(DistantModels.diagnostics()["failed"]==false)
                    photo("distant");passed+="Distant Horizons supplies transmission silhouettes and thin conductors after normal chunks unload"
                    server { camera(552.0,112.0,610.0,Vec3(552.0,97.0,535.0)) };phase=5
                }
                5->if(work!!.isDone && ticks>180) {
                    work!!.join();checkEmptySections();check(DistantModels.diagnostics()["active"]==0)
                    photo("returned");passed+="Returning to the detailed model restores air-only overhangs without placing blocks"
                    finish(null)
                }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
