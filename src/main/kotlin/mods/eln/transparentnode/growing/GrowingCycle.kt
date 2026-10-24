package mods.eln.transparentnode.growing

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import kotlin.math.exp

/** No world edits or random ticks: crop work is earned only after a committed electrical step. */
class GrowingCycle {
    companion object {
        const val SECONDS=180.0
        const val PLANTS=20
        fun crop(stack: ItemStack)=(stack.item as? BlockItem)?.block as? CropBlock
        fun temperatureFactor(celsius: Double)=when {
            !celsius.isFinite() || celsius<=5 || celsius>=40 -> 0.0
            celsius<18 -> (celsius-5)/13
            celsius>28 -> (40-celsius)/12
            else -> 1.0
        }
    }
    var progress=0.0;private set
    var temperature=20.0;private set
    fun account(seconds: Double,ambient: Double,lampWatts: Double,heaterWatts: Double,light: Double,canGrow: Boolean) {
        if(!seconds.isFinite() || seconds<=0 || !ambient.isFinite() || !lampWatts.isFinite() || !heaterWatts.isFinite())return
        // The ventilated rack's air/soil mass stores heat. Most lamp radiation leaves
        // the open rack; the thermostat switches a real passive heating load.
        val equilibrium=ambient+(lampWatts.coerceAtLeast(0.0)*.2+heaterWatts.coerceAtLeast(0.0))/110.0
        temperature=equilibrium+(temperature-equilibrium)*exp(-110*seconds/10_000)
        if(canGrow && lampWatts>0 && light.isFinite())
            progress=(progress+seconds*light.coerceIn(0.0,1.0)*temperatureFactor(temperature)).coerceAtMost(SECONDS)
    }
    fun reset() { progress=0.0 }
    fun restore(work: Double,celsius: Double) {
        progress=if(work.isFinite())work.coerceIn(0.0,SECONDS) else 0.0
        temperature=if(celsius.isFinite())celsius.coerceIn(-100.0,250.0) else 20.0
    }
}
