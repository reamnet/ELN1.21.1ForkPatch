package mods.eln.utility

import net.minecraft.nbt.CompoundTag
import kotlin.math.abs
import mods.eln.Eln
import mods.eln.misc.stackFromNbt
import mods.eln.misc.writeToNBT
import net.minecraft.world.item.ItemStack

class CutoutFuse {
    private var cartridge: ItemStack?=null // Null is the included legacy link, materialised lazily.
    val hasCartridge get()=cartridge?.isEmpty!=true
    fun rating(fallback: Double)=PoleHardware.fuse(cartridge)?.amps?.takeIf { it>0 } ?: fallback
    fun insertCartridge(stack: ItemStack): Boolean {
        if(!UtilityItems.isFuse(stack) || (hasCartridge && !blown))return false
        cartridge=stack.copyWithCount(1);blown=false;closed=true;heat=0.0;pickupWindow=.5;return true
    }
    fun removeCartridge(): ItemStack {
        val result=if(!hasCartridge)ItemStack.EMPTY else if(blown)Eln.findItemStack(PoleHardware.spentName,1) else cartridge ?: Eln.findItemStack("Pole Fuse Link",1)
        cartridge=ItemStack.EMPTY;closed=false;return result
    }
    var blown=false
        private set
    var closed=true
        private set
    var lastLoaded=false
    private var heat=0.0
    private var pickupWindow=0.0
    fun pickup(seconds: Double,amps: Double): Boolean {
        if(!closed || pickupWindow<=0 || !seconds.isFinite() || seconds<=0)return false
        pickupWindow=(pickupWindow-seconds).coerceAtLeast(0.0)
        if(abs(amps)>.02) { pickupWindow=0.0;return true };return false
    }
    fun step(seconds: Double,amps: Double,rating: Double,outputLimited: Boolean=false): Boolean {
        if(!seconds.isFinite() || seconds<=0 || !closed || blown)return false
        if(abs(amps)>.02)lastLoaded=true
        val ratio=abs(amps)/this.rating(rating).coerceAtLeast(.1)
        heat=if(outputLimited || ratio>1.05)heat+seconds*if(outputLimited)1.0 else (ratio*ratio-1).coerceAtLeast(1.0) else (heat-seconds).coerceAtLeast(0.0)
        if(heat<.25)return false
        blown=true;closed=false;heat=0.0;pickupWindow=0.0;return true
    }
    fun operate(): Boolean { if(blown || !hasCartridge)return false;closed=!closed;pickupWindow=if(closed).5 else 0.0;return true }
    fun replace() { if(!hasCartridge)cartridge=null;blown=false;closed=true;heat=0.0;pickupWindow=.5 }
    fun write(tag: CompoundTag,prefix: String) { cartridge?.let { tag.put(prefix+"Cartridge",CompoundTag().also(it::writeToNBT)) };tag.putBoolean(prefix+"Blown",blown);tag.putBoolean(prefix+"Closed",closed);tag.putDouble(prefix+"Heat",heat);tag.putBoolean(prefix+"Loaded",lastLoaded) }
    fun read(tag: CompoundTag,prefix: String) { cartridge=if(tag.contains(prefix+"Cartridge"))stackFromNbt(tag.getCompound(prefix+"Cartridge")) else null;blown=tag.getBoolean(prefix+"Blown");closed=hasCartridge && !blown && (!tag.contains(prefix+"Closed") || tag.getBoolean(prefix+"Closed"));heat=tag.getDouble(prefix+"Heat").takeIf { it.isFinite() }?.coerceIn(0.0,.25) ?: 0.0;lastLoaded=tag.getBoolean(prefix+"Loaded") }
}

/** Gang trips; retry timers advance once per physical tick, never during solver retries. */
class RecloserControl {
    var pickupAmps=100.0
    var retrySeconds=3.0
    var maxFailures=3
    var closed=true
        private set
    var locked=false
        private set
    var failures=0
        private set
    var remaining=0.0
        private set
    private var retrying=false
    private var stable=0.0
    var manualOff=false
        private set
    fun reset() { locked=false;failures=0;remaining=0.0;retrying=false;stable=0.0;manualOff=false;closed=true }
    fun operate() { if(closed) { manualOff=true;closed=false;remaining=0.0 } else reset() }
    /** Returns true whenever the mechanical contact changes position. */
    fun step(seconds: Double,currents: List<Double>): Boolean {
        if(!seconds.isFinite() || seconds<=0 || locked || manualOff)return false
        if(closed) {
            if(currents.any { it.isFinite() && abs(it)>pickupAmps }) {
                closed=false;stable=0.0
                if(retrying)failures++
                locked=failures>=maxFailures
                remaining=if(locked)0.0 else retrySeconds
                return true
            }
            stable+=seconds
            if(stable>=10.0) { failures=0;retrying=false }
        } else {
            remaining=(remaining-seconds).coerceAtLeast(0.0)
            if(remaining==0.0) { closed=true;retrying=true;stable=0.0;return true }
        }
        return false
    }
    fun write(tag: CompoundTag) { tag.putDouble("recloserPickup",pickupAmps);tag.putDouble("recloserDelay",retrySeconds);tag.putInt("recloserMax",maxFailures);tag.putInt("recloserFailures",failures);tag.putBoolean("recloserClosed",closed);tag.putBoolean("recloserLocked",locked);tag.putBoolean("recloserManualOff",manualOff);tag.putBoolean("recloserRetrying",retrying);tag.putDouble("recloserRemaining",remaining);tag.putDouble("recloserStable",stable) }
    fun read(tag: CompoundTag) {
        pickupAmps=tag.getDouble("recloserPickup").takeIf { it.isFinite() && it in 1.0..2000.0 } ?: 100.0
        retrySeconds=tag.getDouble("recloserDelay").takeIf { it.isFinite() && it in 1.0..60.0 } ?: 3.0
        maxFailures=tag.getInt("recloserMax").takeIf { it in 1..5 } ?: 3
        failures=tag.getInt("recloserFailures").coerceIn(0,maxFailures);locked=tag.getBoolean("recloserLocked");manualOff=tag.getBoolean("recloserManualOff")
        closed=!locked && !manualOff && (!tag.contains("recloserClosed") || tag.getBoolean("recloserClosed"));retrying=tag.getBoolean("recloserRetrying")
        remaining=tag.getDouble("recloserRemaining").takeIf { it.isFinite() }?.coerceIn(0.0,retrySeconds) ?: 0.0
        stable=tag.getDouble("recloserStable").takeIf { it.isFinite() }?.coerceIn(0.0,10.0) ?: 0.0
    }
}
