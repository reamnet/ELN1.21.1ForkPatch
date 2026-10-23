# Laundry and fusion matter production

## Washing and drying

The Electric Washing Machine uses **120 V / 800 W**. Put one damaged tool,
weapon, shield or armor item into its input. A cycle uses **1,000 mB of water**
and **48 kJ**, taking 60 seconds at rated power. The internal tank holds 10,000 mB.
Every face exposes the standard NeoForge fill-only fluid capability, so Ender IO
and other compatible pipes can supply water. Non-water fluids are rejected.

The washer outputs **Wet Equipment**. This is a sealed, unusable carrier for the
fully repaired original item. It retains the original enchantments, name,
components and mod data; it cannot be equipped or used as the original tool.

The Electric Dryer uses **240 V / 6,000 W**. It accepts Wet Equipment and restores
the usable repaired item after **720 kJ**, or 120 seconds at nominal power.

Both machines accept horizontal floor cables. The washer also accepts a normal
120 V Flexible Appliance Cord. The dryer draws 25 A at nominal voltage, so it has
a new **240 V 30 A Dryer Cord** and matching NEMA 6-30 outlet; it does not accept
the 20 A appliance cord. Select either a cord or fixed cables: plugging in a
cord isolates the machine's fixed-cable terminals. Use an upstream breaker.

Power outages pause work; cycles are paid for by actual electrical energy.
Lower voltage reduces the power of these passive loads and lengthens a cycle.
Reserved wash water and paid progress survive save/reload. Changing the input
abandons that job and its already used water. Full outputs stop new work.

## Refrigerator preservation

Both refrigerator sizes pause **Spoiled**, **Freshness** and
**FFFoodSpoilageAndPreservation** while their storage is at or below **5 C**.
These mods remain optional. No global container blacklist or permanent item
immunity is added. The saved cold-storage clock preserves Freshness age across
unloaded-time intervals while the saved cabinet remains cold. Food retains its
existing spoilage state; the fridge does
not turn rotten food fresh. Removing food or allowing the cabinet to warm above
5 C resumes spoilage. Residual cold continues to protect food briefly after
power loss while the cabinet warms.

The integrations are scoped to food-storage slots, including pipe/menu reads.
Spoiled recipe processing is suppressed for resident cold stacks; Freshness's
production timestamp is shifted by cold storage time; FF's accumulated progress
is retained while its clock is paused. Optional mixins avoid compile-time mod
dependencies. The independent CurseForge-only `food-spoilage` project has not
been verified; no compatibility is claimed for that additional project.

## Fusion Matter Reactor

The reactor occupies **6 blocks wide × 2 deep × 2 high**. Placement reserves its
whole rotated footprint. Supply **3,200 V** through a floor cable to the
controller. At maximum demand it draws 156 A, so the existing 125 A industrial
cord is not accepted. The controller has a GUI with a
retained block sample, output, power demand, energy progress and nominal duration.

Each cycle produces one clean block matching the sample's item type. The sample
is retained. Item components and storage contents are not copied. Blocks without
a safe mapped value, or in the blacklist, cannot be produced.

The embedded ProjectE graph calculator derives values through ordinary crafting
and cooking recipes, including ingredient alternatives and recipe output counts. Exact fractions
preserve reversible recipes such as nine nuggets to one ingot.
Unknown ingredients do not acquire arbitrary prices. Value generation happens
at server startup and datapack reload, rather than during electrical ticks.
ProjectE itself is **not required**; no EMC currency or other ProjectE features
are added. See [source and license attribution](projecte-fusion-calculator.md).

Let `t = clamp(log2(max(1, value)) / 20, 0, 1)`. Demand is `100 + 400*t` kW and
nominal duration is `10 + 20*t` minutes. Thus each block costs **60–900 MJ**.
Brownouts below 80% of required power pause production, and no work completes
while the controller's chunk is unloaded. Progress survives saving and reboot;
o offline production is credited. A source capable of the demand is required.
The machine is a passive resistive load, avoiding iterative converter probing.

Datapacks can supply `data/<namespace>/fusion_values/<name>.json`:

```json
{
  "values": {
    "some_mod:rare_ingot": 65536,
    "some_mod:unusual_block": 100000
  },
  "blacklist": ["some_mod:creative_only_block"]
}
```

Use a path which sorts after `eln:fusion_values/defaults.json` when overriding
an existing base value. The `eln:fusion_blacklist` item tag can also prohibit
blocks. Run `/reload` to recalculate. Only this reactor uses the calculated values.

The washer, dryer and reactor have stationary original OBJ bodies baked into
normal chunk meshes. Their progress packets do not invalidate the model. Static
item previews use the existing baked-item cache. Cables and flexible cords keep
their existing rendering behavior.
