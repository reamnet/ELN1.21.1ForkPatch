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
    PARKING("twinparkinglight", I18N.TR_NAME(I18N.Type.NONE, "Twin Parking Lot Light"), 2, 5.5, 6,
        listOf(Vec3(5.15,-.8,0.0),Vec3(5.15,.8,0.0)),Vec3(-1.0,0.0,0.0)),
    WAREHOUSE("warehousewalllight", I18N.TR_NAME(I18N.Type.NONE, "Warehouse Wall Floodlight"), 1, 1.0, 12,
        listOf(Vec3(.57,-.03,0.0)),Vec3(cos(Math.toRadians(25.0)),-sin(Math.toRadians(25.0)),0.0)),
    DESK("householddesklamp", I18N.TR_NAME(I18N.Type.NONE, "Household Desk Lamp"), 1, .8, 0,
        listOf(Vec3(.49,0.0,0.0)),Vec3(-1.0,0.0,0.0)),
    RECESSED("roundrecessedlight", I18N.TR_NAME(I18N.Type.NONE, "Round Recessed Ceiling Light"), 1, .6, 3,
        listOf(Vec3(.05,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    CHANDELIER("basicchandelier", I18N.TR_NAME(I18N.Type.NONE, "Basic Chandelier"), 5, 1.0, 0,
        (0..4).map { Vec3(.68,.34*cos(it*2*PI/5),.34*sin(it*2*PI/5)) },Vec3(1.0,0.0,0.0)),
    DOT("minidotspotlight", I18N.TR_NAME(I18N.Type.NONE, "Mini Dot Spotlight"), 1, .24, 8,
        listOf(Vec3(.215,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    PORCH("porchlanternsconce", I18N.TR_NAME(I18N.Type.NONE, "Porch Lantern Sconce"), 1, .8, 0,
        listOf(Vec3(.31,-.06,0.0)),Vec3(1.0,0.0,0.0)),
    HOLDER("basiclampholder", I18N.TR_NAME(I18N.Type.NONE, "Basic Lamp Holder"), 1, .34, 0,
        listOf(Vec3(.20,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    SHOP("thinshoplight", I18N.TR_NAME(I18N.Type.NONE, "Thin Fluorescent Shop Light"), 2, 2.5, 4,
        listOf(Vec3(.12,-.07,0.0),Vec3(.12,.07,0.0)),Vec3(1.0,0.0,0.0)),
    FACTORY("hangingfactorylight", I18N.TR_NAME(I18N.Type.NONE, "Suspended Factory Fluorescent Light"), 4, 2.5, 4,
        (0..3).map { Vec3(1.24,-.21+it*.14,0.0) },Vec3(1.0,0.0,0.0)),
    MOTION("outdoormotionlight", I18N.TR_NAME(I18N.Type.NONE, "Outdoor Motion Floodlight"), 2, 1.1, 10,
        listOf(Vec3(.45,.12,-.25),Vec3(.45,.12,.25)),Vec3(.94,-.34,0.0)),
    CANDLE("candlefoyerchandelier", I18N.TR_NAME(I18N.Type.NONE, "Candle Arm Foyer Chandelier"), 6, 1.7, 1,
        (0..5).map { Vec3(1.02,.60*cos(it*PI/3),.60*sin(it*PI/3)) },Vec3(1.0,0.0,0.0)),
    CRYSTAL("crystaltierchandelier", I18N.TR_NAME(I18N.Type.NONE, "Crystal Tier Foyer Chandelier"), 6, 1.9, 1,
        (0..5).map { Vec3(1.04,.40*cos(it*PI/3),.40*sin(it*PI/3)) },Vec3(1.0,0.0,0.0)),
    WAGON("wagonwheelchandelier", I18N.TR_NAME(I18N.Type.NONE, "Wagon Wheel Foyer Chandelier"), 8, 1.9, 1,
        (0..7).map { Vec3(.99,.77*cos(it*PI/4),.77*sin(it*PI/4)) },Vec3(1.0,0.0,0.0)),
    LANTERN("lanternfoyerchandelier", I18N.TR_NAME(I18N.Type.NONE, "Lantern Cage Foyer Chandelier"), 4, 1.8, 1,
        (0..3).map { Vec3(1.05,.23*cos(it*PI/2),.23*sin(it*PI/2)) },Vec3(1.0,0.0,0.0)),
    SPUTNIK("sputnikchandelier", I18N.TR_NAME(I18N.Type.NONE, "Modern Sputnik Foyer Chandelier"), 8, 1.9, 1,
        (0..7).map { Vec3(1.05+if(it%2==0) -.25 else .25,.70*cos(it*PI/4),.70*sin(it*PI/4)) },Vec3(1.0,0.0,0.0)),
    SCONCE_CYLINDER("cylinderwallsconce", I18N.TR_NAME(I18N.Type.NONE, "Up Down Cylinder Wall Sconce"), 2, .65, 4,
        listOf(Vec3(.21,.28,0.0),Vec3(.21,-.28,0.0)),Vec3(.15,1.0,0.0)),
    SCONCE_FABRIC("fabricwallsconce", I18N.TR_NAME(I18N.Type.NONE, "Fabric Shade Wall Sconce"), 1, .8, 0,
        listOf(Vec3(.28,.05,0.0)),Vec3(1.0,0.0,0.0)),
    SCONCE_GLOBE("globewallsconce", I18N.TR_NAME(I18N.Type.NONE, "Glass Globe Wall Sconce"), 1, .65, 0,
        listOf(Vec3(.32,.03,0.0)),Vec3(1.0,0.0,0.0)),
    SCONCE_CAGE("cagewallsconce", I18N.TR_NAME(I18N.Type.NONE, "Industrial Cage Wall Sconce"), 1, .8, 0,
        listOf(Vec3(.24,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    SCONCE_CANDLE("candlewallsconce", I18N.TR_NAME(I18N.Type.NONE, "Double Candle Wall Sconce"), 2, .9, 0,
        listOf(Vec3(.30,.04,-.23),Vec3(.30,.04,.23)),Vec3(1.0,0.0,0.0)),
    TABLE_DRUM("ceramicdrumtablelamp", I18N.TR_NAME(I18N.Type.NONE, "Ceramic Drum Table Lamp"), 1, .92, 0,
        listOf(Vec3(.65,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    TABLE_BANKER("bankerstablelamp", I18N.TR_NAME(I18N.Type.NONE, "Green Banker Table Lamp"), 1, .65, 0,
        listOf(Vec3(.47,.10,0.0)),Vec3(-1.0,0.0,0.0)),
    TABLE_TASK("articulatedtablelamp", I18N.TR_NAME(I18N.Type.NONE, "Articulated Task Table Lamp"), 1, .98, 4,
        listOf(Vec3(.72,.27,0.0)),Vec3(-1.0,.35,0.0)),
    POT_BAFFLE("deepbafflepotlight", I18N.TR_NAME(I18N.Type.NONE, "Deep Baffle Recessed Light"), 1, .40, 5,
        listOf(Vec3(.06,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    POT_TRIMLESS("trimlesspotlight", I18N.TR_NAME(I18N.Type.NONE, "Trimless Recessed Downlight"), 1, .38, 4,
        listOf(Vec3(.025,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    POT_GIMBAL("gimbalpotlight", I18N.TR_NAME(I18N.Type.NONE, "Adjustable Gimbal Recessed Light"), 1, .46, 8,
        listOf(Vec3(.085,0.0,0.0)),Vec3(.94,.34,0.0),true),
    OUTDOOR_BOLLARD("gardenbollardlight", I18N.TR_NAME(I18N.Type.NONE, "Garden Bollard Light"), 1, 1.1, 0,
        listOf(Vec3(.83,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    OUTDOOR_BARN("gooseneckbarnlight", I18N.TR_NAME(I18N.Type.NONE, "Gooseneck Barn Light"), 1, .96, 7,
        listOf(Vec3(.63,-.04,0.0)),Vec3(.3,-1.0,0.0)),
    OUTDOOR_POST("traditionallanternpost", I18N.TR_NAME(I18N.Type.NONE, "Traditional Lantern Post Light"), 1, 2.6, 0,
        listOf(Vec3(2.06,0.0,0.0)),Vec3(1.0,0.0,0.0));

    val tableLamp get() = this == DESK || this == TABLE_DRUM || this == TABLE_BANKER || this == TABLE_TASK
    val wallLamp get() = when(this) {
        WAREHOUSE,PORCH,MOTION,SCONCE_CYLINDER,SCONCE_FABRIC,SCONCE_GLOBE,SCONCE_CAGE,SCONCE_CANDLE,OUTDOOR_BARN -> true
        else -> false
    }
    val foyer get() = when(this) {
        CANDLE, CRYSTAL, WAGON, LANTERN, SPUTNIK -> true
        else -> false
    }
    val ceiling get() = this == RECESSED || this == CHANDELIER || this == SHOP || this == FACTORY || foyer || this == POT_BAFFLE || this == POT_TRIMLESS || this == POT_GIMBAL
    val uprightItem get() = this == PARKING || tableLamp || this == OUTDOOR_BOLLARD || this == OUTDOOR_POST || ceiling
    val exposedBulbs get() = foyer || tableLamp || this == SCONCE_CANDLE || this == SCONCE_CAGE || this == SCONCE_GLOBE || this == OUTDOOR_POST || this == DESK || this == CHANDELIER || this == PORCH || this == HOLDER

    fun localFacing(v: Vec3, front: LRDU) = front.rotateOnXnLeft(v.xRot((-PI/2).toFloat()))
    fun worldPoint(origin: Vec3, side: Direction, front: LRDU, local: Vec3): Vec3 =
        origin.add(side.rotateFromXN(localFacing(local,front).add(-.5,0.0,0.0)))
    fun worldDirection(side: Direction, front: LRDU, local: Vec3) = side.rotateFromXN(localFacing(local,front)).normalize()
}
