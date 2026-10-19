package mods.eln.transparentnode.worklight

import mods.eln.Eln
import mods.eln.fluid.FluidRegistry
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.item.lampitem.*
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.lampsocket.FixtureBulbs
import mods.eln.sixnode.lampsocket.LampChannelMemory
import mods.eln.sixnode.lampsocket.LightAim
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.transparentnode.FuelGeneratorDescriptor
import mods.eln.transparentnode.FuelGeneratorElement
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidUtil
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs

/** The established gasoline generator powers two ordinary parallel bulb loads; no converter is added. */
class WorkLightDescriptor(name: String) : FuelGeneratorDescriptor(name,Eln.obj.getObj("gasworklight"),
    Eln.instance.meduimVoltageCableDescriptor,2000.0,240.0,1200.0) {
    init {
        ElementClass=WorkLightElement::class.java;RenderClass=WorkLightRender::class.java
        ghostGroup=mods.eln.ghost.GhostGroup().apply { addElement(0,1,0);addElement(0,2,0) }
    }
    override fun renderItem(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        mods.eln.client.gl.GL11.glScalef(.48f,.48f,.48f)
        mods.eln.client.gl.GL11.glTranslatef(0f,-.65f,0f)
        drawFixture(0,2,"led")
    }
    fun drawFixture(light: Int,count: Int,technology: String,aim: LightAim=LightAim(2,-12.0))=WorkLightMesh.draw(obj!!,light,count,technology,aim)
    override fun addInformation(itemStack: ItemStack,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Wheeled gasoline generator with two replaceable floodlight bulbs"))
        list.add(tr("2000 W generator; output follows the installed bulb voltage (12, 120 or 240 V)"))
        list.add(tr("Right-click with a gasoline container to refuel; open the GUI for controls"))
        list.add(tr("Accepts every bulb type; cable power or wireless lamp supply also supported"))
    }
}

class WorkLightElement(node: TransparentNode,descriptor: TransparentNodeDescriptor) : FuelGeneratorElement(node,descriptor),mods.eln.item.IConfigurable {
    override val inventory=TransparentNodeElementInventory(1,2,this)
    private val wiredLamp=Resistor(positiveLoad,null)
    var wireless=false
    var channel=LampChannelMemory.DEFAULT_CHANNEL
    val aim=LightAim(2,-12.0)
    var brightness=0
        private set
    val bulb get()=Utils.getItemObject(inventory.getItem(0)) as? LampDescriptor
    override val operatingVoltage get()=bulb?.lampData?.nominalU ?: 120.0
    val bulbCount get()=inventory.getItem(0).count.coerceIn(0,2)
    private var lifeTime=0.0
    private var warmup=0.0
    private var supplyRefresh=0.0
    private var supply: LampSupplyElement.PowerSupplyChannelHandle?=null
    init {
        electricalComponentList.add(wiredLamp)
        slowProcessList.add(IProcess(::updateLight))
    }
    override fun initialize() { super.initialize();configureBulbs() }
    fun configureBulbs() {
        val d=bulb
        wiredLamp.resistance=if(!wireless && d!=null && bulbCount>0)d.lampData.resistance/bulbCount else 1e19
        val voltage=operatingVoltage
        powerSource.setMaximumVoltage(voltage)
        powerSource.setMaximumCurrent(descriptor.nominalPower*5/voltage)
    }
    override fun inventoryChange(inventory: Container?) { configureBulbs();needPublish() }
    override fun hasGui()=true
    override fun newContainer(side: Direction,player: Player)=WorkLightContainer(player,inventory)
    override fun onBlockActivated(player: Player,side: Direction,vx: Float,vy: Float,vz: Float): Boolean {
        val held=player.mainHandItem
        if(Utils.getItemObject(held) is LampDescriptor) {
            val inserted=FixtureBulbs.insert(inventory,held,2,player.isCreative)
            if(inserted)inventory.setChanged()
            return inserted
        }
        if(!held.isEmpty && FluidUtil.getFluidContained(held).isPresent) {
            val wasOn=on
            val result=super.onBlockActivated(player,side,vx,vy,vz)
            on=wasOn // Refuelling must not start the engine, including with an invalid/full container.
            return result
        }
        return false
    }
    private fun updateLight(time: Double) {
        if(wireless && on)on=false
        val d=bulb
        var volts=positiveLoad.voltage
        if(wireless) {
            supplyRefresh-=time
            if(supplyRefresh<=0) {
                supplyRefresh=1.0
                supply=LampSupplyElement.channelMap[channel]?.filterNotNull()
                    ?.filter { it.element.isInRange(coordinate()) }
                    ?.minByOrNull { it.element.sixNode!!.coordinate.trueDistanceTo(coordinate()) }
            }
            val h=supply
            volts=if(h!=null && h.element.getChannelState(h.id) && h.element.isInRange(coordinate())) {
                if(d!=null && bulbCount>0)h.element.addToRp(d.lampData.resistance/bulbCount)
                h.element.powerLoad.voltage
            } else 0.0
        }
        var light=0
        if(d!=null && bulbCount>0) {
            val data=d.lampData
            val factor=((abs(volts)/data.nominalU-data.technology.minimalUFactor)/(1-data.technology.minimalUFactor)).coerceIn(0.0,1.0)
            if(factor>0) {
                warmup+=time
                light=(factor*data.nominalLightValue).toInt()
                if(data.technology.lampType=="fluorescent" && warmup<data.technology.timeUntilStableInSeconds && Math.random()>warmup/data.technology.timeUntilStableInSeconds)light=0
            } else warmup=0.0
            lifeTime+=time
            if(lifeTime>=1.0) {
                lifeTime-=1.0
                if(d.decreaseLampLife(inventory.getItem(0),abs(volts))<=0) {
                    inventory.setItem(0,ItemStack.EMPTY);inventory.setChanged();light=0
                }
            }
        } else warmup=0.0
        if(light!=brightness) { brightness=light;needPublish() }
        if(brightness>0) {
            val world=coordinate().world() as? ServerLevel ?: return
            // Place beam endpoints only in already loaded air, so the cart never loads chunks.
            for(head in 0..1) {
                val pivot=WorkLightOptics.pivot(head)
                val local=aim.pointZ(WorkLightOptics.lens(head),pivot,head)
                val start=coordinate().toVec3().add(WorkLightOptics.world(front,local))
                val direction=WorkLightOptics.world(front,aim.rotateZ(net.minecraft.world.phys.Vec3(0.0,0.0,1.0),head))
                var last: BlockPos?=null
                for(step in 1..10) {
                    val at=BlockPos.containing(start.add(direction.scale(step.toDouble())))
                    if(world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)==null)break
                    if(world.getBlockState(at).isSolidRender(world,at))break
                    last=at
                }
                last?.let { LightBlockEntity.addLight(world,it.x,it.y,it.z,brightness,5) }
            }
        }
    }
    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id=super.networkUnserialize(stream)
        try { when(id.toInt()) {
            80 -> { if(on)on=false else if(!wireless && tankLevel>0) { on=true;voltageGracePeriod=FuelGeneratorDescriptor.VoltageStabilizationGracePeriod } }
            81 -> { wireless=!wireless;if(wireless)on=false;configureBulbs() }
            82 -> { channel=stream.readUTF().take(128);supply=null;supplyRefresh=0.0 }
            in 83..86 -> aim.set((id.toInt()-83)/2,(id.toInt()-83)%2,stream.readDouble())
        }
        } catch(_: java.io.IOException) {}
        needPublish();return id
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeDouble(tankLevel);stream.writeBoolean(wireless);stream.writeUTF(channel)
        stream.writeInt(brightness);stream.writeInt(bulbCount);stream.writeUTF(bulb?.lampData?.technology?.lampType ?: "led")
        stream.writeDouble(bulb?.lampData?.nominalU ?: 120.0)
        aim.write(stream)
    }
    private fun settings(nbt: CompoundTag) { wireless=nbt.getBoolean("wireless");channel=nbt.getString("channel").ifBlank { LampChannelMemory.DEFAULT_CHANNEL };aim.read(nbt) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt);settings(nbt) }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt);nbt.putBoolean("wireless",wireless);nbt.putString("channel",channel);aim.write(nbt) }
    override fun getItemStackNBT()=CompoundTag().apply {
        putDouble("tankLevel",tankLevel);putString("tankFluid",FluidRegistry.getFluidName(tankFluid) ?: "")
        putBoolean("wireless",wireless);putString("channel",channel)
        aim.write(this)
    }
    override fun readItemStackNBT(nbt: CompoundTag?) {
        if(nbt==null)return
        tankLevel=nbt.getDouble("tankLevel").coerceIn(0.0,1.0);tankFluid=FluidRegistry.getFluid(nbt.getString("tankFluid"))
        settings(nbt);on=false
    }
    override fun getWaila()=super.getWaila().toMutableMap().apply { put(tr("Bulbs"),tr("%1$/2",bulbCount));put(tr("Lighting power"),if(wireless)tr("Wireless lamp supply") else tr("Generator / cable")) }
    override fun readConfigTool(compound: CompoundTag,invoker: Player) { aim.read(compound);needPublish() }
    override fun writeConfigTool(compound: CompoundTag,invoker: Player) { aim.write(compound) }
}

object WorkLightOptics {
    fun pivot(head: Int)=net.minecraft.world.phys.Vec3(if(head==0)-.32 else .32,2.15,.12)
    fun lens(head: Int)=net.minecraft.world.phys.Vec3(if(head==0)-.32 else .32,2.15,.36)
    fun world(front: Direction,v: net.minecraft.world.phys.Vec3): net.minecraft.world.phys.Vec3 {
        val r=DoubleArray(3);front.right().applyTo(r,v.x);front.applyTo(r,v.z)
        return net.minecraft.world.phys.Vec3(r[0],v.y,r[2])
    }
}
