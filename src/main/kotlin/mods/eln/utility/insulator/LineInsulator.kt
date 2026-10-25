package mods.eln.utility.insulator

import mods.eln.Eln
import mods.eln.gridnode.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.utility.ServiceTerminals
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream

enum class InsulatorVoltage(val volts: Int,val length: Double,val radius: Double,val range: Int,val model: String,val lineName: String,val feedName: String) {
    LOW(800,.20,.065,40,"lineinsulator800",I18N.TR_NAME(I18N.Type.NONE,"800 V Line Insulator"),I18N.TR_NAME(I18N.Type.NONE,"800 V Cable Feed Insulator")),
    MEDIUM(3200,.252,.081,64,"lineinsulator3200",I18N.TR_NAME(I18N.Type.NONE,"3.2 kV Line Insulator"),I18N.TR_NAME(I18N.Type.NONE,"3.2 kV Cable Feed Insulator")),
    DISTRIBUTION(12500,.28,.09,128,"lineinsulator12500",I18N.TR_NAME(I18N.Type.NONE,"12.5 kV Line Insulator"),I18N.TR_NAME(I18N.Type.NONE,"12.5 kV Cable Feed Insulator")),
    SUBTRANSMISSION(69000,.80,.15,256,"lineinsulator69000",I18N.TR_NAME(I18N.Type.NONE,"69 kV Line Insulator"),I18N.TR_NAME(I18N.Type.NONE,"69 kV Cable Feed Insulator")),
    TRANSMISSION(230000,2.0,.375,256,"lineinsulator230000",I18N.TR_NAME(I18N.Type.NONE,"230 kV Line Insulator"),I18N.TR_NAME(I18N.Type.NONE,"230 kV Cable Feed Insulator"));
    fun title(feed: Boolean)=if(feed)feedName else lineName
}

class LineInsulatorElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): GridElement(node,descriptor,(descriptor as GridDescriptor).connectRange) {
    val insulator=descriptor as LineInsulatorDescriptor
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val bus=NbtElectricalLoad("insulatorBus").apply { serialResistance=.00001 }
    var terminal=32;private set
    init { electricalLoadList.add(bus);slowProcessList.add(NodePeriodicPublishProcess(node,1.0,.5)) }
    override fun getGridElectricalLoad(side: Direction)=bus
    override fun gridLoads(terminal: Int): List<ElectricalLoad> = if(terminal in 0..5 || terminal==this.terminal)listOf(bus) else emptyList()
    override fun selectedGridTerminal(side: Direction)=terminal
    override fun aimedGridTerminal(side: Direction,localHit: Vec3)=terminal
    override fun gridCircuitKey(terminal: Int)=if(terminal<6)"LEGACY" else if(terminal==32)"HV_SINGLE" else ServiceTerminals.key(terminal)
    override fun renderWireCount(terminal: Int)=1
    override fun renderedGridCablePoint(terminal: Int,wire: Int)=front.rotateFromXN(gridCablePoint(terminal,wire))
    override fun gridCablePoint(terminal: Int,wire: Int)=Vec3(-.5+insulator.voltage.length,0.0,0.0)
    override fun getElectricalLoad(side: Direction,lrdu: LRDU)=if(insulator.feed && side!=front.inverse)bus else null
    override fun getConnectionMask(side: Direction,lrdu: LRDU)=if(getElectricalLoad(side,lrdu)!=null)NodeBase.maskElectricalPower else 0
    override fun getThermalLoad(side: Direction,lrdu: LRDU)=null
    override fun onNeighborBlockChange() {
        super.onNeighborBlockChange()
        val world=DimensionIds.serverLevel(coordinate().dimension) ?: return
        val pos=coordinate().pos.relative(front.toFacing())
        if(world.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)!=null && !insulator.supported(world,coordinate().pos,front))selfDestroy()
    }
    fun selectCircuit(value: Int): Boolean {
        if(insulator.voltage!=InsulatorVoltage.LOW || value !in listOf(32,40,41,42) || gridLinkList.isNotEmpty())return false
        terminal=value;needPublish();return true
    }
    override fun hasGui()=insulator.voltage==InsulatorVoltage.LOW
    override fun newContainer(side: Direction,player: Player)=BasicContainer(player,inventory,emptyArray())
    override fun multiMeterString(side: Direction)=Utils.plotUIP(bus.voltage,bus.current)
    override fun thermoMeterString(side: Direction)=""
    override fun getWaila()=linkedMapOf(tr("Circuit") to circuitLabel(terminal),tr("Voltage") to Utils.plotVolt("",bus.voltage),tr("Current") to Utils.plotAmpere("",bus.current))
    private fun read(tag: CompoundTag) { terminal=tag.getInt("insulatorCircuit").takeIf { insulator.voltage==InsulatorVoltage.LOW && it in listOf(32,40,41,42) } ?: 32 }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);tag.putInt("insulatorCircuit",terminal) }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);read(tag) }
    override fun getItemStackNBT()=CompoundTag().apply { putInt("insulatorCircuit",terminal) }
    override fun readItemStackNBT(tag: CompoundTag?) { if(tag!=null)read(tag) }
    override fun networkSerialize(out: DataOutputStream) { super.networkSerialize(out);out.writeByte(terminal);out.writeDouble(bus.voltage);out.writeDouble(bus.current);out.writeBoolean(gridLinkList.isNotEmpty()) }
    override fun networkUnserialize(input: DataInputStream): Byte { val id=super.networkUnserialize(input);if(id.toInt()==80)selectCircuit(input.readInt());return id }
}
fun circuitLabel(terminal: Int)=when(terminal) { 40->tr("L1 120 V service");41->tr("L2 120 V service");42->tr("240 V service");else->tr("General single circuit") }
