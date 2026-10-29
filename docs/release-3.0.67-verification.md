# Electrical Age 3.0.67 verification

The update adds five outdoor fixtures and a compact UPS, replaces the floor/wall UPS and ATS enclosure meshes, and reserves one additional optional media ID. Existing IDs are unchanged. New compact UPS: `(126 << 6) + 12`; wall TV: `(126 << 6) + 11`.

The compact UPS uses the existing conservative two-stage UPS solver, battery energy accounting and alarms. New solver tests cover both supply voltages, charging/current limits, loss of input, aggregate overload, independent battery removal, four-socket occupancy, persistence and unsafe voltage/bypass rejection. Existing UPS/standby and electrical regression tests are also run. The additional fixture test loads the actual OBJ files and checks registered bulb counts, heads and old IDs.

The native model check is in EAMedia's opt-in `eln.mediaModelChecks` campaign because it verifies the new wall TV together with the new core models. It checks actual terrain bodies and item geometry, reloads resources and captures screenshots. It runs under software rendering in the cloud; this is functional verification, not a client FPS or physical-audio benchmark.

Final command outputs, counts and limitations are recorded with the release artifacts under `docs/verification/release-3.0.67/`.

The initial full suite ran 1,004 tests: 1,002 passed; the two failures were the old fixture-count assertion and a rear trim extending into the wall. Both were corrected. A subsequent 114-test focused run passed, followed by additional final packaging/cord checks. The final reports distinguish these runs rather than treating the initial run as fully successful.

The final core run passed **111 tests** covering cords, all UPS variants, wall cabinets and lighting. Data generation then completed successfully and emitted all six new core recipes, advancements and item models. The native retest uses this final packaged jar.

The native test also caught an inherited item-load override that skipped the portable snapshot. Calling the base loader restores the cells and input cord; **26 UPS tests** passed afterward, including a new 240 V snapshot regression. The final native result is recorded separately.

The final packaged native run passed all **7 observations**. All thirteen models had terrain bodies and all 65 item/context combinations had baked geometry before and after resource reload. Four lamps measured 119.980–119.982 V from the compact UPS; individual disconnect and pickup/replacement preserved the expected connections and four cells. The cabinet close-up shows live output-voltage/charge readouts and the amber backup indicator. Screenshots and the full native report are included alongside the test reports.
