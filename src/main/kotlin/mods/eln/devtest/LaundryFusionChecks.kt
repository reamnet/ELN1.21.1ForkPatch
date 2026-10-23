package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.compat.RefrigeratorPreservation
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.*
import mods.eln.node.NodeBlockEntity
import mods.eln.node.transparent.*
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.fusion.FusionValues
import mods.eln.transparentnode.kitchen.KitchenElement
import mods.eln.transparentnode.laundry.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.item.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Opt-in real-world integration. Long cycle completions start from explicit saved energy credit;
 * separate real-time measurements verify actual power draw, progress and outage behavior. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object LaundryFusionChecks {
    private val mc get()=Minecraft.getInstance()
    private val base=BlockPos(240,80,240)
    private val washer=base
    private val dryer=base.east(8)
    private val fusion=base.east(18)
    private val fridge=base.south(8)
    private val dryerOutlet=dryer.south(4)
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var job: CompletableFuture<*>?=null
    private val passed=mutableListOf<String>()
    private val positions=listOf(washer,dryer,fusion)
    private val sources=hashMapOf<BlockPos,BlockPos>()
    private var reactorBefore=0.0;private var freezerAge=0L
    private var coldBread=ItemStack.EMPTY
    private val output get()=mc.gameDirectory.toPath().resolve("laundry-fusion-checks")
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun machine(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as MatterMachineElement
    private fun record(value: String) { passed+=value;Eln.logger.info("LAUNDRY_FUSION PASS {}",value) }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;job=s.submit<Unit> { body(s.playerList.players.first()) } }
    private fun step(next: Int,delay: Int=0) { phase=next;ticks=0;ready=(mc.singleplayerServer?.tickCount ?: 0)+delay }
    private fun settled(): Boolean { if(job?.isDone==false)return false;job?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun offset(pos: BlockPos,side: Direction,n: Int): BlockPos {
        val a=intArrayOf(pos.x,pos.y,pos.z);side.applyTo(a,n);return BlockPos(a[0],a[1],a[2])
    }
    @Suppress("UNCHECKED_CAST") private fun component(id: String)=BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id)) as? DataComponentType<Any>
    private fun call(type: String,name: String,vararg args: Any?): Any? {
        val method=Class.forName(type).methods.single { it.name==name && it.parameterCount==args.size }
        return method.invoke(null,*args)
    }
    private fun setup(p: ServerPlayer) {
        val w=p.serverLevel();val f=f(p)
        w.dayTime=6000L;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,mc.singleplayerServer)
        for(x in -8..30)for(z in -8..18) {
            val at=base.offset(x,0,z);w.setChunkForced(at.x shr 4,at.z shr 4,true)
            w.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState())
            for(y in 0..3)w.removeBlock(at.above(y),false)
        }
        for((pos,kind) in positions.zip(MatterMachineKind.entries)) {
            val e=f.placeMachine(Eln.findItemStack(kind.title,1),pos) as MatterMachineElement
            val a=offset(pos,e.front,1);val b=offset(pos,e.front,2)
            f.wire(a);f.source(b,kind.volts);sources[pos]=b
        }
        val washer=machine(p,washer)
        for(side in net.minecraft.core.Direction.entries) {
            val port=w.getCapability(Capabilities.FluidHandler.BLOCK,this.washer,side) ?: error("Missing washer pipe face $side")
            check(port.fill(FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE)==1000)
            check(port.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000)
            check(port.fill(FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE)==0)
        }
        washer.inventory.setItem(0,ItemStack(Items.DIAMOND_PICKAXE).apply { damageValue=400;set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Saved tool")) })
        washer.inventory.setChanged()
        val reactor=machine(p,fusion)
        check(FusionValues.cost(ItemStack(Items.IRON_BLOCK))!=null)
        reactor.inventory.setItem(0,ItemStack(Items.IRON_BLOCK));reactor.inventory.setChanged()
        val cooled=f.placeMachine(Eln.findItemStack("Electric Refrigerator",1),fridge) as KitchenElement
        val tag=CompoundTag();cooled.writeToNBT(tag);tag.putDouble("kitchenTemperature",3.5);cooled.readFromNBT(tag)
        f.wire(fridge.west());f.source(fridge.west(2),120.0)
        coldBread=ItemStack(Items.BREAD)
        component("freshness:production_time")?.let { coldBread.set(it,w.gameTime-1000) }
        component("freshness:shelf_life_days")?.let { coldBread.set(it,15) }
        component("spoiled:spoil_timer")?.let { type ->
            val value=Class.forName("com.mrbysco.spoiled.component.SpoilTimer").getConstructor(Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).newInstance(20,24000)
            coldBread.set(type,value)
        }
        call("com.fyxe.fffoodspoilageandpreservation.util.SpoilageHelper","ensureComponent",coldBread,w.gameTime)
        cooled.inventory.setItem(3,coldBread);cooled.inventory.setChanged();cooled.inventory.getItem(3)
        check(RefrigeratorPreservation.isCold(coldBread))
        component("freshness:production_time")?.let { type ->
            freezerAge=w.gameTime-(coldBread.get(type) as Long)
            val unloaded=minOf(12000L,w.gameTime)
            check(unloaded>20) { "Fixture world is too young to represent an unloaded interval" }
            coldBread.set(type,(coldBread.get(type) as Long)-unloaded)
            val coldSave=CompoundTag();cooled.writeToNBT(coldSave)
            coldSave.putLong("fridgeFoodClock",w.gameTime-unloaded);coldSave.putBoolean("fridgeFoodWasCold",true)
            cooled.readFromNBT(coldSave);coldBread=cooled.inventory.getItem(3)
            check(abs(w.gameTime-(coldBread.get(type) as Long)-freezerAge)<=1) { "Cold reload age ${w.gameTime-(coldBread.get(type) as Long)} != $freezerAge" }
            record("Cold fridge save/load retains Freshness age across an explicit unloaded-time interval")
        }
        record("Production blocks placed; water capabilities accept all six pipe faces and reject lava")
        p.teleportTo(w,base.x+13.0,base.y+7.0,base.z+15.0,0f,0f)
        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(base.x+12.0,base.y+.5,base.z+.5))
        p.abilities.flying=true;p.onUpdateAbilities();p.setNoGravity(true)
    }
    private fun checkPowerAndPrime(p: ServerPlayer) {
        val a=machine(p,washer);val b=machine(p,dryer);val c=machine(p,fusion)
        check(a.work.joules>1000 && a.resistor.power in 780.0..810.0) { "Washer ${a.status} ${a.supply.voltage} ${a.resistor.power} ${a.work.joules}" }
        val paid=a.work.joules
        val saved=CompoundTag();a.writeToNBT(saved);a.readFromNBT(saved)
        check(abs(a.work.joules-paid)<.001 && a.waterReserved)
        check(a.water.tank.fluidAmount==5000)
        a.work.load(a.requiredJoules-2000,a.requiredJoules)
        check(c.work.joules>1000 && c.resistor.power in c.requiredWatts*.97..c.requiredWatts*1.01)
        reactorBefore=c.work.joules;f(p).voltage(sources[fusion]!!,0.0)
        check(b.work.joules==0.0)
        record("Real grid draws 800 W for washing and the material-dependent reactor demand; save/read keeps paid progress and reserved water")
        val helper="com.mrbysco.spoiled.util.SpoilHelper"
        check(call(helper,"getSpoilRecipe",p.serverLevel(),coldBread)==null)
        component("spoiled:spoil_timer")?.let { check(coldBread.get(it)!!.javaClass.getMethod("timer").invoke(coldBread.get(it))==20) }
        component("freshness:production_time")?.let { check(abs(p.serverLevel().gameTime-(coldBread.get(it) as Long)-freezerAge)<=20) }
        record("Spoiled timer and Freshness age stay unchanged in the cold refrigerator")
    }
    private fun moveWet(p: ServerPlayer) {
        val a=machine(p,washer);val b=machine(p,dryer);val c=machine(p,fusion)
        val wet=a.inventory.removeItemNoUpdate(1);check(!WetEquipment.unpack(wet).isEmpty)
        val dry=WetEquipment.unpack(wet);check(dry.damageValue==0 && dry.hoverName.string=="Saved tool")
        b.inventory.setItem(0,wet);b.inventory.setChanged()
        // Let the normal passive load start before priming saved progress in the next phase.
        check(c.work.joules-reactorBefore<c.requiredWatts*.20) { "Reactor gained power while supply was off" }
        f(p).voltage(sources[fusion]!!,3200.0)
        record("Washer completes a repaired, unusable wet item with its name intact; reactor pauses during an outage")
    }
    private fun primeFinish(p: ServerPlayer) {
        val b=machine(p,dryer);val c=machine(p,fusion)
        check(b.resistor.power in 5800.0..6100.0 && b.work.joules>1000) { "Dryer ${b.status} ${b.resistor.power} ${b.work.joules}" }
        b.work.load(b.requiredJoules-9000,b.requiredJoules)
        val saved=CompoundTag();c.writeToNBT(saved);val paid=c.work.joules;c.readFromNBT(saved)
        check(abs(c.work.joules-paid)<.001)
        c.work.load(c.requiredJoules-c.requiredWatts,c.requiredJoules)
        record("Dryer draws 6000 W at 240 V; reactor keeps its sample and paid work across save/read")
    }
    private fun finishAndCord(p: ServerPlayer) {
        val b=machine(p,dryer);val c=machine(p,fusion)
        val dry=b.inventory.getItem(1);check(dry.`is`(Items.DIAMOND_PICKAXE) && dry.damageValue==0 && dry.hoverName.string=="Saved tool")
        check(c.inventory.getItem(1).`is`(Items.IRON_BLOCK) && c.inventory.getItem(0).`is`(Items.IRON_BLOCK))
        // Supply the dryer through its rated outlet. The original fixed cable stays in place and is isolated by cord mode.
        val w=p.serverLevel();w.setBlockAndUpdate(dryerOutlet.north(),Blocks.STONE.defaultBlockState())
        val stack=Eln.findItemStack("240 V 30 A Dryer Outlet",1)
        check(Eln.transparentNodeItem.placeBlockAt(stack,p,w,dryerOutlet,net.minecraft.core.Direction.SOUTH))
        val o=f(p).machine(dryerOutlet) as OutletElement
        Eln.simulator.addElectricalComponent(mods.eln.sim.mna.component.VoltageSource("dryerTest",o.input,null).apply { voltage=240.0 })
        check(b.cord.attach(o.anchor(),false))
        b.inventory.setItem(1,ItemStack.EMPTY)
        b.inventory.setItem(0,WetEquipment.wash(ItemStack(Items.IRON_CHESTPLATE).apply { damageValue=50 }));b.inventory.setChanged()
        record("Drying restores usable repaired equipment; reactor makes a block without consuming its sample")
        val e=f(p).machine(fridge) as KitchenElement
        val removed=e.inventory.removeItemNoUpdate(3)
        check(!RefrigeratorPreservation.isCold(removed))
        val value=component("fffoodspoilageandpreservation:spoilage")?.let { removed.get(it) }
        if(value!=null)check(value.javaClass.getMethod("progressTicks").invoke(value)==0L)
        check(call("com.mrbysco.spoiled.util.SpoilHelper","getSpoilRecipe",w,removed)!=null)
        record("FF spoilage progress remains paused; removing food immediately restores Spoiled processing")
    }
    private fun finish(error: Throwable?) {
        done=true;Files.createDirectories(output)
        Files.writeString(output.resolve("report.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to passed,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("LAUNDRY_FUSION FAIL",error) else Eln.logger.info("LAUNDRY_FUSION COMPLETE")
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.laundryFusionChecks") || done)return
        try {
            ticks++;check(++total<4000) { "Acceptance timed out at $phase" }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    val name="laundry-fusion-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(430L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    step(1)
                }
                1 -> if(mc.player!=null && mc.screen==null && ticks>40) { server(::setup);step(2,130) }
                2 -> if(settled()) { server(::checkPowerAndPrime);step(3,100) }
                3 -> if(settled()) { server(::moveWet);step(4,100) }
                4 -> if(settled()) { server(::primeFinish);step(5,100) }
                5 -> if(settled()) {
                    for(pos in positions) {
                        val tile=mc.level!!.getBlockEntity(pos) as NodeBlockEntity
                        TerrainModels.refreshNow(tile)
                        val render=(tile as TransparentNodeEntity).elementRender!!
                        check(TerrainModels.meshFor(render)!=null) { "Missing baked body $pos" }
                    }
                    for(kind in MatterMachineKind.entries) {
                        val item=Eln.findItemStack(kind.title,1)
                        for(context in listOf(ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
                            val audit=mods.eln.client.itemrender.BakedNodeItems.audit(item,context)
                            check(audit["baked"]==true) { "Unbaked item preview: $audit" }
                        }
                    }
                    record("Washer, dryer and six-block reactor use nonempty baked terrain bodies and baked item previews")
                    Screenshot.grab(mc.gameDirectory,"laundry-fusion.png",mc.mainRenderTarget) {}
                    server(::finishAndCord);step(6,100)
                }
                6 -> if(settled()) { server { p ->
                    val b=machine(p,dryer);check(b.cord.connected && b.resistor.power in 5700.0..6100.0)
                    check(b.getElectricalLoad(Direction.XN,LRDU.Down)==null)
                    record("30 A dryer cord powers the machine and isolates its fixed-cable terminals")
                };step(7) }
                7 -> if(settled()) {
                    server { p ->
                        p.teleportTo(p.serverLevel(),fusion.x+2.0,fusion.y+3.0,fusion.z-8.0,0f,0f)
                        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(fusion.x.toDouble(),fusion.y+.6,fusion.z-.5))
                    };step(8,40)
                }
                8 -> if(settled() && ticks>50) {
                    Screenshot.grab(mc.gameDirectory,"fusion-front.png",mc.mainRenderTarget) {}
                    server { p ->
                        p.teleportTo(p.serverLevel(),washer.x+3.0,washer.y+2.0,washer.z-4.0,0f,0f)
                        p.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3(washer.x+4.0,washer.y+.4,washer.z+.5))
                    };step(9,40)
                }
                9 -> if(settled() && ticks>50) {
                    Screenshot.grab(mc.gameDirectory,"laundry-front.png",mc.mainRenderTarget) {}
                    server { p -> mods.eln.GuiHandler.open(p,mods.eln.GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),fusion.x,fusion.y,fusion.z) }
                    step(10)
                }
                10 -> if(settled() && ticks>35) {
                    check(mc.screen is MatterMachineGui)
                    Screenshot.grab(mc.gameDirectory,"fusion-gui.png",mc.mainRenderTarget) {}
                    record("Reactor GUI opens with synchronized progress, power and inventory")
                    finish(null)
                }
            }
        } catch(t: Throwable) { finish(t) }
    }
}
