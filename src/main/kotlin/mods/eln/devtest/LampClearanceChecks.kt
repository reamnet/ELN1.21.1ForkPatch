package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.sixnode.lampsocket.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.MoverType
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

/** Real saved cube ghosts, synchronized client shapes, player movement and world-reload regression. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object LampClearanceChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="lamp-clearance-${System.currentTimeMillis()}"
    private val base=BlockPos(256,70,256)
    private data class Fixture(val style: DecorativeLampStyle,val pos: BlockPos,val side: Direction)
    private val fixtures=listOf(DecorativeLampStyle.LUXURY_RINGS,DecorativeLampStyle.LUXURY_CASCADE,
        DecorativeLampStyle.LUXURY_BRANCH,DecorativeLampStyle.LUXURY_SPIRAL,DecorativeLampStyle.LUXURY_EMPIRE,
        DecorativeLampStyle.STRIP_SURFACE,DecorativeLampStyle.RIG_LINEAR,DecorativeLampStyle.FACTORY,
        DecorativeLampStyle.PARKING).mapIndexed { i,s -> Fixture(s,base.offset(i%4*10,if(s.luxury)10 else if(s==DecorativeLampStyle.PARKING)0 else 4,i/4*12),
            if(s==DecorativeLampStyle.PARKING)Direction.YN else Direction.YP) }
    private val foreign=base.west(8).above()
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(f: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ f(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun node(p: ServerPlayer,e: Fixture)=NativeCampaignFixtures(p.serverLevel(),p).node(e.pos) as SixNode
    private fun element(p: ServerPlayer,e: Fixture)=node(p,e).getElement(e.side) as LampSocketElement
    private fun cells(e: Fixture)= (Eln.sixNodeItem.getDescriptor(Eln.findItemStack(e.style.itemName,1)) as LampSocketDescriptor)
        .getGhostGroup(e.side,LRDU.Up)!!.elementList.map { e.pos.offset(it.x,it.y,it.z) }
    private fun record(s: String) { passed+=s;Eln.logger.info("LAMP_CLEARANCE PASS {}",s) }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("lamp-clearance-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("LAMP_CLEARANCE FAIL phase $phase",error) else Eln.logger.info("LAMP_CLEARANCE COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.lampClearanceChecks") || done)return
        try {
            check(++total<8000) { "Timeout phase $phase" };ticks++
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(34567L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();p.abilities.flying=true;p.onUpdateAbilities();p.teleportTo(w,260.0,74.0,262.0,150f,10f)
                        for(x in -12..38)for(z in -4..29) { val at=base.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        var legacy=0
                        for(e in fixtures) {
                            w.setBlockAndUpdate(if(e.side==Direction.YP)e.pos.above() else e.pos.below(),Blocks.STONE.defaultBlockState())
                            check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(e.style.itemName,1),p,w,e.pos,e.side.toFacing().opposite,.5f,.5f,.5f)) { "Place ${e.style}" }
                            val lamp=element(p,e)
                            lamp.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",e.style.bulbs));lamp.inventoryChange(lamp.inventory)
                            for(at in cells(e)) {
                                check(w.getBlockState(at).block===Eln.ghostBlock)
                                w.setBlock(at,Eln.ghostBlock.stateForMeta(GhostBlock.tCube),3)
                                check(!w.getBlockState(at).getCollisionShape(w,at).isEmpty);legacy++
                            }
                        }
                        val c=Coordinate(foreign.x,foreign.y,foreign.z,w)
                        Eln.ghostManager.createGhost(c,c.newWithOffset(-1,0,0),9876)
                        record("Reproduced $legacy solid legacy clearance cells across five grand chandeliers, long strips, a factory light and a parking lamp")
                    };waitFor(2,100)
                }
                2 -> if(settled()) {
                    server { p ->
                        val w=p.serverLevel()
                        for(e in fixtures) {
                            check(element(p,e).installedBulbs==e.style.bulbs)
                            check(w.getBlockState(e.pos).getCollisionShape(w,e.pos).isEmpty) { "Solid master ${e.style}" }
                            check(!w.getBlockState(e.pos).getShape(w,e.pos).isEmpty)
                            for(at in cells(e)) {
                                val state=w.getBlockState(at)
                                check(state.getValue(GhostBlock.SHAPE)==GhostBlock.tClearance) { "Unconverted ${e.style} at $at" }
                                check(state.getCollisionShape(w,at).isEmpty);check(!state.isLadder(w,at,p))
                                check(!state.getShape(w,at).isEmpty)
                                val ghost=Eln.ghostManager.getGhost(Coordinate(at.x,at.y,at.z,w))!!
                                check(ghost.observatorCoordonate==node(p,e).coordinate)
                            }
                        }
                        record("Existing cube reservations upgrade in place; all fixture masters and ghost cells are passable, selectable and retain their owners and bulbs")
                        check(!w.getBlockState(foreign).getCollisionShape(w,foreign).isEmpty)
                        val strip=fixtures.single { it.style==DecorativeLampStyle.STRIP_SURFACE }
                        val n=node(p,strip);n.sixNodeCacheBlock=Blocks.STONE
                        check(!w.getBlockState(strip.pos).getCollisionShape(w,strip.pos).isEmpty)
                        n.sixNodeCacheBlock=Blocks.AIR;n.needPublish=true
                        record("Unrelated machine ghosts and camouflage still have solid collision")
                        for(e in listOf(fixtures.first(),strip)) {
                            val at=cells(e).first()
                            p.teleportTo(w,at.x-.1,at.y+.05,at.z+.5,0f,0f)
                            p.noPhysics=false;p.setNoGravity(true)
                            val start=p.x;p.move(MoverType.SELF,Vec3(1.2,0.0,0.0))
                            check(p.x-start>1.19) { "Movement blocked at ${e.style}: ${p.x-start}" }
                        }
                        record("A real player moves through former chandelier and strip-light barriers without collision")
                        p.teleportTo(w,260.0,74.0,262.0,150f,10f)
                    };waitFor(3,40)
                }
                3 -> if(settled()) {
                    val w=mc.level!!
                    for(e in fixtures) {
                        check(w.getBlockState(e.pos).getCollisionShape(w,e.pos).isEmpty)
                        for(at in cells(e)) {
                            val state=w.getBlockState(at);check(state.block===Eln.ghostBlock)
                            check(state.getValue(GhostBlock.SHAPE)==GhostBlock.tClearance);check(state.getCollisionShape(w,at).isEmpty)
                        }
                    }
                    record("The client receives passable block states too, preventing invisible client-side barriers")
                    server { p ->
                        // Save a genuine old-state world after the per-instance migration has finished.
                        // Its next startup must upgrade saved metadata and restore owner tables correctly.
                        for(e in fixtures)for(at in cells(e))p.serverLevel().setBlock(at,Eln.ghostBlock.stateForMeta(GhostBlock.tCube),3)
                    };step(8)
                }
                8 -> if(settled()) { mc.level!!.disconnect();mc.disconnect(TitleScreen());step(4) }
                4 -> if(ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen clearance world") };step(5) }
                5 -> if(mc.player!=null && mc.level!=null && mc.screen==null)waitFor(6,100)
                6 -> if(settled()) {
                    server { p ->
                        val w=p.serverLevel()
                        for(e in fixtures)for(at in cells(e))check(w.getBlockState(at).getValue(GhostBlock.SHAPE)==GhostBlock.tClearance)
                        record("A cold restart of an old-state saved world automatically converts its reservations and preserves fixture inventories")
                        val e=fixtures.single { it.style==DecorativeLampStyle.STRIP_SURFACE }
                        w.destroyBlock(cells(e).first(),false,p)
                        check(w.getBlockState(e.pos).isAir)
                        check(cells(e).all { w.getBlockState(it).isAir })
                        for(other in fixtures.filter { it!=e })check(element(p,other).installedBulbs==other.style.bulbs)
                        record("Breaking a reserved cell still removes its fixture and reservations, leaving other lights intact")
                    };step(7)
                }
                7 -> if(settled())finish(null)
            }
        } catch(t: Throwable) { finish(t.cause ?: t) }
    }
}
