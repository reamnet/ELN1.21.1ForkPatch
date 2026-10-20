#!/usr/bin/env python3
"""Original utility distribution and Aura-inspired fan meshes (metres, triangulated OBJ)."""
from generate_fans_and_outlet import Mesh,root
from math import sin,cos,pi,tau

def raised_models():
 for folder,old,new in [('powerpole','utilitypole','utilitypoletall'),('transmissiontower','transmissiontower','transmissiontowertall')]:
  source=root/folder/(old+'.obj');lines=[]
  for line in source.read_text().splitlines():
   if line.startswith('v '):
    x,y,z=map(float,line.split()[1:4]);line='v %.6f %.6f %.6f'%(x,y+3 if y>0 else y,z)
   lines.append(line)
  (source.parent/(new+'.obj')).write_text('\n'.join(lines)+'\n')

def aura():
 m=Mesh()
 m.cyl('Base',0,(.0,0,0),.075,.095,.075,32)
 m.cyl('Metal',0,(.075,0,0),.20,.016,n=20)
 m.cyl('Metal',0,(.275,0,0),.10,.065,.10,40)
 m.cyl('Rotor',0,(.375,0,0),.055,.105,n=48)
 m.cyl('Metal',0,(.43,0,0),.045,.102,.096,48)
 # Pear-shaped swept blades: slim curved neck, broad round outer paddle.
 controls=[(.078,-.020),(.15,-.020),(.20,-.10),(.31,-.115),(.405,-.115),(.454,-.060),(.455,.010),(.455,.085),(.405,.127),(.335,.116),(.24,.10),(.17,.027),(.085,.025)]
 # Closed Catmull-Rom outline, sampled densely enough to retain the round paddle silhouette.
 outline=[]
 for j in range(len(controls)):
  p0,p1,p2,p3=[controls[k%len(controls)] for k in [j-1,j,j+1,j+2]]
  for k in range(5):
   t=k/5;t2=t*t;t3=t2*t
   outline.append(tuple(.5*(2*p1[d]+(-p0[d]+p2[d])*t+(2*p0[d]-5*p1[d]+4*p2[d]-p3[d])*t2+(-p0[d]+3*p1[d]-3*p2[d]+p3[d])*t3) for d in range(2)))
 for j in range(5):
  a=j*tau/5
  def point(p,top):
   r,w=p;return(.398+top+.025*(w/.13),r*cos(a)-w*sin(a),r*sin(a)+w*cos(a))
  # Surface triangles from an interior point avoid fan triangulation across the narrow neck.
  # Separate longitudinal curved strips make the blade solid and subtly cupped.
  # Polygon is star-shaped around the neck/paddle junction.
  center=(.245,.005)
  for k,p in enumerate(outline):
   q=outline[(k+1)%len(outline)]
   m.poly('Rotor',[point(center,0),point(q,0),point(p,0)])
   m.poly('Rotor',[point(center,.013),point(p,.013),point(q,.013)])
   m.poly('Rotor',[point(p,0),point(q,0),point(q,.013),point(p,.013)])
 for i in range(7):
  a=i*tau/7;y=.067*cos(a);z=.067*sin(a)
  m.cyl('Trim',0,(.474,y,z),.003,.012,n=16)
  m.cyl('Light%d'%i,0,(.477,y,z),.002,.008,n=16)
 m.save('auraceilingfan')

def lead(m,part,a,b,r=.013,n=8):
 # Orthonormal tube frame; no degenerate quads for vertical jumpers.
 from math import sqrt
 d=[b[i]-a[i] for i in range(3)];length=sqrt(sum(x*x for x in d));t=[x/length for x in d]
 ref=[0,1,0] if abs(t[1])<.9 else [1,0,0]
 u=[t[1]*ref[2]-t[2]*ref[1],t[2]*ref[0]-t[0]*ref[2],t[0]*ref[1]-t[1]*ref[0]];k=sqrt(sum(x*x for x in u));u=[x/k for x in u]
 v=[t[1]*u[2]-t[2]*u[1],t[2]*u[0]-t[0]*u[2],t[0]*u[1]-t[1]*u[0]]
 def q(end,j):return tuple(end[i]+r*(u[i]*cos(j*tau/n)+v[i]*sin(j*tau/n)) for i in range(3))
 for j in range(n):m.poly(part,[q(a,j),q(a,j+1),q(b,j+1),q(b,j)])

def pole():
 m=Mesh();m.cyl('main',1,(0,-.5,0),7.37,.145,.095,16)
 m.box('Crossarm',-.12,6.15,-1.18,.24,.16,2.36)
 m.box('SingleArm',-.12,6.15,-.25,.24,.16,.50)
 for i,z in enumerate([-.95,0,.95]):
  m.cyl('Pin%d'%i,1,(0,6.31,z),.28,.045,n=16)
  for k in range(3):m.cyl('Pin%d'%i,1,(0,6.35+k*.065,z),.025,.09,n=20)
 m.box('SecondaryRack',.12,4.45,-.39,.12,1.1,.78)
 for i in range(3):
  m.cyl('Secondary%d'%i,0,(.24,4.65+i*.32,0),.15,.055,n=16)
  m.cyl('Secondary%d'%i,0,(.38,4.65+i*.32,0),.025,.10,n=20)
 m.cyl('Transformer',1,(-.33,4.45,0),1.05,.29,n=32)
 m.cyl('Transformer',1,(-.33,5.50,0),.035,.315,n=32)
 m.cyl('TransformerPin',1,(-.33,5.535,0),.18,.045,n=16)
 for i,z in enumerate([-.95,0,.95]):
  m.box('CutoutBase%d'%i,-.42,5.68,z-.08,.24,.13,.16)
  m.cyl('CutoutInsulator%d'%i,0,(-.65,5.745,z),.23,.065,n=16)
  # Fuse door pivot at (-.68,5.45,z); closed tube rises to the upper contact.
  m.cyl('FuseDoor%d'%i,1,(-.68,5.45,z),.55,.029,n=12)
  m.cyl('FuseDoor%d'%i,1,(-.68,5.45,z),.045,.065,n=12)
  m.box('FuseDoor%d'%i,-.72,5.99,z-.055,.08,.045,.11)
  m.cyl('Riser%d'%i,1,(.26,-.48,z*.30),6.70,.040,n=12)
  m.cyl('Riser%d'%i,1,(.26,6.22,z*.30),.12,.075,n=16)
  lead(m,'RiserJumper%d'%i,(.26,6.34,z*.30),(0,6.63,z))
  m.cyl('Regulator%d'%i,1,(.45,4.0,z*.73),1.2,.25,n=24)
  m.cyl('Regulator%d'%i,1,(.45,5.2,z*.73),.06,.28,n=24)
  m.cyl('RegulatorPin%d'%i,1,(.45,5.26,z*.73),.22,.05,n=16)
  m.cyl('Capacitor%d'%i,1,(.34,4.3,z*.7),.80,.16,n=20)
  m.cyl('CapacitorPin%d'%i,1,(.34,5.1,z*.7),.2,.045,n=12)
  m.cyl('SwitchInsulator%d'%i,1,(-.35,6.1,z),.40,.07,n=16)
  m.box('Knife%d'%i,-.35,6.51,z-.025,.7,.025,.05)
 m.box('CapacitorRack',.08,4.2,-.98,.55,.08,1.96)
 m.box('Recloser',-.48,4.5,-.55,.75,.65,1.1)
 m.box('Control',-.29,3.6,-.26,.44,.56,.52)
 m.cyl('Handle',1,(-.27,.4,0),3.3,.018,n=10)
 m.box('Handle',-.36,.4,-.06,.18,.04,.12)
 # Streetlight: bracket is visibly clamped to the stem below the conductors.
 m.box('LampBracket',-.20,5.10,-.08,.17,.42,.16)
 m.box('LampBracket',-1.15,5.32,-.045,1.0,.055,.09)
 m.box('LampHousing',-1.48,5.20,-.17,.62,.12,.34)
 m.box('LampLens',-1.43,5.193,-.135,.52,.008,.27)
  # Raise the cutouts away from the LV rack. Individual output spans terminate on their hinges.
 for name,faces in list(m.p.items()):
  if name.startswith(('Cutout','FuseDoor')):m.p[name]=[[(x,y+.4,z) for x,y,z in face] for face in faces]
 for i,z in enumerate([-.95,0,.95]):
  lead(m,'FuseJumper%d'%i,(0,6.63,z),(-.68,6.40,z))
  lead(m,'TransformerFeed%d'%i,(0,6.63,z),(-.68,6.40,0))
  lead(m,'TransformerFeed%d'%i,(-.68,5.85,0),(-.33,5.715,0))
  lead(m,'RegulatorFeed%d'%i,(-.68,5.85,z),(.30,5.48,z*.73))
  m.cyl('RegulatorLoadPin%d'%i,1,(.65,5.26,z*.73),.22,.05,n=16)
  lead(m,'CapacitorFeed%d'%i,(0,6.63,z),(.34,5.30,z*.7))
  lead(m,'SwitchFeed%d'%i,(0,6.63,z),(-.35,6.51,z))
 for i in range(3):
  lead(m,'TransformerSecondary',(-.60,4.65+i*.24,.20),(.39,4.65+i*.32,.20),.009)
  lead(m,'TransformerSecondary',(.39,4.65+i*.32,.20),(.39,4.65+i*.32,0),.009)
 m.save('distributionpole')

def cabinets():
 # Wall units use the cabinet renderer's Z-facing convention, support at local Z=-.5.
 m=Mesh();m.box('Case',-.19,-.49,-.49,.38,.73,.17)
 m.cyl('Pipe',1,(0,.24,-.40),.68,.045,n=16)
 m.cyl('Hood',1,(0,.90,-.40),.10,.12,.08,24)
 for i in range(3):m.cyl('Terminal%d'%i,2,(-.17+i*.17,.80,-.42),.14,.037,n=16)
 m.save('serviceweatherhead')
 m=Mesh();m.box('Case',-.30,-.47,-.49,.60,.88,.18)
 m.cyl('Socket',2,(0,.05,-.31),.04,.235,n=48)
 m.cyl('MeterFace',2,(0,.05,-.27),.13,.205,n=48)
 m.cyl('Glass',2,(0,.05,-.14),.09,.213,.195,48)
 m.box('Readout',-.135,.075,-.133,.27,.085,.006)
 # Meter revolutions visibly move a marked aluminium disk inside the glass.
 m.cyl('Disk',1,(0,-.065,-.18),.014,.155,n=40)
 m.box('DiskMark',-.16,-.066,-.189,.075,.016,.022)
 for i in range(5):
  x=-.132+i*.066;m.cyl('Dial%d'%i,2,(x,.05,-.129),.006,.027,n=24)
  m.box('Needle%d'%i,x-.0015,.05,-.12,.003,.022,.003)
 m.box('SmartScreen',-.15,.01,-.129,.3,.12,.007)
 m.box('Disconnect',-.025,-.23,-.305,.05,.07,.006)
 for i in range(3):m.cyl('Terminal%d'%i,1,(-.16+i*.16,-.49,-.40),.04,.035,n=12)
 for i in range(3):m.cyl('TopTerminal%d'%i,1,(-.16+i*.16,.40,-.36),.04,.035,n=12)
 m.save('northamericanmeterbase')
 m=Mesh();m.box('Case',-.36,-.49,-.49,.72,.98,.21)
 m.box('Black',-.31,-.43,-.27,.62,.83,.008)
 m.box('Door',.32,-.45,-.26,.025,.9,.15)
 m.box('Main',-.12,.29,-.25,.24,.10,.035)
 for i in range(12):
  x=-.26+(i%2)*.29;y=.17-(i//2)*.098
  m.box('Breaker%d'%i,x,y,-.25,.23,.074,.045)
  m.box('Toggle%d'%i,x+.08,y+.015,-.202,.07,.04,.025)
 for i in range(3):m.cyl('Terminal%d'%i,1,(-.22+i*.22,-.5,-.39),.05,.04,n=12)
 for i in range(3):m.cyl('TopTerminal%d'%i,1,(-.22+i*.22,.40,-.36),.04,.035,n=12)
 m.save('twelvecircuitpanel')

if __name__=='__main__':
 raised_models();aura();pole();cabinets()
