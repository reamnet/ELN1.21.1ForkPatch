# 3.0.39 generator startup and exposed cable rendering

A Large Generator at normal shaft speed could settle at a low electrical voltage
when a target-voltage step-up converter was already connected during startup.
Replacing a placed cable restarted the solve and restored normal output.

The new regression reproduces that failure using the actual Large Generator
controller at 200 rad/s, a placed-cable resistance and a converter targeting
12.5 kV. Before the fix, even the 5 kW case settled at approximately 24 V on the
primary and 6.18 kV on the secondary instead of reaching the normal operating
point. The exact stalled voltage depends on the load and converter limits; this
is the same failure mode as the reported approximately 400 V after reboot.

While the generator builds its field, the solver now advances the initial
voltage guesses together for the generator and the converter input sources on
that electrical subsystem. Networks without an active target-voltage input
clamp skip this startup correction. Moving only the generator's guess would let the
converter's old input clamp immediately pull it back. Each resulting candidate
still runs the ordinary controller laws and current, power and convergence
checks before one physical simulation step is committed. These startup guesses
are requested only below the source's current-limit knee while its field is
increasing, including gradual shaft acceleration, and stop once it settles.
They preserve the generator's nominal voltage, current limit and available
shaft-energy budget.

The regression covers 5, 20 and 60 kW loads, an already-spinning, delayed or
gradually accelerating shaft, and both controller registration orders. It also
checks current and power limits throughout startup and the absence of startup
requests after settling.

## Rendering

The supplied client profile shows utility cable rendering among the remaining
ELN costs. Exposed round and flat conductors previously rebuilt render
descriptors and terminal geometry and submitted immediate drawing commands on
every frame. Their renderer now records and replays those meshes, invalidates
them when a publication changes connections, dye or palette, and releases them
with the renderer. Current render-context lighting, heat effects and texture
selection remain live.

The benchmark uses real production models and Minecraft/Iris vertex buffers,
with 20 copies per trial, 40 warmup trials and 80 measured trials per model.
It excludes buffer allocation and final buffer building. The before and final
captures use Sodium 0.8.13, Iris 1.8.14-beta.1 and BSL 10.1.8 on software Mesa. These are CPU
mesh-emission measurements, not a prediction of gameplay FPS on the user's GPU.
An experimental shared vertex-transform change had mixed timings and was removed.

| Model (20 copies) | Before median (ms) | After median (ms) |
| --- | ---: | ---: |
| transformer-street-pole | 5.2245 | 5.9651 |
| crystal-chandelier | 13.9106 | 15.0402 |
| overhead-span | 0.2533 | 0.3137 |
| utility-straight | 0.0570 | 0.0902 |
| utility-terminal | 0.2741 | 0.1283 |
| duplex-outlet-plugs | 4.6575 | 4.4504 |
| industrial-pump | 2.3633 | 2.8950 |

Exposed cable terminals improved from **0.2741 to 0.1283 ms** per 20 copies
(about **53% less CPU time** in this capture). The final render path passed all
**12 checks**, including cached cable replay after packet updates; every model
kept the same vertex count. Timings for unchanged models varied in both
directions on the shared host, so these captures support the targeted terminal
optimization and do not establish a general FPS gain. Straight jacketed cable
geometry was already cached and is unaffected by the exposed-conductor cache.

Raw results: [before](verification/release-3.0.39/render-before.json) and
[after](verification/release-3.0.39/render-after.json).

## Reproduction

With a working display and the renderer mods installed in the development client:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.renderCostChecks=true -Deln.renderCostOutput=render-cost39.json" ./gradlew --no-daemon --max-workers=3 runClient
```

The report includes timings, vertex counts, shader status and correctness checks.
Those checks compare every populated entity/unlit vertex attribute, including
Iris extensions, against the original emission path. Exposed cable replay is
also compared with freshly generated meshes after camera transforms and actual
connection, dye and palette packets.

A native saved-world campaign additionally uses real Large Generators, driven
shafts, placed cables and actual converters. It includes three cable types,
both cable/generator placement orders, twelve resistive loads, six step-down
converters and four 12.5 kV target-voltage step-up cases at 5 or 20 kW.

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.generatorChecks=place -Deln.generatorWorld=generator-39-stepup-scoped" ./gradlew --no-daemon --max-workers=3 runClient
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.generatorChecks=verify -Deln.generatorWorld=generator-39-stepup-scoped" ./gradlew --no-daemon --max-workers=3 runClient
```

Use a new world name for `place`. Run `verify` in a separate JVM. It reopens the
saved world without replacing cables and repeatedly samples converter output
to detect retry blinking, then checks recovery from incomplete node registration.
The reports are `run/client/generator-checks-place.json` and
`run/client/generator-checks-verify.json`. All **22 setups passed** placement,
a separate-JVM saved-world reload without cable replacement, repeated output
samples and node-registration recovery. The 5 kW step-up fixtures restored about
3.12 kV on the primary at approximately 195 rad/s and 12.5 kV on the secondary.
The heavier 20 kW fixtures ran at a lower shaft speed and correspondingly lower
primary voltage while maintaining their requested 12.5 kV output.

The full reports are [placement](verification/release-3.0.39/generator-place.json)
and [restart/recovery](verification/release-3.0.39/generator-verify.json).

All **818 unit tests passed**, with zero failures, errors or skips across
**202 suites**. The new startup regression covers 18 combinations of load,
shaft startup and controller order. See [unit results](verification/release-3.0.39/units.json).

This source-only release builds `build/libs/ElectricalAge-3.0.39.jar` with JDK 21.
See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
