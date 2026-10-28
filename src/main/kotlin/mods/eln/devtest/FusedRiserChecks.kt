package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.gridnode.GridLink
import mods.eln.misc.Direction
import mods.eln.utility.DistributionPoleElement
import mods.eln.utility.PoleStreetlightAttachment
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Real placed cables and an overhead span. Explicit opt-in; never changes an ordinary world. */
@EventBusSubscriber(modid="eln")
object FusedRiserChecks {
    private var ticks=0
    private var fixture: NativeCampaignFixtures?=null
    private lateinit var riser: DistributionPoleElement
    private val observations=linkedMapOf<String,Any>()
    @SubscribeEvent fun start(event: ServerStartedEvent) {
        if(!java.lang.Boolean.getBoolean("eln.fusedRiserChecks"))return
        try {
            val w=event.server.overworld();val player=FakePlayerFactory.getMinecraft(w)
            val f=NativeCampaignFixtures(w,player);fixture=f
            val a=BlockPos(512,80,512);val b=a.east(12)
            for(x in -4..16)for(z in -3..4) {
                w.setChunkForced((a.x+x) shr 4,(a.z+z) shr 4,true)
                w.setBlockAndUpdate(a.offset(x,-1,z),Blocks.STONE.defaultBlockState())
            }
            val feeder=f.placeMachine(Eln.findItemStack("Single Phase Underground Feeder Pole",1),a) as DistributionPoleElement
            feeder.front=Direction.ZP;feeder.reconnect()
            val input=feeder.front.left();f.wire(a.relative(input.toFacing()));f.source(a.relative(input.toFacing(),2),12470.0)
            riser=f.placeMachine(Eln.findItemStack("Single Phase Underground Feeder Pole",1),b) as DistributionPoleElement
            riser.front=Direction.ZP;riser.reconnect()
            val cable=Eln.sixNodeItem.getDescriptor(f.spool()) as mods.eln.sixnode.electricalcable.UtilityCableDescriptor
            GridLink.addLink(feeder,riser,32,32,cable,12)
            val output=riser.front.left();f.wire(b.relative(output.toFacing()));f.load(b.relative(output.toFacing(),2),12470.0/2,output);f.ground(b.relative(output.toFacing(),3))
            check(riser.poleStreetlight!!.install(Eln.findItemStack(PoleStreetlightAttachment.NAME,1),true))
            Eln.simulator.mna.addComponent(VoltageSource("separate secondary",riser.services[2],null).apply { voltage=240.0 })
        } catch(e: Throwable) { finish(event.server,e) }
    }
    private fun sample(name: String,energized: Boolean) {
        check(abs(riser.lines[0].voltage-12470)<1)
        check(if(energized)abs(riser.outs[0].voltage-12470)<1 else abs(riser.outs[0].voltage)<.001)
        check(abs(riser.poleStreetlight!!.power-70)<.01)
        check(if(energized)abs(riser.outs[0].current-2)<.05 else abs(riser.outs[0].current)<.001)
        observations[name]=mapOf("overheadVolts" to riser.lines[0].voltage,"buriedVolts" to riser.outs[0].voltage,
            "buriedAmps" to riser.outs[0].current,"secondaryVolts" to riser.services[2].voltage,"lampWatts" to riser.poleStreetlight!!.power,
            "closed" to riser.fuses[0].closed,"blown" to riser.fuses[0].blown)
    }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        val f=fixture ?: return
        try {
            when(++ticks) {
                30->{ sample("closed",true);riser.operateFuse(0) }
                50->{ sample("manualOpen",false);riser.operateFuse(0) }
                70->{
                    sample("reclosed",true);riser.fuses[0].removeCartridge()
                    f.player.setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack("1 A Pole Fuse",1))
                    check(riser.replaceFuse(f.player,0));check(f.player.mainHandItem.isEmpty)
                }
                100->{ check(riser.fuses[0].blown);sample("overload",false);finish(event.server,null) }
            }
        } catch(e: Throwable) { finish(event.server,e) }
    }
    private fun finish(server: net.minecraft.server.MinecraftServer,error: Throwable?) {
        fixture=null
        val out=Path.of(System.getProperty("eln.fusedRiserReport","fused-riser-checks.json"))
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.writeString(out,GsonBuilder().setPrettyPrinting().create().toJson(mapOf("passed" to (error==null),"failure" to error?.stackTraceToString(),"observations" to observations)))
        Eln.LOGGER.info("FUSED_RISER_CHECKS {}",if(error==null)"passed" else error.stackTraceToString())
        server.halt(false)
    }
}
