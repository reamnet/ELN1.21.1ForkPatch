# Lighting regression checks

Halogen replacement bulbs (12 V, 120 V and 240 V) fit lamp sockets and floodlights.
Match the bulb voltage to its supply: compatibility does not prevent overvoltage or burnout.
Direct insertion preserves a used bulb's remaining life. Wired sockets still require a cable
in their cable slot and wireless/lamp-supply mode disabled.

Socket wires terminate at the fixture's housing with a metal collar and insulated cable gland.
The connector follows the fixture's rotation and installed cable size. Fluorescent fixtures
only accept connections on the two sides where they can actually render a connector.

Floodlights support floor, ceiling and four wall mountings. Horizontal aim, vertical aim and
beam width use the same mounting frame, including straight-up aim without invalid rays.
Motorized floodlights use their three signal inputs; the manual sliders are intentionally disabled.
Invisible beam lights also work in otherwise empty chunk sections and cave air.

## Automated coverage

- `gradlew test`: registered bulb/slot compatibility, lifetime preservation, all 24 mounting
  frames and beam-cone invariants, negative-coordinate flooring, non-air light states and connector footprint rotation.
- `gradlew runServer -PsmokeTest=all`: named `lighting/<face>` contracts for every supported
  lamp/floodlight mounting, actual right-click insertion, menu acceptance, control packets,
  floodlight ports, electrical-load uniqueness and actual powered light placement on all six mountings.
- `gradlew runClient -PsmokeClient=smoke`: a real stitched-atlas particle check, hit/break
  callbacks with no block entity, and a lamp gallery with connected cables. The gallery
  requires newly placed cable renderers to synchronize and render before capturing them.

Cached model transforms apply to lighting normals as well as positions. Unit checks cover
rotation, non-uniform scaling and degenerate faces; world lighting is not forced to full brightness.

Use the isolated smoke setup described in [headless testing](../tools/port/headless.md).
Do not point the smoke runner at a gameplay world. GitHub CI already invokes these suites.
Reports are under `build/smoke-artifacts/contracts`; gallery images are named
`smoke-lighting-*.png` in the client screenshots artifact. Screenshots are review evidence,
not an assertion that a design looks good.


## Commercial fixtures, motion sensor and generator cart

Run the standard unit suite with `./gradlew test`. The lighting tests cover every
registered bulb in every decorative fixture, per-bulb power draw, direct inventory
insertion, saved settings, clearance and mesh parts. Generator checks run real
MNA circuits at every bulb voltage for longer than the engine protection grace
period, and check measured power, fuel consumption and empty-tank shutdown.
Motion checks cover hold timing, retriggering, daylight cancellation and the
opened electrical load when off.

Two opt-in native client checks use fresh isolated development worlds:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.decorativeLightChecks=true" ./gradlew runClient
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.workLightChecks=true" ./gradlew runClient
```

The gallery exercises all 18 bulb variants in the 30 electric fixture styles,
then real wired connections, actual GUI controls, lit/unlit screenshots and a
world restart. Its test world force-loads its gallery chunks so all test circuits
remain active while the camera moves; production lights do not force-load chunks.
The work-light check seeds a measured fuel quantity (no companion gasoline mod is
needed), starts/stops the engine through the real GUI, checks fuel use and both
bulbs, changes to real cable and wireless-supply power, tests wall occlusion and
the 30-second motion timeout, and restarts the saved world. It does not validate
a companion mod's gasoline container or its refining chain.

Reports are `run/client/decorative-light-checks.json` and
`run/client/work-light-checks.json`; screenshots are in `run/client/screenshots`.
Both checks stop their own client on completion. Run Gradle commands sequentially
in the same checkout.

The expanded work-light check also runs all five kitchen appliances through a
power outage, actual GUI toggles and a saved-world restart. It tests every
ceiling style on both half-slab orientations, all table styles on slabs/stairs/
fences/chests, and a direct client click on the visible head of a recessed lamp
mounted into a top slab. `-Deln.upsChecks=true` additionally checks actual client
sound-play events for grid loss and a 15 kW overload. On a headless Linux machine
without an audio device, `ALSOFT_DRIVERS=null` provides OpenAL's no-output backend
for those sound-event checks.

### 3.0.15 verification

The full unit suite passed 621 tests, with no failures or skipped tests. Native
checks passed for the 18-bulb compatibility cycle across all 30 electric styles;
the wired GUI, lit/unlit rendering and gallery reload stages passed in a
separate focused run. The work-light/kitchen, UPS and fresh-process reload
checks also passed, including actual appliance outputs, partial-block clicking,
power outages, fuel consumption and client backup/overload sound events.

## Household update native checks

With a full JDK 21, a graphical session and the normal development runtime, run:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.householdQolChecks=true" ./gradlew runClient
```

This opt-in campaign creates its own flat world, places real lamps, cables,
appliances and wall-mounted devices, then checks powered behavior, independent
head aiming through the actual GUI, switch sound events, raw-voltage UPS bypass
and return to regulation, AC on/off, enclosed-room cooling and a world restart.
It saves `run/client/household-qol-checks.json` and screenshots. Success requires
11 recorded checks and no `failure` entry. For headless Linux graphics, use the
existing X display, software GL and `ALSOFT_DRIVERS=null` for sound-event checking.

JUnit coverage includes legal renderable triangle meshes, all bulb technologies,
independent aim/save/network state, fast switch animation without overshoot,
fan motor power, both wall UPS ports on every wall, direct bypass without cells,
return to battery operation, thermostat hysteresis, heat conservation, unique
saved IDs and the rice cooker's water/container handling.
