# Streetlight controls and visuals

Right-click a Utility Pole Street Light while holding a **Mains Step-down Unit**
to install one directly. This consumes one item and leaves any existing upgrade
untouched. The menu's upgrade slot still works. The creative insertion preference
also applies to hand installation. Saved poles retain their installed units and
their 120/240 V selection.

The pole menu now has **Dusk: Manual** / **Dusk: Auto** beside **Lamp: On/Off**.
Manual mode keeps the previous behavior. Automatic mode turns the lamp on as the
sky darkens and off after dawn; weather also affects the result. The detector uses
global sky darkness, so the lamp cannot switch itself off with its own light.
The thresholds are 4 to turn on and 2 to turn off, avoiding repeated switching
near dusk. Dimensions without skylight count as dark.

**Lamp: Off** overrides automatic lighting. Dusk control and the lamp button affect
only the built-in 60 W light: the base outlet remains available throughout the
day. The converter's **Off** button disables the entire low-voltage supply.
Automatic mode is saved across restarts; existing saves default to manual mode.
It does not keep chunks loaded.

## Height adjustment (3.0.10)

Use **Up** and **Down** in the pole menu to slide the lamp and its mounting
brackets along the pole in **0.25-block** steps. The displayed height ranges from
**1.30 m to 2.55 m** above the pole base; existing poles start at **2.30 m**.
The buttons disable at each end of the range. The upper limit keeps the brackets
more than half a block below the overhead conductors.

The transformer can, overhead wires and base terminals stay fixed. The emitted
light follows the head's position, and the old light position expires normally.
Height changes do not reset the electrical controller or change the base outlet.
Height is saved per pole, survives a world restart and copies with the
configuration tool. It can be adjusted while the lamp is off or unpowered.

## Fixture and wiring

The fixture now has two pole collars, a braced arm below the overhead conductors,
a separate transformer can when upgraded, and an LED head with an underside lens.
The lens is dark and normally shaded when unpowered; only a powered lamp uses
the bright lens state. The existing pole identity and default light-emitter
location are preserved.

The multimeter and All Meter work on both the pole base and its upper body.
Sneak-click diagnostic probes on the upper body resolve back to the owning pole.

Overhead cables now use closed cross-sections with shared joins and exact terminal
endpoints. This fixes the collapsed sides and missing end segments that made bare
250 kcmil copper spans appear disconnected from some viewing angles. Cable ratings,
connection distances and electrical behavior are unchanged.

## Verification

`./gradlew test` includes cable geometry, fixture loading and clearance at every
height, height limits, dusk hysteresis, manual overrides, base-output operation,
saved settings and malformed packet checks.

For the native check, run `./gradlew runClient` with
`-Deln.streetLightQolChecks=true` in `JAVA_TOOL_OPTIONS` under an X/GL display.
It creates a disposable world, installs upgrades by hand, uses meters on the base
and upper body, clicks the actual menu controls, tests daylight/night/manual-off
with a loaded base outlet, moves the lamp through both height limits using the
actual GUI, checks the light source and loaded outlet, captures cable side/end
views, and reopens the saved world. Results are written to
`run/client/streetlight-qol-checks.json` and
`run/client/screenshots/streetlight-qol-*.png`.
