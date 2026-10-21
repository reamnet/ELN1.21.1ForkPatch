#!/usr/bin/env python3
"""Keyed fictional machine couplers (not NEMA household plugs), and an open service disconnect."""
from generate_fans_and_outlet import Mesh
from math import sin,cos,pi
m=Mesh()
m.box('Case',-.17,-.31,-.49,.34,.62,.035)
for y in [-.1,.16]:
 m.cyl('Case',2,(0,y,-.455),.09,.128,n=24)
 m.cyl('Trim',2,(0,y,-.366),.008,.113,n=24)
 m.cyl('Black',2,(0,y,-.357),.004,.095,n=24)
 for a in [0,2*pi/3,4*pi/3]:
  m.cyl('Case',2,(.048*cos(a),y+.048*sin(a),-.352),.004,.018,n=10)
 m.box('Black',.079,y-.014,-.35,.022,.028,.006)
for y in [-.27,.27]:m.cyl('Trim',2,(0,y,-.449),.008,.015,n=12)
m.save('industrialmachineoutlet')
m=Mesh()
m.cyl('Case',2,(0,-.10,-.354),.095,.096,.085,n=24)
for z in [-.337,-.312,-.287]:m.cyl('Grip',2,(0,-.10,z),.009,.102,n=24)
m.cyl('Case',2,(0,-.10,-.259),.035,.040,.026,n=16)
for a in [0,2*pi/3,4*pi/3]:m.cyl('Blades',2,(.048*cos(a),-.10+.048*sin(a),-.410),.058,.012,n=12)
m.box('Grip',.078,-.114,-.385,.018,.028,.03)
m.box('Badge',-.026,-.120,-.257,.052,.040,.003)
m.save('industrialmachineplug')
# No front face or closed door: backplate and four side walls form an open enclosure.
m=Mesh()
m.box('Case',-.31,-.43,-.49,.62,.86,.06)
m.box('Case',-.31,-.43,-.43,.04,.86,.24)
m.box('Case',.27,-.43,-.43,.04,.86,.24)
m.box('Case',-.27,-.43,-.43,.54,.04,.24)
m.box('Case',-.27,.39,-.43,.54,.04,.24)
m.box('Insulator',-.26,-.34,-.425,.52,.68,.055)
for x in [-.17,0,.17]:
 for y in [-.31,.22]:
  m.box('Lugs',x-.045,y,-.37,.09,.09,.09)
  m.cyl('Hinge',2,(x,y+.045,-.28),.012,.022,n=12)
 m.box('Blade'+str(round((x+.17)/.17)),x-.025,-.26,-.28,.05,.52,.03)
# The spindle and entire red lever remain outside the right wall in both states.
m.cyl('Hinge',0,(.30,0,-.24),.10,.027,n=16)
m.box('Handle',.34,-.025,-.27,.06,.295,.06)
m.box('Handle',.33,.19,-.30,.10,.08,.12)
m.save('redhandleservicedisconnect')
