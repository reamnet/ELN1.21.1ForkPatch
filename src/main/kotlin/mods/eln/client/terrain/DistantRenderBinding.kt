package mods.eln.client.terrain

/** DH can replace its renderer while Minecraft still holds the same ClientLevel.
 * Dispose groups against their old registry before adopting the new one. */
internal class DistantRenderBinding {
    var register: Any?=null;private set
    var generation=0;private set
    fun bind(next: Any,release: ()->Unit): Boolean {
        if(register===next)return false
        release();register=next;generation++;return true
    }
    fun clear(release: ()->Unit) { release();register=null }
}
