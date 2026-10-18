package mods.eln.registration

import mods.eln.Eln
import mods.eln.ghost.GhostGroup
import mods.eln.item.lampitem.LampLists
import mods.eln.misc.Direction
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsocket.objrender.DecorativeLampObjRender

object DecorativeLightRegistration {
    /** Append new fixtures after slots 20..24; existing save identities stay fixed. */
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
                            for(x in 1..4) addElement(x,0,0)
                            for(y in -1..1) for(z in -1..1) addElement(5,y,z)
                        }
                    }
                    DecorativeLampStyle.OUTDOOR_POST -> {
                        setPlaceDirection(Direction.YN)
                        ghostGroup = GhostGroup().apply { addElement(1,0,0);addElement(2,0,0) }
                    }
                    DecorativeLampStyle.DESK,DecorativeLampStyle.TABLE_DRUM,DecorativeLampStyle.TABLE_BANKER,
                    DecorativeLampStyle.TABLE_TASK,DecorativeLampStyle.OUTDOOR_BOLLARD -> setPlaceDirection(Direction.YN)
                    DecorativeLampStyle.SHOP, DecorativeLampStyle.FACTORY -> {
                        setPlaceDirection(Direction.YP)
                        ghostGroup = GhostGroup().apply {
                            for(z in -1..1) if(z != 0) addElement(0,0,z)
                            if(style == DecorativeLampStyle.FACTORY) for(z in -1..1) addElement(1,0,z)
                        }
                    }
                    DecorativeLampStyle.CANDLE, DecorativeLampStyle.CRYSTAL, DecorativeLampStyle.WAGON,
                    DecorativeLampStyle.LANTERN, DecorativeLampStyle.SPUTNIK -> {
                        setPlaceDirection(Direction.YP)
                        ghostGroup = GhostGroup().apply {
                            for(y in -1..1) for(z in -1..1) addElement(1,y,z)
                        }
                    }
                    DecorativeLampStyle.POT_BAFFLE,DecorativeLampStyle.POT_TRIMLESS,DecorativeLampStyle.POT_GIMBAL,
                    DecorativeLampStyle.RECESSED, DecorativeLampStyle.CHANDELIER -> setPlaceDirection(Direction.YP)
                    DecorativeLampStyle.SCONCE_CYLINDER,DecorativeLampStyle.SCONCE_FABRIC,DecorativeLampStyle.SCONCE_GLOBE,
                    DecorativeLampStyle.SCONCE_CAGE,DecorativeLampStyle.SCONCE_CANDLE,DecorativeLampStyle.OUTDOOR_BARN,
                    DecorativeLampStyle.WAREHOUSE, DecorativeLampStyle.PORCH, DecorativeLampStyle.MOTION -> setPlaceDirection(arrayOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
                    DecorativeLampStyle.DOT -> setPlaceDirection(arrayOf(Direction.YP,Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
                    DecorativeLampStyle.HOLDER -> Unit // Plain holder mounts on any supporting face.
                }
            }
            Eln.sixNodeItem.addDescriptor((group shl 6)+20+index,descriptor)
        }
    }
}
