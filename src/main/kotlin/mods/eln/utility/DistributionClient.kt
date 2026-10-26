package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostBlock
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridRender
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.transparentnode.utilitylight.StreetLightMount
import mods.eln.transparentnode.utilitylight.MainsStepDown
import mods.eln.transparentnode.utilitylight.isStepDownUnit
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale

class DistributionPoleDescriptor(val kind: DistributionKind): GridDescriptor(kind.title,Eln.obj.getObj("distributionpole"),
    DistributionPoleElement::class.java,DistributionPoleRender::class.java,"textures/wire.png",Eln.instance.veryHighVoltageCableDescriptor,64) {
    init {
        creativeTab=Eln.creativeTabPowerElectronics
        ghostGroup=GhostGroup().apply {
            for(y in 1..if(kind.underground) { if(kind.light)5 else 1 } else 7)addElement(0,y,0,if(kind.underground && y==1)GhostBlock.tCube else GhostBlock.tClearance)
            if(!kind.lvOnly && !kind.underground)for(y in 6..7)for(x in -1..1)for(z in -1..1)
                if(x!=0 || z!=0)addElement(x,y,z,GhostBlock.tClearance)
        }
    }
    private val twinFixture by lazy { Eln.obj.getObj("utilitytwinled") }
    override fun rotationIsFixed()=true
    fun drawEquipment(front: Direction,display: DistributionDisplay?=null,hvAngle: Float=mods.eln.gridnode.PoleSpanAlignment.frontAngle(front),lvAngle: Float=mods.eln.gridnode.PoleSpanAlignment.frontAngle(front),serviceReach: Double=mods.eln.gridnode.ServiceCableMount.MIN_REACH)=preserveMatrix {
        if(kind.underground) { UndergroundCabinetVisual.draw(kind,front,display);return@preserveMatrix }
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);obj.getPart(name)?.drawNoBind() }
            part("main",.39f,.27f,.14f)
            val base=mods.eln.gridnode.PoleSpanAlignment.frontAngle(front)
            preserveMatrix {
            GL11.glRotatef(hvAngle-base,0f,1f,0f)
            if(!kind.lvOnly) {
                part(if(kind.phases==1)"SingleArm" else "Crossarm",.36f,.25f,.13f)
                if(kind.phases==3)for(i in listOf(0,2))part("Pin$i",.57f,.66f,.68f)
                part("TopPin",.57f,.66f,.68f)
            }
            if(kind.transformer && display?.installed!=false) {
                PoleTransformerVisual.draw(display?.rating ?: 10_000)
                PoleLeadVisual.draw(net.minecraft.world.phys.Vec3(-.68,5.85,0.0),PoleTransformerShape.of(display?.rating ?: 10_000).bushing)
                val phase=if(kind.phases==1)1 else display?.phase ?: 0
                PoleLeadVisual.draw(net.minecraft.world.phys.Vec3(0.0,if(phase==1)7.15 else 6.63,if(kind.phases==1)0.0 else (phase-1)*.95),net.minecraft.world.phys.Vec3(-.68,6.40,0.0))
            }
            if(kind.transformer || kind.cutout || kind.regulator || kind.singlePhaseTap)repeat(kind.fuseCount) { i ->
                preserveMatrix {
                if(kind.transformer && kind.singlePhaseTap && i==1)GL11.glTranslated(0.0,0.0,.65)
                val visual=if(kind.phases==1 || kind.transformer || kind.singlePhaseTap)1 else i
                if(kind.cutout || kind.regulator)PoleLeadVisual.draw(net.minecraft.world.phys.Vec3(0.0,if(visual==1)7.15 else 6.63,(visual-1)*.95),net.minecraft.world.phys.Vec3(-.68,6.40,(visual-1)*.95))
                PoleCutoutVisual.draw(visual,display?.fuseClosed?.getOrNull(i)!=false,display?.fuseBlown?.getOrNull(i)==true)
                }
            }
            if(kind.singlePhaseTap)PoleLeadVisual.draw(net.minecraft.world.phys.Vec3(0.0,if(display?.phase==1)7.15 else 6.63,((display?.phase ?: 0)-1)*.95),net.minecraft.world.phys.Vec3(-.68,6.4,if(kind.transformer).65 else 0.0))
            if(kind==DistributionKind.FEEDER_THREE)repeat(3) { part("Riser$it",.28f,.31f,.32f);part("RiserJumper$it",.13f,.15f,.16f) }
            if(kind.regulator)repeat(kind.phases) { val i=if(kind.phases==1)1 else it;part("Regulator$i",.64f,.66f,.63f);part("RegulatorPin$i",.44f,.48f,.38f);part("RegulatorLoadPin$i",.44f,.48f,.38f);part("RegulatorFeed$i",.13f,.15f,.16f) }
            if(kind.capacitor) { part("CapacitorRack",.35f,.38f,.40f);repeat(3) { part("Capacitor$it",.76f,.77f,.72f);part("CapacitorPin$it",.4f,.45f,.35f);part("CapacitorFeed$it",.13f,.15f,.16f) } }
            if(kind.recloser) { part("Recloser",.57f,.61f,.58f);part("Control",.60f,.63f,.61f) }
            if(kind.recloser || kind==DistributionKind.AIR_BREAK_THREE) {
                repeat(3) { i ->
                    part("SwitchInsulator$i",.50f,.60f,.59f);part("SwitchFeed$i",.13f,.15f,.16f)
                    preserveMatrix {
                        GL11.glTranslated(-.35,6.51,(i-1)*.95)
                        if(if(kind.recloser)display?.closed==false else display?.enabled==false)GL11.glRotated(-70.0,0.0,0.0,1.0)
                        GL11.glTranslated(.35,-6.51,-(i-1)*.95);part("Knife$i",.73f,.61f,.38f)
                    }
                }
                part("Handle",.37f,.39f,.38f)
            }
            }
            preserveMatrix {
                GL11.glRotatef(lvAngle-base,0f,1f,0f)
                PoleSecondaryVisual.suspension(serviceReach)
            }
            if(kind.transformer && display?.installed!=false)PoleTransformerVisual.secondaryLeads(display?.rating ?: 10_000,hvAngle-base,lvAngle-base,serviceReach)
            if(kind.light)preserveMatrix {
                fun lampPart(name: String,r: Float,g: Float,b: Float) {
                    if(kind.twinLight)preserveMatrix {
                        GL11.glTranslated(0.0,2.9,0.0)
                        GL11.glColor3f(r,g,b);twinFixture.getPart(name.removePrefix("Lamp"))?.drawNoBind()
                    } else part(name,r,g,b)
                }
                GL11.glTranslated(0.0,StreetLightMount.offset(display?.heightStep ?: 0),0.0)
                GL11.glRotated((display?.yaw ?: 0).toDouble(),0.0,1.0,0.0)
                lampPart("LampBracket",.48f,.50f,.51f)
                val p=StreetLightMount.pivot
                GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated((display?.pitch ?: 0).toDouble(),0.0,0.0,1.0);GL11.glTranslated(-p.x,-p.y,-p.z)
                lampPart("LampHousing",.26f,.29f,.31f)
                if((display?.light ?: 0)>0) { UtilsClient.disableLight();lampPart("LampLens",1f,.94f,.77f);UtilsClient.enableLight() }
                else lampPart("LampLens",.14f,.17f,.19f)
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        GL11.glScaled(.20,.20,.20);GL11.glTranslated(0.0,-2.8,0.0);drawEquipment(Direction.XN)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        if(kind.underground) {
            list.add(tr("Underground LV service: bottom left L1, right L2, other bottom terminals 240 V"))
            if(!kind.pedestal)list.add(tr("HV: left A, right B, rear C; lower cable is line, upper cable is fused load"))
            if(kind.light)list.add(tr("Street light requires an upstream 120 V secondary supply"))
            list.add(tr("Use physical Pole Fuse Links; opening or blowing a fuse isolates its load"))
            return
        }
        if(kind.lvOnly) {
            list.add(tr("120/240 V secondary circuits only; no high-voltage terminals or transformer"))
            list.add(tr("Aim at the LV rack to join a service bundle; bottom cables use L1, L2 or 240 V"))
            if(kind.twinLight)list.add(tr("Select 120 V or 240 V lamp supply in the menu; feed that circuit upstream"))
        } else {
        list.add(tr("Aim at HV tie points or the LV rack; three-phase poles link all three phases"))
        list.add(tr("A, B and C are independent electrical circuits"))
        list.add(tr("Hold Shift to use the individual terminal selected in the menu"))
        list.add(tr("Single-phase branches use the A, B or C insulator you aim at"))
        }
        list.add(tr("Secondary rack carries L1 120 V, L2 120 V and 240 V service circuits"))
        if(kind.transformer) { list.add(tr("Fixed ratio: 12.47 kV nominal primary; output follows grid voltage"));list.add(tr("Insert a physical 10, 25, 50, 75 or 100 kW Pole Transformer"));list.add(tr("Transformer phase is selectable; a blown fuse needs a Pole Fuse Link")) }
        if(kind.singlePhaseTap) { list.add(tr("A, B and C pass through; a physical fuse protects only the single-phase tap"));list.add(tr("Select tap phase A, B or C; connect the branch to the fused load terminal")) }
        if(kind.cutout || kind.regulator)list.add(tr("Separate line and load terminals; replace blown links with a Pole Fuse Link"))
        if(kind.recloser)list.add(tr("Any phase fault opens all three; timed retries stop at lockout"))
        if(kind.regulator)list.add(tr("Automatic regulation within 10 percent of line voltage; passive, power-conserving taps"))
        if(kind.capacitor)list.add(tr("Three independent charge storage branches smooth DC-equivalent voltage changes"))
        if(kind.light && !kind.transformer)list.add(tr("Street light draws from the LV rack only; feed it from an upstream transformer"))
        if(kind==DistributionKind.FEEDER_THREE)list.add(tr("Base: left A, right B, rear C; three separate underground cable inputs"))
    }
}

class DistributionDisplay {
    var terminal=32;var phase=0;var rating=10_000;var voltage=120;var enabled=true;var installed=false
    var fuseClosed=listOf<Boolean>();var fuseBlown=listOf<Boolean>();var fuseRating=100.0
    var locked=false;var closed=true;var failures=0;var remaining=0.0;var pickup=100.0;var delay=3.0;var maxFailures=3
    var target=12_470.0;var automatic=true;var ratios=listOf<Double>();var light=0;var lampOn=true;var dusk=false
    var yaw=0;var pitch=0;var heightStep=0
    var input=0.0;var output=0.0;var watts=0.0
    fun write(s: DataOutputStream) {
        s.writeInt(terminal);s.writeInt(phase);s.writeInt(rating);s.writeInt(voltage);s.writeBoolean(enabled);s.writeBoolean(installed)
        s.writeByte(fuseClosed.size);fuseClosed.indices.forEach { s.writeBoolean(fuseClosed[it]);s.writeBoolean(fuseBlown[it]) };s.writeDouble(fuseRating)
        s.writeBoolean(locked);s.writeBoolean(closed);s.writeInt(failures);s.writeDouble(remaining);s.writeDouble(pickup);s.writeDouble(delay);s.writeInt(maxFailures)
        s.writeDouble(target);s.writeBoolean(automatic);s.writeByte(ratios.size);ratios.forEach(s::writeDouble);s.writeByte(light);s.writeBoolean(lampOn);s.writeBoolean(dusk)
        s.writeShort(yaw);s.writeByte(pitch);s.writeByte(heightStep)
        s.writeDouble(input);s.writeDouble(output);s.writeDouble(watts)
    }
    fun read(s: DataInputStream) {
        terminal=s.readInt();phase=s.readInt();rating=s.readInt();voltage=s.readInt();enabled=s.readBoolean();installed=s.readBoolean()
        val n=s.readUnsignedByte().coerceAtMost(3);val a=ArrayList<Boolean>();val b=ArrayList<Boolean>();repeat(n) { a.add(s.readBoolean());b.add(s.readBoolean()) };fuseClosed=a;fuseBlown=b;fuseRating=s.readDouble()
        locked=s.readBoolean();closed=s.readBoolean();failures=s.readInt();remaining=s.readDouble();pickup=s.readDouble();delay=s.readDouble();maxFailures=s.readInt()
        target=s.readDouble();automatic=s.readBoolean();ratios=List(s.readUnsignedByte().coerceAtMost(3)) { s.readDouble() };light=s.readUnsignedByte();lampOn=s.readBoolean();dusk=s.readBoolean()
        yaw=s.readUnsignedShort();pitch=s.readByte().toInt();heightStep=s.readByte().toInt()
        input=s.readDouble();output=s.readDouble();watts=s.readDouble()
    }
}
class DistributionContainer(player: Player,inventory: Container,transformer: Boolean,recoveryOnly: Boolean=false): BasicContainer(player,inventory,
    if(recoveryOnly)Array(2) { i->object: SlotWithSkinAndComment(inventory,i,8+i*18,31,ISlotSkin.SlotSkin.medium,arrayOf(tr("Recovered hardware; take out only"))) {
        override fun mayPlace(stack: ItemStack)=false
        override fun getMaxStackSize()=1
    } } else if(transformer)arrayOf(object: SlotWithSkinAndComment(inventory,0,9,31,ISlotSkin.SlotSkin.medium,arrayOf(tr("Pole Transformer or Mains Step-down Unit"))) {
        override fun mayPlace(stack: ItemStack)=isStepDownUnit(stack)
        override fun getMaxStackSize()=1
    }) else emptyArray<Slot>())

class DistributionPoleRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(if((descriptor as DistributionPoleDescriptor).kind.twinLight)2 else if(descriptor.kind.transformer)1 else 0,1,this)
    val display=DistributionDisplay()
    var arresterFitted=false;private set
    var arresterRemaining=0.0;private set
    private val connections=LRDUMask()
    private var cable: mods.eln.cable.CableRenderType?=null
    override fun draw() {
        drawCables()
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front,highVoltageRenderAngle,lowVoltageRenderAngle,serviceMountReach,display.rating,
            display.installed,display.phase,display.fuseClosed,display.fuseBlown,display.closed,display.enabled,
            display.heightStep,display.yaw,display.pitch,display.light>0,arresterFitted,arresterRemaining<=0)) {
            if(arresterFitted)mods.eln.transparentnode.industrial.PoleArresterVisual.draw(highVoltageRenderAngle,(transparentNodeDescriptor as DistributionPoleDescriptor).kind.phases,arresterRemaining<=0)
            (transparentNodeDescriptor as DistributionPoleDescriptor).drawEquipment(front ?: Direction.XN,display,highVoltageRenderAngle,lowVoltageRenderAngle,serviceMountReach.toDouble())
        }
        cable=drawCable(Direction.YN,if((transparentNodeDescriptor as DistributionPoleDescriptor).kind==DistributionKind.FEEDER_THREE)Eln.instance.stdCableRender3200V else Eln.instance.stdCableRender200V,connections,cable)
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream);connections.deserialize(stream);arresterFitted=stream.readBoolean();arresterRemaining=stream.readDouble();cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=DistributionGui(player,this)
}
class DistributionGui(player: Player,private val render: DistributionPoleRender): GuiContainerEln(DistributionContainer(player,render.inventory,(render.transparentNodeDescriptor as DistributionPoleDescriptor).kind.transformer,(render.transparentNodeDescriptor as DistributionPoleDescriptor).kind.twinLight)) {
    private val kind=(render.transparentNodeDescriptor as DistributionPoleDescriptor).kind
    private val d get()=render.display
    private lateinit var terminal: GuiButtonEln
    private var phase: GuiButtonEln?=null;private var rating: GuiButtonEln?=null;private var voltage: GuiButtonEln?=null;private var operate: GuiButtonEln?=null
    private var current: GuiTextFieldEln?=null;private var applyCurrent: GuiButtonEln?=null;private var retry: GuiTextFieldEln?=null;private var applyRetry: GuiButtonEln?=null;private var max: GuiButtonEln?=null;private var reset: GuiButtonEln?=null
    private var target: GuiTextFieldEln?=null;private var applyTarget: GuiButtonEln?=null;private var auto: GuiButtonEln?=null;private var lamp: GuiButtonEln?=null;private var dusk: GuiButtonEln?=null
    private var left: GuiButtonEln?=null;private var right: GuiButtonEln?=null;private var up: GuiButtonEln?=null;private var down: GuiButtonEln?=null;private var tiltUp: GuiButtonEln?=null;private var tiltDown: GuiButtonEln?=null
    private val fuses=ArrayList<GuiButtonEln>()
    override fun newHelper()=GuiHelperContainer(this,240,if(kind.light)382 else 310,8,if(kind.light)300 else 228)
    private fun terminals()=buildList { if(!kind.lvOnly) { repeat(kind.phases) { add(32+it) };if(kind.protectedLine)repeat(kind.outputPhases) { add(36+it) } };add(50);addAll(40..42) }
    override fun initGui() {
        super.initGui();fuses.clear();terminal=newGuiButton(8,58,224,"").apply { enabled=!kind.underground }
        if(kind.transformer) {
            phase=newGuiButton(8,82,106,"");rating=newGuiButton(118,82,114,"");voltage=newGuiButton(8,106,106,"").apply { setComment(0,tr("Nominal output at 12.47 kV; follows grid voltage proportionally")) }
            operate=newGuiButton(118,106,114,"")
        }
        if(kind.twinLight)voltage=newGuiButton(8,82,224,"").apply { setComment(0,tr("Draws directly from the selected LV circuit; does not convert voltage")) }
        if(kind.singlePhaseTap && !kind.transformer)phase=newGuiButton(8,84,224,"")
        if(kind.cutout || kind.transformer || kind.regulator || kind.singlePhaseTap)repeat(kind.fuseCount) { i -> fuses.add(newGuiButton(8+i*75,134,72,"")) }
        if(kind.cutout || kind.recloser) {
            current=newGuiTextField(8,88,85).apply { text=(if(kind.recloser)d.pickup else d.fuseRating).toString();setComment(0,tr("Trip current in amperes: 1 to 2000 A")) }
            applyCurrent=newGuiButton(98,84,134,tr("Set trip current"))
        }
        if(kind.recloser) {
            retry=newGuiTextField(8,112,85).apply { text=d.delay.toString();setComment(0,tr("Retry delay: 1 to 60 seconds")) };applyRetry=newGuiButton(98,108,134,tr("Set retry delay"))
            max=newGuiButton(8,132,224,"");operate=newGuiButton(8,156,106,"");reset=newGuiButton(118,156,114,tr("Reset lockout"))
        }
        if(kind.regulator) {
            target=newGuiTextField(8,88,100).apply { text=d.target.toString() };applyTarget=newGuiButton(114,84,118,tr("Set voltage"));auto=newGuiButton(8,108,224,"")
            current=newGuiTextField(8,170,85).apply { text=d.fuseRating.toString();setComment(0,tr("Fuse trip current: 1 to 2000 A")) };applyCurrent=newGuiButton(98,166,134,tr("Set fuse current"))
        }
        if(kind==DistributionKind.AIR_BREAK_THREE)operate=newGuiButton(8,84,224,"")

        if(kind.light) {
            lamp=newGuiButton(8,162,106,"");dusk=newGuiButton(118,162,114,"")
            left=newGuiButton(8,186,106,tr("Pan left"));right=newGuiButton(118,186,114,tr("Pan right"))
            down=newGuiButton(8,210,106,tr("Lower lamp"));up=newGuiButton(118,210,114,tr("Raise lamp"))
            tiltDown=newGuiButton(8,234,106,tr("Tilt down"));tiltUp=newGuiButton(118,234,114,tr("Tilt up"))
        }
        refresh()
    }
    private fun refresh() {
        terminal.displayString=tr("Cable: %1$",ServiceTerminals.label(d.terminal))
        phase?.displayString=tr("Phase: %1$",listOf("A","B","C")[d.phase.coerceIn(0,2)])
        rating?.enabled=false
        rating?.displayString=tr("Rating: %1$ kW",d.rating/1000);voltage?.displayString=if(kind.twinLight)tr("Lamp supply: %1$ V",d.voltage) else tr("Nominal: %1$ V",d.voltage)
        operate?.displayString=when { kind.transformer -> if(d.enabled)tr("Transformer: On") else tr("Transformer: Off");kind.recloser -> if(d.closed)tr("Open all phases") else tr("Close all phases");else -> if(d.enabled)tr("Open switch") else tr("Close switch") }
        fuses.forEachIndexed { i,b -> b.displayString=tr("%1$: %2$",listOf("A","B","C")[if(kind.transformer || kind.singlePhaseTap)d.phase.coerceIn(0,2) else i],if(d.fuseBlown.getOrNull(i)==true)tr("Blown") else if(d.fuseClosed.getOrNull(i)!=false)tr("Open") else tr("Close"));b.enabled=d.fuseBlown.getOrNull(i)!=true;b.setComment(0,tr("Right-click the pole holding a Pole Fuse Link to replace a blown fuse"));if(kind.transformer && kind.singlePhaseTap)b.setComment(1,if(i==0)tr("Transformer fuse") else tr("Single phase tap fuse")) }
        max?.displayString=tr("Lockout after %1$ failed reclosures",d.maxFailures)
        auto?.displayString=if(d.automatic)tr("Voltage control: Auto") else tr("Voltage control: Hold tap")
        lamp?.displayString=if(d.lampOn)tr("Lamp: On") else tr("Lamp: Off");dusk?.displayString=if(d.dusk)tr("Dusk: Auto") else tr("Dusk: Manual")
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            left -> render.clientSendInt(85,if(net.minecraft.client.gui.screens.Screen.hasShiftDown())1 else 5)
            right -> render.clientSendInt(85,if(net.minecraft.client.gui.screens.Screen.hasShiftDown())-1 else -5)
            down -> render.clientSendInt(84,-1)
            up -> render.clientSendInt(84,1)
            tiltDown -> render.clientSendInt(98,if(net.minecraft.client.gui.screens.Screen.hasShiftDown())1 else 5)
            tiltUp -> render.clientSendInt(98,if(net.minecraft.client.gui.screens.Screen.hasShiftDown())-1 else -5)
            terminal -> { val ts=terminals();render.clientSendInt(87,ts[(ts.indexOf(d.terminal)+1)%ts.size]) }
            phase -> render.clientSendInt(88,(d.phase+1)%kind.phases)
            rating -> render.clientSendInt(86,MainsStepDown.RATINGS[(MainsStepDown.RATINGS.indexOf(d.rating)+1)%5])
            voltage -> render.clientSendInt(80,if(d.voltage==120)240 else 120)
            operate -> if(kind.transformer)render.clientSendBoolean(81,!d.enabled) else render.clientSendId(95)
            applyCurrent -> current?.text?.toDoubleOrNull()?.let { render.clientSendDouble(91,it) }
            applyRetry -> retry?.text?.toDoubleOrNull()?.let { render.clientSendDouble(92,it) }
            max -> render.clientSendInt(93,d.maxFailures%5+1)
            reset -> render.clientSendId(94)
            applyTarget -> target?.text?.toDoubleOrNull()?.let { render.clientSendDouble(96,it) }
            auto -> render.clientSendBoolean(97,!d.automatic)
            lamp -> render.clientSendBoolean(82,!d.lampOn)
            dusk -> render.clientSendBoolean(83,!d.dusk)
            else -> fuses.indexOf(obj).takeIf { it>=0 }?.let { render.clientSendInt(89,it) }
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);refresh()
        drawString(8,8,font.plainSubstrByWidth(tr(kind.title),224))
        if(kind.twinLight)drawString(54,35,tr("Recovered hardware"))
        else if(kind.transformer)drawString(32,35,if(d.installed)tr("Step-down unit installed") else tr("Insert step-down unit"))
        else drawString(8,30,tr("Line: %1$",Utils.plotVolt("",d.input).trim()))
        if(kind.light) {
            val aimX=if(kind.transformer)32 else 8
            drawString(aimX,47,font.plainSubstrByWidth(tr("Aim: %1$° / %2$°; height: %3$ m",d.yaw,d.pitch,String.format(Locale.ROOT,"%.2f",5.2+StreetLightMount.offset(d.heightStep))),232-aimX))
        }
        if(kind.recloser)drawString(8,184,if(d.locked)tr("Locked out; inspect fault and reset") else if(!d.closed)tr("Retry in %1$ s",String.format(Locale.ROOT,"%.1f",d.remaining)) else tr("All three phases closed"))
        else if(kind.regulator)drawString(8,if(kind.light)262 else 190,tr("Tap: %1$ %%",String.format(Locale.ROOT,"%.1f",100*(d.ratios.firstOrNull() ?: 1.0))))
        else drawString(8,if(kind.light)262 else 190,tr("Load: %1$",Utils.plotVolt("",d.output).trim()))
        drawString(8,if(kind.light)276 else 208,font.plainSubstrByWidth(if(kind.transformer)tr("Nominal primary: 12.47 kV; fixed ratio") else tr("Secondary: L1 120 V / L2 120 V / 240 V"),224))
    }
}
