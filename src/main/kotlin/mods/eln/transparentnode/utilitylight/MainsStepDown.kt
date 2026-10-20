package mods.eln.transparentnode.utilitylight

import mods.eln.i18n.I18N.tr
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.power.ConverterLimits
import mods.eln.sim.power.RegulatedPowerProcess
import mods.eln.sim.power.ResettablePowerProcess
import mods.eln.sim.power.SafeTransformerProcess
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Shared hardware: standalone active converter, or fixed-ratio 12.47 kV pole transformer. */
class MainsStepDown(private val owner: TransparentNodeElement, val poleTransformer: Boolean = false, available: () -> Boolean = { true }) {
    companion object {
        const val NOMINAL_POLE_VOLTS = 12_470.0
        const val MAX_INPUT_VOLTS = 51_200.0
        const val MAX_OUTPUT_WATTS = 10_000.0
        const val MAX_INPUT_AMPS = 100.0
        const val EFFICIENCY = .95
        fun validVoltage(value: Int) = value == 120 || value == 240
        val RATINGS=intArrayOf(10_000,25_000,50_000,75_000,100_000)
        fun limits(voltage: Int,watts: Int=10_000) = ConverterLimits(voltage.toDouble(), MAX_INPUT_VOLTS, 240.0,
            MAX_INPUT_AMPS, watts.toDouble() / voltage, watts.toDouble(), EFFICIENCY, 1.0 / 1024, 1.0)
    }

    var ratedWatts=10_000
        private set
    var outputVoltage = 120
        private set
    var enabled = true
        private set
    var lampEnabled = true
        private set
    var lampAutomatic = false
        private set
    var lampHeightStep = 0
        private set
    var lampYaw = 0
        private set
    val primary = NbtElectricalLoad("mainsPrimary")
    val secondary = NbtElectricalLoad("mainsSecondary")
    private val inputPin = NbtElectricalLoad("mainsInputPin")
    private val outputPin = NbtElectricalLoad("mainsOutputPin")
    val input = SwitchableVoltageSource("mainsInput").apply { connectTo(inputPin, null) }
    val output = SwitchableVoltageSource("mainsOutput").apply { connectTo(outputPin, null) }
    private val inputWinding = Resistor(primary, inputPin).apply { resistance = .01 }
    private val outputWinding = Resistor(outputPin, secondary).apply { resistance = .01 }
    val thermal = NbtThermalLoad("mainsThermal").apply {
        heatCapacity = 2000.0; Rp = .08; Rs = .1; setAsSlow()
    }
    val controller: ResettablePowerProcess = if (poleTransformer)
        SafeTransformerProcess(inputPin, outputPin, input, output) {
            enabled && available() && thermal.temperatureCelsius < 100.0
        }.apply {
            ratio = outputVoltage / NOMINAL_POLE_VOLTS
            maximumPrimaryVoltage = MAX_INPUT_VOLTS
        }
    else RegulatedPowerProcess(inputPin, null, outputPin, null, input, output,
        { enabled && available() && thermal.temperatureCelsius < 100.0 },
        { limits(outputVoltage,ratedWatts) }, { outputVoltage.toDouble() })

    init {
        listOf(primary, secondary, inputPin, outputPin).forEach {
            it.serialResistance = .00001
            owner.electricalLoadList.add(it)
        }
        owner.electricalComponentList.addAll(listOf(output, inputWinding, outputWinding))
        owner.electricalComponentList.add(input)
        owner.thermalLoadList.add(thermal)
        owner.electricalProcessList.add(IProcess {
            // The fixed-ratio pole has physical copper losses; the standalone converter also has electronics losses.
            thermal.movePowerTo((-input.power - output.power).coerceAtLeast(0.0) + inputWinding.power + outputWinding.power)
        })
    }

    /** Uses the existing solve result, never another expensive Thevenin probe. */
    val outputOverloaded get()=!poleTransformer && secondary.voltage<outputVoltage*.92 && when(controller.status) {
        "OUTPUT_POWER","OUTPUT_CURRENT","GAIN_UNAVAILABLE" -> true
        else -> false
    }

    private fun updateController() {
        (controller as? SafeTransformerProcess)?.ratio = outputVoltage / NOMINAL_POLE_VOLTS
        controller.resetFault()
    }

    fun write(tag: CompoundTag) {
        tag.putInt("mainsRatedWatts",ratedWatts)
        tag.putInt("mainsOutputVoltage", outputVoltage)
        tag.putBoolean("mainsEnabled", enabled)
        tag.putBoolean("mainsLampEnabled", lampEnabled)
        tag.putBoolean("mainsLampAutomatic", lampAutomatic)
        tag.putInt("mainsLampHeightStep", lampHeightStep)
        tag.putInt("mainsLampYaw", lampYaw)
    }
    fun read(tag: CompoundTag) {
        ratedWatts=tag.getInt("mainsRatedWatts").takeIf { it in RATINGS } ?: 10_000
        thermal.heatCapacity=2000.0*ratedWatts/10_000;thermal.Rp=.08*10_000/ratedWatts;outputWinding.resistance=.01*10_000/ratedWatts
        outputVoltage = tag.getInt("mainsOutputVoltage").takeIf(::validVoltage) ?: 120
        enabled = !tag.contains("mainsEnabled") || tag.getBoolean("mainsEnabled")
        lampEnabled = !tag.contains("mainsLampEnabled") || tag.getBoolean("mainsLampEnabled")
        lampAutomatic = tag.getBoolean("mainsLampAutomatic")
        lampHeightStep = tag.getInt("mainsLampHeightStep").takeIf(StreetLightMount::validStep) ?: 0
        lampYaw = tag.getInt("mainsLampYaw").takeIf(StreetLightMount::validYaw) ?: 0
        updateController()
    }
    fun packet(id: Byte, stream: DataInputStream): Boolean {
        if (id.toInt() !in 80..86) return false
        try {
            when (id.toInt()) {
                86 -> { val next=stream.readInt();if(next in RATINGS) { ratedWatts=next;thermal.heatCapacity=2000.0*ratedWatts/10_000;thermal.Rp=.08*10_000/ratedWatts;outputWinding.resistance=.01*10_000/ratedWatts } }
                80 -> { val value = stream.readInt(); if (!validVoltage(value)) return true; outputVoltage = value }
                81 -> enabled = stream.readBoolean()
                82 -> lampEnabled = stream.readBoolean()
                83 -> lampAutomatic = stream.readBoolean()
                84 -> {
                    val movement = stream.readInt()
                    if (movement == -1 || movement == 1)
                        lampHeightStep = (lampHeightStep + movement).coerceIn(StreetLightMount.MIN_STEP, StreetLightMount.MAX_STEP)
                    return true // Moving the fixture must not reset the electrical controller.
                }
                85 -> {
                    val movement = stream.readInt()
                    if (movement == -5 || movement == -1 || movement == 1 || movement == 5)
                        lampYaw = StreetLightMount.turn(lampYaw, movement)
                    return true
                }
            }
            updateController()
        } catch (_: IOException) { /* Reject truncated settings without mutating them. */ }
        return true
    }
    fun publish(stream: DataOutputStream, installed: Boolean, light: Int) {
        stream.writeInt(ratedWatts);stream.writeInt(outputVoltage); stream.writeBoolean(enabled); stream.writeBoolean(lampEnabled); stream.writeBoolean(lampAutomatic)
        stream.writeBoolean(installed); stream.writeByte(light)
        stream.writeByte(lampHeightStep)
        stream.writeShort(lampYaw)
        stream.writeDouble(primary.voltage); stream.writeDouble(secondary.voltage)
        stream.writeDouble(output.power); stream.writeUTF(controller.status)
        owner.node!!.lrduCubeMask.getTranslate(owner.front.down()).serialize(stream)
    }
    fun waila() = linkedMapOf(
        tr("Input voltage") to mods.eln.misc.Utils.plotVolt("", primary.voltage),
        tr("Output voltage") to mods.eln.misc.Utils.plotVolt("", secondary.voltage),
        tr("Output power") to mods.eln.misc.Utils.plotPower("", output.power),
        tr("Converter state") to mods.eln.transparentnode.converterStateText(controller.status))
}
