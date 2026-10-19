package mods.eln.transparentnode.kitchen

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
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.io.DataInputStream

fun kitchenStatus(status: String)=when(status) {
    "HISSING" -> tr("Battery hissing - explosion imminent!")
    "HEATING_BATTERY" -> tr("Heating battery")
    "EXPLODING" -> tr("Battery rupturing!")
    "OFF" -> tr("Switched off")
    "OVERVOLTAGE" -> tr("Wrong supply voltage; input protected")
    "NO_POWER" -> tr("Waiting for rated supply voltage")
    "BLOCKED" -> tr("Needs water or output space")
    "COOLING" -> tr("Cooling the refrigerator")
    "WORKING" -> tr("Processing")
    else -> tr("Ready")
}
class KitchenDescriptor(val type: KitchenType) : TransparentNodeDescriptor(type.title,KitchenElement::class.java,KitchenRender::class.java) {
    private val model=Eln.obj.getObj(type.model)
    init { if(type==KitchenType.FRIDGE)ghostGroup=mods.eln.ghost.GhostGroup().apply { addElement(0,1,0) } }
    override fun mustHaveFloor()=false
    override fun checkCanPlace(coord: Coordinate?,front: Direction): String? {
        val error=super.checkCanPlace(coord,front)
        if(error!=null)return error
        val c=coord ?: return tr("Needs a supporting surface")
        val below=c.pos.below()
        val boxes=c.world().getBlockState(below).getCollisionShape(c.world(),below).toAabbs()
        return if(mods.eln.sixnode.lampsocket.LampMounting.surface(boxes,Direction.YN)==null)tr("Needs a supporting surface") else null
    }
    fun draw(front: Direction,active: Boolean,inset: Double=0.0)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glTranslated(0.0,-inset,0.0)
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.76f,.78f,.79f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.08f,.095f,.11f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.43f,.46f,.49f);model.getPart("Trim")?.drawNoBind()
            if(active)UtilsClient.disableLight()
            GL11.glColor3f(if(active).90f else .12f,if(active).23f else .13f,if(active).04f else .14f)
            model.getPart("Heat")?.drawNoBind()
            if(active)GL11.glColor3f(.10f,.80f,.20f) else GL11.glColor3f(.05f,.12f,.07f)
            model.getPart("Display")?.drawNoBind()
            if(active)UtilsClient.enableLight()
            model.getPart("Glass")?.let {
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false)
                GL11.glColor4f(.13f,.18f,.20f,.35f);it.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
        Unit
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        if(this.type==KitchenType.FRIDGE)GL11.glScalef(.7f,.7f,.7f)
        draw(Direction.ZP,false)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Rated supply: %1$ V / %2$ W",type.volts.toInt(),type.watts.toInt()))
        list.add(tr("Connect a floor cable to any horizontal side"))
        if(type==KitchenType.MICROWAVE)list.add(tr("Batteries hiss and explode when microwaved"))
        list.add(when(type) {
            KitchenType.MICROWAVE,KitchenType.STOVE,KitchenType.MINI_OVEN -> tr("Cooks food using the world's smelting recipes")
            KitchenType.TOASTER -> tr("Turns bread into edible toasted bread")
            KitchenType.COFFEE -> tr("Brews fresh coffee from cocoa beans and a water bottle")
            KitchenType.KETTLE -> tr("Heats a water bottle into drinkable hot water")
            KitchenType.BLENDER -> tr("Blends an apple, melon slice or sweet berries with a water bottle")
            KitchenType.RICE -> tr("Steams wheat or tagged rice with a water bottle; returns the empty bottle")
            KitchenType.FRIDGE -> tr("27 food-storage slots; freezes a water bucket into ice and returns the bucket")
        })
    }
}
class KitchenContainer(player: Player,inventory: Container,type: KitchenType) : BasicContainer(player,inventory,
    Array<net.minecraft.world.inventory.Slot>(if(type==KitchenType.FRIDGE)30 else 3) { index ->
        val x=if(index>=3)8+(index-3)%9*18 else when(index) { 0->17;1->63;else->135 }
        val y=if(index>=3)110+(index-3)/9*18 else 79
        object : SlotWithSkinAndComment(inventory,index,x,y,ISlotSkin.SlotSkin.medium,arrayOf(
            if(index==2)tr("Output") else if(index==1) {
                if(type in listOf(KitchenType.COFFEE,KitchenType.BLENDER,KitchenType.RICE))tr("Water bottle") else tr("Returned container")
            } else if(index>=3)tr("Food storage") else tr("Input"))) {
            override fun mayPlace(stack: ItemStack)=KitchenRecipe.allowed(type,index,stack)
        }
    })
class KitchenDisplay {
    var enabled=true;var active=false;var progress=0.0;var volts=0.0;var watts=0.0;var temperature=20.0;var status="IDLE"
    val connections=LRDUMask()
    fun read(stream: DataInputStream) {
        enabled=stream.readBoolean();active=stream.readBoolean();progress=stream.readDouble()
        volts=stream.readDouble();watts=stream.readDouble();temperature=stream.readDouble();status=stream.readUTF();connections.deserialize(stream)
    }
}
class KitchenRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    val type=(descriptor as KitchenDescriptor).type
    override val inventory=TransparentNodeElementInventory(if(type==KitchenType.FRIDGE)30 else 3,64,this)
    val display=KitchenDisplay()
    val operatingSound=object : LoopedSound("eln:${type.sound}",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
        override fun getVolume()=if(display.enabled && display.active && display.watts>1 && display.status!="EXPLODING").18f else 0f
    }
    val warningSound=if(type==KitchenType.MICROWAVE)object : LoopedSound("eln:appliance_battery_hiss",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
        override fun getVolume()=if(display.enabled && display.active && display.status=="HISSING").65f else 0f
    } else null
    init { addLoopedSound(operatingSound);warningSound?.let(::addLoopedSound) }
    private var cable: CableRenderType?=null
    override fun draw() {
        cable=drawCable(Direction.YN,Eln.instance.meduimVoltageCableDescriptor.render,display.connections,cable)
        val below=tileEntity.blockPos.below();val world=tileEntity.level!!
        val height=mods.eln.sixnode.lampsocket.LampMounting.surface(world.getBlockState(below).getCollisionShape(world,below).toAabbs(),Direction.YN) ?: 1.0
        (transparentNodeDescriptor as KitchenDescriptor).draw(front ?: Direction.ZP,display.active,(1-height).coerceIn(-.5,1.0))
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=KitchenGui(player,this)
}
class KitchenGui(player: Player,private val render: KitchenRender) : GuiContainerEln(KitchenContainer(player,render.inventory,render.type)) {
    private val title=Eln.findItemStack(render.type.title,1).hoverName.string
    private lateinit var enabled: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,176,if(render.type==KitchenType.FRIDGE)266 else 211,8,if(render.type==KitchenType.FRIDGE)184 else 129)
    override fun initGui() { super.initGui();enabled=newGuiButton(126,21,42,"") }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);if(obj==enabled)render.clientSendId(80) }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        val state=render.display
        enabled.displayString=if(state.enabled)tr("On") else tr("Off")
        drawString(8,8,font.plainSubstrByWidth(title,160))
        drawString(8,26,tr("%1$ V / %2$ W",render.type.volts.toInt(),render.type.watts.toInt()))
        drawString(8,42,font.plainSubstrByWidth(kitchenStatus(state.status),160))
        drawString(8,54,tr("Work: %1$% / %2$ W",(state.progress*100).toInt(),state.watts.toInt()))
        drawString(16,68,tr("Input"));drawString(132,68,tr("Output"))
        if(render.type==KitchenType.FRIDGE)drawString(8,99,tr("Temperature: %1$ C",String.format(java.util.Locale.ROOT,"%.1f",state.temperature)))
        else {
            if(render.cord.endpoint!=null)drawString(8,99,font.plainSubstrByWidth(mods.eln.transparentnode.cord.cordState(render.cord.state),160))
            drawString(8,110,font.plainSubstrByWidth(when(render.type) {
            KitchenType.KETTLE -> tr("Water bottle makes hot water")
            KitchenType.BLENDER -> tr("Fruit + water makes a smoothie")
            KitchenType.RICE -> tr("Wheat or rice + water bottle")
            KitchenType.COFFEE -> tr("Cocoa beans + water bottle")
            KitchenType.TOASTER -> tr("Bread makes toasted bread")
            else -> tr("Food recipes; no fuel needed")
        },160))
        }
    }
}
