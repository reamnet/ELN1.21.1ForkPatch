package mods.eln.utility

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.misc.preserveMatrix

internal object PoleTransformerVisual {
    private val model by lazy { Eln.obj.getObj("distributionpole") }
    private data class LeadKey(val watts: Int,val high: Float,val low: Float,val reach: Double)
    private val leads=object: LinkedHashMap<LeadKey,List<List<net.minecraft.world.phys.Vec3>>>(128,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LeadKey,List<List<net.minecraft.world.phys.Vec3>>>?)=size>256
    }
    fun secondaryLeads(watts: Int,highAngle: Float,lowAngle: Float,reach: Double) {
        val paths=leads.getOrPut(LeadKey(watts,highAngle,lowAngle,reach)) {
            val shape=PoleTransformerShape.of(watts)
            (0..2).map { i->
                val target=mods.eln.gridnode.OverheadSpanMesh.terminal(mods.eln.gridnode.ServiceCableMount.center(reach),i,.025)
                    .yRot(Math.toRadians(lowAngle.toDouble()).toFloat())
                shape.secondaryLead(i,highAngle,target)
            }
        }
        paths.forEach { it.zipWithNext().forEach { (a,b)->PoleLeadVisual.draw(a,b) } }
    }
    fun draw(watts: Int) {
        val shape=PoleTransformerShape.of(watts)
        preserveMatrix {
            GL11.glTranslated(PoleTransformerShape.BACK,PoleTransformerShape.TOP,0.0)
            GL11.glScaled(shape.diameter,shape.height,shape.diameter)
            GL11.glTranslated(.015,-PoleTransformerShape.TOP,0.0)
            GL11.glColor3f(.53f,.55f,.54f);model.getPart("Transformer")?.drawNoBind()
        }
        preserveMatrix {
            GL11.glTranslated(shape.bushing.x+.33,0.0,0.0)
            GL11.glColor3f(.32f,.43f,.44f);model.getPart("TransformerPin")?.drawNoBind()
        }
    }
}
