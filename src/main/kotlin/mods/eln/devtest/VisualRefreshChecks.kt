package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.itemrender.BakedNodeItems
import mods.eln.client.itemrender.CatalogItemPresentation
import mods.eln.client.itemrender.NodeItemRenderer
import mods.eln.client.terrain.TerrainAtlas
import mods.eln.client.terrain.TerrainCapture
import mods.eln.client.terrain.TerrainMesh
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.misc.Direction
import mods.eln.node.transparent.*
import mods.eln.transparentnode.kitchen.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.item.ItemDisplayContext
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
import java.util.zip.GZIPOutputStream

/** Opt-in native model/material, catalog, reload and multiplayer-door lifecycle acceptance. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object VisualRefreshChecks {
    private val mc get()=Minecraft.getInstance()
    private val output get()=mc.gameDirectory.toPath().resolve("visual-refresh-checks")
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work:CompletableFuture<*>?=null
    private var openMenu:KitchenContainer?=null
    private val observations=mutableListOf<String>()
    private val data=linkedMapOf<String,Any?>()
    private val gson=GsonBuilder().create()
    private val fridgePos=BlockPos(258,80,260)
    private val placed=mutableListOf<BlockPos>()
    private fun descriptors():List<GenericItemBlockUsingDamageDescriptor> =
        (Eln.transparentNodeItem.subItemList.values.filterNotNull()+Eln.sixNodeItem.subItemList.values.filterNotNull())
    private fun selected()=descriptors().filter { CatalogItemPresentation.icon(it)!=null }
    private fun media()=descriptors().filter { it.javaClass.name=="mods.eamedia.MediaDescriptor" }
    private fun capture(body:()->Unit):TerrainMesh {
        lateinit var mesh:TerrainMesh
        FixedFunction.isolated {
        val sink=TerrainCapture().apply { enabled=true }
        FixedFunction.setMeshSink(sink)
        try {
            FixedFunction.begin(PoseStack(),MultiBufferSource { error("Mesh capture escaped its sink") },LightTexture.FULL_BRIGHT,0)
            try { body() } finally { FixedFunction.finish() }
        } finally { FixedFunction.setMeshSink(null) }
        check(sink.supported) { "Unsupported geometry: ${sink.unsupportedReason}" }
        mesh=sink.finish()
        }
        return mesh
    }
    private fun export() {
        val items=selected()
        val export=linkedMapOf<String,Any>()
        System.setProperty("eln.exportCatalogMeshes","true")
        try {
            for(d in items) {
                val stack=d.newItemStack()
                val sink=TerrainCapture().apply { enabled=true }
                FixedFunction.isolated {
                    FixedFunction.setMeshSink(sink)
                    try { NodeItemRenderer.get().renderByItem(stack,ItemDisplayContext.GROUND,PoseStack(),MultiBufferSource { error("Catalog capture escaped") },LightTexture.FULL_BRIGHT,0) }
                    finally { FixedFunction.setMeshSink(null) }
                }
                check(sink.supported) { "Catalog ${d.name}: ${sink.unsupportedReason}" }
                val mesh=sink.finish();check(mesh.batches.isNotEmpty()) { "Empty preview ${d.name}" }
                export[d.iconName]=mapOf("name" to d.name,"id" to BuiltInRegistries.ITEM.getKey(stack.item).toString(),"batches" to mesh.batches.map { b ->
                    mapOf("texture" to b.material.texture.toString(),"unlit" to b.material.unlit,"blend" to b.material.blend,"cull" to b.material.cull,"vertices" to b.vertices.toList())
                })
            }
        } finally { System.clearProperty("eln.exportCatalogMeshes") }
        Files.createDirectories(output)
        GZIPOutputStream(Files.newOutputStream(output.resolve("catalog-meshes.json.gz"))).bufferedWriter().use { gson.toJson(export,it) }
        data["catalogItems"]=items.size
        observations+="Every new lamp and pole catalog icon was exported from its actual descriptor preview"
    }
    private fun auditModels() {
        val resources=mc.resourceManager.listResources("model") { it.namespace=="eln" && it.path.endsWith("equipment_finish.mtl") }
        var models=0;var quads=0;var finishBatches=0
        for((id,_) in resources) {
            val folder=id.path.removeSuffix("/equipment_finish.mtl");val name=folder.substringAfterLast('/')
            val model=mods.eln.misc.Obj3D().apply { check(loadFile("${folder.removePrefix("model/")}/$name.obj")) }
            val obj=mc.resourceManager.getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eln","$folder/$name.obj")).orElseThrow()
            val parts=obj.open().bufferedReader().use { it.readLines().filter { s->s.startsWith("o ") }.map { s->s.substring(2) } }
            val mesh=capture { parts.forEach { part->checkNotNull(model.getPart(part)).drawNoBind() } }
            check(mesh.batches.isNotEmpty()) { "Empty model $name" }
            mesh.batches.forEach { b ->
                if(b.material.texture.path=="textures/block/equipment_finishes.png")finishBatches++
                check(TerrainAtlas.supported(b.material.texture)) { "Missing atlas material ${b.material.texture}" }
                for(i in b.vertices.indices.step(9))repeat(8) { n->check(Float.fromBits(b.vertices[i+n]).isFinite()) { "Nonfinite mesh $name" } }
            }
            quads+=mesh.batches.sumOf { it.vertices.size/36 };models++
        }
        check(models>=222 && finishBatches>=models) { "Incomplete finish coverage: $models / $finishBatches" }
        data["modelAudit"]=mapOf("models" to models,"quads" to quads,"finishBatches" to finishBatches)
        observations+="All refreshed OBJ models load with finite geometry and atlas-backed materials in the native client"
    }
    private fun auditPreviews(label:String) {
        if(java.lang.Boolean.getBoolean("eln.visualExportOnly"))return
        val icons=selected().map { BuiltInRegistries.ITEM.getKey(it.parentItem).toString() }.toSet()
        val results=(selected()+media()).flatMap { d->listOf(ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).map { c->BakedNodeItems.audit(d.newItemStack(),c) } }
        check(results.all { it["baked"]==true && (it["quads"] as Int)>0 }) { "A catalog item did not bake: ${results.filter { it["baked"]!=true }}" }
        val g=results.filter { it["context"]=="GUI" && it["item"] in icons };check(g.all { (it["quads"] as Int)==1 }) { "A catalog icon still renders the full model" }
        data[label]=results
        observations+="$label: every new lamp/pole is a one-quad catalog sprite, with baked 3D held and dropped previews"
    }
    private fun setup() {
        val server=mc.singleplayerServer!!;val p=server.playerList.players.first();val w=p.serverLevel()
        w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server)
        for(x in 250..290)for(z in 254..276)w.setBlockAndUpdate(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState())
        val names=listOf("Electric Refrigerator","Electric Mini Fridge","Electric Microwave","Electric Toaster","Electric Washing Machine","Electric Dryer",
            "MPPT Solar Controller","Hybrid Solar Inverter","Four Cell Plug-in UPS","Automatic Transfer Switch")
        var n=0
        for(name in names) {
            val d=descriptors().filterIsInstance<TransparentNodeDescriptor>().first { it.name==name }
            val pos=if(d is KitchenDescriptor && d.type==KitchenType.FRIDGE)fridgePos else BlockPos(262+(n++%5)*4,80,260+(n/5)*5)
            w.setChunkForced(pos.x shr 4,pos.z shr 4,true)
            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(),p,w,pos,net.minecraft.core.Direction.UP)) { "Could not place $name" }
            ((w.getBlockEntity(pos) as TransparentNodeEntity).node as TransparentNode).element!!.apply { front=Direction.ZP;reconnect();needPublish() }
            placed+=pos
        }
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        p.teleportTo(w,266.0,83.0,272.0,180f,15f)
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3(266.0,80.7,261.0))
    }
    private fun camera() {
        val p=mc.player ?: return
        p.setNoGravity(true);p.abilities.flying=true
        val door=phase==3
        p.setPos(if(door)259.5 else 266.0,if(door)80.5 else 82.0,if(door)263.5 else 272.0)
        p.yRot=180f;p.yRotO=180f;p.xRot=if(door)14f else 18f;p.xRotO=p.xRot
    }
    private fun fridge()=(mc.singleplayerServer!!.playerList.players.first().serverLevel().getBlockEntity(fridgePos) as TransparentNodeEntity).node.let { (it as TransparentNode).element as KitchenElement }
    private fun bodiesReady()=placed.all { pos ->
        val render=(mc.level!!.getBlockEntity(pos) as? TransparentNodeEntity)?.elementRender
        render!=null && mods.eln.client.terrain.TerrainModels.hasBakedBody(render)
    }
    private fun doorLifecycle() {
        val server=mc.singleplayerServer!!;val p=server.playerList.players.first();val f=fridge()
        // Initialize a genuinely cold, powered-off fridge, then exercise the production menu callbacks.
        f.readFromNBT(net.minecraft.nbt.CompoundTag().apply { f.writeToNBT(this);putDouble("kitchenTemperature",4.0);putBoolean("kitchenEnabled",false) })
        val c=KitchenContainer(p,f.inventory,f.type);p.containerMenu=c
        check(f.doorOpen)
        f.slowProcessList.forEach { it.process(8.0) }
        check(f.temperature>8 && !f.coldStorage) { "Open fridge failed to warm: ${f.temperature}" }
        val warm=f.temperature
        c.removed(p);p.containerMenu=p.inventoryMenu
        check(!f.doorOpen)
        // More than one viewer: closing one must leave the door open for the other.
        val other=net.minecraft.server.level.ServerPlayer(server,p.serverLevel(),com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"FridgeViewer"),p.clientInformation())
        f.inventory.startOpen(p);f.inventory.startOpen(other);f.inventory.stopOpen(p);check(f.doorOpen)
        f.inventory.stopOpen(other);check(!f.doorOpen)
        data["fridge"]=mapOf("warmAfter8Seconds" to warm,"coldStoragePausedWhileWarm" to !f.coldStorage)
        observations+="Opening the actual fridge menu warms its interior and ends cold-food protection; the last viewer closes the door"
        openMenu=KitchenContainer(p,f.inventory,f.type);p.containerMenu=openMenu!!;f.needPublish()
    }
    @SubscribeEvent fun gallery(event:net.neoforged.neoforge.client.event.RenderGuiEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.visualRefreshChecks") || phase<2 || done)return
        val items=selected().filterIndexed { i,_->i%5==0 }.take(40)
        val g=event.guiGraphics;g.fill(2,2,10*22+6,4*22+6,0xff3d4145.toInt())
        items.forEachIndexed { i,d->g.renderItem(d.newItemStack(),6+(i%10)*22,6+(i/10)*22) }
    }
    private fun shot(name:String) {
        val camera=mc.gameRenderer.mainCamera
        data["camera-$name"]=mapOf("position" to camera.position.toString(),"yaw" to camera.yRot,"pitch" to camera.xRot,
            "player" to mc.player?.position().toString(),"playerYaw" to mc.player?.yRot,"playerPitch" to mc.player?.xRot,
            "cameraEntity" to mc.cameraEntity?.position().toString(),"loadedTiles" to placed.map { p->mapOf("pos" to p.toShortString(),"block" to mc.level!!.getBlockState(p).toString(),"tile" to mc.level!!.getBlockEntity(p)?.javaClass?.simpleName) })
        Files.createDirectories(output);Files.writeString(output.resolve("view-debug.json"),gson.toJson(data["camera-$name"]))
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) }
    }
    private fun finish(error:Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to observations,"measurements" to data,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.logger.info("VISUAL_REFRESH COMPLETE") else Eln.logger.error("VISUAL_REFRESH FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.visualRefreshChecks") || done)return
        try {
            ticks++;check(++total<3600) { "Visual check timed out in phase $phase" }
            if(phase>=2)camera()
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="visual-refresh-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(68068L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { work=mc.singleplayerServer!!.submit<Unit> { setup() };phase=2;ticks=0 }
                2->if(work!!.isDone && ticks>240 && bodiesReady()) {
                    work!!.join();shot("before-audits");auditModels();export();auditPreviews("previews")
                    data["placedBodies"]=placed.size
                    observations+="All ten representative placed appliance/controller cabinets have baked terrain bodies"
                    mc.gui.chat.clearMessages(false)
                    shot("equipment-and-catalog");work=mc.singleplayerServer!!.submit<Unit> { doorLifecycle() };phase=3;ticks=0
                }
                3->if(work!!.isDone && ticks>140 && bodiesReady()) {
                    work!!.join();shot("fridge-open")
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val p=mc.singleplayerServer!!.playerList.players.first();openMenu!!.removed(p);p.containerMenu=p.inventoryMenu
                    };phase=5;ticks=0
                }
                5->if(work!!.isDone && ticks>20) { work!!.join();work=mc.reloadResourcePacks();phase=4;ticks=0 }
                4->if(work!!.isDone && mc.overlay==null && ticks>100) {
                    work!!.join();auditModels();auditPreviews("afterReload");shot("after-reload");finish(null)
                }
            }
        } catch(t:Throwable) { finish(t) }
    }
}
