# 3.0.37 primary wire ordering and client rendering

## New client profile

The supplied Spark text still points to drawing and buffer uploads:

| Path | Displayed sample share |
| --- | ---: |
| Sodium block-entity rendering | 28.32% |
| Lamp socket drawing | 8.44% |
| Decorative lamp models | 7.54% |
| Utility cable drawing | 2.44% |
| Turbine drawing | 2.15% |
| Iris buffered rendering, endBatch | 21.03% |
| GPU buffer uploads, glBufferData | 16.32% |

These are nested, overlapping views with Spark's displayed denominator. They
cannot be added, converted to FPS, or compared as absolute frame times against
the previous profile. Iris's upload branch is shared by other rendered content;
the profile does not establish that all its cost belongs to ELN.

## Mesh change

The existing untextured triangle compactor required bit-identical shared vertex
attributes. OBJ face-normal cross products frequently produce positive zero on
one triangle and negative zero on the adjacent triangle. Those values describe
the same position, UV and normal, but `FloatArray.contentEquals` distinguishes
their bits and kept these otherwise matching faces separate.

Shared-vertex matching now treats signed zeros as equal. NaN equality retains
the inherited-color marker. The existing coplanarity, winding, nondegenerate
area, UV, hard-normal and color-seam checks still apply. Textured primitive
metadata retains its original path. This does not remove model details, reduce
render distance, change culling, or freeze light/animation values.

## Three-phase primary wires

The local crossarm search treats a half turn as the same arm orientation. That
is sufficient for the arm's shape, but reverses the physical order of A and C.
Independent local results can therefore cross the outer primaries, especially
where spans turn through the search's half-circle boundary.

New three-phase distribution poles now orient their primary crossarms together.
A stable coordinate-based traversal chooses between each arm's two equivalent
angles, comparing the physical phase order across the shared span. The same
angles drive the equipment models, wire endpoints and aimed terminal selection.
It does not swap electrical loads, saved phase terminals or feeder circuits.
Secondary alignment remains independent.

The traversal runs on placement/link changes and initialization, not simulation
ticks or frames. It uses cached local search results and only resolves known
nodes; it does not load chunks. Changed angles also republish the neighbouring
owners of affected wire meshes. Existing spans are recalculated when their
nodes initialize after updating.

Regression coverage includes all four horizontal placement directions,
opposite-facing neighbours, straight and diagonal spans, both bend directions,
90-degree corners, stable traversal order, junction phase projection, individual
phase selection and unchanged electrical load identities.

## Native renderer measurements

Both runs use the production model-buffer benchmark from 3.0.36: twenty copies,
forty warm-up trials and eighty measured trials per case, with buffer allocation
and disposal excluded. Sodium 0.8.13 and Iris 1.8.14-beta.1 are installed, with
BSL 10.1.8 enabled and a 54-byte entity vertex stride.

| Model, 20 copies | Vertices before → after | Reduction | Median ms before → after |
| --- | ---: | ---: | ---: |
| Transformer street pole | 132,000 → 122,880 | 6.91% | 8.396 → 6.040 |
| Crystal chandelier | 348,080 → 333,040 | 4.32% | 22.576 → 14.526 |
| Overhead span | 5,280 → 5,280 | 0% | 0.395 → 0.317 |

The vertex reductions are deterministic. Timing also improved for the unchanged
wire case, demonstrating run-to-run variation; the table does not establish
that the mesh change caused the entire observed CPU improvement. These cloud
CPU buffer-construction measurements do not measure GPU upload stalls or predict
end-to-end FPS on the player's computer.

All **seven native checks passed**, including real model buffers, live colors,
camera/local transforms, nonuniform scale, shader attribute bytes, emissive LEDs,
and actual primary insulator meshes under all four placement directions. The
LED campaign separately passed **42 checks**. The new insulator check covers
three-phase line, transformer streetlight and underground-feeder pole models
at four crossarm angles, comparing every emitted insulator vertex.

[Before](verification/release-3.0.37/before.json) ·
[After](verification/release-3.0.37/after.json) ·
[LED checks](verification/release-3.0.37/leds.json)

The complete unit suite passed **813 tests**, with zero failures, errors or skips.
The result is recorded in
[the unit summary](verification/release-3.0.37/units.json).

This source-only release produces `build/libs/ElectricalAge-3.0.37.jar` when
built with JDK 21. See [Ubuntu 24.04 build instructions](building-on-ubuntu-24.04.md).
