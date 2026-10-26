package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.GridDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.UtilsClient
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.TransparentNodeDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class StepDownUnitDescriptor(name: String) :
    TransparentNodeDescriptor(name, StepDownUnitElement::class.java, StepDownUnitRender::class.java) {
    private val case = Eln.obj.getObj("transformatorCase")
    fun draw(front: Direction) = preserveMatrix {
        front.glRotateXnRef()
        for (part in arrayOf("Case", "DoorL", "DoorR")) case.getPart(part)?.draw()
    }
    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType) = true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper) = true
    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        preserveMatrix { GL11.glTranslatef(0f, -.5f, 0f); draw(Direction.XN) }
    }
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Selectable output: %1$ V or %2$ V", 120, 240))
        list.add(tr("Maximum input: %1$ V; maximum output: %2$ W", MainsStepDown.MAX_INPUT_VOLTS.toInt(), 100_000))
        list.add(tr("Step-down only; grid voltage must reach the selected output"))
        list.add(tr("Place on its own or install in a pole with a transformer slot"))
    }
}

enum class PoleLampStyle { ORIGINAL, COBRAHEAD, TWIN_LED }

class UtilityStreetLightDescriptor(name: String, val style: PoleLampStyle = PoleLampStyle.ORIGINAL) : GridDescriptor(name, Eln.obj.getObj("UtilityPoleTall"),
    UtilityStreetLightElement::class.java, UtilityStreetLightRender::class.java,
    "textures/wire.png", Eln.instance.veryHighVoltageCableDescriptor, 40) {
    private val fixture = Eln.obj.getObj(when(style) {
        PoleLampStyle.ORIGINAL -> "utilitystreetlamp"
        PoleLampStyle.COBRAHEAD -> "utilitycobrahead"
        PoleLampStyle.TWIN_LED -> "utilitytwinled"
    })
    override fun rotationIsFixed()=true
    val lampWatts get() = when(style) { PoleLampStyle.ORIGINAL -> 60.0;PoleLampStyle.COBRAHEAD -> 70.0;PoleLampStyle.TWIN_LED -> 120.0 }
    val heads get() = when(style) {
        PoleLampStyle.ORIGINAL -> listOf(net.minecraft.world.phys.Vec3(-1.095,5.30,0.0))
        PoleLampStyle.COBRAHEAD -> listOf(net.minecraft.world.phys.Vec3(-1.15,5.30,0.0))
        PoleLampStyle.TWIN_LED -> listOf(net.minecraft.world.phys.Vec3(-1.15,5.30,-.62),net.minecraft.world.phys.Vec3(-1.15,5.30,.62))
    }
    init {
        creativeTab = Eln.creativeTabLighting
        ghostGroup = GhostGroup().apply {
            for (y in 1..7) addElement(0, y, 0)
            for(y in 4..7)for(x in -2..2)for(z in -2..2)if(x!=0 || z!=0)addElement(x,y,z,mods.eln.ghost.GhostBlock.tClearance)

        }
    }
    fun drawLamp(front: Direction, lit: Boolean, installed: Boolean, heightStep: Int = 0, yaw: Int = 0, pitch: Int = 0, rating: Int = 10_000, transformerAngle: Float = mods.eln.gridnode.PoleSpanAlignment.frontAngle(front)) = preserveMatrix {
        if(installed)preserveMatrix {
            GL11.glRotatef(transformerAngle,0f,1f,0f);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
            try { GL11.glDisable(GL11.GL_TEXTURE_2D);mods.eln.utility.PoleTransformerVisual.draw(rating) }
            finally { GL11.glPopAttrib() }
        }
        front.glRotateXnRef()
        GL11.glTranslated(0.0,3.0,0.0)
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glTranslated(0.0, StreetLightMount.offset(heightStep), 0.0)
            GL11.glRotatef(yaw.toFloat(), 0f, 1f, 0f)
            GL11.glColor3f(.46f, .49f, .52f)
            fixture.getPart("Bracket")?.drawNoBind()
            val p=StreetLightMount.pivot.add(0.0,-3.0,0.0)
            GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated(pitch.toDouble(),0.0,0.0,1.0);GL11.glTranslated(-p.x,-p.y,-p.z)
            GL11.glColor3f(.22f, .25f, .28f)
            fixture.getPart("Housing")?.drawNoBind()
            if (lit) {
                UtilsClient.disableLight()
                GL11.glColor3f(1f, .94f, .76f)
                fixture.getPart("Lens")?.drawNoBind()
                UtilsClient.enableLight()
            } else {
                GL11.glColor3f(.12f, .16f, .19f)
                fixture.getPart("Lens")?.drawNoBind()
            }
        } finally { GL11.glPopAttrib() }
    }
    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        preserveMatrix {
            objItemScale(obj)
            Direction.ZN.glRotateXnRef()
            GL11.glTranslatef(0f, -1f, 0f); GL11.glScalef(.6f, .6f, .6f)
            draw(0f); drawLamp(Direction.XN, false, false)
        }
    }
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Grid-powered %1$ W street light with %2$-block pole connections", lampWatts.toInt(), 40))
        list.add(tr("Install a Mains Step-down Unit and select %1$ V or %2$ V", 120, 240))
        list.add(tr("Fixed ratio: 12.47 kV nominal primary; output follows grid voltage"))
        list.add(tr("Right-click with a Mains Step-down Unit to install it"))
        list.add(tr("Optional dusk control switches the lamp automatically"))
        list.add(tr("Adjust the lamp height with Up and Down in the menu"))
        list.add(tr("Rotate the lamp in the menu to follow angled streets"))
    }
}
