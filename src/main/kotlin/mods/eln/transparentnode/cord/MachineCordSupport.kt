package mods.eln.transparentnode.cord

import mods.eln.item.HeatingCorpElement
import mods.eln.mechanical.MotorElement
import mods.eln.misc.Utils
import mods.eln.node.INodeElement
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.batterycharger.BatteryChargerElement
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.transparentnode.*
import mods.eln.transparentnode.autominer.AutoMinerDescriptor
import mods.eln.transparentnode.electricalantennatx.ElectricalAntennaTxDescriptor
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceElement
import mods.eln.transparentnode.electricalmachine.ElectricalMachineDescriptor
import mods.eln.transparentnode.eggincubator.EggIncubatorDescriptor
import mods.eln.transparentnode.evaporative.EvaporativeCoolerElement
import mods.eln.transparentnode.teleporter.TeleporterDescriptor
import mods.eln.transparentnode.thermaldissipatoractive.ThermalDissipatorActiveDescriptor
import mods.eln.transparentnode.turret.TurretDescriptor

/** Optional inlet on existing consumers: the cord feeds their original simulated load and controls. */
class MachineCordDevice(
    owner: INodeElement,
    val input: NbtElectricalLoad,
    private val nominal: () -> Double,
    private val returnLoad: NbtElectricalLoad?=null
): CordDevice {
    override val cord by lazy { ApplianceCord(owner,input,returnLoad) }
    override val cordVoltage get()=nominal()
    override fun acceptsCord(rating: CordRating)=rating.accepts(cordVoltage)
    fun blocks(load: ElectricalLoad?)=load!=null && cord.plugged && (load===input || load===returnLoad)
}

object CordDevices {
    fun get(owner: INodeElement): CordDevice?=when(owner) {
        is CordDevice -> owner
        is TransparentNodeElement -> owner.machineCord
        is SixNodeElement -> owner.machineCord
        else -> null
    }
}

object MachineCordSupport {
    fun create(e: SixNodeElement): MachineCordDevice?=when(e) {
        is BatteryChargerElement -> MachineCordDevice(e,e.powerLoad,{ e.descriptor.nominalVoltage })
        is LampSupplyElement -> MachineCordDevice(e,e.powerLoad,{ e.descriptor.nominalVoltage })
        else -> null
    }
    fun create(e: TransparentNodeElement): MachineCordDevice? {
        // Explicit consumer families only: generators, storage outputs and signal terminals must not become inputs.
        if(e is MotorElement)return MachineCordDevice(e,e.wireLoad,{ e.desc.nominalU.toDouble() },if(e.desc.bipolarTerminals)e.negativeLoad else null)
        if(e is EvaporativeCoolerElement)return MachineCordDevice(e,e.supply,{ EvaporativeCoolerElement.VOLTAGE })
        val d=e.transparentNodeDescriptor
        val nominal: ()->Double=when {
            d is ElectricalMachineDescriptor -> { { d.nominalU } }
            d is AutoMinerDescriptor -> { { d.nominalVoltage } }
            d is EggIncubatorDescriptor -> { { d.nominalVoltage } }
            d is ThermalDissipatorActiveDescriptor -> { { d.nominalElectricalU } }
            d is ElectricalAntennaTxDescriptor -> { { d.electricalNominalVoltage } }
            d is TeleporterDescriptor -> { { d.cable.electricalNominalVoltage } }
            d is TurretDescriptor || e is ArcFurnaceElement -> { { 800.0 } }
            e is WireMachineElement || e is FabricatorElement -> { { 200.0 } }
            e is ElectricalFurnaceElement -> { { (Utils.getItemObject((e as TransparentNodeElement).inventory!!.getItem(ElectricalFurnaceElement.heatingCorpSlotId)) as? HeatingCorpElement)?.electricalNominalU ?: Double.NaN } }
            else -> return null
        }
        // Existing consumer constructors list their real power inlet before any optional signal loads.
        val input=e.electricalLoadList.filterIsInstance<NbtElectricalLoad>().firstOrNull() ?: return null
        return MachineCordDevice(e,input,nominal)
    }
}
