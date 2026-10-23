#!/usr/bin/env python3
"""Original front-loading laundry pair and six-block industrial matter reactor. Bodies are stationary/baked."""
from generate_fans_and_outlet import Mesh, ring
from math import sin, cos, tau
from pathlib import Path
for name,washer in [('electricwasher',True),('electricdryer',False)]:
 m=Mesh()
 m.box('Case',-.46,-.45,-.43,.92,.91,.86)
 m.box('Trim',-.47,.39,-.45,.94,.07,.90)
 m.box('Black',-.43,.24,.431,.86,.13,.014)
 m.box('Display',-.13,.274,.447,.23,.052,.012)
 m.cyl('Trim',2,(.28,.302,.447),.025,.043,n=24)
 m.box('Case',-.40,.267,.448,.19,.058,.020)
 m.box('Trim',-.38,.29,.47,.13,.010,.012)
 ring(m,'Black',0,-.10,.434,.285,.345,.025,48)
 ring(m,'Trim',0,-.10,.459,.267,.325,.027,48)
 m.cyl('Glass',2,(0,-.10,.463),.018,.263,n=48)
 ring(m,'Trim',0,-.10,.483,.235,.267,.015,40)
 m.box('Black',.265,-.11,.491,.035,.115,.035)
 for x in [-.35,.35]:
  for z in [-.32,.32]:m.cyl('Black',1,(x,-.50,z),.05,.043,n=12)
 for i in range(10):m.box('Black',-.37+i*.076,-.36,.433,.044,.025,.007)
 if washer:
  for x in [-.24,.24]:m.cyl('Trim',2,(x,.19,-.46),.04,.036,n=16)
 else:
  m.cyl('Trim',2,(0,-.20,-.49),.07,.095,n=24)
 m.save(name)
# Native model orientation XN: six across Z, two deep X, two high Y.
# Root at local block (0,0,0); ghost occupancy is x=0..1,y=0..1,z=-2..3.
m=Mesh()
m.box('Black',-.50,-.50,-2.50,2,.16,6)
m.box('Case',-.46,-.34,-2.46,1.92,.22,5.92)
m.box('Trim',-.40,1.31,-2.40,1.80,.19,5.80)
# Long vacuum vessel and repeated copper magnet rings.
m.cyl('Black',2,(.5,.55,-2.27),5.54,.60,n=32)
for z in [-2.25,-1.5,-.75,0,.75,1.5,2.25,3.22]:
 ring(m,'Coils',.5,.55,z,.53,.76,.13,32)
for x in [-.42,1.30]:
 for z in [-2.4,3.2]:m.box('Case',x,-.12,z,.12,1.43,.18)
# Service cabinets on the front edge, inspection windows, pipes and console.
for z in [-2.35,2.55]:
 m.box('Case',-.48,-.10,z,.34,1.30,.80)
 for y in [.12,.24,.36,.48,.60]:m.box('Black',-.489,y,z+.12,.015,.035,.54)
m.box('Case',-.48,.68,-.46,.28,.55,.92)
m.box('Black',-.49,.74,-.39,.025,.39,.78)
m.box('Display',-.513,.81,-.33,.018,.25,.48)
for z in [-.27,-.07,.13,.33]:m.cyl('Trim',0,(-.535,.77,z),.024,.025,n=12)
for x in [-.25,1.20]:m.cyl('Trim',2,(x,1.19,-2.22),5.44,.045,n=12)
for z in [-1.1,.75]:m.box('Glass',-.27,.36,z,.026,.35,.48)
m.save('fusionmatterreactor')
# A heavier 240 V 30 A NEMA 6-30 connector, retaining the normal two-socket placement.
m=Mesh();m.box('Case',-.17,-.31,-.49,.34,.62,.035);m.box('Case',-.135,-.23,-.455,.27,.46,.09)
for y in [-.11,.07]:
 m.box('Black',-.065,y-.025,-.362,.022,.070,.005)
 m.box('Black',.035,y-.025,-.362,.022,.070,.005)
 m.cyl('Black',2,(0,y-.055,-.362),.005,.019,n=16)
for y in [-.27,.26]:m.cyl('Trim',2,(0,y,-.453),.006,.014,n=12)
m.save('dryeroutlet30')
# The plug is drawn centered at the existing socket anchor.
m=Mesh();m.box('Case',-.075,-.07,-.37,.15,.14,.11)
m.box('Grip',-.060,-.055,-.26,.12,.11,.05)
for x in [-.05,.04]:m.box('Blades',x,-.012,-.405,.01,.045,.035)
m.cyl('Blades',2,(0,-.045,-.405),.035,.012,n=10)
m.box('Badge',-.025,.015,-.208,.05,.025,.002);m.save('nema630plug')
# Wet carrier item uses a standard baked generated item model.
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln'
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.rounded_rectangle((6,8,25,27),radius=4,fill=(75,121,155),outline=(170,217,234),width=2)
d.line((9,14,23,14),fill=(210,235,246),width=2)
d.polygon([(16,1),(11,9),(12,12),(16,14),(20,12),(21,9)],fill=(65,175,232),outline=(190,236,255))
(root/'textures/items/wet_equipment.png').parent.mkdir(parents=True,exist_ok=True);im.save(root/'textures/items/wet_equipment.png')
(root/'models/item').mkdir(parents=True,exist_ok=True)
(root/'models/item/wet_equipment.json').write_text('{"parent":"minecraft:item/generated","textures":{"layer0":"eln:items/wet_equipment"}}\n')
