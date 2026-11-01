package mods.eln.transparentnode.renewable

import mods.eln.Eln
import org.junit.Test
import kotlin.test.*

class ControlPanelModelTest {
    @Test fun printedLabelsSpanAllReadoutsAndAreNotReplacedByTheLastLegend() {
        for(name in listOf("solarmppt","solarhybrid","wallmppt","wallhybrid","industrial_relay","industrial_shedder")) {
            val model=Eln.obj.getObj(name)
            val ink=model.getPart("PanelInk").boundingBox()
            assertTrue(ink.max.y>.28 && ink.min.y<-.28,"$name must retain both its title and lower legends")
        }
    }
}
