# Decorative lighting

Versions 3.0.12 and 3.0.13 add eight fixtures in the Lighting creative tab, with crafting
recipes and models for both placed blocks and inventory items.

| Fixture | Mounting | Bulbs | Power |
| --- | --- | --- | --- |
| Twin Parking Lot Light | Ground, with clear space for its tall pole and two heads | 2 | Wireless or wired |
| Warehouse Wall Floodlight | Any vertical wall | 1 | Wireless or wired |
| Household Desk Lamp | Top of a block or table | 1 | Wireless or wired |
| Round Recessed Ceiling Light | Underside of a ceiling | 1 | Wireless |
| Basic Chandelier | Underside of a ceiling | 5 | Wireless or wired |
| Mini Dot Spotlight | Ceiling or any vertical wall | 1 | Wireless |
| Porch Lantern Sconce | Any vertical wall | 1 | Wireless or wired |
| Basic Lamp Holder | Any supporting face | 1 | Wireless or wired |

Every fixture accepts every existing bulb: incandescent, carbon incandescent,
fluorescent, farming, LED and halogen, at each available voltage. Match the supply
voltage to the bulb's rating; the fixture does not convert voltage. The chosen
bulb retains its normal brightness, power consumption, warm-up and lifetime.

Right-click with bulbs to install them, or use the bulb slot in the fixture GUI.
The twin-head light and chandelier take a stack of matching bulbs. Partial
installation works: only installed bulbs illuminate, and each installed bulb
adds its normal power draw. A batch shares its lifetime, so install matching
fresh bulbs together; differently aged bulbs cannot be combined in that slot.
The GUI shows how many bulbs are installed.

For wireless power, select **Powered by lamp supply** in the fixture GUI and enter the
channel of a powered Lamp Supply within range. As with the existing lamp sockets,
put at least one metre of suitable cable in the fixture's cable slot. Place a
fixture after configuring another lamp to reuse the remembered channel. The
recessed ceiling light and mini dot spotlight always use wireless power and have
no external terminals.

For wired power, select **Powered by cable** in the GUI and connect cable on the same mounting
surface as the fixture. Use cable rated for the voltage and combined bulb current.
The parking pole connects at its base; the wall floodlight connects along its
wall. Wired and wireless modes both use the same installed bulbs.

The mini dot spotlight is a compact cylindrical fixture, about 0.22 blocks long
and 0.17 blocks across, with a recessed lens. It shines outward from a wall or
downward from a ceiling. The porch lantern has a bronze cage, peaked roof and
transparent glass around its replaceable bulb. The basic holder has a small
porcelain-style base and an exposed bulb.

The parking light casts light beneath both heads, the floodlight projects light
outward and downward, and the recessed light projects downward. The desk lamp
and chandelier illuminate their surroundings. Lenses and bulbs become dark when
the fixture is off. The pole's upper parts reserve space, so leave clearance
above and around its heads when placing it. In 3.0.13 the pole and its heads are
two blocks taller (about 5.4 blocks overall). Existing poles update their reserved
space when loaded, provided the added space is clear; this does not replace
other blocks or force neighboring chunks to load.

Crafting uses lamp sockets and metal parts, with glass or white wool for some
fixtures. The basic holder uses a copper ingot and an iron plate. The chandelier needs five classic sockets, and the parking fixture needs
two robust sockets. Use the recipe viewer or recipe book for the exact layouts.

Fixture meshes can be regenerated with
`python3 tools/lighting/build_fixture_models.py`. After changing registrations or
recipes, run `./gradlew runData generateLangFiles` and review the generated item
models, recipes and translations. The opt-in native client check
uses `-Deln.decorativeLightChecks=true`; it exercises placement, all registered
bulbs, power modes, actual GUIs and saved-world restart, and writes screenshots
and `decorative-light-checks.json` under the client's run directory.
