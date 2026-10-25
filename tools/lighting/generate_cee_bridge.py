#!/usr/bin/env python3
"""Original ELN/CEE bridge cabinet, with orange extraction face and blue input lugs."""
from generate_fans_and_outlet import Mesh
m=Mesh();m.box('Case',-.44,-.45,-.41,.88,.83,.82)
m.box('Trim',-.47,-.49,-.44,.94,.06,.88)
for y in [-.25,-.14,-.03,.08,.19]:m.box('Trim',-.455,y,-.31,.025,.025,.62)
for z in [-.27,.27]:m.cyl('Input',0,(-.49,-.24,z),.06,.065,n=16)
m.box('Output',.44,-.24,-.25,.055,.48,.50)
m.box('Screen',-.19,.20,.415,.38,.12,.012);m.box('LED',.24,.24,.415,.04,.04,.014)
m.save('ceepowerbridge')
