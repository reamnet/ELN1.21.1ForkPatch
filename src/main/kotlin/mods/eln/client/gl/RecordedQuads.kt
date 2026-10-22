package mods.eln.client.gl

/** Join only consecutive flat triangle pairs with identical shared vertex attributes. */
object RecordedQuads {
    @JvmStatic fun triangles(input: List<FloatArray>): List<FloatArray> {
        val output=ArrayList<FloatArray>(input.size+4)
        var i=0
        while(i+2<input.size) {
            val a=input[i];val b=input[i+1];val c=input[i+2]
            if(i+5<input.size && mergeable(a,b,c,input[i+3],input[i+4],input[i+5])) {
                output.add(a);output.add(b);output.add(c);output.add(input[i+5]);i+=6
            } else { output.add(a);output.add(b);output.add(c);output.add(c);i+=3 }
        }
        return output
    }
    private fun mergeable(a: FloatArray,b: FloatArray,c: FloatArray,aa: FloatArray,cc: FloatArray,d: FloatArray): Boolean {
        // UV seams, hard normals and explicit/inherited tint differences must keep their own vertices.
        if(!sameVertex(a,aa) || !sameVertex(c,cc))return false
        if(listOf(b,c,d).any { v -> (5..7).any { v[it]!=a[it] } })return false
        val ab=DoubleArray(3) { b[it].toDouble()-a[it] };val ac=DoubleArray(3) { c[it].toDouble()-a[it] };val ad=DoubleArray(3) { d[it].toDouble()-a[it] }
        fun cross(x: DoubleArray,y: DoubleArray)=doubleArrayOf(x[1]*y[2]-x[2]*y[1],x[2]*y[0]-x[0]*y[2],x[0]*y[1]-x[1]*y[0])
        fun dot(x: DoubleArray,y: DoubleArray)=(0..2).sumOf { x[it]*y[it] }
        val normal=cross(ab,ac);val area=dot(normal,normal)
        if(!area.isFinite() || area<=1e-24)return false
        // Opposite sides of the shared diagonal: reject overlapping or zero-area faces.
        val second=cross(ac,ad)
        if(dot(normal,second)<=0.0)return false
        val distance=dot(normal,ad)
        val scale=maxOf(dot(ab,ab),dot(ac,ac),dot(ad,ad))
        return distance*distance<=area*scale*1e-12
    }
    private fun sameVertex(a: FloatArray,b: FloatArray): Boolean {
        // OBJ cross products can produce -0 on one triangle and +0 on its neighbour.
        // Those are the same attribute, including when Minecraft packs the normal;
        // FloatArray.contentEquals uses bit equality and needlessly splits these faces.
        return a.indices.all { a[it]==b[it] || (a[it].isNaN() && b[it].isNaN()) }
    }
}
