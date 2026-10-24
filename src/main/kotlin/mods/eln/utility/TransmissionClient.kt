package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostBlock
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.*
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

class TransmissionDescriptor(val kind: TransmissionKind): GridDescriptor(kind.title,Eln.obj.getObj(kind.model),
    TransmissionElement::class.java,TransmissionRender::class.java,"textures/wire.png",Eln.instance.veryHighVoltageCableDescriptor,if(kind.inputVolts>100_000)192 else 128) {
    init {
        creativeTab=Eln.creativeTabPowerElectronics
        ghostGroup=GhostGroup().apply {
            if(kind.station) {
                val width=if(kind.inputVolts>100_000)3 else 2
                for(x in -3..3)for(y in 0..kind.height)for(z in -width..width)
                    if(x!=0 || y!=0 || z!=0)addElement(x,y,z,GhostBlock.tClearance)
            } else {
                for(y in 1..kind.height)addElement(0,y,0,GhostBlock.tClearance)
                val arm=kotlin.math.ceil(kind.spread).toInt()
                for(z in -arm..arm)for(y in kind.height-2..kind.height)
                    if(z!=0)addElement(0,y,z,GhostBlock.tClearance)
            }
        }
    }
    override fun rotationIsFixed()=true
    fun drawEquipment(angle: Float,primaryClosed: Boolean,secondaryClosed: Boolean)=preserveMatrix {
        GL11.glRotatef(angle,0f,1f,0f)
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.48f,.51f,.54f);obj.getPart("main")?.drawNoBind()
            GL11.glColor3f(.29f,.33f,.35f);obj.getPart("Transformer")?.drawNoBind()
            GL11.glColor3f(.68f,.74f,.72f);obj.getPart("Porcelain")?.drawNoBind()
            GL11.glColor3f(.19f,.22f,.24f);obj.getPart("Bus")?.drawNoBind()
            for((part,closed,x) in listOf(Triple("KnifeIn",primaryClosed,-1.5),Triple("KnifeOut",secondaryClosed,1.5)))preserveMatrix {
                GL11.glTranslated(x,kind.height-1.1,0.0)
                if(!closed)GL11.glRotated(if(x<0)-70.0 else 70.0,0.0,0.0,1.0)
                GL11.glTranslated(-x,-(kind.height-1.1),0.0)
                GL11.glColor3f(.69f,.58f,.35f);obj.getPart(part)?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        val scale=1.6/kind.height;GL11.glScaled(scale,scale,scale);GL11.glTranslated(0.0,-kind.height*.45,0.0)
        drawEquipment(-35f,true,true)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Three independent phase circuits; nominal input: %1$ kV",kind.inputVolts/1000))
        if(kind.transformer) {
            list.add(tr("Nominal output: %1$ kV; fixed ratio follows grid fluctuations",kind.outputVolts/1000))
            list.add(tr("Separate gang-operated primary and secondary disconnects"))
            list.add(tr("Design loading per phase: %1$ MW",kind.wattsPerPhase/1_000_000))
        }
        if(kind.riser)list.add(tr("Base cables: left A, right B, rear C; overhead links connect all three phases"))
        if(!kind.station)list.add(tr("Steel structure: %1$ blocks tall",kind.height))
        list.add(tr("Use bare overhead conductors or cables rated for the applied voltage"))
    }
}

class TransmissionRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val kind=(descriptor as TransmissionDescriptor).kind
    var primaryClosed=true;var secondaryClosed=true;var selected=ServiceTerminals.HV_LINE_BUNDLE
    val inputs=DoubleArray(3);val outputs=DoubleArray(3)
    override fun draw() {
        drawCables()
        mods.eln.client.terrain.TerrainModels.body(this,listOf(highVoltageRenderAngle,primaryClosed,secondaryClosed)) {
            (transparentNodeDescriptor as TransmissionDescriptor).drawEquipment(highVoltageRenderAngle,primaryClosed,secondaryClosed)
        }
    }
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream);primaryClosed=stream.readBoolean();secondaryClosed=stream.readBoolean();selected=stream.readInt()
        repeat(3) { inputs[it]=stream.readDouble() };repeat(3) { outputs[it]=stream.readDouble() }
    }
    override fun newGuiDraw(side: Direction,player: Player): Screen=TransmissionGui(player,this)
}
class TransmissionGui(player: Player,private val render: TransmissionRender): GuiContainerEln(mods.eln.misc.BasicContainer(player,render.inventory,arrayOf())) {
    private val kind=render.kind
    private var primary: GuiButtonEln?=null;private var secondary: GuiButtonEln?=null
    private lateinit var terminal: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,240,230,8,148)
    private val terminals get()=if(kind.station)listOf(52,53,32,33,34,36,37,38) else listOf(52,32,33,34)
    override fun initGui() {
        super.initGui();terminal=newGuiButton(8,27,224,"")
        if(kind.station)primary=newGuiButton(8,53,224,"")
        if(kind.transformer)secondary=newGuiButton(8,79,224,"")
    }
    override fun preDraw(f: Float,x: Int,y: Int) {
        super.preDraw(f,x,y)
        terminal.displayString=tr("Terminal: %1$",ServiceTerminals.label(render.selected))
        primary?.displayString=if(render.primaryClosed)tr("Primary disconnect: Closed") else tr("Primary disconnect: Open")
        secondary?.displayString=if(render.secondaryClosed)tr("Secondary disconnect: Closed") else tr("Secondary disconnect: Open")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) { primary->render.clientSendId(80);secondary->render.clientSendId(81)
            terminal->render.clientSendInt(82,terminals[(terminals.indexOf(render.selected)+1)%terminals.size]) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,font.plainSubstrByWidth(tr(kind.title),224))
        repeat(3) { i->drawString(8,108+i*12,tr("%1$: %2$ / %3$",listOf("A","B","C")[i],Utils.plotVolt("",render.inputs[i]).trim(),Utils.plotVolt("",render.outputs[i]).trim())) }
    }
}
