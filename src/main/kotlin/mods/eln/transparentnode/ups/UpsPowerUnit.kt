package mods.eln.transparentnode.ups

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.power.*
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.IOException
import kotlin.math.min

/** Online UPS: two isolated regulated stages share a stiff, energy-accounted battery DC bus. */
class UpsPowerUnit(owner: TransparentNodeElement, val bank: UpsBatteryBank) {
    companion object {
        const val MAX_WATTS=10_000.0
        const val MAX_INPUT_VOLTS=51_200.0
        const val MIN_OUTPUT_VOLTS=1.0
        const val MAX_OUTPUT_VOLTS=3200.0
        const val EFFICIENCY=.95
        private const val BUS_VOLTS=48.0
        fun validVoltage(v: Double)=v.isFinite() && v in MIN_OUTPUT_VOLTS..MAX_OUTPUT_VOLTS
    }
    var outputVoltage=120.0
        private set
    var enabled=true
        private set
    var bypass=false
        private set
    private var stepSeconds=.05
    val primary=NbtElectricalLoad("upsPrimary")
    val secondary=NbtElectricalLoad("upsSecondary")
    val bypassContact=mods.eln.transparentnode.standby.TransferContact(primary,secondary)
    private val inputPin=NbtElectricalLoad("upsInputPin")
    private val outputPin=NbtElectricalLoad("upsOutputPin")
    private val batteryPin=NbtElectricalLoad("upsBatteryPin")
    private val bus=NbtElectricalLoad("upsBus")
    private val chargePin=NbtElectricalLoad("upsChargePin")
    private val inverterPin=NbtElectricalLoad("upsInverterPin")
    val batterySource=VoltageSource("upsCells",batteryPin,null).apply { voltage=BUS_VOLTS }
    private val batteryWire=Resistor(batteryPin,bus).apply { resistance=.0005 }
    private val inputWire=Resistor(primary,inputPin).apply { resistance=.01 }
    private val outputWire=Resistor(outputPin,secondary).apply { resistance=.001 }
    private val chargeWire=Resistor(chargePin,bus).apply { resistance=.001 }
    private val inverterWire=Resistor(bus,inverterPin).apply { resistance=.001 }
    val chargerInput=SwitchableVoltageSource("upsChargerInput").apply { connectTo(inputPin,null) }
    val chargerOutput=SwitchableVoltageSource("upsChargerOutput").apply { connectTo(chargePin,null) }
    val inverterInput=SwitchableVoltageSource("upsInverterInput").apply { connectTo(inverterPin,null) }
    val inverterOutput=SwitchableVoltageSource("upsInverterOutput").apply { connectTo(outputPin,null) }
    val thermal=NbtThermalLoad("upsThermal").apply { heatCapacity=20_000.0;Rp=.025;Rs=.1;setAsSlow() }
    private val cool get()=thermal.temperatureCelsius<90.0
    private val chargeControl=UpsChargeControl()
    private fun requestedChargeWatts()=min(16_000.0,(-inverterInput.power).coerceAtLeast(0.0)+bank.availableChargeWatts(stepSeconds))
    val charger=RegulatedPowerProcess(inputPin,null,chargePin,null,chargerInput,chargerOutput,
        { bank.activeCount>0 && cool && (bank.energy<bank.capacity-1e-7 || -inverterInput.power>1e-7) },
        {
            // Feed the inverter's last accepted demand, plus bounded charging headroom.
            // Load changes are absorbed by the battery for one physical step.
            ConverterLimits(12.0,MAX_INPUT_VOLTS,50.0,100.0,500.0,
                min(requestedChargeWatts(),chargeControl.outputBudget(chargerInput.voltage)),EFFICIENCY,1.0/4096,8.0)
        }, { 49.0 })
    val inverter=RegulatedPowerProcess(inverterPin,null,outputPin,null,inverterInput,inverterOutput,
        { !bypass && enabled && cool && bank.activeCount>0 && bank.energy>1e-7 },
        {
            val available=bank.availableDischargeWatts(stepSeconds)
            ConverterLimits(40.0,52.0,MAX_OUTPUT_VOLTS,min(400.0,available/BUS_VOLTS),200.0,
                MAX_WATTS,EFFICIENCY,1.0/128,128.0)
        }, { outputVoltage })
    /** The clock advances once per accepted physical step, never inside convergence trials. */
    val clock=object : ConservativePowerProcess {
        override fun beginStep(seconds: Double) {
            if(!seconds.isFinite() || seconds<=0)return
            stepSeconds=seconds
            val input=probePort(inputPin,null,chargerInput)
            chargeControl.advance(seconds,input.volts,requestedChargeWatts()/EFFICIENCY,chargerInput.enabled && inputWatts>1)
        }
        override fun rootSystemPreStepProcess() {}
        override fun prepareStep() {
            // Open the inverter before the direct feed, or open the feed before
            // the inverter is prepared. No accepted solve connects both outputs.
            if(bypass) { inverterInput.enabled=false;inverterOutput.enabled=false }
            bypassContact.closed=bypass && enabled
        }
        override fun acceptsCandidate()=true
        override fun failClosed() {}
        override fun connectedSystems()=emptySet<mods.eln.sim.mna.SubSystem>()
    }
    val controllers get()=listOf(clock,charger,inverter)
    var batteryWatts=0.0
        private set
    val outputWatts get()=if(bypassContact.closed)(secondary.voltage*bypassContact.current).coerceAtLeast(0.0) else inverterOutput.power.coerceAtLeast(0.0)
    val inputWatts get()=(-chargerInput.power).coerceAtLeast(0.0)+(if(bypassContact.closed)primary.voltage*bypassContact.current else 0.0).coerceAtLeast(0.0)
    val status: String get()=when {
        bypass && enabled -> if(primary.voltage>1.0)"BYPASS" else "BYPASS_NO_INPUT"
        bank.activeCount==0 -> "NEEDS_BATTERIES"
        !cool -> "OVERTEMPERATURE"
        !enabled -> if(inputWatts>1) "CHARGING_OFF" else "DISABLED"
        bank.energy<=1e-7 -> "EMPTY"
        !inverterOutput.enabled -> "PROTECTED"
        inverter.status !in listOf("REGULATING","IDLE") -> "LIMITED"
        primary.voltage !in 12.0..MAX_INPUT_VOLTS || (inputWatts<1 && outputWatts>1) -> "BACKUP"
        batteryWatts > 1 -> "ASSIST"
        batteryWatts < -1 -> "CHARGING"
        else -> "ONLINE"
    }
    init {
        listOf(primary,secondary,inputPin,outputPin,batteryPin,bus,chargePin,inverterPin).forEach {
            it.serialResistance=.000001;owner.electricalLoadList.add(it)
        }
        owner.electricalComponentList.addAll(listOf(batterySource,batteryWire,inputWire,outputWire,chargeWire,inverterWire,
            chargerInput,chargerOutput,inverterInput,inverterOutput,bypassContact))
        owner.thermalLoadList.add(thermal)
        owner.electricalProcessList.add(IProcess { seconds -> account(seconds) })
    }
    fun account(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0) return
        batteryWatts=batterySource.power.takeIf { it.isFinite() } ?: 0.0
        val requested=-batteryWatts*seconds
        val actual=bank.transfer(requested,seconds)
        // Excess charging at a full cell is dissipated; it never becomes extra stored energy.
        val rejected=(requested-actual).coerceAtLeast(0.0)/seconds
        val electronics=(-chargerInput.power-chargerOutput.power-inverterInput.power-inverterOutput.power).coerceAtLeast(0.0)
        thermal.movePowerTo(rejected+electronics+batteryWire.power+inputWire.power+outputWire.power+chargeWire.power+inverterWire.power+bypassContact.power)
    }
    fun settings(tag: CompoundTag) {
        val value=tag.getDouble("upsOutputVoltage")
        outputVoltage=value.takeIf(::validVoltage) ?: 120.0
        enabled=!tag.contains("upsEnabled") || tag.getBoolean("upsEnabled")
        bypass=tag.getBoolean("upsBypass")
        charger.resetFault();inverter.resetFault()
    }
    fun write(tag: CompoundTag) { tag.putDouble("upsOutputVoltage",outputVoltage);tag.putBoolean("upsEnabled",enabled);tag.putBoolean("upsBypass",bypass) }
    fun packet(id: Byte, stream: DataInputStream): Boolean {
        if(id.toInt() !in 80..82)return false
        try {
            when(id.toInt()) {
                80 -> { val value=stream.readDouble();if(!validVoltage(value))return true;outputVoltage=value }
                81 -> enabled=stream.readBoolean()
                82 -> bypass=stream.readBoolean()
            }
            charger.resetFault();inverter.resetFault()
        } catch(_: IOException) {}
        return true
    }
}
