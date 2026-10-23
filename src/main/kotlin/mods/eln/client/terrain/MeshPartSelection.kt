package mods.eln.client.terrain

/** Part and dynamic-region selection is thread-local: integrated-server bounds lookups stay independent. */
object MeshPartSelection {
    private data class Selection(val names: Set<String>,val only: Boolean,val automatic: Boolean=false)
    private val current=ThreadLocal<Selection>()
    private val partDepth=ThreadLocal.withInitial { 0 }
    private val dynamicDepth=ThreadLocal.withInitial { 0 }
    @JvmStatic fun allows(name: String): Boolean = current.get()?.let {
        if(it.automatic && dynamicDepth.get()>0)true
        else ((name in it.names) || (it.automatic && name.startsWith("led",ignoreCase=true)))==it.only
    } ?: true
    @JvmStatic fun staticPass()=current.get()?.only==false
    @JvmStatic fun suppressDirectGeometry()=current.get()?.let { it.automatic && it.only && partDepth.get()==0 && dynamicDepth.get()==0 }==true
    @JvmStatic fun beginPart(name: String): Boolean {
        if(!allows(name))return false
        partDepth.set(partDepth.get()+1);return true
    }
    @JvmStatic fun endPart() { partDepth.set(partDepth.get()-1) }
    @JvmStatic fun beginAnimated(): Boolean {
        if(current.get()?.let { it.automatic && !it.only }==true)return false
        dynamicDepth.set(dynamicDepth.get()+1);return true
    }
    @JvmStatic fun beginDynamic(): Boolean {
        if(staticPass())return false
        dynamicDepth.set(dynamicDepth.get()+1);return true
    }
    @JvmStatic fun endDynamic() { dynamicDepth.set(dynamicDepth.get()-1) }
    /** Nested item models own their selection; a parent's live-part filter must not hide them. */
    @JvmStatic fun unfiltered(draw: Runnable) {
        val previous=current.get();val parts=partDepth.get();val dynamic=dynamicDepth.get()
        current.remove();partDepth.set(0);dynamicDepth.set(0)
        try { draw.run() } finally {
            if(previous!=null)current.set(previous) else current.remove()
            partDepth.set(parts);dynamicDepth.set(dynamic)
        }
    }
    fun <T> dynamic(draw:()->T): T? {
        if(!beginDynamic())return null
        return try { draw() } finally { endDynamic() }
    }
    fun <T> with(names: Set<String>,only: Boolean,draw:()->T)=select(Selection(names,only),draw)
    fun <T> automatic(names: Set<String>,only: Boolean,draw:()->T)=select(Selection(names,only,true),draw)
    private fun <T> select(selection: Selection,draw:()->T):T {
        val previous=current.get();current.set(selection)
        return try { draw() } finally { if(previous==null)current.remove() else current.set(previous) }
    }
}
