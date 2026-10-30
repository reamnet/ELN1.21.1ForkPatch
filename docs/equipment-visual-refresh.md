# Equipment appearance and refrigerator doors (3.0.68)

The asset inventory covers the 224 OBJ meshes introduced after 3.0.1. Two tall
variants already use the original ELN art and keep those textures. The other
222 gain original atlas-backed finishes: painted and brushed metal, porcelain,
wood grain, fabric, rubber and molded enamel. No external model assets are used.

The refresh softens 135 box-shaped equipment sections and adds small construction
details to 33 cabinets, including folded seams, hinges, fasteners, latches,
ventilation and service labels. Lamps, fans, pole equipment, underground service,
transmission structures, kitchen/laundry appliances, renewable controllers,
industrial equipment and accessories retain their original registered identities.
Animated parts, connection points, collision rules and machine behavior are retained,
apart from the refrigerator change described below. Existing saved installations
receive the revised art without replacement.

The optional EAMedia 1.0.10 release updates its three media cabinets too. The TV
picture area, browser operation and media controls retain their existing geometry.

## Catalog and rendering

The 148 new lamp/pole items now have shaded 32 × 32 pixel catalog sprites, matching
the older Electrical Age item artwork. These are generated from the actual
descriptor geometry and retain distinct silhouettes for different fixtures and
pole equipment. Ceiling fittings are shown from below so their lenses can be seen.
The bulb in catalog artwork is illustrative; recipes still produce empty fixtures.

Creative/inventory thumbnails use one baked quad instead of a complete model.
Held and dropped objects keep their 3D models. Placed blocks use the existing baked
terrain path; moving rotors, switched lenses and live readouts retain their existing
dynamic paths. The shared finish sheet is part of the normal terrain atlas, including
the Sodium/Iris path, and respects resource-pack replacements.

Solar controllers, generator controls, industrial cabinets and laundry/kitchen
equipment with suitable display panels now show live values. Controller/generator
and industrial readouts show volts; laundry/kitchen processing readouts show percent
complete; refrigerators show interior Celsius. Small indicator-only panels remain
indicators. Digits update separately from the static enclosure.

This is an appearance update, not a measured in-game FPS benchmark. Adding surface
detail does not make geometry free; the static parts remain baked to avoid adding
per-frame OBJ rendering work.

## Refrigerator doors and food

Opening either refrigerator inventory opens its modeled door and increases heat
exchange with the surrounding temperature. The compressor has finite cooling power,
so leaving the door open warms the interior even with a working supply. Closing it
allows powered cooling to bring the contents back to the normal 3.5–5 °C range.

With multiple viewers the door stays open until the last viewer closes the menu.
Disconnects and menu changes are cleaned up, and a server restart does not restore
stale viewers. Temperature is saved, including temperatures above the old 20 °C cap.
The existing spoilage integrations protect food only while the interior is 5 °C
or colder; warm open storage loses that protection until it cools again.

## Reproducing assets

Ordinary builds use the committed meshes and sprites; no Python is needed.
Authoring tools require Python 3, Pillow and NumPy:

```bash
python3 tools/models/refresh_equipment.py
```

The manifest pins unmodified source meshes to `v3.0.67`, so rerunning the tool never
repeatedly bevels its previous output. Run the opt-in native
`-Deln.visualRefreshChecks=true -Deln.visualExportOnly=true` campaign to export
the actual item previews, then render their sprites with:

```bash
python3 tools/models/refresh_catalog.py run/client/visual-refresh-checks/catalog-meshes.json.gz
./gradlew runData
```

The native output path follows the selected client run directory. Native test flags
are for development verification only and must not be enabled in normal play.
