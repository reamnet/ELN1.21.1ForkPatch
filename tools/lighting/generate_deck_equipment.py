#!/usr/bin/env python3
"""Original deck fixtures and electrical appliances; metres."""
from generate_fans_and_outlet import Mesh
from math import sin,cos,pi
m=Mesh();m.box('Base',0,-.24,-.24,.055,.48,.48);m.box('Metal',.055,-.23,-.23,.15,.46,.46)
for y in [-.22,.22]:
 for z in [-.22,.22]:m.box('Trim',.20,y-.012,z-.012,.10,.024,.024)
m.box('Light0',.21,-.19,-.19,.07,.38,.38)
# Four sloping roof faces.
top=(.34,0,0)
for a,b in [((.29,-.29,-.29),(.29,.29,-.29)),((.29,.29,-.29),(.29,.29,.29)),((.29,.29,.29),(.29,-.29,.29)),((.29,-.29,.29),(.29,-.29,-.29))]:m.poly('Metal',[a,b,top])
m.save('deckpostcaplight')
m=Mesh();m.box('Base',.005,-.10,-.25,.025,.20,.50);m.box('Metal',.03,-.095,-.245,.035,.19,.49)
m.box('Light0',.066,-.067,-.20,.006,.055,.40);m.box('Trim',.065,-.005,-.23,.04,.07,.46);m.save('decksteplight')
m=Mesh();m.cyl('Base',0,(.005,0,0),.07,.14,.10,n=20);m.cyl('Metal',0,(.075,0,0),.30,.022,n=12);m.cyl('Metal',0,(.36,0,0),.13,.14,.11,n=24)
for j in range(5):
 a=j*2*pi/5
 def pt(r,ang,x):return (x,r*cos(a+ang),r*sin(a+ang))
 v=[pt(.12,-.3,.43),pt(.56,-.30,.42),pt(.84,-.14,.45),pt(.92,.08,.49),pt(.76,.25,.48),pt(.24,.22,.46)]
 back=[(x+.025,y,z) for x,y,z in v];m.poly('Rotor',list(reversed(v)));m.poly('Rotor',back)
 for i in range(len(v)):m.poly('Rotor',[v[i],v[(i+1)%len(v)],back[(i+1)%len(v)],back[i]])
m.cyl('Trim',0,(.49,0,0),.05,.12,n=20);m.cyl('Light0',0,(.54,0,0),.10,.115,.07,n=24);m.save('patiotropicalfan')
m=Mesh();m.box('Case',-.39,.14,-.25,.78,.23,.50);m.box('Black',-.37,.37,-.23,.74,.025,.46)
for x in [-.28,.28]:
 for z in [-.18,.18]:m.box('Trim',x-.018,-.48,z-.018,.036,.62,.036)
m.box('Trim',-.34,-.17,-.20,.68,.025,.40)
for i in range(12):m.box('Trim',-.33+i*.058,.399,-.21,.012,.015,.42)
for z in [-.1,.1]:m.box('Heat',-.31,.395,z-.012,.62,.004,.024)
for x in [-.22,0,.22]:m.cyl('Black',2,(x,.26,.25),.035,.026,n=12)
m.box('Case',.39,.20,-.23,.23,.03,.46);m.box('Display',-.06,.19,.252,.12,.035,.004);m.save('deckelectricgrill')
m=Mesh();m.cyl('Black',1,(0,-.5,0),.10,.28,.24,n=24);m.cyl('Trim',1,(0,-.40,0),1.20,.035,n=16)
m.cyl('Case',1,(0,.78,0),.40,.18,.18,n=24)
for i in range(12):
 a=i*pi/6;m.cyl('Trim',1,(.19*cos(a),.76,.19*sin(a)),.45,.009,n=6)
m.cyl('Heat',1,(0,.82,0),.33,.12,n=20);m.cyl('Case',1,(0,1.18,0),.09,.33,.10,n=24)
m.cyl('Black',1,(0,.73,0),.06,.21,n=20);m.box('Display',-.025,-.25,.041,.05,.025,.004);m.save('deckpatioheater')
