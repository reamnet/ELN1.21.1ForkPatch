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
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Shared hardware for the placeable unit and the identical unit installed in a pole. */
class MainsStepDown(private val owner: TransparentNodeElement, available: () -> Boolean = { true }) {
    companion object {
        const val MAX_INPUT_VOLTS = 51_200.0
        const val MAX_OUTPUT_WATTS = 10_000.0
        const val MAX_INPUT_AMPS = 100.0
        const val EFFICIENCY = .95
        fun validVoltage(value: Int) = value == 120 || value == 240
        fun limits(voltage: Int) = ConverterLimits(voltage.toDouble(), MAX_INPUT_VOLTS, 240.0,
            MAX_INPUT_AMPS, MAX_OUTPUT_WATTS / voltage, MAX_OUTPUT_WATTS, EFFICIENCY, 1.0 / 1024, 1.0)
    }

    var outputVoltage = 120
        private set
    var enabled = true
        private set
    var lampEnabled = true
        private set
    var lampAutomatic = false
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
    val controller = RegulatedPowerProcess(inputPin, null, outputPin, null, input, output,
        { enabled && available() && thermal.temperatureCelsius < 100.0 },
        { limits(outputVoltage) }, { outputVoltage.toDouble() })

    init {
        listOf(primary, secondary, inputPin, outputPin).forEach {
            it.serialResistance = .00001
            owner.electricalLoadList.add(it)
        }
        owner.electricalComponentList.addAll(listOf(input, output, inputWinding, outputWinding))
        owner.thermalLoadList.add(thermal)
        owner.electricalProcessList.add(IProcess {
            // Account for electronics and both physical winding losses after the accepted solve.
            thermal.movePowerTo((-input.power - output.power).coerceAtLeast(0.0) + inputWinding.power + outputWinding.power)
        })
    }

    fun write(tag: CompoundTag) {
        tag.putInt("mainsOutputVoltage", outputVoltage)
        tag.putBoolean("mainsEnabled", enabled)
        tag.putBoolean("mainsLampEnabled", lampEnabled)
        tag.putBoolean("mainsLampAutomatic", lampAutomatic)
    }
    fun read(tag: CompoundTag) {
        outputVoltage = tag.getInt("mainsOutputVoltage").takeIf(::validVoltage) ?: 120
        enabled = !tag.contains("mainsEnabled") || tag.getBoolean("mainsEnabled")
        lampEnabled = !tag.contains("mainsLampEnabled") || tag.getBoolean("mainsLampEnabled")
        lampAutomatic = tag.getBoolean("mainsLampAutomatic")
        controller.resetFault()
    }
    fun packet(id: Byte, stream: DataInputStream): Boolean {
        if (id.toInt() !in 80..83) return false
        try {
            when (id.toInt()) {
                80 -> { val value = stream.readInt(); if (!validVoltage(value)) return true; outputVoltage = value }
                81 -> enabled = stream.readBoolean()
                82 -> lampEnabled = stream.readBoolean()
                83 -> lampAutomatic = stream.readBoolean()
            }
            controller.resetFault()
        } catch (_: IOException) { /* Reject truncated settings without mutating them. */ }
        return true
    }
    fun publish(stream: DataOutputStream, installed: Boolean, light: Int) {
        stream.writeInt(outputVoltage); stream.writeBoolean(enabled); stream.writeBoolean(lampEnabled); stream.writeBoolean(lampAutomatic)
        stream.writeBoolean(installed); stream.writeByte(light)
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
