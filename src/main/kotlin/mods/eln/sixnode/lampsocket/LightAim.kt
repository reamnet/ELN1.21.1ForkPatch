package mods.eln.sixnode.lampsocket

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Shared by the visible swivel and the emitted beam; angles are local to the mount. */
class LightAim(val heads: Int, val defaultTilt: Double=0.0) {
    private val pan=DoubleArray(heads)
    private val tilt=DoubleArray(heads) { defaultTilt }
    fun pan(head: Int)=pan[head]
    fun tilt(head: Int)=tilt[head]
    fun set(head: Int,axis: Int,value: Double): Boolean {
        if(head !in 0 until heads || axis !in 0..1 || !value.isFinite())return false
        if(axis==0)pan[head]=value.coerceIn(-90.0,90.0) else tilt[head]=value.coerceIn(-75.0,75.0)
        return true
    }
    fun rotate(v: Vec3,head: Int): Vec3 {
        val a=Math.toRadians(pan(head));val b=Math.toRadians(tilt(head))
        val x=v.x*cos(b)-v.y*sin(b);val y=v.x*sin(b)+v.y*cos(b)
        return Vec3(x*cos(a)-v.z*sin(a),y,x*sin(a)+v.z*cos(a))
    }
    fun point(v: Vec3,pivot: Vec3,head: Int)=pivot.add(rotate(v.subtract(pivot),head))
    fun rotateZ(v: Vec3,head: Int): Vec3 {
        val q=rotate(Vec3(v.z,v.y,-v.x),head)
        return Vec3(-q.z,q.y,q.x)
    }
    fun pointZ(v: Vec3,pivot: Vec3,head: Int)=pivot.add(rotateZ(v.subtract(pivot),head))
    fun write(nbt: CompoundTag) { for(h in 0 until heads) { nbt.putDouble("lightAimPan$h",pan(h));nbt.putDouble("lightAimTilt$h",tilt(h)) } }
    fun read(nbt: CompoundTag) {
        for(h in 0 until heads) {
            if(nbt.contains("lightAimPan$h"))set(h,0,nbt.getDouble("lightAimPan$h"))
            if(nbt.contains("lightAimTilt$h"))set(h,1,nbt.getDouble("lightAimTilt$h"))
        }
    }
    fun write(stream: DataOutputStream) { for(h in 0 until heads) { stream.writeDouble(pan(h));stream.writeDouble(tilt(h)) } }
    fun read(stream: DataInputStream) { for(h in 0 until heads) { set(h,0,stream.readDouble());set(h,1,stream.readDouble()) } }
}
