package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.Obj3D
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import org.junit.Test
import kotlin.test.*

class GumdropParkingTest {
    @Test fun appendedFixturesKeepExistingIdsAndExposeEveryLampHead() {
        val added=DecorativeLampStyle.entries.takeLast(5)
        assertEquals(5,added.map(DecorativeLightRegistration::idFor).toSet().size)
        assertEquals((64 shl 6)+20,DecorativeLightRegistration.idFor(DecorativeLampStyle.PARKING))
        for(style in added) {
            val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(style)] as LampSocketDescriptor
            assertEquals(style.bulbs,d.bulbCapacity)
            val m=Obj3D().apply { assertTrue(loadFile("${style.model}/${style.model}.obj")) }
            assertNotNull(m.getPart("Base"));assertNotNull(m.getPart("Metal"))
            repeat(style.bulbs) { assertNotNull(m.getPart("Light$it"),style.name) }
            assertTrue(m.xMax<=style.depth+.01)
            if(style.parking)assertEquals(style.bulbs,style.aimHeadCount)
            else { assertTrue(style.gumdrop);assertTrue(style.outdoorFloor);assertEquals(0,style.aimHeadCount) }
        }
    }
}
