# 3.0.31 verification

Fixes the Large Generator dropping to zero volts when a switch, relay or diode is attached to its output at 200 rad/s / 3.2 kV.

The generator's voltage calculation treated every external resistance above 100 MΩ as a completely open circuit. Switches and relays have 1 GΩ pull-down resistors, and open contacts and blocking diodes also retain finite leakage resistance. At 3.2 kV these paths draw microamps. The generator commanded exactly its unloaded voltage, but its independent I–V acceptance check correctly detected the missing droop current and rejected the candidate. Repeated retries then left the generator output off.

The voltage calculation now solves every finite resistance using the existing droop, current-limit and available-shaft-energy equations. Only an infinite-resistance port uses the open-circuit shortcut. Acceptance tolerances, protection ratings, the physical retry timer, numerical iteration limits and save identifiers are unchanged.

`GeneratorSwitchRuntimeTest` constructs the registered Large Generator at a maintained 200 rad/s and attaches the real registered switch, relay and diode electrical components. It checks attachment to an already running generator, unloaded closed contacts, a 100 W switched load, reopening, forward/reverse diode operation, and resistances around the old cutoff through 1 TΩ. A further case starts and restarts the shaft with an open switch already attached. These are electrical regressions; device damage/watchdog processes are not stepped, so exercising a lower-rated contact does not change or certify its voltage rating.

All four initial regression tests failed against 3.0.30, including immediate generator trips on the first step after attaching a switch or relay and after adding a diode load. The same four passed after the fix. `ShaftElectricalMathTest` additionally checks finite leakage with normal, zero and tiny mechanical-energy budgets. Existing generator/converter tests cover loaded feeders, parallel generators, short circuits, reverse current, shaft-energy limits and ramp timing.

```bash
./gradlew --no-daemon --max-workers=3 test
```

The full suite passed **778 tests**, with zero failures, errors or skips. Verification used NeoForge’s JUnit runtime and the production circuit components; no native Minecraft world campaign was run for this arithmetic fix.
