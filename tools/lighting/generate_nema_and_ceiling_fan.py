#!/usr/bin/env python3
"""Original NEMA 20 A plates/plugs and a five-metre HVLS ceiling fan; Python 3.
Blade coordinates use the outlet-facing view. Male mating faces are mirrored.
Ground down: both receptacles have a left T slot; 5-20R has a vertical right
slot, 6-20R a horizontal right slot. Reference: StayOnline NEMA straight-blade chart.
"""
from generate_fans_and_outlet import Mesh
from math import cos,sin,tau
for rating in [120,240]:
 m=Mesh()
 m.box('Case',-.17,-.31,-.49,.34,.62,.035)
 m.box('Case',-.135,-.23,-.455,.27,.46,.09)
 # T-neutral left + vertical hot right (5-20); T left + horizontal right (6-20).
 m.box('Black',-.089,-.057,-.362,.070,.017,.004)
 m.box('Black',-.0625,-.080,-.362,.017,.064,.004)
 if rating==120:m.box('Black',.0455,-.080,-.362,.017,.064,.004)
 else:m.box('Black',.019,-.057,-.362,.070,.017,.004)
 m.cyl('Black',2,(0,-.145,-.362),.004,.020,n=24)
 for y in [-.27,.26]:
  m.cyl('Trim',2,(0,y,-.450),.004,.014,n=16)
  m.box('Black',-.010,y-.0015,-.445,.020,.003,.002)
 m.box('Display',.09,.18,-.361,.018,.018,.004)
 m.save('appliancewalloutlet' if rating==120 else 'walloutlet240')
 m=Mesh()
 # Round moulded plug, raised finger grips, two blades, ground and strain relief.
 m.cyl('Case',2,(0,-.10,-.354),.095,.096,.085,n=32)
 for z in [-.337,-.312,-.287]:m.cyl('Grip',2,(0,-.10,z),.009,.098,n=32)
 m.cyl('Case',2,(0,-.10,-.259),.035,.040,.026,n=16)
 # Blade orientation is horizontal neutral for 5-20P, rotated opposite for 6-20P.
 flat=-.054 if rating==120 else .054
 for x in [-.054,.054]:
  w,h=(.064,.012) if x==flat else (.012,.064)
  m.box('Blades',x-w/2,-.048-h/2,-.410,w,h,.058)
 m.cyl('Blades',2,(0,-.145,-.410),.058,.013,n=16)
 m.box('Badge',-.026,-.120,-.257,.052,.040,.003)
 m.save('nema520plug' if rating==120 else 'nema620plug')
m=Mesh()
m.cyl('Base',0,(0,0,0),.075,.15,.12,n=32)
m.cyl('Metal',0,(.075,0,0),.36,.025,n=20)
m.cyl('Metal',0,(.395,0,0),.16,.17,.14,n=32)
m.cyl('Trim',0,(.54,0,0),.025,.13,n=32)
for j in range(6):
 a=j*tau/6
 def v(rad,angle,x):return(x,rad*cos(a+angle),rad*sin(a+angle))
 f=[v(.12,-.35,.44),v(2.50,-.025,.44),v(2.50,.035,.44),v(.22,.38,.44)]
 b=[(x+.035,y,z) for x,y,z in f]
 m.poly('Rotor',list(reversed(f)));m.poly('Rotor',b)
 for i in range(4):m.poly('Rotor',[f[i],f[(i+1)%4],b[(i+1)%4],b[i]])
m.cyl('Light0',0,(.565,0,0),.04,.09,n=24)
m.save('industrialceilingfan')
