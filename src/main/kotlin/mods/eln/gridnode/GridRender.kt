package mods.eln.gridnode

import mods.eln.misc.UtilsClient
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.world.phys.AABB
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

import java.io.DataInputStream
import java.io.IOException
import java.util.ArrayList

import mods.eln.client.gl.GL11.*
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

abstract class GridRender(tileEntity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(tileEntity, descriptor) {
    private val descriptor: GridDescriptor
    private val cableTexture: ResourceLocation
    private val catenaries = ArrayList<Catenary>()
    private var idealRenderingAngle: Float = 0.toFloat()

    init {
        this.descriptor = descriptor as GridDescriptor

        cableTexture = ResourceLocation.fromNamespaceAndPath("eln", this.descriptor.cableTexture)
    }

    override fun draw() {
        descriptor.draw(idealRenderingAngle)
        drawCables()
    }

    open fun drawCables() {
        UtilsClient.bindTexture(cableTexture)
        // TODO: Try not to need this. (How? Math.)
        glDisable(GL_CULL_FACE)
        for (catenary in catenaries) {
            catenary.draw()
        }
        glEnable(GL_CULL_FACE)
    }

    @Throws(IOException::class)
    private fun readVec(stream: DataInputStream): Vec3 {
        return Vec3(stream.readFloat().toDouble(), stream.readFloat().toDouble(), stream.readFloat().toDouble())
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            for (catenary in catenaries) {
                catenary.destroy()
            }
            catenaries.clear()
            idealRenderingAngle = stream.readFloat()
            val linkCount = stream.readInt()
            for (i in 0..linkCount - 1) {
                // Links always come in pairs.
                val splus = readVec(stream)
                val tplus = readVec(stream)
                val sgnd = readVec(stream)
                val tgnd = readVec(stream)
                var dplus = splus.subtract(tplus).normalize()
                var dgnd = sgnd.subtract(tgnd).normalize()
                val straightV = dplus.dot(dgnd)
                dplus = splus.subtract(tgnd).normalize()
                dgnd = sgnd.subtract(tplus).normalize()
                val crossV = dplus.dot(dgnd)
                if (crossV < straightV) {
                    catenaries.add(Catenary(splus, tplus))
                    catenaries.add(Catenary(sgnd, tgnd))
                } else {
                    catenaries.add(Catenary(splus, tgnd))
                    catenaries.add(Catenary(sgnd, tplus))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun cameraDrawOptimisation(): Boolean {
        return false
    }

    override fun unoptimizedRenderBoundingBox(): AABB {
        val margin = 16.0
        val extent = descriptor.connectRange + margin
        val cx = tileEntity.xCoord + 0.5
        val cy = tileEntity.yCoord + 0.5
        val cz = tileEntity.zCoord + 0.5
        return AABB(
            cx - extent, cy - extent, cz - extent,
            cx + extent, cy + extent, cz + extent
        )
    }

    private inner class Catenary(start: Vec3, end: Vec3) {
        private val list = glGenLists(1)
        init {
            val sections = OverheadCableMesh.sections(start, end)
            glNewList(list, GL_COMPILE)
            glBegin(GL_TRIANGLES)
            fun face(a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
                val normal = b.subtract(a).cross(c.subtract(a)).normalize()
                glNormal3f(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
                for ((point, uv) in listOf(a to (0f to 0f), b to (1f to 0f), c to (1f to 1f),
                    a to (0f to 0f), c to (1f to 1f), d to (0f to 1f))) {
                    glTexCoord2f(uv.first, uv.second)
                    glVertex3f(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                }
            }
            for (i in 0 until sections.lastIndex) for (side in 0..3) {
                val next = (side + 1) % 4
                face(sections[i][side], sections[i][next], sections[i + 1][next], sections[i + 1][side])
            }
            face(sections.first()[3], sections.first()[2], sections.first()[1], sections.first()[0])
            face(sections.last()[0], sections.last()[1], sections.last()[2], sections.last()[3])
            glEnd()
            glEndList()
        }
        fun draw() = glCallList(list)
        fun destroy() = glDeleteLists(list, 1)
    }
}
