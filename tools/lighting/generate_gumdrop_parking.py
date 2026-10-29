#!/usr/bin/env python3
"""Five appended outdoor fixtures; +X rises from the floor, matching existing lamp optics."""
from pathlib import Path
# Reuse the geometry helper without executing the older generator's fixtures.
source=(Path(__file__).with_name('generate_pool_parking_models.py')).read_text()
ns={'__file__':__file__};exec(compile(source[:source.index('# Wall-mounted area flood')],__file__,'exec'),ns)
Mesh=ns['Mesh'];post=ns['post'];head=ns['parking_head']
from math import pi,cos,sin
for name,points in [('shoeboxparkinglight',[(.92,0)]),('twinareaparkinglight',[(-.95,0),(.95,0)]),('triarmparkinglight',[(1.1*cos(i*2*pi/3),1.1*sin(i*2*pi/3)) for i in range(3)])]:
 m=Mesh();post(m,5.78)
 for i,(y,z) in enumerate(points):
  m.tube('Metal',(5.78,0,0),(5.78,y,z),.045)
  head(m,i,5.65,y,z)
  m.box('Head%dTrim'%i,5.76,y-.17,z-.13,.015,.34,.26)
 m.save(name)
for name,centers in [('gumdropsidewalklight',[(0,0)]),('dualgumdropsidewalklight',[(-.63,0),(.63,0)])]:
 m=Mesh();post(m,3.28)
 m.cylinder('Trim',0,(.10,0,0),.08,.18,.13)
 for i,(y,z) in enumerate(centers):
  m.tube('Metal',(3.19,0,0),(3.28,y,z),.038)
  m.cylinder('Metal',0,(3.28,y,z),.10,.16,.22,n=20)
  # Continuous rounded gumdrop diffuser: stacked gently tapering rings form a domed glass shade.
  steps=10
  for j in range(steps):
   a=(j/steps)*pi/2;b=((j+1)/steps)*pi/2
   def ring(t,k):return (3.38+.40*sin(t),y+.235*cos(t)*cos(k*2*pi/24),z+.235*cos(t)*sin(k*2*pi/24))
   for k in range(24):m.face('Light%d'%i,[ring(a,k),ring(a,k+1),ring(b,k+1),ring(b,k)])
  for k in range(24):m.face('Light%d'%i,[(3.38,y,z),(3.38,y+.235*cos((k+1)*2*pi/24),z+.235*sin((k+1)*2*pi/24)),(3.38,y+.235*cos(k*2*pi/24),z+.235*sin(k*2*pi/24))])
  m.cylinder('Trim',0,(3.365,y,z),.018,.245,n=24)
 m.save(name)
