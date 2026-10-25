package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.client.itemrender.BakedNodeItems
import mods.eln.client.terrain.TerrainModels
import mods.eln.gridnode.*
import mods.eln.integration.electroenergetics.*
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.transparent.*
import mods.eln.sixnode.electricalcable.*
import mods.eln.transparentnode.firealarm.*
import mods.eln.utility.*
import mods.eln.utility.insulator.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.*
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Real placement, wire spans, loaded-chunk sensors and official CEE FE interoperability. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object GridAlarmCeeChecks {
    private val mc get()=Minecraft.getInstance()
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var work: CompletableFuture<*>?=null
    private val passed=ArrayList<String>();private val previews=ArrayList<Map<String,Any?>>()
    private val positions=ArrayList<BlockPos>();private val items=ArrayList<ItemStack>()
    private val pPanel=BlockPos(440,80,400);private val pPull=BlockPos(442,80,400)
    private val pHeat=BlockPos(444,82,408);private val pSmoke=BlockPos(442,82,408);private val pFire=BlockPos(444,80,408)
    private val pBridge=BlockPos(470,80,400);private val pConverter=pBridge.east();private val pResistor=pBridge.east(6)
    private var backupBefore=0.0;private var panelSource: mods.eln.sim.mna.component.VoltageSource?=null
    private var ceeConverter: Any?=null;private var ceeResistor: Any?=null
    private val measurements=linkedMapOf<String,Any>()
    private val output get()=mc.gameDirectory.toPath().resolve("grid-alarm-cee-checks")
    private fun machine(pos: BlockPos)=NodeManager.instance!!.getTransparentNodeFromCoordinate(Coordinate(pos.x,pos.y,pos.z,0))!!
    private fun alarm(pos: BlockPos)=machine(pos) as AlarmElement
    private fun packet(id: Int)=java.io.DataInputStream(java.io.ByteArrayInputStream(byteArrayOf(id.toByte())))
    private fun setup() {
        val player=mc.singleplayerServer!!.playerList.players.first();val world=player.serverLevel();val f=NativeCampaignFixtures(world,player)
        world.gameRules.getRule(GameRules.RULE_DOFIRETICK).set(false,world.server)
        world.setWeatherParameters(12000,0,false,false);world.setDayTime(6000)
        player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.onUpdateAbilities();player.setNoGravity(true)
        fun place(name: String,pos: BlockPos,clicked: net.minecraft.core.Direction=net.minecraft.core.Direction.UP): TransparentNodeElement {
            world.setChunkForced(pos.x shr 4,pos.z shr 4,true)
            world.setBlockAndUpdate(pos.relative(clicked.opposite),Blocks.STONE.defaultBlockState())
            val item=f.requiredItem(name);check(Eln.transparentNodeItem.placeBlockAt(item,player,world,pos,clicked)) { "Placement failed: $name $clicked" }
            positions+=pos;if(items.none { it.item===item.item })items+=item.copy()
            return f.machine(pos)
        }
        for((i,v)in InsulatorVoltage.entries.withIndex())for((j,face)in net.minecraft.core.Direction.entries.withIndex())for(feed in listOf(false,true)) {
            val pos=BlockPos(400+j*6,80+i*5,440+if(feed)8 else 0)
            val e=place(v.title(feed),pos,face) as LineInsulatorElement
            check(e.front.toFacing()==face.opposite)
            check(e.insulator.supported(world,pos,e.front))
            val expected=e.front.rotateFromXN(Vec3(-.5+v.length,0.0,0.0))
            check(e.renderedGridCablePoint(32,0).distanceTo(expected)<1e-9)
        }
        passed+="All ten insulator variants place on all six mounting faces with matching conductor endpoints"
        val feedPos=BlockPos(400,80,410);val linePos=BlockPos(412,80,410)
        val feed=place(InsulatorVoltage.TRANSMISSION.feedName,feedPos) as LineInsulatorElement
        val line=place(InsulatorVoltage.TRANSMISSION.lineName,linePos) as LineInsulatorElement
        val bare=UtilityCableDescriptor.allDescriptors().first { !it.insulated && !it.melted }
        val wirePos=feedPos.west();world.setBlockAndUpdate(wirePos.below(),Blocks.STONE.defaultBlockState())
        f.placeSix(bare.newItemStack().also { bare.setRemainingLengthMeters(it,1.0) },wirePos)
        val wire=f.six(wirePos).getElectricalLoad(LRDU.Right,mods.eln.node.NodeBase.maskElectricalPower)!!
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("230 kV cable boundary",wire,null).apply { voltage=230_000.0 })
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.Resistor(line.bus,null).apply { resistance=1_000_000.0 })
        GridLink.addLink(feed,line,32,32,bare,12)
        val pole=place(DistributionKind.SECONDARY.title,BlockPos(420,80,410)) as DistributionPoleElement
        val lv=place(InsulatorVoltage.LOW.lineName,BlockPos(430,80,410)) as LineInsulatorElement;lv.selectCircuit(41)
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("L2 boundary",pole.services[1],null).apply { voltage=120.0 })
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.Resistor(lv.bus,null).apply { resistance=120.0 })
        val cable=f.spool(30.0);player.setItemInHand(InteractionHand.MAIN_HAND,cable)
        val desc=ElectricalCableDescriptor.getDescriptor(cable) as ElectricalCableDescriptor
        check(pole.onTryGridConnect(player,cable,desc,Direction.XN,pole.renderedGridCablePoint(41,0)))
        check(lv.onTryGridConnect(player,cable,desc,Direction.XN,lv.renderedGridCablePoint(41,0)))
        check(lv.gridLinkList.size==1)
        passed+="Aiming the L2 rack connects a single-conductor LV insulator without shorting the service bundle"
        for(kind in AlarmKind.entries) {
            val pos=when(kind) {
                AlarmKind.PANEL->pPanel;AlarmKind.PULL->pPull;AlarmKind.HORN->BlockPos(444,80,400)
                AlarmKind.BELL->BlockPos(446,80,400);AlarmKind.STROBE->BlockPos(448,80,400);AlarmKind.SMOKE->pSmoke;AlarmKind.HEAT->pHeat
            }
            place(kind.title,pos,if(kind.sensor)net.minecraft.core.Direction.DOWN else net.minecraft.core.Direction.SOUTH)
        }
        panelSource=mods.eln.sim.mna.component.VoltageSource("120 V panel boundary",alarm(pPanel).supply,null).apply { voltage=120.0 }
        Eln.simulator.addElectricalComponent(panelSource)
        for(face in net.minecraft.core.Direction.entries)world.setBlockAndUpdate(pFire.relative(face),Blocks.GLASS.defaultBlockState())
        world.setBlockAndUpdate(pFire.below(),Blocks.NETHERRACK.defaultBlockState())
        world.setBlockAndUpdate(pFire,Blocks.FIRE.defaultBlockState())
        val wired=place(AlarmKind.HORN.title,BlockPos(452,80,400),net.minecraft.core.Direction.SOUTH) as AlarmElement
        wired.changeChannel("")
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("wired 24 V supply",wired.supply,null).apply { voltage=24.0 })
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("wired trigger",wired.signal,null).apply { voltage=Eln.SVU })
        val bridge=place("ELN to CEE Power Bridge",pBridge) as CeeBridgeElement
        bridge.front=Direction.XN;bridge.settings(3200.0,10_000.0,40.0);bridge.reconnect()
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("ELN bridge boundary",bridge.input,null).apply { voltage=3200.0 })
        setupCee(world)
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        camera(440.0,88.0,430.0,Vec3(431.0,84.0,410.0))
    }
    private fun setupCee(world: ServerLevel) {
        check(CeeConfig.present()) { "Run these optional checks with CEE installed" }
        fun block(id: String,pos: BlockPos,source: Boolean=false) {
            val b=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics",id));check(b!==Blocks.AIR)
            world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            var state=b.defaultBlockState()
            if(state.hasProperty(BlockStateProperties.FACING))state=state.setValue(BlockStateProperties.FACING,net.minecraft.core.Direction.EAST)
            if(source)state=state.setValue(state.properties.filterIsInstance<net.minecraft.world.level.block.state.properties.BooleanProperty>().single { it.name=="source" },true)
            world.setBlockAndUpdate(pos,state)
            b.javaClass.methods.single { it.name=="ensureNodesExist" }.invoke(b,world,pos,state)
        }
        block("converter",pConverter,true);block("resistor",pResistor)
        val type=Class.forName("com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData")
        val infrastructure=type.getMethod("load",ServerLevel::class.java).invoke(null,world)
        val node=Class.forName("com.george_vi.electroenergetics.foundation.nodes.InWorldNode")
        val wireType=Class.forName("com.george_vi.electroenergetics.simulation.WireType")
        val holder=Class.forName("com.george_vi.electroenergetics.CEEWireTypes").getField("STANDARD").get(null) as java.util.function.Supplier<*>
        val connect=type.getMethod("connect",node,node,wireType)
        val ctor=node.getConstructor(Int::class.javaPrimitiveType,BlockPos::class.java)
        for(i in 0..1)connect.invoke(infrastructure,ctor.newInstance(i,pConverter),ctor.newInstance(i,pResistor),holder.get())
    }
    private fun device(world: ServerLevel,pos: BlockPos): Any {
        val type=Class.forName("com.george_vi.electroenergetics.devices.device.DevicesSavedData")
        // CEE also declares optional Sable method signatures. Targeted handles avoid
        // reflectively resolving unrelated optional APIs when Sable is not installed.
        val lookup=java.lang.invoke.MethodHandles.publicLookup()
        val saved=lookup.findStatic(type,"load",java.lang.invoke.MethodType.methodType(type,ServerLevel::class.java)).invokeWithArguments(world)
        val deviceType=Class.forName("com.george_vi.electroenergetics.devices.device.SimulatedDevice")
        return lookup.findVirtual(type,"getDevice",java.lang.invoke.MethodType.methodType(deviceType,BlockPos::class.java)).invokeWithArguments(saved,pos) ?: error("CEE device not initialized at $pos")
    }
    private fun camera(x: Double,y: Double,z: Double,at: Vec3) { val p=mc.singleplayerServer!!.playerList.players.first();p.teleportTo(p.serverLevel(),x,y,z,0f,0f);p.lookAt(EntityAnchorArgument.Anchor.EYES,at) }
    private fun server(action: (ServerLevel)->Unit) { work=mc.singleplayerServer!!.submit<Unit> { action(mc.singleplayerServer!!.overworld()) };ticks=0 }
    private fun photo(name: String) { Files.createDirectories(output);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(output.resolve("$name.png")) } }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString(),"measurements" to measurements,"previews" to previews)))
        if(error!=null)Eln.logger.error("GRID_ALARM_CEE FAIL",error) else Eln.logger.info("GRID_ALARM_CEE COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.gridAlarmCeeChecks") || done)return
        try {
            ticks++;check(++total<4000) { "Timed out at phase $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.createWorldOpenFlows().createFreshLevel("grid-alarm-cee-${System.currentTimeMillis()}",LevelSettings("Grid alarm CEE",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(50052L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { mc.options.hideGui=true;server { setup() };phase=2 }
                2->if(work!!.isDone && ticks>240) {
                    work!!.join()
                    server { w->
                        val line=machine(BlockPos(412,80,410)) as LineInsulatorElement;check(line.bus.voltage in 229_000.0..230_001.0)
                        val lv=machine(BlockPos(430,80,410)) as LineInsulatorElement;check(lv.bus.voltage>119.0)
                        check(alarm(pHeat).detected && !alarm(pSmoke).detected);check(alarm(pPanel).panel.latched)
                        check(alarm(BlockPos(452,80,400)).alarming && alarm(BlockPos(452,80,400)).powered)
                        ceeConverter=device(w,pConverter);ceeResistor=device(w,pResistor)
                        w.getBlockEntity(pResistor)!!.javaClass.getMethod("setResistance",Double::class.javaPrimitiveType).invoke(w.getBlockEntity(pResistor),100.0)
                        w.removeBlock(pFire.above(),false)
                    };phase=3
                }
                3->if(work!!.isDone && ticks>100) {
                    work!!.join();passed+="230 kV spans and direct fixed-cable feed carry real power; wired-only alarms work at 24 V"
                    passed+="Heat detects the sealed fire while smoke is blocked by glass"
                    server { w->check(alarm(pSmoke).detected);w.removeBlock(pFire,false) };phase=4
                }
                4->if(work!!.isDone && ticks>110) {
                    work!!.join();passed+="Opening the smoke path activates the smoke detector"
                    server { _->check(!alarm(pHeat).detected && !alarm(pSmoke).detected);check(alarm(pPanel).panel.latched);alarm(pPanel).networkUnserialize(packet(85));check(!alarm(pPanel).panel.latched);alarm(pPull).operatePull() };phase=5
                }
                5->if(work!!.isDone && ticks>30) {
                    work!!.join();server { _->
                        check(alarm(pPanel).panel.latched);alarm(pPanel).networkUnserialize(packet(84));alarm(pPanel).networkUnserialize(packet(85));check(alarm(pPanel).panel.latched)
                    };phase=6
                }
                6->if(work!!.isDone && ticks>30) {
                    work!!.join();server { _->
                        check(alarm(BlockPos(444,80,400)).silenced && alarm(BlockPos(448,80,400)).alarming)
                        alarm(pPull).operatePull()
                    };phase=7
                }
                7->if(work!!.isDone && ticks>25) {
                    work!!.join();passed+="Pull station latches the panel; silence retains strobes; reset refuses active sources"
                    server { _->alarm(pPanel).networkUnserialize(packet(85));check(!alarm(pPanel).panel.latched);backupBefore=alarm(pPanel).panel.energy;panelSource!!.voltage=0.0 };phase=8
                }
                8->if(work!!.isDone && ticks>25) {
                    work!!.join();server { _->
                        check(alarm(pPanel).powered && alarm(pPanel).trouble);check(alarm(pPanel).panel.energy<backupBefore);panelSource!!.voltage=120.0
                        val bridge=machine(pBridge) as CeeBridgeElement;check(bridge.scale!=null && bridge.budget.exportedJoules>100)
                        check(bridge.budget.inputJoules>bridge.budget.exportedJoules)
                        val temp=ceeResistor!!.javaClass.getField("temp").getFloat(ceeResistor);check(temp>0)
                        measurements["ceeResistorTemperature"]=temp;measurements["ceeScaleWattsPerFeTick"]=bridge.scale!!.wattsPerFeTick
                        measurements["bridgeInputJoules"]=bridge.budget.inputJoules;measurements["exportedJoules"]=bridge.budget.exportedJoules
                        measurements["bufferJoules"]=bridge.budget.joules;measurements["lossJoules"]=bridge.budget.lossJoules
                    };phase=9
                }
                9->if(work!!.isDone && ticks>30) {
                    work!!.join();passed+="Panel backup energy decreases during mains loss and signals trouble"
                    passed+="Real CEE 1.1.4 Converter pulls the bridge's FE and supplies a native wired CEE resistor"
                    for(pos in positions) {
                        val entity=mc.level!!.getBlockEntity(pos) as TransparentNodeEntity
                        check(TerrainModels.meshFor(entity.elementRender!!)!=null) { "Unbaked body at $pos" }
                    }
                    for(item in items)for(context in listOf(ItemDisplayContext.GUI,ItemDisplayContext.GROUND)) {
                        val audit=BakedNodeItems.audit(item,context);check(audit["baked"]==true);previews+=audit
                    }
                    passed+="All new static bodies and all 18 new item previews are baked"
                    server { camera(415.0,91.0,469.0,Vec3(415.0,90.0,444.0)) };phase=10
                }
                10->if(work!!.isDone && ticks>70) { work!!.join();photo("insulators");server { camera(444.0,82.0,404.0,Vec3(444.0,80.5,400.0)) };phase=11 }
                11->if(work!!.isDone && ticks>70) { work!!.join();photo("alarm-system");server { camera(474.0,82.0,405.0,Vec3(473.0,80.5,400.0)) };phase=12 }
                12->if(work!!.isDone && ticks>70) { work!!.join();photo("cee-bridge");finish(null) }
            }
        } catch(error: Throwable) { finish(error) }
    }
}
