package mods.eln.transparentnode.offgrid

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.nbt.NbtThermalLoad

/** ELN thermal loads store a rise above local ambient, not an absolute cell temperature. */
class CabinetTemperature(private val owner: TransparentNodeElement,private val load: NbtThermalLoad) {
    private var ambient=20.0
    private var remaining=0.0
    val celsius get()=ambient+load.temperatureCelsius
    fun advance(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        remaining-=seconds
        if(remaining<=0) {
            // Unplaced circuit fixtures and unloaded dimensions have no climate to sample.
            // Retain the last value without loading a world or chunk.
            if(owner.node?.coordinate?.worldExist==true)ambient=owner.getAmbientTemperatureCelsius()
            remaining=.5
        }
    }
}
