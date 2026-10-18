package mods.eln.transparentnode.ups

/** Immediate first warning, then a restrained cadence; warning stops on recovery. */
class UpsAlarm {
    private var previous=""
    private var countdown=0.0
    fun step(seconds: Double,status: String,enabled: Boolean): Boolean {
        val warning=if(enabled) when(status) {
            "BACKUP","EMPTY" -> "BACKUP"
            "LIMITED","PROTECTED","OVERTEMPERATURE" -> "OVERLOAD"
            else -> ""
        } else ""
        if(warning.isEmpty()) { previous="";countdown=0.0;return false }
        countdown-=seconds
        val beep=warning!=previous || countdown<=0.0
        if(beep)countdown=if(warning=="OVERLOAD")1.0 else 5.0
        previous=warning
        return beep
    }
}
