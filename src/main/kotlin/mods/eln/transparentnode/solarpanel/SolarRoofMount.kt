package mods.eln.transparentnode.solarpanel

import mods.eln.ghost.GhostGroup
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Roof pitch is separate from the panel's existing manual / sun-tracking adjustment.
 * The origin is the mounting foot, so a 45-degree roof does not rotate through its steps. */
data class SolarRoofMount(val uphill: net.minecraft.core.Direction?=null) {
    val pitched get()=uphill!=null
    fun vector(point: Vec3): Vec3 {
        val up=uphill ?: return point
        val axis=Vec3(-up.stepZ.toDouble(),0.0,up.stepX.toDouble())
        val c=sqrt(.5)
        return point.scale(c).add(axis.cross(point).scale(c)).add(axis.scale(axis.dot(point)*(1-c)))
    }
    fun point(point: Vec3)=vector(point.add(0.0,.5,0.0)).add(0.0,-.5,0.0)
    fun targetAlpha(sunAlpha: Double): Double {
        val sun=Vec3(cos(sunAlpha),sin(sunAlpha),0.0)
        // Inverse pitch is the transpose: dot the sun with the pitched basis vectors.
        return atan2(sun.dot(vector(Vec3(0.0,1.0,0.0))),sun.dot(vector(Vec3(1.0,0.0,0.0))))
    }
    fun incidence(panelAlpha: Double,sunAlpha: Double): Double {
        if(!panelAlpha.isFinite() || !sunAlpha.isFinite() || sunAlpha !in 0.0..Math.PI)return 0.0
        val normal=vector(Vec3(cos(panelAlpha),sin(panelAlpha),0.0))
        return normal.dot(Vec3(cos(sunAlpha),sin(sunAlpha),0.0)).coerceIn(0.0,1.0)
    }
    fun groundOffset(original: Coordinate)=Coordinate(original).apply { if(pitched) { y=1;z=1 } }
    fun footprint(original: GhostGroup?,front: Direction): GhostGroup? {
        if(original==null)return null
        if(!pitched)return original.newRotate(front)
        val group=GhostGroup()
        for(cell in original.elementList) {
            // A two-row array follows the uphill roof course. Do not replace its stairs
            // with horizontal invisible reservation blocks.
            group.addElement(cell.x,if(cell.x>0)1 else cell.y,cell.z,cell.block,cell.meta)
        }
        return group.newRotate(front)
    }

    companion object {
        @JvmStatic fun at(world: BlockGetter,pos: BlockPos): SolarRoofMount {
            if(world is net.minecraft.world.level.Level && !world.isClientSide) {
                // ELN nodes continue simulating after vanilla unloads their chunks. Fetch
                // the saved mount from that node, rather than force-loading its block entity.
                val element=mods.eln.node.NodeManager.instance?.getTransparentNodeFromCoordinate(Coordinate(pos.x,pos.y,pos.z,world)) as? SolarPanelElement
                if(element?.roofMountKnown==true)return element.roofMount
                return fromSupport(world,pos)
            }
            val entity=mods.eln.node.LoadedBlockEntities.get(world,pos) as? mods.eln.node.transparent.TransparentNodeEntity
            val render=entity?.elementRender as? SolarPanelRender
            if(render?.roofMountKnown==true)return render.roofMount
            return fromSupport(world,pos)
        }
        @JvmStatic fun fromSupport(world: BlockGetter,pos: BlockPos): SolarRoofMount {
            val state=mods.eln.node.LoadedBlockStates.get(world,pos.below()) ?: return SolarRoofMount()
            return if(state.block is StairBlock && state.getValue(StairBlock.HALF)==Half.BOTTOM)
                SolarRoofMount(state.getValue(StairBlock.FACING)) else SolarRoofMount()
        }
        @JvmStatic fun fromId(id: Int)=SolarRoofMount(net.minecraft.core.Direction.entries.firstOrNull { it.get3DDataValue()==id && it.axis.isHorizontal })
    }
}
