package mods.eln.client.terrain

/** Thread-local selection: OBJ bounds queries on an integrated server remain unaffected. */
object MeshPartSelection {
    private data class Selection(val names: Set<String>, val only: Boolean)
    private val current = ThreadLocal<Selection>()
    @JvmStatic fun allows(name: String): Boolean = current.get()?.let { (name in it.names) == it.only } ?: true
    @JvmStatic fun staticPass(): Boolean = current.get()?.only == false
    fun <T> with(names: Set<String>, only: Boolean, draw: () -> T): T {
        val previous = current.get()
        current.set(Selection(names, only))
        return try { draw() } finally { if (previous == null) current.remove() else current.set(previous) }
    }
}
