package mods.eln.sixnode.lampsocket

import net.minecraft.core.BlockPos

import mods.eln.item.lampitem.BoilerplateLampData
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils
import mods.eln.sim.IProcess
import mods.eln.sixnode.lampsupply.LampSupplyElement
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord
import mods.eln.misc.getBlockState
import mods.eln.misc.isNothing
import mods.eln.misc.rand

class LampSocketProcess(var element: LampSocketElement) : IProcess {

    private var processElapsedTime = 0.0

    var cachedBestChannelHandle: Pair<Double, LampSupplyElement.PowerSupplyChannelHandle>? = null
    var stableLightProbability = 0.0
    var fastLightValue = 0

    private var lampCountInInventory = 0
    private var cableInInventory = false
    private var lampClearanceReady = false
    private var lampClearanceRetry = 0.0
    private var parkingClearanceReady = false
    private var parkingClearanceRetry = 0.0

    override fun process(time: Double) {
        if (element.descriptor.fixture == DecorativeLampStyle.PARKING && !parkingClearanceReady) {
            parkingClearanceRetry -= time
            if (parkingClearanceRetry <= 0) {
                parkingClearanceReady = ParkingLightClearance.update(element)
                parkingClearanceRetry = 1.0
            }
        }
        if (element.descriptor.fixture != null && !lampClearanceReady) {
            lampClearanceRetry -= time
            if (lampClearanceRetry <= 0) {
                lampClearanceReady = LampClearance.update(element)
                lampClearanceRetry = 1.0
            }
        }
        val energized = element.updateMotion(time)
        if(!energized && element.poweredByLampSupply) element.electricalLoad.state = 0.0
        val lampStack = element.inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)
        val cableStack = element.inventory.getItem(LampSocketContainer.CABLE_SLOT_ID)

        var activeLampSupplyConnection = false
        var newLightValue = BoilerplateLampData.MIN_LIGHT_VALUE
        if(element.descriptor.fixture?.fan==true) {
            if(element.poweredByLampSupply) {
                findBestLampSupply(element.sixNode!!.coordinate)
                val h=cachedBestChannelHandle?.second
                activeLampSupplyConnection=h!=null
                if(h!=null && h.element.getChannelState(h.id) && !cableStack.isEmpty) {
                    h.element.addToRp(element.fanMotor.resistance)
                    element.electricalLoad.state=h.element.powerLoad.state
                } else element.electricalLoad.state=0.0
            }
            element.updateFan()
        }

        if (energized && !lampStack.isNothing() && !cableStack.isNothing()) {
            val lampDescriptor = Utils.getItemObject(lampStack) as LampDescriptor

            if (element.poweredByLampSupply) {
                findBestLampSupply(element.sixNode!!.coordinate)

                val bestLampSupply = cachedBestChannelHandle?.second

                if (bestLampSupply != null && bestLampSupply.element.getChannelState(bestLampSupply.id)) {
                    bestLampSupply.element.addToRp(lampDescriptor.lampData.resistance / element.installedBulbs.coerceAtLeast(1))
                    element.electricalLoad.state = bestLampSupply.element.powerLoad.state
                } else {
                    element.electricalLoad.state = 0.0
                }

                activeLampSupplyConnection = (bestLampSupply != null)
            }

            val lampData = lampDescriptor.lampData
            val lampVoltage = abs(element.lampResistor.voltage)

            if (lampVoltage > (lampData.nominalU * lampData.technology.minimalUFactor)) {
                val num: Double = lampVoltage - (lampData.nominalU * lampData.technology.minimalUFactor)
                val den: Double = lampData.nominalU - (lampData.nominalU * lampData.technology.minimalUFactor)

                newLightValue = ((num / den) * lampData.nominalLightValue).toInt()

                // This code makes the fluorescent lights blink, and the other lights are just "stable"
                if (lampData.technology.lampType == "fluorescent") {
                    if (newLightValue >= LampSocketRender.MIN_LIGHT_ON_VALUE && stableLightProbability <= 1.0) {
                        stableLightProbability += (lampVoltage / lampData.nominalU) * (time / lampData.technology.timeUntilStableInSeconds)
                        if (stableLightProbability < Math.random()) newLightValue = BoilerplateLampData.MIN_LIGHT_VALUE
                        if (stableLightProbability > 1.0) stableLightProbability = 1.0
                    } else {
                        newLightValue = BoilerplateLampData.MIN_LIGHT_VALUE
                        stableLightProbability = 0.0
                    }
                } else {
                    stableLightProbability = 1.0
                }

                if (newLightValue < BoilerplateLampData.MIN_LIGHT_VALUE) newLightValue = BoilerplateLampData.MIN_LIGHT_VALUE
                else if (newLightValue > BoilerplateLampData.MAX_LIGHT_VALUE) newLightValue = BoilerplateLampData.MAX_LIGHT_VALUE
            } else {
                stableLightProbability = 0.0
            }

            if (element.coordinate!!.blockExist) {
                updateNearbyBlocks(lampData.technology.cropGrowthRateFactor, lampData.nominalLightValue, newLightValue, time)
            }

            /* Only decrease the life of a bulb once a second. This reduces the update rate at which the NBT is changed
             * to once per second from once per tick, reducing the probability of an NBT mismatch bug occurring when
             * shift-clicking. When the bug is eventually fixed, the processElapsedTime variable and supporting code can
             * be deleted. Also update the decreaseLampLife function definition according to the note there.
             */
            if (processElapsedTime in -0.001..0.001) {
                val lampLife = lampDescriptor.decreaseLampLife(lampStack, lampVoltage)

                if (lampLife <= 0.0) {
                    newLightValue = BoilerplateLampData.MIN_LIGHT_VALUE
                    element.inventory.setItem(LampSocketContainer.LAMP_SLOT_ID, ItemStack.EMPTY)
                    element.inventory.setChanged()
                }
            }
        } else {
            stableLightProbability = 0.0
        }

        // Only run raytracing when the lamp socket is actually on.
        if (newLightValue > BoilerplateLampData.MIN_LIGHT_VALUE) {
            val fixture = element.descriptor.fixture
            if (fixture != null) DecorativeLampLighting.update(element,fixture,newLightValue) else placeSpot(newLightValue)
        }

        updateFastLight(newLightValue)
        updateInventoryAndPublish(lampStack, cableStack, activeLampSupplyConnection, newLightValue)

        processElapsedTime += time
        if (processElapsedTime >= 1.0) processElapsedTime = 0.0
    }

    private fun findBestLampSupply(coordinate: Coordinate, forceUpdate: Boolean = false) {
        val channelMap = LampSupplyElement.channelMap[element.lampSupplyChannel]

        if (channelMap != null) {
            if (channelMap.contains(cachedBestChannelHandle?.second) &&
                cachedBestChannelHandle?.second?.element?.isInRange(coordinate) == true && !forceUpdate) return
            else {
                channelMap.filterNotNull()
                cachedBestChannelHandle = channelMap
                    .map { Pair(it.element.sixNode!!.coordinate.trueDistanceTo(coordinate), it) }
                    .filter { it.second.element.isInRange(coordinate) }
                    .minByOrNull { it.first }
            }
        } else cachedBestChannelHandle = null
    }

    private fun updateNearbyBlocks(growRate: Double, nominalLight: Int, actualLight: Int, deltaT: Double) {
        val randTarget = growRate * deltaT * (actualLight.toDouble() / nominalLight.toDouble())

        if (randTarget > Math.random()) {
            var rotationVector = Vec3(1.0, 0.0, 0.0)
            rotationVector = rotationVector.xRot((element.projectionRotationAngle * (Math.PI / 180.0)).toFloat())
            rotationVector = rotationVector.yRot(((Math.random() - 0.5) * Math.PI / 2.0).toFloat())
            rotationVector = rotationVector.xRot(((Math.random() - 0.5) * Math.PI / 2.0).toFloat())
            rotationVector = element.front.rotateOnXnLeft(rotationVector)
            rotationVector = element.side.rotateFromXN(rotationVector)

            val lbCoordinate = raytrace(rotationVector, actualLight)
            val lbPos = BlockPos(lbCoordinate.x, lbCoordinate.y, lbCoordinate.z)
            val lbWorld = lbCoordinate.world()
            // 1.7.10 ticked the lit block by hand; the light block schedules nothing (its entity ticks), so the state read suffices.
            lbWorld.getBlockState(lbPos)
        }
    }

    private fun placeSpot(lightValue: Int) {
        var rotationVector = Vec3(1.0, 0.0, 0.0)
        rotationVector = rotationVector.xRot((element.projectionRotationAngle * (Math.PI / 180.0)).toFloat())
        rotationVector = element.front.rotateOnXnLeft(rotationVector)
        rotationVector = element.side.rotateFromXN(rotationVector)

        val lbCoordinate = raytrace(rotationVector, 0)

        // This makes the projected light "flicker" when a fluorescent bulb is turning on. It's not quite in sync with
        // the bulb, but it's the best that can be done without rewriting the light block handler to allow updating the
        // light value of an existing light block.
        val lightTimeout = if (stableLightProbability <= 1.0) 1 else 5

        LightBlockEntity.addLight(lbCoordinate, lightValue, lightTimeout)
    }

    private fun raytrace(rotationVector: Vec3, vectorLengthModifier: Int): Coordinate {
        val mounting=element.sixNode!!.coordinate
        val inset=LampMounting.inset(mounting.world(),mounting.pos,element.side,element.descriptor)
        val origin = mounting.toVec3().add(element.side.rotateFromXN(Vec3(-inset,0.0,0.0)))
        val lbCoordinate = Coordinate(origin, element.sixNode!!.coordinate.dimension)

        // Vec3 is immutable on 1.12, and this steps a ray one block at a time, so the position
        // is carried in three doubles rather than reallocating a vector per step.
        var lightX = origin.x
        var lightY = origin.y
        var lightZ = origin.z
        val position = DoubleArray(3)

        fun placeAt(x: Double, y: Double, z: Double) {
            position[0] = x; position[1] = y; position[2] = z
            lbCoordinate.setPosition(position)
        }

        for (idx in 0 until element.descriptor.range + vectorLengthModifier) {
            lightX += rotationVector.x
            lightY += rotationVector.y
            lightZ += rotationVector.z
            placeAt(lightX, lightY, lightZ)

            if (!lbCoordinate.blockExist || lbCoordinate.blockState.isSolidRender(lbCoordinate.world(), lbCoordinate.pos)) {
                // Step back so the light lands in the last open block, not inside the wall.
                placeAt(lightX - rotationVector.x, lightY - rotationVector.y, lightZ - rotationVector.z)
                break
            }
        }

        return lbCoordinate
    }

    /**
     * Sync "fast" light changes (fluorescent flicker)
     */
    private fun updateFastLight(newLightValue: Int) {
        if (fastLightValue != newLightValue) {
            fastLightValue = newLightValue

            val bos = ByteArrayOutputStream(64)
            val packet = DataOutputStream(bos)

            element.preparePacketForClient(packet)

            try {
                packet.writeInt(newLightValue)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            element.sendPacketToAllClient(bos)
        }
    }

    /**
     * Manually update the server-side inventory every time an item is inserted/removed from the GUI.
     * This should be happening automatically, but it is not.
     */
    private fun updateInventoryAndPublish(lampStack: ItemStack?, cableStack: ItemStack?, activeLampSupplyConnection: Boolean, newLightValue: Int) {
        var inventoryChanged = false
        var publishChanges = false

        if (lampCountInInventory != element.installedBulbs) {
            lampCountInInventory = element.installedBulbs
            inventoryChanged = true
        }

        if (cableInInventory != (!cableStack.isNothing())) {
            cableInInventory = (!cableStack.isNothing())
            inventoryChanged = true
        }

        if (element.activeLampSupplyConnection != activeLampSupplyConnection) {
            element.activeLampSupplyConnection = activeLampSupplyConnection
            publishChanges = true
        }

        if (element.sixNode!!.lightValue != newLightValue) {
            element.sixNode!!.lightValue = newLightValue
            publishChanges = true
        }

        // Prevent duplicate calls of these functions
        if (inventoryChanged) element.inventoryChange(element.inventory)
        else if (publishChanges) element.needPublish()
    }

}
