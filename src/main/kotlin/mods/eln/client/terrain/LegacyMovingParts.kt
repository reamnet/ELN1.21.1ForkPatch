package mods.eln.client.terrain

import mods.eln.mechanical.*
import mods.eln.node.transparent.TransparentNodeElementRender

/** Named parts which rotate through external matrix commands rather than an OBJ angle overload.
 * Angle/UV overloads, text, item contents, halo billboards and explicitly marked regions stay live. */
internal object LegacyMovingParts {
    private val cached=java.util.IdentityHashMap<Any,Set<String>>()
    fun names(owner: Any):Set<String> {
        val descriptor=when(owner) {
            is TransparentNodeElementRender -> owner.transparentNodeDescriptor
            is mods.eln.node.six.SixNodeElementRender -> owner.sixNodeDescriptor
            else -> owner.javaClass
        }
        return cached.getOrPut(descriptor) { compute(owner) }
    }
    private fun compute(owner: Any): Set<String> {
        val result=HashSet<String>()
        val descriptor=when(owner) {
            is TransparentNodeElementRender -> owner.transparentNodeDescriptor
            is mods.eln.node.six.SixNodeElementRender -> owner.sixNodeDescriptor
            else -> null
        }
        if(descriptor is SimpleShaftDescriptor)result+=descriptor.rotating.map { it.meshName }
        result+=when(descriptor) {
            is mods.eln.sixnode.energymeter.EnergyMeterDescriptor ->
                (listOfNotNull(descriptor.powerDisk,descriptor.energySignWheel,descriptor.timeUnitWheel,descriptor.energyUnitWheel)+descriptor.energyNumberWheel+descriptor.timeNumberWheel).map { it.meshName }.toSet()
            is JointHubDescriptor -> descriptor.rotatingOnAllSides.map { it.meshName }.toSet()
            is VerticalHubDescriptor -> descriptor.rotatingOnConnectedSides.map { it.meshName }.toSet()
            is ClutchDescriptor -> setOf(descriptor.leftShaftPart.meshName,descriptor.rightShaftPart.meshName)
            is RollingShaftMachineDescriptor -> setOf("rot2")
            is GeneratorDescriptor -> descriptor.powerLights.map { it.meshName }.toSet()
            is MotorDescriptor -> descriptor.leds.map { it.meshName }.toSet()
            else -> emptySet()
        }
        result+=when(owner.javaClass.simpleName) {
            "ElectricalSwitchRender","ElectricalGateSourceRender","WirelessSignalSourceRender" -> setOf("lever","button","rot","led","halo")
            "ResistorRender" -> setOf("ResistorWiper")
            "LargeRheostatRender" -> setOf("wiper")
            "ElectricalMachineRender" -> setOf("move")
            "TurretRender" -> setOf("Turret","LeftGun","RightGun","Sensor","Fire")
            "AutoMinerRender" -> setOf("MinerPipe","MinerHead")+(0..15).map { "button$it" }
            "FloodlightRender" -> setOf("Lamp_Swivel_Cube.014","Lamp_Head_Cylinder.004","Lamp1_OFF_Cylinder.003","Lamp2_OFF_Cylinder.002","Lamp1_ON_Cylinder.000","Lamp2_ON_Cylinder.001")
            "LampSocketRender" -> setOf("lightAlpha","lightAlphaNoDepth")
            "FabricatorRender" -> setOf("Cube.003_Cube.012","Cube.001_Cube.010")
            "EvaporativeCoolerRender" -> setOf("rotor","water")
            else -> emptySet()
        }
        return result
    }
}
