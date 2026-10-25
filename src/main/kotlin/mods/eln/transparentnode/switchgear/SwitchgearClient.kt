package mods.eln.transparentnode.switchgear

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale

class SwitchgearDescriptor(val kind: SwitchgearKind): TransparentNodeDescriptor(kind.title,SwitchgearElement::class.java,SwitchgearRender::class.java) {
    private val model by lazy { Eln.obj.getObj(kind.model) }
    init { creativeTab=Eln.creativeTabPowerElectronics }
    fun draw(front: Direction=Direction.ZP,on: Boolean=false,fault: Boolean=false)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            part("Base",.14f,.16f,.18f);part("Case",.72f,.74f,.73f);part("Terminal",.66f,.48f,.20f)
            part("Heatsink",.22f,.24f,.26f);part("Coil",.58f,.27f,.12f);part("Control",.12f,.35f,.67f)
            preserveMatrix { if(on)GL11.glTranslated(0.0,-.025,0.0);part("Armature",.48f,.51f,.53f) }
            part("NO",.92f,.49f,.10f);part("NC",.60f,.29f,.68f)
            if(on || fault)UtilsClient.disableLight()
            part("Indicator",if(fault).95f else .08f,if(on && !fault).85f else .16f,.06f)
            if(on || fault)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw()
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("%1$ independent contacts; %2$ V / %3$ A per pole",kind.poles,kind.volts.toInt(),kind.amps.toInt()))
        list.add(tr("Left face: commons / line; right face: normally open / load"))
        if(kind.changeover)list.add(tr("Rear face: normally closed contacts; break-before-make switching"))
        list.add(tr("Contact numbers on each face: left 1, right 2, bottom 3, top 4"))
        list.add(tr("Front: left A1, right A2, top ELN control signal"))
        list.add(tr("Select ELN signal, 120 V coil or 240 V coil in the menu"))
        list.add(tr("Coil consumption: %1$ W at rated voltage",kind.coilWatts))
        if(kind.solidState)list.add(tr("Silent semiconductor switching; real conduction loss and overheat protection"))
        else list.add(tr("Mechanical switching click; independent electrically isolated poles"))
        list.add(tr("Overload or overheating opens all contacts; inspect the fault before resetting"))
    }
}
class SwitchgearRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val kind=(descriptor as SwitchgearDescriptor).kind
    var control=RelayControl.SIGNAL;var enabled=true;var on=false;var transitioning=false;var fault=false
    var temperature=20.0;var coilVolts=0.0;var coilWatts=0.0;var losses=0.0
    val volts=DoubleArray(kind.poles);val noAmps=DoubleArray(kind.poles);val ncAmps=DoubleArray(kind.poles)
    override fun draw()=mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("Armature","Indicator")) {
        (transparentNodeDescriptor as SwitchgearDescriptor).draw(front ?: Direction.ZP,on,fault)
    }
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);control=RelayControl.entries[input.readUnsignedByte().coerceIn(0,2)]
        enabled=input.readBoolean();on=input.readBoolean();transitioning=input.readBoolean();fault=input.readBoolean()
        temperature=input.readDouble();coilVolts=input.readDouble();coilWatts=input.readDouble();losses=input.readDouble()
        volts.indices.forEach { volts[it]=input.readDouble();noAmps[it]=input.readDouble();ncAmps[it]=input.readDouble() }
    }
    override fun newGuiDraw(side: Direction,player: Player)=SwitchgearGui(player,this)
}
class SwitchgearGui(player: Player,private val render: SwitchgearRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var control: GuiButtonEln;private lateinit var enable: GuiButtonEln;private lateinit var reset: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,280,310,8,228)
    override fun initGui() {
        super.initGui();control=newGuiButton(8,36,264,"");enable=newGuiButton(8,60,128,"").apply { setComment(0,tr("Isolates every contact; coil terminals remain live")) };reset=newGuiButton(144,60,128,tr("Reset trip"))
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) { control->render.clientSendInt(80,(render.control.ordinal+1)%3);enable->render.clientSendBoolean(81,!render.enabled);reset->render.clientSendId(82) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,font.plainSubstrByWidth(tr(render.kind.title),264))
        drawString(8,22,if(render.fault)tr("Tripped; inspect load and allow cooling") else if(!render.enabled)tr("Contacts isolated") else if(render.transitioning)tr("Contacts transferring") else if(render.on)tr("Energized") else tr("Released"))
        control.displayString=tr("Control: %1$",render.control.label());enable.displayString=if(render.enabled)tr("Isolate contacts") else tr("Restore contacts")
        reset.enabled=render.fault && render.temperature<80
        drawString(8,88,tr("Coil: %1$ / %2$",Utils.plotVolt("",render.coilVolts).trim(),Utils.plotPower("",render.coilWatts).trim()))
        drawString(8,102,tr("Loss: %1$; case: %2$ C",Utils.plotPower("",render.losses).trim(),String.format(Locale.ROOT,"%.1f",render.temperature)))
        for(i in render.volts.indices)drawString(8,120+i*15,font.plainSubstrByWidth(tr("%1$: %2$; NO %3$; NC %4$",i+1,Utils.plotVolt("",render.volts[i]).trim(),Utils.plotAmpere("",render.noAmps[i]).trim(),Utils.plotAmpere("",render.ncAmps[i]).trim()),264))
        drawString(8,186,tr("Front: A1 left / A2 right / signal top"))
        drawString(8,200,if(render.kind.changeover)tr("Left common; right NO; rear NC") else tr("Left line; right load (normally open)"))
    }
}
