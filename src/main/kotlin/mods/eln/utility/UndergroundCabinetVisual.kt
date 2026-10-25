package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.misc.*
import mods.eln.transparentnode.utilitylight.StreetLightMount

object UndergroundCabinetVisual {
    fun model(kind: DistributionKind)=if(kind.pedestal)"undergroundservicepedestal" else "undergroundfused${kind.phases}"+if(kind.light)"light" else ""
    fun draw(kind: DistributionKind,front: Direction,display: DistributionDisplay?)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            val mesh=Eln.obj.getObj(model(kind))
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            fun part(name: String,r: Float,g: Float,b: Float) { GL11.glColor3f(r,g,b);mesh.getPart(name)?.drawNoBind() }
            part("Case",.22f,.34f,.28f);part("Trim",.45f,.48f,.46f);part("Services",.72f,.68f,.35f)
            repeat(kind.phases) { i->
                part("Fuse$i",.70f,.73f,.65f)
                preserveMatrix {
                    GL11.glTranslated(-.36,.35,(i-(kind.phases-1)/2.0)*.26)
                    if(display?.fuseClosed?.getOrNull(i)==false)GL11.glRotated(100.0,0.0,0.0,1.0)
                    GL11.glTranslated(.36,-.35,-(i-(kind.phases-1)/2.0)*.26)
                    part("Door$i",.52f,.36f,.22f)
                }
            }
            if(kind.light) {
                part("Pole",.43f,.46f,.48f)
                GL11.glTranslated(0.0,StreetLightMount.offset(display?.heightStep ?: 0),0.0)
                GL11.glRotated((display?.yaw ?: 0).toDouble(),0.0,1.0,0.0);part("LampBracket",.43f,.46f,.48f)
                val p=StreetLightMount.pivot
                GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated((display?.pitch ?: 0).toDouble(),0.0,0.0,1.0);GL11.glTranslated(-p.x,-p.y,-p.z)
                part("LampHousing",.22f,.25f,.27f)
                if((display?.light ?: 0)>0)UtilsClient.disableLight()
                part("LampLens",if((display?.light ?: 0)>0)1f else .15f,if((display?.light ?: 0)>0).94f else .18f,if((display?.light ?: 0)>0).75f else .20f)
                if((display?.light ?: 0)>0)UtilsClient.enableLight()
            }
        } finally { GL11.glPopAttrib() }
    }
}
