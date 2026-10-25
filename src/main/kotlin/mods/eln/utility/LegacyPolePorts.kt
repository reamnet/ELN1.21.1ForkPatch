package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.ghost.GhostBlock
import mods.eln.gridnode.GridElement
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream

/** Adds secondary pass-through without changing the meaning of saved legacy HV links. */
object UndergroundService {
    fun circuit(lrdu: LRDU)=when(lrdu) { LRDU.Left->0;LRDU.Right->1;else->2 }
}

class LegacyPolePorts(owner: GridElement,private val high: ElectricalLoad,suppliedServices: List<NbtElectricalLoad>?=null) {
    val services=suppliedServices ?: List(3) { NbtElectricalLoad("poleService$it").apply { serialResistance=.00001 } }
    var selected=32
        private set
    init { if(suppliedServices==null)owner.electricalLoadList.addAll(services) }
    val terminals=listOf(32,50,40,41,42)
    fun select(value: Int) { if(value in terminals)selected=value }
    fun loads(id: Int): List<ElectricalLoad> = when(id) { in 0..5,32->listOf(high);50->services;in 40..42->listOf(services[id-40]);else->emptyList() }
    fun point(id: Int,wire: Int)=Vec3(.405,4.65+(if(id==50)wire else (id-40).coerceIn(0,2))*.32,0.0)
    fun key(id: Int)=if(id<6)"LEGACY" else ServiceTerminals.key(id)
    fun write(nbt: CompoundTag) { nbt.putInt("poleSelectedTerminal",selected) }
    fun read(nbt: CompoundTag) { selected=nbt.getInt("poleSelectedTerminal").takeIf { it in terminals } ?: 32 }
}

object PoleHeightUpgrade {
    /** Reserve the raised head in existing worlds, without replacing player blocks or loading chunks. */
    fun update(element: TransparentNodeElement): Boolean {
        val at=element.node?.coordinate ?: return false
        val world=at.world() as? ServerLevel ?: return false
        val descriptor=element.transparentNodeDescriptor
        val group=descriptor.getGhostGroupFront(element.front) ?: return true
        var complete=true
        for(cell in group.elementList) {
            val c=at.newWithOffset(cell.x,cell.y,cell.z)
            if(world.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)==null) { complete=false;continue }
            val state=world.getBlockState(c.pos)
            if(state.isAir && Eln.ghostManager.getGhost(c)==null)
                Eln.ghostManager.createGhost(c,at,descriptor.ghostGroupUuid,Eln.ghostBlock,if(cell.x!=0 || cell.z!=0)GhostBlock.tClearance else cell.meta)
        }
        // Old streetlight arm reservations no longer contain solid hardware after raising the head.
        for(x in -2..2)for(z in -1..1)if(x!=0 || z!=0)for(y in 1..7) {
            val rotated=element.front.rotateFromXN(net.minecraft.world.phys.Vec3(x.toDouble(),y.toDouble(),z.toDouble()))
            val c=at.newWithOffset(kotlin.math.round(rotated.x).toInt(),y,kotlin.math.round(rotated.z).toInt())
            if(world.chunkSource.getChunkNow(c.x shr 4,c.z shr 4)==null)continue
            val ghost=Eln.ghostManager.getGhost(c) ?: continue
            if(ghost.observatorCoordonate!=at || ghost.uUID!=descriptor.ghostGroupUuid)continue
            val state=world.getBlockState(c.pos)
            if(state.block===Eln.ghostBlock && state.getValue(GhostBlock.SHAPE)!=GhostBlock.tClearance)
                world.setBlock(c.pos,state.setValue(GhostBlock.SHAPE,GhostBlock.tClearance),3)
        }
        return complete
    }
}
object PoleFuseVisual {
    private val model by lazy { Eln.obj.getObj("distributionpole") }
    fun draw(front: Direction,closed: Boolean,blown: Boolean)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.34f,.35f,.35f);model.getPart("CutoutBase1")?.drawNoBind()
            GL11.glColor3f(.65f,.66f,.60f);model.getPart("CutoutInsulator1")?.drawNoBind()
            GL11.glTranslated(-.68,5.85,0.0);if(!closed)GL11.glRotated(125.0,0.0,0.0,1.0);GL11.glTranslated(.68,-5.85,0.0)
            GL11.glColor3f(if(blown).22f else .53f,.36f,.20f);model.getPart("FuseDoor1")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
    }
}
class LegacyPoleDisplay {
    var terminal=32;var fuseClosed=true;var fuseBlown=false;var rating=10_000;var voltage=3200
    fun write(s: DataOutputStream) { s.writeInt(terminal);s.writeBoolean(fuseClosed);s.writeBoolean(fuseBlown);s.writeInt(rating);s.writeInt(voltage) }
    fun read(s: DataInputStream) { terminal=s.readInt();fuseClosed=s.readBoolean();fuseBlown=s.readBoolean();rating=s.readInt();voltage=s.readInt() }
}
object PoleSecondaryVisual {
    private val model by lazy { Eln.obj.getObj("distributionpole") }
    fun draw(front: Direction)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.29f,.31f,.32f);model.getPart("SecondaryRack")?.drawNoBind()
            GL11.glColor3f(.54f,.65f,.67f);repeat(3) { model.getPart("Secondary$it")?.drawNoBind() }
        } finally { GL11.glPopAttrib() }
    }
}
