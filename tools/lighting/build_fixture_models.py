"""Generate the original, texture-free fixture meshes. Run from the repository root."""
from pathlib import Path
from adjustable_street_heads import split_heads
from math import sin, cos, pi, sqrt
ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/eln/model'
class Mesh:
    def __init__(self):
        self.lines = ['# Electrical Age fixtures. +X projects out of the mounting surface.', 'mtllib fixture.mtl']
        self.count = 0
        self.transform = lambda p: p
    def part(self, name): self.lines += ['o '+name, 'usemtl Neutral']
    def face(self, points):
        # Preserve outward winding when a mounting transform swaps/reflections axes.
        origin=self.transform((0,0,0))
        basis=[tuple(self.transform(tuple(int(i==j) for j in range(3)))[k]-origin[k] for k in range(3)) for i in range(3)]
        a,b,c=basis
        determinant=a[0]*(b[1]*c[2]-b[2]*c[1])-a[1]*(b[0]*c[2]-b[2]*c[0])+a[2]*(b[0]*c[1]-b[1]*c[0])
        if determinant < 0: points=list(reversed(points))
        for p in points:
            self.lines.append('v '+' '.join(f'{v:.6f}' for v in self.transform(p)))
        for i in range(1,len(points)-1): self.lines.append(f'f {self.count+1} {self.count+i+1} {self.count+i+2}')
        self.count += len(points)
    def box(self, a,b):
        x,y,z=a; X,Y,Z=b
        p=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        for ids in [(3,2,1,0),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,4,7,3)]: self.face([p[i] for i in ids])
    def tube(self,a,b,r,n=10):
        def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
        d=[b[i]-a[i] for i in range(3)]; length=sqrt(sum(v*v for v in d)); d=[v/length for v in d]
        u=cross(d,(0,1,0) if abs(d[1])<.9 else (1,0,0)); length=sqrt(sum(v*v for v in u));u=[v/length for v in u];v=cross(d,u)
        rings=[[tuple(p[j]+r*(u[j]*cos(2*pi*i/n)+v[j]*sin(2*pi*i/n)) for j in range(3)) for i in range(n)] for p in [a,b]]
        for i in range(n): k=(i+1)%n; self.face([rings[0][i],rings[0][k],rings[1][k],rings[1][i]])
        self.face(list(reversed(rings[0]))); self.face(rings[1])
    def cone(self,x0,x1,r0,r1,y=0,z=0,n=24,caps=True):
        rings=[[(x,y+r*cos(2*pi*i/n),z+r*sin(2*pi*i/n)) for i in range(n)] for x,r in [(x0,r0),(x1,r1)]]
        for i in range(n): k=(i+1)%n; self.face([rings[0][i],rings[0][k],rings[1][k],rings[1][i]])
        if caps: self.face(list(reversed(rings[0])));self.face(rings[1])
    def ring(self,x,outer,inner,y=0,z=0,n=32):
        for i in range(n):
            a=2*pi*i/n;b=2*pi*(i+1)/n
            self.face([(x,y+inner*cos(a),z+inner*sin(a)),(x,y+outer*cos(a),z+outer*sin(a)),(x,y+outer*cos(b),z+outer*sin(b)),(x,y+inner*cos(b),z+inner*sin(b))])
    def sphere(self,c,r,n=16,rows=10):
        def p(i,j):
            a=pi*i/rows;b=2*pi*j/n
            return(c[0]+r[0]*cos(a),c[1]+r[1]*sin(a)*cos(b),c[2]+r[2]*sin(a)*sin(b))
        for i in range(rows):
            for j in range(n):
                if i==0:self.face([p(i,j),p(i+1,j),p(i+1,j+1)])
                elif i==rows-1:self.face([p(i,j),p(i+1,j),p(i,j+1)])
                else:self.face([p(i,j),p(i+1,j),p(i+1,j+1),p(i,j+1)])
    def housing(self,x0,x1,y,z,w,d):
        outline=[(-w/2+.07,-d/2), (w/2-.07,-d/2),(w/2,-d/2+.07),(w/2,d/2-.07),(w/2-.07,d/2),(-w/2+.07,d/2),(-w/2,d/2-.07),(-w/2,-d/2+.07)]
        rings=[[(x,y+a,z+b) for a,b in outline] for x in [x0,x1]]
        for i in range(8):k=(i+1)%8;self.face([rings[0][i],rings[0][k],rings[1][k],rings[1][i]])
        self.face(list(reversed(rings[0])));self.face(rings[1])
    def save(self,name):
        folder=ROOT/name;folder.mkdir(parents=True,exist_ok=True)
        (folder/(name+'.obj')).write_text(split_heads('\n'.join(self.lines)+'\n',name))
        (folder/'fixture.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 1 1 1\n')

m=Mesh();m.part('Base');m.cone(.01,.12,.19,.14)
for y in [-.11,.11]:
    for z in [-.11,.11]:m.cone(.12,.145,.018,.018,y,z,n=8)
m.part('Metal');m.cone(.12,5.40,.075,.05,n=12)
for y in [-.8,.8]:
    m.tube((5.34,0,0),(5.34,y,0),.045)
    m.housing(5.17,5.40,y,0,.70,.58)
    for offset in [-.23,-.11,.01,.13,.25]:m.box((5.4,y+offset,-.20),(5.43,y+offset+.018,.20))
m.part('Trim')
for y in [-.8,.8]:m.box((5.155,y-.27,-.23),(5.175,y+.27,.23))
for i,y in enumerate([-.8,.8]):m.part('Light'+str(i));m.box((5.145,y-.25,-.21),(5.157,y+.25,.21))
m.save('twinparkinglight')

m=Mesh();m.part('Base');m.box((.015,-.24,-.18),(.08,.24,.18))
m.part('Metal')
for z in [-.32,.32]:
    m.tube((.08,-.12,z*.45),(.34,-.12,z),.035)
    m.tube((.34,-.12,z),(.40,.03,z),.035)
a=-25*pi/180
m.transform=lambda p:(.38+cos(a)*(p[0]-.38)-sin(a)*p[1],sin(a)*(p[0]-.38)+cos(a)*p[1],p[2])
m.housing(.31,.55,.03,0,.52,.88)
for y in [-.18,-.08,.02,.12,.22]:m.box((.275,y,-.34),(.315,y+.022,.34))
m.part('Trim');m.box((.55,-.20,-.40),(.565,.26,.40))
m.part('Light0');m.box((.565,-.17,-.37),(.57,.23,.37));m.save('warehousewalllight')

m=Mesh();m.part('Base');m.cone(.01,.055,.22,.23);m.cone(.055,.08,.23,.18)
m.part('Metal');m.cone(.08,.53,.025,.023,n=12);m.cone(.46,.51,.08,.06,n=16)
m.part('Trim');m.cone(.77,.795,.035,.025,n=12);m.ring(.32,.295,.277);m.ring(.78,.164,.15)
m.part('Shade');m.cone(.32,.78,.29,.16,caps=False)
m.part('Light0');m.sphere((.49,0,0),(.10,.065,.065));m.save('householddesklamp')

m=Mesh();m.part('Base');m.cone(.01,.06,.29,.29,caps=False);m.ring(.06,.29,.23)
m.part('Metal');m.cone(.015,.06,.23,.23,caps=False)
m.part('Trim');m.ring(.062,.285,.26)
m.part('Light0');m.cone(.022,.035,.225,.225,n=32);m.save('roundrecessedlight')

m=Mesh();m.part('Base');m.cone(.01,.08,.15,.10)
m.part('Metal')
for k in range(4):
    c=.14+k*.105
    points=[(c+.065*cos(i*2*pi/12),.04*sin(i*2*pi/12) if k%2==0 else 0,.04*sin(i*2*pi/12) if k%2 else 0) for i in range(12)]
    for i in range(12):m.tube(points[i],points[(i+1)%12],.009,n=6)
m.cone(.50,.72,.06,.095,n=16)
for k in range(5):
    a=k*2*pi/5
    def p(x,r):return(x,r*cos(a),r*sin(a))
    points=[p(.65,.04),p(.77,.14),p(.87,.27),p(.86,.32),p(.81,.34)]
    for start,end in zip(points,points[1:]):m.tube(start,end,.018,n=8)
    m.cone(.79,.83,.075,.07,.34*cos(a),.34*sin(a),n=16)
m.part('Trim');m.cone(.7,.76,.10,.06,n=16)
for k in range(5):
    a=k*2*pi/5;m.ring(.79,.076,.035,.34*cos(a),.34*sin(a),n=16)
for k in range(5):
    a=k*2*pi/5;m.part('Light'+str(k));m.sphere((.68,.34*cos(a),.34*sin(a)),(.13,.06,.06))
m.save('basicchandelier')

# A small slug-shaped cylindrical spotlight, with a recessed circular lens.
m=Mesh();m.part('Base');m.cone(.005,.025,.075,.075,n=24)
m.part('Metal');m.cone(.025,.205,.065,.082,n=24,caps=False)
for x in [.05,.08,.11]:m.cone(x,x+.012,.069,.069,n=24,caps=False)
m.part('Trim');m.ring(.215,.083,.062,n=32);m.cone(.205,.218,.083,.083,n=32,caps=False)
m.part('Light0');m.cone(.200,.205,.061,.061,n=32);m.save('minidotspotlight')

# Bronze porch lantern: wall plate, curved bracket, peaked roof, cage and glass.
m=Mesh();m.part('Base');m.box((.01,-.29,-.09),(.055,.32,.09))
m.part('Metal')
points=[(.04,.24,0),(.12,.32,0),(.27,.34,0),(.34,.29,0),(.34,.23,0)]
for a,b in zip(points,points[1:]):m.tube(a,b,.018,n=8)
x0,x1,z0,z1=.14,.48,-.17,.17
for x in [x0,x1]:
    for z in [z0,z1]:m.box((x-.012,-.30,z-.012),(x+.012,.19,z+.012))
m.box((.115,-.33,-.195),(.505,-.29,.195))
m.box((.105,.175,-.205),(.515,.205,.205))
peak=(.31,.32,0)
roof=[(.095,.205,-.215),(.525,.205,-.215),(.525,.205,.215),(.095,.205,.215)]
for i in range(4):m.face([roof[i],peak,roof[(i+1)%4]])
m.tube((.31,.31,0),(.31,.36,0),.018,n=8)
m.box((.255,-.29,-.055),(.365,-.21,.055))
m.part('Trim');m.tube((.31,-.39,0),(.31,-.32,0),.025,n=8)
m.part('Light0');m.sphere((.31,-.07,0),(.058,.13,.058))
m.part('Glass');m.box((.15,-.285,-.16),(.47,.17,.16));m.save('porchlanternsconce')

# Plain porcelain-style holder with a screw collar and exposed replaceable bulb.
m=Mesh();m.part('Base');m.cone(.005,.03,.125,.125,n=24);m.cone(.03,.08,.10,.055,n=24)
m.part('Metal');m.cone(.08,.115,.052,.052,n=20)
m.part('Trim')
for x in [.083,.095,.107]:m.cone(x,x+.005,.055,.055,n=20,caps=False)
m.part('Light0');m.cone(.105,.145,.038,.05,n=16);m.sphere((.215,0,0),(.10,.075,.075));m.save('basiclampholder')

# Commercial fluorescent fixtures: long axis Z, normal +X (down when ceiling mounted).
for thick,name in [(False,'thinshoplight'),(True,'hangingfactorylight')]:
    m=Mesh();m.part('Base')
    if thick:
        for z in [-.83,.83]:m.box((.005,-.09,z-.09),(.045,.09,z+.09))
    else:m.box((.005,-.13,-1.14),(.045,.13,1.14))
    m.part('Metal');x=1.045 if thick else .045;w=.64 if thick else .28
    if thick:
        # Exactly one block of exposed suspension wire between mounting pads and housing.
        for z in [-.83,.83]:
            m.tube((.045,0,z),(1.045,0,z),.009,n=6)
            for y in [-.22,.22]:m.tube((.98,0,z),(1.06,y,z),.008,n=6)
        m.box((x,-w/2,-1.2),(x+.13,w/2,1.2))
        for z in [-1.1,1.1]:m.box((x+.13,-w/2,z-.018),(x+.20,w/2,z+.018))
        count=4;ys=[-.21,-.07,.07,.21]
    else:
        m.box((x,-w/2,-1.2),(x+.04,w/2,1.2));count=2;ys=[-.07,.07]
    m.part('Trim')
    for y in [-w/2,w/2]:m.box((x,y-.012,-1.2),(x+.18 if thick else x+.065,y+.012,1.2))
    for z in [-1.12,1.12]:
        for y in ys:m.box((x+.02,y-.047,z-.035),(x+.13 if thick else x+.075,y+.047,z+.035))
    for i,y in enumerate(ys):
        m.part('Light'+str(i));m.tube((x+.195 if thick else .12,y,-1.10),(x+.195 if thick else .12,y,1.10),.038,n=12)
    m.save(name)

# Two adjustable flood heads with a separate downward PIR sensor.
m=Mesh();m.part('Base');m.cone(.01,.06,.16,.16,n=24)
m.part('Metal')
for z in [-.25,.25]:
    m.tube((.05,.08,0),(.23,.12,z),.025)
    m.tube((.23,.12,z),(.29,.12,z),.035)
    m.housing(.27,.43,.12,z,.25,.34)
m.tube((.07,-.08,0),(.19,-.18,0),.024);m.cone(.13,.29,.08,.075,-.18,0,n=16)
m.part('Shade');m.sphere((.29,-.18,0),(.04,.064,.064),n=12,rows=6)
m.part('Trim')
for z in [-.25,.25]:m.box((.43,-.005,z-.16),(.455,.245,z+.16))
for i,z in enumerate([-.25,.25]):m.part('Light'+str(i));m.box((.45,.015,z-.14),(.46,.225,z+.14))
m.save('outdoormotionlight')

# Larger foyer designs. All include their ceiling canopy and hanging stem/chain.
def canopy():
    m=Mesh();m.part('Base');m.cone(.005,.06,.16,.13,n=20)
    m.part('Metal');m.cone(.06,.47,.018,.018,n=10)
    return m

def bulb(m,i,c,r=(.12,.055,.055)):
    m.part('Light'+str(i));m.sphere(c,r,n=12,rows=8)

m=canopy();m.cone(.42,1.32,.05,.065,n=16)
for i in range(6):
    a=i*pi/3
    def p(x,r):return(x,r*cos(a),r*sin(a))
    ps=[p(.69,.06),p(.94,.20),p(1.24,.38),p(1.30,.54),p(1.23,.60)]
    for a0,b0 in zip(ps,ps[1:]):m.tube(a0,b0,.018,n=8)
    m.cone(1.17,1.24,.08,.06,.60*cos(a),.60*sin(a),n=12)
    m.cone(1.08,1.18,.032,.032,.60*cos(a),.60*sin(a),n=12)
m.part('Trim');m.sphere((1.36,0,0),(.09,.09,.09),n=12,rows=6)
for i in range(6):a=i*pi/3;bulb(m,i,(1.02,.60*cos(a),.60*sin(a)))
m.save('candlefoyerchandelier')

m=canopy()
for x,r in [(.70,.67),(1.0,.51),(1.28,.34)]:
    m.cone(x,x+.035,r,r,n=32,caps=False)
    for i in range(12):a=i*pi/6;m.tube((.46,0,0),(x,r*cos(a),r*sin(a)),.008,n=6)
for i in range(6):
    a=i*pi/3;m.tube((.7,0,0),(1.16,.4*cos(a),.4*sin(a)),.013,n=8)
for i in range(6):a=i*pi/3;bulb(m,i,(1.04,.4*cos(a),.4*sin(a)),(.08,.055,.055))
m.part('Glass')
for x,r in [(.73,.67),(1.035,.51),(1.315,.34)]:
    for i in range(24):
        a=i*pi/12;y=r*cos(a);z=r*sin(a)
        m.cone(x,x+.20,.023,.023,y,z,n=6);m.cone(x+.20,x+.26,.023,0,y,z,n=6)
m.sphere((1.65,0,0),(.12,.07,.07),n=12,rows=6);m.save('crystaltierchandelier')

m=canopy()
for i in range(4):
    a=i*pi/2;m.tube((.42,0,0),(1.27,.76*cos(a),.76*sin(a)),.012,n=6)
m.cone(1.25,1.36,.86,.86,n=48,caps=False);m.cone(1.25,1.36,.70,.70,n=48,caps=False)
m.ring(1.25,.86,.70,n=48);m.ring(1.36,.86,.70,n=48)
for i in range(8):
    a=i*pi/4;m.cone(1.09,1.25,.038,.038,.77*cos(a),.77*sin(a),n=10)
for i in range(8):a=i*pi/4;bulb(m,i,(.99,.77*cos(a),.77*sin(a)))
m.save('wagonwheelchandelier')

m=canopy()
for x in [.72,1.54]:
    for y in [-.44,.44]:m.box((x-.018,y-.018,-.46),(x+.018,y+.018,.46))
    for z in [-.44,.44]:m.box((x-.018,-.44,z-.018),(x+.018,.44,z+.018))
for y in [-.44,.44]:
    for z in [-.44,.44]:
        m.tube((.72,y,z),(1.54,y,z),.018,n=8);m.tube((.45,0,0),(.72,y,z),.017,n=8)
m.cone(.5,1.3,.022,.022,n=10)
for i in range(4):
    a=i*pi/2;m.tube((1.28,0,0),(1.28,.23*cos(a),.23*sin(a)),.015,n=8)
    m.cone(1.16,1.29,.032,.032,.23*cos(a),.23*sin(a),n=10)
for i in range(4):a=i*pi/2;bulb(m,i,(1.05,.23*cos(a),.23*sin(a)))
m.save('lanternfoyerchandelier')

m=canopy();m.cone(.47,1.05,.025,.025,n=12);m.sphere((1.05,0,0),(.15,.15,.15),n=16,rows=8)
for i in range(8):
    a=i*pi/4;x=1.05+(-.25 if i%2==0 else .25);m.tube((1.05,0,0),(x,.61*cos(a),.61*sin(a)),.024,n=10)
for i in range(8):
    a=i*pi/4;x=1.05+(-.25 if i%2==0 else .25);bulb(m,i,(x,.70*cos(a),.70*sin(a)),(.095,.095,.095))
m.save('sputnikchandelier')

# Wheeled generator cart: this transparent-node mesh uses Minecraft +Y up, +Z forward.
m=Mesh();m.transform=lambda p:(p[1],p[0]-.5,p[2]);m.part('Base')
m.box((.12,-.42,-.38),(.24,.42,.38));m.box((.24,-.30,-.25),(.68,.30,.25))
for y in [-.31,.31]:
    m.tube((.20,y,-.42),(.20,y,.42),.028)
    m.tube((.20,y,-.42),(.90,y,-.53),.026)
    m.tube((.90,y,-.53),(.95,y,-.68),.026)
    m.box((.20,y-.04,.33),(.34,y+.04,.41))
m.tube((.95,-.31,-.68),(.95,.31,-.68),.026)
m.part('Metal');m.box((.57,-.37,-.30),(.78,.37,.30))
m.cone(.78,.82,.065,.065,.23,-.17,n=12)
m.cone(.25,2.58,.036,.027,n=12)
m.tube((2.55,-.49,0),(2.55,.49,0),.03)
for y in [-.36,.36]:
    m.tube((2.55,y,0),(2.36,y,0),.022)
    m.box((2.20,y-.27,.015),(2.53,y+.27,.19))
m.part('Trim')
for y in [-.39,.39]:m.tube((.20,y,-.27),(.20,y,.27),.025)
for y in [-.27,.27]:
    for x in [.33,.39,.45,.51]:m.box((x,y-.018,-.23),(x+.025,y+.018,.23))
for y in [-.45,.45]:m.tube((.20,y-.06,-.27),(.20,y+.06,-.27),.055,n=12)
m.part('Rubber')
for y in [-.45,.45]:m.tube((.20,y-.06,-.27),(.20,y+.06,-.27),.18,n=20)
for y in [-.31,.31]:m.tube((.94,y,-.55),(.95,y,-.68),.035,n=10)
for i,y in enumerate([-.36,.36]):
    m.part('Light'+str(i));m.box((2.23,y-.235,.19),(2.50,y+.235,.205))
m.save('gasworklight')

# Five wall sconces. Wall coordinates: X projects out, Y is up and Z runs along the wall.
def wallplate():
    m=Mesh();m.part('Base');m.box((.005,-.17,-.085),(.045,.17,.085));m.part('Metal');return m
m=wallplate();m.tube((.03,0,0),(.21,0,0),.027)
m.tube((.21,-.27,0),(.21,.27,0),.105,n=24)
m.part('Trim')
for y in [-.27,.27]:m.tube((.21,y-.008,0),(.21,y+.008,0),.11,n=24)
for i,y in enumerate([.28,-.28]):m.part('Light'+str(i));m.tube((.21,y-.005,0),(.21,y+.005,0),.085,n=24)
m.save('cylinderwallsconce')

m=wallplate();m.tube((.03,-.16,0),(.28,-.16,0),.019);m.tube((.28,-.16,0),(.28,.04,0),.019)
m.part('Shade');m.transform=lambda p:(p[1]+.28,p[0],p[2]);m.cone(-.08,.30,.23,.18,caps=False)
m.transform=lambda p:p;m.part('Light0');m.sphere((.28,.05,0),(.06,.11,.06));m.save('fabricwallsconce')

m=wallplate();m.tube((.04,0,0),(.19,0,0),.025);m.cone(.16,.20,.07,.07)
m.part('Light0');m.sphere((.32,.03,0),(.13,.13,.13));m.part('Glass');m.sphere((.32,.03,0),(.19,.19,.19));m.save('globewallsconce')

m=wallplate();m.tube((.04,.18,0),(.24,.25,0),.022)
for z in [-.12,.12]:
    for x in [.12,.36]:m.tube((x,-.26,z),(x,.22,z),.012,n=6)
for y in [-.26,.22]:
    m.tube((.12,y,-.12),(.36,y,-.12),.012,n=6);m.tube((.12,y,.12),(.36,y,.12),.012,n=6)
    m.tube((.12,y,-.12),(.12,y,.12),.012,n=6);m.tube((.36,y,-.12),(.36,y,.12),.012,n=6)
m.part('Light0');m.sphere((.24,0,0),(.07,.18,.07));m.save('cagewallsconce')

m=wallplate()
for z in [-.23,.23]:
    points=[(.03,-.10,0),(.22,-.19,z*.65),(.30,-.12,z),(.30,-.03,z)]
    for a,b in zip(points,points[1:]):m.tube(a,b,.018,n=8)
    m.tube((.30,-.05,z),(.30,-.03,z),.065,n=16)
    m.tube((.30,-.03,z),(.30,.02,z),.025,n=12)
for i,z in enumerate([-.23,.23]):m.part('Light'+str(i));m.sphere((.30,.105,z),(.045,.105,.045))
m.save('candlewallsconce')

# Table fixtures use canonical X up, and can follow the height of partial supporting blocks.
m=Mesh();m.part('Base');m.cone(.005,.04,.23,.23);m.part('Metal');m.sphere((.26,0,0),(.25,.17,.17));m.cone(.45,.62,.025,.025,n=10)
m.part('Shade');m.cone(.49,.87,.31,.29,caps=False)
m.part('Trim');m.ring(.49,.31,.295);m.ring(.87,.29,.275)
m.part('Light0');m.sphere((.65,0,0),(.12,.07,.07));m.save('ceramicdrumtablelamp')

m=Mesh();m.part('Base');m.sphere((.045,0,0),(.04,.20,.25));m.part('Metal');m.cone(.04,.38,.025,.025,n=12);m.tube((.38,0,0),(.46,.10,0),.025)
m.part('Shade');m.housing(.47,.58,.10,0,.32,.58)
m.part('Trim');m.box((.455,-.065,-.285),(.475,.265,.285))
m.part('Light0');m.tube((.455,.10,-.22),(.455,.10,.22),.035,n=12);m.save('bankerstablelamp')

m=Mesh();m.part('Base');m.cone(.005,.045,.20,.20);m.part('Metal')
points=[(.04,0,0),(.45,-.16,0),(.84,.18,0),(.80,.27,0)]
for a,b in zip(points,points[1:]):
    for z in [-.027,.027]:m.tube((a[0],a[1],z),(b[0],b[1],z),.013,n=8)
for p in points[1:3]:m.sphere(p,(.035,.035,.035),n=10,rows=6)
m.cone(.70,.85,.15,.055,.27,0,caps=False)
m.part('Trim');m.ring(.70,.15,.135,.27,0)
m.part('Light0');m.sphere((.72,.27,0),(.055,.07,.07));m.save('articulatedtablelamp')

for style in ['deepbafflepotlight','trimlesspotlight','gimbalpotlight']:
    m=Mesh();m.part('Base');outer=.20 if style!='gimbalpotlight' else .23
    m.ring(.025,outer,.15);m.cone(.005,.025,outer,outer,caps=False)
    m.part('Metal');m.cone(.005,.07,.15,.12,caps=False)
    if style=='deepbafflepotlight':
        for x in [.025,.04,.055]:m.ring(x,.15,.125)
    if style=='gimbalpotlight':
        m.tube((.045,0,-.17),(.045,0,.17),.012,n=8)
        m.transform=lambda p:(.065+cos(.35)*(p[0]-.065)-sin(.35)*p[1],sin(.35)*(p[0]-.065)+cos(.35)*p[1],p[2])
        m.cone(.02,.09,.13,.13,caps=False);m.part('Trim');m.ring(.09,.13,.105)
    else:m.part('Trim');m.ring(.028,outer,.17 if style=='trimlesspotlight' else .155)
    m.part('Light0');m.cone(.06,.066,.105,.105,n=32);m.save(style)

m=Mesh();m.part('Base');m.cone(.005,.06,.18,.18);m.part('Metal');m.cone(.06,.69,.105,.105,n=16);m.cone(.98,1.045,.17,.13,n=20)
for y in [-.12,.12]:
    for z in [-.12,.12]:m.tube((.69,y,z),(.99,y,z),.012,n=6)
m.part('Light0');m.cone(.70,.97,.10,.10,n=24);m.save('gardenbollardlight')

m=wallplate()
points=[(.04,.10,0),(.14,.27,0),(.37,.33,0),(.60,.23,0),(.63,.15,0)]
for a,b in zip(points,points[1:]):m.tube(a,b,.022,n=10)
m.part('Shade');m.transform=lambda p:(p[1]+.63,p[0],p[2]);m.cone(-.08,.17,.29,.05,caps=False)
m.part('Trim');m.ring(-.08,.29,.275)
m.transform=lambda p:p;m.part('Light0');m.sphere((.63,-.04,0),(.07,.09,.07));m.save('gooseneckbarnlight')

m=Mesh();m.part('Base');m.cone(.005,.13,.22,.14,n=16);m.part('Metal');m.cone(.13,1.70,.075,.06,n=12);m.cone(1.67,1.76,.18,.22,n=20)
for y in [-.19,.19]:
    for z in [-.19,.19]:m.tube((1.76,y,z),(2.31,y,z),.015,n=8)
m.cone(2.30,2.35,.31,.31,n=4);m.cone(2.35,2.57,.31,.025,n=4)
m.part('Trim');m.cone(1.75,1.80,.24,.24,n=4)
m.part('Light0');m.sphere((2.06,0,0),(.16,.08,.08))
m.part('Glass');m.box((1.82,-.175,-.175),(2.29,.175,.175));m.save('traditionallanternpost')

# Kitchen machines use +Y up / +Z front and a bottom at Y=-.5.
def kitchenmesh():
    m=Mesh();m.transform=lambda p:(p[1],p[0]-.5,p[2]);return m
m=kitchenmesh();m.part('Case');m.box((.02,-.46,-.31),(.57,.46,.31))
m.part('Black');m.box((.10,-.40,.31),(.49,.23,.325))
m.part('Trim');m.box((.12,.23,.325),(.49,.27,.36));m.box((.09,.29,.325),(.49,.42,.34))
for x in [.14,.21,.28,.35]:m.box((x,.31,.34),(x+.035,.39,.35))
m.part('Heat');m.box((.18,-.32,.326),(.41,.16,.329))
m.part('Display');m.box((.41,.29,.345),(.49,.42,.35))
m.part('Glass');m.box((.12,-.38,.33),(.47,.21,.338));m.save('kitchenmicrowave')

m=kitchenmesh();m.part('Case');m.housing(.04,.36,0,0,.46,.65)
m.part('Black')
for z in [-.15,.15]:m.box((.352,-.17,z-.045),(.365,.17,z+.045))
m.box((.12,.225,-.21),(.28,.24,.21))
m.part('Trim');m.box((.28,.24,-.04),(.30,.31,.04));m.cone(.02,.05,.04,.04,-.15,-.22);m.cone(.02,.05,.04,.04,.15,.22)
m.part('Heat')
for z in [-.15,.15]:m.box((.349,-.14,z-.025),(.355,.14,z+.025))
m.save('kitchentoaster')

m=kitchenmesh();m.part('Case');m.box((.015,-.48,-.43),(.12,.48,.43))
m.part('Black');m.box((.12,-.465,-.415),(.135,.465,.415))
m.part('Trim')
for y in [-.22,.22]:
    for z in [-.18,.20]:m.ring(.137,.16,.15,y,z,n=24)
for y in [-.20,-.065,.065,.20]:m.cone(.135,.16,.025,.025,y,.36,n=12)
m.part('Heat')
for y in [-.22,.22]:
    for z in [-.18,.20]:
        for r in [.06,.10,.135]:m.ring(.139,r,r-.009,y,z,n=24)
m.part('Display');m.box((.14,.29,.32),(.145,.40,.38));m.save('kitchenflatstove')

m=kitchenmesh();m.part('Case');m.box((.02,-.43,-.39),(1.85,.43,.39))
m.part('Black');m.box((.08,-.40,-.40),(.16,.40,-.39));m.box((1.22,-.43,.389),(1.24,.43,.405))
m.part('Trim');m.box((.64,.30,.40),(1.10,.34,.45));m.box((1.38,.30,.40),(1.69,.34,.45))
for y in [-.34,.34]:m.cone(.005,.025,.04,.04,y,0,n=12)
m.part('Display');m.box((1.56,-.25,.395),(1.68,-.10,.405));m.save('kitchenfridge')

m=kitchenmesh();m.part('Case');m.box((.015,-.25,-.24),(.07,.25,.24));m.box((.07,-.23,-.23),(.70,.23,-.08));m.box((.57,-.25,-.12),(.75,.25,.23))
m.part('Black');m.cone(.07,.10,.17,.17,0,.035);m.cone(.48,.59,.08,.08,0,.08);m.box((.10,-.13,-.075),(.13,.13,.18))
m.part('Trim');m.cone(.15,.43,.125,.15,0,.055,caps=False)
m.tube((.23,.13,.05),(.23,.21,.05),.015);m.tube((.23,.21,.05),(.39,.21,.05),.015);m.tube((.39,.21,.05),(.39,.13,.05),.015)
m.part('Heat');m.cone(.16,.30,.11,.11,0,.055,n=20)
m.part('Display');m.box((.62,.12,.235),(.67,.19,.245))
m.part('Glass');m.cone(.15,.43,.13,.15,0,.055,caps=False);m.save('kitchencoffeemaker')
