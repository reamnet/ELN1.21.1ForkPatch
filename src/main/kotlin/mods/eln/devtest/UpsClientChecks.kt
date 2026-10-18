package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.misc.Direction
import mods.eln.sim.IProcess
import mods.eln.transparentnode.ups.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.AABB
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.min

/** Opt-in real GUI, real cables, battery inventory, outage and world-save checks. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object UpsClientChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="ups-${System.currentTimeMillis()}"
    private val pos=BlockPos(256,66,256)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private var alarmBeepCount=0
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("eln.upsChecks") && event.sound?.location?.toString()=="eln:beep_error")alarmBeepCount++
    }
    private var outage=false;private var minimumVoltage=Double.POSITIVE_INFINITY;private var before=0.0
    private var observer: IProcess?=null
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun complete(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return true }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun settled()=complete() && mc.singleplayerServer!!.tickCount>=ready
    private fun fixture(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun element(p: ServerPlayer)=fixture(p).machine(pos) as UpsElement
    private fun cells(p: ServerPlayer,from: Int,to: Int) {
        val e=element(p)
        for(i in from until to) {
            val stack=Eln.findItemStack("Portable Battery Pack",1);val type=upsBattery(stack)!!
            type.setEnergy(stack,type.getEnergyMax(stack)*.5);e.inventory.setItem(i,stack)
        }
        e.inventory.setChanged();e.needPublish()
    }
    private fun gui(p: ServerPlayer)=GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z)
    private fun button(name: String) {
        val g=mc.screen as UpsGui
        val b=UpsGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiButtonEln
        g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun display()=UpsGui::class.java.getDeclaredField("display").apply { isAccessible=true }.get(mc.screen) as UpsDisplay
    private fun voltage(v: Double) {
        val g=mc.screen as UpsGui
        val field=UpsGui::class.java.getDeclaredField("voltage").apply { isAccessible=true }.get(g) as GuiTextFieldEln
        field.setText(v.toString());button("apply")
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"ups-$name.png",mc.mainRenderTarget) {}
    private fun record(s: String) { passed+=s;Eln.logger.info("UPS_NATIVE PASS {}",s) }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("ups-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("UPS_NATIVE FAIL",error) else Eln.logger.info("UPS_NATIVE COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.upsChecks") || done)return
        try {
            check(++total<12000) { "UPS check timeout at phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        p.abilities.flying=true;p.onUpdateAbilities()
                        p.teleportTo(p.serverLevel(),259.5,67.0,260.5,143f,22f)
                        for(x in -6..6)for(z in -5..6)p.serverLevel().setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                        val f=fixture(p);val e=f.placeMachine(Eln.findItemStack("Programmable UPS",1),pos) as UpsElement
                        e.front=Direction.ZP;e.reconnect()
                        f.wire(pos.west());f.wire(pos.east());f.source(pos.west(2),3200.0);f.load(pos.east(2),120.0*120/500);f.ground(pos.east(3))
                        cells(p,0,24)
                        observer=IProcess { if(outage)minimumVoltage=min(minimumVoltage,e.unit.secondary.voltage) }.also { Eln.simulator.addElectricalProcess(it) }
                    };waitFor(2,80)
                }
                2 -> if(settled()) {
                    capture("enclosure")
                    GLFW.glfwSetCursorPos(mc.window.window,10.0,10.0)
                    server { p ->val e=element(p);check(e.bank.activeCount==24);check(abs(e.unit.secondary.voltage-120)<.1);check(e.unit.inputWatts>e.unit.outputWatts);check(e.unit.outputWatts>490) { "Test load is not drawing power" };gui(p) };step(3)
                }
                3 -> if(complete() && mc.screen is UpsGui && ticks>30) {
                    check(display().active==24);capture("24-batteries");button("rack");waitFor(4,15)
                }
                4 -> if(settled()) {
                    check((mc.screen!!.menuOrNull() as? UpsContainer)?.page==1)
                    server { p -> check((p.containerMenu as UpsContainer).page==1);cells(p,24,48) };waitFor(5,30)
                }
                5 -> if(settled()) {
                    check(display().active==48);capture("48-batteries")
                    server { p -> fixture(p).load(pos.east(2),850.0*850/500) };step(6)
                }
                6 -> if(complete()) { voltage(850.0);waitFor(7,50) }
                7 -> if(settled()) {
                    check(display().target==850.0)
                    server { p ->val e=element(p);check(abs(e.unit.secondary.voltage-850)<.1);check(e.unit.outputWatts>495);before=e.bank.energy;alarmBeepCount=0;outage=true;minimumVoltage=Double.POSITIVE_INFINITY;fixture(p).voltage(pos.west(2),0.0) }
                    waitFor(8,60)
                }
                8 -> if(settled()) {
                    capture("battery-backup")
                    server { p ->
                        val e=element(p);check(alarmBeepCount>0) { "No client alarm sound received during grid loss" };check(minimumVoltage>849.0);check(e.bank.energy<before-1400)
                        check(e.unit.status=="BACKUP");check(e.unit.inputWatts<.01);outage=false
                        record("A real 24-to-48 battery upgrade, paged GUI and 850 V setting work; grid loss plays a client alarm beep and causes no output interruption or input backfeed")
                        fixture(p).voltage(pos.west(2),3200.0)
                    };waitFor(25,50)
                }
                25 -> if(settled()) {
                    server { p ->alarmBeepCount=0;fixture(p).load(pos.east(2),850.0*850/15000.0) };waitFor(26,60)
                }
                26 -> if(settled()) {
                    server { p ->val e=element(p);check(e.unit.status in setOf("LIMITED","PROTECTED","OVERTEMPERATURE")) { "No overload status: ${e.unit.status}" }
                        check(alarmBeepCount>=2) { "Overload alarm did not repeat at the client" }
                        record("A real 15 kW demand triggers the faster repeating overload alarm on the client")
                        fixture(p).load(pos.east(2),850.0*850/500)
                    };waitFor(9,60)
                }
                9 -> if(settled()) {
                    server { p ->
                        val e=element(p);check(e.unit.inputWatts>e.unit.outputWatts)
                        val energy=e.bank.energy;val removed=e.inventory.removeItem(47,1)
                        check(e.bank.activeCount==24)
                        val heldEnergy=upsBattery(removed)!!.getEnergy(removed)
                        val stillStored=(0 until 48).sumOf { val s=e.inventory.getItem(it);upsBattery(s)?.getEnergy(s) ?: 0.0 }
                        check(abs(energy-stillStored-heldEnergy)<.01)
                        e.inventory.setItem(47,removed);e.inventory.setChanged();check(e.bank.activeCount==48)
                        record("Mains restoration recharges the cells; removing and replacing a live battery preserves every joule and updates the active rack count")
                    };step(16)
                }
                16 -> if(complete()) {
                    val menu=(mc.screen as UpsGui).menu
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,47,0,ClickType.PICKUP,mc.player!!)
                    waitFor(17,10)
                }
                17 -> if(settled()) {
                    server { p ->check(element(p).bank.activeCount==24);check(upsBattery(p.containerMenu.carried)!=null) };step(18)
                }
                18 -> if(complete()) {
                    val menu=(mc.screen as UpsGui).menu
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,47,0,ClickType.PICKUP,mc.player!!)
                    waitFor(19,10)
                }
                19 -> if(settled()) {
                    server { p ->check(element(p).bank.activeCount==48);check(p.containerMenu.carried.isEmpty)
                        record("Picking up and replacing a battery through the actual GUI works while the UPS is running") };step(10)
                }
                10 -> if(complete() && ticks>20) {
                    capture("recharging")
                    server { _ ->observer?.let { Eln.simulator.removeElectricalProcess(it) };observer=null };step(11)
                }
                11 -> if(complete()) { mc.setScreen(null);mc.level!!.disconnect();mc.disconnect(TitleScreen());step(12) }
                12 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen UPS world") };step(13) }
                13 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(14,80)
                14 -> if(settled()) {
                    server { p ->
                        val e=element(p);check(e.unit.outputVoltage==850.0);check(e.bank.activeCount==48);check(abs(e.unit.secondary.voltage-850)<.1)
                        record("World restart preserves all 48 charged cells, programmed voltage and working input/output connections")
                        val energy=e.bank.energy
                        p.serverLevel().destroyBlock(pos,true,p)
                        val drops=p.serverLevel().getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(4.0)).map { it.item }.filter { upsBattery(it)!=null }
                        check(drops.sumOf { it.count }==48) { "Dropped ${drops.sumOf { it.count }} batteries" }
                        check(abs(drops.sumOf { upsBattery(it)!!.getEnergy(it) }-energy)<.01)
                        record("Breaking the UPS drops all 48 batteries with their exact saved charge")
                    };step(15)
                }
                15 -> if(complete())finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
    private fun net.minecraft.client.gui.screens.Screen.menuOrNull()=(this as? UpsGui)?.menu
}
