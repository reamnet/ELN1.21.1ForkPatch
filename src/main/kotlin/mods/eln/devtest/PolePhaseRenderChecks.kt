package mods.eln.devtest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.VertexColors
import mods.eln.misc.Direction
import mods.eln.utility.DistributionKind
import mods.eln.utility.DistributionPoleDescriptor
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Vector3f
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/** Check actual insulator meshes, not just the endpoint calculation, under opposite placement. */
internal object PolePhaseRenderChecks {
    fun verify() {
        val pins=ArrayList<Vector3f>()
        var position=Vector3f()
        val pinColor=VertexColors.pack(.57f,.66f,.68f,1f)
        val consumer=Proxy.newProxyInstance(VertexConsumer::class.java.classLoader,arrayOf(VertexConsumer::class.java)) { proxy,method,args ->
            if(method.name=="addVertex" && args?.size==3)
                position=Vector3f((args[0] as Number).toFloat(),(args[1] as Number).toFloat(),(args[2] as Number).toFloat())
            if(method.name=="setColor" && args?.size==4 && args[0] is Int) {
                val color=((args[3] as Int) shl 24) or ((args[0] as Int) shl 16) or ((args[1] as Int) shl 8) or (args[2] as Int)
                if(color==pinColor)pins.add(Vector3f(position))
            }
            if(method.isDefault)InvocationHandler.invokeDefault(proxy,method,*(args ?: emptyArray()))
            else if(method.returnType==VertexConsumer::class.java)proxy else null
        } as VertexConsumer
        val buffers=MultiBufferSource { consumer }
        for(kind in listOf(DistributionKind.THREE,DistributionKind.STREET_THREE,DistributionKind.FEEDER_THREE)) {
            val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind }
            for(angle in listOf(0f,45f,-90f,-135f)) {
                fun emit(front: Direction): List<Vector3f> {
                    pins.clear()
                    FixedFunction.begin(PoseStack(),buffers,0x00e000c0,0)
                    try { descriptor.drawEquipment(front,hvAngle=angle,lvAngle=0f) } finally { FixedFunction.finish() }
                    check(pins.isNotEmpty());return pins.toList()
                }
                val expected=emit(Direction.XN)
                for(front in listOf(Direction.XP,Direction.ZN,Direction.ZP)) {
                    val actual=emit(front)
                    check(actual.size==expected.size)
                    check(actual.indices.all { actual[it].distance(expected[it])<.00003f }) {
                        "Primary insulators changed phase positions: $kind $front $angle"
                    }
                }
            }
        }
    }
}
