# 3.0.50: outdoor lighting, underground service and simulation probing

## Lighting and generation

Fifteen original bulb fixtures are appended in a separate unused item group:
five pier lights (nautical bollard, marine bulkhead, harbor gooseneck, lantern
post and low-profile dock), five walkway lights (louvered bollard, shielded path,
blade, mushroom and arched) and five shallow stone-wall lights (brick, round,
slotted, square and wall wash). They use the existing replaceable-bulb, wireless
lamp-supply, fixed-cable and 120/240 V cord systems. Existing lamp IDs are retained.

The original standby generator remains unchanged in identity. Four larger tiers
retain its automatic wireless ATS linkage, generic tagged gasoline support,
programmable voltage, fuel accounting, noise, smoke and overload/backfeed safety:

| Tier | Width × height × depth | Rated output | Gasoline tank |
| --- | --- | --- | --- |
| Existing small | 1 × 1 × 1 | 10 kW | 20 buckets |
| Medium | 2 × 1 × 1 | 20 kW | 40 buckets |
| Large | 2 × 2 × 1 | 40 kW | 80 buckets |
| Extra large | 3 × 2 × 2 | 80 kW | 160 buckets |
| Extreme | 5 × 3 × 3 | 240 kW | 480 buckets |

Output current limits scale with tier; series resistance scales inversely, so
larger generators do not gain an excessive internal voltage drop. Recipes
consume the preceding tier. The extreme tier consumes three extra-large units.
Actual output remains subject to voltage/current limits and physical losses.

## Underground connections and trays

Distribution poles expose bottom LV service terminals: left/right LRDU select
L1/L2, and the remaining bottom terminals select the 240 V equivalent. Legacy
pole adapters also expose underground secondary access while retaining the old
HV cable behavior until their service terminals are selected.

The new fused single-phase 120/240 V service pedestal accepts underground service
and opens all secondary outputs with one replaceable fuse. A passive wired split
makes 120 V follow the incoming 240 V proportionally. Four underground feeder
cabinets cover single/three-phase 12.5 kV, each with and without a street light.
HV line and fused load connections are distinct; LV service passes through
separately. Street lights require upstream secondary power. These cabinets use
placed cables rather than overhead-span linking. Physical Pole Fuse Links,
independent phase protection, operation sounds and open fuse visuals are reused.

The ceiling cable tray has sixteen color slots. Each installed color consumes a
one-metre power-cable segment and retains its resistance. Adjacent sections join
only matching installed colors. Ordinary cables can use a manually selected
breakout color or automatically follow their paint color. Changing one port does
not combine circuits. Empty colors do not register simulation states. Trays are
passable and need ceiling support; upstream protection is still required.

Three additional service masts provide compact sideways entry, through-roof entry
and a gooseneck. Through-roof pipe geometry can intersect an existing overhang
without replacing its blocks. Three-phase wooden poles now use a top insulator
for B, with the wire and equipment jumpers terminating there. The 230 kV steel
insulator strings have 2.5 times the 69 kV dimensions. Steel lattice heads end
below their crossarms so strings do not disappear inside the structure.

## Rendering and sound

New static bodies use terrain section meshes, including every new fixture,
generator, underground cabinet, mast and tray. Item previews use ordinary baked
quads. Voltage, fuel and GUI telemetry do not rebuild stationary geometry.
Meter glass uses a curved shell and narrow rim with an optically clear front,
so shader opacity cannot cover the readouts. The glass stays in the live
translucent path, with the opaque cabinet baked;
needles, disks and digits retain their dynamic rendering. This avoids treating
the glass as an opaque shader terrain surface.

All fireplace TV stands are two blocks wide, with matching clearance and
collision. Wall switches have short audible clicks without being muffled by
their own support block. Legacy wired switches, relays and meter contacts also
use the registered `eln:click` event instead of the obsolete `random.click` ID. Window AC and mini-split loop volumes are reduced to
30% and 20% of their former values, respectively.

Washer/dryer case roofs previously shared y=0.46 with the gray top-cap faces.
The case now ends at the underside of that cap, removing the coplanar white/gray
surfaces that caused flickering. The complete stationary machines stay baked.

## Server work

The supplied trace includes roughly 70% idle/wait time. Its largest ELN active
path is convergence evaluation, especially repeated floating-network probe
validation. Systems with fewer than 128 states, or fewer than 256 changing-RHS
probe validations in the current matrix generation, retain the existing path.
This avoids paying sparse setup costs on small or newly rebuilt networks.
After that threshold, the new path computes only solution variables referenced by discarded
KCL equations (at most eight), reusing precise inverse rows. A conservative,
upward-rounded triangular-solve bound first ensures that every variable and
intermediate remains finite. Dense constraints, unsafe magnitudes and unavailable
bounds fall back to the existing full/affine validation. New matrix generations
invalidate all related caches. Precision, residual tolerances, physical steps,
source limits and nonlinear candidate acceptance remain in force.

A same-JVM warmed probe benchmark compares the existing and new paths with
four ports and 16,000 probes per timed round (seven rounds; median reported).
The 80-node case leaves the optimization inactive. Cold/rebuilt networks also
retain the original path until the repeated-probe threshold is reached. Results
vary with JVM warmup and scheduling; this is not a whole-server, chunk-loading,
FPS or TPS claim. The final full-suite run measured:

| Nodes | Existing path, ms | New path, ms | Sparse path active? |
| --- | --- | --- | --- |
| 80 | 40.26 | 34.93 | No |
| 160 | 66.12 | 29.25 | Yes |
| 320 | 120.12 | 52.51 | Yes |

The two eligible warmed cases take about 56% less time in this run. Ineligible
cold cases show ordinary measurement variation; no cold-start improvement is
claimed. Raw timings and methodology are in
[benchmark.json](verification/release-3.0.50/benchmark.json).
Differential tests compare full/sparse validation across command changes,
resistor changes, open sources, unbalanced floating currents, dependent sources
and extreme/nonfinite commands. The older affine optimization tests explicitly
exercise their original path; dedicated new tests exercise the sparse path.

## Verification

- Full correctness suite: **900 tests, 219 suites; zero failures, errors or skips**.
  [Results](verification/release-3.0.50/units.json).
- Final asset checks also reject duplicate OBJ object names, which can discard
  earlier geometry such as the new bollards' stems at load time.
- Native client: actual NeoForge 21.1.249 / Minecraft 1.21.1, Sodium 0.8.13,
  Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3. All fifteen new
  fixtures illuminate from real 240 V electrical boundary supplies. Three
  adjacent trays join three independent installed colors. Static terrain
  geometry and 84 GUI/ground item previews pass the baked geometry audit.
  Wireless and legacy wired switches both emit registered audible click events.
  [Client results](verification/release-3.0.50/native.json).
- Native screenshots use Mesa llvmpipe software rendering for correctness checks;
  they are not an FPS measurement. The scene is an isolated floating test layout.
  [Lighting](verification/release-3.0.50/lamps.png),
  [equipment](verification/release-3.0.50/equipment.png),
  [meter faces](verification/release-3.0.50/meters.png),
  [trays](verification/release-3.0.50/trays.png),
  [insulators](verification/release-3.0.50/insulators.png),
  [washer/dryer tops](verification/release-3.0.50/laundry-tops.png).

This is a source-only release; build with a full Java 21 JDK using
`./gradlew --no-daemon --max-workers=2 build`. Output:
`build/libs/ElectricalAge-3.0.50.jar`.
