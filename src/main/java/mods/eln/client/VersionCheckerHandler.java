package mods.eln.client;

/** Compatibility shim: this fork never contacts the old upstream update service. */
public final class VersionCheckerHandler {
    private static final VersionCheckerHandler INSTANCE = new VersionCheckerHandler();
    private VersionCheckerHandler() {}
    public static VersionCheckerHandler getInstance() { return INSTANCE; }
    public void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {}
}
