#!/usr/bin/env python3
"""Regenerate original fan/outlet OBJ meshes and cord icon (Python 3 + Pillow)."""
from pathlib import Path
from math import *
from pathlib import Path
from math import sin,cos,pi
from collections import defaultdict
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
class Mesh:
 def __init__(self):self.p=defaultdict(list)
 def poly(self,p,v):self.p[p].append(v)
 def box(self,p,x,y,z,a,b,c):
  v=[(x+i*a,y+j*b,z+k*c) for i,j,k in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]]
  for ids in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:self.poly(p,[v[i] for i in ids])
 def cyl(self,p,axis,center,length,r0,r1=None,n=24):
  r1=r0 if r1 is None else r1
  def v(t,r,a):
   q=[0.,0.,0.];q[axis]=t;q[(axis+1)%3]=r*cos(a);q[(axis+2)%3]=r*sin(a)
   return tuple(q[i]+center[i] for i in range(3))
  for i in range(n):
   a=i*2*pi/n;b=(i+1)*2*pi/n
   self.poly(p,[v(0,0,0),v(0,r0,b),v(0,r0,a)])
   self.poly(p,[v(length,0,0),v(length,r1,a),v(length,r1,b)])
   self.poly(p,[v(0,r0,a),v(0,r0,b),v(length,r1,b),v(length,r1,a)])
 def save(self,name):
  d=root/name;d.mkdir(exist_ok=True)
  (d/'fixture.mtl').write_text('newmtl fixture\nKd 1 1 1\nKa 1 1 1\nd 1\n')
  lines=['# Electrical Age fixture; metres in local coordinates','mtllib fixture.mtl'];idx=1
  for p,faces in self.p.items():
   lines+=['o '+p,'usemtl fixture']
   for polygon in faces:
    for j in range(1,len(polygon)-1):
     face=[polygon[0],polygon[j],polygon[j+1]]
     lines += ['v %.6f %.6f %.6f'%v for v in face]
     lines += ['f '+' '.join(str(idx+i) for i in range(3))];idx+=3
  (d/(name+'.obj')).write_text('\n'.join(lines)+'\n')

def ring(m,part,cx,cy,z,r0,r1,depth,n=64):
 for i in range(n):
  a=i*tau/n;b=(i+1)*tau/n
  def v(r,t,d):return(cx+r*cos(t),cy+r*sin(t),z+d)
  for q in [[v(r0,a,0),v(r0,b,0),v(r1,b,0),v(r1,a,0)],
            [v(r0,a,depth),v(r1,a,depth),v(r1,b,depth),v(r0,b,depth)],
            [v(r1,a,0),v(r1,b,0),v(r1,b,depth),v(r1,a,depth)],
            [v(r0,b,0),v(r0,a,0),v(r0,a,depth),v(r0,b,depth)]]:m.poly(part,q)
def blades(m,cy,r,n):
 m.cyl('Rotor',2,(0,cy,-.03),.12,r*.16,n=32)
 for j in range(n):
  a=j*tau/n
  def p(radius,angle,z):return(radius*cos(a+angle),cy+radius*sin(a+angle),z)
  verts=[p(r*.14,-.35,-.025),p(r*.96,-.03,-.025),p(r,.30,-.025),p(r*.22,.48,-.025)]
  back=[(x,y,z+.045) for x,y,z in verts]
  m.poly('Rotor',list(reversed(verts)));m.poly('Rotor',back)
  for k in range(4):m.poly('Rotor',[verts[k],verts[(k+1)%4],back[(k+1)%4],back[k]])
for name,compact,shop in [('classicboxfan',False,False),('metalboxfan',False,True),('compactboxfan',True,False)]:
 m=Mesh();s=.64 if compact else 1;cy=-.18 if compact else -.02;r=.22 if compact else .39
 w=.46*s;h=.46*s
 for x in [-w,w-.045*s]:m.box('Case',x,cy-h,-.15*s,.045*s,2*h,.30*s)
 for y in [cy-h,cy+h-.045*s]:m.box('Case',-w,y,-.15*s,2*w,.045*s,.30*s)
 m.box('Black',-w*.8,-.50,-.18*s,w*1.6,.07,.36*s)
 ring(m,'Trim',0,cy,-.145*s,r,r+.018*s,.02*s,48)
 blades(m,cy,r*.94,3 if shop else 4 if compact else 5)
 for z in [-.153*s,.153*s]:
  for k in range(-9,10):
   y=cy+k*r/10;half=sqrt(max(0,r*r-(y-cy)**2))
   m.box('Trim',-half,y-.0035*s,z,half*2,.007*s,.007*s)
  for k in range(-5,6):
   x=k*r/6;half=sqrt(max(0,r*r-x*x))
   m.box('Trim',x-.0035*s,cy-half,z,.007*s,half*2,.007*s)
 m.cyl('Black',2,(0,cy,.156*s),.015*s,.07*s,n=24)
 m.box('Case',-.13*s,cy+h,.0,.26*s,.04*s,.06*s)
 m.cyl('Black',1,(w-.09*s,cy+h,.0),.025*s,.022*s,n=12)
 if shop:
  for x in [-.38,.30]:m.box('Black',x,-.50,-.22,.08,.12,.44)
 m.save(name)
m=Mesh();cy=1.9;r=2.28
ring(m,'Case',0,cy,-.16,r,r+.17,.32,96)
for x in [-2.42,2.34]:m.box('Case',x,-.49,-.17,.08,4.78,.34)
for y in [-.49,4.21]:m.box('Case',-2.42,y,-.17,4.84,.08,.34)
m.box('Black',-.42,-.50,-.32,.84,.15,.64)
m.box('Black',-.11,-.35,-.19,.22,2.25,.38)
for y in [1.8,2.0]:m.box('Black',-2.28,y,-.17,4.56,.06,.06)
m.cyl('Black',2,(0,cy,-.25),.20,.30,n=32)
blades(m,cy,2.22,6)
for i in range(12):m.box('Black',-2.42+i*.4,4.24,.171,.18,.045,.002)
m.save('industrialfloorfan')
m=Mesh()
m.box('Case',-.17,-.31,-.49,.34,.62,.035)
m.box('Case',-.135,-.23,-.455,.27,.46,.09)
for y in [-.11,.07]:
 for x in [-.055,.035]:m.box('Black',x,y,-.362,.020,.065,.005)
 m.cyl('Black',2,(0,y-.045,-.362),.005,.018,n=16)
for y in [-.27,.26]:m.cyl('Trim',2,(0,y,-.453),.006,.014,n=12)
m.box('Display',.08,-.19,-.36,.024,.025,.005)
m.save('appliancewalloutlet')
# A coiled cord item with a moulded plug and two brass prongs.
from PIL import Image,ImageDraw
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
for rad in [11,7,3]:
 d.ellipse((15-rad,17-rad,15+rad,17+rad),outline=(25,29,33,255),width=3)
 d.arc((15-rad,17-rad,15+rad,17+rad),200,300,fill=(80,85,89,255),width=1)
d.line([(26,17),(29,13),(26,6),(22,6)],fill=(30,34,37,255),width=3)
d.rounded_rectangle((16,2,24,9),radius=2,fill=(48,51,53,255),outline=(100,105,108,255))
d.rectangle((17,0,18,2),fill=(192,151,70,255));d.rectangle((22,0,23,2),fill=(192,151,70,255))
im.save(root.parent/'textures/items/flexibleappliancecord.png')
