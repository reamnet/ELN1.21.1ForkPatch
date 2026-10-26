package mods.eln.transparentnode.industrial

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.i18n.I18N.tr
import mods.eln.misc.preserveMatrix
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object IndustrialTransportModels {
    private val forklift by lazy { Eln.obj.getObj("industrial_forklift") }
    private val pallet by lazy { Eln.obj.getObj("industrial_pallet") }
    fun draw(isPallet: Boolean,lift: Double,cargo: Boolean): Unit=preserveMatrix {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun p(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);(if(isPallet)pallet else forklift).getPart(name)?.drawNoBind() }
            if(isPallet) {
                p("Wood",.52f,.34f,.16f);if(cargo)p("Cargo",.48f,.51f,.53f)
            } else {
                p("Body",.95f,.62f,.05f);p("Black",.08f,.09f,.10f);p("Wheel",.10f,.11f,.12f);p("Mast",.21f,.23f,.26f);p("Trim",.38f,.41f,.44f);p("Lamp",.95f,.38f,.04f)
                preserveMatrix {
                    GL11.glTranslated(0.0,lift,0.0);p("Fork",.36f,.38f,.41f)
                    if(cargo) { GL11.glTranslated(0.0,.24,1.35);draw(true,0.0,true) }
                }
            }
        } finally { GL11.glPopAttrib() }
    }
}
class IndustrialTransportRenderer<T: Entity>(context: EntityRendererProvider.Context,private val pallet: Boolean): EntityRenderer<T>(context) {
    init { shadowRadius=if(pallet).5f else .8f }
    override fun getTextureLocation(entity: T)=FixedFunction.WHITE
    override fun render(entity: T,yaw: Float,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int) {
        pose.pushPose();FixedFunction.begin(pose,buffers,light,OverlayTexture.NO_OVERLAY)
        try {
            GL11.glRotatef(-yaw,0f,1f,0f)
            IndustrialTransportModels.draw(pallet,(entity as? ForkliftEntity)?.lift ?: 0.0,(entity as? ForkliftEntity)?.hasCargo ?: (entity as? PalletEntity)?.loaded ?: false)
        } finally { FixedFunction.finish();pose.popPose() }
        super.render(entity,yaw,partial,pose,buffers,light)
    }
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT],bus=EventBusSubscriber.Bus.MOD)
object ForkliftKeys {
    val raise=KeyMapping(tr("Forklift: raise forks"),GLFW.GLFW_KEY_PAGE_UP,tr("Electrical Age vehicles"))
    val lower=KeyMapping(tr("Forklift: lower forks"),GLFW.GLFW_KEY_PAGE_DOWN,tr("Electrical Age vehicles"))
    val cargo=KeyMapping(tr("Forklift: load or unload pallet"),GLFW.GLFW_KEY_G,tr("Electrical Age vehicles"))
    @SubscribeEvent fun register(event: RegisterKeyMappingsEvent) { event.register(raise);event.register(lower);event.register(cargo) }
}
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object ForkliftClientControls {
    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        val mc=Minecraft.getInstance();val player=mc.player ?: return
        val vehicle=player.vehicle as? ForkliftEntity ?: return
        if(vehicle.controllingPassenger!==player)return
        val active=mc.screen==null
        fun axis(a: Boolean,b: Boolean)=if(!active)0 else (if(a)1 else 0)-(if(b)1 else 0)
        mods.eln.network.ElnNetwork.sendToServer(ForkliftControls(axis(mc.options.keyUp.isDown,mc.options.keyDown.isDown),axis(mc.options.keyLeft.isDown,mc.options.keyRight.isDown),axis(ForkliftKeys.raise.isDown,ForkliftKeys.lower.isDown),active && ForkliftKeys.cargo.isDown))
        if(player.tickCount%20==0)player.displayClientMessage(net.minecraft.network.chat.Component.literal(tr("Traction pack: %1$%; forks: %2$ cm",(vehicle.stateOfCharge*100).toInt(),(vehicle.lift*100).toInt())),true)
    }
}
