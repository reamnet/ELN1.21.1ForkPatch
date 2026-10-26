package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.DistantModels
import mods.eln.gridnode.*
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sixnode.electricalcable.*
import mods.eln.utility.*
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
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/** Explicit opt-in visual gallery, real server span publication, native GL and DH draw calls. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object OverheadAppearanceChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val passed=ArrayList<String>();private val measured=linkedMapOf<String,Any>()
    private val output get()=mc.gameDirectory.toPath().resolve("overhead-appearance-checks")
    private val poles=ArrayList<BlockPos>()
    private val mainPos=BlockPos(280,80,256);private val nextPos=BlockPos(304,80,256);private val mastPos=BlockPos(280,82,272)
    private lateinit var main: GridElement;private lateinit var next: GridElement;private lateinit var mast: GridElement
    private var drop: GridLink?=null
    private var savedMesh: Any?=null
    private var savedBakedMesh: Any?=null
    private var bakedDrawCalls=0L
    private val draws=AtomicLong();private val groups=IdentityHashMap<Any,Boolean>()
    private fun cable(size: String,material: UtilityCableMaterial,insulated: Boolean)=UtilityCableDescriptor.allDescriptors().first {
        it.sizeLabel==size && it.material==material && it.insulated==insulated && !it.melted
    }
    private fun server(action: ()->Unit) { work=mc.singleplayerServer!!.submit<Unit> { action() };ticks=0 }
    private fun camera(x: Double,y: Double,z: Double,at: Vec3) {
        val p=mc.singleplayerServer!!.playerList.players.first();p.teleportTo(p.serverLevel(),x,y,z,0f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,at)
    }
    private fun mesh(pos: BlockPos): Any? {
        val render=(mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as GridRender
        return GridRender::class.java.getDeclaredField("cableMesh").apply { isAccessible=true }.get(render)
    }
    private fun baked(pos: BlockPos)=mods.eln.client.terrain.TerrainModels.meshFor(
        ((mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as GridRender).terrainCableOwner)
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        w.setWeatherParameters(12000,0,false,false);w.setDayTime(6000)
        fun place(pos: BlockPos,name: String): GridElement {
            for(x in -1..1)for(z in -1..1)w.setChunkForced((pos.x shr 4)+x,(pos.z shr 4)+z,true)
            w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());poles+=pos
            if(name=="120/240V Service Weatherhead") {
                w.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
                check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,w,pos,net.minecraft.core.Direction.SOUTH))
                return f.machine(pos) as GridElement
            }
            return f.placeMachine(Eln.findItemStack(name,1),pos) as GridElement
        }
        for(i in 0..3) {
            val a=place(BlockPos(256,80,288+i*10),DistributionKind.SINGLE.title)
            val b=place(BlockPos(288,80,288+i*10),DistributionKind.SINGLE.title)
            val c=cable(if(i%2==0)"2 AWG" else "1000 kcmil",if(i<2)UtilityCableMaterial.COPPER else UtilityCableMaterial.ALUMINUM,false)
            GridLink.addLink(a,b,32,32,c,32)
        }
        val first=place(BlockPos(256,80,256),DistributionKind.SECONDARY.title)
        main=place(mainPos,DistributionKind.SECONDARY.title);next=place(nextPos,DistributionKind.SECONDARY.title)
        mast=place(mastPos,"120/240V Service Weatherhead")
        val bundle=cable("250 kcmil",UtilityCableMaterial.ALUMINUM,true)
        GridLink.addLink(first,main,50,50,bundle,72);GridLink.addLink(main,next,50,50,bundle,72)
        GridLink.addLink(main,mast,50,50,cable("2 AWG",UtilityCableMaterial.COPPER,true),51)
        drop=main.gridLinkList.first { it.getOtherElement(main)===mast }
        check(OverheadSpanPublication.span(drop!!).startSplice!=null)
        val bare=cable("2 AWG",UtilityCableMaterial.COPPER,false)
        val stack=bare.newItemStack().also { bare.setRemainingLengthMeters(it,100.0) }
        p.setItemInHand(InteractionHand.MAIN_HAND,stack)
        val before=main.gridLinkList.size
        main.onTryGridConnect(p,stack,bare,Direction.XN,main.renderedGridCablePoint(50,1))
        check(main.gridLinkList.size==before && bare.getRemainingLengthMeters(stack)==100.0)
        passed+="Native server rejects a bare service spool without consuming it; installed secondary and service drop use insulated cable"
        camera(301.0,99.0,336.0,Vec3(272.0,85.0,298.0))
    }
    private fun attachDhCounters() {
        val entries=DistantModels.javaClass.getDeclaredField("entries").apply { isAccessible=true }.get(DistantModels) as Map<*,*>
        for((key,entry)in entries) {
            if(key!!.javaClass.getDeclaredField("face").apply { isAccessible=true }.getInt(key)!=6)continue
            val group=entry!!.javaClass.getDeclaredField("group").apply { isAccessible=true }.get(entry) ?: continue
            if(groups.put(group,true)==null)group.javaClass.getMethod("setPreRenderFunc",java.util.function.Consumer::class.java).invoke(group,java.util.function.Consumer<Any> { draws.incrementAndGet() })
        }
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output);measured["distantWireDraws"]=draws.get()
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"measurements" to measured,"distant" to DistantModels.diagnostics())))
        if(error==null)Eln.logger.info("OVERHEAD_APPEARANCE COMPLETE") else Eln.logger.error("OVERHEAD_APPEARANCE FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.overheadAppearanceChecks") || done)return
        try {
            ticks++;check(++total<2600) { "Timed out in phase $phase" }
            if(phase==8)attachDhCounters()
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.renderDistance().set(8)
                    mc.createWorldOpenFlows().createFreshLevel("overhead-appearance-${System.currentTimeMillis()}",LevelSettings("Overhead appearance",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(55055L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;server { setup() };phase=2 }
                2->if(work!!.isDone && ticks>180) {
                    work!!.join()
                    val all=poles.mapNotNull { mesh(it) as? GridRender.Catenary }.flatMap { it.wires }
                    var bakedQuads=0;var spanSections=0
                    for(pos in poles) {
                        val render=(mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as GridRender
                        if(mesh(pos)==null)continue
                        val baked=checkNotNull(mods.eln.client.terrain.TerrainModels.meshFor(render.terrainCableOwner)) { "Unbaked span at $pos" }
                        bakedQuads+=baked.batches.sumOf { it.vertices.size/36 }
                        spanSections+=mods.eln.client.terrain.TerrainModels.sectionsFor(render.terrainCableOwner).size
                    }
                    check(bakedQuads>1000 && spanSections>4)
                    measured["bakedCableQuads"]=bakedQuads;measured["bakedCableSections"]=spanSections
                    passed+="Native spans and splice fittings bake into terrain meshes across multiple air-only sections"
                    check(all.any { it.color==OverheadCableAppearance.COPPER } && all.any { it.color==OverheadCableAppearance.ALUMINUM })
                    check(all.filter { it.color==OverheadCableAppearance.COPPER }.map { it.radius }.distinct().size>=2)
                    measured["meshComponents"]=all.size;measured["wireColors"]=all.map { "%08x".format(it.color) }.distinct()
                    measured["wireRadii"]=all.map { it.radius }.distinct().sorted()
                    passed+="Real span packets create native copper/aluminum geometry at distinct installed gauges, plus insulated triplex"
                    photo("gauge-gallery")
                    server { camera(286.0,85.5,263.0,Vec3(281.0,85.3,256.5)) };phase=3
                }
                3->if(work!!.isDone && ticks>100) {
                    work!!.join();photo("triplex-splice")
                    server { drop!!.onBreakElement();drop=null;camera(286.0,85.5,263.0,Vec3(281.0,85.3,256.5)) };phase=4
                }
                4->if(work!!.isDone && ticks>100) {
                    work!!.join();photo("triplex-without-drop")
                    val mainMesh=mesh(mainPos) as GridRender.Catenary
                    check(mainMesh.wires.size==3) { "Removed service drop left splice components" }
                    check(baked(mainPos)!!.batches.none { batch->(8 until batch.vertices.size step 9).any { batch.vertices[it]==OverheadCableAppearance.COPPER } }) {
                        "Terrain retained the removed copper service drop"
                    }
                    passed+="Disconnecting the service drop removes its splice and leads, leaving only the suspended main triplex"
                    server {
                        GridLink.addLink(mast,main,50,50,cable("2 AWG",UtilityCableMaterial.COPPER,true),51)
                        drop=mast.gridLinkList.single();check(OverheadSpanPublication.span(drop!!).endSplice!=null)
                    };phase=5
                }
                5->if(work!!.isDone && ticks>100) {
                    work!!.join();check((mesh(mastPos) as GridRender.Catenary).wires.size>3)
                    savedMesh=mesh(mastPos)
                    savedBakedMesh=checkNotNull(baked(mastPos))
                    bakedDrawCalls=(savedMesh as GridRender.Catenary).drawCalls
                    server { mast.needPublish() };phase=6
                }
                6->if(work!!.isDone && ticks>80) {
                    work!!.join();check(mesh(mastPos)===savedMesh)
                    check(baked(mastPos)===savedBakedMesh)
                    check((savedMesh as GridRender.Catenary).drawCalls==bakedDrawCalls) { "Baked span still emitted device-renderer vertices" }
                    passed+="Reversing connection ownership preserves branch splices; unchanged status packets reuse native cable geometry"
                    photo("triplex-reconnected")
                    mc.options.renderDistance().set(4)
                    server { camera(340.0,90.0,256.0,Vec3(280.0,85.0,256.0)) };phase=7
                }
                7->if(work!!.isDone && ticks>140) {
                    work!!.join()
                    check(baked(mainPos)==null) { "Render-boundary span should use its complete fallback" }
                    check((mesh(mainPos) as GridRender.Catenary).drawCalls>2) { "Unbaked boundary span was not drawn" }
                    photo("boundary-fallback")
                    passed+="Baked spans emit no per-frame device vertices; whole-span fallback preserves render-boundary wires before DH handoff"
                    server { camera(650.0,125.0,620.0,Vec3(280.0,87.0,280.0)) };phase=8
                }
                8->if(work!!.isDone && ticks>240) {
                    work!!.join();check(DistantModels.diagnostics()["failed"]==false)
                    check(draws.get()>0) { "DH did not draw styled spans after normal chunk unload" }
                    measured["distant"]=DistantModels.diagnostics();photo("distant")
                    passed+="DH actually draws cached colored/gauged spans after vanilla chunks unload"
                    finish(null)
                }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
