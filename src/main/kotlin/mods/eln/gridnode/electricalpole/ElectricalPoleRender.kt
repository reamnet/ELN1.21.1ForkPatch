package mods.eln.gridnode.electricalpole

import mods.eln.utility.*
import mods.eln.misc.Direction
import net.minecraft.world.entity.player.Player
import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.gridnode.GridRender
import mods.eln.misc.LRDUMask
import mods.eln.misc.SlewLimiter
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sound.LoopedSound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.phys.Vec3
import mods.eln.client.gl.GL11

import java.io.DataInputStream
import java.io.IOException

class ElectricalPoleRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : GridRender(entity, descriptor) {

    override val inventory=mods.eln.node.transparent.TransparentNodeElementInventory(if((descriptor as ElectricalPoleDescriptor).includeTransformer)1 else 0,1,this)
    val display=LegacyPoleDisplay()
    internal var cableRenderType: CableRenderType? = null
    internal var eConn = LRDUMask()

    private val descriptor: ElectricalPoleDescriptor
    private val load = SlewLimiter(0.5f)

    init {
        this.descriptor = descriptor as ElectricalPoleDescriptor

        if (this.descriptor.includeTransformer) {
            addLoopedSound(object : LoopedSound("eln:transformer", coordinate(), SoundInstance.Attenuation.LINEAR) {
                override fun getVolume(): Float {
                    if (load.position > this@ElectricalPoleRender.descriptor.minimalLoadToHum)
                        return 0.05f * (load.position - this@ElectricalPoleRender.descriptor.minimalLoadToHum) / (1 - this@ElectricalPoleRender.descriptor.minimalLoadToHum)
                    else
                        return 0f
                }
            })
        }
    }

    override fun draw() {
        drawCables();descriptor.drawPole(highVoltageRenderAngle,display.rating)
        preserveMatrix {
            GL11.glRotatef(lowVoltageRenderAngle,0f,1f,0f)
            PoleSecondaryVisual.draw(Direction.XN,serviceMountReach.toDouble())
        }
        if(descriptor.includeTransformer)preserveMatrix {
            GL11.glRotatef(highVoltageRenderAngle,0f,1f,0f)
            PoleFuseVisual.draw(Direction.XN,display.fuseClosed,display.fuseBlown)
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                descriptor.plus.firstOrNull()?.let { PoleLeadVisual.draw(it.boundingBox().centre(),Vec3(-.68,6.40,0.0)) }
                PoleLeadVisual.draw(Vec3(-.68,5.85,0.0),PoleTransformerShape.of(display.rating).bushing)
            } finally { GL11.glPopAttrib() }
        }
        if(descriptor.includeTransformer) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
            try { GL11.glDisable(GL11.GL_TEXTURE_2D);PoleTransformerVisual.secondaryLeads(display.rating,highVoltageRenderAngle,lowVoltageRenderAngle,serviceMountReach.toDouble()) }
            finally { GL11.glPopAttrib() }
        }
        cableRenderType = drawCable(front!!.down(), Eln.instance.stdCableRender3200V, eConn, cableRenderType)
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        eConn.deserialize(stream)
        cableRenderType = null
        try {
            load.target = stream.readFloat()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        display.read(stream)

    }

    override fun newGuiDraw(side: Direction,player: Player)=LegacyPoleGui(player,this,descriptor.includeTransformer)

    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        load.step(deltaT)
    }
}
