#!/usr/bin/env python3
"""Original detailed UPS / ATS enclosures. Static parts enter the terrain mesh; LEDs stay live."""
from pathlib import Path
from collections import defaultdict
from math import sin, cos, pi
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
class Mesh:
 def __init__(self):self.parts=defaultdict(list)
 def box(self,p,x,y,z,a,b,c):
  v=[(x+i*a,y+j*b,z+k*c) for i,j,k in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]]
  for ids in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:self.parts[p].append([v[i] for i in ids])
 def cylinder(self,p,x,y,z,r,d,n=12):
  for i in range(n):
   a=2*pi*i/n;b=2*pi*(i+1)/n
   ring=lambda t,q:(x+r*cos(q),y+r*sin(q),z+t)
   self.parts[p].extend([[(x,y,z),ring(0,b),ring(0,a)],[(x,y,z+d),ring(d,a),ring(d,b)],[ring(0,a),ring(0,b),ring(d,b),ring(d,a)]])
 def save(self,name):
  folder=ROOT/name;folder.mkdir(exist_ok=True);lines=['# Original power equipment; front +Z','mtllib panel.mtl'];idx=1
  for p,faces in self.parts.items():
   lines+=['o '+p,'usemtl panel']
   for face in faces:
    for i in range(1,len(face)-1):
     lines+=['v %.6f %.6f %.6f'%((max(-.46,min(.46,v[0])),max(-.46,min(.46,v[1])),max(-.5,v[2])) if name.startswith('wall') else v) for v in [face[0],face[i],face[i+1]]];lines+=['f %d %d %d'%(idx,idx+1,idx+2)];idx+=3
  (folder/(name+'.obj')).write_text('\n'.join(lines)+'\n')
  (folder/'panel.mtl').write_text('newmtl panel\nKd 1 1 1\nKa 1 1 1\n')
def panel(m,z,ats=False):
 # Raised display surround, inset screen, small control keys and individually driven LEDs.
 m.box('Trim',-.255,.175,z,.51,.20,.018);m.box('Display',-.235,.19,z+.018,.47,.163,.004)
 for i,p in enumerate(['MainsLed','BackupLed','ChargeLed','FaultLed','BypassLed']):
  x=-.20+i*.10;m.cylinder('Vents',x,.135,z+.005,.025,.012);m.cylinder(p,x,.135,z+.018,.017,.004)
 for x in [-.16,-.04,.08,.20]:m.box('Trim',x-.026,.06,z+.01,.052,.025,.012)
 # Engraved-looking flow schematic; no per-frame font calls.
 for x in [-.15,.15]:m.box('Trim',x-.03,-.02,z+.004,.06,.035,.005)
 m.box('Trim',-.12,-.004,z+.004,.24,.007,.005)
 m.box('Trim',-.003,-.052,z+.004,.006,.05,.005)
 for x in [-.285,.285]:
  for y in [-.38,.38]:m.cylinder('Trim',x,y,z,.011,.007,8)
for name in ['programmableups','wallups','compactups','automatictransferswitch','wallats']:
 m=Mesh();wall=name.startswith('wall');compact=name=='compactups';ats=name in ['automatictransferswitch','wallats']
 back=-.49 if wall else -.31;front=-.207 if wall else .35 if compact else .32 if not ats else .25
 m.box('Case',-.30,-.44,back,.60,.86,front-back)
 m.box('Trim',-.315,-.47,back-.01,.63,.035,front-back+.02)
 m.box('Trim',-.313,.42,back-.015,.626,.027,front-back+.03)
 # Recessed door, gasket, hinges, latch and replaceable battery bay.
 m.box('Vents',-.28,-.405,front,.56,.80,.004)
 m.box('Case',-.268,-.393,front+.004,.536,.77,.008)
 for y in [-.27,.25]:m.box('Trim',-.29,y,front+.005,.025,.085,.023)
 m.box('Trim',.24,-.01,front+.012,.022,.10,.022)
 if not compact:
  m.box('Trim',-.23,-.335,front+.013,.46,.18,.008)
  m.box('Vents',-.18,-.258,front+.022,.36,.009,.003)
 for y in [-.33,-.28,-.23,-.18,-.13]:
  for x in [-.303,.298]:m.box('Vents',x,y,back+.08,.005,.017,max(.10,front-back-.16))
 if not wall:
  for x in [-.24,.19]:m.box('Vents',x,-.50,-.23,.05,.03,.46)
 panel(m,front+.015,ats)
 if compact:
  m.box('Input',-.08,-.43,-.335,.16,.08,.025)
  m.cylinder('Vents',0,-.40,-.35,.027,.10)
  for i in range(4):
   x=-.135 if i%2==0 else .135;y=-.08 if i<2 else -.32
   m.cylinder('Trim',x,y,.356,.095,.006,20)
   m.cylinder('Case',x,y,.362,.081,.007,20)
   for volts in [120,240]:
    part='Socket'+str(volts)
    m.box(part,x-.089,y+.0435,.370,.070,.017,.003)
    m.box(part,x-.0625,y+.020,.370,.017,.064,.003)
    if volts==120:m.box(part,x+.0455,y+.020,.370,.017,.064,.003)
    else:m.box(part,x+.019,y+.0435,.370,.070,.017,.003)
    m.cylinder(part,x,y-.045,.370,.020,.003,16)
 else:
  m.box('Mains' if ats else 'Input',-.46 if wall else -.50,-.43,back+.10,.16 if wall else .20,.06,.12)
  m.box('Output',.30,-.43,back+.10,.16 if wall else .20,.06,.12)
  if ats:m.box('Backup',-.075,-.47,back-.03,.15,.07,.07)
 m.save(name)
