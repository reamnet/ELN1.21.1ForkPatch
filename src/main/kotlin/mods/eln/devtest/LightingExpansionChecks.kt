package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.*
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceElement
import mods.eln.transparentnode.standby.*
import mods.eln.transparentnode.ups.*
import mods.eln.transparentnode.utilitylight.UtilityStreetLightElement
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.*

/** Isolated opt-in gallery, production wiring, controls, batteries, exhaust and audio. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object LightingExpansionChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="lighting-expansion-${System.currentTimeMillis()}"
    private val base=BlockPos(256,70,256)
    private val supply=base.west(6)
    private val generator=base.west(6).south(10)
    private val ups=generator.south(7)
    private val ats=base.west(2).south(14)
    private val poles=listOf(base.west(6).south(28),base.west(6).south(38))
    private data class Fixture(val style: DecorativeLampStyle,val pos: BlockPos,val side: Direction)
    private val fixtures=DecorativeLampStyle.entries.drop(30).mapIndexed { i,s ->
        val side=if(s.ceiling)Direction.YP else if(s.wallLamp)Direction.XN else Direction.YN
        Fixture(s,base.offset(i%6*9,if(s.ceiling)8 else if(s.wallLamp)3 else 0,i/6*10),side)
    }
    private val controls=(0..3).map { base.west(5).south(2+it*2).above(2) }
    private val controlNames=listOf("Wireless Paddle Switch","Wireless Toggle Switch","Wireless Paddle Button","Wireless Toggle Button")
    private const val channel="Expansion gallery"
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var shot=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private var sounds=0;private var closeSounds=0;private var openSounds=0;private var smokeBefore=0;private var energyBefore=0.0
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(!java.lang.Boolean.getBoolean("eln.lightingExpansionChecks"))return
        when(event.sound?.location?.toString()) {
            "eln:fuelgenerator" -> sounds++
            "eln:circuit_breaker_close" -> closeSounds++
            "eln:circuit_breaker_open" -> openSounds++
        }
    }
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun six(p: ServerPlayer,pos: BlockPos,side: Direction)=(f(p).node(pos) as SixNode).getElement(side)!!
    private fun lamp(p: ServerPlayer,e: Fixture)=six(p,e.pos,e.side) as LampSocketElement
    private fun control(p: ServerPlayer,i: Int)=six(p,controls[i],Direction.XN) as WirelessSignalSourceElement
    private fun gen(p: ServerPlayer)=(f(p).machine(generator) as StandbyGeneratorElement).unit
    private fun battery(p: ServerPlayer)=f(p).machine(ups) as UpsElement
    private fun packet(body: DataOutputStream.()->Unit)=DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).use(body) }.toByteArray()))
    private fun place(p: ServerPlayer,name: String,pos: BlockPos,side: Direction): SixNodeElement {
        val support=when(side) { Direction.YN->pos.below();Direction.YP->pos.above();else->pos.west() }
        p.serverLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,p.serverLevel(),pos,side.toFacing().opposite,.5f,.5f,.5f)) { "Placement $name at $pos/$side" }
        return six(p,pos,side)
    }
    private fun record(s: String) { passed+=s;Eln.logger.info("LIGHTING_EXPANSION PASS {}",s) }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"lighting-expansion-$name.png",mc.mainRenderTarget) {}
    private fun view(p: ServerPlayer,e: Fixture) {
        val d=if(e.style.luxury)6.5 else if(e.style.street)7.0 else if(e.style==DecorativeLampStyle.FLOOR_DRUM)3.5 else 2.2
        val h=if(e.style.luxury)-e.style.depth/2 else if(e.style.ceiling)-.5 else if(e.style.street)3.0 else .4
        val eyeY=e.pos.y+h
        val x=e.pos.x+.5+d;val z=e.pos.z+.5+d
        val y=eyeY+d*.30-p.eyeHeight
        p.teleportTo(p.serverLevel(),x,y,z,135f,Math.toDegrees(atan2(d*.30,hypot(d,d))).toFloat())
    }
    private fun genRender()=(mc.level!!.getBlockEntity(generator) as? TransparentNodeEntity)?.elementRender as? BackupRender
    private fun finish(error: Throwable?) {
        done=true;mc.options.hideGui=false
        Files.writeString(mc.gameDirectory.toPath().resolve("lighting-expansion-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("LIGHTING_EXPANSION FAIL phase $phase",error) else Eln.logger.info("LIGHTING_EXPANSION COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.lightingExpansionChecks") || done)return
        try {
            check(++total<18000) { "Timeout phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server);w.setWeatherParameters(6000,0,false,false)
                        p.abilities.flying=true;p.onUpdateAbilities();view(p,fixtures.first())
                        for(x in -10..50)for(z in -3..46) { val at=base.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        LampChannelMemory.remember(p.persistentData,channel)
                        val supplyElement=place(p,"240V Lamp Supply",supply,Direction.YN) as LampSupplyElement
                        supplyElement.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!
                        supplyElement.inventory!!.setItem(0,f(p).spool());supplyElement.inventoryChange(supplyElement.inventory)
                        supplyElement.networkUnserialize(packet { writeByte(1);writeByte(0);writeUTF(channel) })
                        supplyElement.networkUnserialize(packet { writeByte(LampSupplyElement.setTransmissionRange.toInt());writeInt(128) })
                        f(p).wire(supply.west());f(p).source(supply.west(2),120.0)
                        for(e in fixtures) {
                            val lamp=place(p,e.style.itemName,e.pos,e.side) as LampSocketElement
                            lamp.motionMode=2
                            p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("120V LED Light Bulb",e.style.bulbs))
                            check(lamp.onBlockActivated(p,e.side,.5f,.5f,.5f));check(lamp.lampSupplyChannel==channel)
                            p.setItemInHand(InteractionHand.MAIN_HAND,f(p).spool(2.0));check(lamp.onBlockActivated(p,e.side,.5f,.5f,.5f))
                        }
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        for((i,pos) in controls.withIndex()) {
                            val e=place(p,controlNames[i],pos,Direction.XN) as WirelessSignalSourceElement
                            e.networkUnserialize(packet { writeByte(1);writeUTF("Gallery control") })
                        }
                        val fixture=f(p)
                        fixture.placeMachine(Eln.findItemStack("Standby Gasoline Generator",1),generator).apply { front=Direction.ZP;reconnect() }
                        gen(p).fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0),20_000),FluidAction.EXECUTE);gen(p).mode=StandbyMode.MANUAL
                        fixture.wire(generator.east());fixture.load(generator.east(2),240.0*240/1000);fixture.ground(generator.east(3))
                        fixture.placeMachine(Eln.findItemStack("Programmable UPS",1),ups).apply { front=Direction.ZP;reconnect() }
                        repeat(24) { i -> val stack=Eln.findItemStack(listOf("UPS AGM Battery Cell","UPS LiFePO4 Battery Cell","UPS Industrial Battery Module")[i%3],1)
                            val cell=upsBattery(stack)!!;cell.setEnergy(stack,cell.getEnergyMax(stack)*.5);battery(p).inventory.setItem(i,stack) }
                        fixture.wire(ups.west());fixture.source(ups.west(2),3200.0);fixture.wire(ups.east());fixture.load(ups.east(2),120.0*120/500);fixture.ground(ups.east(3))
                        for((i,pos) in poles.withIndex()) {
                            val name=if(i==0)"Utility Pole Cobrahead Street Light" else "Utility Pole Twin LED Street Light"
                            val pole=fixture.placeMachine(Eln.findItemStack(name,1),pos) as UtilityStreetLightElement
                            pole.front=Direction.ZP;pole.reconnect()
                            p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("Mains Step-down Unit",1));check(pole.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f))
                            // Test fixture injects the upstream overhead boundary at its real grid load.
                            val source=mods.eln.sim.mna.component.VoltageSource("overhead",pole.unit.primary,null).apply { voltage=14_000.0 }
                            Eln.simulator.mna.addComponent(source)
                        }
                        fixture.placeMachine(Eln.findItemStack("Automatic Transfer Switch",1),ats).apply { front=Direction.ZP;reconnect() }
                        fixture.wire(ats.west());fixture.source(ats.west(2),240.0)
                        fixture.wire(ats.north());fixture.source(ats.north(2),240.0)
                        fixture.wire(ats.east());fixture.load(ats.east(2),240.0*240/1000);fixture.ground(ats.east(3))
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                    };waitFor(2,180)
                }
                2 -> if(settled()) {
                    server { p ->
                        for(e in fixtures) { val lamp=lamp(p,e);check(lamp.installedBulbs==e.style.bulbs);check(lamp.activeLampSupplyConnection) { "Wireless ${e.style}" };check(lamp.sixNode!!.lightValue>=13) { "Dark ${e.style}: level ${lamp.sixNode!!.lightValue}, voltage ${lamp.electricalLoad.voltage}" } }
                        record("All 30 new fixtures place with physical clearance, hand-inserted bulbs and cables, and run wirelessly on a real Lamp Supply")
                        check(battery(p).unit.outputWatts>499);check(battery(p).bank.activeCount==24)
                        check(gen(p).running);check(gen(p).watts>990)
                        for(pos in poles) { val pole=f(p).machine(pos) as UtilityStreetLightElement;check(pole.installed);check(pole.lightLevel>=14) { "Pole ${pole.lightLevel}, ${pole.unit.secondary.voltage}" } }
                        record("Both new pole fixtures accept the hand-installed step-down unit and light from their 14 kV grid input")
                        val s=six(p,supply,Direction.YN) as LampSupplyElement
                        s.networkUnserialize(packet { writeByte(LampSupplyElement.setWirelessName.toInt());writeByte(0);writeUTF("Gallery control") })
                        s.networkUnserialize(packet { writeByte(LampSupplyElement.setSelectedAggregator.toInt());writeByte(0);writeByte(0) })
                        p.teleportTo(p.serverLevel(),generator.x+4.0,generator.y+.5,generator.z+4.0,135f,10f)
                    };waitFor(3,80)
                }
                3 -> if(settled()) {
                    val r=genRender()!!;check(r.emittedSmoke>0);check(sounds>0);capture("generator-running");smokeBefore=r.emittedSmoke
                    record("The loaded standby engine plays its real sound event and emits smoke at the modeled exhaust")
                    server { p ->
                        fixtures.forEach { check(lamp(p,it).sixNode!!.lightValue==0) }
                        control(p,0).onBlockActivated(p,Direction.XN,.5f,.5f,.5f)
                        f(p).voltage(ats.west(2),0.0)
                        energyBefore=battery(p).bank.energy
                        f(p).voltage(ups.west(2),0.0);gen(p).mode=StandbyMode.OFF;gen(p).stop()
                    };waitFor(4,100)
                }
                4 -> if(settled()) {
                    check(genRender()!!.emittedSmoke<=smokeBefore+5);capture("generator-stopped")
                    server { p ->
                        fixtures.forEach { check(lamp(p,it).sixNode!!.lightValue>=13) }
                        check(battery(p).unit.status=="BACKUP") { "UPS ${battery(p).unit.status}" };check(battery(p).unit.outputWatts>499) { "UPS output ${battery(p).unit.outputWatts}" };check(battery(p).bank.energy<energyBefore) { "UPS did not discharge: $energyBefore -> ${battery(p).bank.energy}" }
                        record("Wireless paddle switch controls the whole gallery; mixed larger cells carry the UPS through a real grid outage")
                        check((f(p).machine(ats) as AtsElement).unit.control.selected==TransferFeed.GENERATOR)
                        check(openSounds>=1 && closeSounds>=1) { "ATS outage clicks: $openSounds/$closeSounds" }
                        f(p).voltage(ats.west(2),240.0)
                        gen(p).mode=StandbyMode.MANUAL
                        // Put one fixture from each mounting family on physical cables.
                        for(e in fixtures.filter { it.style in listOf(DecorativeLampStyle.WINDOW_CANDLE,DecorativeLampStyle.FLOOR_DRUM,DecorativeLampStyle.STRIP_RECESSED,DecorativeLampStyle.LUXURY_RINGS,DecorativeLampStyle.HEAVY_BULKHEAD,DecorativeLampStyle.RIG_LINEAR,DecorativeLampStyle.STREET_LED,DecorativeLampStyle.HALLOWEEN_JACK) }) {
                            lamp(p,e).poweredByLampSupply=false;lamp(p,e).reconnect()
                            val between=if(e.side==Direction.XN)e.pos.north() else e.pos.west()
                            place(p,"Electrical Source",if(e.side==Direction.XN)e.pos.north(2) else e.pos.west(2),e.side).let { it as mods.eln.sixnode.electricalsource.ElectricalSourceElement;it.readConfigTool(net.minecraft.nbt.CompoundTag().apply { putDouble("voltage",120.0) },p) }
                            val support=when(e.side) { Direction.YP->between.above();Direction.XN->between.west();else->between.below() }
                            p.serverLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState())
                            check(Eln.sixNodeItem.placeBlockAt(f(p).spool(),p,p.serverLevel(),between,e.side.toFacing().opposite,.5f,.5f,.5f))
                        }
                    };waitFor(5,160)
                }
                5 -> if(settled()) {
                    server { p ->
                        fixtures.forEach { check(lamp(p,it).sixNode!!.lightValue>=13) { "Wired ${it.style}" } }
                        check((f(p).machine(ats) as AtsElement).unit.control.selected==TransferFeed.MAINS)
                        check(openSounds>=2 && closeSounds>=2) { "ATS return clicks: $openSounds/$closeSounds" }
                        record("ATS contactors play real open/close sound events on backup transfer and return to mains")
                        record("Wired window, standing, strip, grand chandelier, heavy, offshore, street and seasonal fixtures draw power from same-plane cables")
                        view(p,fixtures[shot])
                    };waitFor(6,15)
                }
                6 -> if(settled()) { mc.options.hideGui=true;capture(fixtures[shot].style.model);shot++;if(shot<fixtures.size) { server { p -> view(p,fixtures[shot]) };waitFor(6,15) } else { mc.options.hideGui=false;record("All 30 fixture models render in the native client");server { p ->
                            p.teleportTo(p.serverLevel(),ups.x+3.0,ups.y+1.0,ups.z+4.0,140f,10f)
                        };waitFor(7,40) } }
                7 -> if(settled() && (mc.level!!.getBlockEntity(ups) as? TransparentNodeEntity)?.elementRender is UpsRender) {
                    server { p -> GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),ups.x,ups.y,ups.z) };step(8)
                }
                8 -> if(settled() && mc.screen is UpsGui && ticks>20) {
                    capture("larger-cell-ups-gui");mc.setScreen(null)
                    server { p -> val pos=poles[0];p.teleportTo(p.serverLevel(),pos.x+5.0,pos.y+2.0,pos.z+6.0,140f,5f) };waitFor(9,40)
                }
                9 -> if(settled()) {
                    capture("cobrahead-pole")
                    server { p -> val pos=poles[1];p.teleportTo(p.serverLevel(),pos.x+5.0,pos.y+2.0,pos.z+6.0,140f,5f) };waitFor(10,40)
                }
                10 -> if(settled()) {
                    capture("twin-led-pole");record("Larger-cell rack GUI and both new pole models render in the native client")
                    mc.level!!.disconnect();mc.disconnect(TitleScreen());step(11)
                }
                11 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen gallery") };step(12) }
                12 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(13,160)
                13 -> if(settled()) {
                    server { p ->
                        fixtures.forEach { check(lamp(p,it).installedBulbs==it.style.bulbs);check(lamp(p,it).sixNode!!.lightValue>=13) { "Saved ${it.style}" } }
                        check(battery(p).bank.activeCount==24);check(battery(p).unit.outputWatts>499)
                        record("World restart preserves fixture identities, channels, wired modes and the larger-cell backup rack")
                    };step(14)
                }
                14 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
