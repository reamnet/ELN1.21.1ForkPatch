#!/usr/bin/env python3
"""Original vector control panels; regenerate only the six named equipment meshes.

Appends a deterministic panel to each existing mesh. IDs, connectors and housings stay intact.
Labels are electrical abbreviations/units; full translated operating messages are in the GUI.
No raster art or external font dependency.
"""
from pathlib import Path
from collections import defaultdict
ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/model'
MARK='# ELN live control panel v73'
FONT={
'A':['010','101','111','101','101'],'C':['011','100','100','100','011'],
'D':['110','101','101','101','110'],'E':['111','100','110','100','111'],
'F':['111','100','110','100','100'],'G':['011','100','101','101','011'],
'H':['101','101','111','101','101'],'I':['111','010','010','010','111'],
'L':['100','100','100','100','111'],'M':['10001','11011','10101','10001','10001'],
'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],
'R':['110','101','110','101','101'],'S':['011','100','010','001','110'],
'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],
'V':['101','101','101','101','010'],'W':['10001','10001','10101','10101','01010'],
'Y':['101','101','010','010','010'],'B':['110','101','110','101','110'],
'1':['010','110','010','010','111'],'2':['110','001','010','100','111'],
'3':['110','001','010','001','110'],'4':['101','101','111','001','001'],
'!':['1','1','1','0','1'],' ':['0']*5}

class Panel:
 def __init__(self,source,z):
  self.source=source.split(MARK)[0].rstrip()+'\n';self.lines=[MARK];self.index=sum(l.startswith('v ') for l in self.source.splitlines());self.z=z
 def part(self,name):self.lines+=['o '+name,'usemtl Neutral']
 def quad(self,x,y,w,h,z=None):
  z=self.z if z is None else z
  for a,b in [(x,y),(x+w,y),(x+w,y+h),(x,y+h)]:self.lines.append(f'v {a:.6f} {b:.6f} {z:.6f}')
  i=self.index+1;self.lines.extend([f'f {i} {i+1} {i+2}',f'f {i} {i+2} {i+3}']);self.index+=4
 def label(self,text,x,y,p=.0045):
  for ch in text:
   pattern=FONT[ch]
   for row,s in enumerate(pattern):
    for col,v in enumerate(s):
     if v=='1':self.quad(x+col*p,y+(4-row)*p,p*.84,p*.84,self.z+.002)
   x+=(len(pattern[0])+1)*p
 def screen(self,name,y):
  self.part(name);self.quad(-.14,y,.33,.068,self.z+.001)
 def led(self,name,x,y):
  self.part(name);self.quad(x,y,.022,.022,self.z+.003)
 def finish(self,path):
  # Obj3D indexes objects by name: repeated `o PanelInk` records would replace
  # earlier legends. Group every face of a part into one object declaration.
  vertices=[];parts=defaultdict(list);part=None
  for line in self.lines[1:]:
   if line.startswith('o '):part=line[2:]
   elif line.startswith('v '):vertices.append(line)
   elif line.startswith('f '):parts[part].append(line)
  body=[MARK]+vertices
  for name,faces in parts.items():body+=['o '+name,'usemtl Neutral']+faces
  path.write_text(self.source+'\n'.join(body)+'\n')

for name in ['solarmppt','solarhybrid','wallmppt','wallhybrid','industrial_shedder','industrial_relay']:
 path=ROOT/name/(name+'.obj');solar='solar' in name or 'wall' in name;hybrid='hybrid' in name
 source=path.read_text()
 if 'wall' in name:
  # Retire only legacy front louvres/bezel faces covered by the new panel.
  # Retain the shallow enclosure, outer trim, wall brackets and handle.
  before=source.split(MARK)[0];verts=[];kept=[];part=''
  for line in before.splitlines():
   if line.startswith('o '):part=line[2:]
   if line.startswith('v '):verts.append(tuple(map(float,line.split()[1:4])))
   if part=='Trim' and line.startswith('f '):
    points=[verts[int(v.split('/')[0])-1] for v in line.split()[1:]]
    if all(-.30<=x<=.30 and -.325<=y<=.35 and z>=-.24 for x,y,z in points):continue
   kept.append(line)
  source='\n'.join(kept)+'\n'
 p=Panel(source,-.173 if 'wall' in name else .269 if solar else .259)
 p.part('PanelFrame');p.quad(-.285,-.325,.57,.675,p.z-.002)
 p.part('PanelFace');p.quad(-.27,-.31,.54,.645)
 p.part('PanelAccent');p.quad(-.27,.313,.54,.022,p.z+.001)
 p.part('PanelInk');p.label('HYBRID' if hybrid else 'MPPT' if solar else 'SHED' if 'shedder' in name else 'RELAY',-.23,.272)
 if solar:
  for title,y in [('PV W',.153),('GRID W' if hybrid else 'FEED W',.044),('OUT V',-.065)]:p.label(title,-.235,y+.024,p=.0032)
  for i,y in enumerate([.14,.031,-.078]):p.screen('Readout'+str(i),y)
  for i,(label,x) in enumerate([('PV',-.23),('GRID',-.10),('OUT',.04),('!',.19)]):
   p.part('PanelInk');p.label(label,x,-.188,p=.0035);p.led('StatusLed'+str(i),x,-.152)
  p.part('LinkTrack');p.quad(-.22,-.267,.44,.018,p.z+.001)
  p.part('PanelInk');p.label('DC',-.23,-.303,p=.0035)
 else:
  p.part('PanelInk');p.label('V',-.23,.195);p.label('W' if 'shedder' in name else 'A',-.23,.102)
  for i,y in enumerate([.176,.083]):p.screen('Readout'+str(i),y)
  labels=['1','2','3','4'] if 'shedder' in name else ['UV','OV','OC','DIFF']
  for i,label in enumerate(labels):
   y=-.015-i*.073;p.part('PanelInk');p.label(label,-.23,y,p=.004)
   p.part('BranchTrack'+str(i));p.quad(-.13,y,.29,.022,p.z+.001);p.led('ChannelLed'+str(i),.20,y)
  p.part('PanelInk');p.label('P1' if 'shedder' in name else 'TRIP',-.23,-.303,p=.0035)
 p.finish(path)
