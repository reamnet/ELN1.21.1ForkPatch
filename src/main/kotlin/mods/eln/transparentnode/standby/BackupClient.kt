package mods.eln.transparentnode.standby

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.sound.LoopedSound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream

fun backupStatus(value: String)=when(value) {
    "OFF" -> tr("Switched off")
    "MAINS" -> tr("Load supplied by mains")
    "GENERATOR" -> tr("Load supplied by generator")
    "WAITING_GENERATOR" -> tr("Start requested; waiting for rated voltage")
    "BACKUP_FEED_FAULT" -> tr("Backup feed fault; reset ATS")
    "TRANSFER" -> tr("Both contacts open; checking supply")
    "STARTING" -> tr("Starting engine (3 seconds)")
    "RUNNING" -> tr("Engine running")
    "COOLDOWN" -> tr("Engine cooling down")
    "EMPTY" -> tr("Refuel the gasoline tank")
    "OVERLOAD" -> tr("Overload stopped engine; reset to retry")
    "BACKFEED" -> tr("Reverse power stopped engine; reset to retry")
    else -> tr("Standby; waiting for wireless request")
}

class BackupDisplay {
    var target=240.0;var channel="";var status="STANDBY";var mode=0
    var mains=0.0;var backup=0.0;var output=0.0;var watts=0.0;var fuel=0.0;var request=false
    var connections=LRDUMask()
    fun write(s: DataOutputStream) {
        s.writeDouble(target);s.writeUTF(channel);s.writeUTF(status);s.writeInt(mode)
        s.writeDouble(mains);s.writeDouble(backup);s.writeDouble(output);s.writeDouble(watts);s.writeDouble(fuel);s.writeBoolean(request);connections.serialize(s)
    }
    fun read(s: DataInputStream) {
        target=s.readDouble();channel=s.readUTF();status=s.readUTF();mode=s.readInt()
        mains=s.readDouble();backup=s.readDouble();output=s.readDouble();watts=s.readDouble();fuel=s.readDouble();request=s.readBoolean();connections.deserialize(s)
    }
}

class BackupDescriptor(name: String,val generator: Boolean,wallMounted: Boolean=false) : PowerCabinetDescriptor(name,
    if(generator)StandbyGeneratorElement::class.java else AtsElement::class.java,BackupRender::class.java,
    wallMounted,if(generator)EntityMetaTag.Fluid else EntityMetaTag.Basic) {
    init { require(!generator || !wallMounted) }
    private val model=Eln.obj.getObj(if(generator)"standbygenerator" else if(wallMounted)"wallats" else "automatictransferswitch")
    fun draw(front: Direction,display: BackupDisplay?=null)=preserveMatrix {
        front.inverse.glRotateZnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(if(generator).73f else .28f,if(generator).72f else .31f,if(generator).63f else .34f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.16f,.18f,.20f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(.04f,.045f,.05f);model.getPart("Vents")?.drawNoBind()
            GL11.glColor3f(.1f,.35f,.85f);model.getPart("Mains")?.drawNoBind()
            GL11.glColor3f(1f,.65f,.06f);model.getPart("Backup")?.drawNoBind()
            GL11.glColor3f(.95f,.33f,.05f);model.getPart("Output")?.drawNoBind()
            val lit=display?.status in listOf("RUNNING","COOLDOWN","MAINS","GENERATOR")
            if(lit)UtilsClient.disableLight()
            GL11.glColor3f(if(lit).08f else .02f,if(lit).8f else .08f,.12f);model.getPart("Indicator")?.drawNoBind()
            GL11.glColor3f(.035f,if(lit).35f else .05f,if(lit).42f else .06f);model.getPart("Display")?.drawNoBind()
            if(lit)UtilsClient.enableLight()
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any) { draw(Direction.ZP) }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        if(generator) {
            list.add(tr("10000 W gasoline standby generator; 20-bucket tank"))
            list.add(tr("Programmable output: 12 to 51200 V (200 A maximum)"))
            list.add(tr("Auto, manual and off modes; three-second engine start"))
            list.add(tr("Accepts tagged/configured gasoline from other mods; pipes and containers supported"))
            list.add(tr("Orange side terminals: power output"))
        } else {
            list.add(tr("Automatic transfer switch; opens one source before closing the other"))
            list.add(if(wallMounted)tr("Wall cables: blue left mains; yellow bottom generator; orange right load")
                else tr("Blue left: mains; yellow rear: generator; orange right: load"))
            list.add(tr("Set both sources to the same rated voltage (12 to 51200 V)"))
            list.add(tr("Detects failure after 1 s; returns after 5 s of stable mains"))
        }
        list.add(tr("Matching wireless channels control engine starting; power still needs cables"))
        list.add(tr("Wireless control works between loaded blocks in the same dimension"))
        if(wallMounted)list.add(tr("Place against a vertical wall; connect cables on the same wall face"))
    }
}

class BackupRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val display=BackupDisplay()
    private var cable: CableRenderType?=null
    init {
        if((descriptor as BackupDescriptor).generator)addLoopedSound(object : LoopedSound("eln:fuelgenerator",coordinate(),SoundInstance.Attenuation.LINEAR) {
            override fun getVolume()=GeneratorEffects.volume(display.status,display.watts)
            override fun getPitch()=(.84+.10*(display.watts/10_000).coerceIn(0.0,1.0)).toFloat()
        })
    }
    override fun draw() {
        cable=drawCable((transparentNodeDescriptor as BackupDescriptor).connectionPlane(front ?: Direction.ZP),Eln.instance.stdCableRender3200V,display.connections,cable)
        (transparentNodeDescriptor as BackupDescriptor).draw(front ?: Direction.ZP,display)
    }
    override fun cameraDrawOptimisation()=false
    private var smokeClock=0f
    var emittedSmoke=0
        private set
    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        if(!(transparentNodeDescriptor as BackupDescriptor).generator || !GeneratorEffects.running(display.status)) { smokeClock=0f;return }
        smokeClock+=deltaT.coerceIn(0f,.25f)
        if(smokeClock<.15f)return
        smokeClock%=.15f
        val world=tileEntity.level ?: return
        val pipe=GeneratorEffects.exhaust(front ?: Direction.ZP)
        val pos=tileEntity.blockPos
        world.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,pos.x+.5+pipe.x,pos.y+.5+pipe.y,pos.z+.5+pipe.z,0.0,.045,0.0)
        emittedSmoke++
    }
    override fun networkUnserialize(stream: DataInputStream) { super.networkUnserialize(stream);display.read(stream);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=BackupGui(player,this)
}

class BackupGui(player: Player,private val render: BackupRender) : GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private val generator=(render.transparentNodeDescriptor as BackupDescriptor).generator
    private val display get()=render.display
    private lateinit var voltage: GuiTextFieldEln
    private lateinit var channel: GuiTextFieldEln
    private lateinit var apply: GuiButtonEln
    private lateinit var save: GuiButtonEln
    private lateinit var mode: GuiButtonEln
    private var reset: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,176,280,8,197)
    override fun initGui() {
        super.initGui()
        mode=newGuiButton(8,70,160,"")
        voltage=newGuiTextField(8,100,70).apply { text=display.target.toString();setComment(0,tr("Rated voltage: 12 to 51200 V; match both supplies")) }
        apply=newGuiButton(84,96,84,tr("Set voltage"))
        channel=newGuiTextField(8,124,106).apply { text=display.channel;setComment(0,tr("Wireless start channel (case-sensitive); blank disables remote starting")) }
        save=newGuiButton(120,120,48,tr("Link"))
        if(generator)reset=newGuiButton(8,144,160,tr("Reset protection"))
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            apply -> voltage.text.toDoubleOrNull()?.takeIf(TransferControl::validVoltage)?.let { render.clientSendDouble(80,it) }
            save -> render.clientSendString(81,channel.text)
            mode -> render.clientSendId(82)
            reset -> render.clientSendId(83)
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,if(generator)tr("Standby Gasoline Generator") else tr("Automatic Transfer Switch"))
        drawString(8,22,font.plainSubstrByWidth(backupStatus(display.status),160))
        if(generator) {
            drawString(8,34,tr("Gasoline: %1$ / 20000 mB",display.fuel.toInt()))
            drawString(8,46,tr("Rated: %1$ V / %2$ W",display.target.toInt(),minOf(10_000.0,200*display.target).toInt()))
        } else {
            drawString(8,34,tr("Mains: %1$",Utils.plotVolt("",display.mains).trim()))
            drawString(8,46,tr("Generator: %1$",Utils.plotVolt("",display.backup).trim()))
        }
        drawString(8,58,font.plainSubstrByWidth(tr("Out: %1$ / %2$",Utils.plotVolt("",display.output).trim(),Utils.plotPower("",display.watts).trim()),160))
        mode.displayString=if(!generator) { if(display.mode==0)tr("Automatic: On") else tr("Automatic: Off") }
            else when(display.mode) { 0->tr("Mode: Auto");1->tr("Mode: Manual run");else->tr("Mode: Off") }
        apply.enabled=voltage.text.toDoubleOrNull()?.let(TransferControl::validVoltage)==true
        if(!generator) {
            drawString(8,148,tr("Blue: mains; yellow: generator"))
            drawString(8,160,tr("Orange: load; same voltage"))
        }
        drawString(8,174,if(display.channel.isBlank())tr("Wireless start: unlinked") else if(display.request)tr("Wireless start: requested") else tr("Wireless start: ready"))
    }
}
