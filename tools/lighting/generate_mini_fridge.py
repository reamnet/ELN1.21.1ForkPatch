#!/usr/bin/env python3
"""Compact original mini fridge: single door, ice-box seam, handle, feet and rear compressor grille."""
from generate_fans_and_outlet import Mesh
m=Mesh()
m.box('Case',-.34,-.46,-.30,.68,.78,.60)
m.box('Case',-.325,-.445,.30,.65,.75,.045)
m.box('Black',-.32,-.46,.299,.64,.008,.004)
m.box('Black',-.32,.14,.345,.64,.008,.005)
m.box('Trim',.235,-.10,.35,.035,.31,.045)
m.box('Trim',.23,-.105,.345,.045,.03,.045)
m.box('Trim',.23,.18,.345,.045,.03,.045)
for x in [-.27,.27]:
 for z in [-.23,.23]:m.cyl('Black',1,(x,-.50,z),.04,.032,n=12)
for i in range(9):m.box('Black',-.27+i*.06,-.36,-.307,.025,.21,.008)
m.box('Display',-.22,.23,.346,.06,.025,.004)
m.save('kitchenminifridge')
