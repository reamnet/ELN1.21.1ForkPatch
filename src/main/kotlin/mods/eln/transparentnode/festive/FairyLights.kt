package mods.eln.transparentnode.festive

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import mods.eln.transparentnode.cord.CordPath
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.*
import net.minecraft.world.phys.*
import java.io.*
import kotlin.math.*

/** Suspended wire, distinct from the terrain-following power lead. Both ends are exact anchors. */
object FairyLightPath {
    fun bulbPoints(points: List<Vec3>,large: Boolean): List<Vec3> {
        if(!large || points.size<2)return points
        var distance=0.0
        return points.filterIndexed { i,p ->
            if(i>0)distance+=p.distanceTo(points[i-1])
            if(i==0 || distance>=.48 || i==points.lastIndex) { distance=0.0;true } else false
        }
    }
    fun points(start: Vec3,end: Vec3): List<Vec3> {
        val distance=start.distanceTo(end)
        if(!distance.isFinite() || distance>8.01)return emptyList()
        val n=ceil(distance/.10).toInt().coerceIn(2,96)
        val sag=min(1.25,hypot(end.x-start.x,end.z-start.z)*.15)
        return (0..n).map { i -> val t=i.toDouble()/n;start.lerp(end,t).add(0.0,-4*t*(1-t)*sag,0.0) }
    }
}
class FairyLightsDescriptor(name: String,val colored: Boolean,val largeBulbs: Boolean=false): TransparentNodeDescriptor(name,FairyLightsElement::class.java,FairyLightsRender::class.java) {
    init { setCreativeTab(Eln.creativeTabLighting) }
    override fun mustHaveFloor()=false
    override fun handleRenderType(item: ItemStack,type: IItemRenderer.ItemRenderType)=true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType,item: ItemStack,helper: IItemRenderer.ItemRendererHelper)=true
    private val preview by lazy { CachedFairyMesh(FairyLightPath.points(Vec3(-.6,.35,0.0),Vec3(.6,.35,0.0)),Vec3.ZERO,colored,largeBulbs) }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preview.draw(true)
    override fun addCollisionBoxesToList(box: AABB,list: MutableList<AABB?>,world: net.minecraft.world.level.Level?,x: Int,y: Int,z: Int) {
        val plate=AABB(x+.42,y+.42,z+.42,x+.58,y+.58,z+.58);if(plate.intersects(box))list.add(plate)
    }
    override fun addInformation(stack: ItemStack?,player: net.minecraft.world.entity.player.Player?,list: MutableList<String>,advanced: Boolean) {
        list.add(tr("Select two supporting surfaces up to 8 blocks apart"))
        list.add(if(largeBulbs)tr("Black suspended wire with hanging globe bulbs about 50 cm apart") else tr("Green suspended wire; miniature lamps every 10 cm"))
        list.add(tr("Use a 120 V or 240 V cord, or set a wireless lamp supply channel"))
    }
}
class FairyLightsElement(node: TransparentNode,descriptor: TransparentNodeDescriptor): FestiveElement(node,descriptor) {
    var start=Vec3.ZERO
        private set
    var end=Vec3.ZERO
        private set
    private var lightPoints=emptyList<Vec3>()
    init { slowProcessList.add(mods.eln.sim.IProcess {
        val world=coordinate().world() as? ServerLevel
        if(world!=null && node.lightValue>0)lightPoints.filterIndexed { i,_->i%20==0 }.forEach { v ->
            val at=BlockPos.containing(v)
            if(world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)!=null)mods.eln.lightblock.LightBlockEntity.addLight(world,at.x,at.y,at.z,node.lightValue,5)
        }
    }) }
    private fun readSpan(tag: CompoundTag) {
        fun vec(prefix: String)=Vec3(tag.getDouble(prefix+"X"),tag.getDouble(prefix+"Y"),tag.getDouble(prefix+"Z"))
        val a=vec("fairyStart");val b=vec("fairyEnd")
        if(FairyLightPath.points(a,b).isNotEmpty() && a.distanceTo(b)>.1 && b.distanceTo(Vec3.atCenterOf(coordinate().pos))<2) { start=a;end=b;lightPoints=FairyLightPath.points(a,b) }
    }
    private fun writeSpan(tag: CompoundTag) {
        for((key,v)in listOf("fairyStart" to start,"fairyEnd" to end)) { tag.putDouble(key+"X",v.x);tag.putDouble(key+"Y",v.y);tag.putDouble(key+"Z",v.z) }
    }
    override fun readFromNBT(tag: CompoundTag) { super.readFromNBT(tag);readSpan(tag) }
    override fun writeToNBT(tag: CompoundTag) { super.writeToNBT(tag);writeSpan(tag) }
    override fun readItemStackNBT(nbt: CompoundTag?) { super.readItemStackNBT(nbt);nbt?.let(::readSpan) }
    override fun networkSerialize(out: DataOutputStream) { super.networkSerialize(out);for(v in listOf(start,end)) { out.writeDouble(v.x);out.writeDouble(v.y);out.writeDouble(v.z) } }
}
class FairyLightsRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): FestiveRender(entity,descriptor) {
    private var points=emptyList<Vec3>()
    private var mesh: CachedFairyMesh?=null
    override fun networkUnserialize(input: DataInputStream) {
        super.networkUnserialize(input)
        fun vec()=Vec3(input.readDouble(),input.readDouble(),input.readDouble())
        val next=FairyLightPath.points(vec(),vec())
        if(next!=points) { mesh?.destroy();mesh=null;points=next }
    }
    override fun draw() {
        if(mesh==null && points.isNotEmpty())mesh=CachedFairyMesh(points,Vec3.atCenterOf(tileEntity.blockPos),(transparentNodeDescriptor as FairyLightsDescriptor).colored,(transparentNodeDescriptor as FairyLightsDescriptor).largeBulbs)
        mesh?.draw(powered)
    }
    override fun destructor() { mesh?.destroy();mesh=null;super.destructor() }
    override fun cameraDrawOptimisation()=false
    override fun unoptimizedRenderBoundingBox(): AABB {
        val p=points.ifEmpty { listOf(Vec3.atCenterOf(tileEntity.blockPos)) }
        return AABB(p.minOf { it.x },p.minOf { it.y },p.minOf { it.z },p.maxOf { it.x },p.maxOf { it.y },p.maxOf { it.z }).inflate(.2)
    }
}
private class CachedFairyMesh(points: List<Vec3>,origin: Vec3,colored: Boolean,large: Boolean) {
    private fun record(draw: ()->Unit)=GL11.glGenLists(1).also { GL11.glNewList(it,GL11.GL_COMPILE);draw();GL11.glEndList() }
    private val wire=record { CordPath.draw(points,origin,if(large)floatArrayOf(.035f,.04f,.035f) else floatArrayOf(.06f,.22f,.09f),if(large).012 else .008) }
    private val lit=record { FairyLightMesh.bulbs(FairyLightPath.bulbPoints(points,large),origin,colored,true,large) }
    private val dark=record { FairyLightMesh.bulbs(FairyLightPath.bulbPoints(points,large),origin,colored,false,large) }
    fun draw(on: Boolean) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glCallList(wire)
            if(on)UtilsClient.disableLight()
            GL11.glCallList(if(on)lit else dark)
        } finally { GL11.glPopAttrib() }
    }
    fun destroy() { for(id in listOf(wire,lit,dark))GL11.glDeleteLists(id,1) }
}
object FairyLightMesh {
    private fun globe(center: Vec3,r: Double) {
        fun point(row: Int,col: Int) {
            val theta=PI*row/6;val phi=2*PI*col/10
            val x=sin(theta)*cos(phi);val y=cos(theta);val z=sin(theta)*sin(phi)
            GL11.glNormal3d(x,y,z);GL11.glVertex3d(center.x+r*x,center.y+r*y,center.z+r*z)
        }
        for(row in 0 until 6)for(col in 0 until 10) { point(row,col);point(row,col+1);point(row+1,col+1);point(row+1,col) }
    }
    fun bulbs(points: List<Vec3>,origin: Vec3,colored: Boolean,on: Boolean,large: Boolean=false) {
        if(points.isEmpty())return
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            if(on)UtilsClient.disableLight()
            GL11.glBegin(GL11.GL_QUADS)
            points.forEachIndexed { i,point ->
                val color=if(!on)floatArrayOf(.16f,.19f,.17f) else if(!colored)floatArrayOf(1f,.91f,.70f) else when(i%4) {
                    0->floatArrayOf(1f,.08f,.04f);1->floatArrayOf(.12f,1f,.20f);2->floatArrayOf(.12f,.35f,1f);else->floatArrayOf(1f,.70f,.04f)
                }
                GL11.glColor3f(color[0],color[1],color[2])
                if(large) {
                    val center=point.subtract(origin).add(0.0,-.105,0.0)
                    globe(center,.055)
                    GL11.glColor3f(.07f,.075f,.07f)
                    val socket=point.subtract(origin).add(0.0,-.04,0.0)
                    for(axis in 0..2)for(sign in listOf(-1,1)) {
                        val n=DoubleArray(3);n[axis]=sign.toDouble();GL11.glNormal3d(n[0],n[1],n[2])
                        val offsets=if(sign>0)listOf(-1 to -1,1 to -1,1 to 1,-1 to 1) else listOf(-1 to 1,1 to 1,1 to -1,-1 to -1)
                        for((u,v)in offsets) {
                            val q=doubleArrayOf(0.0,0.0,0.0);q[axis]=sign.toDouble();q[(axis+1)%3]=u.toDouble();q[(axis+2)%3]=v.toDouble()
                            GL11.glVertex3d(socket.x+q[0]*.021,socket.y+q[1]*.035,socket.z+q[2]*.021)
                        }
                    }
                    return@forEachIndexed
                }
                val p=point.subtract(origin).add(0.0,-.025,0.0);val r=.018
                for(axis in 0..2)for(sign in listOf(-1,1)) {
                    val normal=DoubleArray(3);normal[axis]=sign.toDouble();GL11.glNormal3d(normal[0],normal[1],normal[2])
                    val offsets=if(sign>0)listOf(-1 to -1,1 to -1,1 to 1,-1 to 1) else listOf(-1 to 1,1 to 1,1 to -1,-1 to -1)
                    for((u,v)in offsets) {
                        val q=doubleArrayOf(p.x,p.y,p.z);q[axis]+=sign*r;q[(axis+1)%3]+=u*r;q[(axis+2)%3]+=v*r
                        GL11.glVertex3d(q[0],q[1],q[2])
                    }
                }
            }
            GL11.glEnd()
        } finally { GL11.glPopAttrib() }
    }
}
object FairyLightPlacement {
    fun use(context: UseOnContext): InteractionResult {
        val p=context.player ?: return InteractionResult.PASS
        if(context.level.isClientSide)return InteractionResult.SUCCESS
        val stack=context.itemInHand;val tag=stack.tagCompound ?: CompoundTag()
        val hit=context.clickLocation;val normal=context.clickedFace.normal
        val point=hit.add(normal.x*.025,normal.y*.025,normal.z*.025)
        val first=tag.getCompound("fairyFirst")
        val dimension=Coordinate(context.clickedPos.x,context.clickedPos.y,context.clickedPos.z,context.level).dimension
        if(!tag.contains("fairyFirst") || first.getInt("dimension")!=dimension || p.isShiftKeyDown) {
            tag.put("fairyFirst",CompoundTag().apply { putInt("dimension",dimension);putDouble("x",point.x);putDouble("y",point.y);putDouble("z",point.z) });stack.tagCompound=tag
            Utils.sendMessage(p,tr("First fairy-light anchor selected; choose the other end"));return InteractionResult.SUCCESS
        }
        val start=Vec3(first.getDouble("x"),first.getDouble("y"),first.getDouble("z"))
        if(start.distanceTo(point) !in .25..8.0) { Utils.sendMessage(p,tr("Fairy-light anchors must be 0.25 to 8 blocks apart"));return InteractionResult.FAIL }
        val place=BlockPlaceContext(context)
        if(!place.canPlace() || !p.mayUseItemAt(place.clickedPos,context.clickedFace,stack))return InteractionResult.FAIL
        val copy=stack.copy();copy.tagCompound=CompoundTag().apply {
            for((key,v)in listOf("fairyStart" to start,"fairyEnd" to point)) { putDouble(key+"X",v.x);putDouble(key+"Y",v.y);putDouble(key+"Z",v.z) }
        }
        if(!Eln.transparentNodeItem.placeBlockAt(copy,p,context.level,place.clickedPos,context.clickedFace))return InteractionResult.FAIL
        stack.tagCompound=null;if(!p.abilities.instabuild)stack.shrink(1)
        return InteractionResult.SUCCESS
    }
}
