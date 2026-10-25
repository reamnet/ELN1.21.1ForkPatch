package mods.eln.integration.electroenergetics

import net.neoforged.fml.ModList

/** Optional API access; no CEE classes are linked when that mod is absent. */
object CeeConfig {
    const val MOD_ID="electroenergetics"
    data class Scale(val wattsPerFeTick: Double,val maxWatts: Double) { val joulesPerFe get()=wattsPerFeTick/20.0 }
    fun present()=ModList.get().isLoaded(MOD_ID)
    private val serverMethod by lazy { runCatching { Class.forName("com.george_vi.electroenergetics.config.CEEConfigs").getMethod("server") }.getOrNull() }
    fun read(): Scale? {
        if(!present())return null
        return runCatching {
            val server=serverMethod?.invoke(null) ?: return null
            fun number(name: String): Double {
                val value=server.javaClass.getField(name).get(server)
                return (value.javaClass.getMethod("get").invoke(value) as Number).toDouble()
            }
            val rate=number("wattFeTConversionRate");val max=number("converterMaxPowerKw")*1000
            if(!rate.isFinite() || rate<=0 || !max.isFinite() || max<=0)null else Scale(rate,max)
        }.getOrNull()
    }
}
