# 3.0.30 verification

This release combines the utility/lighting/cord fixes and the subsequently requested workshop cords, mini fridge and electrical deck equipment. Existing numeric IDs and registry names are retained; new items use free sub-IDs or a new group. Clients and servers must both update because element synchronization includes new aiming, cord and circuit settings.

## Supplied server profile

The 3.0.29 profile placed 32.57% of server-thread samples in `Simulator.tick`, including 15.79% in thermal integration (15.28% specifically in overhead-span property refresh) and 10.97% in transformer pre-step work. The latter was dominated by matrix regeneration/factorization after impedance changes. These percentages describe sampled CPU attribution, not milliseconds per tick or a guaranteed speedup.

Pole endpoints now share one climate sample per game second across phases and neighboring spans. Cooling geometry is computed once per conductor. The electrical stamp is refreshed when resistance drifts by more than 0.01%; abrupt heating still updates on the next thermal step. Joules, cooling, melting and fuse checks continue at their original cadence. Electrical operating points and grid sag/rise still solve each electrical step.

`SpanThermalPerformanceTest` checks climate sample counts/clock rollback, resistance accuracy during gradual heating and an immediate hot-conductor update, and equivalence of the cached cooling geometry across materials, sizes, insulation states and temperatures. In its gradual-warming case, fewer than 25 resistance stamps replace 4,000 candidate stamps without exceeding the 0.01% resistance bound. This measures avoided work, not whole-server tick time.

Client rendering reuses recorded topology, reuses temporary vectors and leaves shared material-buffer flushing to Minecraft. Pole culling uses the actual span/model bounds, and placed node/ghost blocks no longer cast a hidden opaque-cube shadow. No particular FPS gain is claimed from the software-rendered cloud client.

## Commands and results

```bash
./gradlew --no-daemon --max-workers=3 test generateLangFiles runData
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.utilityDistributionChecks=true" ./gradlew --no-daemon --max-workers=3 runClient
```

The native command requires a working display and audio device or suitable test-only headless equivalents. The opt-in campaign writes `run/client/utility-distribution-checks.json` and screenshots; read its failure field because a campaign failure can still exit the game with status zero.

The full JUnit run passed **772 tests**, with zero failures, errors or skips. Recipes, item models and translations were generated successfully. The full native Minecraft campaign passed **28 grouped checks**, including a complete world restart, loaded house service and 5% sag/rise, a 5.4 kW chandelier, breakers/fuses/reclosers, pole phase selection, source-water preservation, all new fixtures, suspended strings, machine cords and mini-fridge cooling. A targeted gallery run also passed its four grouped checks.

The visual review confirmed the open service disconnect and outboard red handle, and the thick-cable breaker casing. New item/recipe registration and existing save-sensitive IDs are covered by the unit and native checks.

Reports: [full distribution/cold-restart campaign](verification/utility-lighting-3.0.30/full-campaign.json) and [targeted lighting gallery](verification/utility-lighting-3.0.30/lighting-gallery.json). The final gallery also checks client powered/unpowered state for all four suspended string styles and captures both night views. The large globe meshes and dark unpowered appearance were visually inspected in the native client.
