package mods.eln.utility

import mods.eln.client.gl.GL11
import net.minecraft.world.phys.Vec3

/** Short equipment leads; their endpoints follow the independently turning racks. */
object PoleLeadVisual {
    fun draw(a: Vec3,b: Vec3) {
        val axis=b.subtract(a).normalize()
        if(axis.lengthSqr()<1e-12)return
        val right=(if(kotlin.math.abs(axis.y)>.9)Vec3(1.0,0.0,0.0) else axis.cross(Vec3(0.0,1.0,0.0))).normalize().scale(.012)
        val up=axis.cross(right).normalize().scale(.012)
        val corners=listOf(right.add(up),right.subtract(up),right.add(up).scale(-1.0),up.subtract(right))
        GL11.glColor3f(.12f,.14f,.15f);GL11.glBegin(GL11.GL_QUADS)
        for(i in 0..3) {
            val n=(i+1)%4
            val normal=corners[i].add(corners[n]).normalize()
            GL11.glNormal3f(normal.x.toFloat(),normal.y.toFloat(),normal.z.toFloat())
            for(p in listOf(a.add(corners[i]),a.add(corners[n]),b.add(corners[n]),b.add(corners[i])))
                GL11.glVertex3d(p.x,p.y,p.z)
        }
        GL11.glEnd()
    }
}
