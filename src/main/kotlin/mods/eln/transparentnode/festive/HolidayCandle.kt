package mods.eln.transparentnode.festive

import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.misc.UtilsClient
import mods.eln.node.transparent.*
import mods.eln.client.gl.GL11
import java.io.DataInputStream
import java.io.IOException

class HolidayCandleDescriptor(val name: String, val obj: Obj3D): TransparentNodeDescriptor(name, FestiveElement::class.java, HolidayCandleRender::class.java) {
    private var base: Obj3D.Obj3DPart? = null
    private var glass: Obj3D.Obj3DPart? = null
    private var light: Obj3D.Obj3DPart? = null

    init {
        base = obj.getPart("CandleLamp_Cylinder.001")
        glass = obj.getPart("Glass_Cylinder.000")
        light = obj.getPart("LampOn_Cylinder.002")
    }

    override fun mustHaveFloor()=false
    override fun checkCanPlace(coord: mods.eln.misc.Coordinate?, front: Direction): String? {
        if(coord!=null) {
            val below=coord.pos.below();val world=coord.world()
            val top=mods.eln.sixnode.lampsocket.LampMounting.surface(world.getBlockState(below).getCollisionShape(world,below).toAabbs(),Direction.YN)
            if(top==null)return mods.eln.i18n.I18N.tr("Place the candle on a supporting surface")
        }
        return super.checkCanPlace(coord,front)
    }
    override fun addCollisionBoxesToList(box: net.minecraft.world.phys.AABB,list: MutableList<net.minecraft.world.phys.AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {
        val entity=world?.getBlockEntity(net.minecraft.core.BlockPos(x,y,z)) as? TransparentNodeEntity
        val offset=if(world?.isClientSide==true)(entity?.elementRender as? HolidayCandleRender)?.mountOffset ?: 0.0 else ((entity?.node as? TransparentNode)?.element as? FestiveElement)?.mountOffset ?: 0.0
        val small=net.minecraft.world.phys.AABB(x+.38,y+offset,z+.38,x+.62,y+offset+.6,z+.62)
        if(small.intersects(box))list.add(small)
    }
    fun draw(front: Direction, powered: Boolean) {
        if (base != null && light != null && glass != null) {
            front.glRotateZnRef()
            GL11.glTranslatef(-0.5f, -0.5f, 0.5f)
            //GL11.glScalef(0.5f, 0.5f, 0.5f)
            //UtilsClient.drawLight(led);
            base?.draw()
            UtilsClient.disableCulling()
            if (powered) {
                UtilsClient.drawLight(light)
            }
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            glass?.draw()
            GL11.glDisable(GL11.GL_BLEND)
            UtilsClient.enableCulling()
        }
    }
}

class HolidayCandleRender(tileEntity: TransparentNodeEntity, transparentNodedescriptor: TransparentNodeDescriptor): FestiveRender(tileEntity, transparentNodedescriptor) {


    override fun draw() {
        mods.eln.client.terrain.TerrainModels.body(this,listOf(front,powered,mountOffset)) { mods.eln.misc.preserveMatrix { GL11.glTranslated(0.0,mountOffset,0.0);(transparentNodeDescriptor as HolidayCandleDescriptor).draw(front!!,powered) } }
    }
}
