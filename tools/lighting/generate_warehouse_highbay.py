#!/usr/bin/env python3
"""Original adjustable warehouse fixture; body +X points down from its ceiling canopy."""
import runpy
from pathlib import Path
source=Path(__file__).with_name('build_fixture_models.py')
scope={'__file__':str(source)}
# Load the mesh primitive definitions without regenerating unrelated fixtures.
exec(compile(source.read_text().split("\nm=Mesh();",1)[0],str(source),'exec'),scope)
Mesh=scope['Mesh']
m=Mesh();m.part('Base')
for z in [-.82,.82]:m.box((.005,-.10,z-.11),(.06,.10,z+.11))
# Unit wires scale only along the drop axis; body coordinates are translated separately.
m.part('Suspension')
for z in [-.82,.82]:m.tube((0,0,z),(1,0,z),.008,n=6)
m.part('Metal');m.box((.06,-.36,-1.20),(.23,.36,1.20))
for z in [-.82,.82]:
 for y in [-.27,.27]:m.tube((.06,0,z),(.25,y,z),.009,n=6)
for y in [-.35,.35]:m.box((.23,y-.015,-1.2),(.46,y+.015,1.2))
for z in [-1.16,1.16]:m.box((.23,-.36,z-.015),(.46,.36,z+.015))
m.part('Trim')
for y in [-.30,.30]:m.box((.25,y-.012,-1.12),(.46,y+.012,1.12))
for i in range(4):
 y=-.20 if i<2 else .20;z=-.55 if i%2==0 else .55
 m.part('Light'+str(i));m.box((.46,y-.075,z-.46),(.50,y+.075,z+.46))
m.save('adjustablewarehousehighbay')
