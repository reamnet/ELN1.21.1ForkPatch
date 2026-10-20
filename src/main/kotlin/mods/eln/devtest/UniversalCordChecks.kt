package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.kitchen.*
import mods.eln.transparentnode.climate.*
import mods.eln.transparentnode.fan.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.item.ItemStack
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

/** Actual registered fixtures, wireless feeds, 20 A protection, a cold restart and ceiling blade damage. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object UniversalCordChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="universal-cords-${System.currentTimeMillis()}"
    private val base=BlockPos(256,80,256)
    private val supplyPos=base.west(9)
    private val supply240Pos=supplyPos.north(8)
    private val devices=mutableListOf<Pair<BlockPos,Double>>()
    private val outlets=mutableListOf<BlockPos>()
    private val passed=mutableListOf<String>()
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private var fanPos=BlockPos.ZERO;private var tablePos=BlockPos.ZERO
    private var golem: IronGolem?=null;private var health=0f
    private var overload: mods.eln.sim.mna.component.Resistor?=null
    private var savedId: java.util.UUID?=null
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun device(p: ServerPlayer,pos: BlockPos): CordDevice {
        val n=f(p).node(pos)
        return when(n) { is TransparentNode -> n.element as CordDevice;is SixNode -> n.sideElementList.filterIsInstance<CordDevice>().single();else -> error("Missing device $pos") }
    }
    private fun outlet(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as OutletElement
    private fun supply(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as SixNode).getElement(Direction.YN) as LampSupplyElement
    private fun wall(p: ServerPlayer,stack: ItemStack,pos: BlockPos,side: Direction): SixNodeElement {
        val w=p.serverLevel();w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(stack,p,w,pos,side.toFacing().opposite,.5f,.5f,.5f)) { "Cannot place ${stack.hoverName.string} at $pos" }
        return (f(p).node(pos) as SixNode).getElement(side)!!
    }
    private fun createOutlet(p: ServerPlayer,pos: BlockPos,rating: CordRating): OutletElement {
        val w=p.serverLevel();w.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(rating.outletName,1),p,w,pos,net.minecraft.core.Direction.SOUTH))
        return outlet(p,pos).also { it.wireless=true;it.channel=if(rating==CordRating.V120)"Cord gallery 120" else "Cord gallery 240";it.reconnect();outlets+=pos }
    }
    private fun setupSupply(p: ServerPlayer,pos: BlockPos,volts: Double) {
        for(dx in -3..1) { val at=pos.offset(dx,-1,0);p.serverLevel().setChunkForced(at.x shr 4,at.z shr 4,true);p.serverLevel().setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
        val s=wall(p,Eln.findItemStack("240V Lamp Supply",1),pos,Direction.YN) as LampSupplyElement
        s.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!;s.inventory!!.setItem(0,f(p).spool());s.inventoryChange(s.inventory)
        s.readConfigTool(CompoundTag().apply { putInt("transmissionRange",512);put("powerChannels",ListTag().apply { add(StringTag.valueOf(if(volts==120.0)"Cord gallery 120" else "Cord gallery 240")) }) },p)
        f(p).wire(pos.west());// A 121 V boundary compensates cord losses so the 120 V ceiling fan reaches true full speed.
        f(p).source(pos.west(2),if(volts==120.0)121.0 else volts)
    }
    private fun attach(p: ServerPlayer,pos: BlockPos,volts: Double) {
        val o=createOutlet(p,pos.offset(5,0,3),if(volts==240.0)CordRating.V240 else CordRating.V120)
        check(device(p,pos).cord.attach(o.anchor(),false)) { "Could not attach $pos / $volts" }
        devices+=pos to volts
    }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({body(s.playerList.players.first())},s) }
    private fun next(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;phase=n;ticks=0 }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun pass(s: String) { passed+=s;Eln.logger.info("UNIVERSAL_CORD PASS {}",s) }
    private fun packet(id: Int)=DataInputStream(ByteArrayInputStream(byteArrayOf(id.toByte())))
    private fun checkPower(p: ServerPlayer) {
        for((pos,volts) in devices) {
            val d=device(p,pos);check(d.cord.connected) { "Unconnected $pos: ${d.cord.state}" }
            check(d.cord.input.voltage in volts*.94..volts*1.01) { "Bad voltage $pos: ${d.cord.input.voltage}, expected $volts" }
        }
        check(outlets.none { outlet(p,it).tripped })
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("universal-cord-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"devices" to devices.size,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("UNIVERSAL_CORD FAIL phase $phase",error) else Eln.logger.info("UNIVERSAL_CORD COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.universalCordChecks") || done)return
        try {
            ticks++;check(++total<14000) { "Timeout at phase $phase" }
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(24024L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);phase=1;ticks=0
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();w.dayTime=18000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server);w.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,w.server)
                        p.abilities.flying=true;p.onUpdateAbilities()
                        var cell=0
                        fun cell(): BlockPos {
                            val pos=base.offset((cell%10)*16,0,(cell/10)*16);cell++
                            for(x in -4..8)for(z in -4..7) { val at=pos.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                            return pos
                        }
                        setupSupply(p,supplyPos,120.0);setupSupply(p,supply240Pos,240.0)
                        for(s in DecorativeLampStyle.entries) {
                            val pos=cell().above(if(s.ceiling || s==DecorativeLampStyle.DOT)8 else 0)
                            val side=if(s.ceiling || s==DecorativeLampStyle.DOT || s.miniDot || s==DecorativeLampStyle.EAVE_TWIN)Direction.YP else if(s.wallLamp)Direction.XN else Direction.YN
                            val e=wall(p,Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(s)]!!.newItemStack(1),pos,side) as LampSocketElement
                            e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",s.bulbs));e.inventory.setChanged();e.reconnect()
                            if(s==DecorativeLampStyle.FAN_INDUSTRIAL)fanPos=pos
                            if(s==DecorativeLampStyle.TABLE_DRUM)tablePos=pos
                            attach(p,pos,120.0)
                        }
                        val legacy=Eln.sixNodeItem.subItemList.values.filterNotNull().filter { (it is LampSocketDescriptor && it.fixture==null) || it is mods.eln.sixnode.EmergencyLampDescriptor }
                        for(d in legacy) {
                            val pos=cell()
                            val side=Direction.entries.first { d.checkCanPlace(Coordinate(pos.x,pos.y,pos.z,w),it,LRDU.Up)==null }
                            val e=wall(p,d.newItemStack(1),pos,side)
                            if(e is LampSocketElement) { e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",1));e.inventory.setChanged();e.reconnect() }
                            attach(p,pos,(e as CordDevice).cordVoltage)
                        }
                        val machines=Eln.transparentNodeItem.subItemList.values.filterNotNull().filter { CordDevice::class.java.isAssignableFrom(it.ElementClass) }
                        for(d in machines) {
                            val pos=cell();w.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
                            val face=if(d is PowerCabinetDescriptor && d.wallMounted)net.minecraft.core.Direction.SOUTH else net.minecraft.core.Direction.UP
                            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(1),p,w,pos,face)) { "Could not place ${d.name}" }
                            val e=f(p).machine(pos)
                            when(e) {
                                is mods.eln.transparentnode.worklight.WorkLightElement -> { e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",2));e.inventory.setChanged() }
                                is mods.eln.transparentnode.floodlight.FloodlightElement -> { e.inventory.setItem(0,Eln.findItemStack(if(e.motorized)"240V LED Light Bulb" else "120V LED Light Bulb",1));e.inventory.setChanged() }
                            }
                            val v=(e as CordDevice).cordVoltage
                            attach(p,pos,v)
                        }
                        p.teleportTo(w,tablePos.x+2.0,tablePos.y+1.0,tablePos.z+3.0,140f,12f)
                    };next(2,180)
                }
                2 -> if(settled()) {
                    server { p ->
                        checkPower(p)
                        pass("Every registered decorative lamp and cord-capable appliance powers through matching wireless-fed outlets without an internal cable")
                        val one=outlet(p,outlets.first());val d=device(p,devices.first().first)
                        check(!d.cord.attach(outlet(p,outlets.last()).anchor(),false))
                        val snapshot=CompoundTag().also(one::writeToNBT);check(snapshot.getBoolean("outletWireless"));check(snapshot.getString("outletChannel")==one.channel)
                        val total=outlets.filter { outlet(p,it).rating==CordRating.V120 }.sumOf { abs(outlet(p,it).wirelessPower.conductor.current) }
                        val feed=supply(p,supplyPos).powerLoad
                        check(abs(feed.current)>total*.98 && total>1.0) { "Wireless input current ${feed.current}, outlets $total" }
                        pass("Wireless outlets draw their real combined load from the Lamp Supply and save their selected channel")
                        val control=outlet(p,outlets[1]);val link=control.wirelessPower.conductor;val subsystem=control.input.subSystem
                        repeat(30) { control.wirelessPower.refresh() }
                        check(control.wirelessPower.conductor===link && control.input.subSystem===subsystem)
                        pass("Stable wireless outlets keep their existing conductor and solver subsystem")
                        control.networkUnserialize(packet(82));check(!control.wireless && control.wirelessPower.supply==null)
                        check(Direction.entries.any { side -> LRDU.entries.any { control.getElectricalLoad(side,it)!=null } })
                        control.networkUnserialize(packet(82));check(Direction.entries.all { side -> LRDU.entries.all { control.getElectricalLoad(side,it)==null } })
                        pass("Changing outlet power mode isolates the wired grid before reconnecting wireless power")
                        val g=EntityType.IRON_GOLEM.create(p.serverLevel())!!;g.setNoAi(true);g.setNoGravity(true);g.noPhysics=true
                        val e=device(p,fanPos) as LampSocketElement
                        val c=DecorativeLampStyle.FAN_INDUSTRIAL.worldPoint(Vec3.atCenterOf(fanPos),e.side,e.front,Vec3(.4575,0.0,0.0))
                        g.moveTo(c.x,c.y-1.5,c.z,0f,0f);p.serverLevel().addFreshEntity(g);golem=g
                    };next(3,5)
                }
                3 -> if(settled()) { server { health=golem!!.health };next(4,20) }
                4 -> if(settled()) {
                    server { p ->
                        check(abs(health-golem!!.health-5f)<.01f) { "Ceiling damage was ${health-golem!!.health}" }
                        pass("Five-block ceiling blades inflict exactly five damage per second at full speed")
                        val e=device(p,fanPos) as LampSocketElement
                        golem!!.moveTo(fanPos.x+.5,fanPos.y-3.0,fanPos.z+.5,0f,0f);health=golem!!.health
                        e.networkUnserialize(packet(9))
                        val other=wall(p,Eln.findItemStack("Signal Cable",1),tablePos,Direction.XN)
                        val table=device(p,tablePos)
                        savedId=table.cord.anchor!!.id
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        check(table.cord.carry(p));check(deviceOrNull(p,tablePos)==null)
                        check((f(p).node(tablePos) as SixNode).getElement(Direction.XN)===other)
                        val stack=p.mainHandItem.copy();check(stack.tagCompound!!.contains("carriedAppliance"))
                        check(Eln.sixNodeItem.placeBlockAt(stack,p,p.serverLevel(),tablePos,net.minecraft.core.Direction.UP,.5f,1f,.5f))
                        val restored=device(p,tablePos) as LampSocketElement
                        check(restored.installedBulbs>0 && restored.cord.anchor!!.id==savedId)
                        pass("A plugged table lamp can be picked up and placed with its bulbs and cord reservation intact while preserving another face on the same block")
                    };next(5,30)
                }
                5 -> if(settled()) {
                    server { p ->
                        check(golem!!.health==health)
                        val e=device(p,fanPos) as LampSocketElement;check(!e.fanEnabled && e.fanPower in .1.. .99)
                        checkPower(p)
                        pass("Walking below the ceiling fan is safe while switched-off blades coast down gradually")
                        val o=outlet(p,outlets.first());overload=mods.eln.sim.mna.component.Resistor(o.output,null).apply { resistance=2.4 };Eln.simulator.addElectricalComponent(overload)
                    };next(6,30)
                }
                6 -> if(settled()) {
                    server { p ->
                        val o=outlet(p,outlets.first());check(!o.tripped && o.output.voltage>118 && supply(p,supplyPos).powerLoad.voltage>119)
                        Eln.simulator.removeElectricalComponent(overload);overload!!.connectTo(null,null);o.networkUnserialize(packet(80))
                        pass("A wireless-fed outlet remains passive during overload; upstream branch protection is required")
                        val s=supply(p,supplyPos);s.disconnectJob();check(outlets.filter { outlet(p,it).rating==CordRating.V120 }.all { outlet(p,it).wirelessPower.supply==null });s.connectJob()
                        pass("Source unload disconnects every wireless bridge before removing supply states")
                    };next(7,50)
                }
                7 -> if(settled()) { server { p -> checkPower(p);p.teleportTo(p.serverLevel(),fanPos.x+5.0,fanPos.y-1.5,fanPos.z+5.0,135f,-5f) };next(8,40) }
                8 -> if(settled()) {
                    Screenshot.grab(mc.gameDirectory,"industrial-ceiling24.png",mc.mainRenderTarget) {}
                    mc.level!!.disconnect();mc.disconnect(TitleScreen());phase=9;ticks=0
                }
                9 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen universal cord world") };phase=10;ticks=0 }
                10 -> if(mc.player!=null && mc.level!=null && mc.screen==null)next(11,100)
                11 -> if(settled()) { server { p -> checkPower(p);check(device(p,tablePos).cord.anchor!!.id==savedId);pass("Cold restart restores all wireless outlets, matching cord links, bulb loads and table-lamp reservation") };next(12,1) }
                12 -> if(settled()) {
                    server { p -> p.teleportTo(p.serverLevel(),tablePos.x+2.0,tablePos.y+1.0,tablePos.z+3.0,140f,12f);p.inventory.selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("Flexible Appliance Cord",1));p.containerMenu.broadcastChanges() };next(13,30)
                }
                13 -> if(settled()) { mc.options.keyShift.isDown=true;mc.player!!.isShiftKeyDown=true;next(14,20) }
                14 -> if(settled()) { click(tablePos);next(15,25) }
                15 -> if(settled()) {
                    server { p -> check(!device(p,tablePos).cord.plugged) };mc.options.keyShift.isDown=false;mc.player!!.isShiftKeyDown=false;next(16,20)
                }
                16 -> if(settled()) { click(tablePos.offset(5,0,3));next(17,25) }
                17 -> if(settled()) { click(tablePos);next(18,30) }
                18 -> if(settled()) {
                    server { p -> check(device(p,tablePos).cord.connected);pass("Real cord right-clicks select the outlet and resolve the correct SixNode lamp face") };next(19,1)
                }
                19 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
    private fun click(pos: BlockPos) {
        mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false))
    }
    private fun deviceOrNull(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as? SixNode)?.sideElementList?.filterIsInstance<CordDevice>()?.singleOrNull()
}
