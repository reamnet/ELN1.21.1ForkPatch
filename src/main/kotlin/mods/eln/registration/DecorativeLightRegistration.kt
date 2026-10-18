package mods.eln.registration

import mods.eln.Eln
import mods.eln.ghost.GhostGroup
import mods.eln.item.lampitem.LampLists
import mods.eln.misc.Direction
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsocket.objrender.DecorativeLampObjRender

object DecorativeLightRegistration {
    /** New slots 20..24 in group 64; all earlier lamp identities remain unchanged. */
    fun register(group: Int) {
        DecorativeLampStyle.entries.forEachIndexed { index,style ->
            val descriptor = LampSocketDescriptor(style.itemName,DecorativeLampObjRender(style),style.range,
                LampLists.lampTechnologyList.toTypedArray()).apply {
                fixture = style
                extendedRenderBounds = true
                when (style) {
                    DecorativeLampStyle.PARKING -> {
                        setPlaceDirection(Direction.YN)
                        ghostGroup = GhostGroup().apply {
                            addElement(1,0,0); addElement(2,0,0)
                            for(y in -1..1) for(z in -1..1) addElement(3,y,z)
                        }
                    }
                    DecorativeLampStyle.DESK -> setPlaceDirection(Direction.YN)
                    DecorativeLampStyle.RECESSED, DecorativeLampStyle.CHANDELIER -> setPlaceDirection(Direction.YP)
                    DecorativeLampStyle.WAREHOUSE -> setPlaceDirection(arrayOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
                }
            }
            Eln.sixNodeItem.addDescriptor((group shl 6)+20+index,descriptor)
        }
    }
}
