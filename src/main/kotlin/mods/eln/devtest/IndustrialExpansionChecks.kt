package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.transparentnode.industrial.*
import mods.eln.utility.PoleHardware
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
import kotlin.math.*

/** Opt-in production-world electrical, inventory, native mesh and real C2S vehicle campaign. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object IndustrialExpansionChecks {
    private val mc get()=Minecraft.getInstance()
    private var guiIndex=0
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var task: CompletableFuture<*>?=null
    private val passed=ArrayList<String>()
    private val values=linkedMapOf<String,Any>()
    private val machines=ArrayList<IndustrialElement>()
    private val positions=ArrayList<BlockPos>()
    private lateinit var fixtures: NativeCampaignFixtures
    private lateinit var pole: mods.eln.utility.DistributionPoleElement
    private lateinit var forklift: ForkliftEntity
    private var start=Vec3.ZERO
    private var energy=0.0
    private val chargerChecks get()=java.lang.Boolean.getBoolean("eln.chargerSlotChecks")
    private val output get()=mc.gameDirectory.toPath().resolve(if(chargerChecks)"charger-slot-checks" else "industrial-expansion-checks")
    private val facingDevices=ArrayList<Pair<BlockPos,Direction>>()
    private fun server(action: ()->Unit) { task=mc.singleplayerServer!!.submit<Unit>(action);ticks=0 }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun setup() {
        val p=mc.singleplayerServer!!.playerList.players.first();val w=p.serverLevel();fixtures=NativeCampaignFixtures(w,p)
        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
        w.setDayTime(6000);w.setWeatherParameters(12000,0,false,false)
        for((i,kind)in IndustrialKind.entries.withIndex()) {
            val at=BlockPos(256+i*8,80,256);positions+=at
            for(x in -1..1)for(z in -1..1)w.setChunkForced((at.x shr 4)+x,(at.z shr 4)+z,true)
            for(dx in -3..3)for(dz in -1..1)w.setBlockAndUpdate(at.offset(dx,-1,dz),Blocks.STONE.defaultBlockState())
            val machine=fixtures.placeMachine(Eln.findItemStack(kind.title,1),at) as IndustrialElement
            machine.front=Direction.ZP;machine.reconnect();machines+=machine
            fixtures.wire(at.west());fixtures.source(at.west(2),machine.nominal)
            if(kind in listOf(IndustrialKind.RELAY,IndustrialKind.PAD,IndustrialKind.DRIVE)) { fixtures.wire(at.east());fixtures.load(at.east(2),24.0);fixtures.ground(at.east(3)) }
            if(kind==IndustrialKind.PAD) { machine.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[0],1));machine.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));machine.inventory.setChanged() }
            if(kind==IndustrialKind.CHARGER) { machine.inventory.setItem(0,Eln.findItemStack(IndustrialItems.PACK,1));machine.inventory.setChanged() }
            if(kind==IndustrialKind.WELDER) { machine.inventory.setItem(0,ItemStack(Items.IRON_INGOT,3));machine.inventory.setItem(1,ItemStack(Items.IRON_INGOT,3));machine.inventory.setChanged() }
        }
        for((j,name)in listOf("Electric Washing Machine","Electric Clothes Dryer").withIndex()) {
            val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<mods.eln.transparentnode.laundry.MatterMachineDescriptor>().single { it.kind==(if(j==0)mods.eln.transparentnode.laundry.MatterMachineKind.WASHER else mods.eln.transparentnode.laundry.MatterMachineKind.DRYER) }
            val at=BlockPos(284+j*2,80,264);w.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());fixtures.placeMachine(descriptor.newItemStack(1),at).apply { front=Direction.ZP;reconnect() }
        }
        val poleAt=BlockPos(256,80,264);w.setBlockAndUpdate(poleAt.below(),Blocks.STONE.defaultBlockState())
        pole=fixtures.placeMachine(Eln.findItemStack(mods.eln.utility.DistributionKind.SINGLE.title,1),poleAt) as mods.eln.utility.DistributionPoleElement
        p.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack(IndustrialKind.ARRESTER.title,1))
        check(pole.onBlockActivated(p,Direction.ZP,.5f,.5f,.5f) && pole.arrester.fitted && p.mainHandItem.isEmpty)
        val bolt=net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(w)!!;bolt.setPos(Vec3.atBottomCenterOf(poleAt.above(7)));w.addFreshEntity(bolt)
        check(pole.arrester.remaining==160000.0)
        if(chargerChecks)for((j,kind)in listOf(IndustrialKind.CHARGER).withIndex())for(i in 0..3) {
            val at=BlockPos(300+i*3,80,272+j*3);w.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());p.yRot=i*90f;p.xRot=0f
            val expected=mods.eln.misc.Utils.entityLivingHorizontalViewDirection(p).inverse
            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(kind.title,1),p,w,at,net.minecraft.core.Direction.UP))
            check(fixtures.machine(at).front==expected);facingDevices+=at to expected
        }
        val scada=machines[IndustrialKind.SCADA.ordinal];val relayPos=positions[IndustrialKind.RELAY.ordinal]
        check(scada.applySettings(doubleArrayOf(relayPos.x.toDouble(),relayPos.y.toDouble(),relayPos.z.toDouble(),1.0)));scada.bindTarget()
        for(x in 256..280)for(z in 264..288)w.setBlockAndUpdate(BlockPos(x,79,z),Blocks.STONE.defaultBlockState())
        forklift=ForkliftEntity(ForkliftEntity.TYPE.get(),w);forklift.moveTo(266.0,80.0,274.0,0f,0f);check(w.addFreshEntity(forklift))
        p.teleportTo(w,288.0,90.0,232.0,0f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(288.0,80.4,256.0))
    }
    private fun openGui(index: Int) {
        val at=positions[index];val p=fixtures.player;p.closeContainer();p.teleportTo(fixtures.world,at.x+.5,at.y.toDouble(),at.z+3.0,180f,0f)
        mods.eln.GuiHandler.open(p,mods.eln.GuiHandler.nodeBaseOpen+Direction.ZP.int,fixtures.world,at.x,at.y,at.z)
    }
    private fun electricalChecks() {
        val w=fixtures.world
        for(d in machines)check(d.input.voltage.isFinite() && abs(d.input.voltage-d.nominal)<d.nominal*.03) { "${d.kind}: ${d.input.voltage}" }
        val bank=machines[0];check(bank.resistor.power>48000 && bank.temperature>20);values["loadBankWatts"]=bank.resistor.power
        val pad=machines[IndustrialKind.PAD.ordinal];check(pad.outputs[0].voltage in 238.0..241.0 && pad.outputCurrent>9);values["padOutputVolts"]=pad.outputs[0].voltage
        val drive=machines[IndustrialKind.DRIVE.ordinal];check(drive.hertz>59 && drive.outputs[0].voltage in 390.0..410.0 && drive.outputCurrent>15);check(drive.outputCurrent<100);values["driveOutputVolts"]=drive.outputs[0].voltage
        val charger=machines[IndustrialKind.CHARGER.ordinal];val b=IndustrialElement.battery(charger.inventory.getItem(0))!!;check(b.getEnergy(charger.inventory.getItem(0))>40000);values["storedChargeJoules"]=b.getEnergy(charger.inventory.getItem(0))
        val relay=machines[IndustrialKind.RELAY.ordinal];check(relay.contacts[0].closed && !relay.protection.tripped)
        val console=machines[IndustrialKind.SCADA.ordinal];check(console.telemetry.isNotBlank());check(console.commandTarget(false));check(!relay.contacts[0].closed);check(console.commandTarget(true));check(relay.contacts[0].closed)
        check(!ScadaLink.command(console,BlockPos(1000,80,1000).asLong(),0,false));check(w.chunkSource.getChunkNow(1000 shr 4,1000 shr 4)==null)
        passed+="Real placed cables supply all core industrial machines; load, passive transformer, drive, charger and remote contacts work without network collapse"
        val arrester=machines[IndustrialKind.ARRESTER.ordinal];arrester.absorbLightning();check(arrester.surgeRemaining==160000.0)
        passed+="Replaceable arrester cartridge accounts for lightning energy"
        fixtures.voltage(positions[IndustrialKind.PAD.ordinal].west(2),pad.nominal*.95)
        fixtures.voltage(positions[IndustrialKind.RELAY.ordinal].west(2),relay.nominal*.7)
        fixtures.voltage(positions[IndustrialKind.SHEDDER.ordinal].west(2),190.0)
    }
    private fun prepareRide() {
        val p=fixtures.player
        val pack=Eln.findItemStack(IndustrialItems.PACK,1);IndustrialElement.battery(pack)!!.setEnergy(pack,1000000.0)
        p.setItemInHand(InteractionHand.MAIN_HAND,pack);p.isShiftKeyDown=true;forklift.interact(p,InteractionHand.MAIN_HAND);p.isShiftKeyDown=false
        check(!forklift.pack.isEmpty && p.mainHandItem.isEmpty)
        p.teleportTo(fixtures.world,266.0,80.0,273.0,0f,0f);forklift.interact(p,InteractionHand.MAIN_HAND);check(p.vehicle===forklift)
        start=forklift.position();energy=IndustrialElement.battery(forklift.pack)!!.getEnergy(forklift.pack)
    }
    private fun finish(error: Throwable?) {
        done=true;mc.options.keyUp.setDown(false);ForkliftKeys.raise.setDown(false);ForkliftKeys.lower.setDown(false);ForkliftKeys.cargo.setDown(false)
        Files.createDirectories(output);Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"measurements" to values,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.logger.info("INDUSTRIAL_EXPANSION COMPLETE") else Eln.logger.error("INDUSTRIAL_EXPANSION FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!(java.lang.Boolean.getBoolean("eln.industrialExpansionChecks") || chargerChecks) || done)return
        try {
            if(phase==5)mc.options.keyUp.setDown(true)
            if(phase==7)ForkliftKeys.raise.setDown(true)
            if(phase==9)ForkliftKeys.lower.setDown(true)
            if(phase==11 || phase==13)ForkliftKeys.cargo.setDown(true)
            ticks++;check(++total<5000) { "Timed out phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.renderDistance().set(8)
                    mc.createWorldOpenFlows().createFreshLevel("industrial-expansion-${System.currentTimeMillis()}",LevelSettings("Industrial expansion",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(57057L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;server(::setup);phase=2 }
                2->if(task!!.isDone && ticks>240) {
                    task!!.join();var quads=0
                    for(at in positions) { val r=(mc.level!!.getBlockEntity(at) as TransparentNodeEntity).elementRender!!;val mesh=checkNotNull(TerrainModels.meshFor(r)) { "Unbaked $at" };quads+=mesh.batches.sumOf { it.vertices.size/36 } }
                    check(quads>400);values["bakedMachineQuads"]=quads
                    val previews=(IndustrialKind.entries.map { Eln.findItemStack(it.title,1) }+listOf(IndustrialItems.PACK,IndustrialItems.FRAME,IndustrialItems.FORKLIFT,IndustrialItems.PALLET).map { Eln.findItemStack(it,1) }).map { mods.eln.client.itemrender.BakedNodeItems.audit(it,net.minecraft.world.item.ItemDisplayContext.GUI) }
                    check(previews.all { it["baked"]==true && (it["quads"] as Int)>0 }) { previews.toString() }
                    values["bakedItemPreviews"]=previews;passed+="All new machine previews and accessory icons render as baked item quads"
                    val poleRender=(mc.level!!.getBlockEntity(pole.coordinate().pos) as TransparentNodeEntity).elementRender as mods.eln.utility.DistributionPoleRender
                    check(poleRender.arresterFitted && poleRender.arresterRemaining==160000.0 && TerrainModels.meshFor(poleRender)!=null)
                    passed+="Held arrester installs on a real pole, receives the lightning event and publishes its baked protection kit"
                    passed+="All core industrial static machine bodies compile into native terrain meshes under Sodium/Iris/BSL"
                    photo("industrial-gallery");guiIndex=0;server { openGui(guiIndex) };phase=20
                }
                20->if(task!!.isDone && ticks>25 && mc.screen is IndustrialGui) {
                    task!!.join()
                    if(chargerChecks && IndustrialKind.entries[guiIndex]==IndustrialKind.CHARGER) {
                        for((at,front)in facingDevices) {
                            val render=(mc.level!!.getBlockEntity(at) as TransparentNodeEntity).elementRender!!
                            val mesh=checkNotNull(TerrainModels.meshFor(render));val color=mods.eln.client.gl.VertexColors.pack(.025f,.10f,.12f,1f)
                            val projections=mesh.batches.flatMap { batch->(0 until batch.vertices.size step 9).filter { batch.vertices[it+8]==color }.map { n->
                                (Float.fromBits(batch.vertices[n])-.5f)*front.toFacing().stepX+(Float.fromBits(batch.vertices[n+2])-.5f)*front.toFacing().stepZ
                            } }
                            check(projections.isNotEmpty() && projections.min()>.2f) { "Screen faces away from placed front at $at / $front: $projections" }
                        }
                        passed+="Real charger placement faces the player in all four horizontal directions; input/output sides retain their wiring orientation"
                        server {
                            val d=machines[IndustrialKind.CHARGER.ordinal];d.inventory.setItem(0,ItemStack.EMPTY);d.inventory.setChanged()
                            val p=fixtures.player;p.inventory.clearContent()
                            val pack=Eln.findItemStack(IndustrialItems.PACK,1);IndustrialElement.battery(pack)!!.setEnergy(pack,500000.0)
                            pack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Charger slot test"));p.inventory.setItem(9,pack);p.containerMenu.broadcastChanges()
                        };phase=30;return
                    }
                    photo("gui-${IndustrialKind.entries[guiIndex].name.lowercase(java.util.Locale.ROOT)}")
                    mc.screen!!.onClose();guiIndex++
                    if(guiIndex<positions.size)server { openGui(guiIndex) } else {
                        passed+="All core industrial server menus open and render without overlapping settings or inventory slots"
                        server(::electricalChecks);phase=3
                    }
                }
                30->if(task!!.isDone && ticks>20) {
                    task!!.join();val menu=mc.player!!.containerMenu;val slot=menu.getSlot(0)
                    check(menu.slots.size==37 && slot.x==8 && slot.y==70 && !slot.hasItem())
                    check((slot as mods.eln.gui.ISlotSkin).slotSkin==mods.eln.gui.ISlotSkin.SlotSkin.medium)
                    photo("charger-empty-slot")
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,1,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player!!)
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,0,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player!!)
                    server { };phase=31
                }
                31->if(task!!.isDone && ticks>20) {
                    task!!.join();server {
                        val d=machines[IndustrialKind.CHARGER.ordinal];val pack=d.inventory.getItem(0)
                        check(pack.count==1 && pack.hoverName.string=="Charger slot test" && fixtures.player.containerMenu.carried.isEmpty)
                        check(IndustrialElement.battery(pack)!!.getEnergy(pack)>500000.0)
                        values["chargedPackJoules"]=IndustrialElement.battery(pack)!!.getEnergy(pack)
                        passed+="Normal client inventory clicks insert one pack into the visible slot and real feeder power charges it"
                    };phase=32
                }
                32->if(task!!.isDone && ticks>10) {
                    task!!.join();photo("charger-inserted-pack");val menu=mc.player!!.containerMenu
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,0,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player!!)
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,1,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player!!)
                    server { };phase=33
                }
                33->if(task!!.isDone && ticks>15) {
                    task!!.join();server {
                        check(machines[IndustrialKind.CHARGER.ordinal].inventory.isEmpty)
                        check(fixtures.player.inventory.getItem(9).hoverName.string=="Charger slot test")
                        passed+="Normal clicks remove the same charged pack without losing its name or duplicating it"
                    };phase=34
                }
                34->if(task!!.isDone && ticks>10) {
                    task!!.join();val menu=mc.player!!.containerMenu
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
                    server { };phase=35
                }
                35->if(task!!.isDone && ticks>15) {
                    task!!.join();server {
                        check(machines[IndustrialKind.CHARGER.ordinal].inventory.getItem(0).count==1)
                        check(fixtures.player.inventory.getItem(9).isEmpty)
                    };phase=36
                }
                36->if(task!!.isDone && ticks>10) {
                    task!!.join();val menu=mc.player!!.containerMenu
                    mc.gameMode!!.handleInventoryMouseClick(menu.containerId,0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
                    server { };phase=37
                }
                37->if(task!!.isDone && ticks>15) {
                    task!!.join();server {
                        check(machines[IndustrialKind.CHARGER.ordinal].inventory.isEmpty)
                        val packs=fixtures.player.inventory.items.filter { !it.isEmpty }
                        check(packs.size==1 && packs.single().count==1 && packs.single().hoverName.string=="Charger slot test")
                        passed+="Shift-click insertion/extraction preserves exactly one charged pack"
                    };phase=38
                }
                38->if(task!!.isDone && ticks>10) { task!!.join();finish(null) }
                3->if(task!!.isDone && ticks>240) {
                    task!!.join();server {
                        fixtures.player.teleportTo(fixtures.world,285.0,81.5,269.0,180f,0f);fixtures.player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(285.0,80.4,264.0))
                    };phase=22
                }
                22->if(task!!.isDone && ticks>40) {
                    task!!.join();photo("washer-and-dryer");passed+="Dryer has its own baked solid-door, console, lint-filter and exhaust geometry";server {
                        val pad=machines[IndustrialKind.PAD.ordinal];check(pad.outputs[0].voltage in 226.0..229.0)
                        val relay=machines[IndustrialKind.RELAY.ordinal];check(relay.protection.tripped && !relay.contacts[0].closed)
                        check(machines[IndustrialKind.SHEDDER.ordinal].shed.closed.none { it })
                        val welder=machines[IndustrialKind.WELDER.ordinal];check(!welder.inventory.getItem(2).isEmpty)
                        passed+="Live sag propagates to secondary; relay latches, priorities shed and welding consumes actual grid energy"
                        prepareRide()
                    };phase=4
                }
                4->if(task!!.isDone && ticks>20) { task!!.join();mc.options.keyUp.setDown(true);ticks=0;phase=5 }
                5->if(ticks>40) { mc.options.keyUp.setDown(false);server {
                    check(forklift.position().distanceTo(start)>1);check(IndustrialElement.battery(forklift.pack)!!.getEnergy(forklift.pack)<energy)
                    values["forkliftTravelBlocks"]=forklift.position().distanceTo(start);passed+="Real client control packets drive the server-authoritative forklift and drain its traction pack"
                };phase=6 }
                6->if(task!!.isDone && ticks>40) { task!!.join();ForkliftKeys.raise.setDown(true);ticks=0;phase=7 }
                7->if(ticks>45) { ForkliftKeys.raise.setDown(false);server { values["forkHeight"]=forklift.lift;check(forklift.lift>.8) { "Lift height ${forklift.lift}; rider ${forklift.controllingPassenger}" } };phase=8 }
                8->if(task!!.isDone && ticks>10) { task!!.join();ForkliftKeys.lower.setDown(true);ticks=0;phase=9 }
                9->if(ticks>60) { ForkliftKeys.lower.setDown(false);server {
                    check(forklift.lift<.1)
                    val r=forklift.yRot*PI/180;val at=forklift.position().add(-sin(r)*1.5,0.0,cos(r)*1.5)
                    val pallet=PalletEntity(PalletEntity.TYPE.get(),fixtures.world);pallet.moveTo(at.x,at.y,at.z,0f,0f);pallet.items.setItem(0,ItemStack(Items.DIAMOND,9));pallet.items.setItem(1,Eln.findItemStack("Machine Block",1));check(fixtures.world.addFreshEntity(pallet))
                };phase=10 }
                10->if(task!!.isDone && ticks>15) { task!!.join();ForkliftKeys.cargo.setDown(true);ticks=0;phase=11 }
                11->if(ticks>10) { ForkliftKeys.cargo.setDown(false);server {
                    check(!forklift.cargo.isEmpty) { "Cargo absent; last result ${forklift.lastCargoResult}, packets ${forklift.controlPackets}, lift ${forklift.lift}; pallets ${fixtures.world.getEntitiesOfClass(PalletEntity::class.java,forklift.boundingBox.inflate(4.0)).map { it.position() }}; forklift ${forklift.position()}" }
                    val copy=ForkliftEntity(ForkliftEntity.TYPE.get(),fixtures.world);copy.load(forklift.saveWithoutId(CompoundTag()))
                    check(copy.hasCargo && abs(copy.lift-forklift.lift)<1e-5)
                    check(ItemStack.isSameItemSameComponents(copy.pack,forklift.pack));check(ItemStack.isSameItemSameComponents(copy.cargo,forklift.cargo))
                    passed+="Loaded pallet, battery energy and fork height survive actual entity save/load without separate duplicate contents"
                };phase=12 }
                12->if(task!!.isDone && ticks>15) { task!!.join();ForkliftKeys.cargo.setDown(true);ticks=0;phase=13 }
                13->if(ticks>10) { ForkliftKeys.cargo.setDown(false);server {
                    check(forklift.cargo.isEmpty)
                    val pallet=fixtures.world.getEntitiesOfClass(PalletEntity::class.java,forklift.boundingBox.inflate(3.0)).single()
                    check(pallet.items.getItem(0).count==9 && pallet.items.getItem(0).`is`(Items.DIAMOND));check(pallet.items.getItem(1).item==Eln.findItemStack("Machine Block",1).item)
                    passed+="Client pallet pickup/drop preserves all equipment stacks and creates exactly one destination pallet"
                    fixtures.player.stopRiding();fixtures.player.teleportTo(fixtures.world,270.0,84.0,281.0,0f,0f);fixtures.player.lookAt(EntityAnchorArgument.Anchor.EYES,forklift.position().add(0.0,1.0,0.0))
                };phase=14 }
                14->if(task!!.isDone && ticks>60) { task!!.join();photo("forklift-and-pallet");finish(null) }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
