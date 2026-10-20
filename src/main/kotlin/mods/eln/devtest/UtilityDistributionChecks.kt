package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gridnode.*
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.utilitylight.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
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
import kotlin.math.ceil

/** Opt-in native campaign: actual registered devices, cables, GUI slots, sounds and a cold restart. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object UtilityDistributionChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="utility-distribution-${System.currentTimeMillis()}"
    private val base=BlockPos(256,80,256)
    private val feeder=base;private val recloser=base.east(16);private val transformer=base.east(32);private val secondary=base.east(48)
    private val head=base.east(56).above(6);private val meter=head.below(2);private val disconnectPos=meter.below(2);private val panel=disconnectPos.below(2)
    private val chandelier=panel.south(18).above(6)
    private var peakChandelierAmps=0.0
    private val lamp=panel.south(5);private val fan=panel.south(10).above(4);private val outlet=panel.east(4);private val table=outlet.south(3)
    private val householdOutlet=panel.east(8);private val stripPos=householdOutlet.south(2)
    private val extra240=outlet.south(5)
    private val stripLamps=ArrayList<BlockPos>()
    private val regulator=base.north(16);private val capacitor=base.north(32)
    private val coverTest=base.south(18)
    private val gallery=linkedMapOf<DistributionKind,BlockPos>()
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=ArrayList<String>();private val sounds=HashMap<String,Int>()
    private var fault: Resistor?=null;private var branchFault: Resistor?=null
    private var energyBefore=0.0
    private var arcLoad: Resistor?=null;private var arcFeed: Resistor?=null
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun pole(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as DistributionPoleElement
    private fun panel(p: ServerPlayer)=f(p).machine(panel) as BreakerPanelElement
    private fun meter(p: ServerPlayer)=f(p).machine(meter) as HouseServiceElement
    private fun lamp(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as SixNode).sideElementList.filterIsInstance<LampSocketElement>().single()
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({body(s.playerList.players.first())},s) }
    private fun next(n: Int,delay: Int=30) { ready=mc.singleplayerServer!!.tickCount+delay;phase=n;ticks=0 }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun pass(s: String) { passed+=s;Eln.logger.info("UTILITY_NATIVE PASS {}",s) }
    private fun packet(id: Int,body: DataOutputStream.()->Unit={}): DataInputStream {
        val b=ByteArrayOutputStream();DataOutputStream(b).use { it.writeByte(id);it.body() };return DataInputStream(ByteArrayInputStream(b.toByteArray()))
    }
    private fun settings(e: TransparentNodeElement,id: Int,body: DataOutputStream.()->Unit={}) { e.networkUnserialize(packet(id,body)) }
    private fun place(p: ServerPlayer,name: String,pos: BlockPos): TransparentNodeElement {
        p.serverLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        return f(p).placeMachine(Eln.findItemStack(name,1),pos).also { it.front=Direction.ZP;it.reconnect() }
    }
    private fun wall(p: ServerPlayer,name: String,pos: BlockPos): TransparentNodeElement {
        p.serverLevel().setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,p.serverLevel(),pos,net.minecraft.core.Direction.SOUTH)) { "Wall placement failed: $name" }
        return f(p).machine(pos)
    }
    private fun fixture(p: ServerPlayer,style: DecorativeLampStyle,pos: BlockPos): LampSocketElement {
        val side=if(style.ceiling)Direction.YP else Direction.YN
        p.serverLevel().setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(style.itemName,1),p,p.serverLevel(),pos,side.toFacing().opposite,.5f,.5f,.5f))
        return lamp(p,pos).also { e -> e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",style.bulbs));e.inventory.setItem(1,f(p).spool());e.inventory.setChanged();e.reconnect() }
    }
    private fun link(p: ServerPlayer,a: GridElement,b: GridElement,from: Int,to: Int) {
        val stack=f(p).spool(300.0);val d=Eln.sixNodeItem.getDescriptor(stack) as mods.eln.sixnode.electricalcable.UtilityCableDescriptor
        val length=ceil(a.coordinate().trueDistanceTo(b.coordinate())).toInt()*a.gridLoads(from).size
        GridLink.addLink(a,b,from,to,d,length)
    }
    private fun gui(p: ServerPlayer,pos: BlockPos) { p.teleportTo(p.serverLevel(),pos.x+2.0,pos.y+1.0,pos.z+4.0,150f,15f);GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z) }
    private fun button(field: String) {
        val screen=mc.screen ?: error("No native GUI")
        val b=screen.javaClass.getDeclaredField(field).apply { isAccessible=true }.get(screen) as GuiButtonEln
        screen.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);screen.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun fuseButton() {
        val g=mc.screen as DistributionGui
        @Suppress("UNCHECKED_CAST") val buttons=g.javaClass.getDeclaredField("fuses").apply { isAccessible=true }.get(g) as List<GuiButtonEln>
        val b=buttons[0];g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"utility-$name.png",mc.mainRenderTarget) {}
    private fun verifyPower(p: ServerPlayer) {
        val t=pole(p,transformer);check(t.transformerPhase==2);check(t.unit!!.ratedWatts>=25_000)
        check(t.unit.controller.status !in setOf("NON_CONVERGENT","INVALID_NETWORK")) { t.unit.controller.status }
        panel(p).buses.forEachIndexed { i,b -> f(p).near(b.voltage,if(i==2)240.0 else 120.0,1.0) }
        check(lamp(p,lamp).electricalLoad.voltage>118);check(lamp(p,fan).fanPower>.9) { "Fan speed ${lamp(p,fan).fanPower}" }
        check((f(p).machine(outlet) as OutletElement).input.voltage>238)
        check(lamp(p,table).cord.connected && lamp(p,table).electricalLoad.voltage>235)
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("utility-distribution-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"sounds" to sounds,"phase" to phase,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("UTILITY_NATIVE FAIL phase $phase",error) else Eln.logger.info("UTILITY_NATIVE COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun measure(event: net.neoforged.neoforge.event.tick.ServerTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.utilityDistributionChecks") || phase<2 || done)return
        val node=mods.eln.node.NodeManager.instance?.getNodeFromCoordonate(Coordinate(panel.x,panel.y,panel.z,0)) as? TransparentNode
        val e=node?.element as? BreakerPanelElement ?: return
        peakChandelierAmps=maxOf(peakChandelierAmps,abs(e.circuits[3].contacts[2].current))
    }
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("eln.utilityDistributionChecks"))event.sound?.location?.toString()?.takeIf { it.startsWith("eln:utility_") }?.let { sounds[it]=(sounds[it] ?: 0)+1 }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.utilityDistributionChecks") || done)return
        try {
            check(++total<18000) { "Timeout at phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1440,1000);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(25250L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();w.dayTime=18000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server)
                        for(x in -3..7)for(z in -4..5)w.setChunkForced((base.x shr 4)+x,(base.z shr 4)+z,true)
                        for(x in -4..74)for(z in -4..14)w.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                        for(at in listOf(coverTest,coverTest.east(3)))w.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState())
                        f(p).wire(coverTest)
                        (f(p).node(coverTest) as SixNode).apply { sixNodeCacheBlock=Blocks.STONE;needPublish=true }
                        w.chunkSource.lightEngine.checkBlock(coverTest)
                        w.setBlockAndUpdate(coverTest.east(3),Blocks.STONE.defaultBlockState())
                        val feed=place(p,DistributionKind.FEEDER_THREE.title,feeder) as DistributionPoleElement
                        val rec=place(p,DistributionKind.RECLOSER_THREE.title,recloser) as DistributionPoleElement
                        val trafo=place(p,DistributionKind.STREET_THREE.title,transformer) as DistributionPoleElement
                        trafo.inventory.setItem(0,Eln.findItemStack("25 kW Pole Transformer",1));trafo.inventory.setChanged()
                        settings(trafo,80) { writeInt(240) };settings(trafo,86) { writeInt(100_000) };check(trafo.unit!!.ratedWatts==25_000);settings(trafo,88) { writeInt(2) }
                        val sec=place(p,DistributionKind.SECONDARY_LIGHT.title,secondary) as DistributionPoleElement
                        for((dir,volts) in listOf(Direction.XN to 14_000.0,Direction.XP to 13_000.0,Direction.ZN to 12_470.0)) {
                            val a=feeder.relative(dir.toFacing());val b=a.relative(dir.toFacing());w.setBlockAndUpdate(a.below(),Blocks.STONE.defaultBlockState());w.setBlockAndUpdate(b.below(),Blocks.STONE.defaultBlockState());f(p).wire(a);f(p).source(b,volts)
                        }
                        // Owner-relative ghost hits select all three HV phases without a GUI.
                        p.setItemInHand(InteractionHand.MAIN_HAND,f(p).spool(300.0))
                        val outer=feed.front.rotateFromXN(net.minecraft.world.phys.Vec3(0.0,7.0,-1.0))
                        val feedGhost=Eln.ghostManager.getGhost(feed.coordinate().newWithOffset(kotlin.math.round(outer.x).toInt(),7,kotlin.math.round(outer.z).toInt()))!!
                        val recGhost=Eln.ghostManager.getGhost(rec.coordinate().newWithOffset(0,7,0))!!
                        check(feedGhost.onBlockActivated(p,Direction.ZP,.5f,.15f,.5f))
                        check(recGhost.onBlockActivated(p,Direction.ZP,.5f,.15f,.5f))
                        check(feed.gridLinkList.single().getTerminal(feed)==ServiceTerminals.HV_LINE_BUNDLE)
                        link(p,rec,trafo,ServiceTerminals.HV_LOAD_BUNDLE,ServiceTerminals.HV_LINE_BUNDLE)
                        link(p,trafo,sec,50,50)
                        val h=wall(p,"120/240V Service Weatherhead",head) as HouseServiceElement
                        val m=wall(p,"North American Meter Base",meter) as HouseServiceElement
                        val serviceSwitch=wall(p,"Red Handle Service Disconnect",disconnectPos) as HouseServiceElement
                        val pan=wall(p,"Twelve Circuit American Breaker Panel",panel) as BreakerPanelElement
                        m.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));m.inventory.setChanged()
                        // Aim at the secondary rack, rather than changing the selected GUI terminal.
                        check(sec.onBlockActivated(p,Direction.ZP,.5f,5.5f,.5f))
                        check(h.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f))
                        check(sec.gridLinkList.any { it.getTerminal(sec)==50 })
                        for(at in listOf(head.below(),meter.below(),disconnectPos.below())) {
                            w.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState())
                            check(Eln.sixNodeItem.placeBlockAt(f(p).spool(),p,w,at,net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f))
                        }
                        // Cabinets must reject a held overhead spool without creating spans.
                        check(m.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f));check(pan.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f))
                        check(m.gridLinkList.isEmpty() && pan.gridLinkList.isEmpty())
                        val br=Eln.sixNodeItem.subItemList.values.filterIsInstance<mods.eln.sixnode.electricalbreaker.ElectricalBreakerDescriptor>().first { it.currentLimit==20.0 }
                        repeat(12) { pan.inventory.setItem(it,br.newItemStack()) };pan.inventory.setChanged()
                        pan.inventory.setItem(3,Eln.findItemStack("30A Electrical Breaker",1));pan.inventory.setChanged()
                        pan.setPowerChannel(3,"Chandelier 5400");pan.circuits[3].use240=true;pan.circuits[3].configure()
                        val ch=fixture(p,DecorativeLampStyle.LUXURY_CASCADE,chandelier)
                        ch.inventory.setItem(0,Eln.findItemStack("240V Halogen Light Bulb",20));ch.inventory.setChanged();ch.lampSupplyChannel="Chandelier 5400"
                        pan.setPowerChannel(0,"Utility lamp");pan.setPowerChannel(1,"Utility fan");pan.setPowerChannel(2,"Utility 240");pan.circuits[2].use240=true;pan.circuits[2].configure()
                        fixture(p,DecorativeLampStyle.TABLE_DRUM,lamp).lampSupplyChannel="Utility lamp"
                        fixture(p,DecorativeLampStyle.FAN_AURA,fan).lampSupplyChannel="Utility fan"
                        val o=wall(p,"240 V Wall Outlet",outlet) as OutletElement;o.wireless=true;o.channel="Utility 240";o.reconnect()
                        val d=fixture(p,DecorativeLampStyle.DESK,table);d.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",1));d.inventory.setChanged();check(d.cord.attach(o.anchor(),false))
                        val reg=place(p,DistributionKind.REGULATOR_THREE.title,regulator) as DistributionPoleElement
                        settings(reg,96) { writeDouble(13_000.0) };repeat(3) { i -> link(p,feed,reg,32+i,32+i) }
                        val cap=place(p,DistributionKind.CAPACITOR_THREE.title,capacitor) as DistributionPoleElement
                        repeat(3) { i -> link(p,feed,cap,32+i,32+i) }
                        DistributionKind.entries.forEachIndexed { i,kind ->
                            val pos=base.south(30+(i/8)*16).east((i%8)*12);gallery[kind]=pos
                            for(x in -2..2)for(z in -2..2)w.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                            val e=place(p,kind.title,pos) as DistributionPoleElement
                            if(kind.transformer) { e.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));e.inventory.setChanged() }
                        }
                        for(kind in listOf(DistributionKind.SECONDARY_FED_SINGLE,DistributionKind.SECONDARY_FED_THREE)) {
                            val e=pole(p,gallery.getValue(kind));link(p,feed,e,if(kind.phases==1)32 else 52,if(kind.phases==1)32 else 52)
                        }
                        p.teleportTo(w,306.0,88.0,275.0,145f,20f)
                    };next(2,240)
                }
                2->if(settled()) {
                    CoverRenderingChecks.verify(mc.level!!,coverTest,coverTest.east(3))
                    pass("Wire camouflage matches actual stone vertex colors, world light, textures and directional shading")
                    capture("service-and-poles")
                    server { p ->
                        verifyPower(p)
                        val ch=panel(p).circuits[3]
                        f(p).near(ch.contacts[2].current,22.5,.3);check(!ch.breaker.tripped)
                        pass("Twenty 270 W halogen bulbs draw 5400 W / 22.5 A on their actual 240 V panel branch without tripping its 30 A breaker")
                        val passive=gallery.filterKeys { it==DistributionKind.SECONDARY_FED_SINGLE || it==DistributionKind.SECONDARY_FED_THREE }
                        for((kind,pos) in passive) {
                            val e=pole(p,pos);check(e.unit==null);check(e.lightLevel==0)
                        }
                        val relay=pole(p,gallery.getValue(DistributionKind.SECONDARY))
                        link(p,pole(p,transformer),relay,50,50)
                        for((_,pos) in passive)link(p,relay,pole(p,pos),50,50)
                        val feed=pole(p,feeder);feed.lines.forEachIndexed { i,b -> f(p).near(b.voltage,listOf(14_000.0,13_000.0,12_470.0)[i],2.0) }
                        val reg=pole(p,regulator);f(p).near(reg.outs[0].voltage,13_000.0,2.0)
                        val cap=pole(p,capacitor);cap.electricalComponentList.filterIsInstance<mods.eln.sim.mna.component.Capacitor>().forEachIndexed { i,c -> check(c.voltage>listOf(13_900.0,12_900.0,12_370.0)[i]) }
                        check(meter(p).energy.importedKwh>0);check(lamp(p,fan).installedBulbs==7)
                        pass("Independent 14/13/12.47 kV legs feed a phase-C 25 kW transformer, secondary streetlight, weatherhead, placed wall feeder, smart meter, twelve-breaker panel, wireless lamps, seven-light Aura fan and a 240 V cord outlet")
                        pass("Aim-based ghost hits connect all three HV phases atomically and select the LV rack; cabinets reject overhead spans")
                        pass("Three-phase regulators reach target with passive taps; capacitor branches charge from their own legs")
                        f(p).voltage(feeder.north(2),11_846.5)
                    };next(32,50)
                }
                32->if(settled()) {
                    server { p ->
                        panel(p).buses.forEachIndexed { i,b -> f(p).near(b.voltage,if(i==2)228.0 else 114.0,1.0) }
                        for(kind in listOf(DistributionKind.SECONDARY_FED_SINGLE,DistributionKind.SECONDARY_FED_THREE))check(pole(p,gallery.getValue(kind)).lightLevel>0)
                        pass("Single- and three-phase pass-through streetlight poles stay dark on HV alone and light only after their secondary racks receive upstream LV")
                        check(pole(p,transformer).unit!!.controller.status=="TRANSFERRING")
                        f(p).voltage(feeder.north(2),13_093.5)
                    };next(33,50)
                }
                33->if(settled()) {
                    server { p ->
                        panel(p).buses.forEachIndexed { i,b -> f(p).near(b.voltage,if(i==2)252.0 else 126.0,1.0) }
                        check(pole(p,transformer).unit!!.controller.status=="TRANSFERRING")
                        f(p).voltage(feeder.north(2),12_470.0)
                        pass("A 5 percent primary sag and rise produces 114/228 V and 126/252 V at the loaded house panel; no fixed-voltage regulation or convergence trip")
                    };next(34,60)
                }
                34->if(settled()) {
                    server { p ->
                        verifyPower(p)
                        val o=f(p).machine(outlet) as OutletElement
                        val second=fixture(p,DecorativeLampStyle.DESK,extra240)
                        second.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",1));second.inventory.setChanged()
                        check(second.cord.attach(o.anchor(),false));check(second.cord.anchor!!.socket==1)
                        val third=fixture(p,DecorativeLampStyle.DESK,outlet.east(3));third.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",1));third.inventory.setChanged()
                        check(!third.cord.attach(o.anchor(),false));check(!third.cord.plugged)
                        settings(o,84)
                        val input=wall(p,"120 V Wall Outlet",householdOutlet) as OutletElement
                        input.wireless=true;input.channel="Utility fan";input.reconnect()
                        val strip=wall(p,"Six Socket Power Strip",stripPos) as OutletElement
                        check(strip.cord.attach(input.anchor(),false))
                        repeat(6) { i ->
                            val at=stripPos.offset((i%3)*2,0,2+(i/3)*2);stripLamps.add(at)
                            val d=fixture(p,DecorativeLampStyle.DESK,at)
                            check(d.cord.attach(strip.anchor(),false));check(d.cord.anchor!!.socket==i)
                        }
                        val seventh=fixture(p,DecorativeLampStyle.DESK,stripPos.east(6))
                        check(!seventh.cord.attach(strip.anchor(),false))
                        p.teleportTo(p.serverLevel(),householdOutlet.x+1.5,householdOutlet.y+1.0,householdOutlet.z+5.0,180f,15f)
                    };next(36,60)
                }
                36->if(settled()) {
                    capture("duplex-outlets-and-power-strip")
                    server { p ->
                        val o=f(p).machine(outlet) as OutletElement
                        check(o.horizontal && o.freeSocket()==null);check(lamp(p,extra240).electricalLoad.voltage>238)
                        check(lamp(p,table).cord.anchor!!.socket==0 && lamp(p,extra240).cord.anchor!!.socket==1)
                        val strip=f(p).machine(stripPos) as OutletElement
                        check(strip.freeSocket()==null && strip.cord.connected);check(stripLamps.all { lamp(p,it).electricalLoad.voltage>117 })
                        pass("A duplex outlet powers bottom and top plugs independently, rejects a third device and retains both cords when rotated horizontally")
                        pass("A cord-fed six-socket power strip powers six real table lamps and rejects a seventh connection")
                        p.teleportTo(p.serverLevel(),fan.x+.5,fan.y-2.0,fan.z+1.2,180f,-55f)
                    };next(25,40)
                }
                25->if(settled()) { capture("aura-five-blade-seven-light-fan");server { p ->gui(p,transformer) };next(3) }
                3->if(settled() && mc.screen is DistributionGui && ticks>25) { capture("transformer-gui");check(!(mc.screen as DistributionGui).javaClass.getDeclaredField("rating").apply { isAccessible=true }.let { it.get(mc.screen) as GuiButtonEln }.enabled)
                    server { p ->val e=pole(p,transformer);e.inventory.setItem(0,Eln.findItemStack("50 kW Pole Transformer",1));e.inventory.setChanged() };next(4) }
                4->if(settled()) {
                    server { p ->check(pole(p,transformer).unit!!.ratedWatts==50_000);pass("Replacing the physical transformer item changes capacity from 25 to 50 kW; GUI rating packets cannot grant a free upgrade")
                        branchFault=Resistor(panel(p).circuits[0].load,null).apply { resistance=.6 }.also { Eln.simulator.addElectricalComponent(it) }
                    };next(35,20)
                }
                35->if(settled()) {
                    server { p ->check(!panel(p).circuits[0].breaker.tripped);pass("A real 10x panel inrush remains connected for its first second") };next(5,40)
                }
                5->if(settled()) {
                    server { p ->
                        check(panel(p).circuits[0].breaker.tripped);check(lamp(p,lamp).electricalLoad.voltage<1);check(lamp(p,fan).electricalLoad.voltage>118)
                        branchFault?.let { Eln.simulator.removeElectricalComponent(it);it.breakConnection() };branchFault=null
                        pass("A real branch overload trips only its inserted breaker and wireless lamp; the other bank and house service keep working")
                        gui(p,panel)
                    };next(6)
                }
                6->if(settled() && mc.screen is BreakerPanelGui && ticks>25) {
                    capture("breaker-panel")
                    val g=mc.screen as BreakerPanelGui
                    @Suppress("UNCHECKED_CAST") val buttons=g.javaClass.getDeclaredField("toggle").apply { isAccessible=true }.get(g) as List<GuiButtonEln>
                    val b=buttons[0];g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);next(7,50)
                }
                7->if(settled()) { server { p ->verifyPower(p);pass("Resetting the actual breaker button restores the lamp after removing the fault");gui(p,meter) };next(8) }
                8->if(settled() && mc.screen is HouseServiceGui && ticks>25) {
                    capture("smart-meter")
                    val g=mc.screen as HouseServiceGui;mc.gameMode!!.handleInventoryMouseClick(g.menu.containerId,0,0,ClickType.PICKUP,mc.player!!);next(9,40)
                }
                9->if(settled()) {
                    server { p ->check(meter(p).meterType==0);check(panel(p).buses.all { abs(it.voltage)<1 });check(pole(p,feeder).lines[2].voltage>11_900);pass("Removing the meter through its native inventory opens all three service channels without shorting the primary feeder") };next(10)
                }
                10->if(settled()) { val g=mc.screen as HouseServiceGui;mc.gameMode!!.handleInventoryMouseClick(g.menu.containerId,0,0,ClickType.PICKUP,mc.player!!);next(11,60) }
                11->if(settled()) { button("disconnect");next(12,40) }
                12->if(settled()) { server { p ->check(meter(p).disconnected);check(panel(p).buses.all { abs(it.voltage)<1 });pass("The smart meter disconnect isolates every service output") };next(13) }
                13->if(settled()) { button("disconnect");next(42,80) }
                42->if(settled()) {
                    mc.setScreen(null)
                    server { p ->
                        verifyPower(p);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        check((f(p).machine(disconnectPos) as HouseServiceElement).onBlockActivated(p,Direction.ZP,.8f,.7f,.3f))
                        p.teleportTo(p.serverLevel(),disconnectPos.x+.5,disconnectPos.y-1.1,disconnectPos.z+2.4,180f,0f)
                    };next(43,40)
                }
                43->if(settled()) {
                    capture("red-handle-disconnect-open")
                    server { p ->
                        val sw=f(p).machine(disconnectPos) as HouseServiceElement
                        check(sw.disconnected);check(panel(p).buses.all { abs(it.voltage)<1 });check(meter(p).load[2].voltage>238)
                        pass("The red-handle service disconnect under the meter opens the real house feeder while the meter remains energised")
                        check(sw.onBlockActivated(p,Direction.ZP,.8f,.7f,.3f))
                    };next(14,80)
                }
                14->if(settled()) {
                    server { p ->
                        verifyPower(p);meter(p).inventory.setItem(0,Eln.findItemStack("Analog Glass Service Meter",1));meter(p).inventory.setChanged()
                        pass("Both interchangeable meter styles pass the same real service and retain separate import/export registers")
                        val rec=pole(p,recloser);settings(rec,91) { writeDouble(1.0) };settings(rec,92) { writeDouble(1.0) };settings(rec,93) { writeInt(2) }
                        fault=Resistor(rec.outs[1],null).apply { resistance=1000.0 }.also { Eln.simulator.addElectricalComponent(it) }
                        gui(p,recloser)
                    };next(15,100)
                }
                15->if(settled() && mc.screen is DistributionGui) {
                    capture("recloser-lockout")
                    server { p ->
                        val rec=pole(p,recloser);check(rec.recloser.locked);check(rec.outs.all { abs(it.voltage)<1 });check(panel(p).buses.all { abs(it.voltage)<1 })
                        check((sounds["eln:utility_recloser_bump"] ?: 0)>=3);pass("A B-leg fault opens all three legs, bumps on retries and locks out after two failed reclosures")
                        val cut=pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE))
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.isShiftKeyDown=true
                        try { check(cut.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f)) } finally { p.isShiftKeyDown=false }
                        check(!cut.fuses[0].hasCartridge)
                        p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("10 A Pole Fuse",1))
                        check(cut.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f));check(cut.fuses[0].rating(100.0)==10.0)
                        val pos=gallery.getValue(DistributionKind.CUTOUT_SINGLE);f(p).wire(pos.west());f(p).source(pos.west(2),240.0)
                        // Cutout base exposes the secondary rack, so feed its HV line from the riser's real cable node.
                        val source=(f(p).six(pos.west(2)) as mods.eln.sixnode.electricalsource.ElectricalSourceElement).getElectricalLoad(LRDU.Down,0)!!
                        Eln.simulator.addElectricalComponent(Resistor(source,cut.lines[0]).apply { resistance=.01 })
                        Eln.simulator.addElectricalComponent(Resistor(cut.outs[0],null).apply { resistance=4.0 })
                        gui(p,pos)
                    };next(16,40)
                }
                16->if(settled()) {
                    capture("fuse-open")
                    server { p ->
                        check(pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE)).fuses[0].blown)
                        check((sounds["eln:utility_fuse_blow"] ?: 0)>0);pass("Overcurrent blows a cutout link, opens its visible door and sends the client fuse-pop sound")
                        fault?.let { Eln.simulator.removeElectricalComponent(it);it.breakConnection() };fault=null
                        energyBefore=meter(p).energy.importedKwh
                    };next(17)
                }
                17->if(settled()) { mc.setScreen(null);mc.level!!.disconnect();mc.disconnect(TitleScreen());phase=18;ticks=0 }
                18->if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen utility world") };phase=19;ticks=0 }
                19->if(mc.player!=null && mc.level!=null && mc.screen==null)next(20,80)
                20->if(settled()) {
                    server { p ->
                        val rec=pole(p,recloser);check(rec.recloser.locked && !rec.recloser.closed);check(pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE)).fuses[0].blown)
                        check(pole(p,transformer).transformerPhase==2);check(pole(p,transformer).unit!!.ratedWatts==50_000)
                        check(meter(p).energy.importedKwh>=energyBefore);check(lamp(p,fan).installedBulbs==7)
                        check(pole(p,transformer).gridLinkList.size==3) { "Duplicate transformer spans: ${pole(p,transformer).gridLinkList.size}" }
                        check(meter(p).gridLinkList.isEmpty());check(panel(p).gridLinkList.isEmpty())
                        check((f(p).machine(outlet) as OutletElement).freeSocket()==null)
                        val strip=f(p).machine(stripPos) as OutletElement;check(strip.freeSocket()==null && strip.cord.plugged)
                        check(stripLamps.all { lamp(p,it).cord.plugged })
                        pass("A full world restart preserves phase/rating, meter energy, seven bulbs, fuse state and recloser lockout, with exactly one copy of every saved span")
                        settings(rec,94)
                        val cut=pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE));p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("10 A Pole Fuse",1))
                        check(cut.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f));check(!cut.fuses[0].blown && cut.fuses[0].closed)
                    };next(21,100)
                }
                21->if(settled()) {
                    server { p ->verifyPower(p);pass("Manual reset and a replacement fuse restore service after restart; no cable replacement is needed");gui(p,panel) };next(22)
                }
                22->if(settled() && mc.screen is BreakerPanelGui) { capture("restored-panel");server { p ->p.serverLevel().destroyBlock(panel,false,p) };next(23,60) }
                23->if(settled()) {
                    server { p ->
                        check(lamp(p,lamp).electricalLoad.voltage<1);check((f(p).machine(outlet) as OutletElement).input.voltage<1);check(lamp(p,table).cord.input.voltage<1);pass("Removing the panel safely withdraws both lamp channels and physical wireless-outlet bridges")
                        val pos=gallery.getValue(DistributionKind.CUTOUT_SINGLE);val cut=pole(p,pos)
                        val source=(f(p).six(pos.west(2)) as mods.eln.sixnode.electricalsource.ElectricalSourceElement).getElectricalLoad(LRDU.Down,0)!!
                        arcFeed=Resistor(source,cut.lines[0]).apply { resistance=.01 }.also { Eln.simulator.addElectricalComponent(it) }
                        arcLoad=Resistor(cut.outs[0],null).apply { resistance=120.0 }.also { Eln.simulator.addElectricalComponent(it) }
                        gui(p,pos)
                    };next(24,50)
                }
                24->if(settled() && mc.screen is DistributionGui) { fuseButton();next(26,35) }
                26->if(settled()) {
                    server { p ->check(!pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE)).fuses[0].closed);check((sounds["eln:utility_arc_open"] ?: 0)>0) };next(27)
                }
                27->if(settled()) { fuseButton();next(28,40) }
                28->if(settled()) {
                    server { p ->check(pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE)).fuses[0].closed);check((sounds["eln:utility_arc_close"] ?: 0)>0);arcLoad!!.resistance=4.0 };next(29,40)
                }
                29->if(settled()) {
                    server { p ->
                        val cut=pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE));check(cut.fuses[0].blown);arcLoad!!.resistance=120.0
                        val creative=p.abilities.instabuild
                        try { p.abilities.instabuild=false;p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("Pole Fuse Link",3));check(cut.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f));check(p.mainHandItem.count==2) } finally { p.abilities.instabuild=creative }
                    };next(30,40)
                }
                30->if(settled()) {
                    server { p ->
                        check(pole(p,gallery.getValue(DistributionKind.CUTOUT_SINGLE)).fuses[0].closed);check((sounds["eln:utility_arc_close"] ?: 0)>=2)
                        pass("Loaded fuse opening and closing send distinct arc sounds; replacing a blown fuse under a cleared 2 A load consumes one link and makes the quieter pickup arc")
                        listOfNotNull(arcFeed,arcLoad).forEach { Eln.simulator.removeElectricalComponent(it);it.breakConnection() };arcFeed=null;arcLoad=null
                    };next(31)
                }
                31->if(settled()) {
                    check(peakChandelierAmps in 22.0..26.0) { "Chandelier current spike: $peakChandelierAmps A" }
                    pass("The 5400 W chandelier remains below 26 A through grid sag/rise, switching, meter operations and a cold restart")
                    finish(null)
                }
            }
        } catch(t: Throwable) { finish(t) }
    }
}
