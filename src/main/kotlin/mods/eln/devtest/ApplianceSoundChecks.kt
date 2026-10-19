package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sound.LoopedSound
import mods.eln.transparentnode.climate.*
import mods.eln.transparentnode.kitchen.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
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
import java.util.concurrent.ConcurrentHashMap

/** Production power, native sound engine, mini oven GUI and battery hazard; opt in with eln.applianceSoundChecks. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object ApplianceSoundChecks {
    private val mc get()=Minecraft.getInstance()
    private val worldName="appliance-audio-${System.currentTimeMillis()}"
    private val base=BlockPos(256,80,256)
    private val kitchens=KitchenType.entries.mapIndexed { i,t ->
        // Keep the toaster and rice cooker near the listener for their short-range finish sounds.
        val cell=when(i) { 1 -> 4;4 -> 1;else -> i }
        t to base.offset(cell%3*5,0,cell/3*5)
    }
    private val climates=ClimateType.entries.mapIndexed { i,t -> t to base.offset(i%3*5,if(t.cooling)2 else 0,15+i/3*5) }
    private val fans=DecorativeLampStyle.entries.filter { it.fan }.mapIndexed { i,t -> t to base.offset(i*5,5,-5) }
    private val microwave=kitchens.first().second
    private val oven=kitchens.last().second
    private var phase=0;private var ticks=0;private var total=0;private var ready=0;private var done=false
    private var work: CompletableFuture<Void>?=null
    private val passed=mutableListOf<String>()
    private val heard=ConcurrentHashMap.newKeySet<String>()
    @Volatile private var hissTick=-1
    @Volatile private var explosionTick=-1
    private var pausedHeat=0.0;private var pausedFuse=0.0
    private var removedLoop: LoopedSound?=null
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun kitchen(p: ServerPlayer,pos: BlockPos)=f(p).machine(pos) as KitchenElement
    private fun fan(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as SixNode).getElement(Direction.YP) as LampSocketElement
    private fun packet(id: Int)=DataInputStream(ByteArrayInputStream(byteArrayOf(id.toByte())))
    private fun step(n: Int) { phase=n;ticks=0 }
    private fun server(body: (ServerPlayer)->Unit) { val s=mc.singleplayerServer!!;work=CompletableFuture.runAsync({ body(s.playerList.players.first()) },s) }
    private fun settled(): Boolean { if(work?.isDone==false)return false;work?.join();return mc.singleplayerServer!!.tickCount>=ready }
    private fun waitFor(n: Int,delay: Int=40) { ready=mc.singleplayerServer!!.tickCount+delay;step(n) }
    private fun record(s: String) { passed+=s;Eln.logger.info("APPLIANCE_AUDIO PASS {}",s) }
    private fun wall(p: ServerPlayer,name: String,pos: BlockPos,side: Direction): SixNodeElement {
        val w=p.serverLevel();w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
        check(Eln.sixNodeItem.placeBlockAt(if(name=="Copper Cable")f(p).spool() else Eln.findItemStack(name,1),p,w,pos,side.toFacing().opposite,.5f,.5f,.5f))
        return (f(p).node(pos) as SixNode).getElement(side)!!
    }
    private fun feed(e: KitchenElement) {
        e.inventory.clearContent()
        e.inventory.setItem(0,when(e.type) {
            KitchenType.KETTLE -> PotionContents.createItemStack(Items.POTION,Potions.WATER)
            KitchenType.FRIDGE -> ItemStack(Items.WATER_BUCKET)
            else -> ItemStack(when(e.type) {
                KitchenType.TOASTER -> Items.BREAD
                KitchenType.COFFEE -> Items.COCOA_BEANS
                KitchenType.BLENDER -> Items.APPLE
                KitchenType.RICE -> Items.WHEAT
                else -> Items.BEEF
            },64)
        })
        if(e.type in listOf(KitchenType.COFFEE,KitchenType.BLENDER,KitchenType.RICE))e.inventory.setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER))
        e.inventory.setChanged()
    }
    private fun loops(): List<LoopedSound> = kitchens.map { (_,pos) -> ((mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as KitchenRender).operatingSound }+
        climates.map { (_,pos) -> ((mc.level!!.getBlockEntity(pos) as TransparentNodeEntity).elementRender as ClimateRender).operatingSound }+
        fans.map { (_,pos) -> ((mc.level!!.getBlockEntity(pos) as SixNodeEntity).elementRenderList[Direction.YP.int] as LampSocketRender).operatingSound!! }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("appliance-audio-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"passed" to passed,"heard" to heard.sorted(),"hissTick" to hissTick,"explosionTick" to explosionTick,"failure" to error?.stackTraceToString())))
        if(error!=null)Eln.logger.error("APPLIANCE_AUDIO FAIL phase $phase",error) else Eln.logger.info("APPLIANCE_AUDIO COMPLETE {} checks",passed.size)
        mc.execute { mc.stop();kotlin.concurrent.thread { Thread.sleep(1500);kotlin.system.exitProcess(if(error==null)0 else 1) } }
    }
    @SubscribeEvent fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(!java.lang.Boolean.getBoolean("eln.applianceSoundChecks"))return
        val key=event.sound?.location?.toString() ?: return
        if(key.startsWith("eln:appliance_"))heard+=key
        if(key=="eln:appliance_battery_hiss" && hissTick<0)hissTick=mc.singleplayerServer?.tickCount ?: -1
    }
    @SubscribeEvent fun explosion(event: net.neoforged.neoforge.event.level.ExplosionEvent.Detonate) {
        if(java.lang.Boolean.getBoolean("eln.applianceSoundChecks"))explosionTick=mc.singleplayerServer?.tickCount ?: -1
    }
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("eln.applianceSoundChecks") || done)return
        try {
            ticks++;check(++total<7000) { "Timeout at phase $phase" }
            mc.player?.let { it.setNoGravity(true);it.abilities.flying=true }
            when(phase) {
                0 -> if(mc.screen is TitleScreen && mc.overlay==null && ticks>20) {
                    mc.options.guiScale().set(2);mc.window.setWindowed(1280,960);mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(22022L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!);step(1)
                }
                1 -> if(mc.player!=null && mc.level!=null && mc.screen==null) {
                    server { p ->
                        val w=p.serverLevel();val f=f(p);p.abilities.flying=true;p.onUpdateAbilities();w.dayTime=6000;w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server);w.setWeatherParameters(6000,0,false,false)
                        for(x in -4..14)for(z in -8..24) { val at=base.offset(x,-1,z);w.setChunkForced(at.x shr 4,at.z shr 4,true);w.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState()) }
                        for((t,pos) in kitchens) {
                            val e=f.placeMachine(Eln.findItemStack(t.title,1),pos) as KitchenElement;e.front=Direction.ZP;e.reconnect()
                            f.wire(pos.west());f.source(pos.west(2),t.volts);feed(e)
                        }
                        for((t,pos) in climates) {
                            val side=if(t.cooling)Direction.ZN else Direction.YN
                            w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.STONE.defaultBlockState())
                            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(t.title,1),p,w,pos,if(t.cooling)net.minecraft.core.Direction.SOUTH else net.minecraft.core.Direction.UP))
                            val e=f.machine(pos) as ClimateElement
                            if(!t.cooling) { e.front=Direction.ZP;e.reconnect() }
                            e.control.setTarget(if(t.cooling)16.0 else 32.0)
                            wall(p,"Copper Cable",pos.west(),side)
                            (wall(p,"Electrical Source",pos.west(2),side) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",120.0) },p)
                        }
                        val supplyPos=base.offset(2,0,-5)
                        val supply=wall(p,"240V Lamp Supply",supplyPos,Direction.YN) as LampSupplyElement
                        supply.front=Direction.YN.getLRDUGoingTo(Direction.XN)!!
                        supply.inventory!!.setItem(0,f.spool());supply.inventoryChange(supply.inventory)
                        val bytes=ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.writeByte(1);out.writeByte(0);out.writeUTF("Appliance audio") } }.toByteArray()
                        supply.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes)))
                        f.wire(supplyPos.west());f.source(supplyPos.west(2),120.0)
                        for((t,pos) in fans) {
                            val e=wall(p,t.itemName,pos,Direction.YP) as LampSocketElement
                            e.poweredByLampSupply=true;e.lampSupplyChannel="Appliance audio"
                            e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",t.bulbs));e.inventory.setItem(1,f.spool());e.inventory.setChanged();e.reconnect()
                        }
                        p.teleportTo(w,base.x+5.0,base.y+1.0,base.z+8.0,180f,20f)
                    };waitFor(2,280)
                }
                2 -> if(settled()) {
                    val expected=(KitchenType.entries.map { it.sound }+ClimateType.entries.map { it.sound }+"appliance_ceiling_fan"+"appliance_done"+"appliance_toaster_pop").map { "eln:$it" }.toSet()
                    check(heard.containsAll(expected)) { "Missing sounds ${expected-heard}; heard=$heard" }
                    record("All nine kitchen appliances, six climate units and both fans play loaded sounds in the native OpenAL engine")
                    server { p ->
                        check(kitchen(p,oven).inventory.getItem(2).`is`(Items.COOKED_BEEF));record("Mini oven cooks food with electrical energy")
                        for((_,pos) in kitchens)kitchen(p,pos).networkUnserialize(packet(80))
                        for((_,pos) in climates)(f(p).machine(pos) as ClimateElement).networkUnserialize(packet(81))
                        for((_,pos) in fans)fan(p,pos).networkUnserialize(packet(9))
                    };waitFor(3,40)
                }
                3 -> if(settled()) {
                    check(loops().all { it.volume==0f && !mc.soundManager.isActive(it) });record("Switching appliances and fans off stops their operating loops")
                    server { p ->
                        for((_,pos) in fans)check(fan(p,pos).sixNode!!.lightValue>=13)
                        record("Fan motor switch silences the motor while keeping its light on")
                        for((_,pos) in kitchens) { val e=kitchen(p,pos);feed(e);e.networkUnserialize(packet(80)) }
                        for((_,pos) in climates)(f(p).machine(pos) as ClimateElement).networkUnserialize(packet(81))
                        for((_,pos) in fans)fan(p,pos).networkUnserialize(packet(9))
                    };waitFor(4,50)
                }
                4 -> if(settled()) {
                    check(loops().all { it.volume>0f && mc.soundManager.isActive(it) }) { loops().joinToString { "${it.sample}=${it.volume}/${mc.soundManager.isActive(it)}" } }
                    record("All operating loops restart after re-enabling the machines")
                    server { p ->
                        val e=kitchen(p,microwave);e.enabled=false;e.inventory.clearContent();e.inventory.setItem(0,Eln.findItemStack("UPS LiFePO4 Battery Cell",1));e.inventory.setItem(2,ItemStack(Items.STONE,64));e.inventory.setChanged()
                        p.teleportTo(p.serverLevel(),microwave.x+2.5,microwave.y+1.0,microwave.z+3.0,140f,12f)
                        f(p).voltage(oven.west(2),0.0)
                    };waitFor(5,45)
                }
                5 -> if(settled()) {
                    val r=(mc.level!!.getBlockEntity(oven) as TransparentNodeEntity).elementRender as KitchenRender
                    check(r.operatingSound.volume==0f && !mc.soundManager.isActive(r.operatingSound));record("A supply outage stops oven sound without finishing work")
                    server { p ->
                        val e=kitchen(p,microwave);check(e.batteryHeat.joules==0.0);e.networkUnserialize(packet(80));f(p).voltage(oven.west(2),120.0)
                    };waitFor(6,140)
                }
                6 -> if(settled()) {
                    server { p ->
                        val e=kitchen(p,microwave);check(e.batteryHeat.hissing && !e.detonating);check(e.inventory.getItem(2).count==64)
                        pausedHeat=e.batteryHeat.joules;pausedFuse=e.batteryHeat.warningSeconds
                        val tag=CompoundTag().also(e.batteryHeat::write);e.batteryHeat.reset();e.batteryHeat.read(tag)
                        f(p).voltage(microwave.west(2),0.0)
                    };waitFor(7,65)
                }
                7 -> if(settled()) {
                    check(hissTick>=0 && explosionTick<0)
                    val r=(mc.level!!.getBlockEntity(microwave) as TransparentNodeEntity).elementRender as KitchenRender
                    check(r.warningSound!!.volume==0f && !mc.soundManager.isActive(r.warningSound))
                    record("A battery hisses before the blast; the warning stops during a power outage")
                    server { p ->
                        val e=kitchen(p,microwave);check(e.batteryHeat.joules==pausedHeat && e.batteryHeat.warningSeconds==pausedFuse)
                        record("Power loss pauses battery heating and its fuse, even with a full output slot; heat survives NBT")
                        f(p).voltage(microwave.west(2),120.0)
                    };waitFor(8,70)
                }
                8 -> if(settled()) {
                    check(explosionTick>hissTick)
                    server { p ->
                        check(p.serverLevel().getBlockState(microwave).isAir);check(f(p).node(microwave)==null)
                        val drops=p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(microwave).inflate(3.0)).map { it.item }
                        check(drops.none { MicrowaveBatteryHeat.accepts(it) || ItemStack.isSameItem(it,Eln.findItemStack(KitchenType.MICROWAVE.title,1)) })
                        if(!Eln.config.getBooleanOrElse("gameplay.hazards.explosionsEnabled",false))check(p.serverLevel().getBlockState(microwave.below()).`is`(Blocks.STONE))
                        record("The heated battery destroys its microwave in a real explosion while respecting terrain-damage configuration")
                        p.teleportTo(p.serverLevel(),oven.x+2.5,oven.y+1.0,oven.z+3.0,140f,12f)
                    };waitFor(9,40)
                }
                9 -> if(settled() && (mc.level?.getBlockEntity(oven) as? TransparentNodeEntity)?.elementRender is KitchenRender) {
                    server { p -> GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),oven.x,oven.y,oven.z) };step(10)
                }
                10 -> if(settled() && mc.screen is KitchenGui && ticks>30) {
                    Screenshot.grab(mc.gameDirectory,"mini-oven22-gui.png",mc.mainRenderTarget) {}
                    removedLoop=((mc.level!!.getBlockEntity(oven) as TransparentNodeEntity).elementRender as KitchenRender).operatingSound
                    mc.player!!.closeContainer();record("Mini oven opens its actual inventory GUI with cooking progress and controls")
                    server { p -> p.serverLevel().removeBlock(oven,false) };waitFor(11,40)
                }
                11 -> if(settled()) {
                    check(removedLoop?.active==false && !mc.soundManager.isActive(removedLoop!!));record("Removing the appliance disposes its looping sound")
                    finish(null)
                }
            }
        } catch(t: Throwable) { finish(t) }
    }
}
