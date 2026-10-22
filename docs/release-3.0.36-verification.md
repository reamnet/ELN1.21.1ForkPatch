# 3.0.36 client rendering costs

## Supplied client Spark text

The new pasted profile identifies ELN's expensive render paths more precisely
than the earlier vanilla dump. The main branches are:

| Render path | Reported sample share |
| --- | ---: |
| Distribution pole drawing | 19.33% |
| Pole equipment meshes | 10.99% |
| Overhead cable meshes | 8.29% |
| Lamp socket drawing | 15.78% |
| Decorative lamp models | 15.33% |
| Color submission under pole equipment | 6.32% |
| Color submission under overhead wires | 5.38% |
| Color submission under decorative models | 4.98% |

Rows are nested and overlap; do not add them. The attachment includes overlapping
expanded views with matching pole stacks, and ends with a bare `Render thread
27.04%` without a labelled call tree. Those are not enough to derive a reliable
shader-on/off comparison. The percentages retain Spark's displayed denominator;
without the full viewer settings they cannot be converted into milliseconds per
frame or a fraction of only the render thread.

This is client FPS work. The profile points at repeated vertex submission in
`FixedFunction.emit()`, particularly floating-point color writes. Normal transforms
and neighboring-light lookup account for much smaller shares in these branches.
The preceding transform optimizations therefore did not address the dominant
cost shown here. Poles run through Sodium's global block-entity path because
their wires extend beyond their own chunk; removing that registration would
reintroduce disappearing spans.

## Renderer changes

- Compute inherited packed ARGB when a color command changes, and cache explicit
  recorded vertex colors with each mesh. Avoid repeating four float-to-byte
  conversions for every model vertex on every frame.
- Keep the public chained `VertexConsumer` API and use its packed-color setter.
  The native buffer writer can store that color in one packed write. Wrapped
  consumers and Iris retain their standard hooks, without a raw buffer layout or
  renderer-mod dependency. A trial of the complete-vertex API was rejected after
  it increased model-emission time with Iris's shader-extended format.
- For untextured meshes, join consecutive flat triangle pairs when their shared
  positions, UVs, normals and colors match exactly. Reject seams, overlapping,
  degenerate and non-planar pairs. The same surface triangles and winding remain;
  textured models retain their original primitive metadata. Both topologies are
  cached independently when a mesh is reused with different material settings.
- Build each overhead cable face as four vertices rather than two separately
  padded triangles. A standard sixteen-segment closed span drops from **528 to
  264 submitted vertices**, with its endpoints, sag, radius, texture coordinates
  and normals retained.

No model distance reduction, pole/wire culling change, simulation change or
world-save conversion is part of this update.

## Native measurements and compatibility

A native client benchmark emits twenty copies of each real production model per
trial into real Minecraft buffers. It uses forty warm-up trials and eighty
measured trials per case, excluding buffer allocation/build/disposal from the
measured draw interval. Correctness checks run after timing so their proxy consumers do not affect the
benchmark loop's JIT profile. The test runs with Sodium **0.8.13**, Iris
**1.8.14-beta.1**, and **BSL 10.1.8 enabled**. The shader-extended entity format
has a 54-byte stride. Both runs use the same scene and benchmark code; the reference run uses the
3.0.35 vertex-emission and span paths.

| Model (20 copies per trial) | Vertices before → after | Median ms before → after | p95 ms before → after |
| --- | ---: | ---: | ---: |
| transformer-street-pole | 163,200 → 132,000 | 6.978 → 5.792 | 15.394 → 8.485 |
| crystal-chandelier | 416,000 → 348,080 | 17.889 → 14.000 | 32.365 → 38.095 |
| overhead-span | 10,560 → 5,280 | 0.629 → 0.291 | 0.916 → 0.765 |

Median model-emission time fell approximately **17% for the pole**, **22% for the
chandelier** and **54% for the span** in this controlled comparison. The chandelier's
p95 sample rose despite its lower median, so these results do not establish an
improvement in frame-time spikes.

[Before report](verification/release-3.0.36/before.json) ·
[After report](verification/release-3.0.36/after.json)

These are model buffer construction timings on the cloud runner, not end-to-end
FPS or GPU timings on the user's computer. They do not predict a particular FPS
gain in an existing world. The reduction in submitted vertices is deterministic.

Compatibility checks compare every populated attribute byte for controlled
colored quads in entity and unlit buffers against the original consumer calls, including
Iris's extra attributes; padding is deliberately excluded. Separate live checks
verify changing camera/local transforms, nonuniform scale, inherited tint and
actual generator/motor LED meshes through black, green, yellow and red states.

All **six native checks passed**, including the three real-buffer model cases,
transform/tint verification, byte-for-byte entity/unlit attributes and the LED
campaign. The LED campaign separately passed **42 mesh checks**, each replaying
black, green, yellow, red and black against actual generator/motor OBJ parts.
[LED report](verification/release-3.0.36/leds.json)

The opt-in native command requires a display/OpenGL context and the above
rendering mods installed in `run/client/mods/`:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.renderCostChecks=true" ./gradlew --no-daemon --max-workers=3 runClient
```

It writes `run/client/render-cost-checks.json` and exits. Read the report for a
failure as well as checking Gradle's status. Set `-Deln.renderCostOutput=NAME.json`
to retain separate before/after measurements. The native test is disabled during
normal gameplay and no renderer-mod dependency is added to ELN.

The complete unit suite passed **810 tests**, with zero failures, errors or
skips. Six new regressions cover triangle/attribute preservation, UV/normal/color
seams, degenerate/overlapping/non-planar faces, mixed primitive ordering, alpha,
and independent textured/solid replay caches with live inherited tint.
[Unit summary](verification/release-3.0.36/units.json)

The source-only release builds with JDK 21 and
`./gradlew --no-daemon --max-workers=2 build`, producing
`build/libs/ElectricalAge-3.0.36.jar`. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
