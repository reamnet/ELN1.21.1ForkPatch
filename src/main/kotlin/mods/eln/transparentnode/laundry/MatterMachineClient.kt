package mods.eln.transparentnode.laundry

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.terrain.TerrainModels
import mods.eln.ghost.GhostGroup
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

fun matterMachineStatus(status: String)=when(status) {
    "OFF"->tr("Switched off")
    "UNLOADED"->tr("Machine chunk unloaded")
    "INVALID"->tr("No supported recipe for this item")
    "BLOCKED"->tr("Output full")
    "OVERVOLTAGE"->tr("Overvoltage protection")
    "WATER"->tr("Waiting for water")
    "POWER"->tr("Waiting for electrical power")
    "WORKING"->tr("Processing")
    else->tr("Insert an item to begin")
}
class MatterMachineDescriptor(val kind: MatterMachineKind): TransparentNodeDescriptor(kind.title,
    MatterMachineElement::class.java,MatterMachineRender::class.java,
    if(kind==MatterMachineKind.WASHER)EntityMetaTag.Fluid else EntityMetaTag.Basic) {
    private val model=Eln.obj.getObj(kind.model)
    init {
        if(kind==MatterMachineKind.FUSION)ghostGroup=GhostGroup().apply {
            // Local footprint matches model: six along Z, two along X, two high.
            addRectangle(0,1,0,1,-2,3);removeElement(0,0,0)
        }
    }
    fun draw(front: Direction)=preserveMatrix {
        if(kind==MatterMachineKind.FUSION)front.glRotateXnRef() else front.inverse.glRotateZnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(if(kind==MatterMachineKind.DRYER).88f else .78f,if(kind==MatterMachineKind.DRYER).86f else .80f,if(kind==MatterMachineKind.DRYER).82f else .82f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.10f,.13f,.16f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.43f,.48f,.53f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.10f,.35f,.47f);model.getPart("Glass")?.drawNoBind()
            GL11.glColor3f(.14f,.48f,.62f);model.getPart("Display")?.drawNoBind()
            GL11.glColor3f(.80f,.45f,.12f);model.getPart("Coils")?.drawNoBind()
            if(kind==MatterMachineKind.DRYER) {
                GL11.glColor3f(.83f,.81f,.76f);model.getPart("Door")?.drawNoBind()
                GL11.glColor3f(.72f,.72f,.68f);model.getPart("Lint")?.drawNoBind()
                GL11.glColor3f(.17f,.18f,.18f);model.getPart("Vent")?.drawNoBind()
                GL11.glColor3f(.55f,.58f,.60f);model.getPart("Exhaust")?.drawNoBind()
            }
            Unit
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        if(kind==MatterMachineKind.FUSION) { GL11.glScalef(.18f,.18f,.18f);GL11.glTranslatef(-.5f,-.4f,-.5f) }
        draw(Direction.ZP)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        when(kind) {
            MatterMachineKind.WASHER -> {
                list.add(tr("120 V / 800 W; 60-second wash; 1 bucket of water per item"))
                list.add(tr("Repairs damaged equipment; wet equipment must be dried before use"))
                list.add(tr("Accepts standard fluid pipes on all sides; 10-bucket water tank"))
                list.add(tr("Use a 120 V appliance cord or fixed cables"))
            }
            MatterMachineKind.DRYER -> {
                list.add(tr("240 V / 6000 W; 120-second drying cycle"))
                list.add(tr("Restores usable tools and armor from wet equipment"))
                list.add(tr("Use a 240 V 30 A dryer cord or fixed cables"))
            }
            MatterMachineKind.FUSION -> {
                list.add(tr("6 x 2 x 2 fusion matter reactor; 3.2 kV input"))
                list.add(tr("100-500 kW for 10-30 minutes per block, based on material value"))
                list.add(tr("Insert a block sample; the sample is retained"))
                list.add(tr("Recipe values calculated internally using ProjectE's MIT-licensed mapper"))
                list.add(tr("Pauses on power loss; no production while unloaded"))
            }
        }
    }
}
class MatterMachineContainer(player: Player,inventory: Container,kind: MatterMachineKind): BasicContainer(player,inventory,
    Array<net.minecraft.world.inventory.Slot>(2) { index ->
        object: SlotWithSkinAndComment(inventory,index,if(index==0)35 else 139,101,ISlotSkin.SlotSkin.medium,
            arrayOf(if(index==1)tr("Output") else if(kind==MatterMachineKind.FUSION)tr("Block sample (retained)") else tr("Input"))) {
            override fun mayPlace(stack: ItemStack)=index==0 && when(kind) {
                MatterMachineKind.WASHER -> WetEquipment.washable(stack)
                MatterMachineKind.DRYER -> !WetEquipment.unpack(stack).isEmpty
                // Values are server-side; the server validates the template before drawing any power.
                MatterMachineKind.FUSION -> stack.item is BlockItem
            }
            override fun getMaxStackSize()=if(index==0)1 else super.getMaxStackSize()
        }
    })
class MatterMachineRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    val kind=(descriptor as MatterMachineDescriptor).kind
    override val inventory=TransparentNodeElementInventory(2,64,this)
    var enabled=true;var status="IDLE";var volts=0.0;var watts=0.0;var joules=0.0
    var required=1.0;var rated=kind.watts;var water=0
    val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() {
        cable=drawCable(Direction.YN,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        // The whole stationary body is baked. Network progress updates do not rebuild geometry.
        TerrainModels.body(this,front ?: Direction.ZP) { (transparentNodeDescriptor as MatterMachineDescriptor).draw(front ?: Direction.ZP) }
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream);enabled=stream.readBoolean();status=stream.readUTF()
        volts=stream.readDouble();watts=stream.readDouble();joules=stream.readDouble();required=stream.readDouble();rated=stream.readDouble()
        water=stream.readInt();connections.deserialize(stream);cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=MatterMachineGui(player,this)
}
class MatterMachineGui(player: Player,private val machine: MatterMachineRender): GuiContainerEln(MatterMachineContainer(player,machine.inventory,machine.kind)) {
    private lateinit var enabled: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,208,240,16,158)
    override fun initGui() { super.initGui();enabled=newGuiButton(154,23,46,"") }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);if(obj==enabled)machine.clientSendId(80) }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        enabled.displayString=if(machine.enabled)tr("On") else tr("Off")
        drawString(8,8,font.plainSubstrByWidth(tr(machine.kind.title),192))
        drawString(8,28,tr("Supply: %1$ V",machine.volts.toInt()))
        drawString(8,44,font.plainSubstrByWidth(matterMachineStatus(machine.status),192))
        drawString(8,58,tr("Power: %1$ / %2$ W",machine.watts.toInt(),machine.rated.toInt()))
        drawString(8,72,tr("Progress: %1$%",(100*machine.joules/machine.required.coerceAtLeast(1.0)).toInt()))
        drawString(25,89,if(machine.kind==MatterMachineKind.FUSION)tr("Sample") else tr("Input"));drawString(130,89,tr("Output"))
        when(machine.kind) {
            MatterMachineKind.WASHER -> drawString(8,127,tr("Water: %1$ / 10000 mB",machine.water))
            MatterMachineKind.DRYER -> drawString(8,127,tr("Wet equipment becomes usable"))
            MatterMachineKind.FUSION -> {
                drawString(8,127,tr("Energy: %1$ / %2$ MJ",(machine.joules/1e6).toInt(),(machine.required/1e6).toInt()))
                drawString(8,141,tr("Nominal cycle: %1$ minutes",String.format(java.util.Locale.ROOT,"%.1f",machine.required/machine.rated/60)))
            }
        }
    }
}
