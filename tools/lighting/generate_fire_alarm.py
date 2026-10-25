#!/usr/bin/env python3
"""Original low-profile alarm fixtures and synthesized temporal horn/bell/chirp samples."""
from generate_fans_and_outlet import Mesh
from pathlib import Path
import array,json,math,subprocess,tempfile,wave
for name in ['panel','pull','horn','bell','strobe','smoke','heat']:
 m=Mesh();sensor=name in ['smoke','heat'];r=.18 if sensor else .24
 m.box('Base',-.50,-r,-r,.025,2*r,2*r)
 if sensor:
  m.cyl('Case',0,(-.475,0,0),.08 if name=='smoke' else .12,.17,.13,n=28)
  for j in range(12):
   a=j*math.tau/12;m.box('Vents',-.39,.14*math.cos(a)-.012,.14*math.sin(a)-.012,.015,.024,.024)
  if name=='heat':
   m.cyl('Handle',0,(-.355,0,0),.035,.04,n=12)
   for y in [-.07,.07]:m.box('Base',-.38,y,-.07,.07,.012,.14)
 else:
  h=.34 if name=='panel' else .20
  m.box('Case',-.475,-h,-.23,.16,2*h,.46)
  if name=='panel':
   m.box('Screen',-.31,.02,-.17,.012,.18,.34)
   for z in [-.13,0,.13]:m.box('Label',-.30,-.19,z-.035,.012,.05,.07)
  elif name=='pull':
   m.box('Label',-.31,.05,-.19,.015,.10,.38);m.box('Handle',-.30,-.10,-.15,.05,.065,.30)
  elif name=='bell':
   m.cyl('Case',0,(-.315,0,0),.07,.20,.16,n=32);m.cyl('Base',0,(-.24,0,0),.012,.025,n=12)
  elif name=='horn':
   for y in [-.12,-.06,0,.06,.12]:m.box('Vents',-.309,y,-.17,.015,.022,.34)
  if name in ['strobe','horn']:
   m.box('Flash',-.295,.20 if name=='horn' else -.12,-.13,.055,.12 if name=='horn' else .24,.26)
 m.box('LED',-.29 if not sensor else -.374,-.02,-.02,.012,.035,.035)
 m.save('firealarm_'+name)
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln';rate=22050
for name,duration in [('horn',4),('bell',2),('trouble',.20)]:
 data=[]
 for i in range(int(rate*duration)):
  t=i/rate
  if name=='horn':
   on=any(a<=t<a+.5 for a in [0,1,2]);edge=min((t%1)/.008,(.5-t%1)/.008,1) if on else 0
   value=.3*max(0,edge)*(math.sin(math.tau*880*t)+.30*math.sin(math.tau*1760*t))
  elif name=='bell':
   q=t%.25;value=.35*math.exp(-22*q)*(math.sin(math.tau*1200*t)+.4*math.sin(math.tau*2785*t))
  else:value=.3*math.sin(math.pi*t/duration)**2*math.sin(math.tau*2100*t)
  data.append(round(max(-.95,min(.95,value))*32767))
 with tempfile.TemporaryDirectory() as td:
  wav=Path(td)/'alarm.wav'
  with wave.open(str(wav),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(array.array('h',data).tobytes())
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(root/'sounds'/('fire_alarm_'+name+'.ogg'))],check=True)
p=root/'sounds.json';sounds=json.loads(p.read_text())
for name in ['horn','bell','trouble']:sounds['fire_alarm_'+name]={'sounds':[{'name':'eln:fire_alarm_'+name,'stream':False,'attenuation_distance':16 if name=='trouble' else 48}]}
p.write_text(json.dumps(sounds,indent=2)+'\n')
