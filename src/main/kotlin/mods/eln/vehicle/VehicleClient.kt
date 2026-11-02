package mods.eln.vehicle

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.client.power.PowerPanelDisplay
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import java.io.DataInputStream
import kotlin.math.abs

class RoadVehicleRenderer(context: EntityRendererProvider.Context): EntityRenderer<RoadVehicleEntity>(context) {
    init { shadowRadius=1f }
    override fun getTextureLocation(entity: RoadVehicleEntity)=FixedFunction.WHITE
    override fun render(e: RoadVehicleEntity,yaw: Float,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int) {
        pose.pushPose();FixedFunction.begin(pose,buffers,light,OverlayTexture.NO_OVERLAY)
        try {
            GL11.glRotatef(-yaw,0f,1f,0f)
            VehicleModels.draw(e.kind,e.charge.toDouble(),e.plugged,(e.tickCount+partial)*e.displayedSpeed*8)
        } finally { FixedFunction.finish();pose.popPose() }
        super.render(e,yaw,partial,pose,buffers,light)
    }
}
object VehicleModels {
    fun draw(kind: RoadVehicleKind,charge: Double=0.0,plugged: Boolean=false,spin: Float=0f)=preserveMatrix {
        val model=Eln.obj.getObj(if(kind==RoadVehicleKind.CART)"electric_golf_cart" else "arc_runner_coupe")
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            p("Body",if(kind==RoadVehicleKind.CART).08f else .12f,if(kind==RoadVehicleKind.CART).52f else .26f,if(kind==RoadVehicleKind.CART).33f else .56f)
            p("Trim",.09f,.11f,.14f);p("Seats",.22f,.24f,.27f);p("Frame",.56f,.6f,.64f);p("Roof",.78f,.8f,.82f);p("Headlights",.92f,.96f,1f);p("TailLights",.9f,.08f,.04f)
            for(i in 0..3)preserveMatrix {
                val x=(if(i%2==0)1 else -1)*(if(kind==RoadVehicleKind.CART).77 else .86);val z=if(kind==RoadVehicleKind.CART) { if(i<2).9 else -.9 } else { if(i<2)1.25 else -1.23 }
                GL11.glTranslated(x,.29,z);GL11.glRotatef(spin,1f,0f,0f);p("Wheel$i",.07f,.08f,.09f);p("Rim$i",.64f,.68f,.73f)
            }
            PowerPanelDisplay.lamp(model,"ChargeLamp",plugged,.16f,.9f,.45f)
            PowerPanelDisplay.equipmentNumber(model,charge*100,true,"BatteryDisplay")
            // Draw the glazing after the cabin, with a light tint and no opaque depth mask.
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDepthMask(false)
            GL11.glColor4f(.62f,.82f,.9f,.16f);model.getPart("Glass")?.drawNoBind()
            Unit
        } finally { GL11.glPopAttrib() }
    }
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object RoadVehicleClient {
    @SubscribeEvent fun renderers(event: EntityRenderersEvent.RegisterRenderers) { event.registerEntityRenderer(RoadVehicles.cart.get(),::RoadVehicleRenderer);event.registerEntityRenderer(RoadVehicles.coupe.get(),::RoadVehicleRenderer) }
    @SubscribeEvent fun itemRenderers(event: net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent) {
        val items=mods.eln.registration.ElnRegistry.registeredItems.values.filter { (it as? mods.eln.generic.DescriptorItem<*>)?.descriptor is RoadVehicleItem }.toTypedArray()
        event.registerItem(object: net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer(): net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer=VehicleItemRenderer.instance
        },*items)
    }
    @SubscribeEvent fun screens(event: RegisterMenuScreensEvent) { event.register(RoadVehicles.menu.get(),::VehicleScreen) }
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object RoadVehicleInput {
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        val mc=Minecraft.getInstance();val p=mc.player ?: return;val e=p.vehicle as? RoadVehicleEntity ?: return
        if(e.controllingPassenger!==p)return
        fun axis(a: Boolean,b: Boolean)=if(mc.screen!=null)0 else (if(a)1 else 0)-(if(b)1 else 0)
        mods.eln.network.ElnNetwork.sendToServer(VehicleControls(axis(mc.options.keyUp.isDown,mc.options.keyDown.isDown),axis(mc.options.keyLeft.isDown,mc.options.keyRight.isDown)))
        if(p.tickCount%10==0)p.displayClientMessage(Component.literal(tr("%1$ km/h | Battery %2$% | Packs %3$/%4$",(abs(e.displayedSpeed)*3.6).toInt(),(e.charge*100).toInt(),e.installedPacks,e.kind.packs)),true)
    }
}
private class VehicleScreen(menu: VehicleMenu,inv: Inventory,title: Component): AbstractContainerScreen<VehicleMenu>(menu,inv,title) {
    init { imageWidth=176;imageHeight=188;inventoryLabelY=95 }
    override fun renderBg(g: GuiGraphics,partial: Float,mx: Int,my: Int) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff202d38.toInt())
        menu.slots.filter { it.isActive }.forEach { s->g.fill(leftPos+s.x-1,topPos+s.y-1,leftPos+s.x+17,topPos+s.y+17,0xff111a22.toInt()) }
    }
    override fun renderLabels(g: GuiGraphics,mx: Int,my: Int) {
        g.drawString(font,title,8,8,0xffffff,false)
        g.drawString(font,tr("Traction battery bays"),8,20,0xa7d8ff,false)
        g.drawString(font,tr("Storage"),8,62,0xd8dce0,false)
        g.drawString(font,playerInventoryTitle,8,95,0xd8dce0,false)
    }
    override fun render(g: GuiGraphics,mx: Int,my: Int,partial: Float) { super.render(g,mx,my,partial);renderTooltip(g,mx,my) }
}
class VehicleChargerDescriptor: TransparentNodeDescriptor(TITLE,VehicleChargerElement::class.java,VehicleChargerRender::class.java) {
    companion object { val TITLE=I18N.TR_NAME(I18N.Type.NONE,"240 V 100 A Vehicle Charger") }
    init { creativeTab=Eln.creativeTabPowerElectronics }
    override fun addCollisionBoxesToList(box: net.minecraft.world.phys.AABB,list: MutableList<net.minecraft.world.phys.AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {
        val tile=world?.getBlockEntity(net.minecraft.core.BlockPos(x,y,z)) as? TransparentNodeEntity
        val facing=tile?.elementRender?.front ?: (tile?.node as? TransparentNode)?.element?.front ?: Direction.ZP
        val sideways=facing==Direction.XN || facing==Direction.XP
        val shapes=if(sideways)listOf(net.minecraft.world.phys.AABB(.225,0.0,.18,.775,.13,.82),net.minecraft.world.phys.AABB(.35,.13,.28,.65,1.63,.72))
            else listOf(net.minecraft.world.phys.AABB(.18,0.0,.225,.82,.13,.775),net.minecraft.world.phys.AABB(.28,.13,.35,.72,1.63,.65))
        shapes.map { it.move(x.toDouble(),y.toDouble(),z.toDouble()) }.filter { it.intersects(box) }.forEach(list::add)
    }
    fun draw(front: Direction=Direction.ZP,volts: Double=0.0,watts: Double=0.0,on: Boolean=false)=preserveMatrix {
        front.inverse.glRotateZnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);val model=Eln.obj.getObj("vehicle_charger")
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
            p("Body",.76f,.79f,.82f);p("Trim",.08f,.11f,.14f);p("Lead",.06f,.07f,.08f);p("Socket",.16f,.48f,.72f)
            PowerPanelDisplay.equipmentNumber(model,volts,volts>1,"Voltage");PowerPanelDisplay.equipmentNumber(model,watts,volts>1,"Power");PowerPanelDisplay.lamp(model,"Indicator",on,.12f,.9f,.4f)
        } finally { GL11.glPopAttrib() }
    }
    override fun handleRenderType(item: net.minecraft.world.item.ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: net.minecraft.world.item.ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: net.minecraft.world.item.ItemStack,vararg data: Any)=draw(Direction.ZN)
}
class VehicleChargerRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(entity,descriptor),mods.eln.client.terrain.BakedNodeRenderer {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var volts=0.0;var watts=0.0;var stored=0.0;var state="IDLE";var target=-1
    override fun networkUnserialize(input: DataInputStream) { super.networkUnserialize(input);volts=input.readDouble();watts=input.readDouble();stored=input.readDouble();state=input.readUTF();target=input.readLong().toInt() }
    override fun draw() {
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front),setOf("Indicator","Voltage","Power")) { (transparentNodeDescriptor as VehicleChargerDescriptor).draw(front ?: Direction.ZP,volts,watts,state=="CHARGING") }
        if(mods.eln.client.terrain.TerrainModels.isDiscovering)return
        val world=tileEntity.level ?: return
        val car=world.getEntity(target) as? RoadVehicleEntity ?: return
        val origin=net.minecraft.world.phys.Vec3.atCenterOf(tileEntity.blockPos)
        if(car.position().distanceToSqr(origin)>40)return
        val start=origin.add(mods.eln.transparentnode.worklight.WorkLightOptics.world(front ?: Direction.ZP,net.minecraft.world.phys.Vec3(0.0,.28,.23)))
        val end=car.position().add(net.minecraft.world.phys.Vec3(-.68,.72,if(car.kind==RoadVehicleKind.CART)-1.36 else -2.02).yRot(-car.yRot*kotlin.math.PI.toFloat()/180))
        mods.eln.transparentnode.cord.CordPath.draw(mods.eln.transparentnode.cord.CordPath.samples(start,end) { x,z,y->mods.eln.transparentnode.cord.CordPath.floor(world,x,z,y,tileEntity.blockPos) },origin)
    }
}

/** The vehicle item previews use the same mesh as the placed entity. */
class VehicleItemRenderer: net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    companion object { val instance by lazy { VehicleItemRenderer() } }
    override fun renderByItem(stack: net.minecraft.world.item.ItemStack,context: net.minecraft.world.item.ItemDisplayContext,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val item=mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(stack) as? RoadVehicleItem ?: return
        pose.pushPose();pose.translate(.5,.5,.5);FixedFunction.begin(pose,buffers,light,overlay)
        try { item.renderItem(IItemRenderer.ItemRenderType.of(context),stack) } finally { FixedFunction.finish();pose.popPose() }
    }
}
