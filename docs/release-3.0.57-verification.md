# 3.0.57: Industrial equipment, dryer and web jukebox

Adds the ten accepted industrial systems, their recipes and original graphics: a programmable
load bank, grid console, protective relay, surge arrester and distribution-pole kit, pad transformer,
motor drive, load shedder, removable-pack EV charger, welding station and electric forklift.
The forklift carries equipment pallets and uses a rechargeable traction battery. The existing
dryer now has a separate solid-door design. A powered HTTPS/YouTube jukebox is also included.
See the [equipment guide](industrial-equipment.md) and [jukebox guide](powered-web-jukebox.md).

New electrical loads are resistive. Pad transformers and drives use passive scalar transformer
taps instead of introducing additional constant-power port probes. Voltage changes therefore
propagate through their ratios, and transferred electrical power includes winding losses.
Protection/control timers advance with physical time; load shedding restores priorities gradually.
Lightning energy is charged to the arrester cartridge without also imposing it as a load on a
healthy grid. Battery charging uses actual input energy, capacity and charge-rate limits.

All ten stationary bodies and the installed pole kit bake into terrain sections. Ten new machine
previews and four accessory icons also use baked item quads. The moving forklift and pallet use
entity rendering. Existing item IDs stay unchanged; new transparent IDs occupy group 126, 0–9,
and shared accessories occupy group 117, 0–3. Clients and servers must both update because the
pole status packet and new forklift controls use the new protocol version.

## Verification

The **969-test full suite passes across 233 suites**, with no failures, errors or skips.
After the final battery charge-rate guard, **23 focused device/audio tests pass across two
suites**, again without failures or skips. [Full results](verification/release-3.0.57/units-full.json);
[final focused results](verification/release-3.0.57/units-final.json).

The tests cover passive energy balance and voltage sag, overload/differential/signal trips,
priority shedding/restoration, finite surge budgets, avoiding a second grid load for lightning,
battery charge/capacity/rate limits, welding input/output conservation, tool component retention,
settings persistence, automation restrictions, HTTPS URL validation and positional mono audio.

The native campaign passes **12 checks** with Minecraft 1.21.1 / NeoForge 21.1.249,
Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3.
It verifies actual placed-cable power, loaded output voltage, sag propagation, relay latching,
priority shedding, material/energy-gated welding, charged packs, live remote contacts, a real
lightning event and installed pole-kit publication. It opens all ten real server menus at normal
GUI scale and audits all fourteen new baked item previews against their source geometry.

The gallery contains **1,944 baked machine quads**. Its nominal 50 kW load consumes approximately
49.984 kW after feeder loss. The loaded pad secondary measures 239.850 V, and the current-bounding
drive measures 399.992 V into its test load. Client control packets move the forklift and drain its
pack; raising/lowering, pallet pickup/drop and actual entity save/load preserve the cargo once.

A selected public HTTPS Vorbis file is downloaded asynchronously and reaches a native positional
streaming audio channel. The audio check uses OpenAL Soft's null backend, verifying decoding and
playback events without a physical speaker. YouTube requires optional current client media tools;
live YouTube playback is not asserted by this campaign. The direct HTTPS path is exercised.

These rendering participation/count checks establish correctness, not a hardware FPS benchmark.
[Native results](verification/release-3.0.57/native.json).

- [Industrial gallery](verification/release-3.0.57/industrial-gallery.png)
- [Different washer and dryer fronts](verification/release-3.0.57/washer-and-dryer.png)
- [Forklift and transported pallet](verification/release-3.0.57/forklift-and-pallet.png)
- [Web jukebox controls](verification/release-3.0.57/gui-jukebox.png)
- [Protective relay controls](verification/release-3.0.57/gui-relay.png)
- [Grid console controls](verification/release-3.0.57/gui-scada.png)

Run the opt-in development campaign with `-Deln.industrialExpansionChecks=true`. It creates a
separate test world, writes its report/screenshots under `run/client/industrial-expansion-checks/`
and exits. Do not enable that test flag during normal play.

Source-only prerelease; compile with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.57.jar`. [Ubuntu instructions](building-on-ubuntu-24.04.md).
