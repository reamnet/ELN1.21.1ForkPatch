# 3.0.33 original wind turbine fix

The original Wind Turbine could accelerate to excessive rotor speeds and continue producing misleading electrical output. The rad/s reading itself is the rotor's angular speed; the fault was the wind and rotor/power handling behind it.

## Causes and changes

`WindProcess.getWind` clamped the **height** between a wind-derived lower bound and 100 instead of clamping the calculated wind speed. With a 5 m/s reference wind, Y=100 returned 100 m/s rather than 5 m/s. It now calculates `referenceWind * height / 100` and bounds that result. Invalid wind values and negative-height results cannot propagate NaN or negative speed. This shared wind-reading correction applies to wind-powered devices generally, so available generation now follows the intended height/weather-scaled wind instead of altitude being mistaken for m/s.

The old rotor's aerodynamic curve remained positive at excessive speed and its high-wind cutout was disabled. It now tapers harvesting toward a wind-dependent governed speed, bounded at 1.25 times nominal rotor speed (60 rad/s for the registered original turbine). Below the 3 m/s cut-in or at/above its 22 m/s cutout it harvests no wind power and brakes/coasts down. Overspeed braking and friction dissipate kinetic energy; they do not become electrical output. Sustained aerodynamic input is bounded by the configured generator rating and conversion efficiency.

The old electrical regulator also advanced its voltage filter during algebraic trial solves and used only a sampled, slower electrical-power reading to debit the rotor. It now uses the shared conservative shaft generator controller with reverse power disabled. The controller limits each electrical step to the available rotor kinetic energy and current rating, and advances its voltage ramp once per physical step. A registered post-electrical callback debits the actual accepted output and accounts for wind input, mechanical losses and efficiency on every electrical step. A stopped turbine opens its source without repeatedly probing/refactoring an idle port. Other shaft generators retain their existing reversible behavior by default.

Old save keys and registry IDs are retained. Saved original-turbine rotor speed is bounded to its valid maximum, and invalid/negative values are reset, so existing overspeed units recover without replacing the block. The previous saved regulator voltage does not seed a runaway controller. The client uses the bounded normalized rotor speed directly for animation and wraps its angle. Meter voltage reads the solved terminal voltage, including when the source is open on an externally powered grid.

## Tests

The initial height/wind and overspeed-save regressions both failed against 3.0.32: Y=100 returned 100 m/s for a 5 m/s reference wind, and a saved 1,000,000 rad/s rotor was restored without a bound.

`LegacyWindTurbineTest` covers:

- Correct wind scaling at negative, normal and high world heights, and invalid wind input.
- Overspeed, negative and nonfinite saved rotor values.
- Ten minutes of unloaded rotor operation at low, nominal and strong sub-cutout winds.
- Calm/low-wind and storm-cutout braking until both rotor speed and electrical output reach zero.
- Startup, open loads, normal loads and shorts, checking current limits and the full per-step joule balance.
- Blocking an externally powered 480 V grid from motoring or charging the rotor.
- Trial solves not advancing rotor energy or the voltage ramp.
- Saved rotor/source state resuming through the production registered electrical-accounting callback.

```bash
./gradlew --no-daemon --max-workers=3 test
```

The full suite passed **796 tests**, with zero failures, errors or skips. This includes eight original-wind-turbine regressions, the existing Large Generator tests and the prior convergence/protection suite. Verification used NeoForge’s JUnit runtime and production circuit components; no native Minecraft world or visual campaign was run for this release.
