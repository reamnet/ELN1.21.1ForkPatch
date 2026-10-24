package mods.eln.sixnode.lampsocket

import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.item.BrushDescriptor
import mods.eln.item.ConfigCopyToolDescriptor
import mods.eln.item.IConfigurable
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.NominalVoltage
import mods.eln.misc.Utils
import mods.eln.misc.Utils.getItemObject
import mods.eln.misc.Utils.isCreative
import mods.eln.misc.Utils.plotAmpere
import mods.eln.misc.Utils.plotPower
import mods.eln.misc.Utils.plotValue
import mods.eln.misc.Utils.plotVolt
import mods.eln.misc.Utils.serialiseItemStack
import mods.eln.node.AutoAcceptInventoryProxy
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementInventory
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.MonsterPopFreeProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sixnode.currentcable.CurrentCableDescriptor
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.pow
import mods.eln.misc.isNothing
import mods.eln.misc.writeToNBT

class LampSocketElement(sixNode: SixNode, side: Direction, sixNodeDescriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, sixNodeDescriptor), IConfigurable,mods.eln.transparentnode.cord.CordDevice {

    override val inventory = SixNodeElementInventory(2, 64, this, LampSocketContainer.REQUIRED_CABLE_LENGTH)

    // ElectricalCableDescriptor here covers utility cables
    private val inventoryProxy = AutoAcceptInventoryProxy(inventory)
        .acceptIfEmpty(LampSocketContainer.LAMP_SLOT_ID, LampDescriptor::class.java)
        .acceptIfEmpty(LampSocketContainer.CABLE_SLOT_ID, ElectricalCableDescriptor::class.java, CurrentCableDescriptor::class.java)

    val descriptor = sixNodeDescriptor as LampSocketDescriptor
    val installedBulbs get() = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID).count.coerceIn(0,descriptor.bulbCapacity)

    val electricalLoad = NbtElectricalLoad("electricalLoad")
    override val cord=mods.eln.transparentnode.cord.ApplianceCord(this,electricalLoad)
    override val cordVoltage get()=fanNominal
    override fun acceptsCord(rating: mods.eln.transparentnode.cord.CordRating)=mods.eln.sixnode.lampsocket.LampCordCompatibility.accepts(rating,cordVoltage)
    override val cordPortable get()=descriptor.fixture?.tableLamp==true
    val wirelessPower get()=poweredByLampSupply && !cord.plugged
    val hasPowerLead get()=cord.plugged || !inventory.getItem(LampSocketContainer.CABLE_SLOT_ID).isEmpty
    override fun cordChanged() { computeInventory() }
    val lampResistor = Resistor(electricalLoad, null)

    private val voltageWatchdog = VoltageStateWatchDog(electricalLoad)

    private val watchdogProcess = voltageWatchdog.setDestroys(WorldExplosion(this).cableExplosion())
    private val monsterPopProcess = MonsterPopFreeProcess(sixNode.coordinate,
        Eln.config.getIntOrElse("entities.mobSpawning.preventNearLampsRange", 9))
    private val lampSocketProcess = LampSocketProcess(this)

    var poweredByLampSupply = true
    var lampSupplyChannel = LampChannelMemory.DEFAULT_CHANNEL
    var activeLampSupplyConnection = false
    var projectionRotationAngle = 0.0
    val aim=descriptor.fixture?.newAim() ?: LightAim(0)
    val suspension=WarehouseSuspension()
    var fanEnabled=true
    val rotor=mods.eln.transparentnode.fan.FanDynamics()
    val fanPower get()=rotor.speed
    private val fanHazard=CeilingFanHazard(this)
    private var publishedFanSpeed=0.0
    val fanMotor=Resistor(electricalLoad,null).apply { resistance=1e19 }
    val fanNominal get()=(getItemObject(inventory.getItem(0)) as? LampDescriptor)?.lampData?.nominalU ?: 120.0
    fun updateFan(seconds: Double=.05) {
        if(descriptor.fixture?.fan!=true)return
        val ready=fanEnabled && hasPowerLead
        val level=if(ready)(kotlin.math.abs(electricalLoad.voltage)/fanNominal).coerceIn(0.0,1.0) else 0.0
        rotor.step(seconds,if(level>=.5)level else 0.0)
        if(descriptor.fixture==DecorativeLampStyle.FAN_INDUSTRIAL)fanHazard.tick()
        if(kotlin.math.abs(fanPower-publishedFanSpeed)>.009 || fanPower==0.0 && publishedFanSpeed!=0.0) {
            publishedFanSpeed=fanPower;needPublish()
        }
    }
    var motionMode = 0
    var motionEnergized = false
        private set
    private val motion = MotionLightControl()
    fun updateMotion(time: Double): Boolean {
        if(descriptor.fixture != DecorativeLampStyle.MOTION) return true
        val next = motion.update(this,time)
        if(next != motionEnergized) { motionEnergized=next; computeInventory() }
        return next
    }
    private var paintColor = LampSocketRender.DEFAULT_PAINT_COLOR
    private var grounded = true

    init {
        // We currently have 12V, 120V, and 240V bulbs, so lamp sockets should be able to handle voltages up to 240V nominal
        voltageWatchdog.setNominalVoltage(NominalVoltage.V240)

        slowProcessList.add(watchdogProcess)
        slowProcessList.add(monsterPopProcess)
        slowProcessList.add(lampSocketProcess)
    }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu {
        return LampSocketContainer(player, inventory, descriptor)
    }

    override fun hasGui(): Boolean {
        return true
    }

    override fun onBlockActivated(entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        if (Utils.isPlayerUsingWrench(entityPlayer)) {
            front = front.nextClockwise
            reconnect()
            needPublish()
            return true
        }

        var takeItem = false

        when (val equippedItemDescriptor = getItemObject(entityPlayer.mainHandItem)) {
            is BrushDescriptor -> {
                // Ignore brush use on non-paintable sockets (e.g. Streetlight)
                if (!descriptor.paintable) return false

                val brushColor = equippedItemDescriptor.getColor(entityPlayer.mainHandItem)

                if (brushColor != paintColor && equippedItemDescriptor.use(entityPlayer.mainHandItem, entityPlayer)) {
                    paintColor = brushColor
                    needPublish()
                }

                return true
            }

            is LampDescriptor -> {
                takeItem = equippedItemDescriptor.lampData.technology in descriptor.acceptedLampTypes
                if (takeItem && descriptor.fixture != null) {
                    val free = Eln.config.getBooleanOrElse("gameplay.qol.creativeNoConsumeInsertedItems",false) && entityPlayer is ServerPlayer && isCreative(entityPlayer)
                    val accepted = FixtureBulbs.insert(inventory,entityPlayer.mainHandItem,descriptor.bulbCapacity,free)
                    if (accepted) { inventory.setChanged(); inventoryChange(inventory) }
                    return accepted
                }
            }

            // ElectricalCableDescriptor here covers utility cables (utility cables are not signal cables)
            // Spool length check and trimming are handled in AutoAcceptInventoryProxy
            is ElectricalCableDescriptor -> {
                takeItem = !equippedItemDescriptor.signalWire
            }

            is CurrentCableDescriptor -> {
                takeItem = true
            }
        }

        return if (takeItem) {
            AutoAcceptInventoryProxy.creativeFreeInsert = Eln.config.getBooleanOrElse("gameplay.qol.creativeNoConsumeInsertedItems", false) && entityPlayer is ServerPlayer && isCreative(entityPlayer)
            inventoryProxy.take(entityPlayer.mainHandItem, this, notifyInventoryChange = true).also { accepted ->
                if (accepted && Eln.config.getBooleanOrElse("gameplay.qol.rememberLastLampSocketContents", false)) {
                    lastLampStack = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)?.copy()
                    lastCableStack = inventory.getItem(LampSocketContainer.CABLE_SLOT_ID)?.copy()
                }
                AutoAcceptInventoryProxy.creativeFreeInsert = false
            }
        } else false
    }

    /**
     * The if/else blocks here address the possible existence of legacy NBT tags and should remain in place.
     */
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)

        if (nbt.contains("front")) {
            val temp = nbt.getByte("front")
            front = LRDU.fromInt((temp.toInt() shr 0) and 0x3)
            grounded = (temp.toInt() and 4) != 0
            nbt.remove("front")
        } else {
            front = LRDU.fromInt(nbt.getInt("frontNew"))
            grounded = nbt.getBoolean("grounded")
        }

        if (nbt.contains("color")) {
            paintColor = if (descriptor.paintable) nbt.getByte("color").toInt() and 0xF else LampSocketRender.DEFAULT_PAINT_COLOR
            nbt.remove("color")
        } else {
            paintColor = if (descriptor.paintable) nbt.getInt("paintColor") else LampSocketRender.DEFAULT_PAINT_COLOR
        }

        poweredByLampSupply = descriptor.wirelessOnly || nbt.getBoolean("poweredByLampSupply")

        if (nbt.contains("channel")) {
            lampSupplyChannel = nbt.getString("channel")
            nbt.remove("channel")
        } else {
            lampSupplyChannel = nbt.getString("lampSupplyChannel")
        }

        motionMode = nbt.getInt("motionMode").coerceIn(0,3)
        projectionRotationAngle = nbt.getDouble("projectionRotationAngle")
        aim.read(nbt)
        if(descriptor.fixture?.hangingWarehouse==true)suspension.read(nbt)
        fanEnabled=!nbt.contains("fanEnabled") || nbt.getBoolean("fanEnabled");rotor.read(nbt)
        lampSocketProcess.stableLightProbability = nbt.getDouble("stableLightProbability")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putInt("frontNew", front.toInt())
        nbt.putBoolean("grounded", grounded)
        nbt.putInt("paintColor", paintColor)
        nbt.putBoolean("poweredByLampSupply", poweredByLampSupply)
        nbt.putString("lampSupplyChannel", lampSupplyChannel)
        nbt.putInt("motionMode",motionMode)
        nbt.putDouble("projectionRotationAngle", projectionRotationAngle)
        aim.write(nbt)
        if(descriptor.fixture?.hangingWarehouse==true)suspension.write(nbt)
        if(descriptor.fixture?.fan==true) { nbt.putBoolean("fanEnabled",fanEnabled);rotor.write(nbt) }
        nbt.putDouble("stableLightProbability", lampSocketProcess.stableLightProbability)
    }

    override fun initialize() {
        computeInventory()
        if (Eln.config.getBooleanOrElse("gameplay.qol.rememberLastLampSocketContents", false) && placingPlayerIsCreative) {
            lastLampStack?.let { inventory.setItem(LampSocketContainer.LAMP_SLOT_ID, it.copy()) }
            lastCableStack?.let { inventory.setItem(LampSocketContainer.CABLE_SLOT_ID, it.copy()) }
            computeInventory()
            placingPlayerIsCreative = false
        }
    }

    override fun connectJob() {
        if (cord.plugged || (!poweredByLampSupply && !descriptor.wirelessOnly)) {
            electricalLoadList.add(electricalLoad)
            electricalComponentList.add(lampResistor)
            if(descriptor.fixture?.fan==true)electricalComponentList.add(fanMotor)
        }
        super.connectJob()
    }

    override fun disconnectJob() {
        super.disconnectJob()
        electricalLoadList.remove(electricalLoad)
        electricalComponentList.remove(lampResistor)
        electricalComponentList.remove(fanMotor)
        electricalLoad.state = 0.0
    }

    override fun inventoryChange(inventory: Container?) {
        computeInventory()
        reconnect()
        needPublish()
    }

    fun computeInventory() {
        val lampStack = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)
        val cableStack = inventory.getItem(LampSocketContainer.CABLE_SLOT_ID)
        if(descriptor.fixture?.fan==true)fanMotor.resistance=if(fanEnabled && hasPowerLead)
            fanNominal*fanNominal/descriptor.fixture!!.fanWatts else 1e19

        // 1.11+: an empty slot is ItemStack.EMPTY, never null
        when {
            lampStack.isEmpty || (descriptor.fixture == DecorativeLampStyle.MOTION && !motionEnergized) -> lampResistor.highImpedance()
            else -> {
                (getItemObject(lampStack) as LampDescriptor).applyTo(lampResistor)
                lampResistor.resistance /= installedBulbs.coerceAtLeast(1)
            }
        }

        when {
            cord.plugged -> electricalLoad.serialResistance=.002
            cableStack.isEmpty -> electricalLoad.highImpedance()
            else -> {
                val cableDescriptor = Eln.sixNodeItem.getDescriptor(cableStack)

                // ElectricalCableDescriptor here covers utility cables
                if (cableDescriptor is ElectricalCableDescriptor || cableDescriptor is CurrentCableDescriptor) {
                    cableDescriptor.applyTo(electricalLoad)
                }
            }
        }
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? {
        if (cord.plugged || descriptor.wirelessOnly) return null
        if (!descriptor.renderSideCables && lrdu != front && lrdu != front.inverse()) return null
        return when {
            inventory.getItem(LampSocketContainer.CABLE_SLOT_ID).isNothing() -> null
            poweredByLampSupply -> null
            grounded -> electricalLoad
            else -> null
        }
    }

    override fun getThermalLoad(lrdu: LRDU, mask: Int): ThermalLoad? {
        return null
    }

    override fun getConnectionMask(lrdu: LRDU): Int {
        if (cord.plugged || descriptor.wirelessOnly) return 0
        if (!descriptor.renderSideCables && lrdu != front && lrdu != front.inverse()) return 0
        return when {
            inventory.getItem(LampSocketContainer.CABLE_SLOT_ID).isNothing() -> 0
            poweredByLampSupply -> 0
            grounded -> NodeBase.maskElectricalPower
            front == lrdu || front == lrdu.inverse() -> NodeBase.maskElectricalPower
            else -> 0
        }
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)

        try {
            stream.writeBoolean(poweredByLampSupply)
            stream.writeUTF(lampSupplyChannel)
            stream.writeBoolean(activeLampSupplyConnection)
            stream.writeDouble(projectionRotationAngle)
            stream.writeInt(paintColor)
            stream.writeBoolean(grounded)
            stream.writeInt(sixNode!!.lightValue)
            serialiseItemStack(stream, inventory.getItem(LampSocketContainer.LAMP_SLOT_ID))
            serialiseItemStack(stream, inventory.getItem(LampSocketContainer.CABLE_SLOT_ID))
            // The legacy item summary carries identity only, not the stack count.
            if (descriptor.fixture != null) stream.writeByte(installedBulbs)
            if (descriptor.fixture == DecorativeLampStyle.MOTION) stream.writeByte(motionMode)
            aim.write(stream)
            if(descriptor.fixture?.hangingWarehouse==true)stream.writeDouble(suspension.drop)
            if(descriptor.fixture?.fan==true) { stream.writeBoolean(fanEnabled);stream.writeDouble(fanPower) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream) = readSettings(stream, null)

    override fun networkUnserialize(stream: DataInputStream, player: ServerPlayer?) = readSettings(stream, player)

    private fun readSettings(stream: DataInputStream, player: ServerPlayer?) {
        try {
            val event=stream.readByte()
            when (event) {
                10.toByte() -> if(descriptor.fixture?.hangingWarehouse==true)suspension.set(stream.readDouble())
                9.toByte() -> if(descriptor.fixture?.fan==true) { fanEnabled=!fanEnabled;computeInventory() }
                in 32..63 -> aim.set((event.toInt()-32)/2,(event.toInt()-32)%2,stream.readDouble())
                in 5..8 -> {
                    aim.set((event.toInt()-5)/2,(event.toInt()-5)%2,stream.readDouble())
                }
                LampSocketGui.MOTION_MODE_EVENT -> if(descriptor.fixture == DecorativeLampStyle.MOTION) {
                    motionMode = (motionMode+1)%4
                }
                LampSocketGui.TOGGLE_GROUNDED_EVENT -> {
                    grounded = !grounded
                    reconnect()
                }
                LampSocketGui.TOGGLE_POWER_SOURCE_EVENT -> {
                    poweredByLampSupply = descriptor.wirelessOnly || !poweredByLampSupply
                    reconnect()
                }
                LampSocketGui.UPDATE_LAMP_SUPPLY_CHANNEL_EVENT -> {
                    lampSupplyChannel = stream.readUTF()
                    player?.let { LampChannelMemory.remember(it.persistentData, lampSupplyChannel) }
                }
                LampSocketGui.ADJUST_ROTATION_ANGLE_EVENT -> projectionRotationAngle = stream.readDouble()
            }
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun multiMeterString(): String {
        return plotVolt("U:", electricalLoad.voltage) + plotAmpere("I:", electricalLoad.current)
    }

    override fun thermoMeterString(): String {
        return ""
    }

    override fun getWaila(): Map<String, String> {
        val info: MutableMap<String, String> = LinkedHashMap()

        info[I18N.tr("Power Consumption")] = plotPower("", electricalLoad.voltage.pow(2) / lampResistor.resistance)

        val lampStack = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)
        if (!lampStack.isNothing()) info[I18N.tr("Bulb")] = lampStack.hoverName.string
        else info[I18N.tr("Bulb")] = I18N.tr("None")

        if (Eln.config.getBooleanOrElse("ui.waila.easyMode", false)) {
            info[I18N.tr("Voltage")] = plotVolt("", electricalLoad.voltage)

            if (!lampStack.isNothing()) {
                val lampDescriptor = getItemObject(lampStack) as LampDescriptor
                info[I18N.tr("Bulb Life Left")] = plotValue(lampDescriptor.getLifeInTag(lampStack)) + I18N.tr(" Hours")
            }

            if (poweredByLampSupply) info[I18N.tr("Channel")] = lampSupplyChannel
        }

        if (Eln.config.getBooleanOrElse("debug.logging.enabled", false)) {
            info[I18N.tr("Lamp Brightness")] = plotValue(sixNode!!.lightValue.toDouble())
        }

        return info
    }

    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        var publishChanges = false
        var inventoryChanged = false
        if(aim.heads>0) { aim.read(compound);publishChanges=true }
        if(descriptor.fixture?.hangingWarehouse==true) { suspension.read(compound);publishChanges=true }
        if(descriptor.fixture?.fan==true && compound.contains("fanEnabled")) {
            fanEnabled=compound.getBoolean("fanEnabled");inventoryChanged=true
        }

        if (compound.contains("poweredByLampSupply")) {
            poweredByLampSupply = descriptor.wirelessOnly || compound.getBoolean("poweredByLampSupply")
            publishChanges = true
        }

        if (compound.contains("lampSupplyChannel")) {
            lampSupplyChannel = compound.getString("lampSupplyChannel")
            LampChannelMemory.remember(invoker.persistentData, lampSupplyChannel)
            publishChanges = true
        }

        if (descriptor.enableProjectionRotation && compound.contains("projectionRotationAngle")) {
            projectionRotationAngle = compound.getDouble("projectionRotationAngle")
            publishChanges = true
        }

        if (compound.contains("grounded")) {
            grounded = compound.getBoolean("grounded")
            publishChanges = true
        }

        if (ConfigCopyToolDescriptor.readLampDescriptor(compound, "lamp", inventory, LampSocketContainer.LAMP_SLOT_ID, invoker, descriptor.acceptedLampTypes)) {
            inventoryChanged = true
        }

        if (ConfigCopyToolDescriptor.readCableType(compound, inventory, LampSocketContainer.CABLE_SLOT_ID, invoker, false)) {
            inventoryChanged = true
        }

        // Prevent duplicate calls of these functions
        if (inventoryChanged) {
            inventoryChange(inventory)
            if (Eln.config.getBooleanOrElse("gameplay.qol.rememberLastLampSocketContents", false)) {
                lastLampStack = inventory.getItem(LampSocketContainer.LAMP_SLOT_ID)?.copy()
                lastCableStack = inventory.getItem(LampSocketContainer.CABLE_SLOT_ID)?.copy()
            }
        }
        else if (publishChanges) needPublish()
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        aim.write(compound)
        if(descriptor.fixture?.hangingWarehouse==true)suspension.write(compound)
        if(descriptor.fixture?.fan==true)compound.putBoolean("fanEnabled",fanEnabled)
        compound.putBoolean("poweredByLampSupply", poweredByLampSupply)
        compound.putString("lampSupplyChannel", lampSupplyChannel)
        if (descriptor.enableProjectionRotation) compound.putDouble("projectionRotationAngle", projectionRotationAngle)
        compound.putBoolean("grounded", grounded)
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "lamp", inventory.getItem(LampSocketContainer.LAMP_SLOT_ID))
        ConfigCopyToolDescriptor.writeCableType(compound, inventory.getItem(LampSocketContainer.CABLE_SLOT_ID))
    }

    companion object {
        var lastLampStack: ItemStack? = null
        var lastCableStack: ItemStack? = null
        var placingPlayerIsCreative = false
    }
}
