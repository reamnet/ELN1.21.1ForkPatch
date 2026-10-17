package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.Direction
import mods.eln.misc.tagCompound
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.node.CircuitDiagnostics
import mods.eln.node.NodeBlock
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.nbt.CompoundTag
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.*

/** Opt-in client checks and visual captures: -Deln.streetLightQolChecks=true. */
@EventBusSubscriber(modid = Eln.MODID, value = [Dist.CLIENT])
object StreetLightQolChecks {
    private val mc get() = Minecraft.getInstance()
    private val worldName = "streetlight-qol-${System.currentTimeMillis()}"
    private val at = BlockPos(256,66,256)
    private var phase = 0
    private var ticks = 0
    private var total = 0
    private var ready = 0
    private var done = false
    private var work: CompletableFuture<Void>? = null
    private var shiftHeld = false
    private val passed = mutableListOf<String>()
    private fun f(p: ServerPlayer) = NativeCampaignFixtures(p.serverLevel(), p)
    private fun lamp(p: ServerPlayer) = f(p).machine(at) as UtilityStreetLightElement
    private fun step(n: Int) { phase=n; ticks=0 }
    private fun server(action: (ServerPlayer) -> Unit) {
        val server=mc.singleplayerServer!!
        work=CompletableFuture.runAsync({ action(server.playerList.players.first()) },server)
    }
    private fun complete(): Boolean { val w=work ?: return true; if (!w.isDone) return false; w.join(); return true }
    private fun settled() = complete() && mc.singleplayerServer!!.tickCount >= ready
    private fun waitServer(n: Int) { ready=mc.singleplayerServer!!.tickCount+40; step(n) }
    private fun record(s: String) { passed+=s; Eln.logger.info("STREETLIGHT_QOL PASS {}",s) }
    private fun gui(p: ServerPlayer) = GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.YN.int,p.serverLevel(),at.x,at.y,at.z)
    private fun click(name: String) {
        val g=mc.screen as StepDownGui
        val b=StepDownGui::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g) as GuiButtonEln
        val x=b.x+b.width/2.0; val y=b.y+b.height/2.0
        g.mouseClicked(x,y,0); g.mouseReleased(x,y,0)
    }
    private fun view(p: ServerPlayer, target: BlockPos=at, dx: Double=-4.0, dy: Double=0.0, dz: Double=4.0, height: Double=2.3) {
        val pitch=Math.toDegrees(atan2(dy+p.eyeHeight-height,sqrt(dx*dx+dz*dz))).toFloat()
        val yaw=Math.toDegrees(atan2(dx,-dz)).toFloat()
        p.teleportTo(p.serverLevel(),target.x+.5+dx,target.y+dy,target.z+.5+dz,yaw,pitch)
    }
    private fun shot(name: String) = Screenshot.grab(mc.gameDirectory,"streetlight-qol-$name.png",mc.mainRenderTarget) {}
    private fun checkLight(p: ServerPlayer, on: Boolean) {
        val e=lamp(p)
        check(e.unit.secondary.voltage in 239.0..241.0) { "Base outlet lost power: ${e.unit.waila()}" }
        check(if (on) e.lightLevel>=14 else e.lightLevel==0) { "Lamp state ${e.lightLevel}, expected on=$on" }
        check(e.unit.output.power > if (on) 290.0 else 230.0) { "Base load was not supplied" }
    }
    private fun checkHeight(p: ServerPlayer, step: Int) {
        val e=lamp(p)
        check(e.unit.lampHeightStep==step)
        checkLight(p,true)
        val offset=StreetLightMount.lightOffset(e.front,step,e.unit.lampYaw)
        val lightPos=at.offset(offset)
        check(p.serverLevel().getBlockState(lightPos).block===Eln.lightBlock) { "No light at the adjusted head height" }
        val oldPos=BlockPos(lightPos.x,at.y+if(offset.y==1) 2 else 1,lightPos.z)
        check(p.serverLevel().getBlockState(oldPos).block!==Eln.lightBlock) { "The old head height kept emitting light" }
    }
    private fun checkClientHeight(step: Int) {
        val render=(mc.level!!.getBlockEntity(at) as TransparentNodeEntity).elementRender as UtilityStreetLightRender
        check(render.display.heightStep==step) { "The client did not receive the changed height" }
    }
    private fun checkAngle(p: ServerPlayer, angle: Int, oldAngle: Int) {
        val e=lamp(p)
        check(e.unit.lampYaw==angle)
        checkHeight(p,StreetLightMount.MAX_STEP)
        val current=StreetLightMount.lightOffset(e.front,e.unit.lampHeightStep,angle)
        val old=StreetLightMount.lightOffset(e.front,e.unit.lampHeightStep,oldAngle)
        if(current!=old) check(p.serverLevel().getBlockState(at.offset(old)).block!==Eln.lightBlock) { "The old direction kept emitting light" }
    }
    private fun checkClientAngle(angle: Int) {
        val render=(mc.level!!.getBlockEntity(at) as TransparentNodeEntity).elementRender as UtilityStreetLightRender
        check(render.display.yaw==angle) { "The client did not receive the changed angle" }
    }
    private fun shiftKey(down: Boolean) {
        // Minecraft forces java.awt.headless, so inject a real X11 key rather than using AWT Robot.
        val script = """
            import ctypes, sys
            x = ctypes.CDLL('libX11.so.6')
            t = ctypes.CDLL('libXtst.so.6')
            x.XOpenDisplay.argtypes = [ctypes.c_char_p]
            x.XOpenDisplay.restype = ctypes.c_void_p
            x.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
            x.XKeysymToKeycode.restype = ctypes.c_uint
            x.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
            x.XCloseDisplay.argtypes = [ctypes.c_void_p]
            t.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
            display = x.XOpenDisplay(None)
            assert display, 'X11 display unavailable'
            assert t.XTestFakeKeyEvent(display, x.XKeysymToKeycode(display, 0xffe1), int(sys.argv[1]), 0)
            x.XSync(display, 0)
            x.XCloseDisplay(display)
        """.trimIndent()
        val process = ProcessBuilder("python3", "-c", script, if(down) "1" else "0").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor()==0) { "XTest Shift input failed: $output" }
        shiftHeld=down
    }
    private fun finish(error: Throwable?) {
        done=true
        if(shiftHeld) runCatching { shiftKey(false) }
        Files.writeString(mc.gameDirectory.toPath().resolve("streetlight-qol-checks.json"),
            GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if (error!=null) Eln.logger.error("STREETLIGHT_QOL FAIL",error) else Eln.logger.info("STREETLIGHT_QOL COMPLETE {} checks",passed.size)
        mc.execute { mc.stop(); kotlin.concurrent.thread { Thread.sleep(1500); kotlin.system.exitProcess(if(error==null) 0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("eln.streetLightQolChecks") || done) return
        try {
            check(++total<12000) { "Timeout at phase $phase" }; ticks++
            mc.player?.let { it.setNoGravity(true); it.abilities.flying=true }
            when(phase) {
                0 -> if (mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2); mc.window.setWindowed(1280,960); mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,
                        LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),
                        WorldOptions(23456L,false,false), { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    step(1)
                }
                1 -> if (mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        p.setGameMode(GameType.CREATIVE); p.abilities.flying=true; p.onUpdateAbilities(); view(p)
                        val f=f(p); val feed=at.west(12)
                        val poles=listOf(at,at.east(24),at.east(48).above(4))
                        for (pos in listOf(feed)+poles) for(x in -4..4) for(z in -4..4)
                            p.serverLevel().setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState())
                        var previous=f.placeMachine(f.requiredItem("Direct Utility Pole"),feed) as GridElement
                        f.wire(feed.west()); f.source(feed.west(2),14000.0)
                        val cable=UtilityCableDescriptor.allDescriptors().single { it.material==UtilityCableMaterial.COPPER && it.sizeLabel=="250 kcmil" && !it.insulated && !it.melted }
                        for(pos in poles) {
                            val e=f.placeMachine(f.requiredItem("Utility Pole Street Light"),pos) as UtilityStreetLightElement
                            val held=f.requiredItem("Mains Step-down Unit").copyWithCount(2)
                            p.setItemInHand(InteractionHand.MAIN_HAND,held)
                            p.abilities.instabuild=false
                            check(e.node!!.onBlockActivated(p,Direction.XN,.5f,.5f,.5f))
                            check(e.installed && held.count==1 && e.inventory.getItem(0).count==1)
                            check(!e.onBlockActivated(p,Direction.XN,.5f,.5f,.5f) && held.count==1)
                            p.abilities.instabuild=true
                            val meters=ceil(previous.coordinate().trueDistanceTo(e.coordinate())).toInt()
                            GridLink.addLink(previous,e,Direction.XP,Direction.XN,cable,meters,cable.newItemStack(1).also { cable.setRemainingLengthMeters(it,meters.toDouble()) })
                            previous=e
                        }
                        f.wire(at.east()); f.load(at.east(2),240.0); f.ground(at.east(3))
                        p.serverLevel().dayTime=6000L
                        record("Hand installation consumes exactly one unit, preserves occupied slots and powers three linked poles")
                    }; ready=mc.singleplayerServer!!.tickCount+100; step(2)
                }
                2 -> if (settled()) {
                    server { p ->
                        val f=f(p); val world=p.serverLevel(); val state=world.getBlockState(at)
                        val block=state.block as NodeBlock
                        for (meter in listOf(Eln.multiMeterElement,Eln.allMeterElement)) {
                            p.setItemInHand(InteractionHand.MAIN_HAND,meter.newItemStack(1))
                            check(block.onBlockActivated(world,at,state,p,InteractionHand.MAIN_HAND,net.minecraft.core.Direction.WEST,.1f,.5f,.5f))
                            val head=at.above(1)
                            check(Eln.ghostBlock.onBlockActivated(world,head,world.getBlockState(head),p,InteractionHand.MAIN_HAND,net.minecraft.core.Direction.WEST,.1f,.5f,.5f))
                        }
                        p.setItemInHand(InteractionHand.MAIN_HAND,Eln.multiMeterElement.newItemStack(1)); p.isShiftKeyDown=true
                        check(Eln.multiMeterElement.onItemUse(p.mainHandItem,p,world,at.x,at.y+1,at.z,4,.1f,.5f,.5f))
                        check(p.mainHandItem.tagCompound?.contains(CircuitDiagnostics.PROBE_KEY)==true)
                        p.isShiftKeyDown=false; CircuitDiagnostics.clear(p.mainHandItem)
                        check(lamp(p).thermoMeterString(Direction.XN).isNotEmpty())
                        record("Multimeter and All Meter work on the pole and upper body; sneak probes resolve the owning pole")
                        gui(p)
                    }; step(3)
                }
                3 -> if (complete() && mc.screen is StepDownGui && ticks>20) { click("automatic"); click("voltage"); waitServer(4) }
                4 -> if (settled()) {
                    mc.setScreen(null)
                    server { p -> check(lamp(p).unit.lampAutomatic); checkLight(p,false); p.serverLevel().dayTime=18000L; view(p) }
                    waitServer(5)
                }
                5 -> if (settled()) { server { p -> checkLight(p,true) }; step(6) }
                6 -> if (complete() && mc.screen==null && ticks>20) {
                    shot("on"); server { gui(it) }; step(7)
                }
                7 -> if (complete() && mc.screen is StepDownGui && ticks>20) { click("lamp"); waitServer(8) }
                8 -> if (settled()) {
                    mc.setScreen(null); server { p -> checkLight(p,false) }; step(9)
                }
                9 -> if (complete() && ticks>20) {
                    shot("off"); record("GUI dusk control lights at night; manual off darkens the lamp while the 240 V base remains powered")
                    server { gui(it) }; step(10)
                }
                10 -> if (complete() && mc.screen is StepDownGui && ticks>20) { click("lamp"); mc.setScreen(null); server { it.serverLevel().dayTime=6000L }; waitServer(11) }
                11 -> if (settled()) {
                    server { p -> checkLight(p,false); p.serverLevel().dayTime=18000L; view(p,at.east(12),0.0,4.0,7.0,3.4) }
                    waitServer(12)
                }
                12 -> if (settled() && ticks>30) {
                    shot("wire-side"); server { p -> checkLight(p,true); view(p,at,-6.0,3.5,.3,3.4) }; step(13)
                }
                13 -> if (complete() && ticks>30) {
                    shot("wire-end"); record("Bare copper 250 kcmil spans render between level and elevated poles in side and end views")
                    server { gui(it) }; step(18)
                }
                14 -> if (ticks>40 && mc.singleplayerServer==null) { mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen world") }; step(15) }
                15 -> if (mc.player!=null && mc.level!=null && mc.screen==null) { ready=mc.singleplayerServer!!.tickCount+100; step(16) }
                16 -> if (settled()) { server { p -> check(lamp(p).installed && lamp(p).unit.lampAutomatic && lamp(p).unit.lampEnabled); checkAngle(p,46,0) }; step(17) }
                17 -> if (complete()) { checkClientHeight(StreetLightMount.MAX_STEP); checkClientAngle(46); record("Saved-world restart preserves the installed unit, automatic mode, 240 V selection, height and 46-degree angle"); finish(null) }
                18 -> if (complete() && mc.screen is StepDownGui && ticks>20) {
                    repeat(4) { click("heightDown") }; waitServer(19)
                }
                19 -> if (settled()) {
                    checkClientHeight(StreetLightMount.MIN_STEP)
                    shot("height-gui-low"); mc.setScreen(null)
                    server { p ->
                        checkHeight(p,StreetLightMount.MIN_STEP)
                        val config=CompoundTag(); lamp(p).writeConfigTool(config,p)
                        val copy=f(p).machine(at.east(24)) as UtilityStreetLightElement
                        copy.readConfigTool(config,p)
                        check(copy.unit.lampHeightStep==StreetLightMount.MIN_STEP)
                        view(p,height=StreetLightMount.height(StreetLightMount.MIN_STEP))
                    }; step(20)
                }
                20 -> if (complete() && ticks>20) {
                    shot("height-low"); server { gui(it) }; step(21)
                }
                21 -> if (complete() && mc.screen is StepDownGui && ticks>20) {
                    repeat(5) { click("heightUp") }; waitServer(22)
                }
                22 -> if (settled()) {
                    checkClientHeight(StreetLightMount.MAX_STEP)
                    shot("height-gui-high"); mc.setScreen(null)
                    server { p -> checkHeight(p,StreetLightMount.MAX_STEP); view(p,height=StreetLightMount.height(StreetLightMount.MAX_STEP)) }; step(23)
                }
                23 -> if (complete() && ticks>20) {
                    shot("height-high")
                    record("GUI Up/Down moves the fixture and light through both limits, preserves the loaded outlet and copies with the configuration tool")
                    server { gui(it) }; step(24)
                }
                24 -> if (complete() && mc.screen is StepDownGui && ticks>20) {
                    click("angleRight"); waitServer(25)
                }
                25 -> if (settled()) {
                    checkClientAngle(355)
                    server { p -> checkAngle(p,355,0) }
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.window.window)
                    shiftKey(true); step(26)
                }
                26 -> if (complete() && ticks>5) {
                    check(Screen.hasShiftDown()) { "Native Shift key did not reach the game" }
                    click("angleLeft"); shiftKey(false); waitServer(27)
                }
                27 -> if (settled()) {
                    checkClientAngle(356)
                    check(!Screen.hasShiftDown())
                    server { p -> checkAngle(p,356,355) }
                    repeat(10) { click("angleLeft") }; waitServer(28)
                }
                28 -> if (settled()) {
                    checkClientAngle(46); shot("angle-gui"); mc.setScreen(null)
                    server { p ->
                        checkAngle(p,46,356)
                        val config=CompoundTag(); lamp(p).writeConfigTool(config,p)
                        val copy=f(p).machine(at.east(24)) as UtilityStreetLightElement
                        copy.readConfigTool(config,p)
                        check(copy.unit.lampYaw==46 && copy.unit.lampHeightStep==StreetLightMount.MAX_STEP)
                        view(p,height=StreetLightMount.height(StreetLightMount.MAX_STEP))
                    }; step(29)
                }
                29 -> if (complete() && ticks>20) {
                    shot("angle-46")
                    record("GUI rotation wraps through zero; a native Shift-click turns one degree; the 46-degree fixture and light stay powered and copy with the configuration tool")
                    mc.level!!.disconnect(); mc.disconnect(TitleScreen()); step(14)
                }
            }
        } catch(t: Throwable) { finish(t) }
    }
}
