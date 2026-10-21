# Transformer probe performance (3.0.29)

The supplied server profile spends **82.49% of server-thread samples in `Simulator.tick()`**, including **65.53% in `SafeTransformerProcess.rootSystemPreStepProcess()`**. Inside that path, `sourceThevenin()` accounts for 65.30%, open-port probing for 46.90%, and matrix generation for 18.13%. These percentages overlap: they are inclusive call-tree measurements, not separate amounts to add together. Thermal stepping accounts for another 8.97% outside the root electrical step. The profile does not establish an exact TPS or tick duration.

Three-phase poles retain unfed phases in the structural subsystem even when their contacts are open. Those floating islands require reference gauges. Before this update, each transformer port probe repeated a whole-system high-precision solve to validate the physical currents, plus another solve to check whether a unit current could flow through an open port. Many probes repeated the same work on the same matrix.

## What changed

- Cache the physical-current validation only when the **complete physical RHS is identical**, including the equations discarded by a reference gauge. Changed source voltages, current injection or capacitor history therefore require fresh validation.
- Separately cache a port's unit-current support for its current matrix. Changing contacts, source enable masks, component values or topology creates a new gauge and discards both caches.
- Track nonzero matrix entries through row/column pivoting and elimination, rather than repeatedly scanning all dense zeros to select pivots and build sparse solve lists. Numerical pivot criteria and nonzero arithmetic are unchanged.
- Skip exact-zero arithmetic and division by one in the sparse factorization's solve paths. All nonzero electrical calculations retain double-double precision.

Transformer ratios, winding resistance, physical simulation time, thermal integration, converter acceptance tolerances and fault isolation are unchanged. Floating unbalanced currents remain rejected; no artificial ground leakage or fixed output voltage is introduced.

## Measurements

Paired local runs of `PoleGridPerformanceTest` used the registered three-phase transformer poles, one energised phase, two unfed phases, independent loaded/unloaded secondaries and the same feeder. Each run warmed up for twelve steps and sampled forty electrical steps. The largest shared matrix had 724 states.

| Poles | Previous median (ms) | Updated median (ms) |
| --- | ---: | ---: |
| 10 | 0.348 | 0.201 |
| 40 | 2.134 | 0.394 |
| 120 | 13.717 | 0.575 |

For 120 poles, the measured 95th percentile changed from **19.375 ms to 0.888 ms per electrical step**, about twenty-two times faster for this steady-state fixture. After a 5% supply sag, the next ten steps averaged 22.214 ms before and 4.571 ms after. These are local benchmark results, not a predicted speedup for an entire multiplayer server. Timings vary with the JVM, hardware, world topology and changing component values.

The 120-pole cold-start measurement fell from **7.231 seconds to 3.935 seconds** after sparse pivot/list construction was added. This reduces the startup/reconnection cost but does not eliminate it: initial source-mask changes still require matrix construction/factorization. The fixture also changes every feeder resistance to exercise cache invalidation under wire heating. Those deliberately fresh matrix steps averaged **51.349 ms** in the updated targeted run and **69.458 ms** in the full-suite run; they are not represented by the sub-millisecond steady-state result. There is no measured original heating-step baseline, so no before/after speedup is claimed for that case. Large networks with continuously changing impedances can still be expensive and should be profiled again under the same real load.

Release validation: **745 unit tests passed**, with zero failures, errors or skips. **24 native Minecraft checks passed** in `utility-distribution-1792543288165`, including proportional transformer voltage changes, actual house-service loads, overload isolation, meter/manual disconnects, fuse/recloser recovery and a full world save/restart. Independent matrix residual tests cover sparse pivoting and ordering reuse; additional regressions check discarded-current changes and cache invalidation after grounding/topology changes.

Run the targeted fixture with a full JDK 21:

```bash
./gradlew --no-daemon --max-workers=2 test --tests 'mods.eln.transparentnode.utilitylight.PoleGridPerformanceTest'
```

The measured lines are recorded as `POLE_GRID_BENCH` in `build/test-results/test/TEST-mods.eln.transparentnode.utilitylight.PoleGridPerformanceTest.xml`. To assess a real server, use matching client/server jars, capture another profile under the same load, and compare actual tick times as well as the call percentages. More time in another method after this fix can simply mean the transformer work became cheaper.
