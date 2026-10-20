package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.GridLink
import mods.eln.misc.Direction
import mods.eln.misc.UserError
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.nbt.CompoundTag
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.test.*

class UtilityServiceInteractionTest {
    private fun pole(kind: DistributionKind)=DistributionPoleElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    private fun meter()=HouseServiceElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.meter })
    private fun panel()=BreakerPanelElement(TransparentNode(),
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single())

    @Test fun aimingAtActualTiePointsSelectsVoltageAndBundlesThreeIndependentPhases() {
        for(kind in DistributionKind.entries) {
            val p=pole(kind)
            assertEquals(50,p.aimedGridTerminal(Direction.ZP,p.gridCablePoint(50,1)),kind.name)
            fun clickable(terminal: Int) {
                val hit=p.gridCablePoint(terminal,0)
                val x=kotlin.math.floor(hit.x+.5).toInt();val y=kotlin.math.floor(hit.y+.5).toInt();val z=kotlin.math.floor(hit.z+.5).toInt()
                assertTrue(p.transparentNodeDescriptor.ghostGroup!!.elementList.any { it.x==x && it.y==y && it.z==z },"${kind.name}: $terminal has no clickable tie point")
            }
            if(!kind.lvOnly)for(i in 0 until kind.phases) {
                clickable(32+i);if(kind.protectedLine)clickable(36+i)
                val selected=p.aimedGridTerminal(Direction.ZP,p.gridCablePoint(32+i,0))
                assertEquals(if(kind.phases==3)52 else 32,selected,kind.name)
                assertEquals(if(kind.phases==3)p.lines else listOf(p.lines[0]),p.gridLoads(selected))
                if(kind.protectedLine)assertEquals(if(kind.phases==3)53 else 36,
                    p.aimedGridTerminal(Direction.ZP,p.gridCablePoint(36+i,0)),kind.name)
            }
            if(kind.phases==3 && !kind.lvOnly) {
                assertEquals(3,p.gridLoads(52).distinct().size)
                repeat(3) { assertEquals(p.gridCablePoint(32+it,0),p.gridCablePoint(52,it)) }
                assertEquals("HV_THREE_PHASE",p.gridCircuitKey(52))
                assertNotEquals(p.gridCircuitKey(50),p.gridCircuitKey(52))
            }
        }
    }
    @Test fun neitherCreationApiAllowsOverheadCablesToBypassTheWeatherhead() {
        val p=pole(DistributionKind.SECONDARY)
        val cable=UtilityCableDescriptor.allDescriptors().first { !it.melted }
        for(cabinet in listOf(meter(),panel())) {
            assertFalse(cabinet.allowsNewGridLinks)
            assertThrows(UserError::class.java) { GridLink.addLink(p,cabinet,50,50,cable,3) }
            assertThrows(UserError::class.java) { GridLink.addLink(cabinet,p,50,50,cable,3) }
            assertTrue(cabinet.gridLinkList.isEmpty());assertTrue(p.gridLinkList.isEmpty())
        }
    }
    @Test fun aPlaced240VFeederSuppliesBothPanelBanksThroughTheMeterAndTracksSag() {
        val m=meter();val p=panel()
        m.inventory.setItem(0,Eln.findItemStack("Analog Glass Service Meter",1));m.inventoryChange(m.inventory)
        val root=RootSystem(.01,1)
        for(e in listOf(m,p)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent) }
        val feed=NbtElectricalLoad("wall feeder").apply { serialResistance=.00001 };root.addState(feed)
        val source=VoltageSource("service",feed,null).apply { voltage=240.0 };root.addComponent(source)
        val input=ElectricalConnection(feed,m.line[2]);val output=ElectricalConnection(m.load[2],p.buses[2])
        root.addComponent(input);root.addComponent(output)
        val loads=p.buses.mapIndexed { i,b -> Resistor(b,null).apply { resistance=if(i==2)96.0 else 48.0 }.also(root::addComponent) }
        fun advance() { repeat(6) { listOf(m,p).forEach { e -> e.electricalProcessList.forEach { it.process(.01) } };root.step() } }
        advance()
        p.buses.forEachIndexed { i,b -> assertEquals(if(i==2)240.0 else 120.0,b.voltage,.1) }
        assertTrue(source.power>=loads.sumOf { it.power });assertTrue(source.power-loads.sumOf { it.power }<1.0)
        source.voltage=228.0;advance()
        p.buses.forEachIndexed { i,b -> assertEquals(if(i==2)228.0 else 114.0,b.voltage,.1) }
        m.inventory.removeItemNoUpdate(0);m.inventoryChange(m.inventory);advance()
        p.buses.forEach { assertEquals(0.0,it.voltage,1e-7) }
        assertEquals(228.0,feed.voltage,1e-7)
    }
    @Test fun aFiveAmpBreakerToleratesMotorInrushButEventuallyTripsAndKeepsThermalMemory() {
        val breaker=PanelBreaker()
        repeat(39) { assertFalse(breaker.step(.05,50.0,5.0)) }
        val cold=PanelBreaker();cold.read(CompoundTag().also { breaker.write(it,"b") },"b")
        assertTrue(cold.step(.10,50.0,5.0));assertTrue(cold.tripped)
        cold.operate();repeat(30) { assertFalse(cold.step(.05,-50.0,5.0)) }
        repeat(1000) { assertFalse(cold.step(.05,5.0,5.0)) }
        repeat(30) { assertFalse(cold.step(.05,50.0,5.0)) }
        assertFalse(cold.tripped)
        assertTrue(cold.step(.001,50_000.0,5.0))
    }
}
