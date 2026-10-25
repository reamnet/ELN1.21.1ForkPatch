#!/usr/bin/env python3
"""Original contactor, open-frame changeover relay and solid-state relay meshes."""
from generate_fans_and_outlet import Mesh
for name,poles,relay,ssr in [('contactor',1,False,False),('contactor_three',3,False,False),('relay_two',2,True,False),('relay_four',4,True,False),('ssr',1,False,True),('ssr_three',3,False,True)]:
 m=Mesh();w=.25 if poles==1 else .42
 m.box('Base',-w,-.49,-.35,2*w,.09,.70)
 m.box('Case',-w+.025,-.40,-.25,2*w-.05,.25 if ssr else .43,.50)
 for i in range(poles):
  x=(i-(poles-1)/2)*(.17 if poles==4 else .22)
  for z,part in [(-.29,'Terminal'),(.25,'NO')]+([(.10,'NC')] if relay else []):
   m.box(part,x-.055,-.16 if ssr else .04,z,.11,.07,.075)
   m.cyl('Terminal',1,(x,-.087 if ssr else .115,z+.035),.012,.023,n=10)
 if ssr:
  for j in range(9):m.box('Heatsink',-w,-.39+j*.025,-.35,2*w,.012,.12)
  m.box('Case',-w+.04,-.14,-.22,2*w-.08,.025,.44)
 else:
  m.cyl('Coil',0,(-.15,-.16,0),.30,.11,n=20)
  m.box('Armature',-w+.06,.07,-.17,2*w-.12,.035,.34)
  for x in [-w+.05,w-.075]:m.box('Heatsink',x,-.33,-.16,.025,.42,.04)
 m.box('Control',-.14,-.35,.285,.28,.11,.09)
 for x in [-.09,.09]:m.cyl('Terminal',2,(x,-.28,.38),.012,.023,n=10)
 m.box('Indicator',-.025,-.14 if ssr else .11,-.07,.05,.025,.05)
 m.save('switchgear_'+name)
