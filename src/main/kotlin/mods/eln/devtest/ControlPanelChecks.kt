package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.client.terrain.TerrainModels
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.industrial.*
import mods.eln.transparentnode.renewable.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in native acceptance: production packets, passive wiring, cached bodies and live fronts. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object ControlPanelChecks {
    private val mc get()=Minecraft.getInstance()
    private val positions=List(12) { BlockPos(256+it*8,80,256) }
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work:CompletableFuture<*>?=null
    private lateinit var fixtures:NativeCampaignFixtures
    private lateinit var linkedGenerator:mods.eln.transparentnode.standby.StandbyGeneratorElement
    private val output get()=mc.gameDirectory.toPath().resolve("control-panel-checks")
    private val passed=mutableListOf<String>()
    private fun server(job:()->Unit) { work=mc.singleplayerServer!!.submit<Unit> { job() } }
    private fun settled():Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return true }
    private fun next() { phase++;ticks=0 }
    private fun solar(i:Int)=(fixtures.machine(positions[i]) as SolarElement).unit
    private fun industrial(i:Int)=fixtures.machine(positions[i]) as IndustrialElement
    private fun render(i:Int)=(mc.level!!.getBlockEntity(positions[i]) as TransparentNodeEntity).elementRender!!
    private fun ready()=positions.all { (mc.level!!.getBlockEntity(it) as? TransparentNodeEntity)?.elementRender?.let(TerrainModels::hasBakedBody)==true }
    private fun shot(name:String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun view(i:Int) {
        val p=fixtures.player;val at=positions[i]
        p.closeContainer();p.teleportTo(fixtures.world,at.x+.5,at.y-.4,at.z+2.2,180f,0f)
        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(at.x+.5,at.y+.5,at.z+.5))
    }
    private fun gui(i:Int) { val at=positions[i];GuiHandler.open(fixtures.player,GuiHandler.nodeBaseOpen+Direction.ZP.int,fixtures.world,at.x,at.y,at.z) }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();fixtures=NativeCampaignFixtures(w,p)
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server)
        for((i,name) in listOf("MPPT Solar Controller","Hybrid Solar Inverter",IndustrialKind.SHEDDER.title,IndustrialKind.RELAY.title).withIndex()) {
            val at=positions[i]
            for(x in -3..3)for(z in -3..3) { val pos=at.offset(x,-1,z);w.setChunkForced(pos.x shr 4,pos.z shr 4,true);w.setBlockAndUpdate(pos,Blocks.SMOOTH_STONE.defaultBlockState()) }
            fixtures.placeMachine(Eln.findItemStack(name,1),at).apply { front=Direction.ZP;reconnect() }
            fixtures.wire(at.west());fixtures.source(at.west(2),if(i<2)850.0 else if(i==2)240.0 else 480.0)
            fixtures.wire(at.east())
            if(i==0)fixtures.source(at.east(2),125.0) else { fixtures.load(at.east(2),if(i==1)28.8 else if(i==2)115.2 else 24.0);fixtures.ground(at.east(3)) }
            if(i<2)fixtures.ground(at.north())
            if(i==1) { fixtures.wire(at.south());fixtures.source(at.south(2),14000.0) }
        }
        for(i in 4..5) {
            val at=positions[i]
            for(x in -3..3)for(y in -2..3) { val wall=at.offset(x,y,-1);w.setChunkForced(wall.x shr 4,wall.z shr 4,true);w.setBlockAndUpdate(wall,Blocks.SMOOTH_STONE.defaultBlockState()) }
            val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<RenewableDescriptor>().single { it.wallMounted && it.kind==if(i==4)RenewableKind.MPPT else RenewableKind.HYBRID }
            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(),p,w,at,net.minecraft.core.Direction.SOUTH))
            fun six(name:String,pos:BlockPos):mods.eln.node.six.SixNodeElement { check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,w,pos,net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f));return (fixtures.node(pos) as mods.eln.node.six.SixNode).getElement(Direction.ZN)!! }
            fun wire(pos:BlockPos) { check(Eln.sixNodeItem.placeBlockAt(fixtures.spool(),p,w,pos,net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f)) }
            (six("Electrical Source",at.west(2)) as mods.eln.sixnode.electricalsource.ElectricalSourceElement).readConfigTool(net.minecraft.nbt.CompoundTag().apply { putDouble("voltage",850.0) },p);wire(at.west())
            six("Ground Cable",at.below());wire(at.east())
            val load=six("Creative Power Resistor",at.east(2)) as mods.eln.sixnode.CreativePowerResistorElement
            load.front=mods.eln.misc.LRDU.entries.first { Direction.ZN.applyLRDU(it.right())==Direction.XP };load.reconnect()
            load.readConfigTool(net.minecraft.nbt.CompoundTag().apply { putDouble("resistance",28.8) },p);six("Ground Cable",at.east(3))
        }
        val padAt=positions[6]
        for(x in -3..3)for(z in -2..2) { val at=padAt.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
        val pad=fixtures.placeMachine(Eln.findItemStack(IndustrialKind.PAD.title,1),padAt) as IndustrialElement
        pad.front=Direction.ZP;pad.reconnect()
        pad.inventory.setItem(0,Eln.findItemStack(mods.eln.utility.PoleHardware.transformerNames[2],1))
        pad.inventory.setItem(1,Eln.findItemStack(mods.eln.utility.PoleHardware.fuseNames.last(),1));pad.inventoryChange(pad.inventory)
        fixtures.wire(padAt.west());fixtures.source(padAt.west(2),12470.0)
        fixtures.wire(padAt.east());fixtures.load(padAt.east(2),240.0/145.0);fixtures.ground(padAt.east(3))
        for((index,front) in listOf(Direction.ZN,Direction.ZP,Direction.XN,Direction.XP).withIndex()) {
            val at=positions[7+index];val right=front.right().toFacing();val rear=front.inverse.toFacing()
            for(i in -1..2)for(y in -1..1) {
                val wall=at.relative(right,i).relative(rear).offset(0,y,0)
                w.setChunkForced(wall.x shr 4,wall.z shr 4,true);w.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState())
            }
            val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<mods.eln.utility.HouseServiceDescriptor>().single { it.wide }
            check(Eln.transparentNodeItem.placeBlockAt(d.newItemStack(),p,w,at,front.toFacing()))
            val meter=fixtures.machine(at) as mods.eln.utility.HouseServiceElement;check(meter.front==front)
            val ghost=at.relative(right)
            // Simulate a saved meter's old cube placeholder, then migrate it in place.
            if(index==0) { w.setBlockAndUpdate(ghost,Eln.ghostBlock.stateForMeta(mods.eln.ghost.GhostBlock.tCube));mods.eln.utility.WideMeterWiring.syncGhostShape(meter) }
            val state=w.getBlockState(ghost);check(state.getValue(mods.eln.ghost.GhostBlock.SHAPE)==mods.eln.ghost.GhostBlock.wallServiceMeta(front))
            check(!state.getCollisionShape(w,ghost).bounds().contains(.5,.5,.5))
        }
        val atsAt=positions[11]
        for(x in -4..3)for(z in -4..2) { val at=atsAt.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
        val ats=fixtures.placeMachine(Eln.findItemStack("Automatic Transfer Switch",1),atsAt) as mods.eln.transparentnode.standby.AtsElement
        ats.front=Direction.ZP;ats.reconnect();ats.unit.channel="Native backup"
        linkedGenerator=fixtures.placeMachine(Eln.findItemStack("Standby Gasoline Generator",1),atsAt.north(3).west(3)) as mods.eln.transparentnode.standby.StandbyGeneratorElement
        linkedGenerator.front=Direction.ZP;linkedGenerator.reconnect();linkedGenerator.unit.channel="Native backup"
        linkedGenerator.unit.fuel.tank.fill(net.neoforged.neoforge.fluids.FluidStack(StandbyTestFluids.fluid(0),20000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
        fixtures.wire(atsAt.west());fixtures.source(atsAt.west(2),0.0)
        fixtures.wire(atsAt.east());fixtures.load(atsAt.east(2),240.0*240/20000);fixtures.ground(atsAt.east(3))
        for(at in listOf(atsAt.north(),atsAt.north(2),atsAt.north(3),atsAt.north(3).west(),atsAt.north(3).west(2)))fixtures.wire(at)
        industrial(2).applySettings(doubleArrayOf(240.0,300.0))
        industrial(3).applySettings(doubleArrayOf(480.0,10.0,.8,1.2,.5,0.0))
        view(0)
    }
    private fun finish(error:Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.logger.info("CONTROL_PANEL COMPLETE") else Eln.logger.error("CONTROL_PANEL FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.controlPanelChecks") || done)return
        try {
            mc.options.hideGui=mc.screen==null
            ticks++;check(++total<4000) { "Panel check timeout phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.options.fov().set(45)
                    val name="controls-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(73073L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);next()
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { server { setup() };next() }
                2->if(settled() && ticks>100 && ready()) { server { check(solar(0).status=="BACKFEED") { "${solar(0).status}: ${solar(0).output.voltage}, ${solar(0).diagnostic}" };gui(0) };next() }
                3->if(settled() && ticks>25 && mc.screen is SolarGui) {
                    val g=mc.screen as SolarGui
                    val b=SolarGui::class.java.getDeclaredField("feed").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);next()
                }
                4->if(ticks>100) {
                    check((render(0) as RenewableRender).display.gridFeed)
                    check((render(0) as RenewableRender).display.feedWatts>1000)
                    shot("mppt-gui");server { check(solar(0).feedWatts>1000);check(solar(0).status=="EXPORTING");view(0) };passed+="Real GUI packet enables grid feed through the existing cabled output";next()
                }
                5->if(settled() && ticks>45) { shot("mppt-feeding");server { solar(0).enabled=false };next() }
                6->if(settled() && ticks>45) {
                    check(!(render(0) as RenewableRender).display.enabled);check(TerrainModels.hasBakedBody(render(0)))
                    shot("mppt-off");passed+="Live solar telemetry switches off while its cabinet remains baked"
                    server { view(1) };next()
                }
                7->if(settled() && ticks>45) { shot("hybrid");server { view(2) };next() }
                8->if(settled() && ticks>45) {
                    val d=render(2) as IndustrialRender;check(d.closed.contentEquals(booleanArrayOf(true,true,false,false)))
                    check(d.shedStatus=="HEADROOM");check(d.nominalDemand[2]>450)
                    shot("shedder");passed+="Cabled P3 overload sheds P4 then P3 and holds the oversized branch open"
                    server { gui(2) };next()
                }
                9->if(settled() && ticks>30 && mc.screen is IndustrialGui) { shot("shedder-gui");server { view(3) };next() }
                10->if(settled() && ticks>45) {
                    check((render(3) as IndustrialRender).trip);shot("relay-trip");server { gui(3) };next()
                }
                11->if(settled() && ticks>30 && mc.screen is IndustrialGui) { shot("relay-gui");server { view(3) };next() }
                12->if(settled() && ticks>30) { work=mc.reloadResourcePacks();next() }
                13->if(settled() && mc.overlay==null && ticks>100 && ready()) { shot("relay-after-reload");passed+="Live relay trip indicators and all six static cabinets survive resource reload";server { view(4) };next() }
                14->if(settled() && ticks>45) { shot("wall-mppt");server { view(5) };next() }
                15->if(settled() && ticks>45) {
                    shot("wall-hybrid");server {
                        val pad=industrial(6);check(pad.tap.current in 2.7..3.0);check(!pad.protection.tripped)
                        check(!mods.eln.utility.PoleHardware.fuse(pad.inventory.getItem(1))!!.spent);view(6);gui(6)
                    };next()
                }
                16->if(settled() && ticks>35 && mc.screen is IndustrialGui) {
                    val d=render(6) as IndustrialRender;check(d.primaryAmps in 2.7..3.0);check(d.fuseAmps==200.0);check(d.transformerWatts==50000.0)
                    shot("pad-primary-fuse");passed+="145 A nominal 240 V load runs on a 50 kW core; a 200 A primary fuse sees about 2.8 A and stays intact"
                    server { industrial(6).inventory.setItem(0,Eln.findItemStack(mods.eln.utility.PoleHardware.transformerNames[1],1));industrial(6).inventoryChange(industrial(6).inventory) };next()
                }
                17->if(settled() && ticks>80) {
                    val d=render(6) as IndustrialRender;check(d.trip && d.stats[10].toInt()==7);check(d.fuseAmps==200.0)
                    shot("pad-overload-fuse-intact");passed+="Changing to an undersized 25 kW core trips capacity protection without blowing the 200 A primary fuse"
                    for(i in 7..10) {
                        val at=positions[i];val front=(render(i) as mods.eln.utility.HouseServiceRender).front!!
                        val ghost=at.relative(front.right().toFacing());val state=mc.level!!.getBlockState(ghost)
                        val collision=state.getCollisionShape(mc.level!!,ghost).bounds()
                        check(!collision.contains(.5,.5,.5));check(collision==state.getShape(mc.level!!,ghost).bounds())
                    }
                    passed+="All four wide-meter rotations have shallow client/server collision and selection, including a migrated old cube ghost"
                    server { check(linkedGenerator.unit.fault=="OVERLOAD");fixtures.load(positions[11].east(2),240.0*240/1000);view(11);gui(11) };next()
                }
                18->if(settled() && ticks>35 && mc.screen is mods.eln.transparentnode.standby.BackupGui) {
                    val g=mc.screen as mods.eln.transparentnode.standby.BackupGui
                    val b=g.javaClass.getDeclaredField("reset").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);next()
                }
                19->if(ticks>160) {
                    val d=(render(11) as mods.eln.transparentnode.standby.BackupRender).display
                    check(d.resetResult=="RESET" && d.resetCount==1);check(d.watts>990)
                    shot("ats-generator-reset");passed+="The real ATS GUI resets its linked faulted generator and restores the corrected load"
                    val g=mc.screen as mods.eln.transparentnode.standby.BackupGui
                    val b=g.javaClass.getDeclaredField("reset").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);next()
                }
                20->if(ticks>50) {
                    val d=(render(11) as mods.eln.transparentnode.standby.BackupRender).display
                    check(d.resetResult=="READY" && d.resetCount==0);check(d.watts>990)
                    passed+="Reset leaves an already healthy linked generator supplying the load";finish(null)
                }
            }
        } catch(t:Throwable) { finish(t.cause ?: t) }
    }
}
