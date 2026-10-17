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
        list.add(tr("Maximum input: %1$ V; maximum output: %2$ W", MainsStepDown.MAX_INPUT_VOLTS.toInt(), MainsStepDown.MAX_OUTPUT_WATTS.toInt()))
        list.add(tr("Step-down only; grid voltage must reach the selected output"))
        list.add(tr("Place on its own or install in a Utility Pole Street Light"))
    }
}

class UtilityStreetLightDescriptor(name: String) : GridDescriptor(name, Eln.obj.getObj("UtilityPole"),
    UtilityStreetLightElement::class.java, UtilityStreetLightRender::class.java,
    "textures/wire.png", Eln.instance.veryHighVoltageCableDescriptor, 40) {
    private val fixture = Eln.obj.getObj("utilitystreetlamp")
    init {
        creativeTab = Eln.creativeTabLighting
        ghostGroup = GhostGroup().apply {
            for (y in 1..4) addElement(0, y, 0)
            addElement(-1, 3, 0)
        }
    }
    fun drawLamp(front: Direction, lit: Boolean, installed: Boolean, heightStep: Int = 0, yaw: Int = 0) = preserveMatrix {
        front.glRotateXnRef()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            if (installed) {
                GL11.glColor3f(.52f, .55f, .57f)
                fixture.getPart("Transformer")?.drawNoBind()
            }
            GL11.glTranslated(0.0, StreetLightMount.offset(heightStep), 0.0)
            GL11.glRotatef(yaw.toFloat(), 0f, 1f, 0f)
            GL11.glColor3f(.46f, .49f, .52f)
            fixture.getPart("Bracket")?.drawNoBind()
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
        list.add(tr("Grid-powered %1$ W street light with %2$-block pole connections", 60, 40))
        list.add(tr("Install a Mains Step-down Unit and select %1$ V or %2$ V", 120, 240))
        list.add(tr("Base terminals also supply the regulated output"))
        list.add(tr("Right-click with a Mains Step-down Unit to install it"))
        list.add(tr("Optional dusk control switches the lamp automatically"))
        list.add(tr("Adjust the lamp height with Up and Down in the menu"))
        list.add(tr("Rotate the lamp in the menu to follow angled streets"))
    }
}
