package mods.eln.devtest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.client.gl.MeshNormals
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Matrix4f
import org.joml.Vector3f
import java.lang.reflect.Proxy
import kotlin.math.abs

/** Real client buffer emission: cached meshes must match the mathematical world/normal transforms. */
internal object RenderMeshChecks {
    fun verify() {
        val positions=ArrayList<Vector3f>();val normals=ArrayList<Vector3f>();val colors=ArrayList<List<Float>>()
        val vc=Proxy.newProxyInstance(VertexConsumer::class.java.classLoader,arrayOf(VertexConsumer::class.java)) { proxy,method,args ->
            when(method.name) {
                "addVertex"->if(args?.size==3)positions+=Vector3f((args[0] as Number).toFloat(),(args[1] as Number).toFloat(),(args[2] as Number).toFloat())
                "setNormal"->if(args?.size==3)normals+=Vector3f((args[0] as Number).toFloat(),(args[1] as Number).toFloat(),(args[2] as Number).toFloat())
                "setColor"->if(args?.size==4 && args[0] is Int)colors+=args.map { (it as Number).toFloat()/if(it is Int)255f else 1f }
            }
            if(method.isDefault)java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,*(args ?: emptyArray()))
            else if(method.returnType==VertexConsumer::class.java)proxy else null
        } as VertexConsumer
        val buffers=MultiBufferSource { vc }
        val points=listOf(Vector3f(0f,0f,0f),Vector3f(1f,0f,0f),Vector3f(1f,1f,0f),Vector3f(0f,1f,0f))
        val inputs=listOf(Vector3f(0f,0f,1f),Vector3f(0f,0f,1f),Vector3f(1f,0f,0f),Vector3f(0f,0f,1f))
        fun geometry() {
            GL11.glBegin(GL11.GL_QUADS)
            for(i in points.indices) { val p=points[i];val n=inputs[i];GL11.glNormal3f(n.x,n.y,n.z);GL11.glVertex3f(p.x,p.y,p.z) }
            GL11.glEnd()
        }
        val lists=IntArray(2)
        FixedFunction.begin(PoseStack(),buffers,0,OverlayTexture.NO_OVERLAY)
        try {
            for(i in lists.indices) {
                lists[i]=GL11.glGenLists(1);GL11.glNewList(lists[i],GL11.GL_COMPILE)
                GL11.glPushMatrix();if(i==1) { GL11.glTranslatef(.25f,.5f,-.25f);GL11.glRotatef(37f,0f,1f,0f);GL11.glScalef(.7f,1.3f,.5f) }
                geometry();GL11.glPopMatrix();GL11.glEndList()
            }
        } finally { FixedFunction.finish() }
        try {
            repeat(3) { frame ->
                val pose=PoseStack();pose.translate(20.0+frame,80.0,-32.0);pose.mulPose(org.joml.Quaternionf().rotateX(.37f+frame));pose.scale(2f,1f,.5f)
                for(i in lists.indices) {
                    positions.clear();normals.clear();colors.clear()
                    FixedFunction.begin(pose,buffers,0,OverlayTexture.NO_OVERLAY)
                    try { GL11.glColor3f(frame*.3f,.4f,.8f);GL11.glCallList(lists[i]) } finally { FixedFunction.finish() }
                    check(positions.size==4 && normals.size==4 && colors.size==4)
                    val local=if(i==0)null else Matrix4f().translate(.25f,.5f,-.25f).rotateY(Math.toRadians(37.0).toFloat()).scale(.7f,1.3f,.5f)
                    val model=Matrix4f(pose.last().pose());if(local!=null)model.mul(local)
                    val normalMatrix=MeshNormals.matrix(pose.last().normal(),local)
                    for(v in points.indices) {
                        check(positions[v].distance(model.transformPosition(Vector3f(points[v])))<.00003f)
                        check(normals[v].distance(MeshNormals.transform(normalMatrix,inputs[v].x,inputs[v].y,inputs[v].z))<.00001f)
                        check(abs(colors[v][0]-frame*.3f)<.005f && abs(colors[v][1]-.4f)<.005f && abs(colors[v][2]-.8f)<.005f)
                    }
                }
            }
        } finally { lists.forEach { GL11.glDeleteLists(it,1) } }
    }
}
