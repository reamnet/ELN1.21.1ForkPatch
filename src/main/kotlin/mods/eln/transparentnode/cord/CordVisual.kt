package mods.eln.transparentnode.cord

import mods.eln.client.gl.GL11
import mods.eln.misc.*
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import kotlin.math.*

/** A loose, terrain-following visual; no blocks are placed for the cord itself. */
object CordPath {
    fun samples(start: Vec3,end: Vec3,ground: (Double,Double,Double)->Double): List<Vec3> {
        val dx=end.x-start.x;val dz=end.z-start.z;val length=hypot(dx,dz)
        if(!length.isFinite() || !start.y.isFinite() || !end.y.isFinite())return emptyList()
        val n=(ceil(length*4).toInt()).coerceIn(8,48)
        return (0..n).map { i ->
            if(i==0)return@map start
            if(i==n)return@map end
            val t=i.toDouble()/n
            val bow=sin(PI*t)*min(.35,length*.06)
            val x=start.x+dx*t+if(length>1e-6)-dz/length*bow else 0.0
            val z=start.z+dz*t+if(length>1e-6)dx/length*bow else 0.0
            val y=if(i==0)start.y else if(i==n)end.y else {
                val floor=ground(x,z,max(start.y,end.y))+.025
                max(floor,start.y+(end.y-start.y)*t-sin(PI*t)*1.5)
            }
            Vec3(x,y,z)
        }
    }
    fun floor(world: Level,x: Double,z: Double,ceiling: Double,ignore: BlockPos?=null): Double {
        val start=floor(ceiling).toInt()+1
        for(y in start downTo start-5) {
            val p=BlockPos.containing(x,y.toDouble(),z)
            if(p==ignore)continue
            val boxes=world.getBlockState(p).getCollisionShape(world,p).toAabbs()
            val top=boxes.filter { x-p.x>=it.minX && x-p.x<=it.maxX && z-p.z>=it.minZ && z-p.z<=it.maxZ }.maxOfOrNull { it.maxY }
            if(top!=null && y+top<=ceiling+.6)return y+top
        }
        return ceiling-1.0
    }
    fun draw(points: List<Vec3>,origin: Vec3) {
        if(points.size<2)return
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.035f,.04f,.045f)
            GL11.glBegin(GL11.GL_QUADS)
            for(i in 1 until points.size) {
                val a=points[i-1].subtract(origin);val b=points[i].subtract(origin)
                val direction=b.subtract(a).normalize()
                val u=direction.cross(if(abs(direction.y)>.9)Vec3(1.0,0.0,0.0) else Vec3(0.0,1.0,0.0)).normalize().scale(.018)
                val v=direction.cross(u).normalize().scale(.018)
                for(j in 0..3) {
                    val p=u.scale(cos(j*PI/2)).add(v.scale(sin(j*PI/2)))
                    val q=u.scale(cos((j+1)*PI/2)).add(v.scale(sin((j+1)*PI/2)))
                    val n=p.add(q).normalize();GL11.glNormal3d(n.x,n.y,n.z)
                    for(k in listOf(a.add(p),a.add(q),b.add(q),b.add(p)))GL11.glVertex3d(k.x,k.y,k.z)
                }
            }
            GL11.glEnd()
        } finally { GL11.glPopAttrib() }
    }
}

class CordDisplay {
    var endpoint: Pair<BlockPos,Direction>?=null
        private set
    var rating=CordRating.V120
        private set
    var state="UNPLUGGED"
        private set
    private var cached: List<Vec3> = emptyList()
    private var nextUpdate=0L
    fun read(input: DataInputStream) {
        val next=if(input.readBoolean())BlockPos(input.readInt(),input.readInt(),input.readInt()) to Direction.fromInt(input.readUnsignedByte())!! else null
        if(next!=endpoint) { cached=emptyList();nextUpdate=0L }
        if(next!=null)rating=CordRating.entries[input.readUnsignedByte()]
        endpoint=next;state=input.readUTF()
    }
    fun draw(world: Level,pos: BlockPos,front: Direction,entry: Vec3?=null) {
        val (target,facing)=endpoint ?: return
        val origin=Vec3.atCenterOf(pos)
        val start=Vec3.atCenterOf(target).add(mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,Vec3(0.0,-.1,-.224)))
        val end=origin.add(entry ?: mods.eln.transparentnode.worklight.WorkLightOptics.world(front,Vec3(0.0,-.43,-.25)))
        if(start.distanceTo(end)>ApplianceCord.MAX_REACH+1)return
        if(cached.isEmpty() || world.gameTime>=nextUpdate) {
            nextUpdate=world.gameTime+20;cached=CordPath.samples(start,end) { x,z,y -> CordPath.floor(world,x,z,y,pos) }
        }
        if(((world.getBlockEntity(target) as? mods.eln.node.transparent.TransparentNodeEntity)?.elementRender as? OutletRender)?.occupied!=true)NemaPlug.draw(facing,rating,target.subtract(pos))
        CordPath.draw(cached,origin)
    }
}

/** Moulded body, finger rings, badge, strain relief and the mating metal blades. */
object NemaPlug {
    fun draw(front: Direction,rating: CordRating,offset: BlockPos=BlockPos.ZERO)=preserveMatrix {
        GL11.glTranslated(offset.x.toDouble(),offset.y.toDouble(),offset.z.toDouble())
        front.inverse.glRotateZnRef()
        drawLocal(rating)
    }
    fun drawLocal(rating: CordRating)=preserveMatrix {
        val model=mods.eln.Eln.obj.getObj(if(rating==CordRating.V120)"nema520plug" else "nema620plug")
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.075f,.080f,.085f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.14f,.15f,.16f);model.getPart("Grip")?.drawNoBind()
            GL11.glColor3f(.73f,.61f,.31f);model.getPart("Blades")?.drawNoBind()
            if(rating==CordRating.V120)GL11.glColor3f(.22f,.52f,.80f) else GL11.glColor3f(.85f,.48f,.10f)
            model.getPart("Badge")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
        Unit
    }
}
