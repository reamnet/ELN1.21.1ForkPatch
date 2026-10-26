package mods.eln.gridnode

import net.minecraft.world.phys.Vec3
import kotlin.math.*

internal data class ServiceCableClearance(val center: Vec3,val radius: Double)

/** Immutable geometry only. A small visibility graph routes exceptional crossings around the
 * union of pole/can envelopes; clear spans keep their original catenary and sampling. */
internal object ServiceCablePath {
    private const val SIDES=24
    private const val EPS=1e-4
    private fun horizontal(a: Vec3,b: Vec3)=hypot(a.x-b.x,a.z-b.z)
    private fun distance(a: Vec3,b: Vec3,p: Vec3): Double {
        val dx=b.x-a.x;val dz=b.z-a.z;val squared=dx*dx+dz*dz
        val t=if(squared<1e-16)0.0 else (((p.x-a.x)*dx+(p.z-a.z)*dz)/squared).coerceIn(0.0,1.0)
        return hypot(a.x+dx*t-p.x,a.z+dz*t-p.z)
    }
    fun around(points: List<Vec3>,pole: Vec3?,radius: Double)=
        route(points,if(pole==null)emptyList() else listOf(ServiceCableClearance(pole,radius)),0.0)

    fun route(points: List<Vec3>,obstacles: List<ServiceCableClearance>,envelope: Double): List<Vec3> {
        if(obstacles.isEmpty())return points
        val circles=obstacles.map { it.copy(radius=it.radius+envelope) }
        fun clear(a: Vec3,b: Vec3)=circles.all { distance(a,b,it.center)>=it.radius-1e-7 }
        if(points.zipWithNext().all { (a,b)->clear(a,b) })return points
        val start=points.first();val end=points.last()
        // Invalid external endpoints cannot be moved away from their actual terminal.
        if(circles.any { horizontal(start,it.center)<it.radius-1e-7 || horizontal(end,it.center)<it.radius-1e-7 })return points
        val candidates=circles.flatMap { circle->
            val r=circle.radius/cos(PI/SIDES)+.001
            (0 until SIDES).map { i->val angle=i*2*PI/SIDES
                Vec3(circle.center.x+r*cos(angle),0.0,circle.center.z+r*sin(angle)) }
        }.filter { p->circles.all { horizontal(p,it.center)>=it.radius } }.sortedWith(compareBy({it.x},{it.z}))
        val nodes=listOf(start,end)+candidates
        val count=nodes.size
        val costs=DoubleArray(count) { Double.POSITIVE_INFINITY };costs[0]=0.0
        val previous=IntArray(count) { -1 };val visited=BooleanArray(count)
        for(step in 0 until count) {
            var next=-1
            for(i in 0 until count)if(!visited[i] && costs[i].isFinite() && (next<0 || costs[i]<costs[next]-EPS))next=i
            if(next<0)break
            if(next==1)break
            visited[next]=true
            for(i in 0 until count)if(!visited[i] && clear(nodes[next],nodes[i])) {
                val cost=costs[next]+horizontal(nodes[next],nodes[i])
                if(cost<costs[i]-EPS || abs(cost-costs[i])<=EPS && (previous[i]<0 || next<previous[i])) {
                    costs[i]=cost;previous[i]=next
                }
            }
        }
        if(previous[1]<0)return points
        val path=ArrayList<Vec3>();var at=1
        while(at>=0) { path+=nodes[at];at=previous[at] }
        path.reverse()
        val distances=DoubleArray(path.size)
        for(i in 1..path.lastIndex)distances[i]=distances[i-1]+horizontal(path[i-1],path[i])
        val total=distances.last()
        val sag=OverheadCableMesh.points(Vec3(0.0,start.y,0.0),Vec3(total,end.y,0.0),ceil(total*3).toInt().coerceIn(16,512))
        val output=ArrayList<Vec3>()
        // Include every visibility-graph corner, so resampling cannot cut through an envelope.
        for(i in 0 until path.lastIndex) {
            val n=ceil(horizontal(path[i],path[i+1])*3).toInt().coerceAtLeast(1)
            for(k in 0 until n) {
                val t=k.toDouble()/n
                val along=distances[i]+(distances[i+1]-distances[i])*t
                val s=(along/total*sag.lastIndex).coerceIn(0.0,sag.lastIndex.toDouble())
                val low=s.toInt().coerceAtMost(sag.lastIndex-1);val fraction=s-low
                val p=path[i].add(path[i+1].subtract(path[i]).scale(t))
                output+=Vec3(p.x,sag[low].y+(sag[low+1].y-sag[low].y)*fraction,p.z)
            }
        }
        output[0]=start;output+=end
        return output
    }
}
