package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.wirelesssignal.*
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Test
import java.io.*
import kotlin.math.*
import kotlin.test.*

class FixtureControlsTest {
    @Test fun twoHeadsAimIndependentlyAndRejectBadAngles() {
        val aim=LightAim(2,-20.0)
        assertTrue(aim.set(0,0,90.0));assertTrue(aim.set(0,1,0.0))
        assertTrue(aim.rotate(Vec3(1.0,0.0,0.0),0).distanceTo(Vec3(0.0,0.0,1.0))<1e-12)
        assertEquals(0.0,aim.pan(1));assertEquals(-20.0,aim.tilt(1))
        assertTrue(aim.set(1,0,300.0));assertEquals(90.0,aim.pan(1))
        assertTrue(aim.set(1,1,-300.0));assertEquals(-75.0,aim.tilt(1))
        assertFalse(aim.set(0,0,Double.NaN));assertFalse(aim.set(0,1,Double.POSITIVE_INFINITY))
        assertFalse(aim.set(2,0,5.0));assertFalse(aim.set(0,2,5.0))
        assertEquals(90.0,aim.pan(0));assertEquals(0.0,aim.tilt(0))
    }
    @Test fun rotationsMatchTheVisibleOpenGlMatricesAndPreserveThePivot() {
        val aim=LightAim(2);aim.set(0,0,35.0);aim.set(0,1,-40.0)
        val p=Vec3(.13,.2,-.3);assertTrue(aim.point(p,p,0).distanceTo(p)<1e-12)
        val r=aim.rotate(Vec3(1.0,0.0,0.0),0)
        assertEquals(cos(Math.toRadians(40.0))*cos(Math.toRadians(35.0)),r.x,1e-12)
        assertEquals(-sin(Math.toRadians(40.0)),r.y,1e-12)
        assertEquals(cos(Math.toRadians(40.0))*sin(Math.toRadians(35.0)),r.z,1e-12)
        assertEquals(1.0,r.length(),1e-12)
        val z=aim.rotateZ(Vec3(0.0,0.0,1.0),0)
        assertEquals(-r.z,z.x,1e-12);assertEquals(r.y,z.y,1e-12);assertEquals(r.x,z.z,1e-12)
    }
    @Test fun oldFixtureBeamsKeepTheirDefaultDirectionAndSaveBothHeads() {
        for(s in DecorativeLampStyle.entries.filter { it.aimHeadCount>0 }) {
            val a=s.newAim();val direction=a.rotate(Vec3(1.0,0.0,0.0),0)
            assertTrue(direction.distanceTo(s.beam.normalize())<1e-10,s.name)
            repeat(a.heads) { h -> a.set(h,0,15.0+h*10);a.set(h,1,-5.0-h*10) }
            val tag=CompoundTag().also(a::write);val restored=s.newAim().also { it.read(tag) }
            val bytes=ByteArrayOutputStream().also { a.write(DataOutputStream(it)) }.toByteArray()
            val network=s.newAim().also { it.read(DataInputStream(ByteArrayInputStream(bytes))) }
            repeat(a.heads) { h -> assertEquals(a.pan(h),restored.pan(h));assertEquals(a.tilt(h),network.tilt(h)) }
        }
    }
    @Test fun everyAdjustableFixtureHasMovableHousingsAndAllLampFaces() {
        for(s in DecorativeLampStyle.entries.filter { it.aimHeadCount>0 || it.fan }) {
            val model=Obj3D().apply { assertTrue(loadFile("${s.model}/${s.model}.obj")) }
            assertNotNull(model.getPart("Base"),s.name)
            repeat(s.bulbs) { assertNotNull(model.getPart("Light$it"),s.name) }
            repeat(s.aimHeadCount) { assertNotNull(model.getPart("Head${it}Metal"),s.name) }
            if(s.fan)assertNotNull(model.getPart("Rotor"),s.name)
        }
    }
    @Test fun fansUseRatedMotorPowerWithoutBulbsAndStopWithoutPower() {
        for(s in listOf(DecorativeLampStyle.FAN_CLASSIC,DecorativeLampStyle.FAN_MODERN)) {
            val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(s)] as LampSocketDescriptor
            val e=LampSocketElement(SixNode(),Direction.YP,d)
            e.inventory.setItem(1,Eln.findItemStack("Copper Cable",1));e.computeInventory()
            val root=RootSystem(.05,1);root.addState(e.electricalLoad);root.addComponent(e.fanMotor)
            val source=VoltageSource("fan",e.electricalLoad,null).apply { voltage=120.0 };root.addComponent(source)
            root.step();e.updateFan()
            assertEquals(s.fanWatts,e.fanMotor.power,1e-6);assertEquals(1.0,e.fanPower)
            assertTrue(e.inventory.getItem(0).isEmpty)
            source.voltage=0.0;root.step();e.updateFan();assertEquals(0.0,e.fanPower);assertEquals(0.0,e.fanMotor.power)
            e.fanEnabled=false;e.computeInventory();assertEquals(1e19,e.fanMotor.resistance)
            val copy=LampSocketElement(SixNode(),Direction.YP,d).also { it.readFromNBT(CompoundTag().also(e::writeToNBT)) }
            assertFalse(copy.fanEnabled)
        }
    }
    @Test fun wallControlsFinishASnapIn100MillisecondsAtEveryFrameRate() {
        val field=mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceRender::class.java.getDeclaredField("interpolator").apply { isAccessible=true }
        val state=mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceRender::class.java.getDeclaredField("state").apply { isAccessible=true }
        for(d in Eln.sixNodeItem.subItemList.values.filterIsInstance<WallWirelessDescriptor>())for(dt in listOf(.005f,.016f,.05f,.1f,.5f)) {
            val r=WallWirelessRender(SixNodeEntity(net.minecraft.core.BlockPos.ZERO,Eln.sixNodeBlock.defaultBlockState()),Direction.XN,d)
            val rc=field.get(r) as RcInterpolator;state.setBoolean(r,true)
            repeat(ceil(.1/dt).toInt()) { r.refresh(dt);assertTrue(rc.get() in 0f..1f) }
            assertTrue(rc.get()>.98f,"${d.name} dt=$dt: ${rc.get()}")
            state.setBoolean(r,false);r.refresh(.5f);assertTrue(rc.get()<.001f)
        }
    }

    @Test fun fixtureApplianceAndCabinetMeshesOnlyUseRenderableTriangles() {
        val names=DecorativeLampStyle.entries.filter { it.aimHeadCount>0 || it.fan }.map { it.model }+
            mods.eln.transparentnode.kitchen.KitchenType.entries.drop(5).map { it.model }+
            mods.eln.transparentnode.climate.ClimateType.entries.map { it.model }+
            listOf("wallups","gasworklight","wirelesspaddleswitch","wirelesstoggleswitch")
        for(name in names) {
            val lines=java.io.File(System.getProperty("eln.projectDir"),"src/main/resources/assets/eln/model/$name/$name.obj").readLines()
            assertTrue(lines.any { it.startsWith("f ") },name)
            for(line in lines.filter { it.startsWith("f ") })assertEquals(4,line.trim().split(' ').size,name)
        }
    }
}
