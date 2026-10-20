#!/usr/bin/env python3
"""Original utility operating sounds and small item icons. Python 3, Pillow and ffmpeg."""
from pathlib import Path
import array,json,math,random,subprocess,tempfile,wave
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln'
rate=22050
sounds=['utility_fuse_blow','utility_recloser_bump','utility_arc_close','utility_arc_open']
for index,name in enumerate(sounds):
 rng=random.Random(25250+index);data=[];duration=[.42,.38,.14,.34][index]
 for i in range(int(rate*duration)):
  t=i/rate;noise=rng.uniform(-1,1)
  if index==0:v=.75*math.exp(-20*t)*noise+.25*math.exp(-11*t)*math.sin(math.tau*110*t)
  elif index==1:
   v=.5*math.exp(-18*t)*(math.sin(math.tau*95*t)+.4*noise)
   if t>.07:v+=.35*math.exp(-35*(t-.07))*noise
  else:
   env=math.sin(math.pi*t/duration)**.5
   v=(.24 if index==2 else .5)*env*(.8*noise+.20*math.sin(math.tau*120*t))
  data.append(max(-.95,min(.95,v)))
 pcm=array.array('h',(round(x*32767) for x in data))
 with tempfile.TemporaryDirectory() as td:
  wav=Path(td)/'utility.wav'
  with wave.open(str(wav),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(pcm.tobytes())
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(root/'sounds'/(name+'.ogg'))],check=True)
p=root/'sounds.json';data=json.loads(p.read_text())
for name in sounds:data[name]={'sounds':[{'name':'eln:'+name,'stream':False}]}
p.write_text(json.dumps(data,indent=2)+'\n')
for index,name in enumerate(['polefuselink','analogservicemeter','smartservicemeter']):
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
 if index==0:
  d.rounded_rectangle((13,5,18,26),radius=2,fill=(169,109,56),outline=(64,57,47),width=1)
  for y in [3,24]:d.rectangle((10,y,21,y+4),fill=(193,181,135));d.line((11,y,20,y),fill=(235,229,198),width=1)
 else:
  d.ellipse((2,2,30,30),fill=(103,119,123),outline=(40,47,50),width=2)
  d.ellipse((5,5,27,27),fill=(208,217,205),outline=(158,180,185),width=2)
  if index==1:
   for x in [9,16,23]:
    d.ellipse((x-3,11,x+3,17),fill=(240,235,208),outline=(69,71,65));d.line((x,14,x+1,11),fill=(42,46,44))
   d.line((10,22,23,22),fill=(72,75,72),width=2)
  else:
   d.rectangle((7,10,25,20),fill=(45,83,65));d.text((8,10),'120',fill=(110,228,146));d.rectangle((15,23,18,25),fill=(67,161,89))
  d.arc((4,3,28,28),190,280,fill=(228,243,247),width=2)
 im.save(root/'textures/items'/(name+'.png'))
print('Generated four operating sounds and three item icons.')
