"""Generate the original, texture-free fixture meshes. Run from the repository root."""
from pathlib import Path
from math import sin, cos, pi, sqrt
ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/eln/model'
class Mesh:
    def __init__(self):
        self.lines = ['# Electrical Age fixtures. +X projects out of the mounting surface.', 'mtllib fixture.mtl']
        self.count = 0
        self.transform = lambda p: p
    def part(self, name): self.lines += ['o '+name, 'usemtl Neutral']
    def face(self, points):
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
        (folder/(name+'.obj')).write_text('\n'.join(self.lines)+'\n')
        (folder/'fixture.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 1 1 1\n')

m=Mesh();m.part('Base');m.cone(.01,.12,.19,.14)
for y in [-.11,.11]:
    for z in [-.11,.11]:m.cone(.12,.145,.018,.018,y,z,n=8)
m.part('Metal');m.cone(.12,3.40,.075,.05,n=12)
for y in [-.8,.8]:
    m.tube((3.34,0,0),(3.34,y,0),.045)
    m.housing(3.17,3.40,y,0,.70,.58)
    for offset in [-.23,-.11,.01,.13,.25]:m.box((3.4,y+offset,-.20),(3.43,y+offset+.018,.20))
m.part('Trim')
for y in [-.8,.8]:m.box((3.155,y-.27,-.23),(3.175,y+.27,.23))
for i,y in enumerate([-.8,.8]):m.part('Light'+str(i));m.box((3.145,y-.25,-.21),(3.157,y+.25,.21))
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
