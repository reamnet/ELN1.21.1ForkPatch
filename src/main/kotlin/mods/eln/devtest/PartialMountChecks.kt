package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeManager
import mods.eln.node.SurfaceMounting
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.*
import mods.eln.transparentnode.solarpanel.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in production placement/wiring/terrain acceptance on an isolated native client world. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object PartialMountChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work:CompletableFuture<*>?=null
    private val positions=mutableListOf<BlockPos>()
    private val observations=mutableListOf<String>()
    private val output get()=mc.gameDirectory.toPath().resolve("partial-mount-checks")
    private fun setup() {
        val player=mc.singleplayerServer!!.playerList.players.first();val world=player.serverLevel()
        val f=NativeCampaignFixtures(world,player)
        world.dayTime=6000;world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,world.server)
        val panels=Eln.transparentNodeItem.subItemList.values.filterIsInstance<SolarPanelDescriptor>()
        val slab=Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM)
        for((index,d) in panels.withIndex()) {
            val at=BlockPos(256+index*5,80,256);positions+=at
            world.setChunkForced(at.x shr 4,at.z shr 4,true)
            for(x in -3..3)for(z in -3..3)world.setBlockAndUpdate(at.offset(x,-1,z),slab)
            f.placeMachine(d.newItemStack(),at)
            check(SurfaceMounting.inset(world,at,Direction.YN)==.5)
            world.updateNeighborsAt(at.below(),Blocks.SMOOTH_STONE_SLAB)
            check(f.machine(at) is SolarPanelElement)
        }
        observations+="All four single/multiblock and fixed/tracking panels place and survive on bottom slabs"
        for((index,up) in net.minecraft.core.Direction.Plane.HORIZONTAL.withIndex()) {
            val at=BlockPos(256+index*6,80,264);positions+=at
            world.setChunkForced(at.x shr 4,at.z shr 4,true)
            val stairs=Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,up)
            val sideways=up.clockWise
            for(row in 0..2)for(width in -2..2)world.setBlockAndUpdate(at.offset(up.stepX*row+sideways.stepX*width,row-1,up.stepZ*row+sideways.stepZ*width),stairs)
            val d=panels.single { it.name=="2x3 Solar Panel" }
            val e=f.placeMachine(d.newItemStack(),at) as SolarPanelElement
            check(e.front==Direction.fromFacing(up.opposite))
            val negative=at.relative(up).above().relative(sideways)
            val returnNode=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(negative.x,negative.y,negative.z,world))
            check(returnNode is mods.eln.misc.GhostPowerNode)
            check(world.getBlockState(at.relative(up)).block is StairBlock) { "Array replaced its roof" }
            val positive=at.relative(up.opposite)
            world.setBlockAndUpdate(positive.below(),slab);f.wire(positive)
            check(e.node!!.nodeConnectionList.isNotEmpty()) { "Sloped array positive terminal did not connect" }
            val groundAt=negative.relative(sideways)
            world.setBlockAndUpdate(groundAt.below(),Blocks.GLASS.defaultBlockState());f.ground(groundAt)
            check(returnNode.nodeConnectionList.isNotEmpty()) { "Uphill return terminal did not connect" }
            val saved=net.minecraft.nbt.CompoundTag().also(e::writeToNBT)
            val restored=SolarPanelElement(TransparentNode(),d).also { it.readFromNBT(saved) }
            check(restored.roofMountKnown && restored.roofMount==e.roofMount)
            check(restored.roofMount.groundOffset(Coordinate(1,0,0,0)).z==1)
        }
        observations+="Stairs in all four directions automatically pitch 2x3 arrays; both terminals connect and the stairs remain intact"
        for((index,block) in listOf(slab,Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP),Blocks.GLASS.defaultBlockState()).withIndex()) {
            val at=BlockPos(256+index*5,80,272)
            world.setChunkForced(at.x shr 4,at.z shr 4,true)
            for(x in 0..3)world.setBlockAndUpdate(at.offset(x,-1,0),if(x==3)Blocks.STONE.defaultBlockState() else block)
            f.source(at,120.0);f.wire(at.east());f.load(at.east(2),48.0);f.ground(at.east(3))
            positions+=at;positions+=at.east();positions+=at.east(2);positions+=at.east(3)
            check(f.six(at.east()).sixNode!!.nodeConnectionList.size>=2)
            world.updateNeighborsAt(at.east().below(),block.block)
            check(f.node(at.east())!=null)
        }
        observations+="Electrical sources, cables and grounds place on top/bottom slabs and glass, with live neighboring connections"
        val unsupported=BlockPos(280,80,272)
        check(!Eln.transparentNodeItem.placeBlockAt(panels.first().newItemStack(),player,world,unsupported,net.minecraft.core.Direction.UP))
        observations+="Air still fails support validation; partial mounting does not allow replacing unrelated blocks"
        player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.onUpdateAbilities();player.setNoGravity(true)
        player.teleportTo(world,270.0,85.0,283.0,0f,0f)
        player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(266.0,80.0,263.0))
    }
    private fun ready()=positions.all { pos->when(val entity=mc.level!!.getBlockEntity(pos)) {
        is SixNodeEntity->entity.elementRenderList.filterNotNull().all(TerrainModels::hasBakedBody)
        is TransparentNodeEntity->entity.elementRender?.let(TerrainModels::hasBakedBody)==true
        else->false
    } }
    private fun finish(error:Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to observations,"bakedNodes" to positions.size,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("PARTIAL_MOUNT FAIL",error) else Eln.logger.info("PARTIAL_MOUNT COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.partialMountChecks") || done)return
        try {
            ticks++;check(++total<3600) { "Mounting check timed out in phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="partial-mount-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(69069L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { work=mc.singleplayerServer!!.submit<Unit> { setup() };phase=2;ticks=0 }
                2->{
                    if(work!!.isDone)work!!.join()
                    if(ticks>240 && ready()) {
                        Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("slabs-and-stairs.png")) }
                        observations+="Every placed panel, cable, source and ground has a baked terrain body on its actual mounting surface"
                        work=mc.reloadResourcePacks();phase=3;ticks=0
                    }
                }
                3->if(work!!.isDone && mc.overlay==null && ticks>100 && ready()) {work!!.join();observations+="Baked partial mounts survive resource reload";finish(null)}
            }
        }catch(t:Throwable){finish(t)}
    }
}
