package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.kitchen.KitchenElement
import mods.eln.transparentnode.pump.IndustrialPumpElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
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
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in real-world wiring checks, including an occupied face cell and both placement orders. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object OutletCordChecks {
    private val mc get()=Minecraft.getInstance()
    private data class Case(val pos: BlockPos,val rating: CordRating,val front: Direction,val recessed: Boolean)
    private val cases=ArrayList<Case>();private val passed=ArrayList<String>()
    private val worldName="outlet-cords-${System.currentTimeMillis()}"
    private var phase=0;private var ticks=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private var pumpPos=BlockPos.ZERO;private var fridgePos=BlockPos.ZERO
    private lateinit var selected: Case
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun outlet(p: ServerPlayer,c: Case)=f(p).machine(c.pos) as OutletElement
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ body(s.playerList.players.first()) },s) }
    private fun ready()=work?.isDone!=false && ticks>40
    private fun next(value: Int) { phase=value;ticks=0 }
    private fun setup(p: ServerPlayer) {
        val w=p.serverLevel()
        fun create(rating: CordRating,front: Direction,recessed: Boolean) {
            val i=cases.size;val wall=BlockPos(256+(i%6)*16,80,256+(i/6)*16)
            for(dx in -2..7)for(dz in -2..7) {
                val at=wall.offset(dx,-1,dz);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState())
            }
            repeat(3) { w.setBlockAndUpdate(wall.above(it),Blocks.BRICKS.defaultBlockState()) }
            val c=Case(if(recessed)wall else wall.relative(front.toFacing()),rating,front,recessed);cases+=c
            fun placeOutlet() {
                val stack=Eln.findItemStack(rating.outletName,1)
                if(recessed)stack.tagCompound=CompoundTag().apply { put("outletWall",net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.BRICKS.defaultBlockState())) }
                check(Eln.transparentNodeItem.placeBlockAt(stack,p,w,c.pos,front.toFacing(),recessed))
                check(outlet(p,c).front==front && outlet(p,c).recessed==recessed)
            }
            fun wires() {
                for(y in 1..2) {
                    val at=wall.above(y).relative(front.toFacing())
                    val stack=if(y==1)f(p).spool() else Eln.findItemStack("Electrical Source",1)
                    check(Eln.sixNodeItem.placeBlockAt(stack,p,w,at,front.toFacing(),.5f,.5f,.5f))
                    if(y==2) {
                        val source=(f(p).node(at) as SixNode).getElement(front.inverse) as mods.eln.sixnode.electricalsource.ElectricalSourceElement
                        source.readConfigTool(CompoundTag().apply { putDouble("voltage",rating.volts) },p)
                    }
                }
            }
            // Neither the occupied front cell nor the order of cable placement may gate power.
            if(recessed)w.setBlockAndUpdate(wall.relative(front.toFacing()),Blocks.STONE.defaultBlockState())
            if(i%2==0) { placeOutlet();wires() } else { wires();placeOutlet() }
            val o=outlet(p,c)
            Eln.simulator.addElectricalComponent(Resistor(o.output,null).apply { resistance=rating.volts*rating.volts/250 })
        }
        for(rating in CordRating.entries)for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))create(rating,front,true)
        for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))create(CordRating.V480,front,false)
        selected=cases.single { it.rating==CordRating.V480 && it.front==Direction.ZP && it.recessed }
        fridgePos=selected.pos.south();w.removeBlock(fridgePos,false)
        f(p).placeMachine(Eln.findItemStack("Electric Mini Fridge",1),fridgePos)
        pumpPos=selected.pos.east(5)
        val pump=f(p).placeMachine(Eln.findItemStack("480 V Industrial Fluid Pump",1),pumpPos) as IndustrialPumpElement
        check(pump.cord.attach(outlet(p,selected).anchor(),false))
        p.teleportTo(w,pumpPos.x+2.0,pumpPos.y+2.0,pumpPos.z+5.0,150f,20f)
    }
    private fun verify(p: ServerPlayer,reloaded: Boolean=false) {
        for(c in cases) {
            val o=outlet(p,c)
            check(o.recessed==c.recessed)
            check(kotlin.math.abs(o.input.voltage-c.rating.volts)<c.rating.volts*.02) { "$c has ${o.input.voltage} V" }
            if(!reloaded)check(kotlin.math.abs(o.contact.current)>.05) { "$c draws no load current" }
            val physical=o.node!!.nodeConnectionList.filter { it.EC.isNotEmpty() }
            check(physical.size==1) { "$c has ${physical.size} physical feeder connections" }
        }
        val fridge=f(p).machine(fridgePos) as KitchenElement
        check(kotlin.math.abs(fridge.load.voltage)<.01) { "Adjacent appliance received power without a cord" }
        val pump=f(p).machine(pumpPos) as IndustrialPumpElement
        check(pump.cord.connected && pump.supply.voltage>470) { "480 V pump cord has ${pump.supply.voltage} V / ${pump.cord.state}" }
    }
    private fun toggle(p: ServerPlayer) {
        outlet(p,selected).networkUnserialize(DataInputStream(ByteArrayInputStream(byteArrayOf(82))))
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("outlet-cord-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"outlets" to cases.size,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("OUTLET_CORD FAIL phase $phase",error) else Eln.logger.info("OUTLET_CORD COMPLETE {}",passed)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.outletCordChecks") || done)return
        try {
            check(++ticks<2400) { "Timeout in phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(38038L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);next(1)
                }
                1->if(mc.player!=null && mc.screen==null) { server(::setup);next(2) }
                2->if(ready()) { work?.join();server { p -> verify(p);toggle(p) };next(3) }
                3->if(ready()) {
                    work?.join();passed+="All 8 outlet ratings on 4 wall faces power real loads behind an occupied face cell; original surface mounting also works"
                    server { p -> check(kotlin.math.abs(outlet(p,selected).input.voltage)<.01);toggle(p) };next(4)
                }
                4->if(ready()) { work?.join();server { p -> verify(p) };next(5) }
                5->if(ready()) {
                    work?.join();passed+="Cable/wireless mode isolates and restores the feeder; 480 V pump stays corded and adjacent fridge stays isolated"
                    // Exercise the actual production mesh under the active shader consumer too.
                    RenderMeshChecks.verify();PackedEmissionChecks.verify();passed+="Live mesh transforms and shader attributes passed"
                    mc.level!!.disconnect();mc.disconnect(TitleScreen());next(6)
                }
                6->if(mc.singleplayerServer==null && ticks>30) { mc.createWorldOpenFlows().openWorld(worldName) { error("Native world reopen failed") };next(7) }
                7->if(mc.player!=null && mc.level!=null && mc.screen==null && ticks>60) { server { p -> verify(p,true) };next(8) }
                8->if(ready()) { work?.join();passed+="Cold world reload preserves all wired outlet feeds, original mounts and the live 480 V cord link";finish(null) }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
