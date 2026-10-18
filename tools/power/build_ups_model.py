"""Generate the UPS enclosure, display, battery doors and terminals (no external packages)."""
from pathlib import Path
lines=['# Electrical Age programmable UPS','mtllib ups.mtl']
count=0
def part(name):lines.extend(['o '+name,'usemtl Neutral'])
def box(a,b):
    global count
    x,y,z=a;X,Y,Z=b
    points=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
    lines.extend('v '+' '.join(f'{v:.6f}' for v in p) for p in points)
    for p,q,r,s in [(3,2,1,0),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,4,7,3)]:
        lines.extend([f'f {count+p+1} {count+q+1} {count+r+1}',f'f {count+p+1} {count+r+1} {count+s+1}'])
    count+=8
part('Case');box((-.30,-.45,-.31),(.30,.43,.31))
part('Trim');box((-.32,-.49,-.33),(.32,-.44,.33))
for y in [-.37,-.02]:box((-.315,y,-.285),(-.30,y+.30,.285))
for x in [-.23,.23]:box((x-.025,-.50,-.25),(x+.025,-.48,.25))
part('Vents')
for y in [.03,.065,.10,.135]:box((-.32,y,-.22),(-.315,y+.012,.22))
part('Display');box((-.325,.23,-.20),(-.315,.365,.20))
part('Indicator');box((-.33,.185,-.025),(-.318,.212,.025))
part('Input');box((-.14,-.46,-.36),(.14,-.40,-.31))
part('Output');box((-.14,-.46,.31),(.14,-.40,.36))
folder=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model/programmableups'
folder.mkdir(parents=True,exist_ok=True)
(folder/'programmableups.obj').write_text('\n'.join(lines)+'\n')
(folder/'ups.mtl').write_text('newmtl Neutral\nKd 1 1 1\nKa 1 1 1\n')
