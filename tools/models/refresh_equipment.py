#!/usr/bin/env python3
"""Reproducible, original equipment finishes and construction details for the 3.0 additions.

Reads the unmodified meshes at the manifest's base tag; never repeatedly bevels its own output.
No downloaded art or geometry, and no change to the part names that animate/connect equipment.
"""
from pathlib import Path
from collections import defaultdict
from math import sin, cos, pi, sqrt
import argparse
import hashlib
import json
import random
import subprocess
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/eln'
MANIFEST = json.loads(Path(__file__).with_name('refresh-manifest.json').read_text())
SURFACES = ['paint', 'metal', 'ceramic', 'fabric', 'wood', 'glass', 'rubber', 'reflector',
            'screw', 'warning', 'label', 'vent', 'concrete', 'coil', 'stone', 'enamel']
TEXTURE = 'eln:textures/block/equipment_finishes.png'
LEGACY_TEXTURED = {'powerpole/utilitypoletall.obj', 'transmissiontower/transmissiontowertall.obj'}


def atlas():
    rng = random.Random(67068)
    image = Image.new('RGB', (256, 256))
    for index, surface in enumerate(SURFACES):
        tile = Image.new('RGB', (64, 64), 'white')
        d = ImageDraw.Draw(tile)
        for y in range(64):
            for x in range(64):
                value = 246 + rng.randrange(-3, 4)
                if surface == 'metal': value = 225 + rng.randrange(-7, 8) + (y % 3) * 4
                if surface == 'paint': value = 245 + rng.randrange(-3, 4)
                if surface == 'ceramic': value = 237 + round(14 * sin(x / 64 * pi))
                if surface == 'fabric': value = 210 + (x % 3 == 0) * 18 + (y % 3 == 0) * 12 + rng.randrange(5)
                if surface == 'wood': value = 205 + round(17 * sin(x * .5 + sin(y * .08) * .8)) + rng.randrange(-4, 5)
                if surface == 'glass': value = 230 + round(18 * cos((x + y) * .035))
                if surface == 'rubber': value = 235 + rng.randrange(-5, 5)
                if surface == 'reflector': value = 218 + (x % 8 < 3) * 25 + (y % 8 < 3) * 10
                if surface == 'vent': value = 80 if y % 9 < 4 else 225
                if surface == 'concrete': value = 220 + rng.randrange(-15, 15)
                if surface == 'stone': value = 228 + rng.randrange(-12, 13)
                if surface == 'coil': value = 214 + (y % 8 < 3) * 35
                if surface == 'enamel': value = 234 + round(16 * sin(x / 64 * pi))
                value = max(0, min(255, value))
                tile.putpixel((x, y), (value, value, value))
        if surface == 'screw':
            d.rectangle((0, 0, 63, 63), fill='#6b7376')
            d.ellipse((4, 4, 59, 59), fill='#d9dfe0', outline='#939da0', width=3)
            d.line((20, 20, 44, 44), fill='#525b5d', width=5)
            d.line((20, 44, 44, 20), fill='#525b5d', width=5)
        if surface == 'warning':
            d.rectangle((0, 0, 63, 63), fill='#eabf36')
            d.rectangle((2, 2, 61, 61), outline='#252a2e', width=2)
            d.polygon([(32, 9), (8, 48), (55, 48)], fill='#292e30')
            d.polygon([(34, 16), (23, 32), (31, 32), (28, 43), (42, 26), (33, 26)], fill='#efd64f')
        if surface == 'label':
            d.rectangle((0, 0, 63, 63), fill='#dce0da')
            d.rectangle((2, 2, 61, 61), outline='#646d6c', width=2)
            d.rectangle((6, 7, 57, 16), fill='#6a7678')
            for y, end in [(23, 49), (29, 54), (35, 36), (45, 52), (51, 42)]:
                d.line((8, y, end, y), fill='#798382', width=2)
        image.paste(tile, ((index % 4) * 64, (index // 4) * 64))
    target = ASSETS / 'textures/block/equipment_finishes.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


class Model:
    def __init__(self, source):
        self.parts = defaultdict(list)
        self.original = source
        vertices = []
        part = None
        for line in source.splitlines():
            if line.startswith('v '): vertices.append(tuple(map(float, line.split()[1:4])))
            elif line.startswith('o '): part = line[2:].strip()
            elif line.startswith('f '):
                ids = [int(i.split('/')[0]) for i in line.split()[1:]]
                self.parts[part].append(([vertices[i - 1 if i > 0 else i] for i in ids], None, False))
        self.before = sum(len(v) for v in self.parts.values())

    def bounds(self, part):
        points = [p for face, _, _ in self.parts[part] for p in face]
        return tuple(min(p[i] for p in points) for i in range(3)), tuple(max(p[i] for p in points) for i in range(3))

    def box(self, part, a, b, material=None):
        x, y, z = a; X, Y, Z = b
        p = [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        for ids in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:
            self.parts[part].append(([p[i] for i in ids], material, True))

    def plate(self, part, center, width, height, material='label'):
        x, y, z = center
        self.parts[part].append(([(x-width/2,y-height/2,z),(x+width/2,y-height/2,z),
                                  (x+width/2,y+height/2,z),(x-width/2,y+height/2,z)], material, True))

    def cyl(self, part, a, b, radius, material=None, n=12, end_radius=None):
        axis = [b[i] - a[i] for i in range(3)]; length = sqrt(sum(x*x for x in axis)); axis = [x / length for x in axis]
        def cross(u,v): return (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        right = cross(axis, (0,1,0) if abs(axis[1]) < .9 else (1,0,0))
        length = sqrt(sum(x*x for x in right)); right = [x / length for x in right]; up = cross(axis, right)
        rings = [[tuple(p[k]+r*(right[k]*cos(2*pi*i/n)+up[k]*sin(2*pi*i/n)) for k in range(3)) for i in range(n)] for p,r in [(a,radius),(b,radius if end_radius is None else end_radius)]]
        for i in range(n):
            j=(i+1)%n; self.parts[part].append(([rings[0][i],rings[0][j],rings[1][j],rings[1][i]],material,True))
        self.parts[part].append((list(reversed(rings[0])),material,True));self.parts[part].append((rings[1],material,True))

    def bevel_boxes(self, names):
        count = 0
        for name in names:
            if name not in self.parts: continue
            faces = self.parts[name]; out=[]; i=0
            while i<len(faces):
                group=faces[i:i+12]
                points={p for face,_,_ in group for p in face}
                lo=tuple(min(p[k] for p in points) for k in range(3)); hi=tuple(max(p[k] for p in points) for k in range(3))
                dims=[hi[k]-lo[k] for k in range(3)]
                corners={(x,y,z) for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]}
                valid=len(group)==12 and all(len(f)==3 and not detail for f,_,detail in group) and points==corners and min(dims)>.095
                # Reject triangles covering only part of the box or any internal diagonals/planes.
                valid=valid and len({tuple(sorted(f)) for f,_,_ in group})==12 and all(any(all(abs(p[k]-side)<1e-7 for p in f) for k in range(3) for side in [lo[k],hi[k]]) for f,_,_ in group)
                if not valid: out.append(faces[i]);i+=1;continue
                bevel=min(.008,min(dims)*.045)
                # Six inset faces, twelve edge strips and eight triangular corner caps.
                for axis in range(3):
                    others=[k for k in range(3) if k!=axis]
                    for side in [0,1]:
                        ring=[]
                        for u,v in [(0,0),(1,0),(1,1),(0,1)]:
                            p=[0.,0.,0.];p[axis]=(lo,hi)[side][axis]
                            for k,s in zip(others,[u,v]):p[k]=(lo,hi)[s][k]+(bevel if s==0 else -bevel)
                            ring.append(tuple(p))
                        n=normal(ring)
                        if n[axis]*(1 if side else -1)<0:ring.reverse()
                        out.append((ring,None,False))
                for axis in range(3):
                    a,b=[k for k in range(3) if k!=axis]
                    for sa in [0,1]:
                        for sb in [0,1]:
                            ring=[]
                            for along,which in [(0,0),(1,0),(1,1),(0,1)]:
                                p=[0.,0.,0.];p[axis]=(lo,hi)[along][axis]+(bevel if along==0 else -bevel)
                                p[a]=(lo,hi)[sa][a]+((bevel if sa==0 else -bevel) if which else 0)
                                p[b]=(lo,hi)[sb][b]+((bevel if sb==0 else -bevel) if not which else 0)
                                ring.append(tuple(p))
                            n=normal(ring)
                            if n[a]*(1 if sa else -1)+n[b]*(1 if sb else -1)<0:ring.reverse()
                            out.append((ring,None,False))
                for sx in [0,1]:
                    for sy in [0,1]:
                        for sz in [0,1]:
                            signs=[sx,sy,sz];ring=[]
                            for axis in range(3):
                                p=[(lo,hi)[signs[k]][k]+(0 if k==axis else (bevel if signs[k]==0 else -bevel)) for k in range(3)]
                                ring.append(tuple(p))
                            n=normal(ring)
                            if sum(n[k]*(1 if signs[k] else -1) for k in range(3))<0:ring.reverse()
                            out.append((ring,None,False))
                count+=1;i+=12
            self.parts[name]=out
        return count


def normal(face):
    a=[face[1][k]-face[0][k] for k in range(3)];b=[face[2][k]-face[0][k] for k in range(3)]
    return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])


def surface_for(name, part):
    p=part.lower()
    if any(s in p for s in ['light','lens','glass','display','screen','indicator','led','heat','warningmark']):return None
    if any(s in p for s in ['porcelain','ceramic','insulator','pin']):return 'ceramic'
    if p=='shade':return 'enamel' if 'banker' in name else 'fabric'
    if p in ['black','vents','socket120','socket240','wheel','elbows'] or 'cable' in p:return 'rubber'
    if p in ['wood','cargo'] or name=='distributionpole' and p in ['main','crossarm','singlearm']:return 'wood'
    if 'stone' in name and p in ['base','metal']:return 'stone'
    if 'fireplace' in name and p in ['case','base']:return 'wood'
    if p in ['pad','concrete']:return 'concrete'
    if p=='rotor' and 'watermill' in name:return 'wood'
    if p in ['trim','terminal','lugs','knifein','knifeout','fork','ground','doorhardware'] or 'bracket' in p:return 'metal'
    if p in ['input','output','solar','grid','return'] or p.startswith('port'):return 'rubber'
    return 'paint'


def cabinet_details(m, name):
    if name in ['programmableups','compactups','wallups','automatictransferswitch','wallats']:return 0
    selected=(name.startswith(('kitchen','standbygenerator','industrial_','wallmppt','wallhybrid','solarmppt','solarhybrid')) or name in ['electricwasher','electricdryer','portablespaceheater','wallminisplit','windowairconditioner','fusionmatterreactor','growingbed','ceepowerbridge','twelvecircuitpanel'])
    if not selected or 'Case' not in m.parts:return 0
    lo,hi=m.bounds('Case');x,y,z=lo;X,Y,Z=hi;w=X-x;h=Y-y;depth=Z-z
    if min(w,h)<.22:return 0
    part='Trim' if 'Trim' in m.parts else 'Case'
    # Cabinet screws, folded corner trim and a real side ventilation panel. Keep the original
    # front clear: screens, controls and sockets already occupy that face.
    for px in [x+w*.06,X-w*.06]:
        for py in [y+h*.065,Y-h*.065]:m.plate(part,(px,py,Z+.003),min(.023,w*.035),min(.023,h*.035),'screw')
    if h>.55:
        for py in [y+h*.16,y+h*.75]:
            m.box(part,(x-.004,py,z+depth*.26),(x+.012,py+min(.06,h*.08),z+depth*.50),'metal')
        if name not in ['kitchenfridge','kitchenminifridge']:
            m.box(part,(X-w*.055,y+h*.40,Z+.002),(X-w*.035,y+h*.57,Z+.022),'rubber')
    # Separate folded seams read at ordinary distances without dense per-frame geometry.
    m.box(part,(x-.002,y+h*.045,Z+.001),(X+.002,y+h*.045+.006,Z+.005),'rubber')
    if depth>.22:
        for row in range(5):
            py=y+h*.18+row*min(.038,h*.045)
            m.box(part,(X+.001,py,z+depth*.20),(X+.004,py+.009,z+depth*.70),'rubber')
    if h>.55 and w>.42:
        m.plate(part,(x+w*.18,y+h*.13,Z+.006),min(w*.17,.12),min(h*.085,.065),'label')
    return 1


def pole_details(m, name):
    if name=='distributionpole':
        # Hardware follows the same parts/rotations as the existing arm and pole.
        for y in [.10,.25]:
            m.box('main',(-.17,y,-.17),(.17,y+.025,.17),'metal')
        for p in ['Crossarm','SingleArm']:
            if p not in m.parts:continue
            lo,hi=m.bounds(p)
            for z in [lo[2]+.14,hi[2]-.14]:
                m.box(p,(hi[0]+.001,(lo[1]+hi[1])*.5-.024,z-.024),(hi[0]+.012,(lo[1]+hi[1])*.5+.024,z+.024),'metal')
        m.plate('main',(.005,2.08,.153),.105,.065,'label')
        return 1
    if name.startswith(('steeltransmission','steelriser')):
        # Segmented galvanized mast collars and base anchors; no new collision extents.
        for y in [.10,.20]:m.box('main',(-.23,y,-.23),(.23,y+.025,.23),'metal')
        for x in [-.20,.20]:
            for z in [-.20,.20]:m.cyl('main',(x,-.44,z),(x,-.38,z),.024,'metal',8)
        m.plate('main',(0,1.7,.255),.18,.12,'warning')
        return 1
    return 0


def renewable_details(m, name):
    if name=='utilitywind':
        m.parts['Case']=[];m.parts['Rotor']=[]
        m.box('Case',(-.44,-.50,-.44),(.44,0,.44),'concrete')
        m.cyl('Case',(0,0,0),(0,5.0,0),.23,n=20,end_radius=.16)
        m.cyl('Case',(0,5.0,0),(0,9.5,0),.16,n=20,end_radius=.105)
        for y,r in [(0,.26),(5,.18)]:
            m.cyl('Trim',(0,y,0),(0,y+.045,0),r,'metal',20)
        m.box('Case',(-.36,9.22,-1.0),(.36,9.82,.64))
        m.cyl('Case',(0,9.5,.55),(0,9.5,.86),.24,n=20)
        for y in [9.32,9.39,9.46,9.53]:m.box('Trim',(-.365,y,-.75),(-.36,y+.018,-.28),'rubber')
        # Rotor coordinates remain relative to the existing (0, 9.5, .8) animated pivot.
        m.cyl('Rotor',(0,0,-.16),(0,0,.23),.25,n=20,end_radius=.07)
        stations=[(.20,.19,.13),(.65,.43,.13),(1.4,.47,.105),(2.5,.37,.075),(3.7,.23,.045),(4.5,.055,.02)]
        for blade in range(3):
            angle=blade*2*pi/3;rings=[]
            for radius,chord,thickness in stations:
                twist=(18-15*radius/4.5)*pi/180;ring=[]
                # Rounded leading edge and tapered trailing edge, swept/twisted along the blade.
                profile=[(u,s) for u in [1,.8,.5,.25,.06,0] for s in [1]]+[(u,-1) for u in [.06,.25,.5,.8,1]]
                for u,sign in profile:
                    t=5*thickness*(.2969*sqrt(u)-.126*u-.3516*u*u+.2843*u**3-.1015*u**4)*sign
                    px=(u-.38)*chord+.09*(radius/4.5)**2;pz=t*chord
                    x=px*cos(twist)+pz*sin(twist);z=-px*sin(twist)+pz*cos(twist)
                    ring.append((x*cos(angle)-radius*sin(angle),x*sin(angle)+radius*cos(angle),z))
                rings.append(ring)
            for first,last in zip(rings,rings[1:]):
                for i in range(len(first)):
                    j=(i+1)%len(first);m.parts['Rotor'].append(([first[i],last[i],last[j],first[j]],None,False))
            m.parts['Rotor'].append((list(reversed(rings[0])),None,False));m.parts['Rotor'].append((rings[-1],None,False))
    elif name=='utilitywatermill':
        m.parts['Rotor']=[]
        for z in [-.42,.32]:
            # Separate inner/outer rim faces and a galvanized retaining strip.
            for i in range(48):
                a=i*2*pi/48;b=(i+1)*2*pi/48
                point=lambda r,t,d:(r*cos(t),r*sin(t),d)
                for r0,r1,d0,d1 in [(3.64,3.83,z,z+.08)]:
                    faces=[ [point(r0,a,d0),point(r0,b,d0),point(r1,b,d0),point(r1,a,d0)],
                            [point(r1,a,d1),point(r1,b,d1),point(r0,b,d1),point(r0,a,d1)],
                            [point(r1,a,d0),point(r1,b,d0),point(r1,b,d1),point(r1,a,d1)],
                            [point(r0,b,d0),point(r0,a,d0),point(r0,a,d1),point(r0,b,d1)] ]
                    m.parts['Rotor'].extend((f,None,False) for f in faces)
        for count,width,low,high,depth in [(20,.95,3.70,3.98,.5),(8,.12,.20,3.69,.32)]:
            for i in range(count):
                start=len(m.parts['Rotor']);m.box('Rotor',(-width/2,low,-depth),(width/2,high,depth))
                angle=i*2*pi/count
                m.parts['Rotor'][start:]=[([(x*cos(angle)-y*sin(angle),x*sin(angle)+y*cos(angle),z) for x,y,z in f],material,detail) for f,material,detail in m.parts['Rotor'][start:]]
        m.cyl('Rotor',(0,0,-.59),(0,0,.59),.27,'metal',20)
        for i in range(8):
            a=i*pi/4;m.cyl('Rotor',(.19*cos(a),.19*sin(a),.60),(.19*cos(a),.19*sin(a),.62),.022,'metal',8)


def fridge(mini):
    m=Model('');m.parts.clear()
    x=.34 if mini else .43;y=-.46 if mini else -.48;Y=.32 if mini else 1.35;z=-.30 if mini else -.39;Z=.30 if mini else .37
    # A hollow, insulated body. Opening the door reveals shelves instead of a solid front cube.
    for a,b in [((-x,y,z),(x,Y,z+.045)),((-x,y,z),(x,y+.05,Z)),((-x,Y-.05,z),(x,Y,Z)),
                ((-x,y,z),(-x+.045,Y,Z)),((x-.045,y,z),(x,Y,Z))]:m.box('Case',a,b)
    for height in ([y+.31,y+.56] if mini else [y+.40,y+.82,y+1.26]):
        m.box('Case',(-x+.05,height,z+.05),(x-.05,height+.012,Z-.035),'enamel')
        m.box('Trim',(-x+.055,height-.002,Z-.055),(x-.055,height+.014,Z-.036),'metal')
    # Recessed dark gasket and shallow two-piece door; all door details share one pivot.
    for a,b in [((-x+.025,y+.025,Z),(-x+.05,Y-.025,Z+.006)),((x-.05,y+.025,Z),(x-.025,Y-.025,Z+.006)),
                ((-x+.025,y+.025,Z),(x-.025,y+.045,Z+.006)),((-x+.025,Y-.045,Z),(x-.025,Y-.025,Z+.006))]:m.box('Black',a,b)
    m.box('Door',(-x+.025,y+.025,Z+.007),(x-.025,Y-.025,Z+.045),'enamel')
    if not mini:m.box('DoorBlack',(-x+.032,.72,Z+.045),(x-.032,.733,Z+.048),'rubber')
    hx=x-.14
    for a,b in [((hx,y+.35,Z+.045),(hx+.035,y+(.68 if mini else .88),Z+.09)),
                ((hx-.01,y+.35,Z+.043),(hx+.045,y+.38,Z+.075)),((hx-.01,y+(.65 if mini else .85),Z+.043),(hx+.045,y+(.68 if mini else .88),Z+.075))]:m.box('DoorTrim',a,b,'metal')
    if not mini:m.box('DoorTrim',(hx,.91,Z+.045),(hx+.035,1.20,Z+.09),'metal')
    for py in [y+.10,Y-.15]:m.box('Trim',(-x-.002,py,Z-.035),(-x+.035,py+.08,Z+.03),'metal')
    for px in [-x+.08,x-.08]:
        for pz in [z+.07,Z-.07]:m.cyl('Black',(px,-.50,pz),(px,y,pz),.025,'rubber')
    for i in range(8):m.box('Black',(-x+.07+i*(2*x-.14)/8,y+.07,z-.004),(-x+.09+i*(2*x-.14)/8,y+.24,z+.002),'rubber')
    # Thermostat inside, so the live/open door does not leave a floating screen behind it.
    m.box('Display',(-.11,Y-.18,z+.046),(.11,Y-.09,z+.051))
    return m


def save(m, relative):
    name=Path(relative).stem;folder=ASSETS/'model'/Path(relative).parent
    lines=['# Original 3.0.68 equipment refresh; generated by tools/models/refresh_equipment.py','mtllib equipment_finish.mtl']
    used=set();idx=1;uvindex=1
    for part,faces in m.parts.items():
        lines.append('o '+part);current=None
        vertex_ids={};uv_ids={}
        for face,explicit,detail in faces:
            surface=explicit or surface_for(name,part)
            material=('eln_detail_' if detail and explicit else 'eln_finish_')+surface if surface else 'Neutral'
            if material!=current:lines.append('usemtl '+material);current=material;used.add(material)
            # Each polygon has planar UVs, so seams do not split the two triangles of a box face.
            n=normal(face);axis=max(range(3),key=lambda k:abs(n[k]));axes=[k for k in range(3) if k!=axis]
            bounds=[(min(p[k] for p in face),max(p[k] for p in face)) for k in axes]
            tile=SURFACES.index(surface) if surface else 0;column=tile%4;row=tile//4
            vertex_keys=[];uv_keys=[]
            for p in face:
                if name in ['wallups','wallats','wallmppt','wallhybrid']:
                    p=(max(-.46,min(.46,p[0])),max(-.46,min(.46,p[1])),max(-.5,min(-.17,p[2])))
                key=tuple(round(c,6) for c in p);vertex_keys.append(key)
                if key not in vertex_ids:
                    vertex_ids[key]=idx;idx+=1;lines.append('v %.6f %.6f %.6f'%key)
            for p in face:
                coords=[(p[k]-low)/(high-low) if high-low>1e-12 else .5 for k,(low,high) in zip(axes,bounds)]
                # Half-texel interior prevents neighboring finishes bleeding through mipmaps.
                u=(column*64+4+coords[0]*56)/256;v=1-(row*64+4+(1-coords[1])*56)/256
                key=(round(u,7),round(v,7));uv_keys.append(key)
                if key not in uv_ids:
                    uv_ids[key]=uvindex;uvindex+=1;lines.append('vt %.7f %.7f'%key)
            for i in range(1,len(face)-1):lines.append('f '+' '.join('%d/%d'%(vertex_ids[vertex_keys[j]],uv_ids[uv_keys[j]]) for j in [0,i,i+1]))
    folder.mkdir(parents=True,exist_ok=True);target=folder/Path(relative).name;target.write_text('\n'.join(lines)+'\n')
    materials=['newmtl Neutral','Kd 1 1 1']
    for material in sorted(used-{'Neutral'}):
        surface=material.removeprefix('eln_finish_').removeprefix('eln_detail_')
        # Explicit detail materials restore their own tint only; ordinary surfaces keep the
        # descriptor's colors, including voltage markings and switched-off lenses.
        tint={'metal':(.70,.74,.76),'rubber':(.075,.085,.095),'enamel':(.78,.80,.81)}.get(surface,(1.,1.,1.))
        materials+=['newmtl '+material,'Kd %.3f %.3f %.3f'%tint,'map_Kd '+TEXTURE]
    (folder/'equipment_finish.mtl').write_text('\n'.join(materials)+'\n')
    return {'model':relative,'originalFaces':m.before,'faces':sum(max(1,len(f)-2) for fs in m.parts.values() for f,_,_ in fs),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.check:
        errors=[]
        for path in MANIFEST['models']:
            if path in LEGACY_TEXTURED:continue
            p=ASSETS/'model'/path
            if 'equipment_finish.mtl' not in p.read_text():errors.append(path)
        if errors:raise SystemExit('Unrefreshed models: '+', '.join(errors))
        print('%d equipment meshes checked; %d retain their original detailed textures.'%(len(MANIFEST['models']),len(LEGACY_TEXTURED)));return
    atlas();report=[]
    for path in MANIFEST['models']:
        source=subprocess.check_output(['git','show',MANIFEST['base_ref']+':src/main/resources/assets/eln/model/'+path],cwd=ROOT,text=True)
        name=Path(path).stem
        if path in LEGACY_TEXTURED:
            report.append({'model':path,'retainedLegacyTexture':True,'beveledBoxes':0})
            continue
        m=fridge(name=='kitchenminifridge') if name in ['kitchenfridge','kitchenminifridge'] else Model(source)
        renewable_details(m,name)
        beveled=m.bevel_boxes(['Case','Door','Base','Body','Transformer','main','Metal'])
        cabinets=cabinet_details(m,name);poles=pole_details(m,name)
        report.append(save(m,path)|{'beveledBoxes':beveled,'cabinetDetailPass':bool(cabinets),'poleDetailPass':bool(poles)})
    out=ROOT/'docs/verification/release-3.0.68';out.mkdir(parents=True,exist_ok=True)
    (out/'model-refresh.json').write_text(json.dumps({'models':report,'meshCount':len(report),'beveledBoxes':sum(r['beveledBoxes'] for r in report)},indent=2)+'\n')
    print('Refreshed %d original meshes; %d beveled equipment sections.'%(len(report),sum(r['beveledBoxes'] for r in report)))


if __name__=='__main__':main()
