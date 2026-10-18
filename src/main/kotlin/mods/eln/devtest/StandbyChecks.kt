package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.standby.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opt-in isolated world: actual three-port wiring, GUI packets, containers and fluid capability. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object StandbyChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="standby-${System.currentTimeMillis()}"
    private val atsPos=BlockPos(256,66,256)
    private val genPos=atsPos.north(3).west(3)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private var fuelBefore=0.0
    private var observer: mods.eln.sim.IProcess?=null
    private var verifiedBreaks=0
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun complete(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return true }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun settled()=complete() && mc.singleplayerServer!!.tickCount>=ready
    private fun fixture(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun ats(p: ServerPlayer)=(fixture(p).machine(atsPos) as AtsElement).unit
    private fun gen(p: ServerPlayer)=(fixture(p).machine(genPos) as StandbyGeneratorElement).unit
    private fun gui(p: ServerPlayer,pos: BlockPos)=GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z)
    private fun button(name: String) {
        val g=mc.screen as BackupGui
        val b=BackupGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiButtonEln
        g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun field(name: String,value: String) {
        val g=mc.screen as BackupGui
        val f=BackupGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiTextFieldEln
        f.setText(value)
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"standby-$name.png",mc.mainRenderTarget) {}
    private fun record(s: String) { passed+=s;Eln.logger.info("STANDBY_NATIVE PASS {}",s) }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("standby-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("STANDBY_NATIVE FAIL",error) else Eln.logger.info("STANDBY_NATIVE COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.standbyChecks") || done)return
        try {
            check(++total<12000) { "Standby timeout at phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();p.abilities.flying=true;p.onUpdateAbilities();p.teleportTo(w,259.5,68.0,260.5,140f,25f)
                        for(x in -6..6)for(z in -6..5) { val at=atsPos.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        val f=fixture(p)
                        for((name,pos) in listOf("Automatic Transfer Switch" to atsPos,"Standby Gasoline Generator" to genPos)) {
                            f.placeMachine(Eln.findItemStack(name,1),pos).apply { front=Direction.ZP;reconnect() }
                        }
                        f.wire(atsPos.west());f.source(atsPos.west(2),240.0)
                        f.wire(atsPos.east());f.load(atsPos.east(2),240.0*240/1000);f.ground(atsPos.east(3))
                        for(pos in listOf(atsPos.north(),atsPos.north(2),atsPos.north(3),atsPos.north(3).west(),atsPos.north(3).west(2)))f.wire(pos)
                        val e=f.machine(genPos) as StandbyGeneratorElement
                        // A real survival container transaction returns the empty bucket.
                        p.setGameMode(GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(StandbyTestFluids.bucket(0)))
                        check(e.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f));check(p.mainHandItem.`is`(Items.BUCKET));check(e.unit.fuel.tank.fluidAmount==1000)
                        val pipe=w.getCapability(Capabilities.FluidHandler.BLOCK,genPos,net.minecraft.core.Direction.UP)!!
                        check(pipe.drain(1000,FluidAction.EXECUTE).amount==1000)
                        check(pipe.fill(FluidStack(StandbyTestFluids.fluid(1),1000),FluidAction.SIMULATE)==1000);check(e.unit.fuel.tank.isEmpty)
                        check(pipe.fill(FluidStack(StandbyTestFluids.fluid(1),1000),FluidAction.EXECUTE)==1000)
                        check(pipe.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0)
                        pipe.drain(20_000,FluidAction.EXECUTE)
                        check(pipe.isFluidValid(0,FluidStack(StandbyTestFluids.fluid(2),1000)))
                        check(pipe.fill(FluidStack(StandbyTestFluids.fluid(2),20_000),FluidAction.EXECUTE)==20_000)
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setGameMode(GameType.CREATIVE)
                        var clock=0.0;var opened=0.0;var previous=TransferFeed.OPEN;var supplied=false
                        val unit=ats(p)
                        observer=mods.eln.sim.IProcess { seconds ->
                            clock+=seconds
                            check(!(unit.mainContact.closed && unit.backupContact.closed)) { "Both transfer contacts closed" }
                            val selected=unit.control.selected
                            if(selected==TransferFeed.OPEN && previous!=TransferFeed.OPEN)opened=clock
                            if(selected!=TransferFeed.OPEN && previous==TransferFeed.OPEN) {
                                if(supplied) { check(clock-opened>=.25-1e-8) { "Transfer break was only ${clock-opened} seconds" };verifiedBreaks++ }
                                supplied=true
                            }
                            previous=selected
                        }.also { Eln.simulator.addElectricalProcess(it) }
                        record("A survival gasoline bucket returns its empty bucket; the real pipe capability accepts a second namespace and tagged unusual-name gasoline, simulates fills and rejects water")
                    };waitFor(2,80)
                }
                2 -> if(settled() && (mc.level!!.getBlockEntity(atsPos) as? TransparentNodeEntity)?.elementRender is BackupRender) {
                    capture("installation")
                    server { p ->check(ats(p).control.selected==TransferFeed.MAINS);check(ats(p).watts>990);check(!gen(p).running);gui(p,atsPos) };step(3)
                }
                3 -> if(complete() && mc.screen is BackupGui && ticks>20) {
                    field("voltage","240");button("apply");field("channel","Factory backup");button("save");waitFor(4,20)
                }
                4 -> if(settled()) {
                    capture("ats-gui");mc.setScreen(null)
                    server { p ->check(ats(p).channel=="Factory backup");check(ats(p).mainContact.closed);fuelBefore=gen(p).fuel.availableMb;fixture(p).voltage(atsPos.west(2),0.0) };waitFor(5,160)
                }
                5 -> if(settled()) {
                    server { p ->check(ats(p).control.request);check(!gen(p).running);check(gen(p).fuel.availableMb==fuelBefore)
                        record("The ATS GUI saves its channel and voltage; a real mains failure requests backup but an unlinked generator stays stopped")
                        gui(p,genPos)
                    };step(6)
                }
                6 -> if(complete() && mc.screen is BackupGui && ticks>20) {
                    field("voltage","240");button("apply");field("channel","Factory backup");button("save");waitFor(7,160)
                }
                7 -> if(settled()) {
                    capture("generator-running-gui");mc.setScreen(null)
                    server { p ->check(gen(p).running);check(gen(p).watts>990);check(gen(p).fuel.availableMb<fuelBefore)
                        check(ats(p).control.selected==TransferFeed.GENERATOR);check(ats(p).output.voltage>235);check(ats(p).mainContact.current==0.0)
                        check(abs(ats(p).mains.voltage)<.01)
                        record("Matching the generator channel through its GUI starts the engine wirelessly and supplies the real 1 kW load, burning fuel with zero mains-contact backfeed")
                        fixture(p).voltage(atsPos.west(2),240.0)
                    };waitFor(8,45)
                }
                8 -> if(settled()) {
                    server { p ->check(ats(p).control.selected==TransferFeed.GENERATOR) { "Returned before stable-mains delay" } };waitFor(9,220)
                }
                9 -> if(settled()) {
                    capture("mains-restored")
                    server { p ->check(ats(p).control.selected==TransferFeed.MAINS);check(ats(p).backupContact.current==0.0);check(!gen(p).running)
                        record("Stable mains automatically takes back the load after five seconds; the generator cools down and stops")
                        fixture(p).load(atsPos.east(2),240.0*240/20_000);fixture(p).voltage(atsPos.west(2),0.0)
                    };waitFor(10,190)
                }
                10 -> if(settled()) {
                    server { p ->check(gen(p).fault=="OVERLOAD") { gen(p).status };check(!gen(p).source.enabled);fuelBefore=gen(p).fuel.availableMb };waitFor(11,100)
                }
                11 -> if(settled()) {
                    server { p ->check(gen(p).fuel.availableMb==fuelBefore);check(!ats(p).mainContact.closed && !ats(p).backupContact.closed)
                        record("A real 20 kW overload stops and latches the generator without repeated restarts or joining the two feeds")
                        fixture(p).load(atsPos.east(2),240.0*240/1000);gui(p,genPos)
                    };step(12)
                }
                12 -> if(complete() && mc.screen is BackupGui && ticks>20) { button("reset");waitFor(13,160) }
                13 -> if(settled()) {
                    capture("reset-and-recovered");mc.setScreen(null)
                    server { p ->check(gen(p).running);check(ats(p).watts>990);fuelBefore=gen(p).fuel.availableMb
                        record("Resetting protection through the actual generator GUI restores the corrected load")
                        check(verifiedBreaks>=2);record("Every sampled source change has at least 0.25 seconds with both contacts open, and no physical solve ever closes both contacts")
                    };step(14)
                }
                14 -> if(complete() && ticks>15) {
                    server { p ->
                        ats(p).setVoltage(14_000.0);gen(p).setVoltage(14_000.0)
                        fixture(p).load(atsPos.east(2),14_000.0*14_000/1000)
                    };waitFor(19,180)
                }
                19 -> if(settled()) {
                    server { p ->check(gen(p).running);check(abs(ats(p).output.voltage-14_000)<10);check(ats(p).watts>990)
                        check(ats(p).mainContact.current==0.0);fixture(p).voltage(atsPos.west(2),14_000.0)
                    };waitFor(20,260)
                }
                20 -> if(settled()) {
                    server { p ->check(ats(p).control.selected==TransferFeed.MAINS);check(!gen(p).running)
                        check(abs(ats(p).output.voltage-14_000)<10);fixture(p).voltage(atsPos.west(2),0.0)
                    };waitFor(21,180)
                }
                21 -> if(settled()) {
                    capture("14kv-backup")
                    server { p ->check(gen(p).running);check(ats(p).control.selected==TransferFeed.GENERATOR)
                        check(abs(ats(p).output.voltage-14_000)<10);check(ats(p).watts>990);fuelBefore=gen(p).fuel.availableMb
                        observer?.let(Eln.simulator::removeElectricalProcess);observer=null
                        record("The same real circuit completes outage, stable-mains return and a second automatic start at 14000 V without a step-down converter")
                    };step(22)
                }
                22 -> if(complete() && ticks>15) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(15) }
                15 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen standby world") };step(16) }
                16 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(17,180)
                17 -> if(settled()) {
                    server { p ->check(gen(p).channel=="Factory backup" && ats(p).channel=="Factory backup");check(gen(p).mode==StandbyMode.AUTO)
                        check(gen(p).nominal==14_000.0 && ats(p).nominal==14_000.0)
                        check(gen(p).fuel.availableMb<fuelBefore && gen(p).fuel.availableMb>fuelBefore-400)
                        check(ats(p).control.selected==TransferFeed.GENERATOR);check(ats(p).watts>990);check(gen(p).running)
                        record("World restart preserves both linked channels, gasoline and rated voltage, then revalidates the outage and restores automatic backup without replacing cables")
                    };step(18)
                }
                18 -> if(complete())finish(null)
            }
        } catch(error: Throwable) { finish(error) }
    }
}
