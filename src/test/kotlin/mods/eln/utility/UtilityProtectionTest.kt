package mods.eln.utility

import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class UtilityProtectionTest {
    @Test fun aBlownFuseStaysOpenAcrossColdStartAndNeedsANewLink() {
        val fuse=CutoutFuse();assertFalse(fuse.step(.1,10.0,20.0));assertTrue(fuse.step(.1,60.0,20.0))
        assertTrue(fuse.blown);assertFalse(fuse.closed);assertFalse(fuse.operate())
        val copy=CutoutFuse();copy.read(CompoundTag().also { fuse.write(it,"test") },"test")
        assertTrue(copy.blown);assertFalse(copy.closed);repeat(100) { copy.step(.05,0.0,20.0) };assertFalse(copy.closed)
        copy.replace();assertFalse(copy.blown);assertTrue(copy.closed)
    }
    @Test fun converterPowerLimitingStillLetsThePrimaryFuseDetectAnOutputOverload() {
        val fuse=CutoutFuse();repeat(4) { assertFalse(fuse.step(.05,5.0,10.0,true)) };assertTrue(fuse.step(.05,5.0,10.0,true))
    }
    @Test fun onePhaseFaultGangTripsAndRepeatedFailedRetriesLockOut() {
        val c=RecloserControl();c.pickupAmps=20.0;c.retrySeconds=2.0;c.maxFailures=3
        assertTrue(c.step(.05,listOf(1.0,21.0,3.0)));assertFalse(c.closed);assertEquals(0,c.failures)
        repeat(3) { failure ->
            assertFalse(c.step(1.0,listOf(0.0,0.0,0.0)));assertTrue(c.step(1.0,listOf(0.0,0.0,0.0)));assertTrue(c.closed)
            assertTrue(c.step(.05,listOf(1.0,30.0,3.0)));assertFalse(c.closed);assertEquals(failure+1,c.failures)
        }
        assertTrue(c.locked);repeat(100) { assertFalse(c.step(1.0,listOf(0.0,0.0,0.0))) };assertFalse(c.closed)
        c.reset();assertTrue(c.closed);assertFalse(c.locked)
    }
    @Test fun retryCountdownAndManualOpenSurviveSavingAndNeverAdvanceDuringInvalidTime() {
        val c=RecloserControl();c.step(.05,listOf(200.0));c.step(1.0,listOf(0.0))
        val copy=RecloserControl();copy.read(CompoundTag().also(c::write));assertEquals(c.remaining,copy.remaining)
        assertFalse(copy.step(Double.NaN,listOf(0.0)));assertEquals(c.remaining,copy.remaining)
        copy.step(2.0,listOf(0.0));assertTrue(copy.closed);copy.operate();assertFalse(copy.closed)
        val cold=RecloserControl();cold.read(CompoundTag().also(copy::write));cold.step(1000.0,listOf(0.0));assertFalse(cold.closed)
    }
    @Test fun panelTripLatchPersistsAndResetDoesNotIgnoreAnUnclearedFault() {
        val c=PanelBreaker();assertTrue(c.step(25.0,60.0,20.0));assertTrue(c.tripped)
        val cold=PanelBreaker();cold.read(CompoundTag().also { c.write(it,"c") },"c");assertTrue(cold.tripped)
        cold.step(100.0,0.0,20.0);assertTrue(cold.tripped);cold.operate();assertFalse(cold.tripped)
        assertTrue(cold.step(25.0,60.0,20.0));assertTrue(cold.tripped)
    }
    @Test fun energyRegistersIntegrateImportAndExportWithoutCancellingThem() {
        val e=ServiceEnergy();e.step(3600.0,1000.0);e.step(1800.0,-1000.0);e.step(Double.NaN,1000.0)
        assertEquals(1.0,e.importedKwh,1e-12);assertEquals(.5,e.exportedKwh,1e-12)
        val copy=ServiceEnergy();copy.read(CompoundTag().also(e::write));assertEquals(e.importedKwh,copy.importedKwh);assertEquals(e.exportedKwh,copy.exportedKwh)
    }
}
