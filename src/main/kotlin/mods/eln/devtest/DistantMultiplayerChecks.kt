package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.DistantModels
import mods.eln.gridnode.*
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.*
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.nio.file.Files
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/** Opt-in localhost server; never changes real servers without the explicit JVM flag. */
@EventBusSubscriber(modid=Eln.MODID)
object DistantMultiplayerServer {
    @SubscribeEvent fun start(event: ServerStartedEvent) {
        if(!java.lang.Boolean.getBoolean("eln.distantMultiplayerServer"))return
        val w=event.server.overworld();val p=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(w)
        val f=NativeCampaignFixtures(w,p)
        val wire=UtilityCableDescriptor.allDescriptors().first { !it.insulated && !it.melted }
        w.setDayTime(6000);w.setWeatherParameters(12000,0,false,false)
        for(i in 0..3) {
            val name=when(i) { 0->TransmissionKind.POLE_230.title;1->TransmissionKind.POLE_69.title;2->DistributionKind.THREE.title;else->DistributionKind.SECONDARY.title }
            val elements=(0..1).map { j->
                val pos=BlockPos(256+i*24,80,256+j*48)
                for(x in -1..1)for(z in -1..1)w.setChunkForced((pos.x shr 4)+x,(pos.z shr 4)+z,true)
                w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                if(w.getBlockEntity(pos) is TransparentNodeEntity)f.machine(pos) as GridElement
                else f.placeMachine(Eln.findItemStack(name,1),pos) as GridElement
            }
            if(elements[0].gridLinkList.isEmpty())GridLink.addLink(elements[0],elements[1],if(i==3)50 else 52,if(i==3)50 else 52,wire,144)
        }
        for((i,kind)in listOf(TransmissionKind.STEP_230,TransmissionKind.STEP_69).withIndex()) {
            val pos=BlockPos(280+i*32,80,280)
            if(w.getBlockEntity(pos)==null) {
                w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                (f.placeMachine(Eln.findItemStack(kind.title,1),pos) as TransmissionElement).apply { front=mods.eln.misc.Direction.XN;updateIdealRenderAngle();needPublish() }
            }
        }
        Eln.logger.info("DISTANT_MULTIPLAYER SERVER READY")
    }
    @SubscribeEvent fun join(event: PlayerEvent.PlayerLoggedInEvent) {
        if(!java.lang.Boolean.getBoolean("eln.distantMultiplayerServer"))return
        val p=event.entity as? ServerPlayer ?: return
        p.server.playerList.op(p.gameProfile);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
        p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
    }
}

/** Uses a real remote connection and DH's actual per-group render callbacks. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object DistantMultiplayerChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var originalView=0
    private var nearRequested=false
    private var cacheFile: java.io.File?=null
    private val passed=ArrayList<String>();private val measurements=linkedMapOf<String,Any>()
    private val attached=IdentityHashMap<Any,Boolean>()
    private val bodyDraws=AtomicLong();private val wireDraws=AtomicLong()
    private var dhWorld: Any?=null;private var dhWrapper: Any?=null;private var oldRegistry: Any?=null
    private val output get()=mc.gameDirectory.toPath().resolve(System.getProperty("eln.distantMultiplayerOutput","distant-multiplayer-checks"))
    private fun attachCounters() {
        val field=DistantModels.javaClass.getDeclaredField("entries").apply { isAccessible=true }
        val entries=field.get(DistantModels) as Map<*,*>
        for((key,entry)in entries) {
            val group=entry!!.javaClass.getDeclaredField("group").apply { isAccessible=true }.get(entry) ?: continue
            if(attached.put(group,true)!=null)continue
            val face=key!!.javaClass.getDeclaredField("face").apply { isAccessible=true }.getInt(key)
            group.javaClass.getMethod("setPreRenderFunc",java.util.function.Consumer::class.java).invoke(group,java.util.function.Consumer<Any> { if(face==6)wireDraws.incrementAndGet() else bodyDraws.incrementAndGet() })
        }
    }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun resetDhLevel() {
        val delayed=Class.forName("com.seibel.distanthorizons.api.DhApi\$Delayed")
        val proxy=delayed.getField("worldProxy").get(null)
        dhWrapper=Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy").getMethod("getSinglePlayerLevel").invoke(proxy)
        oldRegistry=Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper").getMethod("getRenderRegister").invoke(dhWrapper)
        dhWorld=Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi").getMethod("getAbstractDhWorld").invoke(null)
        val levelType=Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper")
        check(dhWorld!!.javaClass.getMethod("unloadLevel",levelType).invoke(dhWorld,dhWrapper)==true)
        bodyDraws.set(0);wireDraws.set(0)
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        measurements["bodyRenderCalls"]=bodyDraws.get();measurements["wireRenderCalls"]=wireDraws.get()
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"measurements" to measurements,"distant" to DistantModels.diagnostics())))
        mc.options.renderDistance().set(originalView)
        if(error==null)Eln.logger.info("DISTANT_MULTIPLAYER COMPLETE") else Eln.logger.error("DISTANT_MULTIPLAYER FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.distantMultiplayerChecks") || done)return
        try {
            ticks++;check(++total<2400) { "Timed out at phase $phase: ${DistantModels.diagnostics()}" }
            if(mc.level!=null)attachCounters()
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    originalView=mc.options.renderDistance().get();mc.options.renderDistance().set(4)
                    ConnectScreen.startConnecting(mc.screen!!,mc,ServerAddress.parseString("127.0.0.1:25575"),ServerData("ELN localhost check","127.0.0.1:25575",ServerData.Type.OTHER),false,null)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && !nearRequested) {
                    nearRequested=true;mc.connection!!.sendCommand("tp @s 292 112 350 180 12");ticks=0
                } else if(mc.player!=null && mc.screen==null && ticks>220) {
                    check(mc.singleplayerServer==null)
                    check(mc.level!!.getBlockEntity(BlockPos(256,80,256)) is TransparentNodeEntity)
                    measurements["near"]=DistantModels.diagnostics();photo("near");passed+="Joined a real dedicated server and received the transmission/distribution fixtures"
                    mc.options.hideGui=true;bodyDraws.set(0);wireDraws.set(0)
                    if(java.lang.Boolean.getBoolean("eln.substationPreviewOnly")) {
                        mc.connection!!.sendCommand("time set 6000");mc.connection!!.sendCommand("weather clear")
                        mc.connection!!.sendCommand("tp @s 285 86 287 144 16");phase=5
                    } else { mc.connection!!.sendCommand("tp @s 650 125 620 133 4");phase=2 }
                    ticks=0
                }
                2->if(ticks>240) {
                    check(mc.level!!.getBlockEntity(BlockPos(256,80,256))==null)
                    photo("distant");measurements["far"]=DistantModels.diagnostics()
                    check(bodyDraws.get()>0) { "DH did not draw any pole body after vanilla chunk unload" }
                    check(wireDraws.get()>0) { "DH did not draw any conductor after vanilla chunk unload" }
                    passed+="DH actually rendered pole bodies and wire groups after normal multiplayer chunks unloaded"
                    measurements["initialBodyRenderCalls"]=bodyDraws.get();measurements["initialWireRenderCalls"]=wireDraws.get()
                    resetDhLevel();phase=3;ticks=0
                }
                3->if(ticks>60) {
                    val type=Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper")
                    dhWorld!!.javaClass.getMethod("getOrLoadLevel",type).invoke(dhWorld,dhWrapper)
                    phase=4;ticks=0
                }
                4->if(ticks>200) {
                    val next=Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper").getMethod("getRenderRegister").invoke(dhWrapper)
                    check(next!=null && next!==oldRegistry) { "DH did not replace the test renderer" }
                    check(bodyDraws.get()>0 && wireDraws.get()>0) { "Pole/wire rendering did not recover after DH replaced its registry" }
                    photo("renderer-recovered");passed+="Real DH registry recreation preserves pole and conductor draw calls without reloading vanilla chunks"
                    mc.connection!!.sendCommand("tp @s 285 86 287 144 16");phase=5;ticks=0
                }
                5->if(ticks>140) {
                    photo("substation-230")
                    for(pos in listOf(BlockPos(280,80,280),BlockPos(312,80,280))) {
                        val render=(mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender!!
                        val mesh=mods.eln.client.terrain.TerrainModels.meshFor(render) ?: error("Unbaked substation")
                        check(mesh.batches.any { it.material.texture.path=="model/transmissionmarkings/labels.png" })
                    }
                    passed+="Both substations retain baked voltage/phase nameplates"
                    mc.connection!!.sendCommand("tp @s 317 85 287 144 20");phase=6;ticks=0
                }
                6->if(ticks>100) {
                    photo("substation-69")
                    if(java.lang.Boolean.getBoolean("eln.substationPreviewOnly"))finish(null)
                    else { mc.connection!!.sendCommand("tp @s 650 125 620 133 4");phase=7;ticks=0 }
                }
                7->if(ticks>100) {
                    cacheFile=DistantModels.javaClass.getDeclaredField("save").apply { isAccessible=true }.get(DistantModels) as? java.io.File
                    mc.disconnect();mc.setScreen(TitleScreen());phase=8;ticks=0
                }
                8->if(ticks>25) {
                    check(DistantModels.diagnostics()["cached"]==0 && cacheFile?.isFile==true)
                    passed+="Disconnect clears live groups and flushes the multiplayer snapshot cache"
                    bodyDraws.set(0);wireDraws.set(0)
                    ConnectScreen.startConnecting(mc.screen!!,mc,ServerAddress.parseString("127.0.0.1:25575"),ServerData("ELN localhost check","127.0.0.1:25575",ServerData.Type.OTHER),false,null)
                    phase=9;ticks=0
                }
                9->if(mc.player!=null && mc.screen==null && ticks>220) {
                    check(mc.level!!.getBlockEntity(BlockPos(256,80,256))==null)
                    check(bodyDraws.get()>0 && wireDraws.get()>0) { "Cached poles and conductors did not render after reconnecting beyond normal range" }
                    photo("reconnected");passed+="Persisted multiplayer poles and conductors draw after reconnecting outside vanilla range"
                    finish(null)
                }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
