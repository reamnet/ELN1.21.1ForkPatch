package mods.eln.gridnode

import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor

/** Compatibility of NEW overhead links, not an upgrade of cable or device ratings.
 * Every intact power cable qualifies for HV; new secondary spans also require a jacket.
 * The signalWire flag describes the circuit type, not the creative-tab category: thin utility
 * wires filed under "signal" remain power conductors. Actual signal and damaged cables do not.
 * Bare wire remains bare. Existing saved links and electrical/thermal properties are unchanged.
 */
object GridCablePolicy {
    /** Informational rating only; never a connection-eligibility threshold. */
    fun ratedVoltage(cable: ElectricalCableDescriptor): Double =
        if (cable is UtilityCableDescriptor && cable.insulated) cable.insulationVoltageRating
        else cable.electricalNominalVoltage

    fun accepts(cable: ElectricalCableDescriptor): Boolean =
        !cable.signalWire && !(cable is UtilityCableDescriptor && cable.melted)

    fun insulated(cable: ElectricalCableDescriptor)=cable !is UtilityCableDescriptor || cable.insulated
    fun acceptsTerminal(cable: ElectricalCableDescriptor,terminal: Int)=accepts(cable) &&
        (!mods.eln.utility.ServiceTerminals.isService(terminal) || insulated(cable))
}
