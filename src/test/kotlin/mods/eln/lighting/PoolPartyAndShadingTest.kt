package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.TransparentNodeBlock
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sixnode.lampsocket.*
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.Test
import java.io.*
import kotlin.test.*

class PoolPartyAndShadingTest {
    @Test fun fourteenNewFixturesKeepOldIdsAndHaveDistinctCompleteMeshes() {
        val added=DecorativeLampStyle.entries.drop(70).take(14)
        assertEquals(14,added.size);assertEquals(5,added.count { it.pool });assertEquals(5,added.count { it.name.startsWith("PARTY_") })
        assertEquals(4391,DecorativeLightRegistration.idFor(DecorativeLampStyle.FAN_AURA))
        assertEquals(added.size,added.map { it.model }.distinct().size)
        for(style in added) {
            val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(style)] as LampSocketDescriptor
            assertEquals(style,d.fixture);assertFalse(d.wirelessOnly)
            assertTrue(d.acceptedLampTypes.size>=5)
            val model=Obj3D().apply { assertTrue(loadFile("${style.model}/${style.model}.obj")) }
            assertNotNull(model.getPart("Base"),style.name)
            repeat(style.bulbs) { assertNotNull(model.getPart("Light$it"),style.name) }
            assertTrue(model.xDim>0 && model.yDim>0 && model.zDim>0,style.name)
        }
    }
    @Test fun allTransparentEntityTypesAndGhostShapesPassSkylightWithoutAHiddenSolidCube() {
        val world=EmptyBlockGetter.INSTANCE;val pos=BlockPos.ZERO
        for(tag in mods.eln.node.transparent.EntityMetaTag.entries) {
            val state=Eln.transparentNodeBlock.defaultBlockState().setValue(TransparentNodeBlock.META,tag.meta)
            assertEquals(0,state.getLightBlock(world,pos));assertTrue(state.propagatesSkylightDown(world,pos))
            assertTrue(state.getOcclusionShape(world,pos).isEmpty)
        }
        for(meta in 0..3) {
            val state=Eln.ghostBlock.stateForMeta(meta)
            assertEquals(0,state.getLightBlock(world,pos));assertTrue(state.propagatesSkylightDown(world,pos))
        }
    }
    @Test fun poolFixtureAndTemporaryLightStatesRetainSourceWater() {
        for(block in listOf(Eln.sixNodeBlock,Eln.lightBlock)) {
            assertTrue(block.defaultBlockState().fluidState.isEmpty)
            val wet=block.defaultBlockState().setValue(WATERLOGGED,true)
            assertTrue(wet.fluidState.`is`(Fluids.WATER));assertTrue(wet.fluidState.isSource)
            assertEquals(net.minecraft.world.level.block.Blocks.WATER,wet.fluidState.createLegacyBlock().block)
        }
    }
    @Test fun realisticWiredLossDoesNotDimAWholeLightLevelButRealUndervoltageStillDoes() {
        val d=Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(DecorativeLampStyle.PARKING_QUAD)] as LampSocketDescriptor
        val e=LampSocketElement(SixNode(),Direction.YN,d)
        e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",4));e.computeInventory()
        val data=(mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(e.inventory.getItem(0)) as mods.eln.item.lampitem.LampDescriptor).lampData
        val root=RootSystem(.05,1);val feed=NbtElectricalLoad("feed")
        root.addState(feed);root.addState(e.electricalLoad);root.addComponent(e.lampResistor)
        val source=VoltageSource("wire",feed,null).apply { voltage=120.0 };root.addComponent(source)
        val cable=Resistor(feed,e.electricalLoad).apply { resistance=.002 };root.addComponent(cable)
        repeat(2) { root.step() }
        fun level(v: Double)=LampBrightness.level(v,data.nominalU,data.technology.minimalUFactor,data.nominalLightValue)
        assertTrue(e.electricalLoad.voltage in 119.9..120.0)
        assertEquals(level(120.0),level(e.electricalLoad.voltage))
        assertEquals(data.nominalP*4,e.lampResistor.power,.1)
        source.voltage=100.0;repeat(2) { root.step() }
        assertTrue(level(e.electricalLoad.voltage)<level(120.0));assertEquals(0,level(0.0))
    }
    @Test fun fullCircleStreetAimingMatchesTheModelMatrixAndSurvivesSaveAndNetwork() {
        val style=DecorativeLampStyle.PARKING_QUAD;val aim=style.newAim()
        assertTrue(aim.axialPan);assertEquals(4,aim.heads)
        aim.set(3,0,275.0);aim.set(3,1,35.0)
        val q=aim.rotate(Vec3(-1.0,0.0,0.0),3)
        val expected=Vector3f(-1f,0f,0f).mulDirection(Matrix4f().rotateX(Math.toRadians(-85.0).toFloat()).rotateZ(Math.toRadians(35.0).toFloat()))
        assertTrue(q.distanceTo(Vec3(expected.x.toDouble(),expected.y.toDouble(),expected.z.toDouble()))<1e-6)
        val restored=style.newAim().also { it.read(CompoundTag().also(aim::write)) }
        assertEquals(-85.0,restored.pan(3));assertEquals(35.0,restored.tilt(3));assertEquals(0.0,restored.pan(0))
        val bytes=ByteArrayOutputStream().also { aim.write(DataOutputStream(it)) }.toByteArray()
        val synced=style.newAim().also { it.read(DataInputStream(ByteArrayInputStream(bytes))) }
        assertEquals(aim.point(style.heads[3],style.aimPivot(3),3),synced.point(style.heads[3],style.aimPivot(3),3))
        val angled=StreetLightMount.adjust(Vec3(-1.15,5.2,0.0),Direction.ZP,-2,137,35)
        assertTrue(angled.x.isFinite() && angled.y in 4.4..5.4)
    }
}
