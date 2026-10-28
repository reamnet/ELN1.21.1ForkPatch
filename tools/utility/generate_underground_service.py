#!/usr/bin/env python3
"""Original US residential underground service equipment and instrument faces."""
from pathlib import Path
import sys,math
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'lighting'))
from generate_fans_and_outlet import Mesh,root,ring
from PIL import Image,ImageDraw,ImageFont

def rounded_top(m,part,x,y,z,w,h,d,r=.06,caps=True):
 profile=[(x,y),(x+w,y),(x+w,y+h-r)]
 profile += [(x+w-r+r*math.cos(a),y+h-r+r*math.sin(a)) for a in [i*math.pi/16 for i in range(1,9)]]
 profile += [(x+r,y+h)]
 profile += [(x+r+r*math.cos(a),y+h-r+r*math.sin(a)) for a in [math.pi/2+i*math.pi/16 for i in range(1,9)]]
 a=[(px,py,z) for px,py in profile];b=[(px,py,z+d) for px,py in profile]
 if caps:m.poly(part,list(reversed(a)));m.poly(part,b)
 for i in range(len(a)):
  j=(i+1)%len(a);m.poly(part,[a[i],a[j],b[j],b[i]])

def pad_transformer(m):
 m.box('Pad',-.49,-.50,-.43,.98,.06,.91)
 rounded_top(m,'Case',-.44,-.44,-.36,.88,.86,.77,caps=False)
 rounded_top(m,'Case',-.40,-.36,-.01,.80,.74,.38,r=.04)
 m.box('Case',-.44,-.44,-.36,.88,.08,.77) # continuous closed bottom skirt
 rounded_top(m,'Door',-.405,-.40,-.381,.81,.73,.025,r=.035)
 m.box('DoorHardware',-.009,-.38,-.385,.018,.65,.006)
 for y in [-.27,.15]:m.cyl('Trim',1,(-.415,y,-.368),.07,.012,n=12)
 m.box('DoorHardware',.295,-.13,-.399,.035,.14,.016)
 m.box('Nameplate',-.28,.23,-.412,.24,.045,.004)
 m.poly('Warning',[(.21,.31,-.413),(.29,.18,-.413),(.13,.18,-.413)])
 m.poly('WarningMark',[(.215,.249,-.415),(.234,.249,-.415),(.202,.199,-.415),(.212,.233,-.415),(.194,.233,-.415),(.218,.287,-.415)])
 for x in [-.28,-.12]:
  m.cyl('Elbows',2,(x,.07,-.15),.12,.055,n=20)
  m.cyl('Elbows',1,(x,-.21,-.20),.30,.040,n=20)
  m.cyl('Elbows',1,(x,-.44,-.19),.23,.025,n=12)
 for y in [-.12,.0,.12]:
  m.box('Lugs',.12,y,-.12,.18,.045,.025)
  for x in [.16,.24]:m.cyl('Terminal',2,(x,y+.022,-.20),.09,.014,n=12)
 m.cyl('Ground',1,(.34,-.41,-.14),.44,.009,n=8)
 m.cyl('Elbows',1,(-.28,-.49,-.10),.07,.055,n=16)
 m.cyl('Elbows',1,(.26,-.49,-.10),.07,.055,n=16)

def meter_parts(m):
 # An open rim and recessed rear plate leave the four real jaws visible when empty.
 ring(m,'Socket',0,.05,-.31,.215,.235,.04,48)
 m.cyl('Socket',2,(0,.05,-.309),.001,.215,n=48)
 ring(m,'MeterRing',0,.05,-.30,.219,.240,.045,48)
 m.cyl('MeterFace',2,(0,.05,-.27),.13,.205,n=48)
 m.cyl('Glass',2,(0,.05,-.14),.09,.213,.195,48)
 m.p['Glass']=[f for f in m.p['Glass'] if max(v[2] for v in f)-min(v[2] for v in f)>1e-6]
 ring(m,'Glass',0,.05,-.052,.191,.195,.003,48)
 m.cyl('Disk',1,(0,-.082,-.20),.014,.130,n=40)
 m.box('DiskMark',-.015,-.083,-.075,.030,.017,.009)
 for i in range(5):
  x=-.132+i*.066;ring(m,'Dial%d'%i,x,.05,-.134,.0255,.027,.002,24)
  m.box('Needle%d'%i,x-.0015,.05,-.12,.003,.022,.003)
 m.box('SmartScreen',-.15,.01,-.128,.3,.12,.007)
 m.box('Disconnect',-.025,-.23,-.305,.05,.07,.006)
 for x in [-.105,.105]:
  for y in [-.04,.16]:
   m.box('JawInsulator',x-.035,y-.038,-.325,.07,.076,.018)
   m.box('Jaws',x-.018,y-.026,-.306,.012,.052,.029)
   m.box('Jaws',x+.006,y-.026,-.306,.012,.052,.029)
 for x,y in [(-.25,-.40),(.25,-.40),(-.25,.34),(.25,.34)]:m.cyl('Screws',2,(x,y,-.315),.012,.010,n=12)
 m.cyl('Seal',1,(.17,-.23,-.27),.04,.004,n=8)
 m.box('Seal',.154,-.255,-.275,.032,.028,.012)

def generate_meters():
 for wide in [False,True]:
  m=Mesh();m.box('Case',-.30 if not wide else -.44,-.47,-.49,.60 if not wide else 1.88,.88,.18)
  meter_parts(m)
  if wide:
   m.box('GutterCover',.52,-.42,-.304,.86,.78,.015)
   m.box('Bond',.45,-.40,-.30,.012,.71,.01)
   for x,part in [(0,'EntryLine'),(1,'EntryLoad')]:
    m.cyl(part,1,(x,-.50,-.40),.10,.075,n=24)
    m.cyl('Socket',1,(x,-.51,-.40),.025,.086,n=24)
   for x in [.57,1.33]:
    for y in [-.36,.30]:m.cyl('Screws',2,(x,y,-.30),.018,.009,n=12)
  else:
   for i in range(3):
    m.cyl('Terminal%d'%i,1,(-.16+i*.16,-.49,-.40),.04,.035,n=12)
    m.cyl('TopTerminal%d'%i,1,(-.16+i*.16,.40,-.36),.04,.035,n=12)
  m.save('wideundergroundmetersocket' if wide else 'northamericanmeterbase')

def service_accessories():
 m=Mesh();m.box('Case',-.20,-.49,-.49,.40,.72,.16)
 m.cyl('Pipe',1,(0,-.50,-.40),.38,.065,n=24)
 m.cyl('Pipe',1,(0,.23,-.40),.25,.052,n=24)
 m.cyl('Hood',1,(0,-.25,-.40),.06,.090,n=24)
 for y in [-.18,.10]:m.box('Hood',-.12,y,-.47,.24,.035,.14)
 for i in range(3):m.cyl('Terminal%d'%i,1,(-.12+i*.12,.43,-.40),.045,.025,n=12)
 m.save('undergroundserviceentrance')
 m=Mesh();m.box('Case',-.49,-.50,-.49,.98,.92,.98)
 m.box('Trim',-.48,.42,-.48,.96,.04,.96)
 m.box('Lid',-.445,.46,-.445,.89,.035,.89)
 for x in [-.32,.29]:m.box('Trim',x,.497,-.25,.03,.004,.12)
 for z in [-.30,0,.30]:m.box('Lettering',-.22,.497,z,.44,.002,.017)
 for x in [-.46,.40]:m.box('Lugs',x,-.26,-.18,.06,.10,.36)
 m.save('undergroundservicehandhole')

def font(size,bold=False):
 return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf',size)
def faces():
 target=root/'northamericanmeterbase';target.mkdir(exist_ok=True)
 for smart in [False,True]:
  im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im)
  d.ellipse((3,3,509,509),fill='#e8e8d5',outline='#343a38',width=4)
  def text(x,y,s,size=18,bold=False):d.text((x,y),s,fill='#242d29',font=font(size,bold),anchor='mm')
  text(256,64,'ELN UTILITY',26,True);text(256,95,'SINGLE PHASE 3 WIRE',16)
  if not smart:
   text(256,140,'ENERGY  kWh',21,True)
   for i in range(5):
    cx=(.195-.132+i*.066)/.39*512;cy=256;r=.026/.39*512
    d.ellipse((cx-r,cy-r,cx+r,cy+r),outline='#333a32',width=2)
    for k in range(10):
     a=(1 if i%2==0 else -1)*k*math.tau/10
     sx=math.sin(a);sy=-math.cos(a)
     d.line((cx+sx*(r-1),cy+sy*(r-1),cx+sx*(r-5),cy+sy*(r-5)),fill='#242d29',width=2)
     text(cx+sx*(r-13),cy+sy*(r-13),str(k),11)
   text(256,382,'Kh 7.2 Wh/rev',18)
  else:
   text(256,375,'BIDIRECTIONAL  |  FORM 2S',17,True)
  text(256,421,'CL 200   240 V   60 Hz',19,True);text(256,448,'120/240 V SERVICE',16)
  im.save(target/('smart_face.png' if smart else 'analog_face.png'))
 for name,label in [('lcd_page_0','DEL  kWh'),('lcd_page_1','REC  kWh'),('lcd_page_2','DEMAND  kW'),('lcd_page_3','LINE  V'),('lcd_open','DISCONNECT OPEN'),('lcd_closed','SERVICE CONNECTED')]:
  im=Image.new('RGBA',(256,32),(0,0,0,0));d=ImageDraw.Draw(im);d.text((128,16),label,font=font(20,True),fill='#17291b',anchor='mm');im.save(target/(name+'.png'))

def generate_meter_icons():
 for smart,name in [(False,'analogservicemeter'),(True,'smartservicemeter')]:
  im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
  d.ellipse((4,4,60,60),fill='#9faeb0',outline='#354145',width=3)
  d.ellipse((9,9,55,55),fill='#e7e7d2',outline='#c3d4d5',width=3)
  if smart:
   d.rectangle((16,24,49,39),fill='#839777',outline='#3f5145',width=2)
   d.text((19,26),'00124',font=font(9,True),fill='#263c2b')
  else:
   for x in [18,25,32,39,46]:
    d.ellipse((x-4,26,x+4,34),outline='#30372f');d.line((x,30,x+2,26),fill='#30372f')
   d.line((20,42,45,42),fill='#747c76',width=3)
  d.arc((8,7,56,56),185,285,fill='#f3fcfd',width=2)
  d.rectangle((44,53,48,59),fill='#c8a431')
  d.text((18,16),'kWh',font=font(8),fill='#354138')
  im.save(root.parent/'textures/items'/(name+'.png'))

if __name__=='__main__':
 generate_meters();service_accessories();faces();generate_meter_icons()
