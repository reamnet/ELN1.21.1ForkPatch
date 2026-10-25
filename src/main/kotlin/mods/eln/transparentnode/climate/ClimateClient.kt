package mods.eln.transparentnode.climate

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.sound.LoopedSound
import mods.eln.node.transparent.*
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.util.Locale

fun climateStatus(status: String)=when(status) {
    "OFF" -> tr("Switched off")
    "PROTECTED" -> tr("Input or temperature protection")
    "SATISFIED" -> tr("Thermostat satisfied")
    "COOLING" -> tr("Cooling")
    "HEATING" -> tr("Heating")
    else -> tr("Waiting for 120 V supply")
}
class ClimateDescriptor(val type: ClimateType) : PowerCabinetDescriptor(type.title,ClimateElement::class.java,ClimateRender::class.java,type.cooling) {
    private val model=Eln.obj.getObj(type.model)
    init { if(type.tvStand)ghostGroup=mods.eln.ghost.GhostGroup().apply { addElement(0,0,1,mods.eln.ghost.GhostBlock.tCube) };if(type==ClimateType.PATIO)ghostGroup=mods.eln.ghost.GhostGroup().apply { addElement(0,1,0,mods.eln.ghost.GhostBlock.tClearance) } }
    fun draw(front: Direction,active: Boolean)=preserveMatrix {
        front.inverse.glRotateZnRef()
        if(type.tvStand) { GL11.glTranslated(.5,0.0,0.0);GL11.glScaled(2.0,1.0,1.0) }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            if(type==ClimateType.FARMHOUSE)GL11.glColor3f(.58f,.40f,.24f)
            else if(type==ClimateType.CORNER)GL11.glColor3f(.25f,.17f,.11f)
            else GL11.glColor3f(.80f,.82f,.83f)
            model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.23f,.26f,.28f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.07f,.08f,.09f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.48f,.29f,.14f);model.getPart("Wood")?.drawNoBind()
            if(active)UtilsClient.disableLight()
            GL11.glColor3f(if(active).95f else .06f,if(active).27f else .04f,.015f);model.getPart("Heat")?.drawNoBind()
            GL11.glColor3f(.08f,if(active).85f else .12f,if(type.cooling).9f else .18f);model.getPart("Display")?.drawNoBind()
            if(active)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix { if(this.type.tvStand) { GL11.glScaled(.65,.65,.65);GL11.glTranslated(-.5,0.0,0.0) };draw(Direction.ZP,false) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Rated supply: %1$ V / %2$ W",type.volts.toInt(),type.watts.toInt()))
        list.add(tr("Thermostat: 16 to 32 C; uses simulated room temperature"))
        if(type.cooling) {
            list.add(tr("Mount on an exterior wall; heat is exhausted behind the wall"))
            list.add(tr("Connect cables on the same wall face"))
        } else list.add(tr("Connect a floor cable; heats the surrounding simulated room"))
    }
    override fun addCollisionBoxesToList(par5AxisAlignedBB: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {
        if(wallMounted) { super.addCollisionBoxesToList(par5AxisAlignedBB,list,world,x,y,z);return }
        val front=(world?.getBlockEntity(BlockPos(x,y,z)) as? TransparentNodeEntity)?.let { it.elementRender?.front ?: (it.node as? TransparentNode)?.element?.front } ?: Direction.ZP
        val portable=type==ClimateType.PORTABLE
        val w=if(portable).27 else .48
        val offset=if(type.tvStand).5 else 0.0
        val width=if(type.tvStand)w*2 else w
        val points=listOf(Vec3(offset-width,-.5,-.31),Vec3(offset+width,if(type==ClimateType.PATIO)1.40 else if(portable).25 else .50,if(portable).04 else .33))
        val a=mods.eln.transparentnode.worklight.WorkLightOptics.world(front,points[0]);val b=mods.eln.transparentnode.worklight.WorkLightOptics.world(front,points[1])
        val box=AABB(kotlin.math.min(a.x,b.x)+x+.5,kotlin.math.min(a.y,b.y)+y+.5,kotlin.math.min(a.z,b.z)+z+.5,
            kotlin.math.max(a.x,b.x)+x+.5,kotlin.math.max(a.y,b.y)+y+.5,kotlin.math.max(a.z,b.z)+z+.5)
        if(par5AxisAlignedBB.intersects(box))list.add(box)
    }
}
class ClimateDisplay {
    var target=22.0;var enabled=true;var active=false;var temperature=20.0;var watts=0.0;var status="NO_POWER";var room=false;var indoor=20.0;var outdoor=20.0
    val connections=LRDUMask()
    fun read(stream: DataInputStream) {
        target=stream.readDouble();enabled=stream.readBoolean();active=stream.readBoolean();temperature=stream.readDouble()
        watts=stream.readDouble();status=stream.readUTF();room=stream.readBoolean();indoor=stream.readDouble();outdoor=stream.readDouble();connections.deserialize(stream)
    }
}
class ClimateRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val display=ClimateDisplay()
    val operatingSound=object : LoopedSound("eln:${(descriptor as ClimateDescriptor).type.sound}",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
        override fun getVolume()=if(display.enabled && display.active && display.watts>1)(descriptor as ClimateDescriptor).type.soundVolume else 0f
    }
    init { addLoopedSound(operatingSound) }
    private var cable: CableRenderType?=null
    override fun draw() {
        val d=transparentNodeDescriptor as ClimateDescriptor;val facing=front ?: return
        mods.eln.client.terrain.TerrainModels.body(this,listOf(facing,display.active)) { d.draw(facing,display.active) };cable=drawCable(d.connectionPlane(facing),Eln.instance.meduimVoltageCableDescriptor.render,display.connections,cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=ClimateGui(player,this)
}
class ClimateGui(player: Player,private val render: ClimateRender) : GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var target: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    private lateinit var enabled: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,176,240,8,158)
    override fun initGui() {
        super.initGui()
        target=newGuiTextField(8,112,65).apply { text=render.display.target.toString();setComment(0,tr("Thermostat: 16 to 32 C")) }
        apply=newGuiButton(78,108,48,tr("Set C"));enabled=newGuiButton(130,108,38,"")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) { apply -> target.text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 16.0..32.0 }?.let { render.clientSendDouble(80,it) };enabled -> render.clientSendId(81) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        val d=render.display;val type=(render.transparentNodeDescriptor as ClimateDescriptor).type
        enabled.displayString=if(d.enabled)tr("On") else tr("Off")
        apply.enabled=target.text.toDoubleOrNull()?.let { it.isFinite() && it in 16.0..32.0 }==true
        drawString(8,8,font.plainSubstrByWidth(Eln.findItemStack(type.title,1).hoverName.string,160))
        drawString(8,24,font.plainSubstrByWidth(climateStatus(d.status),160))
        drawString(8,38,tr("Air: %1$ C / target: %2$ C",fmt(d.temperature),fmt(d.target)))
        drawString(8,52,tr("Power: %1$ W",d.watts.toInt()))
        drawString(8,66,tr("Heat exchanger: %1$ C",fmt(d.indoor)))
        if(type.cooling)drawString(8,80,tr("Exhaust: %1$ C",fmt(d.outdoor)))
        drawString(8,94,font.plainSubstrByWidth(if(d.room)tr("Enclosed room detected") else tr("Open air; using outdoor temperature"),160))
    }
    private fun fmt(n: Double)=String.format(Locale.ROOT,"%.1f",n)
}
