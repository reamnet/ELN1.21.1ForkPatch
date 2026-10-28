package mods.eln.gridnode

import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

import java.io.DataInputStream
import java.io.IOException

import mods.eln.client.gl.GL11.*
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

abstract class GridRender(tileEntity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(tileEntity, descriptor) {
    private val descriptor: GridDescriptor
    private var cableMesh: Catenary?=null
    private var spans: List<OverheadSpan>?=null
    internal val terrainCableOwner=mods.eln.client.terrain.TerrainModels.CableOwner(this)
    internal val streetlightOwner=mods.eln.client.terrain.TerrainModels.AttachmentOwner(this)
    var poleStreetlight=mods.eln.utility.PoleStreetlightDisplay();private set
    protected var idealRenderingAngle: Float = 0.toFloat()
    var highVoltageRenderAngle=0f
        private set
    var lowVoltageRenderAngle=0f
        private set
    var serviceMountReach=ServiceCableMount.MIN_REACH.toFloat()
        private set

    init {
        this.descriptor = descriptor as GridDescriptor
    }

    override fun draw() {
        descriptor.draw(highVoltageRenderAngle)
        drawCables()
    }

    open fun drawCables() {
        capturePoleStreetlight()
        if (mods.eln.client.terrain.TerrainModels.isDiscovering) return
        captureTerrainCables()
    }
    internal fun capturePoleStreetlight()=mods.eln.utility.PoleStreetlightVisual.draw(this)

    internal fun captureTerrainCables() {
        if(cableMesh==null)return
        if(mods.eln.client.terrain.TerrainModels.isDiscovering) {
            val client=net.minecraft.client.Minecraft.getInstance()
            val level=tileEntity.level as? net.minecraft.client.multiplayer.ClientLevel ?: return
            val bounds=cableMesh!!.bounds.move(tileEntity.xCoord+.5,tileEntity.yCoord+.5,tileEntity.zCoord+.5)
            if(!OverheadCableVisibility.canBake(bounds,client.gameRenderer.mainCamera.position,client.options.effectiveRenderDistance) { x,z->level.chunkSource.hasChunk(x,z) })return
        }
        mods.eln.client.terrain.TerrainModels.body(terrainCableOwner,spans ?: emptyList<OverheadSpan>()) {
            glPushAttrib(GL_ALL_ATTRIB_BITS)
            try {
                glDisable(GL_TEXTURE_2D)
                glDisable(GL_CULL_FACE)
                cableMesh?.draw()
            } finally { glPopAttrib() }
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            idealRenderingAngle = stream.readFloat()
            highVoltageRenderAngle=stream.readFloat();lowVoltageRenderAngle=stream.readFloat()
            serviceMountReach=stream.readFloat()
            require(serviceMountReach.isFinite() && serviceMountReach in .1f..8f)
            val linkCount=stream.readInt();require(linkCount in 0..4096)
            val incoming=List(linkCount) { OverheadSpan.read(stream) }
            poleStreetlight=if(stream.readBoolean())mods.eln.utility.PoleStreetlightDisplay.read(stream) else mods.eln.utility.PoleStreetlightDisplay()
            // Voltage/status packets must not rebuild identical cable display lists or DH objects.
            if(incoming!=spans) {
                spans=incoming
                val wires=incoming.flatMap(OverheadSpanMesh::geometry)
                val replacement=if(wires.isEmpty())null else Catenary(wires)
                cableMesh?.destroy();cableMesh=replacement
            }
            // DH's bounded cache may evict an unchanged span. Its own signature guard
            // reuses live objects, or restores a missing snapshot without rebuilding GL.
            mods.eln.client.terrain.DistantModels.cables(tileEntity,cableMesh?.wires ?: emptyList())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun destructor() { cableMesh?.destroy();cableMesh=null;spans=null;super.destructor() }

    override fun cameraDrawOptimisation(): Boolean {
        return false
    }

    override fun unoptimizedRenderBoundingBox(): AABB {
        var bounds = descriptor.geometryBounds()
        cableMesh?.let { bounds=bounds.minmax(it.bounds) }
        return bounds.move(tileEntity.xCoord + .5, tileEntity.yCoord + .5, tileEntity.zCoord + .5)
    }

    internal class Catenary(val wires: List<OverheadWire>) {
        constructor(start: Vec3,end: Vec3): this(listOf(OverheadWire(OverheadCableMesh.points(start,end),.05,OverheadCableAppearance.JACKET)))
        val bounds: AABB
        val distantPoints: List<Vec3>
        private val list = glGenLists(1)
        internal var drawCalls=0L;private set
        init {
            val meshes=wires.map { OverheadCableMesh.sections(it.points,it.radius) }
            distantPoints=wires.first().points
            val vertices = meshes.flatten().flatten()
            bounds = AABB(vertices.minOf { it.x }, vertices.minOf { it.y }, vertices.minOf { it.z },
                vertices.maxOf { it.x }, vertices.maxOf { it.y }, vertices.maxOf { it.z }).inflate(.05)
            glNewList(list, GL_COMPILE)
            glBegin(GL_QUADS)
            fun face(a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
                val normal = b.subtract(a).cross(c.subtract(a)).normalize()
                glNormal3f(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
                for ((point, uv) in listOf(a to (0f to 0f), b to (1f to 0f), c to (1f to 1f), d to (0f to 1f))) {
                    glTexCoord2f(uv.first, uv.second)
                    glVertex3f(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                }
            }
            for((index,sections)in meshes.withIndex()) {
                val color=wires[index].color
                glColor3f(((color ushr 16) and 255)/255f,((color ushr 8) and 255)/255f,(color and 255)/255f)
                for (i in 0 until sections.lastIndex) for (side in 0..3) {
                    val next = (side + 1) % 4
                    face(sections[i][side], sections[i][next], sections[i + 1][next], sections[i + 1][side])
                }
                face(sections.first()[3], sections.first()[2], sections.first()[1], sections.first()[0])
                face(sections.last()[0], sections.last()[1], sections.last()[2], sections.last()[3])
            }
            glEnd()
            glEndList()
        }
        fun draw() { drawCalls++;glCallList(list) }
        fun destroy() = glDeleteLists(list, 1)
    }
}
