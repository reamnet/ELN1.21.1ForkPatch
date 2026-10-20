package mods.eln.utility

import mods.eln.gridnode.electricalpole.ElectricalPoleRender
import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.BasicContainer
import mods.eln.transparentnode.utilitylight.MainsStepDown
import net.minecraft.world.entity.player.Player

class LegacyPoleGui(player: Player,private val r: ElectricalPoleRender,private val transformer: Boolean): GuiContainerEln(BasicContainer(player,r.inventory,emptyArray())) {
    private lateinit var terminal: GuiButtonEln
    private var rating: GuiButtonEln?=null;private var fuse: GuiButtonEln?=null
    override fun newHelper()=GuiHelperContainer(this,240,238,8,156)
    override fun initGui() {
        super.initGui();terminal=newGuiButton(8,34,224,"")
        if(transformer) { rating=newGuiButton(8,58,224,"");fuse=newGuiButton(8,82,224,"").apply { setComment(0,tr("Replace a blown fuse by right-clicking with a Pole Fuse Link")) } }
    }
    override fun guiObjectEvent(obj: IGuiObject) {
        super.guiObjectEvent(obj)
        when(obj) {
            terminal->{ val ts=listOf(32,50,40,41,42);r.clientSendInt(87,ts[(ts.indexOf(r.display.terminal)+1)%ts.size]) }
            rating->r.clientSendInt(86,MainsStepDown.RATINGS[(MainsStepDown.RATINGS.indexOf(r.display.rating)+1)%5])
            fuse->r.clientSendInt(89,0)
        }
    }
    override fun postDraw(f: Float,x: Int,y: Int) {
        super.postDraw(f,x,y)
        drawString(8,8,tr("Utility Pole Connections"))
        terminal.displayString=tr("Cable: %1$",ServiceTerminals.label(r.display.terminal))
        rating?.displayString=tr("Transformer fuse rating: %1$ kW",r.display.rating/1000)
        fuse?.apply { enabled=!r.display.fuseBlown;displayString=if(r.display.fuseBlown)tr("Fuse blown") else if(r.display.fuseClosed)tr("Open fuse") else tr("Close fuse") }
        drawString(8,114,tr("Secondary rack passes 120/240 V service"))
        if(transformer)drawString(8,130,tr("Legacy converter retains its 4:1 ratio"))
    }
}
