#!/usr/bin/env python3
"""Render original 32 px ELN-style catalog sprites from native descriptor mesh exports.

This is an offline asset tool, not a new item renderer. The resulting icons are ordinary baked
quads; held/dropped objects still use their complete models. No Blender or downloaded art needed.
"""
import argparse
import gzip
import json
import math
from pathlib import Path
import struct
import hashlib
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/eln'
SCALE=4
SIZE=32*SCALE


def float_bits(i): return struct.unpack('!f',struct.pack('!I',i&0xffffffff))[0]


def render(item):
    # Ceiling fittings are photographed from below so their lenses/bulbs are identifiable.
    ceiling=any(s.lower() in item['name'].lower() for s in ['chandelier','ceiling','fluorescent','recessed','dot spotlight','mini twin dot','LED strip','LED channel','downlight','suspended'])
    ry=math.radians(-35);rx=math.radians(-20 if ceiling else 20)
    Y=np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
    X=np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])
    matrix=X@Y
    batches=[]
    for b in item['batches']:
        raw=np.array(b['vertices'],dtype=np.int64).reshape((-1,9))
        floats=(raw[:,:8]&0xffffffff).astype(np.uint32).view(np.float32)
        p=floats[:,:3]@matrix.T;n=floats[:,5:8]@matrix.T
        color=np.stack([((raw[:,8]>>s)&255)/255 for s in [16,8,0,24]],axis=-1)
        # ELN's legacy lamp sprites illustrate the bulb, even though fixtures are crafted empty.
        # Preview meshes use this exact neutral off-lens tint; brighten it in catalog art only.
        lens=np.all(np.abs(color[:,:3]-np.array([46,54,61])/255)<1.1/255,axis=1)
        title=item['name'].lower()
        lamp_color=[.82,.85,.83]
        if any(s in title for s in ['warm white','festoon','chandelier','candle']):lamp_color=[.97,.83,.55]
        if 'icicle' in title:lamp_color=[.65,.81,.96]
        if 'christmas star' in title:lamp_color=[.98,.80,.22]
        if 'halloween bat' in title:lamp_color=[.62,.28,.90]
        if 'halloween pumpkin' in title:lamp_color=[1.,.40,.055]
        color[lens,:3]=np.array(lamp_color)
        if 'multicolor' in title and np.any(lens):
            positions=floats[lens,:3];axis=np.argmax(np.ptp(positions,axis=0));low=positions[:,axis].min();span=np.ptp(positions[:,axis])
            bins=np.clip(((positions[:,axis]-low)/max(span,1e-6)*6).astype(int),0,5)
            palette=np.array([[.98,.16,.09],[.17,.87,.25],[.20,.40,1.],[1.,.77,.10],[.84,.22,.90],[.18,.86,.65]])
            color[lens,:3]=palette[bins]
        texture=b['texture']
        if texture=='eln:textures/white.png' or texture.endswith('terrain_white.png'):tex=np.ones((1,1,4))
        else:
            namespace,path=texture.split(':',1);file=ROOT/'src/main/resources/assets'/namespace/path
            tex=np.array(Image.open(file).convert('RGBA'),dtype=np.float64)/255 if file.exists() else np.ones((1,1,4))
        batches.append((p,n,floats[:,3:5],color,tex,b))
    points=np.concatenate([b[0] for b in batches]);lo=points.min(axis=0);hi=points.max(axis=0)
    scale=29*SCALE/max(hi[0]-lo[0],hi[1]-lo[1],1e-9);center=(hi+lo)/2
    canvas=np.zeros((SIZE,SIZE,4),dtype=np.float64);zbuffer=np.full((SIZE,SIZE),-np.inf)
    triangles=[];light=np.array([-.4,.7,.65]);light/=np.linalg.norm(light)
    for p,n,uv,color,tex,b in batches:
        p=(p-center)*scale;p[:,0]+=SIZE/2;p[:,1]=SIZE/2-p[:,1]
        for offset in range(0,len(p),4):
            for ids in [[offset,offset+1,offset+2],[offset,offset+2,offset+3]]:
                normal=n[ids].mean(axis=0)
                if b['cull'] and normal[2]<-1e-7:continue
                triangles.append((p[ids],uv[ids],color[ids],tex,b['blend'],normal,b['unlit']))
    # Solid depth first; transparent glass is sorted far-to-near and never hides opaque fittings.
    triangles.sort(key=lambda t:(t[4],t[0][:,2].mean() if t[4] else 0))
    for p,uv,rgba,tex,blend,normal,unlit in triangles:
        x0=max(0,int(np.floor(p[:,0].min())));x1=min(SIZE,int(np.ceil(p[:,0].max())))
        y0=max(0,int(np.floor(p[:,1].min())));y1=min(SIZE,int(np.ceil(p[:,1].max())))
        if x0>=x1 or y0>=y1:continue
        a,b,c=p
        determinant=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(determinant)<1e-10:continue
        yy,xx=np.mgrid[y0:y1,x0:x1];xx=xx+.5;yy=yy+.5
        u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/determinant
        v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/determinant;w=1-u-v
        z=u*a[2]+v*b[2]+w*c[2]
        mask=(u>=-1e-8)&(v>=-1e-8)&(w>=-1e-8)&(z>=zbuffer[y0:y1,x0:x1]-1e-7)
        if not np.any(mask):continue
        weights=np.stack([u,v,w],axis=-1)
        coord=weights@uv
        tx=np.clip((coord[:,:,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1)
        ty=np.clip((coord[:,:,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
        color=(weights@rgba)*tex[ty,tx]
        shade=1.0 if unlit else .72+.28*max(0,np.dot(normal,light))
        color[:,:,:3]*=shade
        color[:,:,:3]=np.minimum(1,color[:,:,:3]*1.08+.025)
        alpha=color[:,:,3];mask&=alpha>.01
        dst=canvas[y0:y1,x0:x1]
        olda=dst[:,:,3];newa=alpha+olda*(1-alpha)
        mixed=(color[:,:,:3]*alpha[:,:,None]+dst[:,:,:3]*olda[:,:,None]*(1-alpha[:,:,None]))/np.maximum(newa[:,:,None],1e-10)
        dst[:,:,:3][mask]=mixed[mask];dst[:,:,3][mask]=newa[mask]
        if not blend:zbuffer[y0:y1,x0:x1][mask]=z[mask]
    large=Image.fromarray(np.round(np.clip(canvas,0,1)*255).astype('uint8'),'RGBA')
    # Tall 8–20 m structures otherwise have sub-pixel stems at icon scale. A quarter-pixel
    # catalog-art expansion keeps them as readable as the older, shorter ELN pole sprites.
    if any(s in item['name'].lower() for s in ['pole','tower','parking','sidewalk','street light','lantern post']):large=large.filter(ImageFilter.MaxFilter(3))
    result=large.resize((32,32),Image.Resampling.BOX)
    if result.getbbox() is None:raise ValueError('Empty icon for '+item['name'])
    return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('export',type=Path);args=parser.parse_args()
    with gzip.open(args.export,'rt') as f:items=json.load(f)
    target=ASSETS/'textures/blocks/catalog';target.mkdir(parents=True,exist_ok=True)
    rows=[];out=ROOT/'docs/verification/release-3.0.68';out.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGB',(960,math.ceil(len(items)/8)*100),'#555c60');draw=ImageDraw.Draw(sheet)
    for i,(icon,item) in enumerate(items.items()):
        image=render(item);path=target/(icon+'.png');path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
        rows.append({'item':item['id'],'icon':str(path.relative_to(ASSETS)),'width':32,'height':32,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        x=(i%8)*120;y=(i//8)*100
        big=image.resize((64,64),Image.Resampling.NEAREST);sheet.paste(big,(x+28,y+2),big)
        title=item['name'].replace('Utility ','').replace('Light','Lt.').replace('Chandelier','Chand.')
        draw.text((x+3,y+68),title[:19],fill='white')
        draw.text((x+3,y+80),title[19:38],fill='#aebcc1')
    (out/'catalog-icons.json').write_text(json.dumps({'count':len(rows),'icons':rows},indent=2)+'\n')
    sheet.save(out/'catalog-contact-sheet.png')
    print('Rendered %d legacy-style catalog sprites.'%len(rows))


if __name__=='__main__':main()
