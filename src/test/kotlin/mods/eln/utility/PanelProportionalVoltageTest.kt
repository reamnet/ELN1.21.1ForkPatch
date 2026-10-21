package mods.eln.utility

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.nbt.NbtElectricalLoad
import org.junit.Test
import kotlin.test.*

class PanelProportionalVoltageTest {
    @Test fun both120VoltBanksTrackActual240VoltFeederSagRiseAndOutageWithoutRegulation() {
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single()
        val panel=BreakerPanelElement(TransparentNode(),descriptor)
        val root=RootSystem(.01,1);val feed=NbtElectricalLoad("service").apply { serialResistance=.0001 }
        root.addState(feed);panel.electricalLoadList.forEach(root::addState);panel.electricalComponentList.forEach(root::addComponent)
        val connection=ElectricalConnection(feed,panel.buses[2]);root.addComponent(connection)
        val source=VoltageSource("service",feed,null).apply { voltage=240.0 };root.addComponent(source)
        val loads=listOf(Resistor(panel.buses[0],null).apply { resistance=24.0 },Resistor(panel.buses[1],null).apply { resistance=48.0 })
        loads.forEach(root::addComponent)
        panel.electricalProcessList.forEach { it.process(.01) }
        for(voltage in listOf(240.0,228.0,252.0,180.0,0.0,240.0)) {
            source.voltage=voltage;repeat(3) { root.step() }
            for(bus in panel.buses.take(2))assertEquals(panel.buses[2].voltage*.5,bus.voltage,.003,"Feeder $voltage")
            assertEquals(voltage*.5,panel.buses[0].voltage,.01)
            assertEquals(loads.sumOf { it.power },source.power,.1)
        }
    }
    @Test fun duskControlHasHysteresisAndKeepsIndependentSavedCircuitSettings() {
        val panel=BreakerPanelElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single())
        panel.updateDarkness(4);assertTrue(panel.darkOutside)
        panel.updateDarkness(3);assertTrue(panel.darkOutside)
        panel.updateDarkness(2);assertFalse(panel.darkOutside)
        panel.circuits[0].dusk=true;panel.circuits[1].dusk=false
        val tag=panel.getItemStackNBT();val copy=BreakerPanelElement(TransparentNode(),panel.transparentNodeDescriptor).also { it.readItemStackNBT(tag) }
        assertTrue(copy.circuits[0].dusk);assertFalse(copy.circuits[1].dusk)
    }
}
