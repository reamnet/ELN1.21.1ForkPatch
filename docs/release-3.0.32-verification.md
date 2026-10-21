# 3.0.32 convergence audit

This audit follows the 3.0.31 Large Generator leakage-load fix. It checks production converter and supply controllers, their shared solver integration, idle loads, load changes, source limits and local fault handling. Existing item IDs, saved settings and electrical ratings are retained.

## Confirmed issues and changes

- **Regulated converters rejected valid standby loads on stiff high-voltage feeds.** A voltage command is stored as a double; at high voltage and low cable resistance, its smallest representable change can correspond to more power than a standby load draws. The controller calculated the load but its power-balance check required an unrepresentable voltage drop. This reproduced in **126 of 600** voltage/impedance/load combinations. Regulated controllers now use the same port-derived voltage-resolution bound already used by fixed-ratio transformers. The shared helper bounds only numerical power resolution; it does not discard the load or introduce an arbitrary wattage allowance.
- **Other converter families had the same numerical failure.** Fixed and isolated one-way converters, legacy saved control modes, and the hybrid solar inverter now apply the same bound. Candidate commands must still match the controller's voltage law. The regulated one-way adapter also preserves its inner controller's choice of trial sources for unloaded operation.
- **One-way converter rejection could shut down a healthy upstream generator.** Its outer adapter did not expose the local-load isolation contract. Rejected one-way stages now open locally before the solver considers tripping connected supplies. The regression injects a rejected candidate at the real adapter boundary and verifies that the generator continues powering another branch.
- **Regulated candidates could exceed their wattage rating while remaining below their current limits and balancing power.** Candidate validation now independently enforces the configured output-wattage ceiling. A 30 kW candidate on a 20 kW stage is rejected even with legal currents and balanced input/output power.
- **Parallel limited supplies could commit an incomplete operating point.** Wind/water sources and standby alternators previously checked only that power/current were below their limits. Coupled updates could therefore leave them below nominal voltage *and* below both limits, without completing the solve. Acceptance now requires the voltage target, power ceiling or current ceiling to be reached as well as checking the upper bounds. The solver receives the active limiter region for its numerical correction. Before the fix, a 21 kW renewable pair delivered approximately 17.5 kW in the overloaded test and two 10 kW standby units delivered approximately 19.81 kW; the tests require the complete available operating point.
- **A speculative backfeed could latch a renewable open for the rest of an electrical step.** A renewable now re-probes after other trial commands change. A final failed solve still blocks it until the next physical step, and a genuinely higher-voltage feed remains blocked against reverse power.

The numerical resolution allowance does not bypass current, wattage, voltage-command or reverse-power checks. A regression deliberately perturbs an input command by 128 voltage ULPs and verifies rejection of the resulting real power imbalance. No global convergence iteration limit or physical timer was increased.

## Regression coverage

`ConvergenceAuditTest` exercises 600 production regulated circuits: 120, 240, 3200, 12470, 33000 and 51200 V feeds; four source impedances; five output targets; four finite idle-load resistances and the open-output model. It also covers idle/load/idle transitions, rejection beyond voltage resolution and the independent wattage ceiling.

`DeviceConvergenceAuditTest` adds 48 fixed/isolated/variable one-way circuits across old/new control versions, 24 hybrid-grid standby-load circuits, production adapter fault isolation, and two running, fueled standby generators reaching their shared power limit before engine timers advance.

`RenewableConvergenceAuditTest` uses unequal 1/20 kW wind/water supply budgets, one/two/four sources, both controller insertion orders, normal loads, overloads, idle operation and recovery. A separate trial test verifies backfeed recovery without advancing or committing physical state.

The existing suite also exercises generator startup and switches/relays/diodes, shaft energy and regeneration, parallel converters, long feeders and pole transformers, UPS charging/backup/bypass, standby/ATS transfers, solar tracking and priorities, invalid controls, shorts and reverse-power protection.

```bash
./gradlew --no-daemon --max-workers=3 test
```

The full suite passed **788 tests**, with zero failures, errors or skips. All new audit cases and the existing protection/energy tests passed. Tests ran through NeoForge’s JUnit runtime with the production MNA solver and registered device components. No native Minecraft world campaign or user-world replay was performed for this release.
