package mods.eln.lighting

import mods.eln.Eln
import mods.eln.item.lampitem.*
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.sixnode.lampsocket.*
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import org.junit.Test
import kotlin.test.*

class DecorativeLightingTest {
    private val fixtures get() = Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().filter { it.fixture != null }
    private val bulbs get() = Eln.sharedItem.subItemList.values.filterIsInstance<LampDescriptor>()
    private fun element(d: LampSocketDescriptor) = LampSocketElement(SixNode().apply { coordinate = Coordinate(0,64,0,0) },Direction.YN,d)

    @Test fun allFixtureIdentitiesAcceptEveryRegisteredBulbWithoutChangingOldFixtures() {
        assertEquals(30,fixtures.size)
        val savedFixtures = listOf("Twin Parking Lot Light","Warehouse Wall Floodlight","Household Desk Lamp",
            "Round Recessed Ceiling Light","Basic Chandelier")
        savedFixtures.forEachIndexed { index,name ->
            assertEquals(name,Eln.sixNodeItem.subItemList[(64 shl 6)+20+index]!!.name)
        }
        assertEquals(LampLists.lampTechnologyList.map { it.lampType }.toSet(),bulbs.map { it.lampData.technology.lampType }.toSet())
        for (fixture in fixtures) {
            val index = DecorativeLampStyle.entries.indexOf(fixture.fixture)
            assertEquals(fixture,Eln.sixNodeItem.subItemList[(64 shl 6)+20+index])
            val slot = LampItemSlot(SimpleContainer(2),0,0,0,fixture.bulbCapacity,fixture.acceptedLampTypes)
            assertEquals(fixture.fixture!!.bulbs,slot.maxStackSize)
            for (bulb in bulbs) assertTrue(slot.mayPlace(bulb.newItemStack()),"${fixture.name}: ${bulb.name}")
        }
        assertEquals("Classic Lamp Socket",Eln.sixNodeItem.subItemList[64 shl 6]!!.name)
        assertEquals("Streetlight",Eln.sixNodeItem.subItemList[(64 shl 6)+8]!!.name)
        assertEquals(1,(Eln.sixNodeItem.subItemList[64 shl 6] as LampSocketDescriptor).bulbCapacity)
    }

    @Test fun everyBulbVoltageDrawsItsOwnRatedPowerForEachInstalledBulb() {
        for (fixture in fixtures) for (bulb in bulbs) for (count in setOf(1,fixture.bulbCapacity)) {
            val e = element(fixture)
            e.inventory.setItem(0,bulb.newItemStack(count)); e.apply { motionMode=2; updateMotion(.05) }; e.computeInventory()
            val root = RootSystem(.05,1)
            root.addState(e.electricalLoad); root.addComponent(e.lampResistor)
            root.addComponent(VoltageSource("testSupply",e.electricalLoad,null).apply { voltage = bulb.lampData.nominalU })
            repeat(2) { root.step() }
            assertEquals(bulb.lampData.nominalP*count,e.lampResistor.power,1e-5,"${fixture.name}/${bulb.name}/$count")
        }
    }

    @Test fun newFixtureSizesAndTallerParkingLightMatchTheirClearance() {
        fun mesh(style: DecorativeLampStyle) = Obj3D().apply { assertTrue(loadFile("${style.model}/${style.model}.obj")) }
        val dot = mesh(DecorativeLampStyle.DOT)
        val body = dot.getPart("Metal").boundingBox()
        assertTrue(body.max.x < .24 && body.max.y-body.min.y < .18 && body.max.z-body.min.z < .18)
        assertNotNull(mesh(DecorativeLampStyle.PORCH).getPart("Glass"))
        val parking = fixtures.single { it.fixture == DecorativeLampStyle.PARKING }
        assertEquals(5.15,parking.fixture!!.heads.first().x,1e-6) // Previous head height was 3.15.
        assertEquals(5.43,mesh(DecorativeLampStyle.PARKING).getPart("Metal").boundingBox().max.x,1e-6)
        val clearance = parking.getGhostGroup(Direction.YN,LRDU.Up)!!.elementList
        assertEquals(13,clearance.size)
        assertTrue(clearance.any { it.y == 5 && it.x != 0 })
        assertTrue(clearance.none { it.y == 3 && it.x != 0 })
    }

    @Test fun bulkInsertionRespectsCapacityComponentsAndCreativeConsumption() {
        val bulb = Eln.findItemStack("120V LED Light Bulb",7)
        val descriptor = mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(bulb) as LampDescriptor
        descriptor.setLifeInTag(bulb,33.0)
        val inventory = SimpleContainer(2)
        assertTrue(FixtureBulbs.insert(inventory,bulb,5,false))
        assertEquals(2,bulb.count); assertEquals(5,inventory.getItem(0).count)
        assertEquals(33.0,descriptor.getLifeInTag(inventory.getItem(0)))
        assertFalse(FixtureBulbs.insert(inventory,bulb,5,false))
        inventory.setItem(0,inventory.getItem(0).copyWithCount(1))
        val fresh = descriptor.newItemStack(4)
        assertFalse(FixtureBulbs.insert(inventory,fresh,5,false))
        assertEquals(4,fresh.count)
        assertTrue(FixtureBulbs.insert(inventory,bulb,5,true))
        assertEquals(2,bulb.count); assertEquals(3,inventory.getItem(0).count)
        inventory.setItem(0,ItemStack.EMPTY)
        assertFalse(FixtureBulbs.insert(inventory,Eln.findItemStack("Iron Plate",1),2,false))
    }

    @Test fun wiredTerminalsMatchFixtureModesAndWirelessOnlySurvivesLoad() {
        for (fixture in fixtures) {
            val e = element(fixture)
            e.inventory.setItem(1,Eln.instance.highVoltageCableDescriptor.newItemStack())
            e.poweredByLampSupply = false
            for (port in LRDU.entries) {
                assertEquals(!fixture.wirelessOnly,e.getElectricalLoad(port,0) != null)
                assertEquals(!fixture.wirelessOnly,e.getConnectionMask(port) != 0)
            }
            e.poweredByLampSupply = true
            for (port in LRDU.entries) assertNull(e.getElectricalLoad(port,0))
            if (fixture.wirelessOnly) {
                val saved = CompoundTag().also(e::writeToNBT).apply { putBoolean("poweredByLampSupply",false) }
                e.readFromNBT(saved)
                assertTrue(e.poweredByLampSupply)
            }
        }
    }

    @Test fun everyFixtureMeshHasClosedLitPartsAndCorrectMountingSpace() {
        for (style in DecorativeLampStyle.entries) {
            val model = Obj3D()
            assertTrue(model.loadFile("${style.model}/${style.model}.obj"))
            assertNotNull(model.getPart("Base")); assertNotNull(model.getPart("Metal"))
            for (i in 0 until style.bulbs) {
                val light = assertNotNull(model.getPart("Light$i"))
                val box = light.boundingBox()
                assertTrue(box.max.x > box.min.x)
                assertTrue(box.max.y > box.min.y)
                assertTrue(box.max.z > box.min.z)
            }
            for (side in listOf(Direction.YN,Direction.YP,Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) for(front in LRDU.entries) {
                val origin = net.minecraft.world.phys.Vec3(-.5,64.5,-.5)
                for (head in style.heads) {
                    val point = style.worldPoint(origin,side,front,head)
                    assertTrue(point.x.isFinite() && point.y.isFinite() && point.z.isFinite())
                    assertEquals(head.subtract(net.minecraft.world.phys.Vec3(.5,0.0,0.0)).length(),point.distanceTo(origin),1e-6)
                }
                assertEquals(1.0,style.worldDirection(side,front,style.beam).length(),1e-6)
            }
        }
    }
}
