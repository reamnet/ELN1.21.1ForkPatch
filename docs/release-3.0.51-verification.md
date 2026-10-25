# 3.0.51: pole cleanup and electrical switchgear

The top-mounted insulator on wooden 12.5 kV poles now copies the exact A/C
geometry, translated above the wood. It has the same diameter, shed count and
height. This applies to the middle B insulator on three-phase poles and the top
insulator on single-phase poles. Wire ends and equipment jumpers move to the new
7.15 m top. The buried, unused lower pin is no longer rendered on single-phase
poles. Static terrain and item geometry stay baked.

## Street-light poles and existing worlds

“Utility Pole Street Light” and “Utility Pole Cobrahead Street Light” are hidden
from creative tabs and no longer craftable. Their old registered IDs, models and
classes remain available so placed poles and saved items can still load.

“Utility Pole Twin LED Street Light” retains its item name, registry identity and
legacy ID **124:13**. It now uses the newer distribution-pole implementation and
appears in the power electronics tab with the other poles. It carries independent
L1 120 V, L2 120 V and 240 V circuits using the LV rack and bottom cable terminals.
It has no high-voltage terminals, transformer or internal fuse. Its two original
LED heads consume 120 W combined at their selected nominal lamp supply.

The GUI chooses whether the lamps draw from L1 at 120 V or the separate 240 V
circuit. There is no voltage conversion: upstream supply must feed the chosen
circuit, and lamp power follows actual voltage squared through the resistor.
Height, pan, tilt, dusk control, independent service circuits and multimeter
readings use the newer pole code. Both heads emit light at their own positions.

Migration preserves lamp voltage, aiming, height and dusk/on-off settings. Former
transformer hardware and fuse cartridges remain recoverable through two
extraction-only GUI slots. Recovery does not repeat after saving. **Former HV
spans to this converted twin pole disconnect; reconnect it to upstream LV service.**
A saved HV terminal is rejected before creating electrical connections, rather
than being reinterpreted as a low-voltage feed. Existing LV service spans remain
valid.

## New switchgear

| Item | Independent poles | Contact arrangement | Rating per pole | Coil/input power |
| --- | --- | --- | --- | --- |
| Single Pole Contactor | 1 | NO | 1000 V / 100 A | 8 W |
| Three Pole Contactor | 3 | NO | 1000 V / 100 A | 12 W |
| Two Pole Changeover Relay | 2 | COM / NO / NC | 600 V / 20 A | 2 W |
| Four Pole Changeover Relay | 4 | COM / NO / NC | 600 V / 20 A | 3 W |
| Solid State Relay | 1 | NO | 600 V / 40 A | 0.5 W |
| Three Pole Solid State Relay | 3 | NO | 600 V / 40 A | 1.5 W |

Each has selectable ELN signal, 120 V coil and 240 V coil control. Power contacts
remain electrically isolated from the control input and from other poles.
Signal control uses ELN hysteresis; coil control picks up at 85% nominal and
drops out below 60%. Coils draw actual electrical power at their selected voltage.
Changing input mode first opens the contacts. NO/NC relays have a physical-time
break-before-make interval, so they never join both output circuits.

Mechanical devices click when switching. SSRs switch silently and have higher
on-resistance, producing real I²R conduction losses. All types track case heat
and overload accumulation, latch faults with every contact open, and expose
reset controls after cooling. The isolation button opens every contact while
leaving coil terminals live. Contact conduction uses the existing simulator's
matrix stamps, with exactly zero conductance/current when open. No converter
probes or additional nonlinear solver iterations are introduced.

Wiring: left face COM/line, right face NO/load, rear face NC when present.
Contact positions on each face are left 1, right 2, bottom 3 and top 4.
The front face accepts A1 on the left, A2 on the right, and an ELN signal at the
top. Coil mode requires a supply and return across both A1 and A2; one coil wire
alone does not complete the circuit. The GUI shows contact currents, common voltages, coil consumption, heat and
losses. New items occupy unused **125:37–42**; existing IDs remain unchanged.
Upgraded multi-pole recipes consume smaller units. All six original static
bodies and item previews use baked geometry. Only the armature and status lamp
remain dynamic, so frequent switching does not rebuild terrain sections.

## Verification

- **908 tests in 221 suites passed** with no failures, errors or skips.
  [Unit results](verification/release-3.0.51/units.json) include pole dimensions,
  actual creative tab contents, twin migration/recovery, 120/240 V sag, independent
  NO/NC operation, source/load/loss power balance, coil hysteresis and consumption,
  SSR heat, fault latching and reload behavior.
- Data generation succeeds. Both retired recipes/advancements are removed; the
  twin recipe uses a modern LV pole. All six switchgear recipes and item models
  are generated. Translation catalogs are regenerated after the UI changes.
- Native Minecraft 1.21.1 / NeoForge 21.1.249 with Sodium 0.8.13, Iris
  1.8.14-beta.1, BSL v10.1.8 and DH 3.3.3 passes. A real LV service span powers
  both twin heads from 240 V while L1 remains at zero; an old HV span is rejected.
  Six switchgear devices operate real independent loads using the three control
  modes. Static bodies and all **102 GUI/ground item previews** pass baked geometry
  checks. Existing fixtures, tray circuits and switch sounds also pass.
  [Native results](verification/release-3.0.51/native.json).
- Reviewed native screenshots show [matching insulators](verification/release-3.0.51/distribution-insulators.png),
  the [LV twin pole](verification/release-3.0.51/twin-secondary.png) and
  [new switchgear](verification/release-3.0.51/switchgear.png). This isolated test
  layout runs on Mesa llvmpipe software rendering for correctness, not FPS measurement.

This is a source-only prerelease; build with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.51.jar`.
