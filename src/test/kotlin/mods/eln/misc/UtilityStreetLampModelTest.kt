package mods.eln.misc

import org.junit.Test
import mods.eln.transparentnode.utilitylight.StreetLightMount
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.*

class UtilityStreetLampModelTest {
    @Test fun theAttachedFixtureLoadsAndLeavesClearanceBelowTheConductors() {
        val fixture = Obj3D()
        val pole = Obj3D()
        assertTrue(fixture.loadFile("utilitystreetlamp/utilitystreetlamp.obj"))
        assertTrue(pole.loadFile("powerpole/utilitypole.obj"))
        val bracket = assertNotNull(fixture.getPart("Bracket")).boundingBox()
        val housing = assertNotNull(fixture.getPart("Housing")).boundingBox()
        val lens = assertNotNull(fixture.getPart("Lens")).boundingBox()
        val transformer = assertNotNull(fixture.getPart("Transformer")).boundingBox()
        val conductor = assertNotNull(pole.getPart("p0")).boundingBox()
        assertTrue(bracket.min.x < housing.max.x && bracket.max.x > -.1, "Arm must overlap the housing and clamp around the pole")
        assertTrue(bracket.max.y < conductor.min.y - .5, "Keep the arm away from the conductors")
        assertTrue(transformer.max.y < bracket.min.y + .2, "Keep the step-down can below the arm")
        assertTrue(lens.max.y <= housing.min.y + .01, "The emitting panel belongs underneath the head")
        assertTrue(fixture.getPart("Lens").faceGroup.all { it.textureResource == null }, "The off state must not bind the old on texture")
        for (step in StreetLightMount.MIN_STEP..StreetLightMount.MAX_STEP) {
            val offset = StreetLightMount.offset(step)
            assertTrue(bracket.max.y + offset < conductor.min.y - .5, "Raised brackets must clear the conductors")
            assertTrue(lens.min.y + offset > 1.0, "Lowered lamps must stay above the base")
            assertEquals(kotlin.math.floor(lens.min.y + offset).toInt(), StreetLightMount.lightY(step))
        }
    }

    @Test fun theLightCellFollowsTheActualLensAtEveryAngleHeightAndPoleFacing() {
        val fixture = Obj3D()
        assertTrue(fixture.loadFile("utilitystreetlamp/utilitystreetlamp.obj"))
        val lens = assertNotNull(fixture.getPart("Lens")).boundingBox()
        val center = Vec3((lens.min.x + lens.max.x) / 2, (lens.min.y + lens.max.y) / 2, (lens.min.z + lens.max.z) / 2)
        for (front in listOf(Direction.XN, Direction.XP, Direction.ZN, Direction.ZP)) {
            for ((angle, cell) in listOf(0 to Vec3(-1.0, 2.0, 0.0), 90 to Vec3(0.0, 2.0, 1.0),
                180 to Vec3(1.0, 2.0, 0.0), 270 to Vec3(0.0, 2.0, -1.0))) {
                val expected = front.rotateFromXN(cell)
                assertEquals(BlockPos(expected.x.toInt(), expected.y.toInt(), expected.z.toInt()), StreetLightMount.lightOffset(front, 0, angle))
            }
            for (height in StreetLightMount.MIN_STEP..StreetLightMount.MAX_STEP) for (angle in 0..359 step 5) {
                val drawn = front.rotateFromXN(center.add(0.0, StreetLightMount.offset(height), 0.0)
                    .yRot(Math.toRadians(angle.toDouble()).toFloat()))
                assertEquals(BlockPos.containing(drawn.x + .5, drawn.y, drawn.z + .5), StreetLightMount.lightOffset(front, height, angle))
            }
        }
    }
}
