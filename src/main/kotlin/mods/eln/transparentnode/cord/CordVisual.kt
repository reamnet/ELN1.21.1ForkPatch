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
        val n=(ceil(length*6).toInt()).coerceIn(8,64)
        if(length<1e-6)return (0..n).map { start.lerp(end,it.toDouble()/n) }
        val path=(0..n).map { i ->
            if(i==0)return@map start
            if(i==n)return@map end
            val t=i.toDouble()/n
            val bow=sin(PI*t)*min(.35,length*.06)
            val x=start.x+dx*t+if(length>1e-6)-dz/length*bow else 0.0
            val z=start.z+dz*t+if(length>1e-6)dx/length*bow else 0.0
            Vec3(x,start.y+(end.y-start.y)*t-sin(PI*t)*1.5,z)
        }
        val support=path.mapIndexed { i,p ->
            if(i==0 || i==n)p.y else {
                val floor=ground(p.x,p.z,max(start.y,end.y))+.025
                if(floor.isFinite())max(floor,p.y) else p.y
            }
        }
        // An upper parabolic envelope anticipates steps and rounds the climb/drop.
        // Unlike averaging or a free spline it never dips below sampled obstacles.
        return path.mapIndexed { i,p ->
            if(i==0)start else if(i==n)end else {
                val y=support.indices.maxOf { j ->
                    val distance=(i-j)*length/n
                    support[j]-distance*distance/(2*.45)
                }
                Vec3(p.x,y,p.z)
            }
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
        return Double.NEGATIVE_INFINITY
    }
    fun draw(points: List<Vec3>,origin: Vec3,color: FloatArray?=null,radius: Double=.018) {
        if(points.size<2)return
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(color?.get(0) ?: .035f,color?.get(1) ?: .04f,color?.get(2) ?: .045f)
            val rings=rings(points,radius)
            GL11.glBegin(GL11.GL_QUADS)
            for(i in 1 until rings.size) {
                for(j in 0..3) {
                    val next=(j+1)%4
                    val a=rings[i-1][j];val b=rings[i-1][next];val c=rings[i][next];val d=rings[i][j]
                    val normal=b.subtract(a).cross(c.subtract(a)).normalize()
                    GL11.glNormal3d(normal.x,normal.y,normal.z)
                    for(k in listOf(a,b,c,d))GL11.glVertex3d(k.x-origin.x,k.y-origin.y,k.z-origin.z)
                }
            }
            GL11.glEnd()
        } finally { GL11.glPopAttrib() }
    }
    /** Shared rings and transported frames avoid disconnected elbows and vertical-frame flips. */
    fun rings(points: List<Vec3>,radius: Double): List<List<Vec3>> {
        var previous=Vec3.ZERO
        return points.mapIndexed { i,p ->
            val delta=points[min(i+1,points.lastIndex)].subtract(points[max(0,i-1)])
            val tangent=if(delta.lengthSqr()>1e-12)delta.normalize() else Vec3(0.0,1.0,0.0)
            var u=previous.subtract(tangent.scale(previous.dot(tangent)))
            if(u.lengthSqr()<1e-12)u=tangent.cross(if(abs(tangent.y)>.9)Vec3(1.0,0.0,0.0) else Vec3(0.0,1.0,0.0))
            if(u.lengthSqr()<1e-12)u=Vec3(1.0,0.0,0.0)
            u=u.normalize();previous=u
            val v=tangent.cross(u).normalize()
            listOf(p.add(u.scale(radius)),p.add(v.scale(radius)),p.subtract(u.scale(radius)),p.subtract(v.scale(radius)))
        }
    }
}

class CordDisplay {
    var endpoint: Pair<BlockPos,Direction>?=null
        private set
    var rating=CordRating.V120
        private set
    var socket=0;var horizontal=false;var strip=false;var recessed=false
    var state="UNPLUGGED"
        private set
    private var cached: List<Vec3> = emptyList()
    private var nextUpdate=0L
    private var mesh=0
    private var cachedStart: Vec3?=null
    private var cachedEnd: Vec3?=null
    fun destroy() { if(mesh!=0)GL11.glDeleteLists(mesh,1);mesh=0;cached=emptyList();nextUpdate=0L;cachedStart=null;cachedEnd=null }
    fun read(input: DataInputStream) {
        val next=if(input.readBoolean())BlockPos(input.readInt(),input.readInt(),input.readInt()) to Direction.fromInt(input.readUnsignedByte())!! else null
        if(next!=endpoint)destroy()
        if(next!=null) { rating=CordRating.entries[input.readUnsignedByte()];socket=input.readUnsignedByte();horizontal=input.readBoolean();strip=input.readBoolean();recessed=input.readBoolean() }
        endpoint=next;state=input.readUTF()
    }
    fun draw(world: Level,pos: BlockPos,front: Direction,entry: Vec3?=null) {
        val (target,facing)=endpoint ?: return
        val origin=Vec3.atCenterOf(pos)
        val outlet=(world.getBlockEntity(target) as? mods.eln.node.transparent.TransparentNodeEntity)?.elementRender as? OutletRender
        // No synthetic plug at a missing outlet. Unloaded endpoints resume when their renderer returns.
        if(outlet==null)return
        val turned=outlet.horizontal
        val start=Vec3.atCenterOf(target).add(mods.eln.transparentnode.worklight.WorkLightOptics.world(facing,OutletGeometry.tipLocal(socket,turned,strip,!outlet.wallState.isAir)))
        val end=origin.add(entry ?: mods.eln.transparentnode.worklight.WorkLightOptics.world(front,Vec3(0.0,-.43,-.25)))
        if(start.distanceTo(end)>ApplianceCord.MAX_REACH+1)return
        if(cached.isEmpty() || start!=cachedStart || end!=cachedEnd || world.gameTime>=nextUpdate) {
            nextUpdate=world.gameTime+20
            val points=CordPath.samples(start,end) { x,z,y -> CordPath.floor(world,x,z,y,pos) }
            if(points!=cached) {
                if(mesh!=0)GL11.glDeleteLists(mesh,1)
                mesh=GL11.glGenLists(1);GL11.glNewList(mesh,GL11.GL_COMPILE);CordPath.draw(points,origin);GL11.glEndList()
                cached=points
            }
            cachedStart=start;cachedEnd=end
        }
        if(((world.getBlockEntity(target) as? mods.eln.node.transparent.TransparentNodeEntity)?.elementRender as? OutletRender)?.occupied!=true)NemaPlug.draw(facing,rating,target.subtract(pos),socket,turned,strip,!outlet.wallState.isAir)
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try { GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glCallList(mesh) } finally { GL11.glPopAttrib() }
    }
}

/** Moulded body, finger rings, badge, strain relief and the mating metal blades. */
object NemaPlug {
    fun draw(front: Direction,rating: CordRating,offset: BlockPos=BlockPos.ZERO,socket: Int=0,horizontal: Boolean=false,strip: Boolean=false,recessed: Boolean=false)=preserveMatrix {
        GL11.glTranslated(offset.x.toDouble(),offset.y.toDouble(),offset.z.toDouble())
        if(recessed) { val move=mods.eln.transparentnode.worklight.WorkLightOptics.world(front,Vec3(0.0,0.0,1.0));GL11.glTranslated(move.x,move.y,move.z) }
        front.inverse.glRotateZnRef()
        if(horizontal)GL11.glRotatef(90f,0f,0f,1f)
        val shift=OutletGeometry.offset(socket,strip);GL11.glTranslated(shift.x,shift.y,shift.z)
        drawLocal(rating)
    }
    fun drawLocal(rating: CordRating)=preserveMatrix {
        val model=mods.eln.Eln.obj.getObj(if(rating.industrial)"industrialmachineplug" else if(rating==CordRating.V120)"nema520plug" else "nema620plug")
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor3f(.075f,.080f,.085f);model.getPart("Case")?.drawNoBind()
            GL11.glColor3f(.14f,.15f,.16f);model.getPart("Grip")?.drawNoBind()
            GL11.glColor3f(.73f,.61f,.31f);model.getPart("Blades")?.drawNoBind()
            val color=rating.color;GL11.glColor3f(color[0],color[1],color[2])
            model.getPart("Badge")?.drawNoBind()
        } finally { GL11.glPopAttrib() }
        Unit
    }
}
