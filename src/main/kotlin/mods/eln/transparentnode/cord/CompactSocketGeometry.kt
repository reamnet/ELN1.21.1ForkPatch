package mods.eln.transparentnode.cord

import mods.eln.client.gl.GL11
import mods.eln.misc.Direction
import mods.eln.misc.preserveMatrix
import net.minecraft.world.phys.Vec3

/** Existing NEMA bodies meet four sockets on the front of the compact UPS. */
object CompactSocketGeometry {
    fun offset(socket: Int)=Vec3(if(socket%2==0)-.135 else .135,if(socket<2).02 else -.22,.72)
    fun tip(socket: Int)=Vec3(0.0,-.1,-.224).add(offset(socket))
    fun plug(front: Direction,rating: CordRating,socket: Int)=preserveMatrix {
        front.inverse.glRotateZnRef();val v=offset(socket);GL11.glTranslated(v.x,v.y,v.z);NemaPlug.drawLocal(rating)
    }
}
