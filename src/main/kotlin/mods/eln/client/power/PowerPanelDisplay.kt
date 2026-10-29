package mods.eln.client.power

import mods.eln.client.gl.GL11
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.UtilsClient
import mods.eln.misc.preserveMatrix

/** Small live seven-segment readouts; enclosure, bezel and labels stay in terrain buffers. */
object PowerPanelDisplay {
    val PARTS=setOf("Indicator","MainsLed","BackupLed","ChargeLed","FaultLed","BypassLed")
    private val digits=intArrayOf(0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f)
    fun number(value: Double,x: Double,y: Double,z: Double,count: Int=4,height: Double=.065)=TerrainModels.dynamic {
        preserveMatrix {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);UtilsClient.disableLight()
                GL11.glColor3f(.24f,.92f,.74f)
                val n=if(value.isFinite())value.toInt().coerceIn(0,9999) else 0
                val text=n.toString().padStart(count,' ').takeLast(count)
                val w=height*.5;val t=height*.09
                fun rect(a: Double,b: Double,c: Double,d: Double) {
                    GL11.glVertex3d(a,b,z);GL11.glVertex3d(c,b,z);GL11.glVertex3d(c,d,z);GL11.glVertex3d(a,d,z)
                }
                GL11.glBegin(GL11.GL_QUADS);GL11.glNormal3f(0f,0f,1f)
                text.forEachIndexed { i,c -> if(c!=' ') {
                    val mask=digits[c-'0'];val left=x+i*w*1.35
                    val bars=arrayOf(doubleArrayOf(t,height-t,w-t,height),doubleArrayOf(w-t,height/2+t/2,w,height-t),
                        doubleArrayOf(w-t,t,w,height/2-t/2),doubleArrayOf(t,0.0,w-t,t),doubleArrayOf(0.0,t,t,height/2-t/2),
                        doubleArrayOf(0.0,height/2+t/2,t,height-t),doubleArrayOf(t,height/2-t/2,w-t,height/2+t/2))
                    bars.forEachIndexed { j,b -> if(mask and (1 shl j)!=0)rect(left+b[0],y+b[1],left+b[2],y+b[3]) }
                } }
                GL11.glEnd()
            } finally { UtilsClient.enableLight();GL11.glPopAttrib() }
        }
    }
    fun lamp(model: mods.eln.misc.Obj3D,name: String,on: Boolean,r: Float,g: Float,b: Float)=TerrainModels.dynamic {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            if(on)UtilsClient.disableLight()
            GL11.glColor3f(if(on)r else r*.15f,if(on)g else g*.15f,if(on)b else b*.15f)
            model.getPart(name)?.drawNoBind()
        } finally { if(on)UtilsClient.enableLight();GL11.glPopAttrib() }
    }
}
