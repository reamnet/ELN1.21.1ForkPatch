#!/usr/bin/env python3
"""Extract one unchanged pole insulator, rotate its axis and add a mounting shoe/feed."""
from pathlib import Path
from generate_fans_and_outlet import Mesh
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
def faces(path,part):
 vertices=[];out=[];current=''
 for line in path.read_text().splitlines():
  if line.startswith('v '):vertices.append(tuple(map(float,line.split()[1:4])))
  elif line.startswith('o '):current=line[2:]
  elif line.startswith('f ') and current==part:out.append([vertices[int(i.split('/')[0])-1] for i in line.split()[1:]])
 return out
for volts,length,radius in [(800,.20,.065),(3200,.252,.081),(12500,.28,.09),(69000,.8,.15),(230000,2,.375)]:
 m=Mesh();m.box('main',-.50,-radius-.025,-radius-.025,.035,2*radius+.05,2*radius+.05)
 if volts in [69000,230000]:
  source=faces(ROOT/('steeltransmission'+str(volts//1000))/('steeltransmission'+str(volts//1000)+'.obj'),'Porcelain')
  source=[face for face in source if all(abs(p[2])<1 for p in face)]
  base=min(p[1] for face in source for p in face)
  for face in source:m.poly('Porcelain',[(y-base-.5,-x,z) for x,y,z in face])
 elif volts in [3200,12500]:
  source=faces(ROOT/'distributionpole/distributionpole.obj','Pin0');base=min(p[1] for f in source for p in f)
  scale=.9 if volts==3200 else 1.0
  for face in source:m.poly('Porcelain',[((y-base)*scale-.5,-x*scale,(z+.95)*scale) for x,y,z in face])
 else:
  m.cyl('Porcelain',0,(-.5,0,0),.20,.025,n=16)
  for x in [-.445,-.365]:m.cyl('Porcelain',0,(x,0,0),.024,.065,n=20)
 m.cyl('Clamp',2,(-.5+length,0,-.035),.07,.027,n=12)
 m.box('CableFeed',-.495,-.045,-.045,.075,.09,.09)
 m.save('lineinsulator'+str(volts))
 print(volts,'length',round(length,6))
