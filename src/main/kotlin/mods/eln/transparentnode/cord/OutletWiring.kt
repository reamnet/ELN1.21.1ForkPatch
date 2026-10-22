package mods.eln.transparentnode.cord

import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor

/** A recessed outlet's terminals sit on the outside wall face, one cell ahead of its node. */
object OutletWiring {
    private fun isCable(element: Any?)=(element as? SixNodeElement)?.sixNodeElementDescriptor is ElectricalCableDescriptor
    fun surfaceEndpoint(outlet: OutletElement,edge: LRDU): NodeConnectionEndpoint? {
        if(!outlet.recessed)return null
        val lateral=outlet.front.applyLRDU(edge)
        val position=outlet.coordinate().moved(outlet.front).moved(lateral)
        val wire=NodeManager.instance?.getNodeFromCoordonate(position) as? SixNode ?: return null
        val side=lateral.inverse
        val lrdu=side.getLRDUGoingTo(outlet.front)?.inverse() ?: return null
        return wire.getConnectionEndpoint(side,lrdu).takeIf {
            isCable(it.element) && it.elementSide==outlet.front.inverse
        }
    }

    /** Called from either end when nodes reconnect, even with an appliance in front of the outlet. */
    fun connect(node: NodeBase) {
        val outlet=(node as? TransparentNode)?.element as? OutletElement
        if(outlet?.recessed==true) {
            for(edge in LRDU.values())connect(outlet,edge)
        } else if(node is SixNode) {
            for(mount in Direction.values())if(isCable(node.getElement(mount))) {
                for(edge in LRDU.values()) {
                    val lateral=mount.applyLRDU(edge)
                    val position=node.coordinate.moved(mount).moved(lateral)
                    val other=(NodeManager.instance?.getNodeFromCoordonate(position) as? TransparentNode)?.element as? OutletElement ?: continue
                    if(other.recessed && other.front==mount.inverse)
                        connect(other,other.front.getLRDUGoingTo(lateral.inverse)!!)
                }
            }
        }
    }

    private fun connect(outlet: OutletElement,edge: LRDU) {
        if(outlet.wireless || outlet.cord.plugged)return
        val endpoint=surfaceEndpoint(outlet,edge) ?: return
        val node=outlet.node!!
        // The normal wrapped-cable path may already have made this exact connection.
        if(node.nodeConnectionList.any { c ->
            c.N1===node && c.dir1==outlet.front && c.lrdu1==edge && c.N2===endpoint.node && c.dir2==endpoint.side && c.lrdu2==endpoint.lrdu ||
                c.N2===node && c.dir2==outlet.front && c.lrdu2==edge && c.N1===endpoint.node && c.dir1==endpoint.side && c.lrdu1==endpoint.lrdu
        })return
        NodeBase.tryConnectTwoNode(node,outlet.front,edge,endpoint.node,endpoint.side,endpoint.lrdu)
    }
}
