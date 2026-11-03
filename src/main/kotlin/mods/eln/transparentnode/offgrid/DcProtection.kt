package mods.eln.transparentnode.offgrid

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.standby.TransferContact
import mods.eln.utility.CutoutFuse
import net.minecraft.nbt.CompoundTag
import kotlin.math.abs

/** Two independently fused PV positives share a return; the handle opens both inputs. */
class DcProtection(owner: TransparentNodeElement,val combiner: Boolean) {
    val first=NbtElectricalLoad("dcFirst")
    val second=NbtElectricalLoad("dcSecond")
    val output=NbtElectricalLoad("dcOutput")
    val negative=NbtElectricalLoad("dcReturn")
    val contacts=listOf(TransferContact(first,output),TransferContact(second,output))
    val fuses=List(2) { CutoutFuse() }
    var enabled=true
    var fault=""
    val volts get()=if(combiner)output.voltage-negative.voltage else output.voltage
    val amps get()=contacts.sumOf { it.current }
    val status get()=when { fault.isNotEmpty()->fault;!enabled->"DISABLED";combiner && fuses.any { it.blown }->"FUSE";else->"READY" }
    init {
        listOf(first,second,output,negative).forEach { it.serialResistance=.000001;owner.electricalLoadList.add(it) }
        owner.electricalComponentList.addAll(contacts)
        owner.electricalComponentList.add(mods.eln.sim.mna.component.Resistor(negative,null).apply { resistance=1e6 })
        owner.electricalProcessList.add(IProcess(::step));update()
    }
    fun update() { contacts.forEachIndexed { i,c->c.closed=enabled && fault.isEmpty() && (if(combiner)fuses[i].closed else i==0) } }
    fun step(dt: Double) {
        if(!dt.isFinite() || dt<=0)return
        if(combiner) {
            if(listOf(first,second,output).any { abs(it.voltage-negative.voltage)>1000 })fault="OVERVOLTAGE"
            contacts.forEachIndexed { i,c->fuses[i].step(dt,c.current,30.0) }
        } else if(abs(contacts[0].current)>400)fault="OVERLOAD"
        update()
    }
    fun read(t: CompoundTag) { enabled=!t.contains("dcEnabled") || t.getBoolean("dcEnabled");fault=t.getString("dcFault").takeIf { it=="OVERVOLTAGE" || it=="OVERLOAD" } ?: "";fuses.forEachIndexed { i,f->f.read(t,"pvFuse$i") };update() }
    fun write(t: CompoundTag) { t.putBoolean("dcEnabled",enabled);t.putString("dcFault",fault);fuses.forEachIndexed { i,f->f.write(t,"pvFuse$i") } }
}
