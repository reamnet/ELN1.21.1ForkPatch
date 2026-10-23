package mods.eln.client.terrain

import mods.eln.client.gl.FixedFunction
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f

/** Direct primitives have no persistent display-list identity; retain exact content, not a lossy hash. */
class GeometrySignature(mode: Int,vertices: List<FloatArray>) {
    private val bits=IntArray(1+vertices.sumOf { it.size }).also { out ->
        out[0]=mode;var i=1;for(v in vertices)for(f in v)out[i++]=f.toRawBits()
    }
    override fun equals(other: Any?)=other is GeometrySignature && bits.contentEquals(other.bits)
    override fun hashCode()=bits.contentHashCode()
}
internal class DrawSignature(private val identity: Any,private val texture: ResourceLocation,private val flags: Int,
    private val color: Int,model: Matrix4f,normals: Matrix3f) {
    private val matrix=Matrix4f(model)
    private val normal=Matrix3f(normals)
    fun matches(identity:Any,texture:ResourceLocation,flags:Int,color:Int,model:Matrix4f,normals:Matrix3f)=
        this.identity==identity && this.texture==texture && this.flags==flags && this.color==color && matrix==model && normal==normals
    override fun equals(other:Any?)=other is DrawSignature && matches(other.identity,other.texture,other.flags,other.color,other.matrix,other.normal)
    override fun hashCode()=31*(31*(31*(31*(31*identity.hashCode()+texture.hashCode())+flags)+color)+matrix.hashCode())+normal.hashCode()
}
/** An unchanged tick reuses every command and the list itself: no per-part matrix/array allocation. */
internal class SignatureCapture(private val expected:List<DrawSignature> = emptyList()): FixedFunction.MeshSink {
    private var changed:ArrayList<DrawSignature>?=null
    private var index=0
    fun finish():List<DrawSignature> = changed ?: if(index==expected.size)expected else expected.take(index)
    override fun accepts()=true
    override fun batch(identity:Any,texture:ResourceLocation,unlit:Boolean,fullBright:Boolean,blend:Boolean,cull:Boolean,model:Matrix4f,normals:Matrix3f,color:Int):Boolean {
        val flags=(if(unlit)1 else 0)+(if(fullBright)2 else 0)+(if(blend)4 else 0)+(if(cull)8 else 0)
        val old=expected.getOrNull(index)
        if(old!=null && old.matches(identity,texture,flags,color,model,normals))changed?.add(old)
        else {
            if(changed==null)changed=ArrayList<DrawSignature>(expected.size+1).apply { addAll(expected.subList(0,index.coerceAtMost(expected.size))) }
            changed!!.add(DrawSignature(identity,texture,flags,color,model,normals))
        }
        index++;return false
    }
    override fun capture(texture:ResourceLocation,unlit:Boolean,fullBright:Boolean,blend:Boolean,cull:Boolean,lines:Boolean,
        vertices:MutableList<FloatArray>,model:Matrix4f,normals:Matrix3f,color:Int)=error("Signatures must not expand vertices")
}
