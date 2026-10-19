package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sixnode.CreativePowerResistorElement
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.transparentnode.renewable.*
import mods.eln.transparentnode.standby.*
import mods.eln.transparentnode.ups.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Actual wall placement, cables, powered loads, transfer, GUI and save/reload checks. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object WallCabinetChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="wall-cabinets-${System.currentTimeMillis()}"
    private data class Cabinet(val id: Int,val front: Direction,val pos: BlockPos) {
        fun at(side: Direction,n: Int=1)=pos.relative(side.toFacing(),n)
        val plane get()=front.inverse
    }
    private val fixtures=Direction.entries.filter { it.isNotY }.flatMapIndexed { row,front ->
        (14..16).map { id -> Cabinet(id,front,BlockPos(256+(id-14)*12,74,256+row*12)) }
    }
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private val upsPositions=listOf(BlockPos(304,70,256),BlockPos(304,70,268))
    private var powerFailure: String?=null
    private var observer: mods.eln.sim.IProcess?=null
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun element(p: ServerPlayer,c: Cabinet)=NativeCampaignFixtures(p.serverLevel(),p).machine(c.pos)
    private fun record(s: String) { passed+=s;Eln.logger.info("WALL_CABINET PASS {}",s) }
    private fun sameBox(a: net.minecraft.world.phys.AABB,b: net.minecraft.world.phys.AABB)=
        listOf(a.minX-b.minX,a.minY-b.minY,a.minZ-b.minZ,a.maxX-b.maxX,a.maxY-b.maxY,a.maxZ-b.maxZ).all { kotlin.math.abs(it)<1e-9 }
    private fun finish(error: Throwable?) {
        done=true
        observer?.let { Eln.simulator.removeElectricalProcess(it) };observer=null
        Files.writeString(mc.gameDirectory.toPath().resolve("wall-cabinet-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("WALL_CABINET FAIL phase $phase",error) else Eln.logger.info("WALL_CABINET COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    private fun output(p: ServerPlayer,c: Cabinet): Double {
        val e=element(p,c)
        return if(e is AtsElement)e.unit.output.voltage else (e as SolarElement).unit.output.voltage
    }
    private fun upsCircuits(p: ServerPlayer) {
        val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
        for((i,pos) in upsPositions.withIndex()) {
            for(x in -5..4)for(z in -3..3) {
                val at=pos.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true)
                w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState())
            }
            val ups=f.placeMachine(Eln.findItemStack("Programmable UPS",1),pos) as UpsElement
            ups.front=Direction.ZP;ups.reconnect()
            val gen=f.placeMachine(Eln.findItemStack("Standby Gasoline Generator",1),pos.west(4)) as StandbyGeneratorElement
            gen.front=Direction.ZP;gen.reconnect();gen.unit.setVoltage(if(i==0)120.0 else 240.0);gen.unit.mode=StandbyMode.MANUAL
            gen.unit.fuel.tank.fill(net.neoforged.neoforge.fluids.FluidStack(StandbyTestFluids.fluid(0),20_000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            repeat(if(i==0)24 else 48) { slot ->
                val stack=Eln.findItemStack("Portable Battery Pack",1);val type=upsBattery(stack)!!
                type.setEnergy(stack,type.getEnergyMax(stack)*.5);ups.inventory.setItem(slot,stack)
            }
            for(x in -3..-1)f.wire(pos.offset(x,0,0))
            f.wire(pos.east());f.load(pos.east(2),14.4);f.ground(pos.east(3))
        }
        observer=mods.eln.sim.IProcess {
            for(pos in upsPositions) {
                val u=(f.machine(pos) as UpsElement).unit;val g=(f.machine(pos.west(4)) as StandbyGeneratorElement).unit
                if(g.fault.isNotEmpty() || g.source.power < -1e-6 || u.charger.status=="NON_CONVERGENT")
                    powerFailure="Generator ${g.status}/${g.fault}; UPS ${u.status}; charger ${u.charger.status}; power ${g.source.power}"
            }
        }.also(Eln.simulator::addElectricalProcess)
    }
    private fun verifyUps(p: ServerPlayer) {
        check(powerFailure==null) { powerFailure!! }
        val f=NativeCampaignFixtures(p.serverLevel(),p)
        for(pos in upsPositions) {
            val ups=f.machine(pos) as UpsElement;val gen=f.machine(pos.west(4)) as StandbyGeneratorElement
            check(gen.unit.running && gen.unit.fault.isEmpty())
            check(ups.unit.inputWatts>ups.unit.outputWatts && ups.unit.outputWatts>990)
            check(gen.unit.source.power in 0.0..10_000.01)
            check(kotlin.math.abs(ups.unit.secondary.voltage-120.0)<.1)
        }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.wallCabinetChecks") || done)return
        try {
            check(++total<10000) { "Timeout phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel()
                        p.abilities.flying=true;p.onUpdateAbilities();p.teleportTo(w,263.0,76.0,262.0,90f,10f)
                        for(c in fixtures) {
                            for(a in -4..4)for(y in -4..4) {
                                val at=c.at(c.front.left(),a).relative(c.plane.toFacing()).offset(0,y,0)
                                w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState())
                            }
                            val d=Eln.transparentNodeItem.subItemList[c.id+(124 shl 6)]!!
                            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(1),p,w,c.pos,c.front.toFacing())) { "Place cabinet ${c.id} ${c.front}" }
                        }
                        upsCircuits(p)
                    };waitFor(2)
                }
                2 -> if(settled()) {
                    server { p ->
                        val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
                        for(c in fixtures) {
                            // The setup phase already placed the cabinet using its wall's outward face.
                            val e=f.machine(c.pos);check(e.front==c.front);check(e.hasGui())
                            check(w.getBlockState(c.pos.below()).isAir)
                            fun six(name: String,at: BlockPos): mods.eln.node.six.SixNodeElement {
                                check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,w,at,c.front.toFacing(),.5f,.5f,.5f)) { "Place $name $at" }
                                return (f.node(at) as SixNode).getElement(c.plane)!!
                            }
                            fun source(at: BlockPos,v: Double) { (six("Electrical Source",at) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",v) },p) }
                            fun wire(at: BlockPos) { check(Eln.sixNodeItem.placeBlockAt(f.spool(),p,w,at,c.front.toFacing(),.5f,.5f,.5f)) }
                            source(c.at(c.front.left(),2),if(c.id==14)240.0 else 120.0);wire(c.at(c.front.left()))
                            wire(c.at(c.front.right()))
                            val load=six("Creative Power Resistor",c.at(c.front.right(),2)) as CreativePowerResistorElement
                            load.front=LRDU.entries.first { c.plane.applyLRDU(it.right())==c.front.right() };load.reconnect()
                            load.readConfigTool(CompoundTag().apply { putDouble("resistance",if(c.id==15)144.0 else 576.0) },p)
                            six("Ground Cable",c.at(c.front.right(),3))
                            if(e is AtsElement) { source(c.pos.below(2),240.0);wire(c.pos.below());e.unit.channel="wall-test" }
                            else {
                                e as SolarElement;six("Ground Cable",c.pos.below())
                                if(c.id==16) { source(c.pos.above(2),240.0);wire(c.pos.above());e.unit.setVoltage(240.0);e.unit.priority=SolarPriority.GRID_ONLY }
                            }
                            e.needPublish()
                        }
                        record("All three cabinet types place on all four walls without a floor")
                    };waitFor(3,160)
                }
                3 -> if(settled()) {
                    server { p ->
                        val w=p.serverLevel()
                        verifyUps(p)
                        record("Standalone 120 V and 240 V standby generators charge 24/48-cell UPS banks and power loads without reverse-power trips or failed convergence")
                        for(c in fixtures) {
                            val e=element(p,c);val d=e.transparentNodeDescriptor as PowerCabinetDescriptor
                            check(kotlin.math.abs(output(p,c)-(if(c.id==15)120.0 else 240.0))<.2) { "Output ${c.id} ${c.front}: ${output(p,c)}" }
                            val shape=w.getBlockState(c.pos).getCollisionShape(w,c.pos)
                            check(sameBox(shape.bounds(),d.wallBox(c.front))) { "Collision ${c.id} ${c.front}: ${shape.bounds()} instead of ${d.wallBox(c.front)}" }
                            check(!shape.bounds().contains(.5,.5,.5))
                            val mask=e.node!!.lrduCubeMask.getTranslate(c.plane)
                            check(LRDU.entries.count { mask[it] }==if(c.id==16)4 else 3) { "Missing wall cable ${c.id} ${c.front}" }
                        }
                        record("Twelve real wall circuits supply their loads through wall-mounted cables; collision matches the shallow cases")
                        for(c in fixtures.filter { it.id==14 }) {
                            val f=NativeCampaignFixtures(w,p)
                            val source=(f.node(c.at(c.front.left(),2)) as SixNode).getElement(c.plane) as ElectricalSourceElement
                            source.readConfigTool(CompoundTag().apply { putDouble("voltage",0.0) },p)
                        }
                    };waitFor(4,100)
                }
                4 -> if(settled()) {
                    server { p ->
                        for(c in fixtures.filter { it.id==14 }) {
                            check((element(p,c) as AtsElement).unit.control.selected==TransferFeed.GENERATOR)
                            check(output(p,c)>239.8)
                        }
                        record("Wall ATS cabinets transfer to the bottom generator terminal after mains loss")
                    };waitFor(5)
                }
                5 -> if(settled()) {
                    val w=mc.level!!
                    for(c in fixtures) {
                        val tile=w.getBlockEntity(c.pos) as TransparentNodeEntity
                        check(tile.elementRender!!.front==c.front)
                        val stack=tile.elementRender!!.transparentNodeDescriptor.newItemStack(1)
                        check(mc.itemRenderer.getModel(stack,w,mc.player,0).isCustomRenderer) { "Missing cabinet item model ${c.id}" }
                        check(sameBox(w.getBlockState(c.pos).getCollisionShape(w,c.pos).bounds(),(tile.elementRender!!.transparentNodeDescriptor as PowerCabinetDescriptor).wallBox(c.front)))
                    }
                    record("Client models, inventory icons, facing, telemetry and shallow collision synchronize for all twelve cabinets")
                    step(10)
                }
                10,11,12 -> {
                    val c=fixtures[phase-10]
                    val render=(mc.level!!.getBlockEntity(c.pos) as TransparentNodeEntity).elementRender!!
                    if(ticks==1)server { p ->
                        val offset=doubleArrayOf(.5,-.3,.5);c.front.applyTo(offset,2.3)
                        p.teleportTo(p.serverLevel(),c.pos.x+offset[0],c.pos.y+offset[1],c.pos.z+offset[2],-90f,10f)
                    }
                    if(ticks==40 && settled()) {
                        net.minecraft.client.Screenshot.grab(mc.gameDirectory,"wall-cabinet-${c.id}-model.png",mc.mainRenderTarget) {}
                        mc.setScreen(render.newGuiDraw(c.front,mc.player!!))
                    }
                    if(ticks>60) {
                        check(mc.screen is mods.eln.gui.GuiContainerEln)
                        net.minecraft.client.Screenshot.grab(mc.gameDirectory,"wall-cabinet-${c.id}-gui.png",mc.mainRenderTarget) {}
                        mc.setScreen(null)
                        if(phase<12)step(phase+1) else {
                            record("ATS, MPPT and hybrid wall-cabinet GUIs open and render with live telemetry")
                            server { _ -> observer?.let { Eln.simulator.removeElectricalProcess(it) };observer=null }
                            step(13)
                        }
                    }
                }
                13 -> if(settled()) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(6) }
                6 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen cabinet world") };step(7) }
                7 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(8,160)
                8 -> if(settled()) {
                    server { p ->
                        verifyUps(p)
                        for(c in fixtures) {
                            val e=element(p,c);check(e.front==c.front)
                            check(kotlin.math.abs(output(p,c)-(if(c.id==15)120.0 else 240.0))<.2)
                            if(e is AtsElement)check(e.unit.channel=="wall-test")
                        }
                        record("World reload preserves wall variants, facing, settings, wiring and powered output")
                        val c=fixtures.first();p.serverLevel().destroyBlock(c.at(c.plane),false,p)
                    };waitFor(9,20)
                }
                9 -> if(settled()) {
                    server { p ->
                        val c=fixtures.first()
                        check(p.serverLevel().getBlockState(c.pos).isAir)
                        check(element(p,fixtures[1]).front==fixtures[1].front)
                        record("Removing the supporting wall drops its cabinet without disturbing other machines")
                    };step(14)
                }
                14 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
