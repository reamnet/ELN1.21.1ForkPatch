package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.vertex.*
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.gridnode.GridRender
import mods.eln.misc.Direction
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.sixnode.lampsocket.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files

/** Real production model emission into Minecraft/Iris buffers; never enabled during normal play. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object RenderCostChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private val samples=linkedMapOf<String,MutableList<Double>>()
    private val vertices=linkedMapOf<String,Int>()
    private val strides=linkedMapOf<String,Int>()
    private var case=0;private var trial=0
    private val names=listOf("transformer-street-pole","crystal-chandelier","overhead-span")
    private var cable: GridRender.Catenary?=null
    private lateinit var pole: DistributionPoleDescriptor
    private lateinit var lamp: LampSocketDescriptor
    private val display=DistributionDisplay().apply { installed=true;light=15;fuseClosed=listOf(true);fuseBlown=listOf(false) }
    private var arena: ByteBufferBuilder?=null
    private val poses=List(20) { i -> PoseStack().apply { translate((i%5)*10.0,70.0,(i/5)*10.0);mulPose(org.joml.Quaternionf().rotateY(.37f));scale(1.1f,1f,.9f) } }
    private val passed=mutableListOf<String>()
    private fun draw(index: Int) {
        when(index) {
            0->pole.drawEquipment(Direction.XN,display,45f,20f)
            1->lamp.renderType.draw(lamp,ItemRenderType.INVENTORY,8.0)
            2->{ GL11.glColor3f(.72f,.4f,.2f);cable!!.draw() }
        }
    }
    private fun sample(index: Int): Pair<Double,Int> {
        val builder=BufferBuilder(arena!!,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY)
        val buffers=MultiBufferSource { builder }
        val start=System.nanoTime()
        for(pose in poses) {
            FixedFunction.begin(pose,buffers,0x00e000c0,OverlayTexture.NO_OVERLAY)
            try { draw(index) } finally { FixedFunction.finish() }
        }
        val elapsed=(System.nanoTime()-start)/1e6
        val mesh=builder.buildOrThrow()
        val count=mesh.drawState().vertexCount
        strides[names[index]]=mesh.drawState().format.vertexSize
        mesh.close();arena!!.clear()
        return elapsed to count
    }
    private fun finish(error: Throwable?) {
        done=true;cable?.destroy();arena?.close()
        val results=samples.mapValues { (_,values) -> val sorted=values.sorted();mapOf("trials" to sorted.size,"medianMs" to sorted[sorted.size/2],"p95Ms" to sorted[(sorted.lastIndex*.95).toInt()]) }
        val pack=runCatching { Class.forName("net.irisshaders.iris.Iris").getMethod("isPackInUseQuick").invoke(null) }.getOrNull()
        val path=mc.gameDirectory.toPath().resolve(System.getProperty("eln.renderCostOutput","render-cost-checks.json"))
        Files.writeString(path,GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"modelsPerTrial" to poses.size,"warmupTrials" to 40,"samples" to results,"vertices" to vertices,"strides" to strides,"irisPackActive" to pack)))
        if(error!=null)Eln.logger.error("RENDER_COST FAIL",error) else Eln.logger.info("RENDER_COST COMPLETE {}",results)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.renderCostChecks") || done)return
        try {
            ticks++;check(++total<10000) { "Render cost check timed out" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="render-cost-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(36036L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.level!=null && mc.screen==null && ticks>60) {
                    pole=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.STREET_THREE }
                    lamp=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==DecorativeLampStyle.CRYSTAL }
                    arena=ByteBufferBuilder(8*1024*1024)
                    FixedFunction.begin(PoseStack(),MultiBufferSource { error("Recording must not emit geometry") },0,0)
                    try { cable=GridRender.Catenary(Vec3(0.0,7.0,0.0),Vec3(32.0,10.0,18.0)) } finally { FixedFunction.finish() }
                    phase=2
                }
                2->{
                    val (ms,count)=sample(case)
                    vertices[names[case]]=count
                    if(trial++>=40)samples.getOrPut(names[case]) { mutableListOf() }.add(ms)
                    if(trial>=120) { passed+="${names[case]} emits complete real vertex buffers";case++;trial=0 }
                    if(case==names.size) {
                    RenderMeshChecks.verify();passed+="Real emitted vertices, normals and inherited tint match camera/local transforms"
                    PackedEmissionChecks.verify();passed+="Packed entity and unlit buffers match every original vertex attribute including Iris extensions"
                    check(PowerClientChecks.run()==0);passed+="All real generator and motor LED meshes retain live colors and emissive light"
                        finish(null)
                    }
                }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
