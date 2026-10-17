package mods.eln.devtest

import mods.eln.Eln
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.mechanical.MotorElement
import mods.eln.misc.Direction
import mods.eln.misc.Coordinate
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.transparentnode.VariableDcDcElement
import mods.eln.transparentnode.teleporter.TeleporterElement
import mods.eln.transparentnode.teleporter.TeleporterDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs

/** Real overhead taps, inventory-configured converters and machine power ports. */
internal class PoleTapFixture(private val origins: List<BlockPos>) {
    private fun pole(i: Int) = origins[i].south(16)
    private fun converter(i: Int) = pole(i).east(2)
    private fun load(i: Int) = pole(i).east(6)
    private fun target(i: Int) = if (i % 2 == 0) 850.0 else 3200.0

    fun install(f: NativeCampaignFixtures) {
        val cable = UtilityCableDescriptor.allDescriptors().single {
            it.material == UtilityCableMaterial.ALUMINUM && it.sizeLabel == "1000 kcmil" && !it.insulated && !it.melted
        }
        origins.indices.forEach { i ->
            val p = pole(i)
            for (x in -3..10) for (z in -4..4)
                f.world.setBlockAndUpdate(p.offset(x, -1, z), Blocks.STONE.defaultBlockState())
            val tap = f.placeMachine(f.requiredItem(if (i < 2) "Direct Utility Pole" else "Utility Pole w/DC-DC Converter"), p) as GridElement
            GridLink.addLink(f.machine(origins[i]) as GridElement, tap, Direction.ZP, Direction.ZN,
                cable, 16, cable.newItemStack(1).also { cable.setRemainingLengthMeters(it, 16.0) })
            val e = f.placeMachine(f.requiredItem("Variable DC-DC Converter"), converter(i)) as VariableDcDcElement
            e.front = Direction.ZP
            e.inventory.setItem(0, f.spool()); e.inventory.setItem(1, f.spool())
            e.inventory.setItem(2, f.requiredItem("Optimal Ferromagnetic Core"))
            e.settings.apply { version = 2; mode = "VOLTAGE"; value = target(i); enabled = true }
            e.inventory.setChanged(); e.reconnect()
            f.wire(p.east())
            (3..5).forEach { f.wire(p.east(it)) }
        }
    }

    fun attachLoads(f: NativeCampaignFixtures) {
        origins.indices.forEach { i ->
            if (i % 2 == 0) {
                val item = f.requiredItem("Experimental Transporter")
                f.player.yRot = 0f; f.player.xRot = 0f
                val descriptor = Eln.transparentNodeItem.getDescriptor(item)!!
                val front = descriptor.getFrontFromPlace(Direction.YN, f.player)!!
                // The transporter owns floor cells and side power nodes. Leave its
                // complete footprint free; the incoming cable ends at the power node.
                descriptor.getGhostGroupFront(front)!!.elementList.forEach {
                    f.world.removeBlock(load(i).offset(it.x, it.y, it.z), false)
                }
                check(descriptor.checkCanPlace(Coordinate(load(i).x, load(i).y, load(i).z, f.world), front) == null) {
                    "Transporter fixture footprint is occupied"
                }
                val delta = intArrayOf(descriptor.spawnDeltaX, descriptor.spawnDeltaY, descriptor.spawnDeltaZ)
                front.rotateFromXN(delta)
                val clicked = load(i).offset(-delta[0], -delta[1], -delta[2])
                check(Eln.transparentNodeItem.placeBlockAt(item, f.player, f.world, clicked,
                    net.minecraft.core.Direction.UP)) { "Transporter fixture placement failed" }
                val teleporter = f.machine(load(i)) as TeleporterElement
                val port = (descriptor as TeleporterDescriptor).getPowerCoordonate(f.world).map {
                    it.applyTransformation(front, Coordinate(load(i).x, load(i).y, load(i).z, f.world)); it
                }.minBy { it.x }
                // Its power terminal is one block forward of the side wall.
                // Turn the incoming cable toward that terminal, outside the footprint.
                check(port.x == load(i).x - 1 && abs(port.z - load(i).z) == 1)
                f.wire(BlockPos(load(i).x - 2, port.y, port.z))
                // Set the production charging resistor to its normal 2 kW demand.
                teleporter.electricalComponentList.filterIsInstance<Resistor>().single().resistance = Eln.HVU * Eln.HVU / 2000.0
            } else {
                // Orient before placement so its electrical opening, rather than a
                // side wall of the multi-block footprint, faces the existing cable.
                f.player.yRot = 270f; f.player.xRot = 0f
                check(Eln.transparentNodeItem.placeBlockAt(f.requiredItem("Large Shaft Motor"),
                    f.player, f.world, load(i), net.minecraft.core.Direction.UP)) { "Motor fixture footprint is occupied" }
                check((f.machine(load(i)) as MotorElement).front == Direction.XN)
            }
        }
    }

    fun sample(f: NativeCampaignFixtures, requireDemand: Boolean = true) {
        origins.indices.forEach { i ->
            val e = f.machine(converter(i)) as VariableDcDcElement
            check(e.primaryLoad.voltage > 100.0 && abs(e.secondaryLoad.voltage - target(i)) < target(i) * .03 &&
                e.interSystemProcess.status != "NON_CONVERGENT") { "Loaded pole tap $i lost power: ${f.converterValues(e)}" }
            check(!requireDemand || e.secondaryVoltageSource.power > if (i % 2 == 0) 1900.0 else 1.0) {
                "Pole tap $i did not power its machine: ${f.converterValues(e)}"
            }
        }
    }
}
