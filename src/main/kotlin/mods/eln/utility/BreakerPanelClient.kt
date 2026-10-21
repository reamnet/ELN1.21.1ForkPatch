package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gridnode.GridRender
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsupply.LampSupplyRange
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream

class BreakerPanelDescriptor: ServiceWallDescriptor(I18N.TR_NAME(I18N.Type.NONE,"Twelve Circuit American Breaker Panel"),"twelvecircuitpanel",BreakerPanelElement::class.java,BreakerPanelRender::class.java) {
    init { creativeTab=Eln.creativeTabPowerElectronics }
    fun drawPanel(front: Direction,states: List<PanelCircuitDisplay>?=null)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.64f,.65f,.63f);obj.getPart("Case")?.drawNoBind();obj.getPart("Door")?.drawNoBind()
            GL11.glColor3f(.11f,.13f,.14f);obj.getPart("Black")?.drawNoBind();obj.getPart("Main")?.drawNoBind()
            repeat(3) { GL11.glColor3f(.72f,.51f,.26f);obj.getPart("Terminal$it")?.drawNoBind();obj.getPart("TopTerminal$it")?.drawNoBind() }
            repeat(12) { i ->
                val s=states?.getOrNull(i)
                if(s==null || s.rating>0) {
                    GL11.glColor3f(.16f,.17f,.17f);obj.getPart("Breaker$i")?.drawNoBind()
                    preserveMatrix {
                        if(s?.closed!=true)GL11.glTranslated(if(i%2==0)-.035 else .035,0.0,0.0)
                        if(s?.tripped==true)GL11.glColor3f(.9f,.20f,.035f) else if(s?.closed==true)GL11.glColor3f(.30f,.47f,.34f) else GL11.glColor3f(.40f,.42f,.42f)
                        obj.getPart("Toggle$i")?.drawNoBind()
                    }
                }
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=drawPanel(Direction.ZP)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Twelve slots accept existing Electrical Breaker items"))
        list.add(tr("Left bank: L1 120 V; right bank: L2 120 V; each circuit can select 240 V"))
        list.add(tr("Every circuit has a wireless power channel and optional wireless switch control"))
        list.add(tr("Terminate overhead service at a weatherhead; feed this panel with placed wall cables"))
        list.add(tr("Top 240 V wall feeder supplies both 120 V banks; breakers tolerate brief motor inrush"))
        list.add(tr("Wall cables: left L1, right L2, top 240 V, bottom selected branch"))
    }
}
class BreakerPanelContainer(player: Player,inventory: Container): BasicContainer(player,inventory,Array(12) { i ->
    object: SlotWithSkinAndComment(inventory,i,8+(i%2)*152,34+(i/2)*20,ISlotSkin.SlotSkin.medium,arrayOf(tr("Circuit %1$: insert an Electrical Breaker",i+1))) {
        override fun mayPlace(stack: ItemStack)=BreakerPanelElement.breakerRating(stack)>0
        override fun getMaxStackSize()=1
    }
})
class PanelCircuitDisplay {
    var rating=0.0;var enabled=true;var tripped=false;var closed=false;var use240=false;var dusk=false;var power="";var control="";var aggregator=0;var volts=0.0;var amps=0.0
    fun read(s: DataInputStream) { rating=s.readDouble();enabled=s.readBoolean();tripped=s.readBoolean();closed=s.readBoolean();use240=s.readBoolean();dusk=s.readBoolean();power=s.readUTF();control=s.readUTF();aggregator=s.readInt();volts=s.readDouble();amps=s.readDouble() }
}
class BreakerPanelRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(12,1,this)
    var selected=0;var terminal=50;var range=32
    val circuits=List(12) { PanelCircuitDisplay() }
    private val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() { drawCables();(transparentNodeDescriptor as BreakerPanelDescriptor).drawPanel(front ?: Direction.XN,circuits);cable=drawCable(front!!.inverse,Eln.instance.stdCableRender200V,connections,cable) }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);selected=stream.readInt();terminal=stream.readInt();range=stream.readInt();circuits.forEach { it.read(stream) };connections.deserialize(stream);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=BreakerPanelGui(player,this)
}
class BreakerPanelGui(player: Player,private val r: BreakerPanelRender): GuiContainerEln(BreakerPanelContainer(player,r.inventory)) {
    private val select=ArrayList<GuiButtonEln>();private val toggle=ArrayList<GuiButtonEln>()
    private lateinit var power: GuiTextFieldEln;private lateinit var control: GuiTextFieldEln
    private lateinit var savePower: GuiButtonEln;private lateinit var saveControl: GuiButtonEln;private lateinit var voltage: GuiButtonEln;private lateinit var aggregator: GuiButtonEln;private lateinit var range: GuiButtonEln;private lateinit var terminal: GuiButtonEln
    private lateinit var dusk: GuiButtonEln
    private var editing=-1
    override fun newHelper()=GuiHelperContainer(this,312,386,8,304)
    override fun initGui() {
        super.initGui()
        repeat(12) { i ->
            val x=30+(i%2)*152;val y=32+(i/2)*20
            select.add(newGuiButton(x,y,65,""));toggle.add(newGuiButton(x+67,y,51,""))
        }
        power=newGuiTextField(8,178,212);savePower=newGuiButton(226,174,78,tr("Power link"))
        control=newGuiTextField(8,202,212);saveControl=newGuiButton(226,198,78,tr("Switch link"))
        voltage=newGuiButton(8,222,138,"");aggregator=newGuiButton(152,222,152,"")
        range=newGuiButton(8,246,138,"");terminal=newGuiButton(152,246,152,"")
        dusk=newGuiButton(8,270,296,"")
        power.setComment(0,tr("Power channel for this breaker; enter the same name on lamps and outlets"))
        control.setComment(0,tr("Optional wireless switch channel; blank means always enabled"))
        refresh()
    }
    private fun refresh() {
        val i=r.selected.coerceIn(0,11);val c=r.circuits[i]
        if(editing!=i) { editing=i;power.text=c.power;control.text=c.control }
        select.forEachIndexed { j,b -> val d=r.circuits[j];b.displayString=tr("%1$: %2$ A",j+1,d.rating.toInt());b.enabled=j!=i }
        toggle.forEachIndexed { j,b -> val d=r.circuits[j];b.enabled=d.rating>0;b.displayString=if(d.tripped)tr("Reset") else if(d.enabled)tr("On") else tr("Off") }
        voltage.displayString=tr("Circuit %1$: %2$ V",i+1,if(c.use240)240 else 120)
        aggregator.displayString=when(c.aggregator) { 0->tr("Switch: Any on");1->tr("Switch: All on");else->tr("Switch: Toggle") }
        range.displayString=tr("Wireless range: %1$",r.range)
        dusk.displayString=if(c.dusk)tr("Lighting control: Dusk / night") else tr("Lighting control: Manual / switch")
        terminal.enabled=false
        terminal.displayString=tr("Placed wall cables only")

    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        val i=r.selected.coerceIn(0,11)
        when(obj) {
            dusk->r.clientSendInt(88,i)
            savePower->r.clientSendString(81,"$i\n${power.text}")
            saveControl->r.clientSendString(82,"$i\n${control.text}")
            voltage->r.clientSendInt(84,i)
            aggregator->r.clientSendInt(85,i)
            range->r.clientSendInt(86,LampSupplyRange.next(r.range))
            terminal->{ val ts=listOf(50,40,41,42,64+i);r.clientSendInt(87,ts[(ts.indexOf(r.terminal)+1)%ts.size]) }
            else->{ select.indexOf(obj).takeIf { it>=0 }?.let { r.clientSendInt(80,it) };toggle.indexOf(obj).takeIf { it>=0 }?.let { r.clientSendInt(83,it) } }
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);refresh()
        drawString(8,8,tr("Twelve Circuit American Breaker Panel"));drawString(8,22,tr("Left bank: L1"));drawString(160,22,tr("Right bank: L2"))
        val c=r.circuits[r.selected.coerceIn(0,11)]
        drawString(8,158,tr("Circuit %1$: %2$",r.selected+1,Utils.plotUIP(c.volts,c.amps).trim()))
    }
}
