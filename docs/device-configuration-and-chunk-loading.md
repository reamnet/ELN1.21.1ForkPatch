# Device configuration and unattended installations

Device text fields accept Ctrl+A, Ctrl+C, Ctrl+X and Ctrl+V (Command on macOS).
Enter, clicking another control, or closing the screen commits a field. Transformer
controls use the same text fields; hover help appears beside the pointer instead of
adding the panel offset a second time.

Lamp sockets remember the last wireless power channel configured by **the player
placing them**. The name survives world/server restarts and is also remembered when
using the configuration-copy tool. Existing lamps retain their saved channels. This
replaces the old, disabled-by-default shared `rememberLastLampSignalName` setting;
it no longer depends on a server-wide last-used name.

The lamp supply's **Power range** button cycles through **32, 64, 128, 256 and 512
blocks**. It controls wireless power coverage, including the boundary, within the
same dimension. One installed power cable is still required to connect the supply;
extra cables no longer determine coverage. Existing supplies migrate their previous
32-plus-cable-count coverage until a range is selected. The configuration-copy tool
also copies a selected range. Wireless control reception is separate from power
coverage; setting a control-signal name does not create a power-transmission channel.

## Keeping a plant running while away

Minecraft unloads chunks outside the active area. ELN retains its electrical graph,
but block ticks, fuel inventories, fluids and other mods' machinery need loaded,
ticking chunks. Server administrators and singleplayer owners can keep an explicit
area active with:

```
/eln chunkload on
/eln chunkload on 1
/eln chunkload list
/eln chunkload off 1
/eln chunkload clear
```

`on` keeps the current chunk ticking. A radius of `1` keeps a 3-by-3 area; `2`
keeps a 5-by-5 area. The optional radius is in **chunks**, from 0 through 4.
Include the generators, cables, machines, fuel/fluid handling and any required
external-mod blocks. F3+G displays chunk boundaries.

Tickets survive a server/world restart. `list` shows this dimension's ELN tickets;
`off` releases the specified area at the command's current position; `clear`
releases all ELN installation tickets in this dimension. These commands preserve
vanilla `/forceload` and other mods' tickets. There is a limit of 256 selected chunks
per dimension. Larger loaded areas increase server work. A paused singleplayer
world or stopped server does not advance time.

## Generator startup

Generators refresh their electrical connections once after all saved nodes and
multiblock shaft ports have initialized. This rebuilds the output connection during
startup without replacing a cable or resetting the shaft's stored energy. It also
applies to the Large Generator. Fuel-dependent plants still need their operating
area loaded as described above.

The generator regulator also participates in the converter solver's final
validation. This prevents a stiff converter input from holding it near its startup
voltage while the shaft is already at full speed. See
[coupled-grid recovery](utility-street-light-and-recovery.md#existing-device-fixes).

## Regression checks

Run `./gradlew build` for the unit suite. Under a working X/GL display, add
`-Deln.configurationChecks=true` to `JAVA_TOOL_OPTIONS` and run `./gradlew runClient`.
The opt-in client check creates a uniquely named disposable flat world, exercises
actual GUI clicks/shortcuts and server packet round trips for transformers and
lighting, tests remote furnace ticking, and reopens the world to check persistence.
It writes `run/client/configuration-checks.json` and GUI screenshots, and exits
nonzero on a failed assertion. It never opens or replaces a pre-existing save.

For the generator check, choose an unused disposable world name and run two
separate Minecraft JVMs under an X/GL display:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.generatorWorld=generator-check-1 -Deln.generatorChecks=place" ./gradlew runClient
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.generatorWorld=generator-check-1 -Deln.generatorChecks=verify" ./gradlew runClient
```

This covers both output ports, cables placed before/after the machine, legacy
very-high-voltage, insulated copper utility and **Aluminum 1000 kcmil Cable Bare**
cables, and installations with/without forced chunks away from the player.
Six more setups feed 120/240 V lights through mains step-down, variable and one-way
buck converters (18 setups total). Repeated samples check for intermittent power.
Verification also deliberately disconnects the
generator's simulation registration before calling its boot hook, checking recovery,
unchanged shaft energy, no duplicate connections, and subsequent delivered power.
Reports are `run/client/generator-checks-place.json` and `generator-checks-verify.json`.
These controlled checks do not reproduce every existing world's wiring or load.
