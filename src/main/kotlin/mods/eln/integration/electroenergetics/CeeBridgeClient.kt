package mods.eln.integration.electroenergetics

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.util.Locale

fun ceeStatus(value: String)=when(value) {
    "OFF"->tr("Disabled");"MISSING"->tr("Create Electro Energetics is not installed");"API"->tr("CEE configuration unavailable")
    "OVERVOLTAGE"->tr("Input overvoltage; check nominal setting");"FULL"->tr("Buffer full; waiting for CEE load")
    "NO_INPUT"->tr("Waiting for ELN input power");else->tr("Ready to supply CEE converter")
}
class CeeBridgeDescriptor: TransparentNodeDescriptor(I18N.TR_NAME(I18N.Type.NONE,"ELN to CEE Power Bridge"),CeeBridgeElement::class.java,CeeBridgeRender::class.java) {
    private val model by lazy { Eln.obj.getObj("ceepowerbridge") }
    init { creativeTab=Eln.creativeTabPowerElectronics }
    override fun mustHaveFloor()=false
    fun draw(front: Direction,ready: Boolean=false)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            p("Case",.34f,.45f,.43f);p("Trim",.21f,.24f,.26f);p("Input",.12f,.40f,.78f);p("Output",.94f,.47f,.08f);p("Screen",.035f,.09f,.10f)
            if(ready)UtilsClient.disableLight();p("LED",.07f,if(ready).85f else .16f,.09f);if(ready)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw(Direction.XN)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Converts real ELN input energy for Create Electro Energetics"))
        list.add(tr("Place a CEE Converter against the orange face, set it to source mode, and point its terminals outward"))
        list.add(tr("The CEE Converter pulls FE from its back; connect its native CEE terminals to the destination network"))
        list.add(tr("95 percent efficiency; uses CEE's configured watts per FE/tick"))
        list.add(tr("Set nominal ELN input voltage and power in the menu; input power follows voltage squared"))
        list.add(tr("Optional integration; does not require CEE to load Electrical Age"))
    }
}
class CeeBridgeRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var nominalVolts=3200.0;var maxWatts=100_000.0;var amps=40.0;var enabled=true
    var volts=0.0;var watts=0.0;var energy=0.0;var rate=0.0;var outputLimit=0.0;var status="MISSING"
    override fun draw()=mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("LED")) {
        (transparentNodeDescriptor as CeeBridgeDescriptor).draw(front ?: Direction.XN,status=="READY" || status=="FULL")
    }
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);nominalVolts=input.readDouble();maxWatts=input.readDouble();amps=input.readDouble();enabled=input.readBoolean()
        volts=input.readDouble();watts=input.readDouble();energy=input.readDouble();rate=input.readDouble();outputLimit=input.readDouble();status=input.readUTF()
    }
    override fun newGuiDraw(side: Direction,player: Player)=CeeBridgeGui(player,this)
}
class CeeBridgeGui(player: Player,private val render: CeeBridgeRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var volts: GuiTextFieldEln;private lateinit var watts: GuiTextFieldEln;private lateinit var amps: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln;private lateinit var enable: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,294,310,8,228)
    override fun initGui() {
        super.initGui();volts=newGuiTextField(8,64,84).apply { text=render.nominalVolts.toInt().toString() }
        watts=newGuiTextField(102,64,84).apply { text=render.maxWatts.toInt().toString() };amps=newGuiTextField(196,64,90).apply { text=render.amps.toString() }
        apply=newGuiButton(8,88,136,tr("Apply input settings"));enable=newGuiButton(152,88,134,"")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        if(obj===apply) {
            val v=volts.text.toDoubleOrNull() ?: return;val w=watts.text.toDoubleOrNull() ?: return;val a=amps.text.toDoubleOrNull() ?: return
            val bytes=java.io.ByteArrayOutputStream();val out=java.io.DataOutputStream(bytes)
            render.preparePacketForServer(out);out.writeByte(80);out.writeDouble(v);out.writeDouble(w);out.writeDouble(a)
            render.sendPacketToServer(bytes)
        } else if(obj===enable)render.clientSendBoolean(81,!render.enabled)
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);drawString(8,8,tr("ELN to CEE Power Bridge"));drawString(8,26,font.plainSubstrByWidth(ceeStatus(render.status),278))
        drawString(8,48,tr("Nominal V"));drawString(102,48,tr("Input watts"));drawString(196,48,tr("Nominal amps"))
        enable.displayString=if(render.enabled)tr("Enabled") else tr("Disabled")
        drawString(8,118,tr("Input: %1$ / %2$",Utils.plotVolt("",render.volts).trim(),Utils.plotPower("",render.watts).trim()))
        drawString(8,136,tr("Buffer: %1$ kJ",String.format(Locale.ROOT,"%.1f",render.energy/1000)))
        drawString(8,154,tr("CEE scale: %1$ W per FE/t",String.format(Locale.ROOT,"%.3f",render.rate)))
        drawString(8,172,tr("Output limit: %1$",Utils.plotPower("",render.outputLimit).trim()))
        drawString(8,192,tr("Orange face to CEE Converter's back"))
        drawString(8,208,tr("CEE Converter: source mode; wire its terminals"))
    }
}
