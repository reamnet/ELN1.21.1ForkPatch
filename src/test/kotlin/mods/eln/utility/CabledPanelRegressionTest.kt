package mods.eln.utility

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.power.ConverterLimits
import mods.eln.sim.power.RegulatedPowerProcess
import mods.eln.sixnode.electricalbreaker.ElectricalBreakerDescriptor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CabledPanelRegressionTest {
    @Test fun aCabled240VoltFeedKeepsAllCircuitsPoweredWhen120VoltLoadsAreAdded() {
        for (fromBottom in listOf(false,true)) for (mode in 0..2) for (installed in listOf(1, 5, 12)) for (length in listOf(1, 120)) {
            val root = RootSystem(.01, 50)
            fun pin(name: String) = NbtElectricalLoad(name).apply { serialResistance = .002; root.addState(this) }
            val supply = pin("supply")
            val panel = BreakerPanelElement(TransparentNode(), Eln.transparentNodeItem.subItemList.values
                .filterIsInstance<BreakerPanelDescriptor>().single()).apply {
                readItemStackNBT(net.minecraft.nbt.CompoundTag().apply { putBoolean("panelFeederBottom",fromBottom) })
            }
            val transformer=if(mode==2)mods.eln.transparentnode.utilitylight.MainsStepDown(panel,true).apply {
                read(net.minecraft.nbt.CompoundTag().apply { putInt("mainsOutputVoltage",240) })
            } else null
            panel.electricalLoadList.forEach(root::addState)
            panel.electricalComponentList.forEach(root::addComponent)
            val breaker = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalBreakerDescriptor>()
                .first { it.currentLimit >= 30.0 }
            panel.circuits.forEach { if(it.id<installed)panel.inventory.setItem(it.id, breaker.newItemStack(1)); it.use240 = true; it.configure() }
            var feed = supply
            repeat(length) { i ->
                val next = pin("wire$i")
                root.addComponent(ElectricalConnection(feed, next))
                // Keep real junctions so a long feeder cannot all collapse into one line.
                root.addComponent(Resistor(next, null).apply { resistance = 1e9 })
                feed = next
            }
            val side=if(fromBottom)mods.eln.misc.Direction.YN else mods.eln.misc.Direction.YP
            val bottom=panel.getElectricalLoad(side,side.getLRDUGoingTo(panel.front.inverse)!!)!!
            root.addComponent(ElectricalConnection(feed,bottom))
            var controller: RegulatedPowerProcess? = null
            if (mode==1) {
                val high = pin("upstream"); val primary = pin("primary")
                root.addComponent(VoltageSource("upstream", high, null).apply { voltage = 3200.0 })
                root.addComponent(Resistor(high, primary).apply { resistance = .01 })
                val input = SwitchableVoltageSource("input").apply { connectTo(primary, null) }
                val output = SwitchableVoltageSource("output").apply { connectTo(supply, null) }
                root.addComponent(input); root.addComponent(output)
                controller = RegulatedPowerProcess(primary, null, supply, null, input, output, { true },
                    { ConverterLimits(1.0, 40000.0, 240.0, 100.0, 200.0, 20000.0, .97, 1.0 / 256, 256.0) }, { 240.0 })
                root.addProcess(controller)
            } else if(transformer!=null) {
                val high=pin("high")
                root.addComponent(VoltageSource("high",high,null).apply { voltage=12470.0 })
                root.addComponent(ElectricalConnection(high,transformer.primary))
                root.addComponent(ElectricalConnection(transformer.secondary,supply))
                root.addProcess(transformer.controller)
            } else root.addComponent(VoltageSource("service", supply, null).apply { voltage = 240.0 })
            fun tick() { root.step(); panel.electricalProcessList.forEach { it.process(.01) } }
            repeat(4) { tick() }
            for (count in listOf(0, 1, 2, 6, 12, 0)) {
                panel.circuits.forEach { c ->
                    c.use240 = c.id >= count; c.configure()
                    c.demand.resistance = (if (c.use240) 240.0 * 240 else 120.0 * 120) / 100.0
                }
                repeat(5) { step ->
                    tick()
                    val message = "bottom=$fromBottom mode=$mode installed=$installed length=$length count=$count step=$step ${controller?.status}"
                    assertTrue(panel.buses[2].voltage > 220.0, "$message panel=${panel.buses.map { it.voltage }}")
                    panel.circuits.forEach { c ->
                        assertEquals(if(c.id<installed)panel.buses[2].voltage * (if (c.use240) 1.0 else .5) else 0.0, c.load.voltage, .1, message)
                    }
                }
            }
        }
    }
    @Test fun feederSelectionSurvivesPickupAndOldPanelsKeepTheirTerminalAssignments() {
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BreakerPanelDescriptor>().single()
        fun panel()=BreakerPanelElement(TransparentNode(),descriptor)
        fun terminal(panel: BreakerPanelElement,side: mods.eln.misc.Direction)=
            panel.getElectricalLoad(side,side.getLRDUGoingTo(panel.front.inverse)!!)
        val old=panel().apply { readItemStackNBT(net.minecraft.nbt.CompoundTag()) }
        assertEquals(old.buses[2],terminal(old,mods.eln.misc.Direction.YP))
        assertEquals(old.circuits[0].load,terminal(old,mods.eln.misc.Direction.YN))
        val bottom=panel().apply {
            readItemStackNBT(net.minecraft.nbt.CompoundTag().apply { putBoolean("panelFeederBottom",true) })
        }
        val restored=panel().apply { readItemStackNBT(bottom.getItemStackNBT()) }
        assertTrue(restored.feederFromBottom)
        assertEquals(restored.buses[2],terminal(restored,mods.eln.misc.Direction.YN))
        assertEquals(restored.circuits[0].load,terminal(restored,mods.eln.misc.Direction.YP))
    }

}
