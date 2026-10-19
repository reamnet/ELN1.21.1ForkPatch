package mods.eln.transparentnode

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.transparent.PowerCabinetDescriptor
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.renewable.RenewableDescriptor
import mods.eln.transparentnode.renewable.SolarElement
import mods.eln.transparentnode.renewable.SolarPriority
import mods.eln.transparentnode.standby.AtsElement
import mods.eln.transparentnode.standby.BackupDescriptor
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.*

class WallPowerCabinetTest {
    private val fronts = Direction.entries.filter { it.isNotY }
    private fun descriptor(id: Int) = Eln.transparentNodeItem.subItemList[id + (124 shl 6)]!!

    @Test fun allWallOrientationsHaveDistinctAccessiblePortsOnTheMountingPlane() {
        for (id in 14..16) for (front in fronts) {
            val d = descriptor(id) as PowerCabinetDescriptor
            val e = if (id == 14) AtsElement(TransparentNode(), d) else SolarElement(TransparentNode(), d)
            e.front = front
            val expected = if (e is AtsElement) mapOf(front.left() to e.unit.mains, front.right() to e.unit.output, Direction.YN to e.unit.backup)
                else (e as SolarElement).let { mapOf(front.left() to it.unit.solar, front.right() to it.unit.output, Direction.YN to it.unit.negative) +
                    if (id == 16) mapOf(Direction.YP to it.unit.grid) else emptyMap() }
            var found = 0
            for (side in Direction.entries) for (lrdu in LRDU.entries) {
                val wanted = if (side.getLRDUGoingTo(front.inverse) == lrdu) expected[side] else null
                assertSame(wanted, e.getElectricalLoad(side, lrdu), "$id $front $side $lrdu")
                assertEquals(wanted != null, e.getConnectionMask(side, lrdu) != 0)
                if (wanted != null) found++
            }
            assertEquals(if (id == 16) 4 else 3, found)
            assertFalse(d.mustHaveFloor()); assertTrue(d.mustHaveWallFrontInverse())
            assertEquals(front, d.getFrontFromPlace(front.inverse, null))
            assertNotNull(d.checkCanPlace(null, Direction.YP))
            assertNotNull(d.checkCanPlace(null, Direction.YN))
        }
    }

    @Test fun shallowCollisionAndMeshTouchTheWallWithoutFillingTheWalkingSpace() {
        val d = descriptor(14) as PowerCabinetDescriptor
        for (front in fronts) {
            val box = d.wallBox(front)
            val depth = if (front == Direction.XN || front == Direction.XP) box.xsize else box.zsize
            assertEquals(.33, depth, 1e-9)
            val center = box.center
            assertFalse(box.contains(.5, .5, .5))
            assertTrue(center.y in .04.. .96)
            assertEquals(0.0, when(front) { Direction.XP -> box.minX; Direction.ZP -> box.minZ; Direction.XN -> 1-box.maxX; else -> 1-box.maxZ }, 1e-9)
        }
        for (name in listOf("wallats", "wallmppt", "wallhybrid")) {
            val lines = Path.of(System.getProperty("eln.projectDir"), "src/main/resources/assets/eln/model/$name/$name.obj").readLines()
            val vertices = lines.filter { it.startsWith("v ") }.map { it.split(' ').drop(1).map(String::toDouble) }
            assertTrue(vertices.size > 100)
            assertEquals(-.5, vertices.minOf { it[2] })
            assertEquals(-.17, vertices.maxOf { it[2] })
            assertTrue(vertices.all { it[0] in -.46.. .46 && it[1] in -.46.. .46 })
            val parts = lines.filter { it.startsWith("o ") }.map { it.removePrefix("o ") }.toSet()
            assertTrue(parts.containsAll(listOf("Case", "Trim", "Display", "Output")))
        }
    }

    @Test fun wallUnitsKeepSettingsAndSaveIdentityWithoutChangingOriginalFloorVariants() {
        val ats = AtsElement(TransparentNode(), descriptor(14))
        ats.front = Direction.XP; ats.unit.setVoltage(3200.0); ats.unit.channel = "Workshop"
        val saved = CompoundTag().also(ats::writeToNBT)
        val restored = AtsElement(TransparentNode(), descriptor(14)).also { it.readFromNBT(saved) }
        assertEquals(Direction.XP, restored.front); assertEquals(3200.0, restored.unit.nominal); assertEquals("Workshop", restored.unit.channel)
        for (id in 15..16) {
            val solar = SolarElement(TransparentNode(), descriptor(id))
            solar.front = Direction.ZN; solar.unit.setVoltage(if (id == 15) 850.0 else 240.0); solar.unit.priority = SolarPriority.GRID_FIRST
            val copy = SolarElement(TransparentNode(), descriptor(id)).also { it.readFromNBT(CompoundTag().also(solar::writeToNBT)) }
            assertEquals(solar.front, copy.front); assertEquals(solar.unit.target, copy.unit.target); assertEquals(solar.unit.priority, copy.unit.priority)
            assertEquals(if (id == 15) 10_000.0 else 20_000.0, copy.unit.rating)
        }
        assertFalse((descriptor(6) as BackupDescriptor).wallMounted)
        for (id in 8..11) assertFalse((descriptor(id) as RenewableDescriptor).wallMounted)
        for (id in listOf(6,7,8,9,10,11)) assertTrue(descriptor(id).mustHaveFloor())
    }
}
