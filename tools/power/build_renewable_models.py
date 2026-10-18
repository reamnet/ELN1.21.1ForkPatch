"""Original meshes for MPPT/hybrid electronics and utility-scale wind/water generators."""
from pathlib import Path
import math
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
class Mesh:
    def __init__(self):self.lines=['# Electrical Age renewable power equipment','mtllib renewable.mtl'];self.count=0
    def part(self,n):self.lines+=['o '+n,'usemtl Neutral']
    def box(self,a,b,angle=0):
        x,y,z=a;X,Y,Z=b
        points=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        c,s=math.cos(angle),math.sin(angle)
        self.lines+=['v '+' '.join(f'{v:.6f}' for v in (c*x-s*y,s*x+c*y,z)) for x,y,z in points]
        for i,j,k,l in [(3,2,1,0),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,4,7,3)]:
            self.lines += [f'f {self.count+i+1} {self.count+j+1} {self.count+k+1}',f'f {self.count+i+1} {self.count+k+1} {self.count+l+1}']
        self.count+=8
    def save(self,n):
        p=ROOT/n;p.mkdir(parents=True,exist_ok=True)
        (p/(n+'.obj')).write_text('\n'.join(self.lines)+'\n');(p/'renewable.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 1 1 1\n')
for hybrid in [False,True]:
    m=Mesh();m.part('Case');m.box((-.34,-.43,-.24),(.34,.37,.24))
    m.part('Trim');m.box((-.36,-.5,-.26),(.36,-.43,.26))
    for x in [-.30,-.22,-.14,-.06,.02,.10,.18,.26]:m.box((x,-.33,-.30),(x+.025,.30,-.24))
    m.box((-.36,.37,-.26),(.36,.41,.26))
    m.part('Display');m.box((-.26,.03,.24),(.26,.26,.26))
    m.part('Solar');m.box((-.5,-.47,-.10),(-.34,-.39,.10))
    m.part('Output');m.box((.34,-.47,-.10),(.5,-.39,.10))
    m.part('Return');m.box((-.08,-.47,-.5),(.08,-.39,-.24))
    if hybrid:m.part('Grid');m.box((-.08,-.47,.24),(.08,-.39,.5))
    m.save('solarhybrid' if hybrid else 'solarmppt')
m=Mesh();m.part('Case');m.box((-.44,-.5,-.44),(.44,.0,.44))
m.box((-.23,0,-.23),(.23,5,.23));m.box((-.17,5,-.17),(.17,9.5,.17))
m.box((-.40,9.2,-1.0),(.40,9.85,.65));m.box((-.22,9.32,.65),(.22,9.68,.86))
m.part('Trim');m.box((-.46,-.47,-.46),(.46,-.38,.46));m.box((-.32,.1,.23),(.32,.9,.34))
m.part('Output');m.box((-.08,-.47,.34),(.08,-.39,.5))
m.part('Rotor');m.box((-.23,-.23,-.16),(.23,.23,.16))
for i in range(3):
    a=i*math.tau/3
    m.box((-.12,.18,-.06),(.16,1.5,.06),a);m.box((-.17,1.5,-.06),(.25,3.2,.06),a);m.box((-.08,3.2,-.04),(.18,4.5,.04),a)
m.save('utilitywind')
m=Mesh();m.part('Case');m.box((-.43,-.5,-.43),(.43,.45,.43))
for x in [-.65,.65]:m.box((x-.09,-.5,-.85),(x+.09,3.7,-.45))
m.box((-.82,3.35,-.85),(.82,3.65,-.45))
m.part('Trim');m.box((-.45,-.48,-.45),(.45,-.38,.45))
m.part('Display');m.box((-.23,.05,.43),(.23,.28,.45))
m.part('Output');m.box((-.08,-.47,.43),(.08,-.39,.5))
m.part('Rotor')
for i in range(20):
    a=i*math.tau/20
    for z in [-.39,.29]:m.box((-.66,3.65,z),(.66,3.84,z+.10),a)
    m.box((-.52,3.74,-.5),(.52,3.98,.5),a)
for i in range(8):m.box((-.09,0,-.32),(.09,3.7,.32),i*math.tau/8)
m.box((-.28,-.28,-.55),(.28,.28,.55));m.save('utilitywatermill')
