package mods.eln.gui

import mods.eln.i18n.I18N.tr
import mods.eln.sixnode.lampsocket.LightAim

/** Compact controls shared by wired/wireless fixtures and the construction cart. */
class LightAimControls(gui: GuiContainerEln,y: Int,private val aim: LightAim,private val send: (Int,Int,Double)->Unit) {
    private var selected=0
    private val head=gui.newGuiButton(8,y,76,"")
    private val reset=gui.newGuiButton(88,y,80,tr("Reset aim"))
    private val left=gui.newGuiButton(8,y+24,28,"<").apply { setComment(0,tr("Pan left")) }
    private val pan=gui.newGuiButton(40,y+24,92,"").apply { enabled=false }
    private val right=gui.newGuiButton(136,y+24,32,">").apply { setComment(0,tr("Pan right")) }
    private val down=gui.newGuiButton(8,y+48,28,"-").apply { setComment(0,tr("Tilt down")) }
    private val tilt=gui.newGuiButton(40,y+48,92,"").apply { enabled=false }
    private val up=gui.newGuiButton(136,y+48,32,"+").apply { setComment(0,tr("Tilt up")) }
    fun refresh() {
        head.enabled=aim.heads>1;head.displayString=tr("Head %1$/%2$",selected+1,aim.heads)
        pan.displayString=tr("Pan: %1$°",aim.pan(selected).toInt())
        tilt.displayString=tr("Tilt: %1$°",aim.tilt(selected).toInt())
    }
    private fun change(axis: Int,value: Double) {
        if(aim.set(selected,axis,value))send(selected,axis,if(axis==0)aim.pan(selected) else aim.tilt(selected))
    }
    fun event(obj: IGuiObject) {
        when(obj) {
            head -> selected=(selected+1)%aim.heads
            left -> change(0,aim.pan(selected)-5)
            right -> change(0,aim.pan(selected)+5)
            down -> change(1,aim.tilt(selected)-5)
            up -> change(1,aim.tilt(selected)+5)
            reset -> { change(0,0.0);change(1,aim.defaultTilt) }
        }
        refresh()
    }
}
