package mods.eln.transparentnode.offgrid

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class OffGridType(val title: String,val kwh: Int=0,val wall: Boolean=false) {
    BANK16(I18N.TR_NAME(I18N.Type.NONE,"16 kWh LiFePO4 Battery Cabinet"),16),
    BANK32(I18N.TR_NAME(I18N.Type.NONE,"32 kWh LiFePO4 Battery Cabinet"),32),
    BANK48(I18N.TR_NAME(I18N.Type.NONE,"48 kWh LiFePO4 Battery Cabinet"),48),
    BANK64(I18N.TR_NAME(I18N.Type.NONE,"64 kWh LiFePO4 Battery Cabinet"),64),
    WALL16(I18N.TR_NAME(I18N.Type.NONE,"16 kWh Wall-mounted LiFePO4 Battery"),16,true),
    WALL32(I18N.TR_NAME(I18N.Type.NONE,"32 kWh Wall-mounted LiFePO4 Battery"),32,true),
    WALL48(I18N.TR_NAME(I18N.Type.NONE,"48 kWh Wall-mounted LiFePO4 Battery"),48,true),
    WALL64(I18N.TR_NAME(I18N.Type.NONE,"64 kWh Wall-mounted LiFePO4 Battery"),64,true),
    INVERTER(I18N.TR_NAME(I18N.Type.NONE,"Off-grid Inverter Charger")),
    MPPT(I18N.TR_NAME(I18N.Type.NONE,"LiFePO4 MPPT Charge Controller")),
    AC_CHARGER(I18N.TR_NAME(I18N.Type.NONE,"AC LiFePO4 Battery Charger")),
    COMBINER(I18N.TR_NAME(I18N.Type.NONE,"Fused PV Combiner and Disconnect")),
    DISCONNECT(I18N.TR_NAME(I18N.Type.NONE,"400 A DC Battery Disconnect"));
    val bank get()=kwh>0
    val model get()=if(bank) { if(wall)"lifepo_wall" else "lifepo_cabinet" } else when(this) {
        INVERTER->"offgrid_inverter";MPPT->"offgrid_mppt";AC_CHARGER->"offgrid_charger";COMBINER->"pv_combiner";else->"battery_disconnect"
    }
}
object OffGridDevices {
    val STACK=I18N.TR_NAME(I18N.Type.NONE,"16 kWh LiFePO4 Cell Stack")
    fun registerItems() { Eln.sharedItem.addElement(7+(117 shl 6),GenericItemUsingDamageDescriptor(STACK,"upsindustrialbatterymodule").apply { creativeTab=Eln.creativeTabOresMaterials }) }
    fun registerNodes() { OffGridType.entries.forEach { Eln.transparentNodeItem.addDescriptor(it.ordinal+(128 shl 6),OffGridDescriptor(it)) } }
    fun recipes(shaped: (ItemStack,Array<out Any>)->Unit) {
        fun item(name: String)=Eln.findItemStack(name,1)
        shaped(item(STACK),arrayOf("IBI","BCB","IBI",'I',item("Iron Plate"),'B',item("UPS Industrial Battery Module"),'C',item("Copper Cable")))
        OffGridType.entries.forEach { t->
            if(t.bank) {
                val pattern=when(t.kwh) { 16->arrayOf(" I ","CSC","IMI");32->arrayOf("SIS","CMC","III");48->arrayOf("SIS","CMC","ISI");else->arrayOf("SIS","CMC","SIS") }
                shaped(item(t.title),arrayOf(*pattern,'I',if(t.wall)ItemStack(Items.CHAIN) else item("Iron Plate"),'S',item(STACK),'C',item("Copper Cable"),'M',item("Machine Block")))
            } else shaped(item(t.title),arrayOf("IPI","WCW","IMI",'I',item("Iron Plate"),'P',ItemStack(Items.COMPARATOR),'W',item("Copper Cable"),'C',when(t) { OffGridType.MPPT->item("MPPT Solar Controller");OffGridType.INVERTER->item("Hybrid Solar Inverter");OffGridType.COMBINER->item("30 A Pole Fuse");else->ItemStack(Items.REPEATER) },'M',item("Machine Block")))
        }
    }
}
