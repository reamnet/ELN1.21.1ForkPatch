package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.client.terrain.TerrainModels
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.electricalcable.UtilityCableElement
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.transparentnode.industrial.IndustrialElement
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
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

/** Real same-color cable entries, both placement orders, billing, GUI disconnect and baked American service models. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object AmericanServiceChecks {
    private val mc get()=Minecraft.getInstance()
    private val base=BlockPos(256,82,256)
    private val wide=listOf(base,base.east(8));private val analog=base.west(8);private val spare=base.west(4)
    private val padPos=base.west(4).south(8).below(2)
    private var phase=0;private var ticks=0;private var total=0;private var done=false
    private var task: CompletableFuture<Unit>?=null
    private val meters=ArrayList<HouseServiceElement>();private val loads=ArrayList<Resistor>();private val sourceElements=ArrayList<ElectricalSourceElement>()
    private lateinit var analogMeter: HouseServiceElement;private lateinit var spareMeter: HouseServiceElement;private lateinit var pad: IndustrialElement
    private val passed=ArrayList<String>();private val data=linkedMapOf<String,Any>()
    private val out get()=mc.gameDirectory.toPath().resolve("american-service-checks")
    private fun work(action: (ServerPlayer)->Unit) { ticks=0;task=mc.singleplayerServer!!.submit<Unit> { action(mc.singleplayerServer!!.playerList.players.first()) } }
    private fun photo(name: String) { Files.createDirectories(out);Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(out.resolve("$name.png")) } }
    private fun view(p: ServerPlayer,at: BlockPos,distance: Double=1.7) {
        p.teleportTo(p.serverLevel(),at.x+.5,at.y-1.07,at.z+distance,180f,0f)
        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(at.x+.5,at.y+.55,at.z+.43))
    }
    private fun wall(p: ServerPlayer,name: String,at: BlockPos): HouseServiceElement {
        val w=p.serverLevel();w.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState());w.setBlockAndUpdate(at.east().north(),Blocks.STONE.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,w,at,net.minecraft.core.Direction.SOUTH)) { "Could not place $name at $at" }
        return NativeCampaignFixtures(w,p).machine(at) as HouseServiceElement
    }
    private fun six(p: ServerPlayer,stack: ItemStack,at: BlockPos): mods.eln.node.six.SixNodeElement {
        val w=p.serverLevel();w.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(stack,p,w,at,net.minecraft.core.Direction.SOUTH,.5f,.5f,.5f))
        return (NativeCampaignFixtures(w,p).node(at) as SixNode).getElement(Direction.ZN)!!
    }
    private fun source(p: ServerPlayer,at: BlockPos)=(six(p,Eln.findItemStack("Electrical Source",1),at) as ElectricalSourceElement).also {
        it.readConfigTool(CompoundTag().apply { putDouble("voltage",240.0) },p);sourceElements+=it
    }
    private fun setup(p: ServerPlayer) {
        val w=p.serverLevel();val f=NativeCampaignFixtures(w,p)
        for(x in 14..17)for(z in 15..17)w.setChunkForced(x,z,true)
        for(x in 240..271)for(z in 251..269)w.setBlockAndUpdate(BlockPos(x,79,z),Blocks.STONE.defaultBlockState())
        for(x in 244..268)for(y in 80..85)w.setBlockAndUpdate(BlockPos(x,y,255),Blocks.STONE.defaultBlockState())
        wide.forEachIndexed { i,at->
            fun cables(): Pair<UtilityCableElement,UtilityCableElement> {
                val a=six(p,f.spool(),at.below()) as UtilityCableElement
                val b=six(p,f.spool(),at.east().below()) as UtilityCableElement
                return a to b
            }
            val before=if(i==1)cables() else null
            val m=wall(p,"Wide Underground Meter Socket",at);meters+=m
            m.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));m.inventoryChange(m.inventory)
            val pair=before ?: cables()
            val r=Resistor(pair.second.getElectricalLoad(LRDU.Up,0),null).apply { resistance=24.0 };loads+=r
            pair.second.electricalComponentList.add(r);pair.second.reconnect()
            source(p,at.west().below())
        }
        analogMeter=wall(p,"North American Meter Base",analog)
        analogMeter.inventory.setItem(0,Eln.findItemStack("Analog Glass Service Meter",1));analogMeter.inventoryChange(analogMeter.inventory)
        analogMeter.energy.step(3600.0,1000.0) // Known pre-existing register for the dial demonstration.
        analogMeter.electricalComponentList.add(Resistor(analogMeter.load[2],null).apply { resistance=48.0 });analogMeter.reconnect();source(p,analog.above())
        spareMeter=wall(p,"North American Meter Base",spare);source(p,spare.above())
        val riser=wall(p,"Underground Service Entrance Riser",analog.below(2))
        check(!riser.allowsNewGridLinks)
        pad=f.placeMachine(Eln.findItemStack("Pad Mounted Neighbourhood Transformer",1),padPos) as IndustrialElement
        pad.front=Direction.ZP;pad.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[2],1));pad.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));pad.inventoryChange(pad.inventory);pad.reconnect()
        val h=padPos.east(3).below();w.removeBlock(h,false);w.setBlockAndUpdate(h.below(),Blocks.STONE.defaultBlockState())
        f.placeMachine(Eln.findItemStack("120/240V Underground Service Handhole",1),h)
        p.abilities.flying=true;p.onUpdateAbilities();view(p,base.east(8),4.0);w.setDayTime(6000)
    }
    private fun assertPowered() {
        for((i,m) in meters.withIndex()) {
            check(m.line[2].voltage in 238.0..241.0 && m.load[2].voltage in 238.0..241.0) { "Wide $i line=${m.line[2].voltage} load=${m.load[2].voltage}" }
            check(loads[i].power in 2350.0..2410.0) { "Wide $i resistor power ${loads[i].power}" }
            check(m.energy.importedKwh>0)
        }
    }
    private fun baked() {
        check(mc.level!!.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,wide[1],null)==null)
        check(mc.level!!.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,wide[1],null)!!.slots==1)
        passed+="Client overlay capability queries do not access server-only simulation nodes"
        val positions=wide+listOf(analog,spare,padPos,analog.below(2),padPos.east(3).below())
        val counts=positions.associate { at->
            val r=(mc.level!!.getBlockEntity(at) as TransparentNodeEntity).elementRender!!
            val mesh=checkNotNull(TerrainModels.meshFor(r)) { "Missing baked ${r.javaClass.simpleName}" }
            val vertices=mesh.batches.sumOf { it.vertices.size/9 };check(vertices>30)
            at.toShortString() to vertices
        }
        data["bakedVertices"]=counts
        for((pos,face) in listOf(analog to "analog_face",wide[1] to "smart_face")) {
            val r=(mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender!!
            check(TerrainModels.meshFor(r)!!.batches.any { it.material.texture.path.contains(face) }) { "Meter face plate did not bake" }
        }
        passed+="All service bodies and printed meter faces compile into nonempty baked terrain meshes"
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(out);Files.writeString(out.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"measurements" to data,"failure" to error?.stackTraceToString())))
        if(error==null)Eln.LOGGER.info("AMERICAN_SERVICE COMPLETE") else Eln.LOGGER.error("AMERICAN_SERVICE FAIL",error)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.americanServiceChecks") || done)return
        try {
            ticks++;check(++total<5000) { "American service timeout at $phase" }
            when(phase) {
                0->if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.renderDistance().set(5)
                    mc.createWorldOpenFlows().createFreshLevel("american-service-${System.currentTimeMillis()}",LevelSettings("American service",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(61061L,false,false),{ it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);phase=1;ticks=0
                }
                1->if(mc.player!=null && mc.screen==null && ticks>40) { work(::setup);phase=2 }
                2->if(task!!.isDone && ticks>220) {
                    task!!.join();baked()
                    work { p->assertPowered();data["wideLoadWatts"]=loads.map { it.power };passed+="Same-color downward entries supply real 2.4 kW loads in both cable/cabinet placement orders";view(p,wide[1]);GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),wide[1].x,wide[1].y,wide[1].z) };phase=3
                }
                3->if(task!!.isDone && mc.screen is HouseServiceGui && ticks>25) {
                    task!!.join();photo("wide-meter-gui")
                    val g=mc.screen!!;val b=g.javaClass.getDeclaredField("disconnect").apply { isAccessible=true }.get(g) as GuiButtonEln
                    g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0);phase=4;ticks=0
                }
                4->if(ticks>30) {
                    work { p->
                        check(meters[1].disconnected);check(meters[1].line[2].voltage>230);check(loads[1].power<1e-5) { "Adjacent cables bypassed meter: ${loads[1].power} W" }
                        passed+="Actual smart-meter GUI opens the customer lead while the utility side remains energized"
                        meters[1].setRemoteDisconnect(false);meters[0].setRemoteDisconnect(true)
                    };mc.setScreen(null);phase=5
                }
                5->if(task!!.isDone && ticks>35) {
                    task!!.join();work { p->
                        check(loads[1].power>2300 && loads[0].power<1e-5)
                        val old=meters[0];val energy=old.energy.importedKwh;val serial=old.meterSerial
                        val carried=old.inventory.removeItemNoUpdate(0);old.inventoryChange(old.inventory)
                        spareMeter.inventory.setItem(0,carried);spareMeter.inventoryChange(spareMeter.inventory)
                        check(spareMeter.energy.importedKwh==energy && spareMeter.meterSerial==serial && spareMeter.disconnected)
                        passed+="Removing and reinstalling the physical smart meter preserves its serial, register and open disconnect"
                        data["carriedMeterKwh"]=energy;view(p,analog)
                    };mc.options.hideGui=true;mc.gui.chat.clearMessages(false);phase=6
                }
                6->if(task!!.isDone && ticks>45) { task!!.join();photo("analog-meter");work { p->view(p,wide[1]) };phase=7 }
                7->if(task!!.isDone && ticks>45) { task!!.join();photo("smart-wide-meter");work { p->view(p,wide[0],2.6) };phase=8 }
                8->if(task!!.isDone && ticks>45) { task!!.join();photo("wide-socket-jaws");work { p->view(p,padPos,2.6) };phase=9 }
                9->if(task!!.isDone && ticks>45) { task!!.join();photo("pad-transformer-closed");work { p->pad.setEnabled(false);view(p,padPos,2.6) };phase=10 }
                10->if(task!!.isDone && ticks>45) { task!!.join();photo("pad-transformer-open");passed+="Analog, smart, open socket and rounded green pad cabinet render with the normal shader stack";finish(null) }
            }
        } catch(e: Throwable) { finish(e) }
    }
}
