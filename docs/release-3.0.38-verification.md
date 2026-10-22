# 3.0.38 outlet feeds and smoother appliance cords

## Wiring recessed outlets

Recessed outlets preserve a wall block inside their node's cell and move the
visible receptacle onto its outside face. Their feed terminals previously kept
the original surface-mounted cabinet coordinates. Ordinary wall cables beside
the visible socket therefore could not reach those terminals. This affected
the household and industrial outlet variants, including 480 V.

`OutletWiring` now locates the real cable mounted on that wall face above, below
or beside the socket. The normal node connection process calls it from either
end on topology changes, so either placement order works. It supports both
legacy electrical cables and the separate newer utility-cable implementation.
It also works if a block or appliance occupies the cell in front, because that
cell's emptiness is not part of the wall cable's electrical route.

The connection uses real electrical loads and the existing cable masks. It
does not generate or convert voltage, bypass a feeder breaker, reconnect every
simulation tick, or implicitly plug in a machine that happens to touch the
outlet. Duplicate connections are rejected. Cable mode and wireless mode stay
electrically isolated, and existing surface-mounted outlets keep their original
terminals. No saved item IDs, cable IDs or outlet identities change.

To feed a recessed outlet, select **Cable power** in its GUI and place ordinary
ELN wall cable on the wall above, below or beside the receptacle. Use a source
matching the outlet and appliance voltage. The flexible cord remains the lead
from the outlet to the appliance.

## Cord routing and drawing

- Sample the ground more closely and use an upper parabolic height envelope.
  The lead starts climbing before a raised support and bends down gradually
  afterwards, retaining both exact attachment points. The envelope stays above
  the sampled supports rather than smoothing through them.
- Share each cross-section between adjacent tube segments and transport its
  orientation through bends. This removes separate elbows and sudden reference
  axis changes near vertical sections.
- Interpolate the carried endpoint between game ticks, rather than displaying
  player position in twenty steps per second.
- Rebuild a placed cord immediately when its plug or appliance endpoint changes,
  including socket rotation. Cache its recorded mesh between changes, refresh
  terrain checks once per second, and release the cache on unplug, renderer
  replacement and unload. Lighting and shader attributes still use the current
  render context.
- When no ground exists within the terrain scan, keep the free curve instead
  of inventing a floating floor relative to the endpoint height.

This is still the terrain-following visual cord prototype. It is not a physics
rope or a general pathfinder around enclosing walls.

## Validation

The native client campaign creates 32 recessed outlets: all eight ratings on
all four wall directions, with the face cell occupied and both outlet/cable
placement orders. Four additional 480 V cases retain the original surface mount.
Each is fed through placed utility cable by an electrical source and draws a
250 W resistor load. It also plugs in a real 480 V industrial pump and confirms
that an adjacent, unplugged mini fridge stays electrically isolated.

The campaign checks voltage, load current and exactly one physical feeder
connection per outlet, switches a feed into wireless mode and back, and reopens
the saved world to check the real cable feeds and 480 V cord link again. Native
transform and shader-attribute checks run with Sodium 0.8.13, Iris
1.8.14-beta.1 and BSL 10.1.8 enabled.

The opt-in command, with a working display and those renderer mods in the
development client, is:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.outletCordChecks=true" ./gradlew --no-daemon --max-workers=3 runClient
```

It writes `run/client/outlet-cord-checks.json` and exits. Check the report's
failure field as well as Gradle's result.

Unit regressions cover the original and utility cable types on every outlet
rating, wall edge and facing, rejection of implicit appliance connections,
wireless isolation, original mounts, rounded obstacle transitions, slab support,
vertical drops, invalid support heights, shared ring radius and near-vertical
frame continuity.

All **817 unit tests passed**, with zero failures, errors or skips. All **36 native
outlet setups** and the power-mode, isolation, shader and cold-reload checks passed.

Final results are in [the native report](verification/release-3.0.38/native.json)
and [the unit summary](verification/release-3.0.38/units.json).

The source-only release builds `build/libs/ElectricalAge-3.0.38.jar` with JDK 21.
See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
