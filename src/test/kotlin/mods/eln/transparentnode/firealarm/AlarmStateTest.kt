package mods.eln.transparentnode.firealarm

import net.minecraft.core.BlockPos
import org.junit.Test
import kotlin.test.*

class AlarmStateTest {
    @Test fun backupConsumesActualJoulesAndAlarmMustBeClearedBeforeReset() {
        val s=AlarmPanelState();s.account(1.0,15.0,5.0)
        assertTrue(s.powered);assertEquals(5.0,s.energy,0.0)
        s.trigger(true);assertTrue(s.latched);s.silenced=true;assertFalse(s.reset(true));assertTrue(s.latched)
        s.account(.5,0.0,5.0);assertTrue(s.powered);assertEquals(0.0,s.energy,0.0)
        s.account(.5,0.0,5.0);assertFalse(s.powered)
        assertTrue(s.reset(false));assertFalse(s.latched);assertFalse(s.silenced)
        s.account(1.0,10.0,0.0);s.test();assertTrue(s.alarm);s.account(11.0,10.0,0.0);assertFalse(s.alarm)
        val energy=s.energy;s.account(Double.NaN,100.0,0.0);assertEquals(energy,s.energy,0.0)
    }
    @Test fun channelLeasesStayInsideWorldAndRangeAndExpireWithoutKeepingChunksAlive() {
        AlarmNetwork.clear();val world=Any();val other=Any();val panel=BlockPos(0,80,0)
        AlarmNetwork.publishPanel(world,"A",AlarmNetwork.Panel(panel,10,true,true,false))
        assertTrue(AlarmNetwork.lease(world,"A",BlockPos(10,80,0),11).alarm)
        assertFalse(AlarmNetwork.lease(other,"A",panel,11).powered)
        assertFalse(AlarmNetwork.lease(world,"B",panel,11).powered)
        assertFalse(AlarmNetwork.lease(world,"A",BlockPos(129,80,0),11).powered)
        assertFalse(AlarmNetwork.lease(world,"A",panel,61).powered)
    }
    @Test fun panelChargesForNotificationPowerIncludingSilencedStrobesAndWiredTriggers() {
        AlarmNetwork.clear();val world=Any();val panel=BlockPos(0,80,0);val horn=BlockPos(4,80,0)
        AlarmNetwork.publishField(world,"A",AlarmNetwork.Field(horn,0,"Horn",false,false,false,panel.asLong(),.1,8.0,3.0,false))
        assertEquals(8.1,AlarmNetwork.snapshot(world,"A",panel,0,true,false).watts,1e-10)
        assertEquals(3.1,AlarmNetwork.snapshot(world,"A",panel,0,true,true).watts,1e-10)
        AlarmNetwork.publishField(world,"A",AlarmNetwork.Field(horn,1,"Horn",false,true,false,panel.asLong(),.1,8.0,3.0,false))
        assertEquals(8.1,AlarmNetwork.snapshot(world,"A",panel,1,false,true).watts,1e-10)
        AlarmNetwork.publishField(world,"A",AlarmNetwork.Field(horn,2,"Horn",false,true,true,panel.asLong(),.1,8.0,3.0,false))
        assertEquals(0.0,AlarmNetwork.snapshot(world,"A",panel,2,true,false).watts,0.0)
    }
    @Test fun redundantPanelsSeeTheSameSourceButOnlyItsOwnerPaysItsLoad() {
        AlarmNetwork.clear();val world=Any();val a=BlockPos(0,80,0);val b=BlockPos(20,80,0)
        AlarmNetwork.publishField(world,"A",AlarmNetwork.Field(BlockPos(1,80,0),0,"Kitchen",true,true,false,a.asLong(),.15,0.0))
        val first=AlarmNetwork.snapshot(world,"A",a,0,false,false);val second=AlarmNetwork.snapshot(world,"A",b,0,false,false)
        assertEquals(1,first.active.size);assertEquals(1,second.active.size);assertEquals(.15,first.watts,0.0);assertEquals(0.0,second.watts,0.0)
    }
}
