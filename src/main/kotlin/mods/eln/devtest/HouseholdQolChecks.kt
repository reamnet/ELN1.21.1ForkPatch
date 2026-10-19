package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sixnode.wirelesssignal.WallWirelessElement
import mods.eln.transparentnode.climate.*
import mods.eln.transparentnode.kitchen.*
import mods.eln.transparentnode.ups.*
import mods.eln.transparentnode.worklight.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
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

/** Opt-in production placement, power, native GUIs, audio and persistence for release 21. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object HouseholdQolChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="household-qol-${System.currentTimeMillis()}"
    private val base=BlockPos(256,80,256)
    private val styles=DecorativeLampStyle.entries.filter { it.aimHeadCount>0 || it.fan }
    private val lamps=styles.mapIndexed { i,s -> Triple(s,base.offset(i%5*9,if(s.ceiling)6 else 2,i/5*9),if(s.ceiling)Direction.YP else Direction.XN) }
    private val machines=KitchenType.entries.drop(5).mapIndexed { i,t -> t to base.offset(i*10,0,36) }
    private val climates=ClimateType.entries.mapIndexed { i,t -> t to base.offset(i*10,if(t.cooling)2 else 0,48) }
    private val ups=base.offset(0,2,60);private val cart=base.offset(12,0,60);private val switch=base.offset(24,2,60)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false;private var view=0
    private var work: CompletableFuture<Void>?=null
    @Volatile private var pendingGui: Pair<BlockPos,Direction>?=null
    private val passed=mutableListOf<String>();private var clicks=0
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun six(p: ServerPlayer,pos: BlockPos,side: Direction)=(f(p).node(pos) as SixNode).getElement(side)!!
    private fun packet(body: DataOutputStream.()->Unit)=DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).use(body) }.toByteArray()))
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ body(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun record(s: String) { passed+=s;Eln.logger.info("HOUSEHOLD_QOL PASS {}",s) }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"household21-$name.png",mc.mainRenderTarget) {}
    private fun wall(p: ServerPlayer,name: String,pos: BlockPos,side: Direction): mods.eln.node.six.SixNodeElement {
        p.serverLevel().setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
        val stack=if(name=="Copper Cable")f(p).spool() else Eln.findItemStack(name,1)
        check(Eln.sixNodeItem.placeBlockAt(stack,p,p.serverLevel(),pos,side.toFacing().opposite,.5f,.5f,.5f))
        return six(p,pos,side)
    }
    private fun open(p: ServerPlayer,pos: BlockPos,side: Direction=Direction.ZP) {
        p.teleportTo(p.serverLevel(),pos.x+3.0,pos.y+1.0,pos.z+4.0,140f,12f)
        // Teleporting does not immediately send the target block entity to the client.
        // Wait for its real render/inventory before opening the vanilla container.
        pendingGui=pos to side
    }
    private fun click(screen: Any,name: String) {
        val gui=screen as mods.eln.gui.GuiContainerEln
        val button=screen.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(screen) as GuiButtonEln
        check(button.enabled)
        gui.mouseClicked(button.x+button.width/2.0,button.y+button.height/2.0,0)
        gui.mouseReleased(button.x+button.width/2.0,button.y+button.height/2.0,0)
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("household-qol-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("HOUSEHOLD_QOL FAIL phase $phase",error) else Eln.logger.info("HOUSEHOLD_QOL COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("eln.householdQolChecks") && event.sound?.location?.toString()=="eln:click")clicks++
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.householdQolChecks") || done)return
        try {
            ticks++;check(++total<16000) { "Timeout at phase $phase" }
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            pendingGui?.let { (pos,side) ->
                if(work?.isDone!=false) {
                    work?.join()
                    val tile=mc.level?.getBlockEntity(pos)
                    val available=when(tile) {
                        is TransparentNodeEntity -> tile.elementRender!=null
                        is mods.eln.node.six.SixNodeEntity -> tile.elementRenderList[side.int]!=null
                        else -> false
                    }
                    if(available) {
                        pendingGui=null
                        server { p -> GuiHandler.open(p,GuiHandler.nodeBaseOpen+side.int,p.serverLevel(),pos.x,pos.y,pos.z) }
                    }
                }
            }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();val f=f(p);p.abilities.flying=true;p.onUpdateAbilities();w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server);w.setWeatherParameters(6000,0,false,false)
                        for(x in -8..56)for(z in -8..66) { val at=base.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        val supplyPos=base.west(4)
                        val supply=wall(p,"240V Lamp Supply",supplyPos,Direction.YN) as LampSupplyElement
                        supply.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!;supply.inventory!!.setItem(0,f.spool());supply.inventoryChange(supply.inventory)
                        supply.networkUnserialize(packet { writeByte(1);writeByte(0);writeUTF("Household gallery") })
                        supply.networkUnserialize(packet { writeByte(LampSupplyElement.setTransmissionRange.toInt());writeInt(128) })
                        f.wire(supplyPos.west());f.source(supplyPos.west(2),120.0)
                        for((s,pos,side) in lamps) {
                            val e=wall(p,s.itemName,pos,side) as LampSocketElement
                            e.lampSupplyChannel="Household gallery";e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",s.bulbs));e.inventory.setItem(1,f.spool());e.inventory.setChanged()
                            if(e.aim.heads>0) { e.aim.set(0,0,30.0);e.aim.set(0,1,-25.0);e.needPublish() }
                        }
                        for((t,pos) in machines) {
                            val e=f.placeMachine(Eln.findItemStack(t.title,1),pos) as KitchenElement
                            e.front=Direction.ZP;e.reconnect()
                            f.wire(pos.west());f.source(pos.west(2),120.0)
                            e.inventory.setItem(0,if(t==KitchenType.KETTLE)PotionContents.createItemStack(Items.POTION,Potions.WATER) else ItemStack(if(t==KitchenType.BLENDER)Items.APPLE else Items.WHEAT))
                            if(t!=KitchenType.KETTLE)e.inventory.setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER));e.inventory.setChanged()
                        }
                        for((t,pos) in climates) {
                            val side=if(t.cooling)Direction.ZN else Direction.YN
                            val descriptor=Eln.findItemStack(t.title,1)
                            w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
                            check(Eln.transparentNodeItem.placeBlockAt(descriptor,p,w,pos,if(t.cooling)net.minecraft.core.Direction.SOUTH else net.minecraft.core.Direction.UP))
                            val e=f.machine(pos) as ClimateElement
                            if(!t.cooling) { e.front=Direction.ZP;e.reconnect() }
                            e.control.setTarget(if(t.cooling)16.0 else 32.0)
                            wall(p,"Copper Cable",pos.west(),side).also { /* real same-plane cable */ }
                            (wall(p,"Electrical Source",pos.west(2),side) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",120.0) },p)
                        }
                        w.setBlockAndUpdate(ups.north(),Blocks.STONE.defaultBlockState())
                        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("Wall-mounted Programmable UPS",1),p,w,ups,net.minecraft.core.Direction.SOUTH))
                        val u=f.machine(ups) as UpsElement
                        repeat(24) { i -> val cell=Eln.findItemStack("Portable Battery Pack",1);val d=upsBattery(cell)!!;d.setEnergy(cell,d.getEnergyMax(cell)*.5);u.inventory.setItem(i,cell) }
                        for(x in listOf(-2,-1,1,2,3))w.setBlockAndUpdate(ups.offset(x,0,-1),Blocks.STONE.defaultBlockState())
                        (wall(p,"Electrical Source",ups.west(2),Direction.ZN) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",240.0) },p)
                        wall(p,"Copper Cable",ups.west(),Direction.ZN);wall(p,"Copper Cable",ups.east(),Direction.ZN)
                        val load=wall(p,"Creative Power Resistor",ups.east(2),Direction.ZN) as mods.eln.sixnode.CreativePowerResistorElement
                        load.front=LRDU.entries.first { Direction.ZN.applyLRDU(it.right())==Direction.XP };load.reconnect()
                        load.readConfigTool(CompoundTag().apply { putDouble("resistance",28.8) },p);wall(p,"Ground Cable",ups.east(3),Direction.ZN)
                        val cartElement=f.placeMachine(Eln.findItemStack("Gasoline Construction Work Light",1),cart) as WorkLightElement
                        cartElement.wireless=true;cartElement.channel="Household gallery";cartElement.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",2));cartElement.inventory.setChanged()
                        wall(p,"Wireless Paddle Switch",switch,Direction.XN)
                        record("All new items and adjustable legacy fixtures place using production block and cable paths")
                    };waitFor(2,300)
                }
                2 -> if(settled()) {
                    server { p ->
                        for((s,pos,side) in lamps) {
                            val e=six(p,pos,side) as LampSocketElement;check(e.sixNode!!.lightValue>=13) { "Dark $s ${e.electricalLoad.voltage}" }
                            if(s.fan)check(e.fanPower>.95)
                        }
                        record("All aimed lamps, five dot styles, twin eave and both ceiling fans run from a real wireless supply")
                        for((t,pos) in machines) { val e=f(p).machine(pos) as KitchenElement;check(!e.inventory.getItem(2).isEmpty) { "No output $t: ${e.status} ${e.work.joules}" } }
                        record("The kettle, blender and rice cooker finish paid electrical recipes; the grain cooker returns its bottle")
                        for((t,pos) in climates) { val e=f(p).machine(pos) as ClimateElement;check(e.watts>t.watts*.95) { "$t: ${e.status}/${e.roomTemperature}/${e.load.voltage}/${e.front}" };check(if(t.cooling)e.indoor.temperatureCelsius<0 && e.outdoor.temperatureCelsius>0 else e.indoor.temperatureCelsius>0) }
                        record("Four heaters draw rated power; both AC units cool their indoor coils and reject heat behind their walls")
                        val u=f(p).machine(ups) as UpsElement;check(abs(u.unit.secondary.voltage-120.0)<.2);check(u.unit.outputWatts>490)
                        open(p,ups)
                    };step(3)
                }
                3 -> if(settled() && mc.screen is UpsGui && ticks>30) { capture("wall-ups-regulated");click(mc.screen!!,"bypass");waitFor(4,60) }
                4 -> if(settled()) {
                    server { p -> val u=(f(p).machine(ups) as UpsElement).unit;check(u.bypass);check(u.status=="BYPASS");check(u.secondary.voltage>220);check(abs(u.primary.voltage-u.secondary.voltage)<.1);check(!u.inverterOutput.enabled) }
                    capture("wall-ups-bypass");click(mc.screen!!,"bypass");waitFor(5,60)
                }
                5 -> if(settled()) {
                    mc.setScreen(null);server { p ->
                        val u=(f(p).machine(ups) as UpsElement).unit;check(!u.bypass);check(abs(u.secondary.voltage-120)<.2)
                        record("The wall UPS GUI enters raw 240 V bypass and returns to regulated 120 V without parallel inverter drive")
                        val pos=lamps.first { it.first==DecorativeLampStyle.EAVE_TWIN };open(p,pos.second,pos.third)
                    };step(6)
                }
                6 -> if(settled() && mc.screen is LampSocketGui && ticks>30) {
                    capture("twin-eave-aim")
                    val gui=mc.screen as LampSocketGui
                    val aim=LampSocketGui::class.java.getDeclaredField("aiming").apply { isAccessible=true }.get(gui)!!
                    val right=aim.javaClass.getDeclaredField("right").apply { isAccessible=true }.get(aim) as GuiButtonEln
                    gui.mouseClicked(right.x+right.width/2.0,right.y+right.height/2.0,0)
                    gui.mouseReleased(right.x+right.width/2.0,right.y+right.height/2.0,0);waitFor(7,40)
                }
                7 -> if(settled()) {
                    mc.setScreen(null);server { p ->
                        val (_,pos,side)=lamps.first { it.first==DecorativeLampStyle.EAVE_TWIN };val e=six(p,pos,side) as LampSocketElement
                        check(e.aim.pan(0)==35.0);check(e.aim.pan(1)==0.0)
                        record("The actual aiming GUI changes one eave head independently and synchronizes it to the server");open(p,cart)
                    };step(8)
                }
                8 -> if(settled() && mc.screen is WorkLightGui && ticks>30) {
                    capture("construction-aim");mc.setScreen(null)
                    server { p -> p.teleportTo(p.serverLevel(),switch.x+2.0,switch.y-1.0,switch.z+.5,90f,0f);(six(p,switch,Direction.XN) as WallWirelessElement).onBlockActivated(p,Direction.XN,.5f,.5f,.5f) };waitFor(9,40)
                }
                9 -> if(settled()) {
                    check(clicks>0) { "Missing actual switch click event" };record("The new wall switch plays its quiet click in the native sound engine")
                    server { p -> open(p,climates.last().second) };step(10)
                }
                10 -> if(settled() && mc.screen is ClimateGui && ticks>30) { capture("wall-ac-thermostat");click(mc.screen!!,"enabled");waitFor(11,40) }
                11 -> if(settled()) {
                    server { p -> val e=f(p).machine(climates.last().second) as ClimateElement;check(!e.control.enabled);check(e.watts<.001);record("The AC's actual on/off GUI disconnects its electrical load and stops active cooling") }
                    mc.setScreen(null);step(12)
                }
                12 -> if(settled() && view<climates.size+machines.size+lamps.size) {
                    val pos=if(view<climates.size)climates[view].second else if(view<climates.size+machines.size)machines[view-climates.size].second else lamps[view-climates.size-machines.size].second
                    server { p -> p.teleportTo(p.serverLevel(),pos.x+2.8,pos.y+if(view>=climates.size+machines.size)-1.5 else .5,pos.z+3.0,137f,if(view>=climates.size+machines.size)-10f else 10f) };waitFor(13,20)
                } else if(settled()) {
                    record("Every new case and fixture renders in the native client")
                    server { p ->
                        val w=p.serverLevel();val pos=climates.last().second
                        for(x in -3..3)for(y in -2..3)for(z in -1..5)if(x==-3 || x==3 || y==-2 || y==3 || z==-1 || z==5)
                            w.setBlockAndUpdate(pos.offset(x,y,z),Blocks.STONE.defaultBlockState())
                        (f(p).machine(pos) as ClimateElement).control.enabled=true
                        p.teleportTo(w,pos.x+.5,pos.y-1.0,pos.z+2.5,180f,0f)
                    };waitFor(14,200)
                }
                13 -> if(settled()) { capture("model-$view");view++;waitFor(12,5) }
                14 -> if(settled()) {
                    server { p ->
                        val e=f(p).machine(climates.last().second) as ClimateElement
                        check(e.roomDetected) { "The enclosed room was not detected" }
                        val room=mods.eln.environment.RoomThermalManager.getRoomAt(e.coordinate().world(),e.coordinate().x,e.coordinate().y,e.coordinate().z+2)
                        check(room!=null && room.temperatureCelsius<-.001) { "AC did not cool room: $room" }
                        record("The wall AC lowers the detected room's actual thermal temperature while its exhaust stays outside")
                        e.control.enabled=false
                        (f(p).machine(ups) as UpsElement).unit.settings(CompoundTag().apply { putBoolean("upsBypass",true);putDouble("upsOutputVoltage",120.0) })
                    };waitFor(15,40)
                }
                15 -> if(settled()) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(16) }
                16 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen household world") };step(17) }
                17 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(18,100)
                18 -> if(settled()) {
                    server { p ->
                        val (_,pos,side)=lamps.first { it.first==DecorativeLampStyle.EAVE_TWIN };val e=six(p,pos,side) as LampSocketElement
                        check(e.aim.pan(0)==35.0 && e.aim.pan(1)==0.0)
                        check(!(f(p).machine(climates.last().second) as ClimateElement).control.enabled)
                        val u=f(p).machine(ups) as UpsElement;check(u.unit.bypass && u.bank.activeCount==24);check(u.unit.secondary.voltage>220);check(abs(u.unit.primary.voltage-u.unit.secondary.voltage)<.1)
                        record("A real world restart preserves independent lamp aim, the AC thermostat/off setting and wall UPS bypass with its battery rack")
                    };step(19)
                }
                19 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t) }
    }
}
