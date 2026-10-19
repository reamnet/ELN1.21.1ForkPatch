package mods.eln.sixnode.lampsocket

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max

/** Horizontal disk through the actual blades, not the full clearance reservation. */
class CeilingFanHazard(private val lamp: LampSocketElement) {
    private val hitAfter=mutableMapOf<UUID,Long>()
    fun tick() {
        val world=lamp.coordinate!!.world() as? ServerLevel ?: return
        if(lamp.fanPower<.1) { hitAfter.clear();return }
        val style=DecorativeLampStyle.FAN_INDUSTRIAL
        val center=style.worldPoint(Vec3.atCenterOf(lamp.coordinate!!.pos),lamp.side,lamp.front,Vec3(.4575,0.0,0.0))
        val touching=mutableSetOf<UUID>()
        for(entity in world.getEntitiesOfClass(LivingEntity::class.java,AABB(center,center).inflate(2.7))) {
            if(!entity.isAlive || entity.isSpectator || !touches(entity.boundingBox,center))continue
            touching+=entity.uuid
            if(world.gameTime>=(hitAfter[entity.uuid] ?: Long.MIN_VALUE)) {
                // Two hits per second, 2.5 health each: exactly five damage/s at full speed.
                entity.hurt(world.damageSources().generic(),(2.5*lamp.fanPower*lamp.fanPower).toFloat())
                hitAfter[entity.uuid]=world.gameTime+10
            }
        }
        hitAfter.keys.retainAll(touching)
    }
    companion object {
        fun touches(box: AABB,center: Vec3): Boolean {
            if(box.maxY<center.y-.04 || box.minY>center.y+.04)return false
            val dx=max(box.minX-center.x,max(0.0,center.x-box.maxX))
            val dz=max(box.minZ-center.z,max(0.0,center.z-box.maxZ))
            return dx*dx+dz*dz<=2.5*2.5
        }
    }
}
