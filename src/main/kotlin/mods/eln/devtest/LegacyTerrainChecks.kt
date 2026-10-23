package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.client.terrain.*
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeBlockEntity
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import java.nio.file.Files

/** Exhaustive registered-descriptor audit; synthetic client render state, real production model code. */
internal object LegacyTerrainChecks {
    private val mc get()=Minecraft.getInstance()
    private fun snapshot(owner:Any,original:Boolean):TerrainMesh {
        System.setProperty("eln.disableTerrainModels",original.toString())
        val c=TerrainCapture();c.enabled=true
        FixedFunction.setMeshSink(c);FixedFunction.begin(PoseStack(),MultiBufferSource { error("External UI must stay live") },0,0)
        try {
            GL11.glTranslatef(.5f,.5f,.5f)
            if(owner is SixNodeElementRender) { owner.side.glRotateXnRef();GL11.glTranslatef(-.5f,0f,0f) }
            TerrainModels.drawElement(owner) { when(owner) {
                is SixNodeElementRender->owner.draw()
                is TransparentNodeElementRender->owner.draw()
            } }
        } finally { FixedFunction.setMeshSink(null);FixedFunction.finish();System.clearProperty("eln.disableTerrainModels") }
        return c.finish()
    }
    private fun vertices(m:TerrainMesh)=m.batches.flatMap { b->b.vertices.toList().chunked(9).map { b.material.toString()+it.joinToString(",") } }.sorted()
    fun run() {
        if(!java.lang.Boolean.getBoolean("eln.terrainCatalogChecks"))return
        val rows=ArrayList<Map<String,Any?>>()
        val failures=ArrayList<Map<String,Any?>>()
        val ds=Eln.sixNodeItem.subItemList.values.filterIsInstance<SixNodeDescriptor>()+
            Eln.transparentNodeItem.subItemList.values.filterIsInstance<TransparentNodeDescriptor>()
        for(d in ds) {
            val name=when(d) { is SixNodeDescriptor->d.name;is TransparentNodeDescriptor->d.name;else->error("descriptor") }
            var entity:NodeBlockEntity?=null
            try {
                val pos=if(d is mods.eln.sixnode.energymeter.EnergyMeterDescriptor)BlockPos(292,89,313) else BlockPos(256,79,400)
                val owner:Any
                if(d is SixNodeDescriptor) {
                    val e=SixNodeEntity(pos,Eln.sixNodeBlock.defaultBlockState()).apply { level=mc.level };entity=e
                    val r=d.RenderClass.getConstructor(SixNodeEntity::class.java,Direction::class.java,SixNodeDescriptor::class.java)
                        .newInstance(e,Direction.YN,d) as SixNodeElementRender
                    r.front=LRDU.Up;r.connectionType=CableRenderType();e.elementRenderList[Direction.YN.int]=r;owner=r
                } else {
                    d as TransparentNodeDescriptor
                    val e=TransparentNodeEntity(pos,Eln.transparentNodeBlock.defaultBlockState()).apply { level=mc.level };entity=e
                    val r=d.RenderClass.getConstructor(TransparentNodeEntity::class.java,TransparentNodeDescriptor::class.java).newInstance(e,d) as TransparentNodeElementRender
                    r.front=Direction.XN;e.elementRender=r;owner=r
                }
                // Network packets normally initialize these legacy subclass fields (some hide
                // the base renderer's front). Exercise a valid, unpowered client state here.
                var type:Class<*>?=owner.javaClass
                while(type!=null) {
                    for(f in type.declaredFields)if(!java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                        f.isAccessible=true
                        if(f.type==LRDU::class.java && f.get(owner)==null)f.set(owner,LRDU.Up)
                        if(f.type==CableRenderType::class.java && f.get(owner)==null)f.set(owner,CableRenderType())
                        if(f.name=="job" && f.type.isEnum && f.get(owner)==null)f.set(owner,f.type.enumConstants.first())
                        if(f.name=="blockFacing" && owner is mods.eln.transparentnode.floodlight.FloodlightRender)f.set(owner,mods.eln.misc.HybridNodeDirection.YN)
                    }
                    type=type.superclass
                }
                TerrainModels.update(entity)
                val cold=TerrainModels.meshFor(owner)
                // Warm lazy OBJ lists before comparing steady draws. Their first compilation
                // changes the legacy emulator's current UV/normal; subsequent replays do not.
                snapshot(owner,true)
                val full=snapshot(owner,true)
                val live=snapshot(owner,false)
                if(cold!=null)check(vertices(full)==vertices(TerrainMesh(cold.batches+live.batches))) { "Cold capture changed steady geometry" }
                TerrainModels.refreshNow(entity)
                val baked=TerrainModels.meshFor(owner)
                if(baked!=null && vertices(full)!=vertices(TerrainMesh(baked.batches+live.batches))) {
                    Files.writeString(mc.gameDirectory.toPath().resolve("legacy-terrain-mismatch.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("descriptor" to name,"full" to full,"baked" to baked,"live" to live)))
                    error("Static+live vertices differ")
                }
                TerrainModels.refreshNow(entity)
                check(baked===TerrainModels.meshFor(owner)) { "Unchanged draw rebuilt geometry" }
                if(owner is mods.eln.mechanical.ShaftRender) {
                    owner.angle=1.234
                    TerrainModels.refreshNow(entity)
                    check(baked===TerrainModels.meshFor(owner)) { "Shaft rotation rebuilt stationary geometry" }
                    check(vertices(snapshot(owner,true))==vertices(TerrainMesh((baked?.batches ?: emptyList())+snapshot(owner,false).batches))) { "Rotated shaft static/live partition differs" }
                }
                if(owner is mods.eln.node.six.SixNodeElementRender && owner.sixNodeDescriptor is mods.eln.sixnode.energymeter.EnergyMeterDescriptor) {
                    var t:Class<*>?=owner.javaClass
                    while(t!=null) {
                        for(field in t.declaredFields)if(field.name in setOf("energyStack","timerCouter","timerCounter")) {
                            field.isAccessible=true;field.setDouble(owner,1234.56)
                        }
                        t=t.superclass
                    }
                    TerrainModels.refreshNow(entity)
                    check(baked===TerrainModels.meshFor(owner)) { "Meter readings rebuilt stationary geometry" }
                    check(vertices(snapshot(owner,true))==vertices(TerrainMesh((baked?.batches ?: emptyList())+snapshot(owner,false).batches)))
                }
                if(owner is mods.eln.sixnode.electricalcable.UtilityCableRender && !(d as mods.eln.sixnode.electricalcable.UtilityCableDescriptor).insulated) {
                    val heat=owner.javaClass.getDeclaredField("temperatureCelsius").apply { isAccessible=true }
                    heat.setFloat(owner,850f)
                    TerrainModels.refreshNow(entity)
                    check(baked===TerrainModels.meshFor(owner)) { "Hot cable glow rebuilt stationary conductor" }
                    check(vertices(snapshot(owner,true))==vertices(TerrainMesh((baked?.batches ?: emptyList())+snapshot(owner,false).batches))) { "Hot cable glow static/live partition differs" }
                }
                rows+=mapOf("descriptor" to name,"renderer" to owner.javaClass.name,"bakedVertices" to (baked?.batches?.sumOf { it.vertices.size/9 } ?: 0),
                    "liveVertices" to live.batches.sumOf { it.vertices.size/9 },"fullVertices" to full.batches.sumOf { it.vertices.size/9 },"bodyRegistered" to (baked!=null))
            } catch(t:Throwable) {
                failures+=mapOf("descriptor" to name,"error" to t.stackTraceToString())
            } finally { entity?.destructor() }
        }
        val path=mc.gameDirectory.toPath().resolve("legacy-terrain-audit.json")
        Files.writeString(path,GsonBuilder().setPrettyPrinting().create().toJson(mapOf("rows" to rows,"failures" to failures,"descriptors" to ds.size)))
        Eln.logger.info("LEGACY_TERRAIN {} descriptors, {} checked, {} errors",ds.size,rows.size,failures.size)
        val itemRows=net.minecraft.core.registries.BuiltInRegistries.ITEM.filterIsInstance<mods.eln.generic.DescriptorBlockItem<*>>()
            .filter { it.descriptor is mods.eln.client.itemrender.IItemRenderer }.flatMap { item ->
                listOf(net.minecraft.world.item.ItemDisplayContext.GUI,net.minecraft.world.item.ItemDisplayContext.GROUND,net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).map { context ->
                    runCatching { mods.eln.client.itemrender.BakedNodeItems.audit(net.minecraft.world.item.ItemStack(item),context) }.getOrElse {
                        mapOf("item" to net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),"context" to context.name,"error" to it.stackTraceToString())
                    }
                }
            }
        Files.writeString(mc.gameDirectory.toPath().resolve("baked-item-audit.json"),GsonBuilder().setPrettyPrinting().create().toJson(itemRows))
        Eln.logger.info("BAKED_ITEMS {} previews, {} baked",itemRows.size,itemRows.count { it["baked"]==true })
        check(itemRows.none { it.containsKey("error") }) { "Item audit failed; see baked-item-audit.json" }
        check(failures.isEmpty()) { "${failures.size} legacy audit failures; see $path" }
    }
}
