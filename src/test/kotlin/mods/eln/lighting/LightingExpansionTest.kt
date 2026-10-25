package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.wirelesssignal.WallWirelessDescriptor
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceElement
import mods.eln.sixnode.wirelesssignal.tx.WirelessSignalTxElement
import mods.eln.transparentnode.standby.GeneratorEffects
import mods.eln.transparentnode.ups.*
import mods.eln.transparentnode.utilitylight.*
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class LightingExpansionTest {
    // The Java activation path explicitly supports a null player (no wrench); avoid a world fixture here.
    private fun click(e: WirelessSignalSourceElement) = e.javaClass.getMethod("onBlockActivated",
        net.minecraft.world.entity.player.Player::class.java,Direction::class.java,
        Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType)
        .invoke(e,null,Direction.XN,0f,0f,0f)

    @Test fun appendedFixturesNeverOverwriteLampSuppliesOrExistingIdentities() {
        assertEquals(103,DecorativeLampStyle.entries.size)
        val ids=DecorativeLampStyle.entries.map(DecorativeLightRegistration::idFor)
        assertEquals(ids.size,ids.toSet().size)
        assertTrue(ids.subList(30,88).all { it shr 6==68 });assertTrue(ids.drop(88).all { it shr 6==70 })
        assertTrue(Eln.sixNodeItem.subItemList[(65 shl 6)] is mods.eln.sixnode.lampsupply.LampSupplyDescriptor)
        for(style in DecorativeLampStyle.entries.filter { it.luxury || it.street || it==DecorativeLampStyle.FLOOR_DRUM }) {
            val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(style)] as LampSocketDescriptor
            val ghosts=d.getGhostGroup(if(style.ceiling)Direction.YP else Direction.YN,LRDU.Up)!!.elementList
            assertEquals(ghosts.size,ghosts.map { Triple(it.x,it.y,it.z) }.toSet().size)
            assertTrue(ghosts.any { kotlin.math.abs(it.y)>=if(style.street)5 else 1 })
        }
    }
    @Test fun wirelessButtonsRetriggerAndReleaseAtTheTimeoutWhileSwitchesPersist() {
        for(d in Eln.sixNodeItem.subItemList.values.filterIsInstance<WallWirelessDescriptor>()) {
            val e=WirelessSignalSourceElement(SixNode().apply { coordinate=Coordinate(0,64,0,0) },Direction.XN,d)
            try {
                click(e);assertEquals(1.0,e.value)
                val tag=CompoundTag().also(e::writeToNBT)
                if(d.momentary) {
                    // Only the button timer; weather glitches are unrelated to pulse semantics.
                    val timer=e.slowProcessList.last()
                    timer.process(.15);click(e)
                    timer.process(.15);assertEquals(1.0,e.value)
                    timer.process(.061);assertEquals(0.0,e.value)
                    e.readFromNBT(tag);assertEquals(0.0,e.value)
                } else {
                    e.readFromNBT(tag);assertEquals(1.0,e.value)
                    click(e);assertEquals(0.0,e.value)
                }
            } finally { WirelessSignalTxElement.channelRemove(e) }
            assertTrue(WirelessSignalTxElement.channelMap.values.none { e in it })
        }
    }
    @Test fun mixedLargeCellRacksStartEmptyAndKeepChargeOnRemoval() {
        val e=UpsElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<UpsDescriptor>().single { !it.wallMounted })
        val types=Eln.sharedItemStackOne.subItemList.values.filterIsInstance<UpsLargeCell>().sortedBy { it.size }
        assertEquals(3,types.size)
        repeat(48) { e.inventory.setItem(it,types[it%3].newItemStack()) }
        val rated=16*types.sumOf { it.energyStorage }
        assertEquals(rated,e.bank.capacity);assertEquals(0.0,e.bank.energy)
        assertEquals(1000.0,e.bank.transfer(1000.0,1.0),1e-6)
        val old=e.bank.energy
        val removed=e.inventory.removeItem(0,1)
        val charge=upsBattery(removed)!!.getEnergy(removed)
        assertTrue(charge>0)
        assertEquals(24,e.bank.activeCount)
        e.inventory.setItem(0,removed)
        assertEquals(old,e.bank.energy,1e-6)
        assertEquals(-old,e.bank.transfer(-1e20,100.0),1e-6)
        assertEquals(0.0,e.bank.energy,1e-6)
        assertEquals(10_000.0,UpsPowerUnit.MAX_WATTS)
    }
    @Test fun allPoleStylesTrackGridVoltageWithoutChangingTheirNominalLampRating() {
        for(d in Eln.transparentNodeItem.subItemList.values.filterIsInstance<UtilityStreetLightDescriptor>()) for(v in listOf(120,240)) {
            val e=UtilityStreetLightElement(TransparentNode(),d)
            e.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));e.inventoryChange(e.inventory)
            e.unit.read(CompoundTag().apply { putInt("mainsOutputVoltage",v) });e.updateAmbientDarkness(15)
            val root=RootSystem(.05,1)
            e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            val source=VoltageSource("grid",e.unit.primary,null);root.addComponent(source);root.addProcess(e.unit.controller)
            for(fraction in listOf(1.0,.95,1.05)) {
                source.voltage=MainsStepDown.NOMINAL_POLE_VOLTS*fraction
                repeat(15) { root.step() }
                assertEquals(v*fraction,e.unit.secondary.voltage,.1)
                assertEquals(d.lampWatts*fraction*fraction,e.lamp.power,.1)
                assertTrue(source.power>=e.lamp.power)
            }
            val ghosts=d.getGhostGroupFront(Direction.XN)!!.elementList
            assertEquals(ghosts.size,ghosts.map { Triple(it.x,it.y,it.z) }.toSet().size)
        }
    }
    @Test fun seasonalStringsKeepEveryHousingAndTheFullWireSpan() {
        fun model(style: DecorativeLampStyle)=Obj3D().apply { assertTrue(loadFile("${style.model}/${style.model}.obj")) }
        for(style in DecorativeLampStyle.entries.filter { it.seasonalString }) {
            val wire=model(style).getPart("Metal").boundingBox()
            assertTrue(wire.max.z-wire.min.z>1.9,"${style.name} lost its main wire")
        }
        for((style,part) in listOf(DecorativeLampStyle.HALLOWEEN_PUMPKIN to "Accent",DecorativeLampStyle.HALLOWEEN_GHOST to "Shade")) {
            val bodies=model(style).getPart(part).boundingBox()
            assertTrue(bodies.max.z-bodies.min.z>1.6,"${style.name} only rendered one housing")
        }
    }
    @Test fun smokeFollowsTheExhaustForEveryFacingAndTheEngineIsSilentWhenStopped() {
        val expected=mapOf(Direction.XN to net.minecraft.world.phys.Vec3(.21,.475,.30),
            Direction.XP to net.minecraft.world.phys.Vec3(-.21,.475,-.30),
            Direction.ZP to net.minecraft.world.phys.Vec3(.30,.475,-.21),
            Direction.ZN to net.minecraft.world.phys.Vec3(-.30,.475,.21))
        for((front,offset) in expected)assertTrue(GeneratorEffects.exhaust(front).distanceTo(offset)<1e-6)
        for(status in listOf("OFF","EMPTY","STANDBY","STARTING","OVERLOAD"))assertEquals(0f,GeneratorEffects.volume(status,10_000.0))
        assertTrue(GeneratorEffects.volume("RUNNING",0.0)>.5f)
        assertTrue(GeneratorEffects.volume("COOLDOWN",0.0)>.5f)
        assertTrue(GeneratorEffects.volume("RUNNING",10_000.0)<1f)
    }
}
