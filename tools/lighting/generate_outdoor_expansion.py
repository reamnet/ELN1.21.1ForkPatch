#!/usr/bin/env python3
"""Original pier/path/stone fixtures, larger standby cabinets and service masts."""
from pathlib import Path
from math import *
from generate_fans_and_outlet import Mesh as Cabinet
source=Path(__file__).with_name('build_fixture_models.py');scope={'__file__':str(source)}
exec(compile(source.read_text().split('\nm=Mesh();',1)[0],str(source),'exec'),scope)
Mesh=scope['Mesh']
# Lamp +X is the mounting normal, including the height of upright floor fittings.
for i,name in enumerate(['nauticalpierbollard','marinepierbulkhead','harborgooseneck','pierlanternpost','lowprofiledocklight','louveredpathbollard','shieldedpathlight','modernbladewalklight','mushroompathlight','archedwalkwaylight','recessedbrickwalllight','roundstonewalllight','slottedstonewalllight','squarestonewalllight','stonewallwashlight']):
 m=Mesh();m.part('Base')
 if i in [0,3,5,6,7,8,9]:
  height=[1.1,0,0,2.4,0,.9,.85,1.2,.75,1.1][i]
  m.cone(0,.06,.18,.18);m.part('Metal')
  if i==7:
   m.box((.06,-.06,-.18),(1.12,.06,.18));m.box((1.04,-.06,-.18),(1.13,.15,.18))
  elif i==9:
   m.tube((.05,0,0),(.88,0,0),.035);m.tube((.88,0,0),(.93,.28,0),.035)
  else:m.cone(.06,height-.18,.07,.055)
  top=height-.16
  if i==3:
   m.cone(top-.34,top-.30,.20,.20);m.cone(top+.04,top+.08,.20,.20);m.cone(top+.08,top+.24,.25,.02)
   for a in range(4):m.tube((top-.34,.18*cos(a*pi/2),.18*sin(a*pi/2)),(top+.07,.18*cos(a*pi/2),.18*sin(a*pi/2)),.018)
  elif i==8:m.cone(top,top+.12,.32,.08)
  elif i==6:m.box((top,-.07,-.28),(top+.10,.30,.28))
  elif i in [0,5]:m.cone(top-.16,top-.14,.20,.20);m.cone(top+.04,top+.08,.20,.20)
  elif i!=7:m.cone(top-.12,top+.06,.20,.20)
  if i in [0,5]:
   m.part('Trim')
   for k in range(4):m.cone(top-.13+k*.045,top-.11+k*.045,.21,.21)
  m.part('Light0');m.cone(top-.14,top-.02,.16,.16)
  if i==7:m.box((.84,.061,-.14),(1.02,.09,.14))
 elif i in [1,2,4]:
  m.box((0,-.18,-.24),(.04,.18,.24));m.part('Metal')
  if i==2:
   m.tube((.04,0,0),(.52,.10,0),.025);m.tube((.52,.10,0),(.60,-.12,0),.025);m.cone(.48,.64,.20,.12,y=-.12)
   m.part('Light0');m.cone(.64,.66,.14,.14,y=-.12)
  elif i==1:
   m.cone(.03,.15,.23,.23);m.part('Light0');m.cone(.15,.18,.19,.19)
   m.part('Trim')
   for z in [-.12,0,.12]:m.tube((.19,-.17,z),(.19,.17,z),.009)
  else:
   m.box((.04,-.10,-.32),(.10,.10,.32));m.part('Light0');m.box((.105,-.055,-.27),(.12,.055,.27))
 else:
  # Shallow recessed frames project only centimetres from the stone face.
  w=.40 if i in [10,12,14] else .24;h=.14 if i==10 else .055 if i==12 else .24
  if i==11:m.cone(.0,.04,.25,.25);m.part('Metal');m.cone(.035,.045,.225,.225);m.part('Light0');m.cone(.045,.055,.20,.20)
  else:
   m.box((0,-h-.025,-w-.025),(.035,h+.025,w+.025));m.part('Metal')
   m.box((.035,-h,-w),(.065,h,w));m.part('Light0');m.box((.066,-h+.02,-w+.025),(.070,h-.02,w-.025))
   if i in [12,14]:
    m.part('Trim')
    for k in range(4):m.box((.072,-h+.02+k*h*.4,-w+.015),(.08,-h+.03+k*h*.4,w-.015))
 m.save(name)
for name,w,h,d in [('medium',2,1,1),('large',2,2,1),('extra_large',3,2,2),('extreme',5,3,3)]:
 m=Cabinet();left=-(w//2)-.46;right=(w-1)//2+.46;back=-.46;deep=d-.54
 m.box('Case',left,-.39,back,right-left,h-.18,deep-back)
 m.box('Trim',left-.02,-.49,back-.02,right-left+.04,.10,deep-back+.04)
 for panel in range(w):
  x=left+.06+panel
  m.box('Trim',x,-.30,deep+.005,.018,h-.45,.018)
  for j in range(7):m.box('Vents',x+.05,-.15+j*(h-.4)/8,deep+.012,.72,.022,.015)
 m.box('Trim',left,h-.68,back,.08,.10,deep-back)
 m.box('Display',-.18,.15,deep+.025,.30,.14,.018)
 m.box('Indicator',.16,.18,deep+.026,.06,.035,.019)
 for x in [-.42,.34]:m.box('Output',x,-.30,back-.02,.08,.10,.06)
 pipex=(w-1)//2+.28;pipez=(d-1)/2+.30
 m.cyl('Trim',1,(pipex,h-.655,pipez),.155,.055,n=12)
 m.cyl('Vents',1,(pipex,h-.50,pipez),.012,.046,n=12)
 m.save('standbygenerator_'+name)
for name,height,offset in [('compactservicemast',.18,.58),('roofservicemast',2.22,.36),('gooseneckservicemast',1.42,.62)]:
 m=Cabinet();m.box('Case',-.20,-.43,-.49,.40,.33,.15)
 # These meshes face +Z. Their pipe stays close to the supporting wall.
 m.cyl('Pipe',1,(0,-.26,-.36),height+.20,.055,n=16)
 if name=='compactservicemast':m.box('Pipe',0,.07,-.40,.45,.08,.08)
 if name=='gooseneckservicemast':m.box('Pipe',0,height-.10,-.40,.40,.08,.08)
 x=.40 if name!='roofservicemast' else 0
 m.box('Hood',x-.14,height-.10,-.46,.28,.18,.25)
 for i in range(3):m.cyl('Terminal'+str(i),2,(x+(i-1)*.09,height-.05,-.20),.08,.024,n=10)
 m.save(name)
for phases,light in [(1,False),(3,False),(1,True),(3,True),(0,False)]:
 m=Cabinet();m.box('Case',-.35,-.48,-.47,.70,1.72,.94)
 m.box('Trim',-.39,-.49,-.49,.78,.12,.98)
 m.box('Trim',-.39,1.24,-.49,.78,.07,.98)
 for i in range(max(1,phases)):
  z=(i-(max(1,phases)-1)/2)*.26
  m.box('Fuse'+str(i),-.38,.33,z-.045,.06,.70,.09)
  m.box('Door'+str(i),-.43,.35,z-.03,.035,.62,.06)
 for i in range(3):m.box('Services',-.40,-.26,(i-1)*.18-.03,.04,.14,.06)
 if light:
  m.cyl('Pole',1,(.17,1.30,.0),4.03,.05,n=12)
  m.box('LampBracket',-1.1,5.29,-.035,1.27,.06,.07)
  m.box('LampHousing',-1.48,5.20,-.21,.65,.17,.42)
  m.box('LampLens',-1.42,5.18,-.17,.54,.022,.34)
 m.save('undergroundservicepedestal' if phases==0 else 'undergroundfused'+str(phases)+('light' if light else ''))
m=Cabinet()
for x in [-.46,.43]:
 m.box('Case',x,-.18,-.5,.03,.08,1.)
 for z in [-.40,.40]:m.box('Case',x,.0,z,.015,.50,.015)
for z in [-.45,-.25,-.05,.15,.35]:m.box('Case',-.43,-.18,z,.86,.015,.025)
for i in range(16):
 x=-.36+i*.048;m.box('Color'+str(i),x,-.11,-.5,.024,.024,1.)
 # Node Direction indices XN=0,XP=1,YN=2,YP=3,ZN=4,ZP=5.
 for side in [0,1,2,4,5]:
  if side in [0,1]:m.box('Branch%d_%d'%(side,i),min(x,(-.5 if side==0 else .5)),-.085,(i-8)*.008,abs(x-(-.5 if side==0 else .5)),.023,.024)
  if side==2:m.box('Branch%d_%d'%(side,i),x,-.5,-.012,.024,.40,.024)
m.save('ceilingcolorcabletray')
