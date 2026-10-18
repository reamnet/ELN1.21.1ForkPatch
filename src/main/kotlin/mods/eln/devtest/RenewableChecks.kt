package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.renewable.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in game checks: real panels, four-port cables, GUI packets and ghost clearance. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object RenewableChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="renewables-${System.currentTimeMillis()}"
    private val hybridPos=BlockPos(256,80,256)
    private val mpptPos=hybridPos.west(12)
    private val windPos=hybridPos.east(14)
    private val waterPos=windPos.south(18)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun settled()=work?.let { if(!it.isDone)false else { it.join();mc.singleplayerServer!!.tickCount>=ready } } ?: true
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun solar(p: ServerPlayer,pos: BlockPos=hybridPos)=(f(p).machine(pos) as SolarElement).unit
    private fun gen(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as LargeRenewableElement
    private fun gui(p: ServerPlayer,pos: BlockPos)=GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z)
    private fun button(name: String) {
        val g=mc.screen as SolarGui
        val b=SolarGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiButtonEln
        g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"renewables-$name.png",mc.mainRenderTarget) {}
    private fun record(s: String) { passed+=s;Eln.logger.info("RENEWABLE_NATIVE PASS {}",s) }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("renewable-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("RENEWABLE_NATIVE FAIL",error) else Eln.logger.info("RENEWABLE_NATIVE COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.renewableChecks") || done)return
        try {
            check(++total<14000) { "Renewable timeout phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val world=p.serverLevel();world.setDayTime(6000);world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,world.server)
                        world.setWeatherParameters(6000,0,false,false);p.abilities.flying=true;p.onUpdateAbilities()
                        p.teleportTo(world,255.0,83.0,263.0,180f,30f)
                        val fixture=f(p)
                        for(x in -20..25)for(z in -8..29) {
                            val at=hybridPos.offset(x,-1,z);world.setChunkForced(at.x shr 4,at.z shr 4,true);world.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState())
                        }
                        for((name,pos) in listOf("MPPT Solar Controller" to mpptPos,"Hybrid Solar Inverter" to hybridPos,
                            "Utility Wind Turbine 3.2kV" to windPos,"Large Watermill 3.2kV" to waterPos)) {
                            p.yRot=180f;p.xRot=0f
                            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,world,pos,net.minecraft.core.Direction.UP))
                            check(fixture.machine(pos).front==Direction.ZP)
                        }
                        for(pos in listOf(mpptPos,hybridPos)) {
                            val panel=fixture.placeMachine(Eln.findItemStack("Small Solar Panel",1),pos.west(3))
                            panel.front=Direction.ZN;panel.reconnect()
                            fixture.wire(pos.west());fixture.wire(pos.west(2));fixture.wire(pos.west(4))
                            for(x in -4..0)fixture.wire(pos.offset(x,0,-2))
                            fixture.wire(pos.west(4).north());fixture.wire(pos.north())
                            fixture.wire(pos.east());fixture.load(pos.east(2),120.0*120/500);fixture.ground(pos.east(3))
                        }
                        fixture.wire(hybridPos.south());fixture.source(hybridPos.south(2),14_000.0)
                        for(pos in listOf(windPos,waterPos)) {
                            fixture.wire(pos.south());fixture.load(pos.south(2),3200.0*3200/1000,Direction.ZP);fixture.ground(pos.south(3))
                        }
                        for(x in listOf(-3,0,3))world.setBlockAndUpdate(waterPos.offset(x,1,-2),Blocks.WATER.defaultBlockState())
                        Eln.wind.windHit=10.0;Eln.wind.windTarget=10.0;Eln.wind.windTargetFiltered.setValue(10f)
                        record("Four items place with distinct ports and multi-block clearance; real panels and a 14 kV grid feed separate hybrid inputs")
                    };waitFor(2,700)
                }
                2 -> if(settled() && (mc.level!!.getBlockEntity(hybridPos) as? TransparentNodeEntity)?.elementRender is RenewableRender) {
                    capture("solar-installation")
                    server { p ->
                        val h=solar(p);check(h.pvWatts>200) { "PV ${h.pvWatts} at ${h.solar.voltage-h.negative.voltage}" }
                        check(h.output.voltage>119.9);check(h.gridWatts in 200.0..400.0) { "Grid ${h.gridWatts}, ${h.status}" }
                        check(solar(p,mpptPos).outputWatts>200)
                        record("Panels reach their maximum-power region; the hybrid blends solar and grid while the standalone MPPT supplies its load")
                        check(gen(p,windPos).source.power>990) { "Wind ${gen(p,windPos).environment}, ${gen(p,windPos).availableWatts}" }
                        check(gen(p,waterPos).source.power>990) { "Water ${gen(p,waterPos).environment}, ${gen(p,waterPos).availableWatts}" }
                        record("Both large generators supply 3.2 kV into cable loads; the watermill detects flowing water")
                        gui(p,hybridPos)
                    };step(3)
                }
                3 -> if(settled() && mc.screen is SolarGui && ticks>20) { capture("hybrid-120-gui");button("v240");button("priority");waitFor(4,60) }
                4 -> if(settled()) {
                    capture("hybrid-240-grid-first-gui");mc.setScreen(null)
                    server { p ->
                        val h=solar(p);check(h.target==240.0);check(h.priority==SolarPriority.GRID_FIRST);check(h.output.voltage>239.5)
                        record("Hybrid GUI buttons program 240 V and grid-first priority on the server")
                        f(p).voltage(hybridPos.south(2),0.0)
                    };waitFor(5,80)
                }
                5 -> if(settled()) {
                    server { p ->
                        val h=solar(p);check(h.gridWatts<1e-6);check(h.outputWatts>200);check(h.status!="PROTECTED")
                        record("Grid outage falls back to finite solar power without reverse feed or a converter fault")
                        gui(p,mpptPos)
                    };step(6)
                }
                6 -> if(settled() && mc.screen is SolarGui && ticks>20) {
                    val g=mc.screen as SolarGui
                    val field=SolarGui::class.java.getDeclaredField("voltage").apply { isAccessible=true }.get(g) as GuiTextFieldEln
                    field.setText("850");button("apply");waitFor(7,40)
                }
                7 -> if(settled()) {
                    capture("mppt-gui");mc.setScreen(null)
                    server { p ->
                        check(solar(p,mpptPos).target==850.0);record("MPPT text field and server packet accept a custom DC voltage")
                        // Level.removeBlock preserves the block's fluid state. Explicit
                        // air is required to drain water rather than remove its container.
                        val w=p.serverLevel();for(x in -6..6)for(z in -5..-2)for(y in 0..2)w.setBlockAndUpdate(waterPos.offset(x,y,z),Blocks.AIR.defaultBlockState())
                        gen(p,waterPos).sampleEnvironment();check(gen(p,waterPos).availableWatts==0.0)
                        p.teleportTo(w,waterPos.x+13.0,waterPos.y+9.0,waterPos.z+15.0,140f,20f)
                    };waitFor(8,60)
                }
                8 -> if(settled()) {
                    capture("watermill-and-wind")
                    server { p ->
                        check(gen(p,waterPos).source.power==0.0)
                        for(x in listOf(-3,0,3))p.serverLevel().setBlockAndUpdate(waterPos.offset(x,1,-2),Blocks.WATER.defaultBlockState())
                    };waitFor(9,80)
                }
                9 -> if(settled()) {
                    server { p ->
                        check(gen(p,waterPos).source.power>990);record("Water removal stops generation; restoring flow restarts the same cabled watermill")
                        p.teleportTo(p.serverLevel(),windPos.x+14.0,windPos.y+10.0,windPos.z+14.0,135f,10f)
                    };waitFor(10,40)
                }
                10 -> if(settled()) { capture("large-wind");finish(null) }
            }
        } catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
