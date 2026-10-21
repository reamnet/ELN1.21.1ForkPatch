"""Split fixed pole geometry from swivelling light heads without changing any vertices."""
from collections import defaultdict

def split_heads(text, name):
    config={'twinparkinglight':(5.12,2),'modernledstreetlight':(5.30,1),'boulevardlanternstreetlight':(4.58,2)}
    if name not in config:return text
    threshold,heads=config[name]
    vertices=[];parts=defaultdict(list);part='';material='usemtl Neutral';mtl='mtllib fixture.mtl'
    for line in text.splitlines():
        if line.startswith('v '):vertices.append(line)
        elif line.startswith('o '):part=line[2:]
        elif line.startswith('usemtl '):material=line
        elif line.startswith('mtllib '):mtl=line
        elif line.startswith('f '):
            ids=[int(v.split('/')[0])-1 for v in line.split()[1:]]
            points=[tuple(map(float,vertices[i].split()[1:])) for i in ids]
            target=part
            if part in ['Metal','Trim'] and min(v[0] for v in points)>=threshold:
                head=0 if heads==1 else int(sum(v[1] for v in points)>0)
                target=f'Head{head}'+part
            parts[(target,material)].append(ids)
    out=['# Separate adjustable heads; all vertices remain in their owning OBJ part.',mtl];index=1
    for (part,material),faces in parts.items():
        out+=['o '+part,material]
        for face in faces:
            out+=[vertices[v] for v in face]
            out+=['f '+' '.join(str(index+i) for i in range(len(face)))];index+=len(face)
    return '\n'.join(out)+'\n'
