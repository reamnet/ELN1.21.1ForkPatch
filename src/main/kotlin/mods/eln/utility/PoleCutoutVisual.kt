package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11.*
import mods.eln.misc.preserveMatrix
import net.minecraft.world.phys.Vec3
import kotlin.math.*

internal object PoleCutoutPose {
    const val OPEN_ANGLE=125.0
    val hinge=Vec3(-.83,5.85,0.0)
    fun top(closed: Boolean): Vec3 {
        val angle=Math.toRadians(if(closed)0.0 else OPEN_ANGLE)
        return hinge.add(-.55*sin(angle),.55*cos(angle),0.0)
    }
}

/** Fixed porcelain/contact holder and a separate hinged fiberglass fuse tube. Both poses bake. */
internal object PoleCutoutVisual {
    private val model by lazy { Eln.obj.getObj("distributionpole") }
    private var holderList=0
    private val carriers=HashMap<Boolean,Int>()
    private fun compile(draw: ()->Unit): Int {
        val id=glGenLists(1);glNewList(id,GL_COMPILE);draw();glEndList();return id
    }
    private fun tube(a: Vec3,b: Vec3,r: Double,color: Triple<Float,Float,Float>) {
        val axis=b.subtract(a).normalize()
        val right=(if(abs(axis.y)>.9)Vec3(1.0,0.0,0.0) else axis.cross(Vec3(0.0,1.0,0.0))).normalize()
        val up=axis.cross(right).normalize()
        val ring=(0 until 12).map { i->val angle=i*PI/6;right.scale(cos(angle)*r).add(up.scale(sin(angle)*r)) }
        glColor3f(color.first,color.second,color.third);glBegin(GL_QUADS)
        for(i in ring.indices) {
            val j=(i+1)%ring.size;val n=ring[i].add(ring[j]).normalize()
            glNormal3d(n.x,n.y,n.z)
            for(p in listOf(a.add(ring[i]),a.add(ring[j]),b.add(ring[j]),b.add(ring[i])))glVertex3d(p.x,p.y,p.z)
        }
        for((end,sign)in listOf(a to -1.0,b to 1.0)) {
            glNormal3d(axis.x*sign,axis.y*sign,axis.z*sign)
            for(i in ring.indices)for(p in listOf(end,end.add(ring[i]),end.add(ring[(i+1)%ring.size]),end))glVertex3d(p.x,p.y,p.z)
        }
        glEnd()
    }
    fun draw(visual: Int,closed: Boolean,blown: Boolean) {
        val z=(visual-1)*.95
        glColor3f(.32f,.34f,.35f);model.getPart("CutoutBase$visual")?.drawNoBind()
        glColor3f(.68f,.68f,.61f);model.getPart("CutoutInsulator$visual")?.drawNoBind()
        val metal=Triple(.63f,.52f,.31f)
        val x=PoleCutoutPose.hinge.x
        if(holderList==0)holderList=compile {
            tube(Vec3(-.44,6.40,0.0),Vec3(x,6.40,0.0),.028,metal)
            tube(Vec3(-.43,5.85,0.0),Vec3(x,5.85,0.0),.030,metal)
            // A forked upper jaw and lower hinge remain on the holder when the tube drops out.
            for(side in listOf(-1,1))tube(Vec3(x,6.34,side*.045),Vec3(x,6.45,side*.045),.016,metal)
            tube(Vec3(x,5.85,-.065),Vec3(x,5.85,.065),.043,metal)
        }
        preserveMatrix { glTranslated(0.0,0.0,z);glCallList(holderList) }
        preserveMatrix {
            glTranslated(x,5.85,z)
            if(!closed)glRotated(PoleCutoutPose.OPEN_ANGLE,0.0,0.0,1.0)
            val carrier=carriers.getOrPut(blown) { compile {
                tube(Vec3.ZERO,Vec3(0.0,.08,0.0),.033,metal)
                tube(Vec3(0.0,.08,0.0),Vec3(0.0,.47,0.0),.028,if(blown)Triple(.25f,.26f,.24f) else Triple(.86f,.86f,.77f))
                tube(Vec3(0.0,.47,0.0),Vec3(0.0,.55,0.0),.034,metal)
                // Operating eye is part of the moving carrier, rather than the fixed holder.
                for(i in 0 until 12) {
                    val a=i*PI/6;val b=(i+1)*PI/6
                    tube(Vec3(-.055+.035*cos(a),.48+.035*sin(a),0.0),Vec3(-.055+.035*cos(b),.48+.035*sin(b),0.0),.008,metal)
                }
            } }
            glCallList(carrier)
        }
    }
}
