package mods.eln.devtest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.six.SixNodeEntity
import mods.eln.sixnode.electricalcable.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import java.io.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/** Real exposed cable geometry must stay live after camera changes and topology/palette packets. */
internal object UtilityCableMeshChecks {
    fun verify() {
        val events=ArrayList<Pair<String,List<Number>>>()
        val consumer=Proxy.newProxyInstance(VertexConsumer::class.java.classLoader,arrayOf(VertexConsumer::class.java)) { proxy,method,args ->
            val record=when(method.name) {
                "addVertex","setNormal"->args?.size==3
                "setUv"->args?.size==2
                "setColor"->args?.size==4 && args[0] is Int
                "setLight","setOverlay"->args?.size==1
                else->false
            }
            if(record)events.add(method.name to args!!.map { it as Number })
            if(method.isDefault)InvocationHandler.invokeDefault(proxy,method,*(args ?: emptyArray()))
            else if(method.returnType==VertexConsumer::class.java)proxy else null
        } as VertexConsumer
        val descriptors=UtilityCableDescriptor.allDescriptors().filter { !it.melted && it.material==UtilityCableMaterial.COPPER && it.insulated &&
            (it.sizeLabel=="2 AWG" && it.insulationVoltageRating==40000.0 || it.sizeLabel=="14/2") }
        check(descriptors.size>=2)
        val buffers=MultiBufferSource { consumer }
        for(descriptor in descriptors)for(side in listOf(Direction.YN,Direction.ZP)) {
            val entity=SixNodeEntity(BlockPos(0,80,0),Eln.sixNodeBlock.defaultBlockState()).apply { level=Minecraft.getInstance().level }
            val render=UtilityCableRender(entity,side,descriptor).apply { front=LRDU.Left;connectedSide[LRDU.Left]=true }
            fun snapshot(frame: Int): List<Pair<String,List<Number>>> {
                events.clear()
                val pose=PoseStack().apply { translate(17.0+frame,80.0,-32.0);mulPose(org.joml.Quaternionf().rotateXYZ(.2f,.31f*frame,-.17f));scale(.7f,1.3f,.9f) }
                FixedFunction.begin(pose,buffers,0x00e000c0,0)
                try { render.draw() } finally { FixedFunction.finish() }
                return events.toList()
            }
            fun same(a: List<Pair<String,List<Number>>>,b: List<Pair<String,List<Number>>>) {
                check(a.isNotEmpty() && a.size==b.size)
                for(i in a.indices) {
                    check(a[i].first==b[i].first)
                    for(j in a[i].second.indices)check(kotlin.math.abs(a[i].second[j].toDouble()-b[i].second[j].toDouble())<.00003) {
                        "Cached cable differs: ${descriptor.name} $side ${a[i]} / ${b[i]}"
                    }
                }
            }
            try {
                snapshot(1);check(render.cableListReady[0])
                val cached=snapshot(2)
                render.cableListReady[0]=false
                same(cached,snapshot(2))
                val bytes=ByteArrayOutputStream()
                DataOutputStream(bytes).use { out ->
                    out.writeByte(1 shl LRDU.Up.dir);out.writeBoolean(false)
                    out.writeByte(12 shl 4);out.writeByte(if(descriptor.colorPalettes.size>1)1 else 0);out.writeFloat(20f)
                }
                render.publishUnserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
                check(!render.cableListReady[0])
                val changed=snapshot(2);check(changed!=cached)
                render.cableListReady[0]=false;same(changed,snapshot(2))
            } finally { render.destructor() }
        }
    }
}
