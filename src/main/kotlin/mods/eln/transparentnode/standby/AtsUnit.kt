package mods.eln.transparentnode.standby

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag

class AtsUnit(owner: TransparentNodeElement) {
    val mains=NbtElectricalLoad("atsMains").apply { serialResistance=.000001 }
    val backup=NbtElectricalLoad("atsBackup").apply { serialResistance=.000001 }
    val output=NbtElectricalLoad("atsOutput").apply { serialResistance=.000001 }
    val mainContact=TransferContact(mains,output)
    val backupContact=TransferContact(backup,output)
    val control=TransferControl()
    var nominal=240.0
        private set
    var enabled=true
        private set
    var channel=""
    val watts get()=(output.voltage*(mainContact.current+backupContact.current)).coerceAtLeast(0.0)
    val status get()=when {
        !enabled -> "OFF"
        control.selected==TransferFeed.MAINS -> "MAINS"
        control.selected==TransferFeed.GENERATOR -> "GENERATOR"
        control.backupFault -> "BACKUP_FEED_FAULT"
        control.request -> "WAITING_GENERATOR"
        else -> "TRANSFER"
    }
    init {
        owner.electricalLoadList.addAll(listOf(mains,backup,output))
        owner.electricalComponentList.addAll(listOf(mainContact,backupContact))
        listOf(mains,backup,output).forEach { pin -> owner.electricalComponentList.add(Resistor(pin,null).apply { resistance=1e10 }) }
        owner.electricalProcessList.add(IProcess(::tick))
    }
    fun tick(seconds: Double) {
        val selected=control.step(seconds,mains.voltage,backup.voltage,nominal,enabled)
        // Open both first, even when settings change or the desired feed reverses mid-transfer.
        if(selected!=TransferFeed.MAINS)mainContact.closed=false
        if(selected!=TransferFeed.GENERATOR)backupContact.closed=false
        if(selected==TransferFeed.MAINS)mainContact.closed=true
        if(selected==TransferFeed.GENERATOR)backupContact.closed=true
    }
    fun reset() { mainContact.closed=false;backupContact.closed=false;control.reset() }
    fun setVoltage(value: Double): Boolean {
        if(!TransferControl.validVoltage(value))return false
        nominal=value;reset();return true
    }
    fun setEnabled(value: Boolean) { enabled=value;reset() }
    fun read(nbt: CompoundTag) {
        reset();nominal=nbt.getDouble("atsVoltage").takeIf(TransferControl::validVoltage) ?: 240.0
        enabled=!nbt.contains("atsEnabled") || nbt.getBoolean("atsEnabled")
        channel=StandbyLink.channel(nbt.getString("standbyChannel"))
    }
    fun write(nbt: CompoundTag) {
        nbt.putDouble("atsVoltage",nominal);nbt.putBoolean("atsEnabled",enabled);nbt.putString("standbyChannel",channel)
    }
}
