package mods.eln.transparentnode.cord

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.gui.*
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.util.UUID

class OutletDescriptor(val rating: CordRating=CordRating.V120,val strip: Boolean=false): PowerCabinetDescriptor(
    if(strip)I18N.TR_NAME(I18N.Type.NONE,"Six Socket Power Strip") else rating.outletName,OutletElement::class.java,OutletRender::class.java,true) {
    val displayName=if(!strip && rating==CordRating.V120)I18N.TR_NAME(I18N.Type.NONE,"120 V Wall Outlet") else name
    val sockets get()=if(strip)6 else 2
    private val model=Eln.obj.getObj(if(strip)"sixsocketpowerstrip" else if(rating==CordRating.V120)"appliancewalloutlet" else "walloutlet240")
    // Keep the old registry identity so placed outlets and inventory stacks survive the rename.
    override fun getName(stack: ItemStack)=if(!strip && rating==CordRating.V120)tr("120 V Wall Outlet") else super.getName(stack)
    fun draw(front: Direction,horizontal: Boolean)=preserveMatrix {
        front.inverse.glRotateZnRef();if(horizontal)GL11.glRotatef(90f,0f,0f,1f)
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.86f,.85f,.79f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.09f,.095f,.10f);model.getPart("Black")?.drawNoBind();model.getPart("TopSocket")?.drawNoBind()
            GL11.glColor3f(.5f,.51f,.53f);model.getPart("Trim")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
        Unit
    }
    override fun addCollisionBoxesToList(box: net.minecraft.world.phys.AABB,list: MutableList<net.minecraft.world.phys.AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {
        val tile=world?.getBlockEntity(net.minecraft.core.BlockPos(x,y,z)) as? TransparentNodeEntity ?: return
        val render=tile.elementRender as? OutletRender;val element=if(world?.isClientSide==false)(tile.node as? TransparentNode)?.element as? OutletElement else null
        val facing=render?.front ?: element?.front ?: return
        val horizontal=render?.horizontal ?: element?.horizontal ?: false
        val width=if(strip).40 else .17;val height=if(strip).22 else .31
        val w=if(horizontal)height else width;val h=if(horizontal)width else height
        val points=listOf(Vec3(-w,-h,-.5),Vec3(w,h,-.36)).map { mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,it) }
        val bound=net.minecraft.world.phys.AABB(points[0],points[1]).move(x+.5,y+.5,z+.5)
        if(bound.intersects(box))list.add(bound)
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw(Direction.ZP,false)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("%1$: %2$ V, 20 A nominal; %3$ sockets",rating.socket,rating.volts.toInt(),sockets))
        list.add(tr("Passive outlet; use an upstream circuit breaker"))
        list.add(tr("Choose wall cables or wireless lamp supply; voltage is not converted"))
        list.add(tr("Rotate in the GUI for horizontal countertop placement"))
        if(strip)list.add(tr("Plug a 120 V appliance cord into another outlet to feed this strip"))
    }
}
class OutletRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    val desc get()=transparentNodeDescriptor as OutletDescriptor
    var wireless=false;var channel="";var supplied=false;var horizontal=false;var sockets=0
    val occupied get()=sockets!=0
    var id=UUID(0,0);val tripped get()=false;var volts=0.0;var amps=0.0
    val connections=LRDUMask();private var cable: CableRenderType?=null
    override fun draw() {
        val facing=front ?: return
        desc.draw(facing,horizontal)
        var drawn=sockets
        repeat(desc.sockets) { i -> if(sockets and (1 shl i)!=0)NemaPlug.draw(facing,desc.rating,socket=i,horizontal=horizontal,strip=desc.strip) }
        cable=drawCable(facing.inverse,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        val world=tileEntity.level!!;val origin=Vec3.atCenterOf(tileEntity.blockPos)
        for(p in world.players()) {
            val a=CordInteraction.heldAnchor(p.mainHandItem) ?: continue
            if(a.id!=id || p.distanceToSqr(origin.x,origin.y,origin.z)>100)continue
            if(drawn and (1 shl a.socket)==0) { NemaPlug.draw(facing,a.rating,socket=a.socket,horizontal=horizontal,strip=desc.strip);drawn=drawn or (1 shl a.socket) }
            val tip=origin.add(mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,OutletGeometry.tipLocal(a.socket,horizontal,desc.strip)))
            val end=p.position().add(.2,.8,.2)
            CordPath.draw(CordPath.samples(tip,end) { x,z,y -> CordPath.floor(world,x,z,y) },origin)
        }
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input);id=UUID(input.readLong(),input.readLong());wireless=input.readBoolean();channel=input.readUTF();supplied=input.readBoolean()
        horizontal=input.readBoolean();sockets=input.readInt();volts=input.readDouble();amps=input.readDouble();connections.deserialize(input);cable=null
    }
    override fun newGuiDraw(side: Direction,player: Player)=OutletGui(player,this)
}
class OutletGui(player: Player,private val render: OutletRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var rotate: GuiButtonEln;private lateinit var release: GuiButtonEln;private lateinit var mode: GuiButtonEln;private lateinit var channel: GuiTextFieldEln
    override fun newHelper()=GuiHelperContainer(this,176,270,8,188)
    override fun initGui() { super.initGui();rotate=newGuiButton(8,72,160,"");release=newGuiButton(8,97,160,tr("Release lost cords"));mode=newGuiButton(8,122,160,"");channel=newGuiTextField(9,153,158).apply { text=render.channel;setComment(0,tr("Wireless lamp supply channel")) } }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);if(obj==rotate)render.clientSendId(84);if(obj==release)render.clientSendId(81);if(obj==mode)render.clientSendId(82);if(obj==channel)render.clientSendString(83,channel.text) }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);drawString(8,8,tr("%1$ / %2$ V",render.desc.rating.socket,render.desc.rating.volts.toInt()))
        rotate.displayString=if(render.horizontal)tr("Mount: Horizontal") else tr("Mount: Vertical")
        mode.displayString=if(render.wireless)tr("Wireless lamp supply") else tr("Cable power");channel.enabled=render.wireless
        if(render.wireless)drawString(8,173,if(render.supplied)tr("Supply connected") else tr("No active supply in range"))
        drawString(8,28,tr("%1$ / %2$ sockets occupied",Integer.bitCount(render.sockets),render.desc.sockets))
        drawString(8,44,tr("%1$ V / %2$ A",render.volts.toInt(),String.format(java.util.Locale.ROOT,"%.1f",render.amps)))
        drawString(8,60,tr("Protected by upstream breaker"))
    }
}
object OutletGeometry {
    fun offset(socket: Int,strip: Boolean)=if(strip)Vec3((socket%3-1)*.25,if(socket<3).02 else .22,0.0) else Vec3(0.0,socket*.26,0.0)
    fun tipLocal(socket: Int,horizontal: Boolean,strip: Boolean): Vec3 {
        val p=Vec3(0.0,-.1,-.224).add(offset(socket,strip))
        return if(horizontal)Vec3(-p.y,p.x,p.z) else p
    }
}
