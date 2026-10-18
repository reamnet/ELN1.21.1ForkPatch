package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.worklight.*
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
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opt-in in-game engine, GUI, sensor and save/load checks: -Deln.workLightChecks=true. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object WorkLightChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="work-lights-${System.currentTimeMillis()}"
    private val pos=BlockPos(256,66,256)
    private val motionPos=pos.east(7).above(2)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private var fuelBefore=0.0
    private var kitchenGui=0
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun complete(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return true }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun settled()=complete() && mc.singleplayerServer!!.tickCount>=ready
    private fun fixture(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun element(p: ServerPlayer)=fixture(p).machine(pos) as WorkLightElement
    private fun sensor(p: ServerPlayer)=(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(motionPos.x,motionPos.y,motionPos.z,p.serverLevel())) as SixNode).getElement(Direction.XN) as LampSocketElement
    private fun gui(p: ServerPlayer)=GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z)
    private fun button(name: String) {
        val g=mc.screen as WorkLightGui
        val b=WorkLightGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiButtonEln
        g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun packet(block: DataOutputStream.()->Unit): DataInputStream {
        val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use(block)
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"work-light-$name.png",mc.mainRenderTarget) {}
    private fun record(s: String) { passed+=s;Eln.logger.info("WORK_LIGHT PASS {}",s) }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("work-light-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("WORK_LIGHT FAIL",error) else Eln.logger.info("WORK_LIGHT COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.workLightChecks") || done)return
        try {
            check(++total<14000) { "Timeout at phase $phase" };ticks++
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
                        p.teleportTo(p.serverLevel(),259.5,66.5,260.5,143f,15f)
                        for(x in -6..42)for(z in -5..65)p.serverLevel().setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                        p.serverLevel().dayTime=18000
                        val f=fixture(p);val e=f.placeMachine(Eln.findItemStack("Gasoline Construction Work Light",1),pos) as WorkLightElement
                        e.front=Direction.ZP;e.reconnect()
                        e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",2));e.inventory.setChanged()
                        // No companion fuel mod is required by this client check; seed a measured tank.
                        e.tankLevel=.5;e.needPublish();fuelBefore=e.tankLevel
                        val supply=f.placeSix(Eln.findItemStack("240V Lamp Supply",1),pos.west(4)) as mods.eln.sixnode.lampsupply.LampSupplyElement
                        supply.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!
                        supply.inventory!!.setItem(0,f.spool());supply.inventoryChange(supply.inventory)
                        supply.networkUnserialize(packet { writeByte(1);writeByte(0);writeUTF(e.channel) })
                        f.wire(pos.west(5));f.source(pos.west(6),120.0)
                        p.serverLevel().setBlockAndUpdate(motionPos.west(),Blocks.STONE.defaultBlockState())
                        check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack("Outdoor Motion Floodlight",1),p,p.serverLevel(),motionPos,net.minecraft.core.Direction.EAST,.5f,.5f,.5f))
                        val sensor=sensor(p);sensor.motionMode=1
                        sensor.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",2));sensor.inventory.setItem(1,f.spool());sensor.inventory.setChanged()
                        sensor.poweredByLampSupply=false;sensor.reconnect()
                        // Floor-plane source below with a wall-plane cable uses the real same-face connection rules.
                        for(z in 1..2)p.serverLevel().setBlockAndUpdate(motionPos.north(z).west(),Blocks.STONE.defaultBlockState())
                        check(Eln.sixNodeItem.placeBlockAt(f.spool(),p,p.serverLevel(),motionPos.north(),net.minecraft.core.Direction.EAST,.5f,.5f,.5f))
                        check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack("Electrical Source",1),p,p.serverLevel(),motionPos.north(2),net.minecraft.core.Direction.EAST,.5f,.5f,.5f))
                        val source=(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(motionPos.x,motionPos.y,motionPos.z-2,p.serverLevel())) as SixNode).getElement(Direction.XN) as mods.eln.sixnode.electricalsource.ElectricalSourceElement
                        source.readConfigTool(net.minecraft.nbt.CompoundTag().apply { putDouble("voltage",120.0) },p)
                        KitchenNativeFixtures.setup(p)
                        p.teleportTo(p.serverLevel(),259.5,66.5,260.5,143f,15f)
                    };waitFor(20,100)
                }
                20 -> if(settled()) { server { p -> KitchenNativeFixtures.partialView(p) };waitFor(95,30) }
                23 -> if(settled()) { server { p ->gui(p) };step(2) }
                2 -> if(complete() && mc.screen is WorkLightGui && ticks>30) { capture("gui-off");button("engine");waitFor(3,100) }
                3 -> if(settled()) {
                    server { p ->
                        val e=element(p);check(e.on);check(abs(e.positiveLoad.voltage-120)<.2);check(e.brightness>=13)
                        check(e.powerSource.effectivePower>1);check(e.tankLevel<fuelBefore)
                        record("Starting the gasoline engine through its actual GUI powers both bulbs and burns a measured fuel quantity")
                        KitchenNativeFixtures.requeue(p)
                    };step(4)
                }
                4 -> if(complete()) { capture("gui-running");mc.setScreen(null);mc.options.hideGui=true;step(21) }
                21 -> if(ticks>20) { capture("cart-on");mc.options.hideGui=false;server { p ->KitchenNativeFixtures.outage(p);gui(p) };step(22) }
                22 -> if(complete() && mc.screen is WorkLightGui && ticks>20) { button("engine");waitFor(5,60) }
                5 -> if(settled()) {
                    server { p -> check(!element(p).on);check(element(p).brightness==0);KitchenNativeFixtures.restore(p)
                        record("All five kitchen appliances draw real electrical power and pause their work during a supply outage") }
                    capture("gui-stopped");mc.setScreen(null);step(6)
                }
                6 -> if(complete() && ticks>20) {
                    capture("cart-off")
                    server { p ->
                        // Prove a cable can power the same floodlights with the gas engine stopped.
                        val f=fixture(p);f.wire(pos.north());f.source(pos.north(2),120.0)
                        p.teleportTo(p.serverLevel(),motionPos.x+3.0,motionPos.y-1.0,motionPos.z+.5,90f,0f)
                    };waitFor(7,80)
                }
                7 -> if(settled()) {
                    server { p ->
                        check(!element(p).on);check(element(p).brightness>=13) { "Cart dark: ${element(p).getWaila()}, load=${element(p).positiveLoad.voltage}, source=${element(p).powerSource.voltage}, current=${element(p).powerSource.current}, power=${element(p).powerSource.power}, front=${element(p).front}" }
                        val e=sensor(p);check(e.motionEnergized);check(e.sixNode!!.lightValue>=13)
                        record("Cable power lights the stopped generator cart; movement in front of the wall sensor lights both motion flood heads")
                        // An opaque wall blocks the sensor's view after its 30-second hold expires.
                        for(y in -2..2)for(z in -3..3)p.serverLevel().setBlockAndUpdate(motionPos.offset(1,y,z),Blocks.STONE.defaultBlockState())
                        p.teleportTo(p.serverLevel(),motionPos.x+3.0,motionPos.y-1.0,motionPos.z+1.0,90f,0f)
                    };waitFor(8,660)
                }
                8 -> if(settled()) {
                    server { p ->check(!sensor(p).motionEnergized);check(sensor(p).sixNode!!.lightValue==0)
                        record("An opaque wall blocks motion detection, and the floodlight turns off after its 30-second hold")
                        val cart=element(p);cart.networkUnserialize(packet { writeByte(81) })
                        fixture(p).voltage(pos.north(2),0.0)
                        sensor(p).motionMode=0;p.serverLevel().dayTime=6000
                        for(y in -2..2)for(z in -3..3)p.serverLevel().setBlockAndUpdate(motionPos.offset(1,y,z),Blocks.AIR.defaultBlockState())
                        p.teleportTo(p.serverLevel(),motionPos.x+3.0,motionPos.y-1.0,motionPos.z+.5,90f,0f)
                    };waitFor(9,50)
                }
                9 -> if(settled()) {
                    server { p ->check(!sensor(p).motionEnergized);check(sensor(p).sixNode!!.lightValue==0)
                        check(element(p).wireless);check(element(p).brightness>=13) { "Cart dark: ${element(p).getWaila()}, load=${element(p).positiveLoad.voltage}, source=${element(p).powerSource.voltage}, current=${element(p).powerSource.current}, power=${element(p).powerSource.power}, front=${element(p).front}" }
                        record("The stopped cart also runs from a real wireless lamp supply, and night-only motion stays off in daylight")
                        element(p).networkUnserialize(packet { writeByte(81) });fixture(p).voltage(pos.north(2),120.0)
                        val e=element(p);fuelBefore=e.tankLevel;check(e.bulbCount==2)
                    };waitFor(90,1200)
                }
                90 -> if(settled()) { server { p ->KitchenNativeFixtures.verify(p);KitchenNativeFixtures.gui(p,kitchenGui) };step(91) }
                91 -> if(complete() && mc.screen is mods.eln.transparentnode.kitchen.KitchenGui && ticks>25) {
                    capture("kitchen-$kitchenGui-gui")
                    val g=mc.screen as mods.eln.transparentnode.kitchen.KitchenGui
                    val b=g.javaClass.getDeclaredField("enabled").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
                    waitFor(92,10)
                }
                92 -> if(settled()) { server { p -> check(!KitchenNativeFixtures.machine(p,kitchenGui).enabled) };step(93) }
                93 -> if(complete()) {
                    val g=mc.screen as mods.eln.transparentnode.kitchen.KitchenGui
                    val b=g.javaClass.getDeclaredField("enabled").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
                    mc.setScreen(null);mc.options.hideGui=true;step(94)
                }
                94 -> if(ticks>20) {
                    capture("kitchen-$kitchenGui-model");mc.options.hideGui=false;kitchenGui++
                    if(kitchenGui<5)waitFor(90,20) else {
                        record("Microwave, toaster, cooktop, fridge and coffee maker produce their real outputs; all five actual GUIs toggle power and all slab/table supports survive neighbor updates")
                        step(10)
                    }
                }
                95 -> if(settled() && ticks>30) {
                    capture("half-slab-recessed")
                    val hit=PartialLampPicking.pick(mc) ?: run {
                        val target=KitchenNativeFixtures.clickTarget!!
                        val entity=mc.level!!.getBlockEntity(target) as mods.eln.node.six.SixNodeEntity
                        val render=entity.elementRenderList[Direction.YP.int] as LampSocketRender
                        val inset=LampMounting.inset(mc.level!!,target,Direction.YP,render.descriptor)
                        error("No partial lamp pick: vanilla=${mc.hitResult?.location}, eye=${mc.player!!.eyePosition}, look=${mc.player!!.getViewVector(1f)}, inset=$inset, bounds=${PartialLampPicking.bounds(render,target,inset)}")
                    }
                    check(hit.blockPos==KitchenNativeFixtures.clickTarget) { "Partial fixture picked ${hit.blockPos}, expected ${KitchenNativeFixtures.clickTarget}" }
                    net.minecraft.client.KeyMapping.click(mc.options.keyUse.key);step(96)
                }
                96 -> if(mc.screen is LampSocketGui && ticks>20) {
                    capture("half-slab-recessed-gui");mc.setScreen(null)
                    record("The visible recessed head on a top half slab is directly clickable and opens its real configuration GUI")
                    server { p ->p.teleportTo(p.serverLevel(),259.5,66.5,260.5,143f,15f) };waitFor(23,30)
                }
                10 -> if(complete()) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(11) }
                11 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen work-light world") };step(12) }
                12 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(13,100)
                13 -> if(settled()) {
                    server { p ->val e=element(p);check(!e.on);check(e.bulbCount==2);check(abs(e.tankLevel-fuelBefore)<1e-9);check(e.brightness>=13)
                        KitchenNativeFixtures.verify(p);check(sensor(p).motionMode==0);record("World restart preserves both bulbs, remaining fuel and motion mode, and restores wired light without replacing a cable") }
                    step(14)
                }
                14 -> if(complete())finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
}
