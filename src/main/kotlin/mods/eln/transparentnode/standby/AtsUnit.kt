package mods.eln.transparentnode.standby

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag

class AtsUnit @JvmOverloads constructor(owner: TransparentNodeElement, private val contactChanged: (Boolean)->Unit = {}) {
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
        contacts(selected==TransferFeed.MAINS,selected==TransferFeed.GENERATOR)
    }
    private fun contacts(main: Boolean, backup: Boolean, audible: Boolean=true) {
        val opened=mainContact.closed && !main || backupContact.closed && !backup
        val closed=!mainContact.closed && main || !backupContact.closed && backup
        // Open first; sound follows actual mechanical changes, never solver retries or GUI refreshes.
        if(!main)mainContact.closed=false
        if(!backup)backupContact.closed=false
        if(main)mainContact.closed=true
        if(backup)backupContact.closed=true
        if(audible) {
            if(opened)contactChanged(false)
            if(closed)contactChanged(true)
        }
    }
    @JvmOverloads fun reset(audible: Boolean=true) { contacts(false,false,audible);control.reset() }
    fun setVoltage(value: Double): Boolean {
        if(!TransferControl.validVoltage(value))return false
        nominal=value;reset();return true
    }
    fun setEnabled(value: Boolean) { enabled=value;reset() }
    fun read(nbt: CompoundTag) {
        reset(false);nominal=nbt.getDouble("atsVoltage").takeIf(TransferControl::validVoltage) ?: 240.0
        enabled=!nbt.contains("atsEnabled") || nbt.getBoolean("atsEnabled")
        channel=StandbyLink.channel(nbt.getString("standbyChannel"))
    }
    fun write(nbt: CompoundTag) {
        nbt.putDouble("atsVoltage",nominal);nbt.putBoolean("atsEnabled",enabled);nbt.putString("standbyChannel",channel)
    }
}
