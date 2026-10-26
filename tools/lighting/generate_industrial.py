#!/usr/bin/env python3
"""Original industrial cabinets, load rack, welding bench and electric forklift."""
from generate_fans_and_outlet import Mesh
for kind in ['load_bank','scada','relay','arrester','pad','drive','shedder','charger','welder','jukebox']:
 m=Mesh()
 if kind=='jukebox':
  m.box('Base',-.42,-.49,-.32,.84,.07,.64)
  m.box('Case',-.39,-.42,-.28,.78,.84,.56)
  m.box('Trim',-.33,-.34,-.297,.66,.48,.016)
  for x in [-.19,.19]:
   m.cyl('Trim',2,(x,-.11,-.32),.015,.13,n=24)
   m.cyl('Terminal',2,(x,-.11,-.338),.012,.038,n=16)
  m.box('Screen',-.28,.21,-.30,.42,.12,.025)
  m.cyl('Terminal',2,(.24,.27,-.317),.035,.05,n=20)
  for i in range(7):m.box('Red',-.28+i*.085,.12,-.32,.04,.035,.02)
 elif kind=='arrester':
  m.box('Base',-.2,-.49,-.2,.4,.07,.4);m.cyl('Terminal',1,(0,-.42,0),.75,.027,n=12)
  for y in range(8):m.cyl('Ceramic',1,(0,-.34+y*.075,0),.045,.12,.06,n=20)
  m.cyl('Terminal',1,(0,.26,0),.17,.045,n=12)
 elif kind=='welder':
  for x in [-.4,.34]:
   for z in [-.32,.26]:m.box('Base',x,-.49,z,.06,.64,.06)
  m.box('Case',-.46,.12,-.38,.92,.07,.76);m.box('Case',-.34,-.40,-.29,.38,.35,.56)
  m.box('Trim',.1,.19,-.25,.07,.16,.1);m.box('Terminal',.1,.34,-.25,.30,.05,.1)
  m.box('Red',-.28,-.33,-.31,.12,.12,.03)
 else:
  m.box('Base',-.44,-.49,-.38,.88,.07,.76)
  if kind=='load_bank':
   m.box('Case',-.4,-.42,-.33,.8,.85,.66)
   for y in range(10):m.box('Trim',-.35,-.35+y*.07,-.342,.70,.03,.02)
   for x in [-.2,.2]:
    m.cyl('Trim',2,(x,.22,.337),.015,.14,n=24)
    for i in range(3):m.box('Terminal',x-.10,.15+i*.06,.35,.20,.025,.015)
  elif kind=='pad':
   m.box('Case',-.43,-.42,-.35,.86,.77,.68)
   m.box('Door',-.40,-.38,-.377,.80,.67,.025)
   for x in [-.36,.33]:m.box('Trim',x,-.34,-.38,.03,.54,.035)
   for y in range(8):m.box('Trim',.434,-.30+y*.065,-.23,.014,.025,.44)
  elif kind=='scada':
   m.box('Case',-.42,-.42,-.32,.84,.55,.64);m.box('Screen',-.34,-.10,-.34,.68,.18,.025)
   m.box('Trim',-.36,-.32,-.35,.72,.15,.04)
   for i in range(10):m.box('Terminal',-.3+i*.065,-.27,-.393,.04,.035,.01)
  else:
   m.box('Case',-.32,-.42,-.23,.64,.83,.46);m.box('Screen',-.23,.13,-.247,.46,.19,.025)
   for y in range(6):m.box('Trim',-.26,-.28+y*.05,-.25,.52,.018,.025)
   if kind=='charger':m.box('Red',.34,-.14,-.08,.05,.24,.16)
   if kind=='drive':
    for x in range(9):m.box('Trim',-.3+x*.07,-.32,.24,.025,.60,.08)
 m.box('Input',-.47,-.39,-.08,.05,.15,.16);m.box('Output',.42,-.39,-.08,.05,.15,.16)
 m.box('Indicator',-.02,.36,-.25,.04,.035,.025)
 m.save('industrial_'+kind)
m=Mesh()
m.box('Body',-.52,.24,-.90,1.04,.42,1.65);m.box('Body',-.5,.65,-.83,1,.28,.58)
m.box('Black',-.30,.67,-.15,.60,.17,.43);m.box('Black',-.30,.80,-.17,.60,.42,.10)
for x in [-.56,.42]:
 for z in [-.53,.53]:m.cyl('Wheel',0,(x,.24,z),.14,.24,n=20)
for x in [-.45,.40]:
 m.box('Mast',x,.30,.77,.05,1.65,.07);m.box('Trim',x,.9,-.72,.05,1.12,.06)
m.box('Body',-.53,1.96,-.82,1.06,.07,1.62)
m.box('Fork',-.46,.20,.84,.92,.13,.10)
for x in [-.36,.25]:m.box('Fork',x,.18,.91,.11,.055,.78)
m.box('Trim',-.25,.98,.37,.05,.31,.05);m.cyl('Trim',1,(-.23,1.29,.4),.025,.17,n=20)
m.box('Lamp',-.05,2.04,-.05,.1,.08,.1);m.save('industrial_forklift')
m=Mesh()
for x in [-.45,0,.36]:m.box('Wood',x,0,-.45,.09,.12,.9)
for z in [-.45,-.23,-.01,.21,.43]:m.box('Wood',-.48,.12,z,.96,.065,.085)
m.box('Cargo',-.43,.19,-.42,.86,.62,.84);m.save('industrial_pallet')
# Static shared-item icons use ordinary baked generated-item quads.
from pathlib import Path
from PIL import Image, ImageDraw
icons=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/textures/items'
for name in ['electricforklift','warehouseequipmentpallet','weldedmachineframe']:
 im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 if name=='electricforklift':
  d.rectangle((17,25,44,46),fill='#e8ad24',outline='#8f6015',width=2)
  d.rectangle((17,9,45,14),fill='#f3c14c',outline='#a46f13',width=2)
  d.rectangle((19,14,22,29),fill='#586064');d.rectangle((40,14,43,30),fill='#586064')
  d.rectangle((25,24,35,31),fill='#30383b');d.rectangle((28,20,34,27),fill='#30383b')
  d.rectangle((45,18,49,51),fill='#555e64');d.rectangle((48,48,61,52),fill='#939da2')
  for x in [18,38]:d.ellipse((x-6,40,x+6,54),fill='#24282c',outline='#545c60',width=2)
  d.rectangle((20,16,39,18),fill='#ebf0ed')
 elif name=='warehouseequipmentpallet':
  d.polygon([(7,41),(40,28),(59,37),(25,52)],fill='#95622b',outline='#50371d')
  for y in [43,48]:d.line((10,y,25,y+8,57,y-6),fill='#c79758',width=3)
  d.polygon([(13,19),(41,10),(54,19),(27,29)],fill='#b0b7b8',outline='#515d61')
  d.polygon([(13,19),(27,29),(27,46),(13,37)],fill='#6b777a',outline='#515d61')
  d.polygon([(27,29),(54,19),(54,36),(27,46)],fill='#89979b',outline='#515d61')
 else:
  for x,y in [(12,23),(37,12),(51,25),(26,36)]:d.rectangle((x-3,y,x+3,y+22),fill='#78878d',outline='#c9d4d7')
  for q in [[(12,23),(37,12),(51,25),(26,36),(12,23)],[(12,45),(37,34),(51,47),(26,58),(12,45)]]:d.line(q,fill='#a5b3b8',width=5);d.line(q,fill='#d5dfe1',width=1)
 im.save(icons/(name+'.png'))
