"""Original standby generator and transfer-switch meshes; +Z is the front."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/eln/model'

class Mesh:
    def __init__(self):
        self.lines = ['# Electrical Age standby power equipment', 'mtllib standby.mtl']
        self.count = 0
    def part(self, name):
        self.lines += ['o '+name, 'usemtl Neutral']
    def box(self, a, b):
        x,y,z=a; X,Y,Z=b
        p=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        self.lines += ['v '+' '.join(f'{v:.6f}' for v in q) for q in p]
        for i,j,k,l in [(3,2,1,0),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,4,7,3)]:
            self.lines += [f'f {self.count+i+1} {self.count+j+1} {self.count+k+1}',
                           f'f {self.count+i+1} {self.count+k+1} {self.count+l+1}']
        self.count += 8
    def save(self, name):
        folder=ROOT/name;folder.mkdir(parents=True,exist_ok=True)
        (folder/(name+'.obj')).write_text('\n'.join(self.lines)+'\n')
        (folder/'standby.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 1 1 1\n')

m=Mesh()
m.part('Case');m.box((-.35,-.43,-.23),(.35,.37,.23))
m.part('Trim')
m.box((-.38,-.49,-.26),(.38,-.43,.26))
m.box((-.365,.37,-.245),(.365,.41,.245))
for x in [-.335,.335]:m.box((x-.01,-.39,.23),(x+.01,.33,.248))
m.box((.25,-.06,.23),(.275,.14,.265))
m.box((-.29,-.38,.23),(.20,.03,.24))
m.box((.24,.41,-.12),(.255,.49,-.105))
m.part('Vents')
for x in [-.23,-.14,-.05,.04,.13]:m.box((x,-.29,.24),(x+.045,-.10,.245))
m.part('Display');m.box((-.25,.16,.23),(.17,.30,.25))
m.part('Indicator');m.box((.23,.23,.23),(.27,.27,.25))
m.part('Mains');m.box((-.50,-.47,-.10),(-.35,-.40,.10))
m.part('Output');m.box((.35,-.47,-.10),(.50,-.40,.10))
m.part('Backup');m.box((-.10,-.47,-.50),(.10,-.40,-.23))
m.save('automatictransferswitch')

m=Mesh()
m.part('Case');m.box((-.44,-.39,-.38),(.44,.27,.38))
m.box((-.455,.27,-.395),(.455,.31,.395))
m.part('Trim')
m.box((-.46,-.46,-.40),(.46,-.39,.40))
for x in [-.35,.35]:m.box((x-.055,-.50,-.31),(x+.055,-.46,.31))
m.box((-.43,-.35,.38),(.13,.23,.391))
m.box((.075,-.07,.391),(.10,.09,.42))
m.box((-.20,.31,-.20),(-.10,.35,-.10))
m.box((.30,.31,-.23),(.34,.43,-.19))
m.box((.27,.41,-.23),(.34,.45,-.19))
m.part('Vents')
for y in [-.25,-.19,-.13,-.07,-.01,.05,.11,.17]:
    m.box((-.445,y,-.29),(-.44,y+.025,.28))
    m.box((.44,y,-.29),(.445,y+.025,.15))
for x in [-.32,-.24,-.16,-.08,0.0]:m.box((x,-.29,.391),(x+.04,-.02,.398))
m.part('Display');m.box((.19,.06,.38),(.39,.21,.40))
m.part('Indicator');m.box((.26,-.01,.38),(.31,.025,.40))
m.part('Output')
m.box((-.5,-.47,-.10),(-.44,-.40,.10));m.box((.44,-.47,-.10),(.5,-.40,.10))
m.save('standbygenerator')
