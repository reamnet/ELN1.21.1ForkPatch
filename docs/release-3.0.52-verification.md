# 3.0.52: line insulators, fire alarms and CEE power bridge

This release adds 18 items in unused legacy slots **125:43–60**. Existing IDs are unchanged. Static bodies and GUI/ground item previews use baked geometry; alarm LEDs, strobes and pull handles remain dynamic so switching does not rebuild terrain sections.

## Single-conductor insulators

Each rating has a span-only **Line Insulator** and a **Cable Feed Insulator** with ordinary cable terminals at its base:

| Insulation rating | Maximum span | Geometry |
| --- | --- | --- |
| 800 V | 40 m | Small two-bell porcelain; shared by lower voltages |
| 3.2 kV | 64 m | Compact version of wooden-pole porcelain |
| 12.5 kV | 128 m | Same porcelain as the distribution poles |
| 69 kV | 256 m | Same porcelain as the 69 kV steel poles |
| 230 kV | 256 m | Same porcelain as the 230 kV steel poles |

All ten mount on floors, walls and ceilings, with the wire terminating at the outer clamp. They carry a single electrical conductor and do not convert voltage. Feed variants join the fixed cable and overhead span to the same conductor. The largest units reserve passable clearance for their longer bodies. Removing the supporting block removes the insulator.

For modern secondary poles, select L1, L2 or 240 V on an 800 V insulator before connecting it. Disconnect spans before changing that selection. Aiming at an individual LV rack tie point now retains that circuit when the other endpoint is a single-conductor device, in either click order. Pole-to-pole connections still bundle the service circuits automatically. Higher-voltage insulators retain single-phase branch selection when joining three-phase poles.

## Fire alarm system

Seven devices are available: **Control Panel, Pull Station, Horn Strobe, Bell, Strobe, Smoke Detector and Heat Detector**. All can be mounted on a surface. Names, channels, enable controls and detector settings are available in their menus.

A panel uses **120 V**. Matching-channel devices within **128 m**, in the same dimension, receive virtual power paid for by the panel's actual electrical draw. Alternatively, each field device accepts a **24 V** wired supply. Leave the channel blank for wired-only operation. Devices can also combine channel linkage and ELN signal wiring.

On the outward face, the left terminal accepts a trigger signal and the right terminal outputs alarm state. The panel additionally has trouble output at the top and reset input at the bottom. Side power terminals use the bottom cable position. Signal terminals use ELN's normal signal voltage, not the power supply voltage.

The panel latches alarms, supports silence/resound, reset and a ten-second test, and reports mains loss or missing previously linked devices. Reset refuses active detectors, pulled stations or an asserted wired trigger. “Forget missing” clears the remembered device list. Empty-handed right-click pulls/resets a pull station; sneak-right-click opens setup. Silence stops horn/bell audio while strobes remain active.

The panel includes a rechargeable **24 Wh** backup, initially empty. Charging and operation consume real simulated energy; remaining charge survives saving. Linked loads are assigned to one nearby panel so redundant panels do not both pay for the same device. Wired field supplies are excluded from the panel's virtual power bill.

Smoke detection searches connected air for fire or lit campfires. Full solid blocks, including glass, block the smoke path. Heat detectors sample nearby fire, lava, magma, lit campfires and simulated room temperature, so nearby heat can trigger through a thin partition even when smoke cannot reach the sensor. This is a gameplay approximation of heat and smoke, not a particle simulation. Detection radius and heat threshold are configurable. Searches are staggered and read already-loaded chunks only; they never force chunk loads. Channel leases and supervision also expire without keeping chunks loaded.

Horn, bell and panel trouble sounds are original synthesized assets. Static bodies are baked; the lights, handle and flash animation remain dynamic.

## ELN to Create Electro Energetics Power Bridge

The bridge converts real ELN electrical input into FE for **CEE's supported Converter input**. CEE remains optional; an absent or unavailable CEE configuration disables charging/export without preventing ELN from loading.

1. Connect ELN cable to a blue input terminal on the bridge.
2. Set nominal input voltage, input watts and nominal current in its GUI.
3. Place CEE's native **Converter** against the bridge's **orange face**. Set it to **source mode**, with its wire terminals pointing away from the bridge. The back of the CEE Converter must touch the orange face.
4. Connect native CEE wires from that Converter to the destination CEE circuit.

The ELN bridge itself does not expose native CEE wire terminals. Its FE output is pull-only, cannot accept FE, and does not backfeed the ELN input. It reads CEE's configured watts-per-FE-per-tick and converter power limit. At CEE's default **34 W per FE/t**, one FE represents **1.7 J**. Conversion is **95% efficient**, with a **200 kJ** physical-energy buffer. Export calls share a per-tick power budget, including fractional FE quota, and simulated extraction does not spend energy. Stored joules remain joules when the conversion ratio changes.

Input is a passive impedance, so power follows voltage squared. It adds no converter probes or nonlinear convergence iterations. The GUI shows actual input, buffer, scale, output limit and status. Overvoltage protection stops input above 125% of the selected nominal voltage. Default settings are 3.2 kV, 100 kW and 40 nominal amps; the GUI supports other supplies.

## Verification

Final automated and native results are stored under [release-3.0.52](verification/release-3.0.52/).

- **921 tests in 224 suites passed**, with no failures, errors or skips. Unit coverage checks six-face insulator orientation, independent service selection, real span voltage drop and power balance; alarm backup accounting, latching, leases and redundant-panel billing; and CEE energy conservation, export simulation, shared quotas, fractional throughput and conversion-ratio changes.
- Native Minecraft 1.21.1 / NeoForge 21.1.249 checks all **60 insulator variant/orientation combinations**, actual cable-fed 230 kV spans, L2 branch selection, wired-only alarms, smoke blocked by glass, heat through the sealed partition, opening the smoke path, pull/silence/reset and mains-loss backup operation.
- The optional-mod run uses **Create 6.0.10 and CEE 1.1.4**. A real CEE Converter pulls energy from the bridge and heats a native CEE resistor through native CEE wires. The report records input, export, buffer and loss energy.
- Static-body checks pass for every placed new device. All **38 GUI/ground previews** pass, covering the 18 new items and the existing secondary pole used by the fixture.
- Runtime rendering includes Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3. Mesa llvmpipe software rendering validates correctness here; these checks are not an FPS benchmark.
- Data generation and translation regeneration are included in the final checks. Recipes become progressively more expensive for higher-voltage insulators, and every new device has a recipe.

See [native results](verification/release-3.0.52/native.json), [unit results](verification/release-3.0.52/units.json), [insulators](verification/release-3.0.52/insulators.png), [alarm devices](verification/release-3.0.52/alarm-system.png) and [CEE connection](verification/release-3.0.52/cee-bridge.png).

This is a source-only prerelease. Build with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.52.jar`. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
