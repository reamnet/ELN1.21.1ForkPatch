package mods.eln.utility

import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor

/** Two real downward cable entries. The right entry is below the reserved second cabinet cell. */
object WideMeterWiring {
    fun port(meter: HouseServiceElement,output: Boolean): LRDU {
        val input=Direction.YN.getLRDUGoingTo(meter.front.inverse)!!
        return if(output)input.inverse() else input
    }
    fun cableCell(meter: HouseServiceElement,output: Boolean)=meter.coordinate().pos.below().let { if(output)it.relative(meter.front.right().toFacing()) else it }
    private fun wide(meter: HouseServiceElement)=(meter.transparentNodeDescriptor as HouseServiceDescriptor).wide
    private fun connect(meter: HouseServiceElement,output: Boolean) {
        val owner=meter.node ?: return
        if(owner.isDestructing)return
        val cell=cableCell(meter,output)
        val wire=NodeManager.instance?.getNodeFromCoordonate(Coordinate(cell.x,cell.y,cell.z,owner.coordinate.dimension)) as? SixNode ?: return
        val edge=Direction.YP.getLRDUGoingTo(meter.front.inverse)!!
        val endpoint=wire.getConnectionEndpoint(Direction.YP,edge)
        if((endpoint.element as? SixNodeElement)?.sixNodeElementDescriptor !is ElectricalCableDescriptor || endpoint.elementSide!=meter.front.inverse)return
        val slot=port(meter,output)
        if(owner.nodeConnectionList.any { c->c.N1===owner && c.N2===wire && c.dir1==Direction.YN && c.lrdu1==slot || c.N2===owner && c.N1===wire && c.dir2==Direction.YN && c.lrdu2==slot })return
        NodeBase.tryConnectTwoNode(owner,Direction.YN,slot,wire,Direction.YP,edge)
    }
    fun refreshCables(meter: HouseServiceElement) {
        for(output in listOf(false,true)) {
            val p=cableCell(meter,output)
            NodeManager.instance?.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,meter.coordinate().dimension))?.takeIf { !it.isDestructing }?.reconnect()
        }
    }
    /** The cabinet's insulating barrier keeps its adjacent downleads from bypassing the meter. */
    fun separates(a: NodeBase,sideA: Direction,edgeA: LRDU,b: NodeBase,sideB: Direction,edgeB: LRDU): Boolean {
        if(a !is SixNode || b !is SixNode || a.coordinate.dimension!=b.coordinate.dimension || a.coordinate.y!=b.coordinate.y)return false
        if(kotlin.math.abs(a.coordinate.x-b.coordinate.x)+kotlin.math.abs(a.coordinate.z-b.coordinate.z)!=1)return false
        for((first,second) in listOf(a to b,b to a)) {
            val p=first.coordinate.pos.above()
            val ownerNode=NodeManager.instance?.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,a.coordinate.dimension)) as? TransparentNode ?: continue
            val owner=ownerNode.element as? HouseServiceElement ?: continue
            if(!wide(owner) || ownerNode.isDestructing || cableCell(owner,true)!=second.coordinate.pos)continue
            val mount=owner.front.inverse
            if(a.getConnectionEndpoint(sideA,edgeA).elementSide==mount && b.getConnectionEndpoint(sideB,edgeB).elementSide==mount)return true
        }
        return false
    }
    fun connect(node: NodeBase) {
        val meter=(node as? TransparentNode)?.element as? HouseServiceElement
        if(meter!=null && wide(meter)) { connect(meter,false);connect(meter,true);return }
        if(node !is SixNode)return
        // Both placement orders and chunk reloads use normal node connection lifetimes.
        for(front in Direction.values().filterNot { it.isY })for(output in listOf(false,true)) {
            val at=node.coordinate.pos.above().let { if(output)it.relative(front.left().toFacing()) else it }
            val owner=(NodeManager.instance?.getNodeFromCoordonate(Coordinate(at.x,at.y,at.z,node.coordinate.dimension)) as? TransparentNode)?.element as? HouseServiceElement ?: continue
            if(wide(owner) && owner.front==front)connect(owner,output)
        }
    }
}
