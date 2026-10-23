package mods.eln.transparentnode.fan

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.*
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sound.LoopedSound
import mods.eln.transparentnode.cord.*
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.io.DataInputStream
import kotlin.math.roundToInt

class FanDescriptor(val type: FanType): TransparentNodeDescriptor(type.title,FanElement::class.java,FanRender::class.java) {
    private val model=Eln.obj.getObj(type.model)
    init {
        if(type.industrial)ghostGroup=GhostGroup().apply {
            for(y in 0..4)for(z in -2..2)if(y!=0 || z!=0)addElement(0,y,z,GhostBlock.tClearance)
        }
    }
    fun draw(front: Direction,angle: Float)=preserveMatrix {
        front.inverse.glRotateZnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            when(type) {
                FanType.CLASSIC -> GL11.glColor3f(.82f,.83f,.78f)
                FanType.SHOP -> GL11.glColor3f(.30f,.34f,.37f)
                FanType.COMPACT -> GL11.glColor3f(.17f,.23f,.29f)
                else -> GL11.glColor3f(.70f,.47f,.06f)
            }
            model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.12f,.14f,.15f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.55f,.58f,.60f);model.getPart("Trim")?.drawNoBind()
            preserveMatrix {
                GL11.glTranslated(0.0,type.centerY,0.0);GL11.glRotatef(angle,0f,0f,1f);GL11.glTranslated(0.0,-type.centerY,0.0)
                GL11.glColor3f(if(type.industrial).45f else .66f,.67f,.64f);model.getPart("Rotor")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
        Unit
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        if(this.type.industrial) { GL11.glScalef(.24f,.24f,.24f);GL11.glTranslated(0.0,-1.7,0.0) }
        draw(Direction.ZP,0f)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Rated supply: %1$ V / %2$ W",type.volts.toInt(),type.watts.toInt()))
        list.add(tr("Motor starts gradually; blades coast when switched off"))
        if(type.industrial)list.add(tr("Exposed blades: 5 hearts per second at full speed"))
        else list.add(tr("Floor cable or flexible appliance cord; no bulb needed"))
    }
    override fun addCollisionBoxesToList(box: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {
        val height=if(type==FanType.COMPACT).55 else .98
        val tile=world?.getBlockEntity(BlockPos(x,y,z)) as? TransparentNodeEntity
        val facing=tile?.elementRender?.front ?: (if(world?.isClientSide==false)tile?.node as? TransparentNode else null)?.element?.front ?: Direction.ZP
        val bound=if(facing==Direction.XN || facing==Direction.XP)AABB(.28,0.0,.08,.72,height,.92) else AABB(.08,0.0,.28,.92,height,.72)
        val moved=bound.move(x.toDouble(),y.toDouble(),z.toDouble())
        if(moved.intersects(box))list.add(moved)
    }
}
class FanRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    val type=(descriptor as FanDescriptor).type
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var enabled=true;var setting=1.0;var speed=0.0;var volts=0.0;var watts=0.0
    val visual=FanVisual()
    val connections=LRDUMask()
    private var cable: CableRenderType?=null
    val operatingSound=object: LoopedSound(if(type.industrial)"eln:appliance_industrial_fan" else "eln:appliance_box_fan",Coordinate(entity.blockPos.x,entity.blockPos.y,entity.blockPos.z,0)) {
        override fun getVolume()=if(visual.speed>.001f)(if(type.industrial).6f else .20f)*visual.speed else 0f
        override fun getPitch()=.45f+.55f*visual.speed
    }
    init { addLoopedSound(operatingSound) }
    override fun refresh(deltaT: Float) { visual.step(deltaT,speed,type.rpm);super.refresh(deltaT) }
    override fun draw() {
        cable=drawCable(Direction.YN,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("Rotor")) { (transparentNodeDescriptor as FanDescriptor).draw(front ?: Direction.ZP,visual.angle) }
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);enabled=input.readBoolean();setting=input.readDouble();speed=input.readDouble();volts=input.readDouble();watts=input.readDouble();connections.deserialize(input);cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=FanGui(player,this)
}
class FanGui(player: Player,private val render: FanRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private val title=Eln.findItemStack(render.type.title,1).hoverName.string
    private lateinit var enabled: GuiButtonEln
    private lateinit var setting: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,176,230,8,148)
    override fun initGui() {
        super.initGui();enabled=newGuiButton(126,24,42,"")
        setting=newGuiTextField(8,92,60).apply { text=(render.setting*100).toInt().toString() }
        apply=newGuiButton(74,88,92,tr("Set speed %"))
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        if(obj==enabled)render.clientSendId(80)
        if(obj==apply)setting.text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 25.0..100.0 }?.let { render.clientSendDouble(81,it/100) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);enabled.displayString=if(render.enabled)tr("On") else tr("Off")
        drawString(8,8,font.plainSubstrByWidth(title,160))
        drawString(8,28,tr("Speed: %1$%",(render.speed*100).roundToInt()))
        drawString(8,44,tr("%1$ V / %2$ W",render.volts.toInt(),render.watts.toInt()))
        drawString(8,60,font.plainSubstrByWidth(cordState(render.cord.state),160))
        drawString(8,116,if(render.type.industrial)tr("Keep clear of rotating blades") else tr("Motor speed: 25 to 100%"))
    }
}
