#!/usr/bin/env python3
"""Original low-poly EVs and charging pedestal. Deterministic, no external artwork."""
from pathlib import Path
from collections import defaultdict
from math import sin,cos,pi
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
class Mesh:
 def __init__(self):self.parts=defaultdict(list)
 def quad(self,n,vs):self.parts[n].append(vs)
 def box(self,n,x,y,z,w,h,d):
  a=x-w/2;b=x+w/2;c=y;f=y+h;g=z-d/2;k=z+d/2
  for vs in [[(a,c,k),(b,c,k),(b,f,k),(a,f,k)],[(b,c,g),(a,c,g),(a,f,g),(b,f,g)],[(a,c,g),(a,c,k),(a,f,k),(a,f,g)],[(b,c,k),(b,c,g),(b,f,g),(b,f,k)],[(a,f,k),(b,f,k),(b,f,g),(a,f,g)],[(a,c,g),(b,c,g),(b,c,k),(a,c,k)]]:self.quad(n,vs)
 def wheel(self,i):
  for n,r,d in [(f'Wheel{i}',.29,.22),(f'Rim{i}',.17,.235)]:
   for j in range(16):
    a=j*pi/8;b=(j+1)*pi/8
    self.quad(n,[(-d/2,r*cos(a),r*sin(a)),(d/2,r*cos(a),r*sin(a)),(d/2,r*cos(b),r*sin(b)),(-d/2,r*cos(b),r*sin(b))])
    for x,sgn in [(-d/2,-1),(d/2,1)]:
     v=[(x,0,0),(x,r*cos(a),r*sin(a)),(x,r*cos(b),r*sin(b))];self.quad(n,v if sgn>0 else v[::-1])
 def write(self,name):
  out=ROOT/name;out.mkdir(exist_ok=True)
  lines=['# Original Electrical Age vehicle geometry','mtllib fixture.mtl'];idx=1
  for n,faces in self.parts.items():
   lines+=['o '+n,'usemtl Neutral']
   for vs in faces:
    lines += ['v %.6f %.6f %.6f'%(v[0],v[1]-.5 if name=='vehicle_charger' else v[1],v[2]) for v in vs]
    lines += [f'f {idx} {idx+1} {idx+2}']
    if len(vs)==4:lines += [f'f {idx} {idx+2} {idx+3}']
    idx+=len(vs)
  (out/(name+'.obj')).write_text('\n'.join(lines)+'\n');(out/'fixture.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 0.2 0.2 0.2\n')
for cart,name in [(True,'electric_golf_cart'),(False,'arc_runner_coupe')]:
 m=Mesh();m.box('Trim',0,.20,0,1.50 if cart else 1.70,.13,2.65 if cart else 4.0);m.box('Body',0,.33,0,1.52 if cart else 1.72,.28,2.65 if cart else 4.0)
 for x in [-.39,.39]:
  m.box('Seats',x,.61,.42 if cart else -.15,.65,.14,.55);m.box('Seats',x,.73,.12 if cart else -.45,.65,.58,.12)
  if cart:m.box('Seats',x,.61,-.68,.65,.14,.55);m.box('Seats',x,.73,-.95,.65,.55,.12)
 if cart:
  for x in [-.68,.68]:
   for z in [-1.05,1.03]:m.box('Frame',x,.6,z,.055,1.5,.055)
  m.box('Roof',0,2.06,0,1.63,.09,2.45);m.box('Glass',0,1.21,1.04,1.25,.67,.035)
  m.box('Body',0,.60,1.12,1.50,.35,.28);m.box('Trim',0,.62,-1.23,1.25,.2,.27)
 else:
  m.quad('Body',[(-.86,.61,2.0),(.86,.61,2.0),(.81,.95,1.25),(-.81,.95,1.25)])
  m.quad('Body',[(-.86,.61,-2.0),(-.81,.95,-1.55),(.81,.95,-1.55),(.86,.61,-2.0)])
  m.quad('Glass',[(-.78,.95,1.26),(.78,.95,1.26),(.67,1.68,.28),(-.67,1.68,.28)])
  m.quad('Glass',[(.78,.95,-1.56),(-.78,.95,-1.56),(-.67,1.68,-.65),(.67,1.68,-.65)])
  for x,sgn in [(-.83,-1),(.83,1)]:
   m.box('Body',x,.61,-.10,.055,.34,2.90)
   m.quad('Glass',[(x,.95,1.24),(x,.95,-1.54),(sgn*.67,1.68,-.65),(sgn*.67,1.68,.28)])
   if sgn<0:m.parts['Glass'][-1].reverse()
   m.quad('Frame',[(x,.95,1.27),(x,.95,1.22),(sgn*.67,1.71,.28),(sgn*.67,1.71,.33)])
   m.quad('Frame',[(x,.95,-1.57),(x,.95,-1.52),(sgn*.67,1.71,-.63),(sgn*.67,1.71,-.68)])
   if sgn<0:
    for face in m.parts['Frame'][-2:]:face.reverse()
   m.box('Trim',x+sgn*.018,.89,-.16,.015,.025,.19)
  m.box('Roof',0,1.68,-.16,1.36,.055,1.0)
  m.box('Body',0,.61,1.88,1.72,.13,.24)
  m.box('Body',0,.61,-1.85,1.72,.18,.30)
 for x in [-.54,.54]:
  m.box('Headlights',x,.69,1.335 if cart else 2.005,.33,.10,.025);m.box('TailLights',x,.68,-1.34 if cart else -2.005,.33,.08,.025)
 m.box('Trim',0,.80,.81,1.37,.08,.25)
 m.box('BatteryDisplay',0,.87,.86,.23,.075,.004)
 m.box('ChargeLamp',-.62,.71,-1.34 if cart else -2.005,.055,.04,.014)
 for i in range(4):m.wheel(i)
 m.write(name)
m=Mesh();m.box('Trim',0,0,0,.64,.13,.55);m.box('Body',0,.13,0,.44,1.5,.30);m.box('Trim',0,.97,.16,.34,.52,.035)
m.box('Voltage',0,1.30,.183,.24,.066,.002);m.box('Power',0,1.12,.183,.24,.066,.002);m.box('Indicator',.12,1.49,.17,.035,.035,.02)
m.box('Socket',0,.72,.19,.13,.17,.08)
for x,y,z,w,h,d in [(.28,.33,0,.05,.86,.05),(.20,.31,0,.20,.05,.05),(.20,1.13,0,.20,.05,.05)]:m.box('Lead',x,y,z,w,h,d)
m.write('vehicle_charger')
