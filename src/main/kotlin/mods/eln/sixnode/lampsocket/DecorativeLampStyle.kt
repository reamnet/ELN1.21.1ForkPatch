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
        listOf(Vec3(2.06,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    WINDOW_CANDLE("windowcandlelamp", I18N.TR_NAME(I18N.Type.NONE, "Window Candle Light"), 1, .52, 0,
        listOf(Vec3(.39,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    FLOOR_DRUM("standingdrumfloorlamp", I18N.TR_NAME(I18N.Type.NONE, "Standing Drum Floor Lamp"), 1, 2.0, 0,
        listOf(Vec3(1.66,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    STRIP_CABINET("undercabinetledstrip", I18N.TR_NAME(I18N.Type.NONE, "Under Cabinet LED Strip"), 2, 1.0, 2,
        listOf(Vec3(.045,0.0,-.23),Vec3(.045,0.0,.23)),Vec3(1.0,0.0,0.0)),
    STRIP_RECESSED("recessedledchannel", I18N.TR_NAME(I18N.Type.NONE, "Recessed LED Channel"), 2, 1.0, 3,
        listOf(Vec3(.025,0.0,-.23),Vec3(.025,0.0,.23)),Vec3(1.0,0.0,0.0)),
    STRIP_COVE("coveledstrip", I18N.TR_NAME(I18N.Type.NONE, "Indirect Cove LED Strip"), 2, 1.0, 3,
        listOf(Vec3(.08,.14,-.23),Vec3(.08,.14,.23)),Vec3(.4,1.0,0.0)),
    STRIP_SURFACE("surfacelinearledstrip", I18N.TR_NAME(I18N.Type.NONE, "Surface Linear LED Strip"), 4, 2.0, 4,
        (0..3).map { Vec3(.075,0.0,-.72+it*.48) },Vec3(1.0,0.0,0.0)),
    STRIP_NEON("flexneonledstrip", I18N.TR_NAME(I18N.Type.NONE, "Flexible Neon LED Strip"), 3, 1.0, 1,
        (0..2).map { Vec3(.06,.12*cos(it*PI/2),-.32+it*.32) },Vec3(1.0,0.0,0.0)),
    LUXURY_RINGS("grandhalochandelier", I18N.TR_NAME(I18N.Type.NONE, "Grand Halo Ring Chandelier"), 16, 4.2, 2,
        (0..15).map { Vec3(1.8+(it/8)*1.25,(if(it<8)1.9 else 1.2)*cos(it*PI/4),(if(it<8)1.9 else 1.2)*sin(it*PI/4)) },Vec3(1.0,0.0,0.0)),
    LUXURY_CASCADE("crystalcascadechandelier", I18N.TR_NAME(I18N.Type.NONE, "Grand Crystal Cascade Chandelier"), 20, 5.0, 2,
        (0..19).map { Vec3(1.6+(it%5)*.65,(1.8-(it%5)*.22)*cos(it*PI/2),(1.8-(it%5)*.22)*sin(it*PI/2)) },Vec3(1.0,0.0,0.0)),
    LUXURY_BRANCH("sculpturalbranchchandelier", I18N.TR_NAME(I18N.Type.NONE, "Sculptural Branch Chandelier"), 18, 3.8, 2,
        (0..17).map { Vec3(2.0+(it%3)*.45,(.75+(it%3)*.65)*cos(it*PI/3),(.75+(it%3)*.65)*sin(it*PI/3)) },Vec3(1.0,0.0,0.0)),
    LUXURY_SPIRAL("spiralrainchandelier", I18N.TR_NAME(I18N.Type.NONE, "Spiral Rain Chandelier"), 20, 5.5, 2,
        (0..19).map { Vec3(1.6+it*.18,1.7*cos(it*PI/5),1.7*sin(it*PI/5)) },Vec3(1.0,0.0,0.0)),
    LUXURY_EMPIRE("grandempirechandelier", I18N.TR_NAME(I18N.Type.NONE, "Grand Empire Chandelier"), 16, 4.8, 2,
        (0..15).map { Vec3(if(it<8)1.8 else 3.0,(if(it<8)1.9 else 1.2)*cos(it*PI/4),(if(it<8)1.9 else 1.2)*sin(it*PI/4)) },Vec3(1.0,0.0,0.0)),
    HEAVY_FLOOD("stadiumwallfloodlight", I18N.TR_NAME(I18N.Type.NONE, "Heavy Duty Stadium Wall Floodlight"), 4, 1.2, 14,
        (0..3).map { Vec3(.62,-.15+(it/2)*.3,-.3+(it%2)*.6) },Vec3(.94,-.34,0.0)),
    HEAVY_BULKHEAD("armoredbulkheadlight", I18N.TR_NAME(I18N.Type.NONE, "Armored Oval Bulkhead Light"), 1, .55, 3,
        listOf(Vec3(.22,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    HEAVY_WALLPACK("industrialwallpacklight", I18N.TR_NAME(I18N.Type.NONE, "Industrial Wall Pack Light"), 2, .8, 8,
        listOf(Vec3(.36,-.14,-.18),Vec3(.36,-.14,.18)),Vec3(.6,-.8,0.0)),
    HEAVY_CANOPY("ruggedcanopylight", I18N.TR_NAME(I18N.Type.NONE, "Rugged Outdoor Canopy Light"), 4, 1.0, 6,
        (0..3).map { Vec3(.17,-.2+(it/2)*.4,-.2+(it%2)*.4) },Vec3(1.0,0.0,0.0)),
    HEAVY_YARD("industrialyardlight", I18N.TR_NAME(I18N.Type.NONE, "Industrial Yard Area Light"), 2, 1.4, 12,
        listOf(Vec3(.85,.1,-.22),Vec3(.85,.1,.22)),Vec3(.5,-.86,0.0)),
    RIG_CAGE("offshorecagedwellglass", I18N.TR_NAME(I18N.Type.NONE, "Offshore Caged Wellglass Light"), 1, 1.1, 4,
        listOf(Vec3(.64,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    RIG_LINEAR("offshoresealedlinearlight", I18N.TR_NAME(I18N.Type.NONE, "Offshore Sealed Linear Light"), 2, 2.0, 5,
        listOf(Vec3(.20,0.0,-.45),Vec3(.20,0.0,.45)),Vec3(1.0,0.0,0.0)),
    RIG_FLOOD("offshoreplatformfloodlight", I18N.TR_NAME(I18N.Type.NONE, "Offshore Platform Floodlight"), 2, 1.2, 16,
        listOf(Vec3(.63,.0,-.28),Vec3(.63,.0,.28)),Vec3(.94,-.34,0.0)),
    STREET_LED("modernledstreetlight", I18N.TR_NAME(I18N.Type.NONE, "Modern LED Street Light"), 1, 5.8, 10,
        listOf(Vec3(5.35,-1.05,0.0)),Vec3(-1.0,0.0,0.0)),
    STREET_BOULEVARD("boulevardlanternstreetlight", I18N.TR_NAME(I18N.Type.NONE, "Boulevard Lantern Street Light"), 2, 5.4, 4,
        listOf(Vec3(4.8,-.72,0.0),Vec3(4.8,.72,0.0)),Vec3(-1.0,0.0,0.0)),
    CHRISTMAS_FAIRY("christmasfairylights", I18N.TR_NAME(I18N.Type.NONE, "Warm White Christmas Fairy Lights"), 6, 2.0, 0,
        (0..5).map { Vec3(.08,-.07,-.80+it*.32) },Vec3(1.0,0.0,0.0)),
    CHRISTMAS_COLOR("multicolorchristmaslights", I18N.TR_NAME(I18N.Type.NONE, "Multicolor Christmas String Lights"), 6, 2.0, 0,
        (0..5).map { Vec3(.08,-.07,-.80+it*.32) },Vec3(1.0,0.0,0.0)),
    CHRISTMAS_ICICLE("christmasiciclelights", I18N.TR_NAME(I18N.Type.NONE, "Christmas Icicle Lights"), 6, 2.0, 0,
        (0..5).map { Vec3(.08,-.12-(it%3)*.12,-.80+it*.32) },Vec3(1.0,0.0,0.0)),
    CHRISTMAS_STAR("christmasstarlight", I18N.TR_NAME(I18N.Type.NONE, "Christmas Star Wall Light"), 1, 1.0, 0,
        listOf(Vec3(.06,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    HALLOWEEN_PUMPKIN("halloweenpumpkinstring", I18N.TR_NAME(I18N.Type.NONE, "Halloween Pumpkin String Lights"), 4, 2.0, 0,
        (0..3).map { Vec3(.10,-.12,-.72+it*.48) },Vec3(1.0,0.0,0.0)),
    HALLOWEEN_GHOST("halloweenghoststring", I18N.TR_NAME(I18N.Type.NONE, "Halloween Ghost String Lights"), 4, 2.0, 0,
        (0..3).map { Vec3(.10,-.12,-.72+it*.48) },Vec3(1.0,0.0,0.0)),
    HALLOWEEN_BAT("halloweenbatlight", I18N.TR_NAME(I18N.Type.NONE, "Halloween Bat Wall Light"), 1, 1.0, 0,
        listOf(Vec3(.07,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    HALLOWEEN_JACK("jackolanternlamp", I18N.TR_NAME(I18N.Type.NONE, "Jack O Lantern Table Lamp"), 1, .8, 0,
        listOf(Vec3(.42,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    DOT_PINHOLE("minipinholedot", I18N.TR_NAME(I18N.Type.NONE,"Mini Pinhole Dot Light"),1,.16,6,
        listOf(Vec3(.10,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    DOT_BEZEL("minibezeldot", I18N.TR_NAME(I18N.Type.NONE,"Mini Bezel Dot Spotlight"),1,.23,8,
        listOf(Vec3(.18,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    DOT_EYEBALL("minieyeballdot", I18N.TR_NAME(I18N.Type.NONE,"Mini Eyeball Dot Spotlight"),1,.24,8,
        listOf(Vec3(.17,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    DOT_DEEP("minideepbaffledot", I18N.TR_NAME(I18N.Type.NONE,"Mini Deep Baffle Dot Light"),1,.18,7,
        listOf(Vec3(.10,0.0,0.0)),Vec3(1.0,0.0,0.0),true),
    DOT_TWIN("minitwindot", I18N.TR_NAME(I18N.Type.NONE,"Mini Twin Dot Light"),2,.24,8,
        listOf(Vec3(.17,0.0,-.09),Vec3(.17,0.0,.09)),Vec3(1.0,0.0,0.0),true),
    EAVE_TWIN("twineavefloodlight", I18N.TR_NAME(I18N.Type.NONE,"Twin Adjustable Eave Floodlight"),2,.60,12,
        listOf(Vec3(.38,0.0,-.20),Vec3(.38,0.0,.20)),Vec3(.94,-.34,0.0)),
    FAN_CLASSIC("classicceilingfan", I18N.TR_NAME(I18N.Type.NONE,"Classic Four Blade Ceiling Fan"),1,1.8,0,
        listOf(Vec3(.56,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    FAN_MODERN("modernceilingfan", I18N.TR_NAME(I18N.Type.NONE,"Modern Three Blade Ceiling Fan"),1,1.8,0,
        listOf(Vec3(.49,0.0,0.0)),Vec3(1.0,0.0,0.0)),
    FAN_INDUSTRIAL("industrialceilingfan", I18N.TR_NAME(I18N.Type.NONE,"Industrial Five Block Ceiling Fan"),1,.65,0,
        listOf(Vec3(.59,0.0,0.0)),Vec3(1.0,0.0,0.0));

    val fan get()=this==FAN_CLASSIC || this==FAN_MODERN || this==FAN_INDUSTRIAL
    val fanWatts get()=if(this==FAN_CLASSIC)55.0 else if(this==FAN_MODERN)28.0 else if(this==FAN_INDUSTRIAL)300.0 else 0.0

    val miniDot get()=this in DOT_PINHOLE..DOT_TWIN
    val aimHeadCount get()=when {
        this==EAVE_TWIN || this==DOT_TWIN -> 2
        miniDot || this==WAREHOUSE || this==POT_GIMBAL || this==HEAVY_FLOOD || this==RIG_FLOOD -> 1
        else -> 0
    }
    val defaultTilt get()=Math.toDegrees(atan2(beam.y,beam.x))
    fun aimPivot(head: Int)=when {
        this==EAVE_TWIN -> Vec3(.13,0.0,if(head==0)-.20 else .20)
        this==DOT_TWIN -> Vec3(.045,0.0,if(head==0)-.09 else .09)
        miniDot || this==POT_GIMBAL -> Vec3(.045,0.0,0.0)
        else -> Vec3(.25,0.0,0.0)
    }
    fun aimHead(bulb: Int)=if(aimHeadCount==2)bulb else 0
    fun newAim()=LightAim(aimHeadCount,defaultTilt)

    val tableLamp get() = this == DESK || this == TABLE_DRUM || this == TABLE_BANKER || this == TABLE_TASK || this == WINDOW_CANDLE || this == HALLOWEEN_JACK
    val wallLamp get() = when(this) {
        CHRISTMAS_FAIRY,CHRISTMAS_COLOR,CHRISTMAS_ICICLE,CHRISTMAS_STAR,HALLOWEEN_PUMPKIN,HALLOWEEN_GHOST,HALLOWEEN_BAT,HEAVY_FLOOD,HEAVY_BULKHEAD,HEAVY_WALLPACK,HEAVY_YARD,RIG_FLOOD,WAREHOUSE,PORCH,MOTION,SCONCE_CYLINDER,SCONCE_FABRIC,SCONCE_GLOBE,SCONCE_CAGE,SCONCE_CANDLE,OUTDOOR_BARN -> true
        else -> false
    }
    val foyer get() = when(this) {
        CANDLE, CRYSTAL, WAGON, LANTERN, SPUTNIK -> true
        else -> false
    }
    val luxury get() = this in LUXURY_RINGS..LUXURY_EMPIRE
    val seasonalString get() = this == CHRISTMAS_FAIRY || this == CHRISTMAS_COLOR || this == CHRISTMAS_ICICLE || this == HALLOWEEN_PUMPKIN || this == HALLOWEEN_GHOST
    val strip get() = this in STRIP_CABINET..STRIP_NEON
    val street get() = this == STREET_LED || this == STREET_BOULEVARD
    val ceiling get() = fan || miniDot || this==EAVE_TWIN || luxury || this == HEAVY_CANOPY || this == RIG_CAGE || this == RIG_LINEAR || this == STRIP_RECESSED || this == RECESSED || this == CHANDELIER || this == SHOP || this == FACTORY || foyer || this == POT_BAFFLE || this == POT_TRIMLESS || this == POT_GIMBAL
    val uprightItem get() = this == FLOOR_DRUM || street || this == PARKING || tableLamp || this == OUTDOOR_BOLLARD || this == OUTDOOR_POST || ceiling
    val exposedBulbs get() = luxury || this == FLOOR_DRUM || this == WINDOW_CANDLE || this == HALLOWEEN_JACK || this == STREET_BOULEVARD || foyer || tableLamp || this == SCONCE_CANDLE || this == SCONCE_CAGE || this == SCONCE_GLOBE || this == OUTDOOR_POST || this == DESK || this == CHANDELIER || this == PORCH || this == HOLDER

    fun localFacing(v: Vec3, front: LRDU) = front.rotateOnXnLeft(v.xRot((-PI/2).toFloat()))
    fun worldPoint(origin: Vec3, side: Direction, front: LRDU, local: Vec3): Vec3 =
        origin.add(side.rotateFromXN(localFacing(local,front).add(-.5,0.0,0.0)))
    fun worldDirection(side: Direction, front: LRDU, local: Vec3) = side.rotateFromXN(localFacing(local,front)).normalize()
}
