package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class ShedControlTest {
    @Test fun anOversizedShedLoadWaitsForRealHeadroomInsteadOfCyclingEveryFiveSeconds() {
        val s=ShedControl();val demand=doubleArrayOf(100.0,100.0,100.0,800.0)
        repeat(21) { s.step(.05,240.0,240.0,1100.0,1000.0,true,demand) }
        assertContentEquals(booleanArrayOf(true,true,true,false),s.closed)
        repeat(400) { s.step(.05,240.0,240.0,300.0,1000.0,true,demand) }
        assertEquals("HEADROOM",s.status);assertFalse(s.closed[3]);assertEquals(3,s.next)
        repeat(101) { s.step(.05,240.0,240.0,300.0,1500.0,true,demand) }
        assertTrue(s.closed.all { it })
    }
    @Test fun voltageHysteresisAndDelaysAreExplicitAndHigherPriorityRestoresFirst() {
        val s=ShedControl()
        repeat(40) { s.step(.05,190.0,240.0,0.0,1000.0,true,shedDelay=.5,restoreDelay=2.0) }
        assertTrue(s.closed.none { it })
        repeat(300) { s.step(.05,220.0,240.0,0.0,1000.0,true,restoreDelay=2.0) }
        assertEquals("HOLD",s.status);assertTrue(s.closed.none { it })
        repeat(39) { s.step(.05,240.0,240.0,0.0,1000.0,true,restoreDelay=2.0) }
        assertEquals("RESTORING",s.status);assertTrue(s.remaining>0);assertEquals(0,s.next)
        s.step(.05,240.0,240.0,0.0,1000.0,true,restoreDelay=2.0)
        assertContentEquals(booleanArrayOf(true,false,false,false),s.closed)
    }
    @Test fun deadInputDoesNotChatterAndRemoteInhibitOpensAllOutputs() {
        val s=ShedControl();repeat(500) { s.step(.05,0.0,240.0,0.0,1000.0,true) }
        assertTrue(s.closed.all { it });assertEquals("WAIT_POWER",s.status)
        s.step(.05,240.0,240.0,100.0,1000.0,false);assertTrue(s.closed.none { it })
        repeat(99) { s.step(.05,240.0,240.0,0.0,1000.0,true) };assertTrue(s.closed.none { it })
        repeat(2) { s.step(.05,240.0,240.0,0.0,1000.0,true) };assertTrue(s.closed[0]);assertFalse(s.closed[1])
        s.step(.05,Double.NaN,240.0,0.0,1000.0,true);assertTrue(s.closed.none { it });assertEquals("INVALID",s.status)
    }
    @Test fun rememberedDemandAndSettingsSurviveSaveAndOldTwoSettingSavesMigrate() {
        fun device()=IndustrialElement(TransparentNode(),Eln.transparentNodeItem.subItemList[IndustrialKind.SHEDDER.ordinal+(126 shl 6)]!!)
        val a=device();assertTrue(a.applySettings(doubleArrayOf(240.0,20000.0)))
        assertEquals(6,a.settings.size);a.shed.closed[3]=false;a.shed.nominalDemand[3]=17000.0
        val restored=device();restored.readFromNBT(CompoundTag().also(a::writeToNBT))
        assertFalse(restored.shed.closed[3]);assertEquals(17000.0,restored.shed.nominalDemand[3])
        assertContentEquals(a.settings,restored.settings)
        assertFalse(a.applySettings(doubleArrayOf(240.0,20000.0,.5,5.0,.95,.85)))
        restored.shed.retry();assertEquals(0.0,restored.shed.nominalDemand[3])
    }
}
