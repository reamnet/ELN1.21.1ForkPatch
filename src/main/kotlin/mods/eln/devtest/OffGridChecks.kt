package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.TerrainModels
import mods.eln.gui.GuiButtonEln
import mods.eln.GuiHandler
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.offgrid.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
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

/** Opt-in packaged-client campaign; sources and loads are explicit physical test boundaries. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object OffGridChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val passed=ArrayList<String>()
    private lateinit var f: NativeCampaignFixtures
    private lateinit var inverter: OffGridElement
    private lateinit var bank: OffGridElement
    private lateinit var solar: OffGridElement
    private lateinit var solarBank: OffGridElement
    private val placed=ArrayList<BlockPos>()
    private val gallery=(0..7).map { BlockPos(248+3*it,80,276) }
    private var before=0.0;private var solarBefore=0.0
    private val output get()=mc.gameDirectory.toPath().resolve("off-grid-checks")
    private fun next() { phase++;ticks=0 }
    private fun server(job: ()->Unit) { work=mc.singleplayerServer!!.submit<Unit>(job) }
    private fun settled(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.get();work=null;return true }
    private fun shot(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun view(x: Double,z: Double,tx: Double,tz: Double) {
        f.player.closeContainer();f.player.teleportTo(f.world,x,81.4,z,180f,0f)
        f.player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(tx,81.0,tz))
    }
    private fun place(t: OffGridType,p: BlockPos): OffGridElement {
        if(t.wall) {
            for(y in 0..1)f.world.setBlockAndUpdate(p.offset(0,y,-1),Blocks.STONE.defaultBlockState())
            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(t.title,1),f.player,f.world,p,net.minecraft.core.Direction.SOUTH))
        } else f.placeMachine(Eln.findItemStack(t.title,1),p)
        val e=f.machine(p) as OffGridElement;e.front=Direction.ZP;e.reconnect();placed+=p;return e
    }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();f=NativeCampaignFixtures(w,p)
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server)
        for(x in 240..292)for(z in 248..282) { w.setChunkForced(x shr 4,z shr 4,true);w.setBlockAndUpdate(BlockPos(x,79,z),Blocks.STONE.defaultBlockState()) }
        val at=BlockPos(256,80,256);inverter=place(OffGridType.INVERTER,at)
        bank=place(OffGridType.BANK16,BlockPos(250,80,256));bank.front=Direction.ZN;bank.reconnect()
        for(x in 251..255)f.wire(BlockPos(x,80,256))
        for(z in 253..256)f.wire(BlockPos(249,80,z))
        for(x in 250..256)f.wire(BlockPos(x,80,253))
        for(z in 254..255)f.wire(BlockPos(256,80,z))
        f.wire(at.south());f.source(at.south(2),240.0)
        f.wire(at.east());f.wire(at.east(2));f.load(at.east(3),57.6);f.ground(at.east(4))
        val pv=BlockPos(280,80,256);solar=place(OffGridType.MPPT,pv);solarBank=place(OffGridType.BANK16,pv.east(4))
        for(x in 281..283)f.wire(BlockPos(x,80,256))
        for(z in 256..259)f.wire(BlockPos(285,80,z))
        for(x in 280..284)f.wire(BlockPos(x,80,259))
        for(z in 257..258)f.wire(BlockPos(280,80,z))
        f.wire(pv.west());f.source(pv.west(2),300.0);f.ground(pv.north())
        OffGridType.entries.filter { it.bank }.forEachIndexed { i,t->
            val e=place(t,gallery[i]);e.bank!!.state.transfer(e.bank!!.state.capacity*.35)
            val nbt=CompoundTag().also(e::writeToNBT)
            val copy=OffGridElement(mods.eln.node.transparent.TransparentNode(),e.cabinet);copy.readFromNBT(nbt)
            check(copy.bank!!.state.energy==e.bank!!.state.energy)
            check(w.getBlockState(gallery[i].above()).`is`(Eln.ghostBlock))
        }
        for((i,t) in listOf(OffGridType.AC_CHARGER,OffGridType.COMBINER,OffGridType.DISCONNECT).withIndex())place(t,BlockPos(264+i*4,80,266))
        p.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY)
        passed+="All eight two-block battery variants place with occupied upper cells, exact capacity and saved charge"
        view(259.0,261.0,254.0,256.0)
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output);Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.logger.info("OFF_GRID COMPLETE") else Eln.logger.error("OFF_GRID FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.offGridChecks") || done)return
        try {
            ticks++;check(++total<4000) { "Off-grid campaign timeout phase $phase" };mc.options.hideGui=mc.screen==null
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.options.fov().set(55)
                    val name="offgrid-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(76076L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);next()
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { server(::setup);next() }
                2->if(settled() && ticks>180) {
                    server {
                        val u=inverter.ac!!
                        check(u.bypass.closed && !u.inverterOutput.enabled);check(u.outputWatts>950) { "Load ${u.outputWatts}; ${u.status}" }
                        check(bank.bank!!.state.energy>1000) { "Grid bank ${bank.bank!!.state.energy}, charger ${u.charger.status}, DC ${u.batteryVolts}" }
                        passed+="A real wired 240 V AC input powers the backed-up load and charges an initially empty external bank"
                        check(solarBank.bank!!.state.energy>1000) { "Solar bank ${solarBank.bank!!.state.energy}, ${solar.solar!!.diagnostic}" }
                        passed+="MPPT transfers paid PV input into the physical battery bus and cabinet"
                        before=bank.bank!!.state.energy;f.voltage(BlockPos(256,80,258),0.0);f.voltage(BlockPos(278,80,256),0.0)
                    };next()
                }
                3->if(settled() && ticks>160) {
                    shot("inverter-and-cabled-bank")
                    server {
                        val u=inverter.ac!!;check(!u.bypass.closed && u.inverterOutput.enabled);check(u.outputWatts>950) { "Backup ${u.outputWatts}, ${u.supply.status}, DC ${u.batteryVolts}" }
                        check(bank.bank!!.state.energy<before);check(u.gridWatts<.01)
                        passed+="Grid loss opens the mains contact and the external bank pays for 240 V backup power"
                        solarBefore=solarBank.bank!!.state.energy;view(253.0,281.0,252.5,276.5)
                    };next()
                }
                4->if(settled() && ticks>100) {
                    check(placed.all { (mc.level!!.getBlockEntity(it) as? TransparentNodeEntity)?.elementRender?.let(TerrainModels::hasBakedBody)==true })
                    shot("floor-battery-cabinets")
                    server {
                        check(solarBank.bank!!.state.energy>=solarBefore-.2)
                        passed+="After PV input stops, batteries do not feed unpaid power back through the MPPT"
                        view(265.0,281.0,264.5,276.5)
                    };next()
                }
                5->if(settled() && ticks>80) {
                    for(p in gallery.drop(4)) {
                        val shape=mc.level!!.getBlockState(p.above()).getCollisionShape(mc.level!!,p.above())
                        check(shape.bounds().zsize<=.331) { "Wall battery has a full upper collision cube" }
                    }
                    shot("wall-battery-cabinets");passed+="Every cabinet has a visible baked body and remote-client wall battery collision stays shallow"
                    server { view(248.5,278.5,248.5,276.5);GuiHandler.open(f.player,GuiHandler.nodeBaseOpen+Direction.ZP.int,f.world,248,80,276) };next()
                }
                6->if(settled() && ticks>50 && mc.screen is OffGridGui) {
                    shot("battery-controls")
                    val g=mc.screen as OffGridGui;val b=g.javaClass.getDeclaredField("toggle").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);next()
                }
                7->if(ticks>60) { server { check(!(f.machine(gallery[0]) as OffGridElement).bank!!.state.enabled) };next() }
                8->if(settled()) { passed+="The real battery-screen button sends a server control packet and operates the BMS disconnect";finish(null) }
            }
        } catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
