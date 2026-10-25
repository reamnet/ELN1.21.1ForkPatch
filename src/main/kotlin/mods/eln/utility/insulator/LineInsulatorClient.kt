package mods.eln.utility.insulator

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostBlock
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.*
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.transparent.*
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.io.DataInputStream

class LineInsulatorDescriptor(val voltage: InsulatorVoltage,val feed: Boolean): GridDescriptor(voltage.title(feed),Eln.obj.getObj(voltage.model),
    LineInsulatorElement::class.java,LineInsulatorRender::class.java,"textures/wire.png",Eln.instance.veryHighVoltageCableDescriptor,voltage.range) {
    init {
        creativeTab=Eln.creativeTabPowerElectronics
        if(voltage.length>1)ghostGroup=GhostGroup().apply { for(x in 1 until kotlin.math.ceil(voltage.length+.03).toInt())addElement(x,0,0,GhostBlock.tClearance) }
    }
    override val frontType get()=TransparentNode.FrontType.BlockSide
    override fun mustHaveFloor()=false
    override fun rotationIsFixed()=true
    override fun geometryBounds(): AABB {
        val radius=maxOf(.55,voltage.length-.45)
        return AABB(-radius,-radius,-radius,radius,radius,radius)
    }
    fun supported(world: Level,pos: BlockPos,front: Direction): Boolean {
        val support=pos.relative(front.toFacing())
        return world.getBlockState(support).isFaceSturdy(world,support,front.inverse.toFacing())
    }
    override fun checkCanPlace(coord: Coordinate?,front: Direction): String? {
        if(coord!=null && !supported(coord.world(),coord.pos,front))return tr("Insulators need a solid mounting face")
        return super.checkCanPlace(coord,front)
    }
    fun drawInsulator(front: Direction)=preserveMatrix {
        front.glRotateXnRef();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor3f(.42f,.45f,.47f);obj.getPart("main")?.drawNoBind()
            GL11.glColor3f(.57f,.66f,.68f);obj.getPart("Porcelain")?.drawNoBind()
            GL11.glColor3f(.60f,.54f,.35f);obj.getPart("Clamp")?.drawNoBind()
            if(feed) { GL11.glColor3f(.25f,.28f,.30f);obj.getPart("CableFeed")?.drawNoBind() }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType,item: ItemStack,vararg data: Any)=preserveMatrix {
        val scale=.85/maxOf(.5,voltage.length);GL11.glScaled(scale,scale,scale);GL11.glTranslated(.5-voltage.length/2,0.0,0.0);drawInsulator(Direction.XN)
    }
    override fun addInformation(itemStack: ItemStack?,entityPlayer: Player?,list: MutableList<String>,par4: Boolean) {
        list.add(tr("Single conductor; mount on floors, walls or ceilings"));list.add(tr("Connect spans at the outer clamp; no voltage conversion"))
        list.add(tr("Rated insulation: %1$ V; span limit: %2$ m",voltage.volts,voltage.range))
        if(feed)list.add(tr("Base cable terminals connect directly to the same conductor"))
        if(voltage==InsulatorVoltage.LOW)list.add(tr("Use for 120, 240, 480 or 600 V; select a service circuit for modern LV poles"))
    }
    override fun addCollisionBoxesToList(query: AABB,list: MutableList<AABB?>,world: Level?,x: Int,y: Int,z: Int) {
        val front=(world?.getBlockEntity(BlockPos(x,y,z)) as? TransparentNodeEntity)?.let { it.elementRender?.front ?: (it.node as? TransparentNode)?.element?.front } ?: Direction.XN
        val a=front.rotateFromXN(net.minecraft.world.phys.Vec3(-.5,-voltage.radius,-voltage.radius));val b=front.rotateFromXN(net.minecraft.world.phys.Vec3(-.5+voltage.length,voltage.radius,voltage.radius))
        val box=AABB(minOf(a.x,b.x),minOf(a.y,b.y),minOf(a.z,b.z),maxOf(a.x,b.x),maxOf(a.y,b.y),maxOf(a.z,b.z)).move(x+.5,y+.5,z+.5)
        if(query.intersects(box))list.add(box)
    }
}
class LineInsulatorRender(entity: TransparentNodeEntity,descriptor: TransparentNodeDescriptor): GridRender(entity,descriptor) {
    override val inventory=TransparentNodeElementInventory(0,1,this)
    var terminal=32;var volts=0.0;var amps=0.0;var connected=false
    override fun draw() {
        drawCables();mods.eln.client.terrain.TerrainModels.body(this,listOf(front)) { (transparentNodeDescriptor as LineInsulatorDescriptor).drawInsulator(front ?: Direction.XN) }
    }
    override fun networkUnserialize(input: DataInputStream) { super.networkUnserialize(input);terminal=input.readUnsignedByte();volts=input.readDouble();amps=input.readDouble();connected=input.readBoolean() }
    override fun newGuiDraw(side: Direction,player: Player)=LineInsulatorGui(player,this)
}
class LineInsulatorGui(player: Player,private val render: LineInsulatorRender): GuiContainerEln(BasicContainer(player,render.inventory,emptyArray())) {
    private lateinit var circuit: GuiButtonEln
    override fun newHelper()=GuiHelperContainer(this,244,208,8,126)
    override fun initGui() { super.initGui();circuit=newGuiButton(8,34,228,"") }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj);if(obj===circuit) { val choices=listOf(32,40,41,42);render.clientSendInt(80,choices[(choices.indexOf(render.terminal)+1)%4]) }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y);drawString(8,8,tr("Single Circuit Insulator"));circuit.displayString=circuitLabel(render.terminal);circuit.enabled=!render.connected
        drawString(8,66,Utils.plotUIP(render.volts,render.amps));drawString(8,86,tr("Disconnect spans before changing circuit"))
    }
}
