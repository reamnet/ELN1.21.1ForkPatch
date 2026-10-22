package mods.eln.client.gl

/** Identical to VertexConsumer's float-to-byte color conversion, packed once rather than per frame. */
object VertexColors {
    @JvmStatic fun pack(r: Float,g: Float,b: Float,a: Float): Int =
        (((a*255f).toInt() and 255) shl 24) or (((r*255f).toInt() and 255) shl 16) or
        (((g*255f).toInt() and 255) shl 8) or ((b*255f).toInt() and 255)
    @JvmStatic fun pack(vertex: FloatArray)=pack(vertex[8],vertex[9],vertex[10],vertex[11])
}
