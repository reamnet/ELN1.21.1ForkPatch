#!/usr/bin/env python3
"""Original 5 x 4 x 1 four-tier growing bed; all geometry is section-baked."""
from generate_fans_and_outlet import Mesh

m = Mesh()
# Canonical -X front. Four levels across five blocks along Z.
for x in [-.49, .39]:
    for z in [-2.49, 2.39]:
        m.box('Frame', x, -.5, z, .1, 4, .1)
for y in [-.49, 3.39]:
    for x in [-.47, .37]:
        m.box('Frame', x, y, -2.39, .1, .1, 4.78)
for level in range(4):
    y = -.32 + level * .96
    m.box('Frame', -.44, y, -2.4, .88, .12, 4.8)
    m.box('Soil', -.36, y + .12, -2.33, .72, .065, 4.66)
    for x in [-.44, .36]:
        m.box('Black', x, y + .12, -2.39, .08, .12, 4.78)
    # Twelve bulbs on every shelf = 48 physical holders.
    for j in range(12):
        z = -2.23 + j * .405
        m.box('Frame', -.14, y + .79, z - .1, .28, .045, .2)
        m.cyl('Black', 1, (0, y + .69, z), .1, .055, n=8)
        m.cyl('Bulbs', 1, (0, y + .64, z), .08, .073, .048, n=10)
    # Five plants per tray, four growth stages; only the current one is baked.
    for j in range(5):
        z = -1.92 + j * .96
        for stage in range(4):
            part = f'Plants{stage}'
            h = .10 + stage * .10
            m.box(part, -.018, y + .185, z - .018, .036, h, .036)
            for side in [-1, 1]:
                m.box(part, min(0, side * (.055 + stage * .035)), y + .20 + h * .4,
                      z - .09 - stage * .025, .075 + stage * .035, .025, .18 + stage * .05)
                m.box(part, -.08 - stage * .025, y + .20 + h * .7,
                      z + min(0, side * (.055 + stage * .025)), .16 + stage * .05, .025, .07 + stage * .03)
# Bottom-center controller: blue water inlet, green harvest outlet.
m.box('Black', -.49, -.43, -.43, .24, .38, .86)
m.box('Display', -.495, -.18, -.13, .01, .09, .26)
m.cyl('Water', 0, (-.498, -.30, -.25), .02, .065, n=16)
m.box('Output', -.5, -.38, .13, .02, .16, .22)
m.save('growingbed')
