package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.mojang.authlib.GameProfile
import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.vehicle.*
import mods.eln.transparentnode.industrial.IndustrialItems
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Opt-in production physics, seats, storage, battery and wired charger campaign. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object RoadVehicleChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var task: CompletableFuture<*>?=null
    private val passed=ArrayList<String>()
    private lateinit var fixtures: NativeCampaignFixtures
    private lateinit var coupe: RoadVehicleEntity
    private lateinit var charger: VehicleChargerElement
    private var before=0.0
    private val output get()=mc.gameDirectory.toPath().resolve("road-vehicle-checks")
    private fun next() { phase++;ticks=0 }
    private fun server(f: ()->Unit) { task=mc.singleplayerServer!!.submit<Unit>(f) }
    private fun settled(): Boolean { task?.let { if(!it.isDone)return false;it.get();task=null };return true }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();fixtures=NativeCampaignFixtures(w,p)
        for(x in 248..336)for(z in 248..320) { w.setChunkForced(x shr 4,z shr 4,true);w.setBlockAndUpdate(BlockPos(x,79,z),Blocks.STONE.defaultBlockState()) }
        fun driver(i: Int): net.minecraft.server.level.ServerPlayer {
            val profile=GameProfile(UUID(74074,i.toLong()),"RoadCheck$i")
            return net.minecraft.server.level.ServerPlayer(w.server,w,profile,net.minecraft.server.level.ClientInformation.createDefault()).apply { connection=FakePlayerFactory.get(w,profile).connection }
        }
        fun vehicle(kind: RoadVehicleKind,x: Double,z: Double): RoadVehicleEntity {
            val e=(if(kind==RoadVehicleKind.CART)RoadVehicles.cart else RoadVehicles.coupe).get().create(w)!!;e.moveTo(x,80.0,z,0f,0f);check(w.addFreshEntity(e))
            for(i in 0 until kind.packs) { val pack=Eln.findItemStack(IndustrialItems.PACK,1);RoadVehicles.battery(pack)!!.setEnergy(pack,1000000.0);e.items.setItem(i,pack) };return e
        }
        val cart=vehicle(RoadVehicleKind.CART,256.0,253.0)
        val riders=(1..5).map(::driver);check(riders.take(4).all { it.startRiding(cart) });check(!riders[4].startRiding(cart))
        val pack=cart.items.getItem(0);val battery=RoadVehicles.battery(pack)!!;val start=battery.getEnergy(pack)
        for(x in 252..260)for(z in 264..267)w.setBlockAndUpdate(BlockPos(x,80,z),Blocks.STONE.defaultBlockState())
        for(x in 252..260)for(y in 80..82)w.setBlockAndUpdate(BlockPos(x,y,268),Blocks.STONE.defaultBlockState())
        var high=cart.y
        repeat(180) { cart.controls(riders[0],1.0,0.0);cart.tick();high=maxOf(high,cart.y) }
        check(high in 80.99..81.01) { "One-block step failed: $high at ${cart.position()}" };check(cart.z<268 && cart.z>265);check(battery.getEnergy(pack)<start)
        passed+="Four cart seats; fifth rider rejected; server-driven cart climbs one block, rejects a two-block step, spends traction energy"
        cart.ejectPassengers();cart.items.setItem(6,net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7))
        val copy=RoadVehicles.cart.get().create(w)!!;copy.load(cart.saveWithoutId(CompoundTag()));check(copy.items.getItem(6).count==7);check(RoadVehicles.battery(copy.items.getItem(0))!!.getEnergy(copy.items.getItem(0))==battery.getEnergy(pack));cart.discard()
        passed+="Storage and installed pack charge survive entity save/load"
        val hazard=vehicle(RoadVehicleKind.CART,300.0,280.0);val d=driver(6);check(d.startRiding(hazard))
        repeat(75) { hazard.controls(d,1.0,0.0);hazard.tick() };check(hazard.isAlive)
        repeat(20) { if(hazard.isAlive) { hazard.controls(d,1.0,1.0);hazard.tick() } };check(!hazard.isAlive);check(d.remainingFireTicks>0)
        passed+="Straight full-speed cart remains intact; sustained fast sharp corner explodes and ignites its rider"
        val fast=vehicle(RoadVehicleKind.COUPE,300.0,280.0);val fastDriver=driver(10);check(fastDriver.startRiding(fast))
        repeat(240) {
            val e=(0..5).map { i->RoadVehicles.battery(fast.items.getItem(i))!!.getEnergy(fast.items.getItem(i)) }
            fast.controls(fastDriver,1.0,0.0);fast.tick()
            check((0..5).all { i->e[i]-RoadVehicles.battery(fast.items.getItem(i))!!.getEnergy(fast.items.getItem(i))<=2500.00001 })
            fast.setPos(300.0,80.0,280.0);fast.deltaMovement=net.minecraft.world.phys.Vec3.ZERO
        }
        check(fast.speed>40) { "Coupe top speed ${fast.speed}" };fast.ejectPassengers();fast.discard()
        passed+="Coupe reaches over 144 km/h in the real server physics; every pack stays within its 50 kW discharge rating"
        coupe=vehicle(RoadVehicleKind.COUPE,288.0,256.0);check(driver(7).startRiding(coupe));check(driver(8).startRiding(coupe));check(!driver(9).startRiding(coupe));coupe.ejectPassengers()
        for(i in 0..5)RoadVehicles.battery(coupe.items.getItem(i))!!.setEnergy(coupe.items.getItem(i),0.0)
        check(coupe.acceptCharge(1200.0,.05) in 1127.999..1128.001);check((0..5).all { RoadVehicles.battery(coupe.items.getItem(it))!!.getEnergy(coupe.items.getItem(it)) in 187.999..188.001 })
        before=(0..5).sumOf { RoadVehicles.battery(coupe.items.getItem(it))!!.getEnergy(coupe.items.getItem(it)) }
        val at=BlockPos(284,80,256);charger=fixtures.placeMachine(Eln.findItemStack(VehicleChargerDescriptor.TITLE,1),at) as VehicleChargerElement;charger.front=Direction.ZP;charger.reconnect()
        fixtures.wire(at.west());fixtures.source(at.west(2),240.0);check(charger.attach(coupe))
        p.teleportTo(w,291.0,83.0,262.0,140f,12f);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3(286.5,80.7,256.5))
        passed+="Two coupe seats; six battery bays share actual incoming energy at 94% efficiency"
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output);Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("ROAD_VEHICLES FAIL",error) else Eln.logger.info("ROAD_VEHICLES COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.roadVehicleChecks") || done)return
        try {
            mc.options.hideGui=phase<4
            ticks++;check(++total<2500) { "Vehicle campaign timeout phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="road-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(74074L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);next()
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { server(::setup);next() }
                2->if(settled() && ticks>120) {
                    server {
                        check(charger.watts in 23900.0..24001.0) { "Input ${charger.input.voltage} V, ${charger.input.current} A, ${charger.watts} W" };check(kotlin.math.abs(charger.input.current)<=100.01)
                        check((0..5).sumOf { RoadVehicles.battery(coupe.items.getItem(it))!!.getEnergy(coupe.items.getItem(it)) }>before+1000)
                        passed+="Wired 240 V charger draws 100 A / 24 kW and charges all six installed packs"
                        charger.unplug();before=(0..5).sumOf { RoadVehicles.battery(coupe.items.getItem(it))!!.getEnergy(coupe.items.getItem(it)) }
                    };next()
                }
                3->if(settled() && ticks>180) {
                    check(mods.eln.client.terrain.TerrainModels.hasBakedBody((mc.level!!.getBlockEntity(BlockPos(284,80,256)) as mods.eln.node.transparent.TransparentNodeEntity).elementRender!!))
                    Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("coupe-and-charger.png")) }
                    server { check(charger.watts<1);check(!coupe.plugged);check((0..5).sumOf { RoadVehicles.battery(coupe.items.getItem(it))!!.getEnergy(coupe.items.getItem(it)) }==before)
                        coupe.items.setItem(6,net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7))
                        fixtures.player.teleportTo(fixtures.world,288.0,80.0,259.0,180f,0f)
                        listOf(RoadVehicleKind.CART.title,RoadVehicleKind.COUPE.title,VehicleChargerDescriptor.TITLE,VehicleLeadItem.TITLE,IndustrialItems.PACK).forEachIndexed { i,name->fixtures.player.inventory.setItem(i,Eln.findItemStack(name,1)) }
                        fixtures.player.inventory.setChanged()
                        passed+="Disconnect removes charging draw and releases vehicle; parked charge remains unchanged" };next()
                }
                4->if(settled()) {
                    mc.options.keyShift.setDown(true)
                    if(ticks>30) {
                        val car=mc.level!!.getEntity(coupe.id) ?: error("Coupe not tracked")
                        mc.gameMode!!.interact(mc.player!!,car,InteractionHand.MAIN_HAND);next()
                    }
                }
                5->if(ticks>40 && mc.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
                    val menu=(mc.screen as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>).menu as? VehicleMenu ?: return
                    check((0..5).all { !menu.slots[it].item.isEmpty });check(menu.slots[6].item.count==7)
                    for(kind in RoadVehicleKind.entries)check(mc.itemRenderer.getModel(Eln.findItemStack(kind.title,1),mc.level,mc.player,0).isCustomRenderer)
                    Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("vehicle-storage-and-inventory.png")) }
                    passed+="Real sneak-right-click packet opens the six-pack/nine-storage menu; both vehicle inventory icons use their 3D meshes"
                    mc.options.keyShift.setDown(false);finish(null)
                }
            }
        }catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
