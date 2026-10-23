package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.vertex.*
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.terrain.*
import mods.eln.misc.Direction
import mods.eln.node.NodeBlockEntity
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.fan.*
import mods.eln.transparentnode.renewable.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.commands.arguments.EntityAnchorArgument
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

/** Opt-in native Sodium/Iris acceptance and original-vs-terrain model CPU comparison. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object TerrainModelChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work:CompletableFuture<*>?=null
    private var settledFrames=0
    @SubscribeEvent fun frame(event:net.neoforged.neoforge.client.event.RenderFrameEvent.Post) { if(phase==6)settledFrames++ }
    private val positions=mutableListOf<BlockPos>()
    private val passed=mutableListOf<String>()
    private val timings=linkedMapOf<String,Any>()
    private val output get()=mc.gameDirectory.toPath().resolve(System.getProperty("eln.terrainOutput","terrain-model-checks"))
    private val renders get()=positions.map { p->when(val e=mc.level!!.getBlockEntity(p)) {
        is TransparentNodeEntity->e.elementRender
        is SixNodeEntity->e.elementRenderList.filterNotNull().single()
        else->null
    } }
    private fun finish(error:Throwable?) {
        done=true;System.clearProperty("eln.disableTerrainModels")
        Files.createDirectories(output)
        val pack=runCatching { Class.forName("net.irisshaders.iris.Iris").getMethod("isPackInUseQuick").invoke(null) }.getOrNull()
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "passed" to passed,"failure" to error?.stackTraceToString(),"timings" to timings,"irisPackActive" to pack,
            "atlasTextures" to TerrainAtlas.textureCount,"atlasRevision" to TerrainAtlas.revision.get(),"directionalShadeDown" to mc.level?.getShade(net.minecraft.core.Direction.DOWN,true),"capturedBodies" to TerrainModels.capturedBodies,"compiledSections" to TerrainModels.compiledSections)))
        if(error!=null)Eln.logger.error("TERRAIN_MODELS FAIL",error) else Eln.logger.info("TERRAIN_MODELS COMPLETE {}",timings)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    private fun camera() {
        val p=mc.singleplayerServer!!.playerList.players.first()
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        p.teleportTo(p.serverLevel(),285.0,91.0,312.0,0f,0f)
        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(278.0,74.0,270.0))
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY)
    }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
        w.dayTime=6000L;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,mc.singleplayerServer)
        val descriptors=Eln.transparentNodeItem.subItemList.values.filterNotNull()
        val selected=listOf(
            descriptors.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.STREET_THREE },
            descriptors.filterIsInstance<FanDescriptor>().first { it.type==FanType.CLASSIC },
            descriptors.filterIsInstance<RenewableDescriptor>().first { it.kind==RenewableKind.WIND },
            descriptors.filterIsInstance<mods.eln.transparentnode.ups.UpsDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.standby.BackupDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.pump.IndustrialPumpDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.kitchen.KitchenDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.cord.OutletDescriptor>().first(),
            descriptors.filterIsInstance<BreakerPanelDescriptor>().first(),
            descriptors.filterIsInstance<HouseServiceDescriptor>().first { it.meter },
            descriptors.filterIsInstance<RenewableDescriptor>().first { it.kind==RenewableKind.MPPT },
            descriptors.filterIsInstance<mods.eln.transparentnode.utilitylight.UtilityStreetLightDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.climate.ClimateDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.worklight.WorkLightDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.utilitylight.StepDownUnitDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.festive.ChristmasTreeDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.festive.HolidayCandleDescriptor>().first(),
            descriptors.filterIsInstance<mods.eln.transparentnode.festive.StringLightsDescriptor>().first())
        for((i,d)in selected.withIndex()) {
            val pos=BlockPos(256+(i%4)*12,79,256+(i/4)*12);positions+=pos
            w.setChunkForced(pos.x shr 4,pos.z shr 4,true);w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            val wall=d.mustHaveWallFrontInverse() || d.mustHaveWall()
            if(wall)w.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(1),p,w,pos,if(wall)net.minecraft.core.Direction.SOUTH else net.minecraft.core.Direction.UP)) { "Could not place ${d.javaClass.simpleName}" }
        }
        val lamps=listOf(DecorativeLampStyle.CRYSTAL,DecorativeLampStyle.entries.first { it.fan },DecorativeLampStyle.entries.first { it.tableLamp })
        for((i,style)in lamps.withIndex()) {
            val pos=BlockPos(256+i*12,79,328);positions+=pos
            w.setChunkForced(pos.x shr 4,pos.z shr 4,true)
            val desc=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==style }
            w.setBlockAndUpdate(if(style.ceiling)pos.above() else pos.below(),Blocks.STONE.defaultBlockState())
            check(Eln.sixNodeItem.placeBlockAt(desc.newItemStack(1),p,w,pos,if(style.ceiling)net.minecraft.core.Direction.DOWN else net.minecraft.core.Direction.UP,.5f,.5f,.5f))
        }
        if(java.lang.Boolean.getBoolean("eln.terrainCatalogChecks")) {
            val oldMachines=listOf("GeneratorRender","MotorRender","FabricatorRender","ElectricalFurnaceRender")
            for((i,name) in oldMachines.withIndex()) {
                val d=descriptors.first { it.RenderClass.simpleName==name }
                val pos=BlockPos(310+i*8,79,268);positions+=pos
                w.setChunkForced(pos.x shr 4,pos.z shr 4,true);w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(1),p,w,pos,net.minecraft.core.Direction.UP))
            }
            val oldSix=listOf("ElectricalGateSourceRender","ElectricalMathRender","ElectricalVuMeterRender","ElectricalDataLoggerRender")
            for((i,name) in oldSix.withIndex()) {
                val d=Eln.sixNodeItem.subItemList.values.filterIsInstance<SixNodeDescriptor>().first { it.RenderClass.simpleName==name }
                val pos=BlockPos(310+i*8,79,280);positions+=pos
                w.setChunkForced(pos.x shr 4,pos.z shr 4,true);w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                check(Eln.sixNodeItem.placeBlockAt(d.newItemStack(1),p,w,pos,net.minecraft.core.Direction.UP,.5f,.5f,.5f))
            }
        }
        camera()
    }
    private fun draw(owner:Any) {
        when(owner) {
            is TransparentNodeElementRender->TerrainModels.drawElement(owner) { owner.draw() }
            is SixNodeElementRender->{owner.side.glRotateXnRef();mods.eln.client.gl.GL11.glTranslatef(-.5f,0f,0f);TerrainModels.drawElement(owner) { owner.draw() }}
        }
    }
    private fun captured(owner:Any,original:Boolean):TerrainMesh {
        System.setProperty("eln.disableTerrainModels",original.toString())
        val c=TerrainCapture();c.enabled=true
        FixedFunction.setMeshSink(c);FixedFunction.begin(PoseStack(),MultiBufferSource { error("Capture emitted live geometry") },0,0)
        try { mods.eln.client.gl.GL11.glTranslatef(.5f,.5f,.5f);draw(owner) }
        finally { FixedFunction.setMeshSink(null);FixedFunction.finish();System.clearProperty("eln.disableTerrainModels") }
        // Font glyphs are deliberately live and use their own atlas, outside the quad sink.
        check(c.supported || c.unsupportedReason=="font") { "Unsupported capture for ${owner.javaClass.simpleName}: ${c.unsupportedReason}" };return c.finish()
    }
    private fun vertices(m:TerrainMesh)=m.batches.flatMap { b->b.vertices.toList().chunked(9).map { b.material.toString()+it.joinToString(",") } }.sorted()
    private fun verify() {
        check(TerrainAtlas.textureCount>0) { "Atlas source found no model textures" }
        for(owner in renders.filterNotNull()) {
            val mesh=checkNotNull(TerrainModels.meshFor(owner)) { "No baked body for ${owner.javaClass.simpleName}" }
            val full=captured(owner,true);val live=captured(owner,false)
            check(vertices(full)==vertices(TerrainMesh(mesh.batches+live.batches))) { "Static/live partition changed original vertices for ${owner.javaClass.simpleName}: ${full.batches.sumOf { it.vertices.size }} vs ${mesh.batches.sumOf { it.vertices.size }}+${live.batches.sumOf { it.vertices.size }}" }
            passed+="${owner.javaClass.simpleName}: all original positions, UVs, normals, colors and materials retained"
        }
        check(TerrainAtlas.revision.get()>0) { "Terrain atlas source was not loaded" }
        check(TerrainModels.compiledSections>0) { "Sodium did not compile section meshes" }
        val pole=renders.filterIsInstance<DistributionPoleRender>().single()
        check(TerrainModels.sectionsFor(pole).size>=2) { "Tall pole did not split at section boundary" }
        val old=TerrainModels.meshFor(pole)
        pole.display.watts+=100.0
        val entity=pole.tileEntity
        TerrainModels.remove(entity);TerrainModels.update(entity)
        check(TerrainModels.meshFor(pole)===old) { "Nonvisual watts recaptured model" }
        passed+="Renderer compiles section geometry; tall poles span independent sections; watt telemetry reuses shared geometry"
        val originalPoleMesh=TerrainModels.meshFor(pole)
        pole.display.yaw=45
        TerrainModels.remove(entity);TerrainModels.update(entity)
        check(TerrainModels.meshFor(pole)!==originalPoleMesh)
        pole.display.yaw=0;TerrainModels.remove(entity);TerrainModels.update(entity)
        val fan=renders.filterIsInstance<FanRender>().single()
        val stationary=TerrainModels.meshFor(fan);val before=vertices(captured(fan,false))
        fan.visual.step(.2f,1.0)
        check(before!=vertices(captured(fan,false))) { "Fan rotor stopped animating" }
        check(TerrainModels.meshFor(fan)===stationary)
        val lamp=renders.filterIsInstance<LampSocketRender>().first()
        val dark=TerrainModels.meshFor(lamp)
        lamp.bulbCount=lamp.descriptor.bulbCapacity;lamp.lightValue=15
        TerrainModels.remove(lamp.tileEntity);TerrainModels.update(lamp.tileEntity)
        val lit=checkNotNull(TerrainModels.meshFor(lamp))
        check(lit!==dark && lit.batches.any { it.material.fullBright }) { "Lamp glow failed to update" }
        lamp.lightValue=0;lamp.bulbCount=0;TerrainModels.remove(lamp.tileEntity);TerrainModels.update(lamp.tileEntity)
        passed+="Lamp glow and pole aiming rebuild their meshes; moving fan blades retain independent live geometry"
        verifyAllFixtures()
        LegacyTerrainChecks.run()
        // Compare actual model rendering after warmup; only bodies are changed, no synthetic substitute.
        val owners=listOf(pole,renders.filterIsInstance<LampSocketRender>().first())+renders.filterNotNull().filter { it.javaClass.simpleName in setOf("GeneratorRender","ElectricalMathRender") }
        val arena=ByteBufferBuilder(16*1024*1024)
        try { for(owner in owners) {
            for(original in listOf(true,false)) {
                val times=mutableListOf<Double>();var count=0
                System.setProperty("eln.disableTerrainModels",original.toString())
                repeat(100) { trial ->
                    val b=BufferBuilder(arena,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY)
                    val start=System.nanoTime()
                    repeat(20) {
                        FixedFunction.begin(PoseStack(),MultiBufferSource { b },0x00f000f0,0)
                        try { draw(owner) } finally { FixedFunction.finish() }
                    }
                    val ms=(System.nanoTime()-start)/1e6
                    val mesh=b.build();count=mesh?.drawState()?.vertexCount ?: 0;mesh?.close();arena.clear()
                    if(trial>=40)times+=ms
                }
                timings[owner.javaClass.simpleName+if(original)"Original" else "Terrain"]=mapOf("medianMs20Models" to times.sorted()[times.size/2],"vertices20Models" to count)
            }
        } } finally { arena.close();System.clearProperty("eln.disableTerrainModels") }
    }
    private fun verifyAllFixtures() {
        val descriptors=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().filter { it.fixture!=null }
        for(d in descriptors) {
            val side=if(d.fixture!!.ceiling)Direction.YP else Direction.YN
            val entity=SixNodeEntity(BlockPos(256,79,328),Eln.sixNodeBlock.defaultBlockState()).apply { level=mc.level }
            val render=LampSocketRender(entity,side,d).apply { front=mods.eln.misc.LRDU.Up;connectionType=mods.eln.cable.CableRenderType();bulbCount=d.bulbCapacity;lightValue=15 }
            entity.elementRenderList[side.int]=render
            try {
                TerrainModels.update(entity)
                val mesh=checkNotNull(TerrainModels.meshFor(render)) { "No terrain mesh for ${d.fixture}" }
                check(vertices(captured(render,true))==vertices(TerrainMesh(mesh.batches+captured(render,false).batches))) { "Fixture partition differs for ${d.fixture}" }
            } finally { entity.destructor() }
        }
        val switches=Eln.sixNodeItem.subItemList.values.filterIsInstance<mods.eln.sixnode.wirelesssignal.WallWirelessDescriptor>()
        for(d in switches) {
            val entity=SixNodeEntity(BlockPos(256,79,328),Eln.sixNodeBlock.defaultBlockState()).apply { level=mc.level }
            val render=mods.eln.sixnode.wirelesssignal.WallWirelessRender(entity,Direction.ZN,d).apply { front=mods.eln.misc.LRDU.Up;connectionType=mods.eln.cable.CableRenderType() }
            entity.elementRenderList[Direction.ZN.int]=render
            try {
                TerrainModels.update(entity)
                val mesh=checkNotNull(TerrainModels.meshFor(render))
                check(vertices(captured(render,true))==vertices(TerrainMesh(mesh.batches+captured(render,false).batches)))
            } finally { entity.destructor() }
        }
        passed+="Every registered decorative lamp style (${descriptors.size}) and wireless wall control (${switches.size}) retains its complete static/animated mesh"
    }
    private fun screenshot(name:String) {
        Files.createDirectories(output)
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) }
    }
    @SubscribeEvent fun itemGallery(event:net.neoforged.neoforge.client.event.RenderGuiEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.terrainCatalogChecks") || done || phase<2)return
        val names=listOf("GeneratorRender","MotorRender","FabricatorRender","UpsRender","DistributionPoleRender","KitchenRender","IndustrialPumpRender","HouseServiceRender")
        val descriptors=Eln.transparentNodeItem.subItemList.values.filterIsInstance<TransparentNodeDescriptor>()
        val items=names.map { name->descriptors.first { it.RenderClass.simpleName==name }.newItemStack(1) }+
            Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().filter { it.fixture!=null }.take(8).map { it.newItemStack(1) }
        val g=event.guiGraphics;g.fill(2,2,8*22+6,2*22+6,0xff444444.toInt())
        items.forEachIndexed { i,item->g.renderItem(item,6+(i%8)*22,6+(i/8)*22) }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.terrainChecks") || done)return
        try {
            ticks++;check(++total<5000) { "Terrain acceptance timed out phase=$phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="terrain-models-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(36041L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { work=mc.singleplayerServer!!.submit<Unit> { setup() };phase=2;ticks=0 }
                2->if(work!!.isDone.also { if(it)work!!.join() } && ticks>120 && renders.all { it!=null } && TerrainModels.compiledSections>0) {
                    work!!.join();verify();phase=6;ticks=0;settledFrames=0
                }
                6->if(ticks>100 && settledFrames>30) {
                    screenshot("terrain");phase=3;ticks=0
                    System.setProperty("eln.disableTerrainModels","true");System.setProperty("eln.disableBakedItems","true")
                    positions.mapNotNull { mc.level!!.getBlockEntity(it) as? NodeBlockEntity }.forEach(TerrainModels::remove)
                }
                3->if(ticks>80) { screenshot("original");System.clearProperty("eln.disableTerrainModels");System.clearProperty("eln.disableBakedItems");phase=4;ticks=0 }
                4->if(ticks>100) {
                    screenshot("restored");check(renders.all { TerrainModels.meshFor(it!!)!=null })
                    passed+="Unbake/rebake removes old section entries and restores every model"
                    work=mc.reloadResourcePacks();phase=5;ticks=0
                }
                5->if(work!!.isDone && ticks>120) {
                    work!!.join();check(renders.all { TerrainModels.meshFor(it!!)!=null })
                    screenshot("reloaded");passed+="Resource/atlas reload recaptures every body";finish(null)
                }
            }
        } catch(t:Throwable) { finish(t) }
    }
}
