#!/usr/bin/env python3
"""Original galvanized lattice poles, cable risers and three-phase substation equipment."""
from generate_fans_and_outlet import Mesh
from math import *
def tube(m,part,a,b,r=.045,n=8):
 d=[b[i]-a[i] for i in range(3)];length=sqrt(sum(v*v for v in d));d=[v/length for v in d]
 def cross(a,b):return(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
 u=cross(d,(0,1,0) if abs(d[1])<.9 else (1,0,0));l=sqrt(sum(v*v for v in u));u=[v/l for v in u];v=cross(d,u)
 rings=[[tuple(p[j]+r*(u[j]*cos(tau*i/n)+v[j]*sin(tau*i/n)) for j in range(3)) for i in range(n)] for p in [a,b]]
 for i in range(n):k=(i+1)%n;m.poly(part,[rings[0][i],rings[0][k],rings[1][k],rings[1][i]])
 m.poly(part,list(reversed(rings[0])));m.poly(part,rings[1])
def insulator(m,x,y,z,length):
 m.cyl('Porcelain',1,(x,y,z),length,.065,n=12)
 for i in range(max(4,int(length/.11))):m.cyl('Porcelain',1,(x,y+i*.10,z),.035,.15,n=16)
for voltage,height,spread in [(69,16,2.7),(230,24,4.8)]:
 for riser in [False,True]:
  m=Mesh();width=1.0 if voltage==69 else 1.7
  def w(y):return width*(1-y/height)*.8+.20
  for x in [-1,1]:
   for z in [-1,1]:
    tube(m,'main',(x*w(0),-.45,z*w(0)),(x*.20,height-.30,z*.20),.085 if voltage==230 else .065)
    m.box('main',x*w(0)-.17,-.49,z*w(0)-.17,.34,.12,.34)
  for y in range(0,height-1,2):
   hi=min(height-.3,y+2)
   for axis in [0,2]:
    for side in [-1,1]:
     def p(a,h):return(a*w(h),h,side*w(h)) if axis==0 else(side*w(h),h,a*w(h))
     tube(m,'main',p(-1,y),p(1,hi),.025);tube(m,'main',p(1,y),p(-1,hi),.025)
   for side in [-1,1]:tube(m,'main',(-w(y),y,side*w(y)),(w(y),y,side*w(y)),.035)
  y=height-1.55
  tube(m,'main',(0,y,-spread-.25),(0,y,spread+.25),.10)
  for z in [-spread,spread]:tube(m,'main',(0,y-1,0),(0,y,z),.045)
  for z in [-spread,0,spread]:insulator(m,0,y,z,.80);tube(m,'Bus',(0,y+.8,z-.12),(0,y+.8,z+.12),.026)
  if riser:
   for phase,z in enumerate([-spread,0,spread]):
    basez=(phase-1)*.30
    tube(m,'Bus',(.35,-.30,basez),(.35,height-2.4,basez),.058)
    tube(m,'Bus',(.35,height-2.4,basez),(0,height-.75,z),.035)
    m.box('main',.2,-.30,basez-.10,.25,.45,.20)
  m.save(('steelriser' if riser else 'steeltransmission')+str(voltage))
for voltage,height,spread in [(69,5,1.8),(230,7,2.7)]:
 for transformer in [True,False]:
  h=height if transformer else height-1
  m=Mesh();m.box('main',-3,-.49,-spread-.45,6,.15,2*spread+.90)
  for x in [-2.5,2.5]:
   tube(m,'main',(x,-.34,-spread-.18),(x,h-1.60,-spread-.18),.095)
   tube(m,'main',(x,-.34,spread+.18),(x,h-1.60,spread+.18),.095)
   tube(m,'main',(x,h-1.60,-spread-.30),(x,h-1.60,spread+.30),.09)
  for z in [-spread,0,spread]:
   for x in [-2.5,2.5]:insulator(m,x,h-1.60,z,.95)
   for side in [-1,1]:
    tube(m,'Bus',(side*2.5,h-.65,z),(side*2.3,h-.65,z),.045)
    tube(m,'KnifeIn' if side<0 else 'KnifeOut',(side*1.5,h-1.1,z),(side*2.3,h-.65,z),.035)
    tube(m,'main',(side*1.5,.0,z),(side*1.5,h-1.65,z),.055)
    insulator(m,side*1.5,h-1.65,z,.55)
  if transformer:
   bodyheight=2.65 if voltage==69 else 4.35
   m.box('Transformer',-.90,-.25,-spread-.10,1.8,bodyheight,2*spread+.20)
   for x in [-1.02,.92]:
    for z in [(-spread-.05)+i*.20 for i in range(int((2*spread+.10)/.20))]:m.box('Transformer',x,.0,z,.10,bodyheight-.25,.035)
   for z in [-spread,0,spread]:
    for side in [-1,1]:
     insulator(m,side*.70,bodyheight-.25,z,.65)
     tube(m,'Bus',(side*.70,bodyheight+.40,z),(side*1.5,h-1.1,z),.035)
  else:
   for z in [-spread,0,spread]:tube(m,'Bus',(-1.5,h-1.1,z),(1.5,h-1.1,z),.035)
  # Each bank is a gang: the common shaft visibly links all three knife hinges.
  for x in [-1.5,1.5]:tube(m,'main',(x,h-1.25,-spread-.15),(x,h-1.25,spread+.15),.018)
  name=('substation69125' if voltage==69 else 'substation23069') if transformer else 'substationdisconnect'+str(voltage)
  m.save(name)
