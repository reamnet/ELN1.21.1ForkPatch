package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.ghost.GhostGroup
import mods.eln.gui.GuiButtonEln
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
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
import kotlin.math.*

/** Opt-in production fixtures, real power and visual checks: -Deln.decorativeLightChecks=true. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object DecorativeLightingChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="decorative-lights-${System.currentTimeMillis()}"
    private val base=BlockPos(256,66,256)
    private val supplyPos=base.west(4).south(4)
    private const val channel="Fixture gallery"
    private data class Fixture(val style: DecorativeLampStyle,val pos: BlockPos,val side: Direction,val wired: Boolean)
    private val fixtures=buildList {
        for(wired in listOf(false,true)) for((i,style) in DecorativeLampStyle.entries.withIndex()) {
            if(wired && style.wirelessOnly) continue
            val y=when(style) { DecorativeLampStyle.WAREHOUSE,DecorativeLampStyle.RECESSED,DecorativeLampStyle.CHANDELIER,DecorativeLampStyle.DOT,DecorativeLampStyle.PORCH,DecorativeLampStyle.HOLDER -> 3; DecorativeLampStyle.DESK -> 1; else -> 0 }
            val side=when(style) { DecorativeLampStyle.WAREHOUSE,DecorativeLampStyle.PORCH,DecorativeLampStyle.HOLDER -> Direction.XN; DecorativeLampStyle.RECESSED,DecorativeLampStyle.CHANDELIER,DecorativeLampStyle.DOT -> Direction.YP; else -> Direction.YN }
            add(Fixture(style,base.offset((i%4)*8,y,(i/4)*8+if(wired) 24 else 0),side,wired))
        }
    }
    private val bulbs get()=Eln.sharedItem.subItemList.values.filterIsInstance<LampDescriptor>()
        .sortedWith(compareBy<LampDescriptor> { it.lampData.nominalU }.thenBy { it.name })
    private var phase=0; private var ticks=0; private var total=0; private var ready=0
    private var done=false; private var variant=0; private var ui=0; private var shot=0; private var off=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) {
        val s=mc.singleplayerServer!!
        work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s)
    }
    private fun complete(): Boolean { val w=work ?: return true;if(!w.isDone)return false;w.join();return true }
    private fun waitFor(n: Int,physicalTicks: Int=50) { ready=mc.singleplayerServer!!.tickCount+physicalTicks;step(n) }
    private fun settled()=complete() && mc.singleplayerServer!!.tickCount>=ready
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun node(p: ServerPlayer,pos: BlockPos)=(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,p.serverLevel())) as SixNode)
    private fun element(p: ServerPlayer,fixture: Fixture)=node(p,fixture.pos).getElement(fixture.side) as LampSocketElement
    private fun packet(block: DataOutputStream.()->Unit): DataInputStream {
        val b=ByteArrayOutputStream();DataOutputStream(b).use(block)
        return DataInputStream(ByteArrayInputStream(b.toByteArray()))
    }
    private fun place(p: ServerPlayer,stack: ItemStack,pos: BlockPos,side: Direction): mods.eln.node.six.SixNodeElement {
        val world=p.serverLevel()
        val support=when(side) { Direction.YN -> pos.below(); Direction.YP -> pos.above(); Direction.XN -> pos.west(); else -> error("Unsupported check mounting") }
        world.setBlockAndUpdate(support,if(side==Direction.YP) Blocks.WHITE_CONCRETE.defaultBlockState() else Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(stack,p,world,pos,side.toFacing().opposite,.5f,.5f,.5f)) { "Cannot place $stack at $pos/$side" }
        return node(p,pos).getElement(side)!!
    }
    private fun sourcePos(entry: Fixture)=if(entry.side==Direction.XN) entry.pos.north(2) else entry.pos.west(2)
    private fun source(p: ServerPlayer,pos: BlockPos,side: Direction,volts: Double) {
        val e=place(p,Eln.findItemStack("Electrical Source",1),pos,side) as ElectricalSourceElement
        e.readConfigTool(CompoundTag().apply { putDouble("voltage",volts) },p)
    }
    private fun voltage(p: ServerPlayer,pos: BlockPos,side: Direction,v: Double) {
        (node(p,pos).getElement(side) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",v) },p)
    }
    private fun bulb(p: ServerPlayer,e: LampSocketElement,d: LampDescriptor) {
        e.inventory.setItem(0,d.newItemStack(e.descriptor.bulbCapacity));e.inventoryChange(e.inventory)
    }
    private fun record(s: String) { passed+=s;Eln.logger.info("DECORATIVE_LIGHT PASS {}",s) }
    private fun checkLit(p: ServerPlayer,entry: Fixture,d: LampDescriptor) {
        val e=element(p,entry)
        check(e.installedBulbs==entry.style.bulbs)
        check(abs(e.electricalLoad.voltage-d.lampData.nominalU)<d.lampData.nominalU*.03+.1) { "${entry.style}: wrong voltage ${e.electricalLoad.voltage}" }
        check(e.sixNode!!.lightValue>=d.lampData.nominalLightValue-1) { "${entry.style}/${d.name}: dark ${e.sixNode!!.lightValue}" }
        check(abs(e.lampResistor.power-d.lampData.nominalP*entry.style.bulbs)<d.lampData.nominalP*entry.style.bulbs*.06+.1)
        if(!entry.wired) check(e.activeLampSupplyConnection)
    }
    private fun view(p: ServerPlayer,entry: Fixture) {
        val (dx,dy,dz,height)=when(entry.style) {
            DecorativeLampStyle.PARKING -> listOf(5.0,1.8,6.0,2.8)
            DecorativeLampStyle.WAREHOUSE -> listOf(2.3,-1.6,2.3,.35)
            DecorativeLampStyle.DESK -> listOf(1.5,.2,1.8,.4)
            DecorativeLampStyle.RECESSED -> listOf(1.0,-1.9,1.2,.9)
            DecorativeLampStyle.CHANDELIER -> listOf(1.5,-1.5,1.8,.6)
            DecorativeLampStyle.DOT -> listOf(.5,-1.4,.6,.8)
            DecorativeLampStyle.PORCH -> listOf(1.1,-1.0,1.2,.45)
            DecorativeLampStyle.HOLDER -> listOf(.65,-1.1,.8,.5)
        }
        val yaw=Math.toDegrees(atan2(dx,-dz)).toFloat()
        val pitch=Math.toDegrees(atan2(dy+p.eyeHeight-height,hypot(dx,dz))).toFloat()
        p.teleportTo(p.serverLevel(),entry.pos.x+.5+dx,entry.pos.y+dy,entry.pos.z+.5+dz,yaw,pitch)
    }
    private fun gui(p: ServerPlayer,entry: Fixture) = GuiHandler.open(p,GuiHandler.nodeBaseOpen+entry.side.int,p.serverLevel(),entry.pos.x,entry.pos.y,entry.pos.z)
    private fun clickPower() {
        val g=mc.screen as LampSocketGui
        val b=LampSocketGui::class.java.getDeclaredField("buttonPowerSource").apply { isAccessible=true }.get(g) as GuiButtonEln
        g.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0);g.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun capture(name: String)=Screenshot.grab(mc.gameDirectory,"decorative-lights-$name.png",mc.mainRenderTarget) {}
    private fun finish(error: Throwable?) {
        done=true;mc.options.hideGui=false
        Files.writeString(mc.gameDirectory.toPath().resolve("decorative-light-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf("world" to worldName,"bulbVariants" to bulbs.size,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("DECORATIVE_LIGHT FAIL",error) else Eln.logger.info("DECORATIVE_LIGHT COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.decorativeLightChecks") || done)return
        try {
            check(++total<24000) { "Timeout at phase $phase variant $variant" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),
                        WorldOptions(34567L,false,false), { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        p.setGameMode(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities();view(p,fixtures[0])
                        val world=p.serverLevel();world.dayTime=6000
                        for(x in 247..286)for(z in 250..294)world.setBlockAndUpdate(BlockPos(x,65,z),Blocks.STONE.defaultBlockState())
                        LampChannelMemory.remember(p.persistentData,channel)
                        val supply=place(p,Eln.findItemStack("240V Lamp Supply",1),supplyPos,Direction.YN) as LampSupplyElement
                        supply.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!
                        supply.inventory!!.setItem(0,f(p).spool(1.0));supply.inventoryChange(supply.inventory)
                        supply.networkUnserialize(packet { writeByte(1);writeByte(0);writeUTF(channel) })
                        supply.networkUnserialize(packet { writeByte(LampSupplyElement.setTransmissionRange.toInt());writeInt(128) })
                        place(p,f(p).spool(),supplyPos.west(),Direction.YN);source(p,supplyPos.west(2),Direction.YN,120.0)
                        val led=bulbs.single { it.lampData.nominalU==120.0 && it.lampData.technology.lampType=="led" }
                        for(entry in fixtures) {
                            if(entry.side==Direction.XN) for(y in -3..1)for(z in -3..2)world.setBlockAndUpdate(entry.pos.offset(-1,y,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState())
                            if(entry.side==Direction.YP)for(x in -3..2)for(z in -1..1)world.setBlockAndUpdate(entry.pos.offset(x,1,z),Blocks.WHITE_CONCRETE.defaultBlockState())
                            if(entry.style==DecorativeLampStyle.DESK)world.setBlockAndUpdate(entry.pos.below(),Blocks.BIRCH_PLANKS.defaultBlockState())
                            val e=place(p,Eln.findItemStack(entry.style.itemName,1),entry.pos,entry.side) as LampSocketElement
                            if(entry.style==DecorativeLampStyle.PARKING && !entry.wired) {
                                // Simulate a saved 3.0.12 pole; the first process pass must migrate its old clearance.
                                Eln.ghostManager.removeGhostAndBlockWithObserver(e.coordinate,e.descriptor.ghostGroupUuid)
                                val old=GhostGroup().apply {
                                    addElement(1,0,0);addElement(2,0,0)
                                    for(y in -1..1)for(z in -1..1)addElement(3,y,z)
                                }.newRotate(entry.side)
                                check(old.plot(e.coordinate!!,e.coordinate!!,e.descriptor.ghostGroupUuid))
                            }
                            check(e.lampSupplyChannel==channel)
                            val held=led.newItemStack(entry.style.bulbs+1);p.setItemInHand(InteractionHand.MAIN_HAND,held)
                            check(e.onBlockActivated(p,entry.side,.5f,.5f,.5f))
                            check(e.installedBulbs==entry.style.bulbs)
                            p.setItemInHand(InteractionHand.MAIN_HAND,f(p).spool(2.0));check(e.onBlockActivated(p,entry.side,.5f,.5f,.5f))
                            if(entry.wired) {
                                e.lampSupplyChannel="Unused wired fixtures";e.needPublish()
                                val between=if(entry.side==Direction.XN)entry.pos.north() else entry.pos.west()
                                place(p,f(p).spool(),between,entry.side);source(p,sourcePos(entry),entry.side,120.0)
                            }
                        }
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        for((i,style)in DecorativeLampStyle.entries.withIndex())p.inventory.setItem(i,Eln.findItemStack(style.itemName,1))
                        p.inventory.setChanged();p.containerMenu.broadcastChanges()
                    };waitFor(2,120)
                }
                2 -> if(settled()) {
                    server { p ->
                        val led=bulbs.single { it.lampData.nominalU==120.0 && it.lampData.technology.lampType=="led" }
                        fixtures.filterNot { it.wired }.forEach { checkLit(p,it,led) }
                        for(entry in fixtures.filter { it.style==DecorativeLampStyle.PARKING }) {
                            val e=element(p,entry)
                            val ghosts=Eln.ghostManager.ghostTable.values.filter { it.observatorCoordonate==e.coordinate }
                            check(ghosts.size==13)
                            check(ghosts.any { it.elementCoordinate!!.y==entry.pos.y+5 })
                            check(ghosts.none { it.elementCoordinate!!.y==entry.pos.y+3 && (it.elementCoordinate!!.x!=entry.pos.x || it.elementCoordinate!!.z!=entry.pos.z) })
                        }
                        record("All fixtures place on their proper surfaces, accept bulb batches by hand and light wirelessly; old parking clearance migrates to the taller pole")
                        voltage(p,supplyPos.west(2),Direction.YN,0.0)
                    };waitFor(19,30)
                }
                3 -> if(settled()) {
                    server { p ->
                        val d=bulbs[variant];fixtures.filterNot { it.wired }.forEach { checkLit(p,it,d) }
                        Eln.logger.info("DECORATIVE_BULB PASS {}",d.name);variant++
                        if(variant<bulbs.size) {
                            val next=bulbs[variant];voltage(p,supplyPos.west(2),Direction.YN,next.lampData.nominalU)
                            fixtures.filterNot { it.wired }.forEach { bulb(p,element(p,it),next) }
                        } else {
                            record("Every registered bulb technology and voltage works wirelessly in all fixtures with per-bulb power draw")
                            voltage(p,supplyPos.west(2),Direction.YN,0.0)
                        }
                    };step(4)
                }
                4 -> if(complete()) { if(variant<bulbs.size)waitFor(3,140) else waitFor(20,30) }
                5 -> if(ticks>20) {
                    val entry=fixtures.filter { it.wired }[ui]
                    server { p ->view(p,entry);gui(p,entry) };step(6)
                }
                6 -> if(complete() && mc.screen is LampSocketGui && ticks>20) {
                    val render=(mc.screen as LampSocketGui).render
                    check(render.bulbCount==render.descriptor.bulbCapacity) { "Client omitted installed bulb count" }
                    clickPower();waitFor(7)
                }
                7 -> if(settled()) {
                    val entry=fixtures.filter { it.wired }[ui]
                    server { p ->
                        check(!element(p,entry).poweredByLampSupply)
                        val led=bulbs.single { it.lampData.nominalU==120.0 && it.lampData.technology.lampType=="led" };checkLit(p,entry,led)
                    };mc.setScreen(null);step(8)
                }
                8 -> if(complete()) { ui++;if(ui<fixtures.count { it.wired })step(5) else { record("All wired fixtures switch through the actual GUI and run on real same-plane cables with correct client bulb counts");shot=0;step(9) } }
                9 -> if(ticks>10) { server { p -> view(p,fixtures.filterNot { it.wired }[shot]) };step(10) }
                10 -> if(complete() && ticks>20) {
                    mc.options.hideGui=true;step(11)
                }
                11 -> if(ticks>5) {
                    capture("${fixtures.filterNot { it.wired }[shot].style.model}-${if(off) "off" else "on"}")
                    shot++
                    if(shot<DecorativeLampStyle.entries.size)step(9) else if(!off) {
                        off=true;shot=0;server { p ->voltage(p,supplyPos.west(2),Direction.YN,0.0);fixtures.filter { it.wired }.forEach { voltage(p,sourcePos(it),it.side,0.0) } };waitFor(12,100)
                    } else { record("All fixture models render lit and unlit, with dark off-state lenses");step(13) }
                }
                12 -> if(settled()) { server { p -> fixtures.forEach { check(element(p,it).sixNode!!.lightValue==0) } };step(9) }
                13 -> if(ticks>10) {
                    mc.options.hideGui=false
                    server { p ->
                        voltage(p,supplyPos.west(2),Direction.YN,120.0);fixtures.filter { it.wired }.forEach { voltage(p,sourcePos(it),it.side,120.0) }
                        val entry=fixtures.single { !it.wired && it.style==DecorativeLampStyle.CHANDELIER };view(p,entry);gui(p,entry)
                    };waitFor(14)
                }
                14 -> if(settled() && mc.screen is LampSocketGui && ticks>20) { capture("chandelier-gui");mc.setScreen(null);mc.level!!.disconnect();mc.disconnect(TitleScreen());step(15) }
                15 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen gallery") };step(16) }
                16 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(17,140)
                17 -> if(settled()) {
                    server { p ->
                        val led=bulbs.single { it.lampData.nominalU==120.0 && it.lampData.technology.lampType=="led" }
                        for(entry in fixtures) { val e=element(p,entry);check(e.poweredByLampSupply==!entry.wired);checkLit(p,entry,led) }
                    };step(18)
                }
                18 -> if(complete()) { record("Saved-world restart preserves all fixture inventories, channels and power modes, with no cable replacement");finish(null) }
                19 -> if(settled()) {
                    server { p ->
                        val d=bulbs[variant];fixtures.filterNot { it.wired }.forEach { bulb(p,element(p,it),d) }
                        voltage(p,supplyPos.west(2),Direction.YN,d.lampData.nominalU)
                    };waitFor(3,140)
                }
                20 -> if(settled()) {
                    server { p ->
                        val led=bulbs.single { it.lampData.nominalU==120.0 && it.lampData.technology.lampType=="led" }
                        fixtures.filterNot { it.wired }.forEach { bulb(p,element(p,it),led) };voltage(p,supplyPos.west(2),Direction.YN,120.0)
                    };step(21)
                }
                21 -> if(complete())step(5)
            }
        } catch(t: Throwable) { finish(t) }
    }
}
