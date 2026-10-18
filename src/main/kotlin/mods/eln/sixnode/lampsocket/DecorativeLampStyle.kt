package mods.eln.sixnode.lampsocket

import mods.eln.i18n.I18N
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Mesh coordinates use +X out of the mounting surface, with Up as the default facing. */
enum class DecorativeLampStyle(val model: String, val itemName: String, val bulbs: Int,
    val depth: Double, val range: Int, val heads: List<Vec3>, val beam: Vec3,
    val wirelessOnly: Boolean = false) {
    PARKING("twinparkinglight", I18N.TR_NAME(I18N.Type.NONE, "Twin Parking Lot Light"), 2, 3.5, 4,
        listOf(Vec3(3.15,-.8,0.0),Vec3(3.15,.8,0.0)),Vec3(-1.0,0.0,0.0)),
    WAREHOUSE("warehousewalllight", I18N.TR_NAME(I18N.Type.NONE, "Warehouse Wall Floodlight"), 1, 1.0, 12,
        listOf(Vec3(.57,-.03,0.0)),Vec3(cos(Math.toRadians(25.0)),-sin(Math.toRadians(25.0)),0.0)),
    DESK("householddesklamp", I18N.TR_NAME(I18N.Type.NONE, "Household Desk Lamp"), 1, .8, 0,
        listOf(Vec3(.49,0.0,0.0)),Vec3(-1.0,0.0,0.0)),
    RECESSED("roundrecessedlight", I18N.TR_NAME(I18N.Type.NONE, "Round Recessed Ceiling Light"), 1, .6, 3,
        listOf(Vec3(.05,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    CHANDELIER("basicchandelier", I18N.TR_NAME(I18N.Type.NONE, "Basic Chandelier"), 5, 1.0, 0,
        (0..4).map { Vec3(.68,.34*cos(it*2*PI/5),.34*sin(it*2*PI/5)) },Vec3(1.0,0.0,0.0));

    fun localFacing(v: Vec3, front: LRDU) = front.rotateOnXnLeft(v.xRot((-PI/2).toFloat()))
    fun worldPoint(origin: Vec3, side: Direction, front: LRDU, local: Vec3): Vec3 =
        origin.add(side.rotateFromXN(localFacing(local,front).add(-.5,0.0,0.0)))
    fun worldDirection(side: Direction, front: LRDU, local: Vec3) = side.rotateFromXN(localFacing(local,front)).normalize()
}
