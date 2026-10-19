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

class OutletDescriptor: PowerCabinetDescriptor(I18N.TR_NAME(I18N.Type.NONE,"Experimental Wall Outlet"),OutletElement::class.java,OutletRender::class.java,true) {
    private val model=Eln.obj.getObj("appliancewalloutlet")
    fun draw(front: Direction,tripped: Boolean)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.86f,.85f,.79f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.09f,.095f,.10f);model.getPart("Black")?.drawNoBind()
            GL11.glColor3f(.5f,.51f,.53f);model.getPart("Trim")?.drawNoBind()
            GL11.glColor3f(if(tripped).9f else .12f,if(tripped).13f else .6f,.05f);model.getPart("Display")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
        Unit
    }
    override fun addCollisionBoxesToList(box: net.minecraft.world.phys.AABB,list: MutableList<net.minecraft.world.phys.AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {
        val tile=world?.getBlockEntity(net.minecraft.core.BlockPos(x,y,z)) as? TransparentNodeEntity
        val facing=tile?.elementRender?.front ?: (if(world?.isClientSide==false)tile?.node as? TransparentNode else null)?.element?.front ?: return
        val a=mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,Vec3(-.17,-.31,-.5))
        val b=mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,Vec3(.17,.31,-.36))
        val bound=net.minecraft.world.phys.AABB(kotlin.math.min(a.x,b.x)+x+.5,y+.19,kotlin.math.min(a.z,b.z)+z+.5,kotlin.math.max(a.x,b.x)+x+.5,y+.81,kotlin.math.max(a.z,b.z)+z+.5)
        if(bound.intersects(box))list.add(bound)
    }
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=draw(Direction.ZP,false)
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("One flexible cord; 120/240 V, 20 A breaker"))
        list.add(tr("Wire the wall outlet to your grid; it does not change voltage"))
    }
}
class OutletRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var id=UUID(0,0);var tripped=false;var volts=0.0;var amps=0.0
    val connections=LRDUMask();private var cable: CableRenderType?=null
    override fun draw() {
        val facing=front ?: return
        (transparentNodeDescriptor as OutletDescriptor).draw(facing,tripped)
        cable=drawCable(facing.inverse,Eln.instance.meduimVoltageCableDescriptor.render,connections,cable)
        val world=tileEntity.level!!;val origin=Vec3.atCenterOf(tileEntity.blockPos)
        for(p in world.players()) {
            val a=CordInteraction.heldAnchor(p.mainHandItem) ?: continue
            if(a.id!=id || p.distanceToSqr(origin.x,origin.y,origin.z)>100)continue
            val end=p.position().add(.2,.8,.2)
            CordPath.draw(CordPath.samples(a.tip(),end) { x,z,y -> CordPath.floor(world,x,z,y) },origin)
        }
    }
    override fun cameraDrawOptimisation()=false
    override fun networkUnserialize(input: DataInputStream) { super.networkUnserialize(input);id=UUID(input.readLong(),input.readLong());tripped=input.readBoolean();volts=input.readDouble();amps=input.readDouble();connections.deserialize(input);cable=null }
    override fun newGuiDraw(side: Direction,player: Player)=OutletGui(player,this)
}
class OutletGui(player: Player,private val render: OutletRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var reset: GuiButtonEln
    private lateinit var release: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,176,210,8,128)
    override fun initGui() { super.initGui();reset=newGuiButton(8,72,160,tr("Reset breaker"));release=newGuiButton(8,97,160,tr("Release lost cord")) }
    override fun guiObjectEvent(obj: IGuiObject) { super.guiObjectEvent(obj);if(obj==reset)render.clientSendId(80);if(obj==release)render.clientSendId(81) }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);drawString(8,8,tr("Wall outlet"))
        drawString(8,28,if(render.tripped)tr("Breaker tripped") else tr("Ready"))
        drawString(8,44,tr("%1$ V / %2$ A",render.volts.toInt(),String.format(java.util.Locale.ROOT,"%.1f",render.amps)))
        drawString(8,60,tr("20 A; one 8-block cord"))
    }
}
