package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.pump.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.level.material.Fluids
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opt-in live pump, world intake, real Ender IO conduit/tank, GUI and sound integration. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object PumpChecks {
    private val mc get()=Minecraft.getInstance()
    private val base=BlockPos(128,80,128)
    private val sink=base.west(3)
    private val secondSink=base.west().north()
    private val worldName="pump35-${System.currentTimeMillis()}"
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var job: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private var heard=false;private var screenshot=false
    private var measured=0L;private var stock=0L;private var startTime=0L
    private val received=java.util.concurrent.atomic.AtomicLong()
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun pump(p: ServerPlayer)=f(p).machine(base) as IndustrialPumpElement
    private fun record(s: String) { passed+=s;Eln.logger.info("PUMP_CHECK PASS {}",s) }
    private fun step(n: Int,delay: Int=0) { phase=n;ticks=0;ready=(mc.singleplayerServer?.tickCount ?: 0)+delay }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;job=CompletableFuture.runAsync({ body(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(job?.isDone==false)return false;job?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun packet(id: Int)=DataInputStream(ByteArrayInputStream(byteArrayOf(id.toByte())))
    private fun clientPump()=((mc.level?.getBlockEntity(base) as? TransparentNodeEntity)?.elementRender as? IndustrialPumpRender)
    private fun cap(p: ServerPlayer,pos: BlockPos)=p.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,null)!!
    // Optional test integration only; the production pump depends solely on NeoForge capabilities.
    private fun call(target: Any,name: String,vararg args: Any?): Any? {
        val candidates=target.javaClass.methods.filter { it.name==name && it.parameterCount==args.size }
        val method=candidates.singleOrNull { m -> m.parameterTypes.indices.all { i -> args[i]==null || m.parameterTypes[i].isInstance(args[i]) || m.parameterTypes[i].isPrimitive } } ?: error("No method $name on ${target.javaClass}")
        return method.invoke(target,*args)
    }
    private fun constant(type: String,name: String)=Class.forName(type).getField(name).get(null)
    private fun tank(p: ServerPlayer,pos: BlockPos) {
        val w=p.serverLevel();w.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("enderio:pressurized_fluid_tank")).defaultBlockState())
        val tile=w.getBlockEntity(pos)!!
        for(side in net.minecraft.core.Direction.entries)call(tile,"setIOMode",side,constant("com.enderio.enderio.api.io.IOMode","NONE"))
    }
    private fun conduits(p: ServerPlayer) {
        val w=p.serverLevel()
        val key=ResourceKey.createRegistryKey<Any>(ResourceLocation.parse("enderio:conduit"))
        val registry=w.registryAccess().registryOrThrow(key)
        val holder=registry.getHolderOrThrow(ResourceKey.create(key,ResourceLocation.parse("enderio:ender_fluid")))
        for(i in 1..2) {
            val pos=base.west(i)
            w.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("enderio:conduit")).defaultBlockState())
            val tile=w.getBlockEntity(pos)!!
            call(tile,"addConduit",holder,net.minecraft.core.Direction.UP,p)
        }
    }
    private fun configureConduits(p: ServerPlayer) {
        val w=p.serverLevel();val key=ResourceKey.createRegistryKey<Any>(ResourceLocation.parse("enderio:conduit"))
        val holder=w.registryAccess().registryOrThrow(key).getHolderOrThrow(ResourceKey.create(key,ResourceLocation.parse("enderio:ender_fluid")))
        for((i,side) in listOf(1 to net.minecraft.core.Direction.EAST,2 to net.minecraft.core.Direction.WEST,1 to net.minecraft.core.Direction.NORTH)) {
            val tile=w.getBlockEntity(base.west(i))!!
            val extracting=side==net.minecraft.core.Direction.EAST
            var cfg=constant("com.enderio.enderio.content.conduits.type.fluid.FluidConduitConnectionConfig","DEFAULT")
            cfg=call(cfg,"withIsInsert",!extracting)!!;cfg=call(cfg,"withIsExtract",extracting)!!
            cfg=call(cfg,"withExtractRedstoneControl",constant("com.enderio.enderio.api.io.RedstoneControl","ALWAYS_ACTIVE"))!!
            call(tile,"setConnectionConfig",holder,side,cfg)
            call(tile,"setConnectionStatus",holder,side,constant("com.enderio.enderio.api.conduits.connection.ConnectionStatus","CONNECTED_BLOCK"))
            call(tile,"tryConnectTo",holder,if(i==1)net.minecraft.core.Direction.WEST else net.minecraft.core.Direction.EAST,true)
            call(tile,"markNodesDirty")
        }
    }
    private fun beginMeasurement(p: ServerPlayer) { measured=received.get();stock=pump(p).ports.output.fluidAmount.toLong();startTime=p.serverLevel().gameTime }
    private fun checkFlow(p: ServerPlayer,volts: Double) {
        val e=pump(p);val dt=(p.serverLevel().gameTime-startTime)/20.0
        val moved=received.get()-measured+e.ports.output.fluidAmount-stock
        val expected=40_000*abs(e.supply.voltage)/480*dt
        check(abs(moved-expected)<expected*.07+2500) { "Flow $moved vs $expected at ${e.supply.voltage} V (${e.status})" }
        check(received.get()>measured) { "Ender IO conduit delivered no fluid" }
        check(abs(e.supply.voltage-volts)<1) { "Unexpected supply ${e.supply.voltage}" }
        record("Live Ender Fluid Conduits deliver voltage-scaled water at $volts V (${moved/dt/1000} B/s)")
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("pump35-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"receivedMb" to received.get(),"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("PUMP_CHECK FAIL phase $phase",error) else Eln.logger.info("PUMP_CHECK COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun sounds(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("eln.pumpChecks") && event.sound?.location?.toString()=="eln:industrial_pump")heard=true
    }
    @SubscribeEvent fun drain(event: net.neoforged.neoforge.event.tick.ServerTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.pumpChecks") || done || phase<3)return
        val w=event.server.overworld();if(!w.hasChunkAt(sink))return
        for(pos in listOf(sink,secondSink)) {
            val handler=w.getCapability(Capabilities.FluidHandler.BLOCK,pos,null) ?: continue
            received.addAndGet(handler.drain(Int.MAX_VALUE,IFluidHandler.FluidAction.EXECUTE).amount.toLong())
        }
    }
    @SubscribeEvent fun frame(event: RenderFrameEvent.Post) {
        if(screenshot && !mc.levelRenderer.sectionStatistics.startsWith("C: 0/0")) {
            screenshot=false;net.minecraft.client.Screenshot.grab(mc.gameDirectory,"pump35-gui.png",mc.mainRenderTarget) {}
        }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.pumpChecks") || done)return
        try {
            ticks++;check(++total<14000) { "Timeout at $phase" }
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(35035L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1->if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();val f=f(p);w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server)
                        w.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,w.server)
                        for(x in -8..8)for(z in -8..8) { val at=base.offset(x,-2,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());w.setBlockAndUpdate(at.above(),Blocks.STONE.defaultBlockState()) }
                        for(x in -1..0)for(z in -1..0)w.setBlockAndUpdate(base.offset(x,-1,z),Blocks.WATER.defaultBlockState())
                        f.placeMachine(Eln.findItemStack("480 V Industrial Fluid Pump",1),base);f.wire(base.east());f.source(base.east(2),480.0)
                        tank(p,sink);tank(p,secondSink);conduits(p)
                        p.teleportTo(w,base.x-3.5,base.y+2.5,base.z+5.5,145f,22f)
                    };step(2,60)
                }
                2->if(settled()) { server { p -> configureConduits(p) };step(20,40) }
                20->if(settled()) { server { p -> beginMeasurement(p) };step(3,100) }
                3->if(settled()) {
                    server { p -> checkFlow(p,480.0);check(p.serverLevel().getFluidState(base.below()).isSource);f(p).voltage(base.east(2),432.0) };step(4,40)
                }
                4->if(settled()) { server { p -> beginMeasurement(p) };step(5,100) }
                5->if(settled()) { server { p -> checkFlow(p,432.0);f(p).voltage(base.east(2),528.0) };step(6,40) }
                6->if(settled()) { server { p -> beginMeasurement(p) };step(7,100) }
                7->if(settled() && clientPump()!=null) {
                    server { p -> checkFlow(p,528.0) };step(8,10)
                }
                8->if(settled()) {
                    RenderMeshChecks.verify();record("Cached model emission preserves positions, normals and live tint under camera/animation/nonuniform scale")
                    val sound=clientPump()!!.pumpSound
                    check(heard && sound.volume>0 && mc.soundManager.isActive(sound)) { "Pump sound was not playing" }
                    record("Running pump loads and plays its original motor/water loop with voltage-dependent pitch")
                    server { p -> GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.XN.int,p.serverLevel(),base.x,base.y,base.z) };step(9,20)
                }
                9->if(settled() && mc.screen is IndustrialPumpGui) {
                    screenshot=true
                    val gui=mc.screen as IndustrialPumpGui
                    val button=IndustrialPumpGui::class.java.getDeclaredField("enabled").apply { isAccessible=true }.get(gui) as mods.eln.gui.GuiButtonEln
                    gui.mouseClicked(button.x+button.width/2.0,button.y+button.height/2.0,0)
                    gui.mouseReleased(button.x+button.width/2.0,button.y+button.height/2.0,0)
                    step(10,40)
                }
                10->if(settled()) {
                    check(clientPump()!!.status=="OFF" && clientPump()!!.pumpSound.volume==0f)
                    record("Live GUI opens; switching off stops pumping and silences the loop")
                    mc.player!!.closeContainer()
                    server { p ->
                        val e=pump(p);e.ports.output.drain(Int.MAX_VALUE,IFluidHandler.FluidAction.EXECUTE)
                        val w=p.serverLevel();for(x in -1..0)for(z in -1..0)w.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                        w.setBlockAndUpdate(base.below(),Blocks.LAVA.defaultBlockState());received.set(0)
                        e.networkUnserialize(packet(80))
                    };step(11,80)
                }
                11->if(settled()) {
                    server { p ->
                        val e=pump(p);check(p.serverLevel().getFluidState(base.below()).isEmpty)
                        check(received.get()+e.ports.output.fluidAmount==1000L) { "Finite lava not conserved: ${received.get()} / ${e.ports.output.fluidAmount}" }
                        record("Finite world lava removes exactly one source bucket without creating fluid")
                        e.networkUnserialize(packet(80));e.ports.output.drain(Int.MAX_VALUE,IFluidHandler.FluidAction.EXECUTE)
                        tank(p,base.above());val modFluid=BuiltInRegistries.FLUID.entrySet().first { it.key.location().namespace=="enderio" && it.value.defaultFluidState().isSource }.value
                        check(cap(p,base.above()).fill(FluidStack(modFluid,5000),IFluidHandler.FluidAction.EXECUTE)==5000)
                        e.networkUnserialize(packet(81));check(e.ports.intake==net.minecraft.core.Direction.UP);received.set(0);e.networkUnserialize(packet(80))
                    };step(12,100)
                }
                12->if(settled()) {
                    server { p ->
                        val e=pump(p);check(cap(p,base.above()).getFluidInTank(0).isEmpty)
                        check(received.get()+e.ports.output.fluidAmount==5000L) { "Modded fluid not conserved: ${received.get()}" }
                        record("Adjacent Ender IO tank supplies 5000 mB of another mod's fluid through the same generic pump")
                    };step(13,10)
                }
                13->if(settled())finish(null)
            }
        } catch(error: Throwable) { finish(error) }
    }
}
