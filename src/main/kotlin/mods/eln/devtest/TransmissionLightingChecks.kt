package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.DistantModels
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in real-client validation, including DH's actual optional API. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object TransmissionLightingChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val passed=ArrayList<String>()
    private val output get()=mc.gameDirectory.toPath().resolve("transmission-lighting-checks")
    private val roof=BlockPos(340,87,300)
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel()
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        val helper=NativeCampaignFixtures(w,p)
        val objects=listOf(TransmissionKind.POLE_69,TransmissionKind.POLE_230,TransmissionKind.STEP_69,
            TransmissionKind.STEP_230,TransmissionKind.SWITCH_69,TransmissionKind.RISER_230)
        objects.forEachIndexed { i,kind->
            val pos=BlockPos(256+i*16,80,256);w.setChunkForced(pos.x shr 4,pos.z shr 4,true)
            w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            helper.placeMachine(Eln.findItemStack(kind.title,1),pos)
        }
        for((i,name) in listOf("Wind Turbine","Utility Wind Turbine 3.2kV").withIndex()) {
            val pos=BlockPos(224,80,256+i*24)
            w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            helper.placeMachine(Eln.findItemStack(name,1),pos)
        }
        val farPole=BlockPos(256,80,320)
        w.setBlockAndUpdate(farPole.below(),Blocks.STONE.defaultBlockState())
        val remote=helper.placeMachine(Eln.findItemStack(TransmissionKind.POLE_69.title,1),farPole) as TransmissionElement
        val first=helper.machine(BlockPos(256,80,256)) as TransmissionElement
        val conductor=mods.eln.sixnode.electricalcable.UtilityCableDescriptor.allDescriptors().first { !it.melted && !it.insulated }
        val spool=conductor.newItemStack(1)
        check(first.onTryGridConnect(p,spool,conductor,Direction.XN))
        check(remote.onTryGridConnect(p,spool,conductor,Direction.XN))
        check(first.gridLinkList.isNotEmpty())
        for((i,kind) in listOf(DistributionKind.FUSED_TAP_LIGHT_THREE,DistributionKind.FUSED_TAP_TRANSFORMER_LIGHT_THREE).withIndex()) {
            val pos=BlockPos(256+i*16,80,280);w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            helper.placeMachine(Eln.findItemStack(kind.title,1),pos)
        }
        val lamp=BlockPos(294,90,280);w.setBlockAndUpdate(lamp.above(),Blocks.STONE.defaultBlockState())
        val desc=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==DecorativeLampStyle.WAREHOUSE_HIGH_BAY }
        check(Eln.sixNodeItem.placeBlockAt(desc.newItemStack(1),p,w,lamp,net.minecraft.core.Direction.DOWN,.5f,.5f,.5f))
        val node=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(lamp.x,lamp.y,lamp.z,w)) as SixNode
        val light=node.sideElementList.filterIsInstance<LampSocketElement>().single()
        light.suspension.set(6.75);light.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",4));light.needPublish()
        w.setBlockAndUpdate(roof.west(),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(helper.spool(),p,w,roof,net.minecraft.core.Direction.EAST,.5f,.5f,.5f))
        val cable=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(roof.x,roof.y,roof.z,w)) as SixNode
        cable.sixNodeCacheBlock=Blocks.STONE;cable.syncCoverState();cable.needPublish=true
        w.setWeatherParameters(0,12000,true,false)
        check(w.getHeight(Heightmap.Types.MOTION_BLOCKING,roof.x,roof.z)==roof.y+1)
        check(!w.isRainingAt(roof.below()))
        passed+="Covered cable updates the real motion-blocking weather heightmap"
        // Keep weather assertions above; use a clear scene for inspectable screenshots.
        w.setWeatherParameters(12000,0,false,false);w.setDayTime(6000)
        val outletPos=BlockPos(307,81,286)
        w.setBlockAndUpdate(outletPos.north(),Blocks.STONE.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("240 V 30 A Dryer Outlet",1),p,w,outletPos,net.minecraft.core.Direction.SOUTH))
        val dryerPos=BlockPos(307,80,289);w.setBlockAndUpdate(dryerPos.below(),Blocks.STONE.defaultBlockState())
        val dryer=helper.placeMachine(Eln.findItemStack("Electric Dryer",1),dryerPos) as mods.eln.transparentnode.cord.CordDevice
        val outlet=helper.machine(outletPos) as mods.eln.transparentnode.cord.OutletElement
        check(dryer.cord.attach(outlet.anchor(0),false))
        passed+="Dryer attaches through the real 30 A outlet and cord lifecycle"
        val single=BlockPos(320,80,280);w.setBlockAndUpdate(single.below(),Blocks.STONE.defaultBlockState())
        helper.placeMachine(Eln.findItemStack(DistributionKind.SINGLE.title,1),single)
        p.teleportTo(w,302.0,99.0,324.0,0f,0f)
        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(296.0,89.0,266.0))
    }
    private fun screenshot(name: String) {
        Files.createDirectories(output)
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) }
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "passed" to passed,"failure" to error?.stackTraceToString(),"distant" to DistantModels.diagnostics())))
        if(error!=null)Eln.logger.error("TRANSMISSION_LIGHTING FAIL",error) else Eln.logger.info("TRANSMISSION_LIGHTING COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.transmissionChecks") || done)return
        try {
            ticks++;check(++total<3000) { "Transmission checks timed out in phase $phase: ${DistantModels.diagnostics()}" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="transmission-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),
                        WorldOptions(49049L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { work=mc.singleplayerServer!!.submit<Unit> { setup() };phase=2;ticks=0 }
                2->if(work!!.isDone && ticks>220) {
                    work!!.join()
                    if(net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) {
                        check(DistantModels.diagnostics()["failed"]==false)
                        if((DistantModels.diagnostics()["groups"] as Int)<6 || (DistantModels.diagnostics()["pending"] as Int)>0)return
                        val wrapper=Class.forName("com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge")
                        check(wrapper.getMethod("isAir",net.minecraft.world.level.block.state.BlockState::class.java).invoke(null,Eln.ghostBlock.defaultBlockState())==true)
                        passed+="Actual DH wrapper ignores invisible ELN placeholder volumes"
                    }
                    mc.options.hideGui=true
                    screenshot("near")
                    val tile=mc.level!!.getBlockEntity(BlockPos(294,90,280)) as SixNodeEntity
                    val light=tile.elementRenderList.filterIsInstance<LampSocketRender>().single()
                    check(light.suspension.drop==6.75);check(light.bulbCount==4)
                    passed+="Placed warehouse light publishes its 6.75-block drop and four bulbs"
                    if(DistantModels.available) {
                        check(DistantModels.diagnostics()["failed"]==false)
                        check((DistantModels.diagnostics()["groups"] as Int)>5)
                        work=mc.singleplayerServer!!.submit<Unit> {
                            val p=mc.singleplayerServer!!.playerList.players.first()
                            p.teleportTo(p.serverLevel(),700.0,110.0,500.0,0f,0f)
                            p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(296.0,90.0,266.0))
                        }
                        phase=3;ticks=0
                    } else finish(null)
                }
                3->if(work!!.isDone && ticks>200) {
                    work!!.join();check((DistantModels.diagnostics()["active"] as Int)>0)
                    check(mc.level!!.getBlockEntity(BlockPos(256,80,256))==null)
                    screenshot("distant");passed+="DH 3.3 custom objects remain active after the normal chunks unload"
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val p=mc.singleplayerServer!!.playerList.players.first()
                        p.teleportTo(p.serverLevel(),299.0,86.0,288.0,0f,0f)
                        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(294.0,84.0,280.0))
                    }
                    phase=4;ticks=0
                }
                4->if(work!!.isDone && ticks>120) {
                    work!!.join();screenshot("warehouse")
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val p=mc.singleplayerServer!!.playerList.players.first()
                        p.teleportTo(p.serverLevel(),308.5,81.5,288.0,0f,0f)
                        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(307.5,81.2,286.5))
                    }
                    phase=5;ticks=0
                }
                5->if(work!!.isDone && ticks>100) {
                    work!!.join();screenshot("dryer-plug")
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val p=mc.singleplayerServer!!.playerList.players.first()
                        p.teleportTo(p.serverLevel(),241.0,96.0,309.0,0f,0f)
                        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(224.0,91.0,270.0))
                    }
                    phase=6;ticks=0
                }
                6->if(work!!.isDone && ticks>100) {
                    work!!.join();screenshot("wind-turbines");mc.options.hideGui=false
                    finish(null)
                }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
