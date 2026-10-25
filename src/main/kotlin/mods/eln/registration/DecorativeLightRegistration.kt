package mods.eln.registration

import mods.eln.Eln
import mods.eln.ghost.GhostGroup
import mods.eln.item.lampitem.LampLists
import mods.eln.misc.Direction
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsocket.objrender.DecorativeLampObjRender

object DecorativeLightRegistration {
    const val LEGACY_FIXTURES = 30
    fun idFor(style: DecorativeLampStyle): Int {
        val index=style.ordinal
        if(index>=88)return (70 shl 6)+index-88 // Separate appended group; do not spill into occupied groups.
        return if(index<LEGACY_FIXTURES)(64 shl 6)+20+index else (68 shl 6)+index-LEGACY_FIXTURES
    }
    /** New styles use a separate group; group 65 already contains Lamp Supplies. */
    fun register(@Suppress("UNUSED_PARAMETER") group: Int) {
        DecorativeLampStyle.entries.forEach { style ->
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
                    else -> {
                        when {
                            style.miniDot || style==DecorativeLampStyle.EAVE_TWIN ->
                                setPlaceDirection(arrayOf(Direction.YP,Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
                            style.parking -> {
                                setPlaceDirection(Direction.YN)
                                ghostGroup=GhostGroup().apply {
                                    for(x in 1..4)addElement(x,0,0)
                                    for(x in 5..6)for(y in -2..2)for(z in -2..2)addElement(x,y,z)
                                }
                            }
                            style==DecorativeLampStyle.PARTY_TIKI || style==DecorativeLampStyle.PARTY_LANTERN -> {
                                setPlaceDirection(Direction.YN)
                                if(style==DecorativeLampStyle.PARTY_TIKI)ghostGroup=GhostGroup().apply { addElement(1,0,0) }
                            }
                            style.outdoorFloor -> {
                                setPlaceDirection(Direction.YN)
                                if(style.depth>1)ghostGroup=GhostGroup().apply { for(x in 1 until kotlin.math.ceil(style.depth).toInt())addElement(x,0,0) }
                            }
                            style.tableLamp -> setPlaceDirection(Direction.YN)
                            style == DecorativeLampStyle.FLOOR_DRUM -> {
                                setPlaceDirection(Direction.YN)
                                ghostGroup=GhostGroup().apply { addElement(1,0,0) }
                            }
                            style.street -> {
                                setPlaceDirection(Direction.YN)
                                ghostGroup=GhostGroup().apply {
                                    val firstHead=if(style==DecorativeLampStyle.STREET_BOULEVARD)4 else 5
                                    for(x in 1 until firstHead)addElement(x,0,0)
                                    for(x in firstHead..5)for(y in -1..1)for(z in -1..1)addElement(x,y,z)
                                }
                            }
                            style.luxury -> {
                                setPlaceDirection(Direction.YP)
                                ghostGroup=GhostGroup().apply {
                                    for(x in 1..kotlin.math.ceil(style.depth).toInt())for(y in -2..2)for(z in -2..2)addElement(x,y,z)
                                }
                            }
                            style.pool -> Unit // Sealed fittings may also mount on a pool floor or step.
                            style.wallLamp -> setPlaceDirection(arrayOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP))
                            style.ceiling -> setPlaceDirection(Direction.YP)
                        }
                        if(style == DecorativeLampStyle.STRIP_SURFACE || style == DecorativeLampStyle.RIG_LINEAR || style.seasonalString || style.partyString)
                            ghostGroup=GhostGroup().apply { addElement(0,0,-1);addElement(0,0,1) }
                        if(style.fan)ghostGroup=GhostGroup().apply {
                            val radius=if(style==DecorativeLampStyle.FAN_INDUSTRIAL)2 else 1
                            for(y in -radius..radius)for(z in -radius..radius)if(y!=0 || z!=0)addElement(0,y,z)
                        }
                    }
                }
            }
            // These cells reserve model clearance, not full cubes of solid machinery.
            descriptor.ghostGroup?.elementList?.forEach { it.meta=mods.eln.ghost.GhostBlock.tLampClearance }
            descriptor.creativeTab=Eln.creativeTabLighting
            Eln.sixNodeItem.addDescriptor(idFor(style),descriptor)
        }
    }
}
