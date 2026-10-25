package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.itemrender.BakedNodeItems
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.wirelesssignal.WallWirelessDescriptor
import mods.eln.sixnode.wirelesssignal.WallWirelessElement
import mods.eln.transparentnode.standby.*
import mods.eln.transparentnode.climate.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
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

/** Opt-in integration of the actual registered devices and Sodium/Iris terrain/item paths. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object OutdoorExpansionChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val positions=ArrayList<BlockPos>();private val items=ArrayList<ItemStack>()
    private val passed=ArrayList<String>();private val previews=ArrayList<Map<String,Any?>>()
    private var clicks=0;private var clickBaseline=0;private var clickVolume=0f
    private val output get()=mc.gameDirectory.toPath().resolve("outdoor-expansion-checks")
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(!java.lang.Boolean.getBoolean("eln.outdoorChecks"))return
        event.sound?.let { if(it.location.toString()=="eln:click") { clicks++;it.resolve(mc.soundManager);clickVolume=maxOf(clickVolume,it.volume) } }
    }
    private fun setup() {
        val player=mc.singleplayerServer!!.playerList.players.first();val w=player.serverLevel();val f=NativeCampaignFixtures(w,player)
        player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.onUpdateAbilities();player.setNoGravity(true)
        w.setWeatherParameters(12000,0,false,false);w.setDayTime(6000)
        fun machine(name: String,p: BlockPos,wall: Boolean=false): TransparentNodeElement {
            w.setChunkForced(p.x shr 4,p.z shr 4,true)
            val stack=f.requiredItem(name);items+=stack.copy();positions+=p
            if((Eln.transparentNodeItem.getDescriptor(stack) as TransparentNodeDescriptor).mustHaveFloor())w.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState())
            if(wall)w.setBlockAndUpdate(p.north(),Blocks.STONE.defaultBlockState())
            check(Eln.transparentNodeItem.placeBlockAt(stack,player,w,p,if(wall)net.minecraft.core.Direction.SOUTH else net.minecraft.core.Direction.UP)) { "Place $name" }
            return f.machine(p)
        }
        GeneratorTier.entries.forEachIndexed { i,tier->
            val g=machine(tier.title,BlockPos(256+i*9,80,256)) as StandbyGeneratorElement
            g.unit.mode=StandbyMode.MANUAL
            g.unit.fuel.tank.fill(net.neoforged.neoforge.fluids.FluidStack(StandbyTestFluids.fluid(0),tier.tankMb),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.Resistor(g.unit.output,null).apply { resistance=240.0*240.0/(tier.watts*.2) })
        }
        DistributionKind.entries.filter { it.underground }.forEachIndexed { i,kind->machine(kind.title,BlockPos(256+i*8,80,300)) }
        DecorativeLampStyle.entries.drop(88).forEachIndexed { i,style->
            val p=BlockPos(256+(i%5)*4,80,275+(i/5)*4);positions+=p
            val d=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==style };items+=d.newItemStack(1)
            w.setChunkForced(p.x shr 4,p.z shr 4,true)
            w.setBlockAndUpdate(if(style.outdoorFloor)p.below() else p.north(),Blocks.STONE.defaultBlockState())
            check(Eln.sixNodeItem.placeBlockAt(d.newItemStack(1),player,w,p,if(style.outdoorFloor)net.minecraft.core.Direction.UP else net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f)) { "Place ${style.name}" }
            val n=f.node(p) as SixNode;val e=n.sideElementList.filterIsInstance<LampSocketElement>().single()
            e.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",1))
            e.inventory.setItem(1,Eln.instance.highVoltageCableDescriptor.newItemStack(1))
            e.poweredByLampSupply=false;e.inventoryChange(e.inventory)
            Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("native lamp boundary",e.electricalLoad,null).apply { voltage=240.0 })
            e.needPublish()
        }
        ServiceMastStyle.entries.drop(1).forEachIndexed { i,mast->
            val p=BlockPos(302+i*4,82,270)
            if(mast==ServiceMastStyle.THROUGH_ROOF)w.setBlockAndUpdate(p.above(),Blocks.STONE.defaultBlockState())
            machine(mast.title,p,true)
            if(mast==ServiceMastStyle.THROUGH_ROOF)check(w.getBlockState(p.above()).`is`(Blocks.STONE))
        }
        machine("Electric Washing Machine",BlockPos(295,80,287));machine("Electric Dryer",BlockPos(297,80,287))
        ClimateType.entries.filter { it.tvStand }.forEachIndexed { i,type->machine(type.title,BlockPos(294+i*4,80,282)) }
        for((i,type)in listOf(1,2).withIndex()) {
            val p=BlockPos(303+i*4,81,290);val m=machine("North American Meter Base",p,true) as HouseServiceElement
            m.inventory.setItem(0,f.requiredItem(if(type==1)"Analog Glass Service Meter" else "Smart Digital Service Meter"));m.inventoryChange(m.inventory)
            Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("native meter boundary",m.line[2],null).apply { voltage=240.0 })
            Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.Resistor(m.load[2],null).apply { resistance=240.0 })
        }
        machine(TransmissionKind.POLE_69.title,BlockPos(320,80,270));machine(TransmissionKind.POLE_230.title,BlockPos(332,80,270))
        val three=machine(DistributionKind.THREE.title,BlockPos(315,80,300)) as DistributionPoleElement
        machine(DistributionKind.SINGLE.title,BlockPos(319,80,300))
        val twin=machine(DistributionKind.TWIN_SECONDARY_LIGHT.title,BlockPos(296,80,300)) as DistributionPoleElement
        twin.readItemStackNBT(net.minecraft.nbt.CompoundTag().apply { putInt("utilityLampVoltage",240) })
        val feeder=machine(DistributionKind.SECONDARY.title,BlockPos(302,80,300)) as DistributionPoleElement
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("240 V secondary boundary",feeder.services[2],null).apply { voltage=240.0 })
        val spool=f.spool(18.0);val cable=mods.eln.sixnode.electricalcable.ElectricalCableDescriptor.getDescriptor(spool) as mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
        mods.eln.gridnode.GridLink.addLink(feeder,twin,50,50,cable,18,spool)
        check(!mods.eln.gridnode.GridLink(three.coordinate(),twin.coordinate(),32,32,spool,cable.electricalRs).connect()) { "A retired HV span must not become an LV feed" }
        passed+="The converted twin pole rejects retired HV spans while its LV bundle joins the new poles"
        mods.eln.transparentnode.switchgear.SwitchgearKind.entries.forEachIndexed { i,kind->
            val device=machine(kind.title,BlockPos(292+i*3,80,312)) as mods.eln.transparentnode.switchgear.SwitchgearElement
            val mode=mods.eln.transparentnode.switchgear.RelayControl.entries[i%3];device.setControl(mode)
            val a=if(mode==mods.eln.transparentnode.switchgear.RelayControl.SIGNAL)device.gate else device.coilA
            val b=if(mode==mods.eln.transparentnode.switchgear.RelayControl.SIGNAL)null else device.coilB
            Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("switchgear control boundary",a,b).apply { voltage=if(mode.nominal==0.0)Eln.SVU else mode.nominal })
            device.commons.forEachIndexed { n,pin->
                Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("contact boundary",pin,null).apply { voltage=120.0+n*12 })
                Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.Resistor(device.normallyOpen[n],null).apply { resistance=120.0 })
            }
        }
        val trays=(0..2).map { i->
            val p=BlockPos(277+i,84,292);w.setBlockAndUpdate(p.above(),Blocks.STONE.defaultBlockState())
            machine("Ceiling Color Segregated Cable Tray",p) as ColorCableTrayElement
        }
        for(t in trays) { for(color in listOf(0,5,9))t.inventory.setItem(color,f.spool());t.inventory.setChanged() }
        check(trays[1].node!!.nodeConnectionList.any { it.EC.size==3 }) { "Matching tray channels did not join through real node connections" }
        passed+="Three real adjacent trays join three independent installed colors"
        val switch=Eln.sixNodeItem.subItemList.values.filterIsInstance<WallWirelessDescriptor>().first { it.paddle && !it.momentary }
        val pos=BlockPos(303,82,289);w.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(switch.newItemStack(1),player,w,pos,net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f));positions+=pos;items+=switch.newItemStack(1)
        val wiredSwitch=BlockPos(306,82,289);w.setBlockAndUpdate(wiredSwitch.below(),Blocks.STONE.defaultBlockState())
        val wiredItem=f.requiredItem("20A Switch");f.placeSix(wiredItem,wiredSwitch)
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        camera(281.0,88.0,313.0,Vec3(278.0,82.0,270.0))
    }
    private fun camera(x: Double,y: Double,z: Double,at: Vec3) {
        val p=mc.singleplayerServer!!.playerList.players.first();p.teleportTo(p.serverLevel(),x,y,z,0f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,at)
    }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun finish(error: Throwable?) {
        done=true;mc.options.hideGui=false;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"previews" to previews,"clicks" to clicks,"clickVolume" to clickVolume,"terrainBodies" to TerrainModels.capturedBodies,"sections" to TerrainModels.compiledSections)))
        if(error!=null)Eln.logger.error("OUTDOOR_EXPANSION FAIL",error) else Eln.logger.info("OUTDOOR_EXPANSION COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.outdoorChecks") || done)return
        try {
            ticks++;check(++total<3000) { "Outdoor checks timed out at $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="outdoor-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(50050L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;work=mc.singleplayerServer!!.submit<Unit> { setup() };phase=2;ticks=0 }
                2->if(work!!.isDone && ticks>220) {
                    work!!.join()
                    for(pos in positions) {
                        val tile=mc.level!!.getBlockEntity(pos)
                        val renders=when(tile) { is TransparentNodeEntity->listOfNotNull(tile.elementRender);is SixNodeEntity->tile.elementRenderList.filterNotNull();else->error("Unloaded new item at $pos") }
                        check(renders.isNotEmpty())
                        renders.forEach { check(TerrainModels.meshFor(it)!=null) { "Not baked: ${it.javaClass.simpleName} at $pos" } }
                    }
                    passed+="Every placed new fixture, generator, mast, cabinet, tray and widened TV stand has baked terrain geometry"
                    for(item in items)for(context in listOf(ItemDisplayContext.GUI,ItemDisplayContext.GROUND)) {
                        val result=BakedNodeItems.audit(item,context);check(result["baked"]==true) { "Unbaked preview: $result" };previews+=result
                    }
                    passed+="All new item previews use baked quads in GUI and ground contexts"
                    for(pos in positions) {
                        val tile=mc.level!!.getBlockEntity(pos) as? SixNodeEntity ?: continue
                        tile.elementRenderList.filterIsInstance<LampSocketRender>().filter { it.descriptor.fixture?.ordinal?.let { n->n>=88 }==true }.forEach { check(it.lightValue>=LampSocketRender.MIN_LIGHT_ON_VALUE) { "New fixture did not illuminate" } }
                    }
                    passed+="All fifteen new fixtures illuminate from actual 240 V electrical boundary supplies"
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val twin=NodeManager.instance!!.getTransparentNodeFromCoordinate(Coordinate(296,80,300,0)) as DistributionPoleElement
                        check(twin.unit==null && twin.fuses.isEmpty() && twin.lightLevel>=14 && twin.lampPower>119.0)
                        check(kotlin.math.abs(twin.services[0].voltage)<1e-6) { "240 V must not be silently converted to 120 V" }
                        mods.eln.transparentnode.switchgear.SwitchgearKind.entries.forEachIndexed { i,kind->
                            val relay=NodeManager.instance!!.getTransparentNodeFromCoordinate(Coordinate(292+i*3,80,312,0)) as mods.eln.transparentnode.switchgear.SwitchgearElement
                            check(relay.energized && !relay.tripped && relay.noContacts.all { it.closed && it.current>.9 }) { "Native switchgear failed: $kind" }
                        }
                    };work!!.join()
                    passed+="Both twin heads illuminate from a real 240 V LV span without a transformer or 120 V conversion"
                    passed+="All six switchgear devices operate real independent loads using signal, 120 V and 240 V control"
                    check(TerrainModels.compiledSections>0);photo("equipment")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(267.0,82.0,289.0,Vec3(264.0,80.6,279.0)) };phase=3;ticks=0
                }
                3->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("lamps")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(305.0,81.0,294.0,Vec3(305.0,81.5,290.0)) };phase=4;ticks=0
                }
                4->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("meters");clickBaseline=clicks
                    work=mc.singleplayerServer!!.submit<Unit> {
                        val n=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(303,82,289,0)) as SixNode
                        val e=n.sideElementList.filterIsInstance<WallWirelessElement>().single()
                        val player=mc.singleplayerServer!!.playerList.players.first()
                        e.onBlockActivated(player,Direction.ZP,.5f,.5f,.5f)
                        val wired=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(306,82,289,0)) as SixNode
                        wired.sideElementList.filterIsInstance<mods.eln.sixnode.electricalswitch.ElectricalSwitchElement>().single().onBlockActivated(player,Direction.YN,.5f,.5f,.5f)
                    };phase=5;ticks=0
                }
                5->if(work!!.isDone && ticks>50) {
                    work!!.join();check(clicks-clickBaseline>=2 && clickVolume>.05f) { "Switch click missing or inaudible: $clicks / $clickVolume" };passed+="Wireless paddle and legacy wired switch both emit audible registered clicks"
                    work=mc.singleplayerServer!!.submit<Unit> { camera(279.0,82.5,297.0,Vec3(278.0,84.0,292.0)) };phase=6;ticks=0
                }
                6->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("trays")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(333.0,101.0,293.0,Vec3(327.0,98.0,270.0)) };phase=7;ticks=0
                }
                7->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("insulators")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(298.0,83.0,291.0,Vec3(296.0,80.6,287.0)) };phase=8;ticks=0
                }
                8->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("laundry-tops")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(318.0,88.0,307.0,Vec3(317.0,87.0,300.0)) };phase=9;ticks=0
                }
                9->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("distribution-insulators")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(299.0,85.0,307.0,Vec3(296.0,84.8,300.0)) };phase=10;ticks=0
                }
                10->if(work!!.isDone && ticks>80) {
                    work!!.join();photo("twin-secondary")
                    work=mc.singleplayerServer!!.submit<Unit> { camera(301.0,82.0,316.0,Vec3(300.0,80.0,312.0)) };phase=11;ticks=0
                }
                11->if(work!!.isDone && ticks>80) { work!!.join();photo("switchgear");finish(null) }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
