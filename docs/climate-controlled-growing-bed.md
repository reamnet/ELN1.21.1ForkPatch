# Climate Controlled Growing Bed

A four-tier growing rack, five blocks wide, four blocks high and one block
deep. Place it on the ground with the whole footprint clear. The controller is
in the bottom-center block; connect electrical cables, a water pipe and an item
extraction pipe there. The blue inlet and green harvest port mark the controller.

Open the GUI and insert planting stock, up to 48 matching light bulbs, and a
heating element. Standard crop-block planting items work, including wheat and
beetroot seeds, carrots and potatoes, plus mod crops implemented as `CropBlock`.
Trees, arbitrary plants and custom non-crop farming systems are not supported.
Up to 20 planting items represent the standing plants and stay in the rack.
Harvests use the crop's actual mature-block loot, reserving one planting item
per plant before exporting surplus. Fortune is not applied.

The tank accepts water from standard NeoForge fluid pipes on any controller
face, including Ender IO fluid conduits. It holds 10 buckets and reserves 50 mB
per planted crop per cycle (one bucket for all 20 plants). Item pipes can insert
planting stock and extract the nine harvest slots. Bulbs, the heater and planted
stock cannot be extracted through sided or unsided pipe capabilities. Install
and remove those service items by hand in the GUI.

Power is 240 V, using placed cables, a 240 V appliance cord, or the existing
240 V 30 A dryer cord. Internal matching drivers allow any supported bulb
voltage; all bulbs in a bank must be the same type. Actual bulb nameplate watts
add up, so choose an appropriate circuit/cord rating. There is no hidden power
limit that reduces the load to fit a cord. Bulbs age normally and used-up bulbs
stop producing light. Fewer bulbs reduce growth proportionally.

The thermostat defaults to 23 C and can be set from 10 to 35 C. A heating
element supplies its rated power, capped at 1500 W in this rack, through a real
passive electrical load. Temperature changes gradually with local ambient
climate, bulb heat and heater power. Heating does not provide cooling; a hot
biome or a very hot bulb bank can exceed the growing range.

At full effective light and 18-28 C, a harvest takes 180 powered seconds.
Growth slows outside that temperature band and stops at or below 5 C / at or
above 40 C. Brownouts reduce light and growth. Power loss pauses growth; no
water means the next cycle cannot start. Completed harvests wait intact when
output is full. Progress, tank contents, temperature and a blocked pending
harvest survive saves; changing planting stock resets that batch. Unloaded
machines receive no offline production credit.

The frame, trays, 48 holders, controller and crop stage geometry use the normal
baked terrain path. Voltage and GUI progress packets do not rebuild the mesh;
only changes in orientation, light state, planted state or one of four crop
stages change its geometry key.
