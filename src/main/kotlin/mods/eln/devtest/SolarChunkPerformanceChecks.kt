package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.SurfaceMounting
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.solarpanel.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.nio.file.Files
import java.nio.file.Path

/** Isolated dedicated-server regression: passive solar work must not generate absent chunks. */
@EventBusSubscriber(modid=Eln.MODID)
object SolarChunkPerformanceChecks {
    private var world: ServerLevel?=null
    private var loads=0
    private var firstLoadStack: String? = null
    @SubscribeEvent fun chunk(event: ChunkEvent.Load) {
        if(event.level===world) {
            loads++
            if(firstLoadStack==null)firstLoadStack=Throwable("first chunk load").stackTraceToString()
        }
    }
    @SubscribeEvent fun start(event: ServerStartedEvent) {
        if(!java.lang.Boolean.getBoolean("eln.solarChunkPerformanceChecks"))return
        val results=linkedMapOf<String,Any>()
        var failure:Throwable?=null
        val w=event.server.overworld();world=w
        try {
            w.dayTime=6000L
            fun sample(name:String,x:Int,action:(BlockPos)->Unit) {
                val pos=BlockPos(x,80,32000)
                check(w.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)==null)
                val before=w.chunkSource.loadedChunksCount;loads=0;firstLoadStack=null
                val began=System.nanoTime()
                action(pos)
                val loaded=w.chunkSource.loadedChunksCount-before
                results[name]=mapOf("loadedChunks" to loaded,"loadEvents" to loads,"milliseconds" to (System.nanoTime()-began)/1e6,"firstLoadStack" to firstLoadStack)
                if(!java.lang.Boolean.getBoolean("eln.solarChunkBaseline"))check(loaded==0 && loads==0) { "$name forced $loaded chunks / $loads load events" }
            }
            sample("roofLookup",32000) { SolarRoofMount.at(w,it) }
            sample("roofSupport",33024) { SolarRoofMount.fromSupport(w,it) }
            val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<SolarPanelDescriptor>().first { it.name=="Small Solar Panel" }
            fun panel(pos:BlockPos,legacy:Boolean):SolarPanelElement {
                val n=TransparentNode();n.coordinate=Coordinate(pos.x,pos.y,pos.z,w)
                val e=SolarPanelElement(n,descriptor);n.element=e;e.front=Direction.XN
                e.readFromNBT(CompoundTag().apply { putDouble("panelAlpha",Math.PI/2);if(!legacy)putInt("roofUphill",-1) })
                return e
            }
            sample("solarSimulation",34048) { pos ->
                val e=panel(pos,false);val process=SolarPannelSlowProcess(e)
                repeat(200) { check(process.solarLight.isFinite()) }
            }
            sample("legacySolarRestore",35072) { pos ->
                val e=panel(pos,true)
                try { e.initialize() } finally { e.node!!.disconnect() }
            }
            sample("partialSupport",36096) { pos ->
                for(side in Direction.entries) {
                    SurfaceMounting.supports(w,pos,side);SurfaceMounting.inset(w,pos,side)
                }
            }
        } catch(t:Throwable) { failure=t }
        finally {
            world=null
            val out=Path.of(System.getProperty("eln.solarChunkReport","solar-chunk-performance.json"))
            Files.createDirectories(out.toAbsolutePath().parent)
            Files.writeString(out,GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to (failure==null),"failure" to failure?.stackTraceToString(),"observations" to results)))
            Eln.LOGGER.info("SOLAR_CHUNK_PERFORMANCE {} {}",if(failure==null)"passed" else "failed",results)
            event.server.halt(false)
        }
    }
}
