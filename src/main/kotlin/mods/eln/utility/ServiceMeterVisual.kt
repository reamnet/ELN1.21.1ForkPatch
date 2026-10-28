package mods.eln.utility

import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.client.terrain.TerrainModels
import mods.eln.misc.*
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import kotlin.math.pow

/** Static printed plates are baked; only pointers, disk, glass and live LCD glyphs remain dynamic. */
object ServiceMeterVisual {
    private fun plate(name: String,x: Double,y: Double,w: Double,h: Double,z: Double) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glColor4f(1f,1f,1f,1f)
        FixedFunction.bindTexture(ResourceLocation.fromNamespaceAndPath("eln","model/northamericanmeterbase/$name.png"))
        GL11.glBegin(GL11.GL_QUADS);GL11.glNormal3f(0f,0f,1f)
        GL11.glTexCoord2f(0f,1f);GL11.glVertex3d(x,y,z)
        GL11.glTexCoord2f(1f,1f);GL11.glVertex3d(x+w,y,z)
        GL11.glTexCoord2f(1f,0f);GL11.glVertex3d(x+w,y+h,z)
        GL11.glTexCoord2f(0f,0f);GL11.glVertex3d(x,y+h,z);GL11.glEnd()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }
    private fun digits(value: Double) {
        val text=(value.coerceAtLeast(0.0)*100).toLong().coerceAtMost(9_999_999).toString().padStart(7,'0')
        val masks=intArrayOf(0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f)
        fun rect(x: Double,y: Double,w: Double,h: Double) {
            GL11.glBegin(GL11.GL_QUADS);GL11.glNormal3f(0f,0f,1f)
            GL11.glVertex3d(x,y,-.116);GL11.glVertex3d(x+w,y,-.116);GL11.glVertex3d(x+w,y+h,-.116);GL11.glVertex3d(x,y+h,-.116);GL11.glEnd()
        }
        text.forEachIndexed { i,c->
            val x=-.137+i*.039;val y=.034;val mask=masks[c-'0'];val w=.027;val h=.065;val t=.003
            val seg=arrayOf(doubleArrayOf(x,y+h-t,w,t),doubleArrayOf(x+w-t,y+h/2,t,h/2),doubleArrayOf(x+w-t,y,t,h/2),doubleArrayOf(x,y,w,t),doubleArrayOf(x,y,t,h/2),doubleArrayOf(x,y+h/2,t,h/2),doubleArrayOf(x,y+h/2-t/2,w,t))
            repeat(7) { k->if(mask and (1 shl k)!=0)seg[k].let { rect(it[0],it[1],it[2],it[3]) } }
            if(i==4)rect(x+w+.002,y,.003,.003)
        }
    }
    fun draw(model: Obj3D,type: Int,imported: Double,exported: Double,watts: Double,volts: Double,open: Boolean,powered: Boolean,disk: Double) {
        fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);model.getPart(name)?.drawNoBind() }
        part("MeterRing",.67f,.70f,.70f);part("MeterFace",.89f,.89f,.83f);part("Seal",.81f,.65f,.23f)
        if(type==1) {
            plate("analog_face",-.195,-.145,.39,.39,-.129)
            repeat(5) { i->
                part("Dial$i",.85f,.85f,.79f)
                val x=-.132+i*.066
                preserveMatrix {
                    GL11.glTranslated(x,.05,-.12);GL11.glRotated((imported/10.0.pow(4-i)%10)*36*(if(i%2==0)-1 else 1),0.0,0.0,1.0);GL11.glTranslated(-x,-.05,.12)
                    part("Needle$i",.10f,.11f,.10f)
                }
            }
            preserveMatrix {
                GL11.glTranslated(0.0,-.075,-.20);GL11.glRotated(disk,0.0,1.0,0.0);GL11.glTranslated(0.0,.075,.20)
                part("Disk",.71f,.72f,.70f);part("DiskMark",.10f,.11f,.10f)
            }
        } else {
            plate("smart_face",-.195,-.145,.39,.39,-.129)
            part("SmartScreen",.42f,.49f,.37f)
            TerrainModels.dynamic {
                if(powered) {
                    val page=((Minecraft.getInstance().level?.gameTime ?: 0)/100%4).toInt()
                    val value=when(page) { 1->exported;2->kotlin.math.abs(watts)/1000;3->kotlin.math.abs(volts);else->imported }
                    GL11.glColor3f(.07f,.12f,.08f);digits(value)
                    plate("lcd_page_$page",-.135,.137,.27,.028,-.115)
                    plate(if(open)"lcd_open" else "lcd_closed",-.118,-.032,.236,.024,-.115)
                }
            }
            part("Disconnect",if(open).65f else .12f,if(open).09f else .40f,.09f)
        }
        TerrainModels.dynamic {
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false)
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glColor4f(.82f,.90f,.94f,.075f)
            model.getPart("Glass")?.drawNoBind()
        }
    }
}
