#!/usr/bin/env python3
"""Original cabinet geometry for the modular 51.2 V storage system; no external artwork."""
from pathlib import Path
from collections import defaultdict
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
class Mesh:
 def __init__(self):self.parts=defaultdict(list)
 def box(self,n,x,y,z,w,h,d):
  a=x-w/2;b=x+w/2;c=y;f=y+h;g=z-d/2;k=z+d/2
  for vs in [[(a,c,k),(b,c,k),(b,f,k),(a,f,k)],[(b,c,g),(a,c,g),(a,f,g),(b,f,g)],[(a,c,g),(a,c,k),(a,f,k),(a,f,g)],[(b,c,k),(b,c,g),(b,f,g),(b,f,k)],[(a,f,k),(b,f,k),(b,f,g),(a,f,g)],[(a,c,g),(b,c,g),(b,c,k),(a,c,k)]]:self.parts[n].append(vs)
 def save(self,name):
  out=ROOT/name;out.mkdir(parents=True,exist_ok=True);lines=['# Original Electrical Age storage cabinet','mtllib fixture.mtl'];idx=1
  for n,faces in self.parts.items():
   lines+=['o '+n,'usemtl Neutral']
   for vs in faces:
    lines+=['v %.6f %.6f %.6f'%v for v in vs];lines+=[f'f {idx} {idx+1} {idx+2}',f'f {idx} {idx+2} {idx+3}'];idx+=4
  (out/(name+'.obj')).write_text('\n'.join(lines)+'\n');(out/'fixture.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 0.2 0.2 0.2\n')
for name in ['lifepo_cabinet','lifepo_wall','offgrid_inverter','offgrid_mppt','offgrid_charger','pv_combiner','battery_disconnect']:
 m=Mesh();bank=name.startswith('lifepo');wall=name=='lifepo_wall';h=1.96 if bank else .92;depth=.32 if wall else .76;cz=-.34 if wall else 0;face=cz+depth/2
 m.box('Case',0,-.48,cz,.88,h,depth);m.box('Trim',0,-.49,cz,.91,.065,depth+.02)
 if bank:
  for i in range(4):
   y=-.30+i*.37;m.box('Trim',0,y,face+.009,.74,.025,.016)
   for s in [-1,1]:m.box('Trim',s*.32,y+.10,face+.019,.055,.15,.025)
  m.box('Trim',0,1.03,face+.01,.76,.36,.025)
  m.box('Capacity',.22,1.07,face+.028,.18,.055,.002)
 else:
  m.box('Trim',0,.07,face+.01,.74,.34,.025)
  for y in [-.32,-.26,-.20]:m.box('Vents',0,y,face+.01,.61,.018,.02)
 top=1.24 if bank else .28
 for tag,x,y in [('Voltage',-.18,top),('Charge',.18,top),('Power',-.18,top-.13)]:m.box(tag,x,y,face+.03,.27,.063,.002)
 for tag,x in [('StatusLed',-.28),('ChargeLed',0),('FaultLed',.28)]:m.box(tag,x,top-.24,face+.032,.042,.042,.005)
 if bank:
  m.box('Positive',-.455,-.23,cz,.04,.13,.16);m.box('Negative',.455,-.23,cz,.04,.13,.16)
 else:
  m.box('Positive' if name!='offgrid_mppt' else 'Solar',-.455,-.20,0,.04,.15,.17)
  m.box('Output',.455,-.20,0,.04,.15,.17);m.box('Negative',0,-.20,-.395,.18,.15,.04)
  m.box('Solar' if name=='pv_combiner' else 'Grid',0,-.20,.395,.18,.15,.04)
  if name in ['pv_combiner','battery_disconnect']:
   m.box('Trim',.27,-.06,face+.05,.10,.16,.065)
   if name=='pv_combiner':
    for x in [-.19,.03]:m.box('Case',x,-.24,face+.022,.10,.18,.04)
 m.save(name)
