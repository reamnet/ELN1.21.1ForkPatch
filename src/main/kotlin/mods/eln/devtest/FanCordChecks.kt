package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.fan.*
import mods.eln.transparentnode.kitchen.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opt-in production clicks, circuits, damage, inertia and portable inventory: eln.fanCordChecks. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object FanCordChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="fan-cords-${System.currentTimeMillis()}"
    private val base=BlockPos(256,80,256)
    private val fans=FanType.entries.mapIndexed { i,t -> t to base.offset(if(i==0)0 else 4+i*5,0,0) }
    private val ceilings=DecorativeLampStyle.entries.filter { it.fan }.mapIndexed { i,t -> t to base.offset(i*7,6,10) }
    private val socket=base.offset(0,1,18);private val portable=base.offset(5,0,18);private val moved=base.offset(6,0,18)
    private val distant=base.offset(10,0,18);private val oven=base.offset(5,0,12)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private var golem: IronGolem?=null;private var health=0f;private var heldToken: java.util.UUID?=null
    private val heard=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var startingWork=0.0
    private var overloadLoad: mods.eln.sim.mna.component.Resistor?=null
    private var savedOutletId: java.util.UUID?=null
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun fan(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as FanElement
    private fun outlet(p: ServerPlayer)=f(p).machine(socket) as OutletElement
    private fun ceiling(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as SixNode).getElement(Direction.YP) as LampSocketElement
    private fun packet(id: Int)=DataInputStream(ByteArrayInputStream(byteArrayOf(id.toByte())))
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ body(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun record(s: String) { passed+=s;Eln.logger.info("FAN_CORD PASS {}",s) }
    private fun wall(p: ServerPlayer,name: String,pos: BlockPos,side: Direction): SixNodeElement {
        val w=p.serverLevel();w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(if(name=="Copper Cable")f(p).spool() else Eln.findItemStack(name,1),p,w,pos,side.toFacing().opposite,.5f,.5f,.5f))
        return (f(p).node(pos) as SixNode).getElement(side)!!
    }
    private fun hold(p: ServerPlayer,stack: ItemStack) { p.inventory.selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.containerMenu.broadcastChanges() }
    private fun near(p: ServerPlayer,pos: BlockPos) { p.teleportTo(p.serverLevel(),pos.x+2.5,pos.y+1.0,pos.z+3.0,140f,12f) }
    private fun click(pos: BlockPos) {
        mc.player!!.inventory.selected=0
        mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false))
    }
    private fun shift(on: Boolean) { mc.options.keyShift.isDown=on;mc.player!!.isShiftKeyDown=on }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("fan-cord-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("FAN_CORD FAIL phase $phase",error) else Eln.logger.info("FAN_CORD COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("eln.fanCordChecks"))event.sound?.location?.toString()?.let { if(it.startsWith("eln:appliance_"))heard+=it }
    }
    private fun sounds()=fans.map { (_,pos) -> ((mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as FanRender).operatingSound }+
        ceilings.map { (_,pos) -> ((mc.level!!.getBlockEntity(pos) as SixNodeEntity).elementRenderList[Direction.YP.int] as LampSocketRender).operatingSound!! }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.fanCordChecks") || done)return
        try {
            ticks++;check(++total<14000) { "Timeout at phase $phase" }
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(23023L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();val f=f(p);p.abilities.flying=true;p.onUpdateAbilities();w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server);w.setWeatherParameters(6000,0,false,false)
                        w.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,w.server)
                        for(x in -4..25)for(z in -4..22) { val at=base.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        for((t,pos) in fans) {
                            val e=f.placeMachine(Eln.findItemStack(t.title,1),pos) as FanElement
                            e.front=Direction.ZP;e.enabled=false;e.reconnect();f.wire(pos.north());f.source(pos.north(2),t.volts)
                        }
                        val sp=base.offset(3,0,10);val supply=wall(p,"240V Lamp Supply",sp,Direction.YN) as LampSupplyElement
                        supply.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!;supply.inventory!!.setItem(0,f.spool());supply.inventoryChange(supply.inventory)
                        val bytes=ByteArrayOutputStream().also { DataOutputStream(it).use { o -> o.writeByte(1);o.writeByte(0);o.writeUTF("Fan gallery") } }.toByteArray()
                        supply.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes)));f.wire(sp.west());f.source(sp.west(2),120.0)
                        for((t,pos) in ceilings) {
                            val e=wall(p,t.itemName,pos,Direction.YP) as LampSocketElement
                            e.poweredByLampSupply=true;e.lampSupplyChannel="Fan gallery";e.fanEnabled=false
                            e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",1));e.inventory.setItem(1,f.spool());e.inventory.setChanged();e.reconnect()
                        }
                        w.setBlockAndUpdate(socket.north(),Blocks.STONE.defaultBlockState())
                        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("Experimental Wall Outlet",1),p,w,socket,net.minecraft.core.Direction.SOUTH))
                        wall(p,"Copper Cable",socket.west(),Direction.ZN)
                        val source=wall(p,"Electrical Source",socket.west(2),Direction.ZN) as mods.eln.sixnode.electricalsource.ElectricalSourceElement
                        source.readConfigTool(CompoundTag().apply { putDouble("voltage",120.0) },p)
                        val box=f.placeMachine(Eln.findItemStack("Classic Box Fan",1),portable) as FanElement;box.front=Direction.ZP;box.reconnect()
                        val e=f.placeMachine(Eln.findItemStack("Electric Mini Oven",1),oven) as KitchenElement;e.front=Direction.ZP;e.reconnect();e.inventory.setItem(0,ItemStack(Items.BEEF,8));e.inventory.setChanged()
                        p.teleportTo(w,base.x+5.0,base.y+2.0,base.z+3.0,135f,15f)
                    };waitFor(2,45)
                }
                2 -> if(settled()) {
                    server { p ->
                        for((_,pos) in fans) { check(fan(p,pos).rotor.speed==0.0);fan(p,pos).networkUnserialize(packet(80)) }
                        for((_,pos) in ceilings) { check(ceiling(p,pos).fanPower==0.0);ceiling(p,pos).networkUnserialize(packet(9)) }
                    };waitFor(3,20)
                }
                3 -> if(settled()) {
                    server { p ->
                        for((_,pos) in fans)check(fan(p,pos).rotor.speed in .01.. .5)
                        for((_,pos) in ceilings)check(ceiling(p,pos).fanPower in .01.. .4)
                        record("All six fan styles accelerate gradually under real wired or wireless electrical power")
                    };waitFor(4,250)
                }
                4 -> if(settled()) {
                    check(heard.containsAll(listOf("eln:appliance_box_fan","eln:appliance_industrial_fan","eln:appliance_ceiling_fan")))
                    check(sounds().all { it.volume>0f && mc.soundManager.isActive(it) })
                    record("All six running fans play their actual ceiling, box or industrial sound samples")
                    Screenshot.grab(mc.gameDirectory,"fans23-gallery.png",mc.mainRenderTarget) {}
                    server { p ->
                        for((_,pos) in fans)check(fan(p,pos).rotor.speed>.99)
                        for((_,pos) in ceilings)check(ceiling(p,pos).fanPower>.99)
                        val e=fan(p,base);val tag=CompoundTag().also(e.rotor::write);val copy=FanDynamics(12.0,20.0).also { it.read(tag) };check(copy.speed==e.rotor.speed)
                        record("Ceiling, box and industrial fans reach full speed and save their rotor inertia")
                        val g=EntityType.IRON_GOLEM.create(p.serverLevel())!!;g.setNoAi(true);g.setNoGravity(true);g.noPhysics=true;g.moveTo(base.x+.5,base.y+1.0,base.z+.5,0f,0f);p.serverLevel().addFreshEntity(g);golem=g
                    };waitFor(5,5)
                }
                5 -> if(settled()) { server { health=golem!!.health };waitFor(6,20) }
                6 -> if(settled()) {
                    server { p ->
                        check(abs(health-golem!!.health-10f)<.1f) { "Blade damage ${health-golem!!.health}" }
                        record("Industrial blades inflict ten health (five hearts) per second at full speed")
                        health=golem!!.health;golem!!.moveTo(base.x+.5,base.y+1.0,base.z+2.0,0f,0f)
                        for((_,pos) in fans)fan(p,pos).networkUnserialize(packet(80))
                        for((_,pos) in ceilings)ceiling(p,pos).networkUnserialize(packet(9))
                    };waitFor(7,30)
                }
                7 -> if(settled()) {
                    check(sounds().all { it.volume>0f && mc.soundManager.isActive(it) })
                    record("Fan sound and blade motion continue during coast-down after the switches are off")
                    server { p ->
                        check(golem!!.health==health);golem!!.discard()
                        for((_,pos) in fans) { val e=fan(p,pos);check(e.rotor.speed in .1.. .99);check(e.motor.power<.001) }
                        for((_,pos) in ceilings)check(ceiling(p,pos).fanPower in .1.. .99)
                        record("Switching off cuts electrical demand while all fans coast; nearby entities outside the blade plane remain safe")
                    };waitFor(8,400)
                }
                8 -> if(settled()) {
                    check(sounds().all { it.volume==0f && !mc.soundManager.isActive(it) })
                    server { p ->
                        for((_,pos) in fans)check(fan(p,pos).rotor.speed==0.0)
                        for((_,pos) in ceilings)check(ceiling(p,pos).fanPower==0.0)
                        record("Every rotor gradually comes to rest")
                        p.setGameMode(GameType.SURVIVAL);p.abilities.mayfly=true;p.abilities.flying=true;p.abilities.invulnerable=true;p.onUpdateAbilities()
                        near(p,socket);hold(p,Eln.findItemStack("Flexible Appliance Cord",1))
                    };waitFor(9,25)
                }
                9 -> if(settled()) { click(socket);step(10) }
                10 -> if(ticks>25) {
                    server { p -> check(CordInteraction.heldAnchor(p.mainHandItem)?.id==outlet(p).id);near(p,portable) };waitFor(11,25)
                }
                11 -> if(settled()) { click(portable);step(12) }
                12 -> if(ticks>45) {
                    server { p ->
                        val e=fan(p,portable);check(e.cord.connected && e.cord.paid);check(p.mainHandItem.isEmpty);check(e.load.voltage>119 && e.motor.power>44)
                        check(outlet(p).breaker.power+e.cord.conductor.power+e.motor.power>44)
                        check(e.getElectricalLoad(Direction.XN,LRDU.Down)==null)
                        heldToken=e.cord.token;record("Real outlet and appliance clicks consume one cord and power a box fan without floor wiring or parallel feed ports")
                        near(p,portable);hold(p,ItemStack.EMPTY)
                    };shift(true);waitFor(13,25)
                }
                13 -> if(settled()) { click(portable);step(14) }
                14 -> if(ticks>25) {
                    shift(false)
                    server { p ->
                        check(p.mainHandItem.`is`(Eln.findItemStack("Classic Box Fan",1).item));check(f(p).node(portable)==null)
                        check(outlet(p).occupant==null && outlet(p).reserved==heldToken)
                        check(abs(outlet(p).breaker.current)<.001)
                        record("Sneak-empty-hand pickup retains the plugged cord while removing appliance demand and reserving the outlet")
                        val carried=p.mainHandItem.copy();hold(p,ItemStack.EMPTY)
                        val e=f(p).placeMachine(carried,moved) as FanElement;e.front=Direction.ZP;e.reconnect()
                        near(p,moved)
                    };waitFor(15,50)
                }
                15 -> if(settled()) {
                    server { p ->
                        val e=fan(p,moved);check(e.cord.connected && e.cord.token==heldToken && e.motor.power>44)
                        record("Placing the carried box fan within cord reach reconnects the same physical grid link")
                        check(e.cord.carry(p));val stack=p.mainHandItem.copy();hold(p,ItemStack.EMPTY)
                        val outside=f(p).placeMachine(stack,distant) as FanElement;outside.front=Direction.ZP;outside.reconnect()
                    };waitFor(16,35)
                }
                16 -> if(settled()) {
                    server { p ->
                        val e=fan(p,distant);check(e.cord.state=="TOO_FAR" && !e.cord.connected && e.motor.power<.001)
                        e.cord.unplug(p);check(e.cord.anchor==null)
                        check(p.inventory.items.sumOf { if(Utils.getItemObject(it) is CordDescriptor)it.count else 0 }==1)
                        record("An overextended cord cannot supply power; unplugging refunds exactly its one paid cord")
                        near(p,socket);hold(p,Eln.findItemStack("Flexible Appliance Cord",1))
                    };waitFor(17,25)
                }
                17 -> if(settled()) { click(socket);step(18) }
                18 -> if(ticks>25) { server { p -> near(p,oven) };waitFor(19,25) }
                19 -> if(settled()) { click(oven);step(20) }
                20 -> if(ticks>45) {
                    server { p ->
                        val e=f(p).machine(oven) as KitchenElement;check(e.cord.connected && e.resistor.power>1200 && e.work.joules>0)
                        startingWork=e.work.joules;val count=e.inventory.getItem(0).count
                        check(e.cord.carry(p));val tag=p.mainHandItem.tagCompound!!.getCompound("carriedAppliance")
                        check(tag.getDouble("kitchenJoules")>=startingWork)
                        val carried=p.mainHandItem.copy();hold(p,ItemStack.EMPTY)
                        val restored=f(p).placeMachine(carried,oven) as KitchenElement;check(restored.inventory.getItem(0).count==count);check(restored.work.joules>=startingWork)
                        near(p,oven)
                        record("Cord-powered kitchen appliances preserve inventory and paid cooking progress through pickup and placement")
                    };waitFor(21,200)
                }
                21 -> if(settled()) {
                    server { p ->
                        val e=f(p).machine(oven) as KitchenElement;check(e.inventory.getItem(2).`is`(Items.COOKED_BEEF))
                        check(outlet(p).input.voltage>119 && !outlet(p).tripped)
                        GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),oven.x,oven.y,oven.z)
                        record("The repositioned mini oven continues cooking from the same wall outlet")
                    };waitFor(22,25)
                }
                22 -> if(settled() && mc.screen is KitchenGui) {
                    Screenshot.grab(mc.gameDirectory,"cord23-oven.png",mc.mainRenderTarget) {}
                    mc.player!!.closeContainer()
                    server { p ->
                        val o=outlet(p);val tag=CompoundTag().also(o::writeToNBT);check(tag.hasUUID("outletReservation"));check(tag.getUUID("outletId")==o.id)
                        val e=f(p).machine(oven) as KitchenElement;e.cord.unplug(p)
                        val t=fan(p,distant);check(t.cord.attach(o.anchor(),false)==false)
                        record("Outlet identity, reservation and breaker state are saved; out-of-reach plugs are rejected")
                    };waitFor(23,25)
                }
                23 -> if(settled()) {
                    server { p ->
                        val o=outlet(p)
                        overloadLoad=mods.eln.sim.mna.component.Resistor(o.output,null).apply { resistance=2.4 }
                        Eln.simulator.addElectricalComponent(overloadLoad)
                    };waitFor(24,30)
                }
                24 -> if(settled()) {
                    server { p ->
                        val o=outlet(p);check(o.tripped && o.input.voltage>119 && o.output.voltage<1)
                        Eln.simulator.removeElectricalComponent(overloadLoad);overloadLoad!!.connectTo(null,null)
                        o.networkUnserialize(packet(80))
                    };waitFor(25,30)
                }
                25 -> if(settled()) {
                    server { p ->
                        val o=outlet(p);check(!o.tripped && o.output.voltage>119)
                        record("A real overload trips only the outlet, leaves its feed healthy and recovers after reset")
                        val e=f(p).machine(oven) as KitchenElement
                        check(e.cord.attach(o.anchor(),true));p.mainHandItem.shrink(1)
                        savedOutletId=o.id
                        fan(p,fans[1].second).networkUnserialize(packet(80))
                    };waitFor(26,60)
                }
                26 -> if(settled()) {
                    server { p ->
                        val e=fan(p,fans[1].second);check(e.rotor.speed>.6);e.networkUnserialize(packet(80))
                    };waitFor(27,2)
                }
                27 -> if(settled()) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(28) }
                28 -> if(ticks>40 && mc.singleplayerServer==null) {
                    mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen fan/cord world") };step(29)
                }
                29 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(30,40)
                30 -> if(settled()) {
                    server { p ->
                        val e=f(p).machine(oven) as KitchenElement;val o=outlet(p)
                        check(e.cord.connected && e.cord.paid && o.id==savedOutletId && o.reserved==e.cord.token)
                        check(e.load.voltage>115 && e.inventory.getItem(0).count+e.inventory.getItem(2).count==8)
                        val coasting=fan(p,fans[1].second);check(!coasting.enabled && coasting.rotor.speed>.1)
                        record("A real world restart restores the powered cord, outlet reservation, intact appliance inventory and a switched-off coasting rotor")
                    };step(31)
                }
                31 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
}
