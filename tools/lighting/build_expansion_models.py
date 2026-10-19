"""Original window, strip, estate, offshore and street-light expansion meshes."""
import runpy
from pathlib import Path
from math import sin,cos,pi
# Reuse the established outward-wound mesh primitives; existing models regenerate identically.
Mesh=runpy.run_path(str(Path(__file__).with_name('build_fixture_models.py')))['Mesh']

def lamp(m,i,p,r=(.09,.07,.07)):
    m.part('Light'+str(i));m.sphere(p,r,n=12,rows=6)
def canopy():
    m=Mesh();m.part('Base');m.cone(.005,.07,.28,.24,n=20)
    m.part('Metal');m.cone(.07,.70,.025,.025,n=8);return m

def crystal(m,x,r,length,count):
    for i in range(count):
        a=i*2*pi/count;y=r*cos(a);z=r*sin(a)
        m.cone(x,x+length,.035,.035,y,z,n=6);m.cone(x+length,x+length+.10,.035,0,y,z,n=6)

m=Mesh();m.part('Base');m.cone(.005,.035,.115,.13);m.part('Metal');m.cone(.035,.08,.13,.055);m.cone(.08,.13,.055,.045)
m.part('Shade');m.cone(.11,.35,.040,.040,n=16)
lamp(m,0,(.415,0,0),(.085,.035,.035));m.save('windowcandlelamp')
m=Mesh();m.part('Base');m.cone(.005,.045,.27,.29);m.part('Metal');m.cone(.045,1.64,.027,.024,n=12)
m.part('Shade');m.cone(1.43,1.97,.37,.30,caps=False)
m.part('Trim');m.ring(1.43,.37,.35);m.ring(1.97,.30,.28)
lamp(m,0,(1.66,0,0),(.13,.08,.08));m.save('standingdrumfloorlamp')

for kind,name in enumerate(['undercabinetledstrip','recessedledchannel','coveledstrip','surfacelinearledstrip','flexneonledstrip']):
    m=Mesh();m.part('Base');length=1.92 if kind==3 else .94
    m.box((.003,-.045,-length/2),(.018,.045,length/2))
    m.part('Metal')
    if kind==4:
        for i in range(24):
            a=-.46+i*.92/24;b=-.46+(i+1)*.92/24
            m.tube((.035,.12*sin(a*2*pi),a),(.035,.12*sin(b*2*pi),b),.028,n=8)
        for i in range(3):
            m.part('Light'+str(i))
            for j in range(8):
                a=-.46+(i*8+j)*.92/24;b=a+.92/24
                m.tube((.060,.12*sin(a*2*pi),a),(.060,.12*sin(b*2*pi),b),.021,n=8)
    else:
        for y in [-.057,.045]:m.box((.018,y,-length/2),(.06 if kind==3 else .04,y+.012,length/2))
        if kind==2:m.box((.018,-.06,-.47),(.11,-.035,.47));m.box((.018,-.06,-.47),(.035,.17,.47))
        count=4 if kind==3 else 2
        for i in range(count):
            m.part('Light'+str(i));lo=-length/2+i*length/count+.007;hi=lo+length/count-.014
            m.box((.024 if kind==1 else .055,.10 if kind==2 else -.033,lo),(.03 if kind==1 else .076,.16 if kind==2 else .033,hi))
    m.save(name)

# Five architecturally different multi-block chandeliers; X is the drop from the ceiling.
m=canopy()
for x,r in [(1.8,1.9),(3.05,1.2)]:
    m.cone(x-.035,x+.035,r+.07,r+.07,n=48,caps=False)
    m.cone(x-.035,x+.035,r-.07,r-.07,n=48,caps=False)
    m.ring(x-.035,r+.07,r-.07,n=48)
    for i in range(4):a=i*pi/2;m.tube((.65,0,0),(x,r*cos(a),r*sin(a)),.009,n=6)
for i in range(16):
    r=1.9 if i<8 else 1.2;a=i*pi/4;x=1.8+(i//8)*1.25
    m.part('Light'+str(i))
    for j in range(6):
        a0=a+(j-.45)*pi/24;a1=a+(j+.45)*pi/24
        m.tube((x+.05,r*cos(a0),r*sin(a0)),(x+.05,r*cos(a1),r*sin(a1)),.045,n=6)
m.save('grandhalochandelier')
m=canopy()
for k in range(5):
    x=1.6+k*.65;r=1.8-k*.22
    m.cone(x-.03,x+.03,r,r,n=40,caps=False)
    for i in range(4):a=i*pi/2;m.tube((.65,0,0),(x,r*cos(a),r*sin(a)),.010,n=6)
for i in range(20):
    x=1.6+(i%5)*.65;r=1.8-(i%5)*.22;a=i*pi/2
    lamp(m,i,(x,r*cos(a),r*sin(a)),(.06,.08,.08))
m.part('Glass')
for k in range(5):crystal(m,1.64+k*.65,1.8-k*.22,.37,32)
m.save('crystalcascadechandelier')
m=canopy();m.cone(.7,2.25,.055,.055,n=12)
for i in range(18):
    a=i*pi/3;x=2+(i%3)*.45;r=.75+(i%3)*.65
    m.tube((1.2,0,0),(x-.25,r*.70*cos(a),r*.70*sin(a)),.025,n=8)
    m.tube((x-.25,r*.70*cos(a),r*.70*sin(a)),(x,r*cos(a),r*sin(a)),.018,n=8)
for i in range(18):
    a=i*pi/3;x=2+(i%3)*.45;r=.75+(i%3)*.65
    lamp(m,i,(x,r*cos(a),r*sin(a)),(.14,.14,.14))
m.part('Glass')
for i in range(18):
    a=i*pi/3;x=2+(i%3)*.45;r=.75+(i%3)*.65
    m.sphere((x,r*cos(a),r*sin(a)),(.20,.20,.20),n=10,rows=6)
m.save('sculpturalbranchchandelier')
m=canopy();m.cone(.70,1.10,.025,.025,n=8);m.cone(1.10,1.15,1.95,1.95,n=40)
for i in range(20):
    a=i*pi/5;x=1.6+i*.18;y=1.7*cos(a);z=1.7*sin(a)
    m.tube((1.15,y,z),(x-.10,y,z),.006,n=5)
for i in range(20):
    a=i*pi/5;x=1.6+i*.18;lamp(m,i,(x,1.7*cos(a),1.7*sin(a)),(.13,.065,.065))
m.part('Glass')
for i in range(20):
    a=i*pi/5;x=1.6+i*.18;y=1.7*cos(a);z=1.7*sin(a)
    m.cone(x+.11,x+.30,.07,0,y,z,n=8)
m.save('spiralrainchandelier')
m=canopy();m.cone(.70,4.1,.06,.04,n=12)
for x,r in [(1.8,1.9),(3.0,1.2)]:
    m.cone(x-.03,x+.04,r,r,n=40,caps=False)
    for i in range(8):
        a=i*pi/4;m.tube((x-.65,0,0),(x,r*cos(a),r*sin(a)),.026,n=8)
for i in range(16):
    a=i*pi/4;r=1.9 if i<8 else 1.2;x=1.8 if i<8 else 3
    lamp(m,i,(x,r*cos(a),r*sin(a)),(.12,.065,.065))
m.part('Glass')
for k in range(12):
    x=.75+k*.30;r=1.95*sin((k+1)*pi/14)
    for i in range(24):
        a=i*pi/12;m.sphere((x,r*cos(a),r*sin(a)),(.035,.045,.045),n=6,rows=4)
m.sphere((4.45,0,0),(.20,.12,.12),n=12,rows=6);m.save('grandempirechandelier')

# Heavy commercial and offshore fixtures: distinct bezels, cages, fins and yokes.
for kind,name in enumerate(['stadiumwallfloodlight','armoredbulkheadlight','industrialwallpacklight','ruggedcanopylight','industrialyardlight','offshorecagedwellglass','offshoresealedlinearlight','offshoreplatformfloodlight']):
    m=Mesh();m.part('Base');m.box((.005,-.18,-.12),(.07,.18,.12));m.part('Metal')
    if kind in [0,7]:
        for z in [-.46,.46]:m.tube((.05,-.15,z*.25),(.37,-.15,z),.035,n=8);m.tube((.37,-.15,z),(.44,.12,z),.035,n=8)
        m.box((.32,-.31,-.48),(.59,.31,.48))
        for y in [-.25,-.15,-.05,.05,.15,.25]:m.box((.28,y,-.44),(.32,y+.025,.44))
        count=4 if kind==0 else 2
        for i in range(count):
            m.part('Light'+str(i));y=-.15+(i//2)*.30 if kind==0 else 0;z=-.28+(i%2)*.56
            m.box((.60,y-.12,z-.23),(.63,y+.12,z+.23))
        m.part('Trim')
        for z in [-.50,.50]:m.box((.31,-.34,z-.025),(.65,.34,z+.025))
        for y in [-.34,.34]:m.box((.31,y-.025,-.50),(.65,y+.025,.50))
        if kind==7:
            for z in [-.35,0,.35]:m.tube((.65,-.33,z),(.65,.33,z),.012,n=6)
    elif kind==1:
        m.sphere((.10,0,0),(.08,.32,.20),n=16,rows=8)
        lamp(m,0,(.22,0,0),(.10,.25,.15));m.part('Trim')
        for y in [-.20,0,.20]:m.tube((.32,y,-.15),(.32,y,.15),.016,n=8)
        m.tube((.32,-.28,0),(.32,.28,0),.014,n=8)
    elif kind==2:
        m.box((.06,-.30,-.40),(.32,.30,.40))
        for i,z in enumerate([-.18,.18]):m.part('Light'+str(i));m.box((.33,-.26,z-.16),(.37,-.02,z+.16))
        m.part('Trim');m.box((.06,.25,-.43),(.44,.32,.43))
    elif kind==3:
        m.box((.035,-.43,-.43),(.14,.43,.43))
        for i in range(4):
            m.part('Light'+str(i));y=-.2+(i//2)*.4;z=-.2+(i%2)*.4;m.box((.15,y-.17,z-.17),(.18,y+.17,z+.17))
        m.part('Trim')
        for y in [-.46,.43]:m.box((.04,y,-.46),(.19,y+.03,.46))
        for z in [-.46,.43]:m.box((.04,-.46,z),(.19,.46,z+.03))
    elif kind==4:
        for a,b in [((.04,0,0),(.40,.20,0)),((.40,.20,0),(.72,.20,0))]:m.tube(a,b,.035,n=10)
        m.housing(.70,.84,.1,0,.42,.85)
        for i,z in enumerate([-.22,.22]):m.part('Light'+str(i));m.box((.84,-.05,z-.18),(.86,.24,z+.18))
    elif kind==5:
        m.cone(.05,.35,.15,.24,n=20);m.cone(.35,.40,.27,.27,n=24)
        for i in range(8):
            a=i*pi/4;m.tube((.39,.23*cos(a),.23*sin(a)),(.89,.23*cos(a),.23*sin(a)),.014,n=6)
        m.cone(.87,.91,.26,.26,n=24,caps=False);m.ring(.91,.26,.21,n=24)
        lamp(m,0,(.64,0,0),(.21,.12,.12));m.part('Glass');m.cone(.4,.86,.20,.20,n=20)
    elif kind==6:
        m.box((.03,-.18,-.96),(.13,.18,.96))
        for i,z in enumerate([-.45,.45]):m.part('Light'+str(i));m.tube((.20,0,z-.42),(.20,0,z+.42),.085,n=12)
        m.part('Trim')
        for z in [-.95,-.48,0,.48,.95]:m.tube((.27,-.16,z),(.27,.16,z),.018,n=6)
        for y in [-.17,.17]:m.tube((.23,y,-.96),(.23,y,.96),.018,n=6)
    m.save(name)
for classic,name in [(False,'modernledstreetlight'),(True,'boulevardlanternstreetlight')]:
    m=Mesh();m.part('Base');m.cone(.005,.12,.22,.17)
    m.part('Metal');m.cone(.12,5.5 if not classic else 5.1,.065,.045,n=12)
    if not classic:
        m.tube((5.45,0,0),(5.5,-.65,0),.045);m.tube((5.5,-.65,0),(5.5,-1.05,0),.04)
        m.housing(5.37,5.52,-1.05,0,.74,.43)
        m.part('Light0');m.box((5.34,-1.37,-.18),(5.37,-.73,.18))
    else:
        for y in [-.72,.72]:
            m.tube((5.05,0,0),(5.15,y,0),.035)
            m.cone(4.42,4.48,.21,.21,y,0,n=16);m.cone(5.05,5.16,.28,0,y,0,n=16)
            for i in range(4):
                a=i*pi/2;m.tube((4.48,y+.17*cos(a),.17*sin(a)),(5.05,y+.17*cos(a),.17*sin(a)),.012,n=6)
        for i,y in enumerate([-.72,.72]):lamp(m,i,(4.8,y,0),(.20,.12,.12))
    m.save(name)

# Pole-mounted street-lamp heads share the existing pole and transformer geometry.
for twin,name in [(False,'utilitycobrahead'),(True,'utilitytwinled')]:
    m=Mesh();m.part('Transformer');m.box((-.24,1.45,-.24),(.24,2.05,.24))
    m.part('Bracket');m.box((-.20,2.08,-.23),(.20,2.18,.23))
    for a,b in [((-.1,2.13,0),(-.5,2.60,0)),((-.5,2.60,0),(-1.1,2.62,0))]:m.tube(a,b,.035,n=10)
    zs=[-.62,.62] if twin else [0]
    if twin:m.tube((-1.0,2.57,-.62),(-1.0,2.57,.62),.035,n=10)
    m.part('Housing')
    for z in zs:
        if twin:m.box((-1.52,2.33,z-.22),(-.77,2.45,z+.22))
        else:m.sphere((-1.15,2.45,z),(.48,.13,.25),n=16,rows=8)
    m.part('Lens')
    for z in zs:m.box((-1.46,2.29,z-.19),(-.84,2.32,z+.19))
    m.save(name)

# Wall control plates, +X out of the wall; the lever/paddle pivots about its own origin.
for paddle,name in [(True,'wirelesspaddleswitch'),(False,'wirelesstoggleswitch')]:
    m=Mesh();m.part('Plate');m.box((.008,-.23,-.145),(.035,.23,.145))
    m.part('Trim')
    for y in [-.19,.19]:m.cone(.035,.041,.014,.014,y,0,n=8)
    m.part('Lever')
    if paddle:m.box((.035,-.135,-.090),(.065,.135,.090))
    else:m.box((.035,-.065,-.05),(.075,.065,.05));m.tube((.075,0,0),(.16,.07,0),.019,n=8)
    m.save(name)
# Seasonal strings and wall decorations; +X is out of the wall, Y is up.
for kind,name in enumerate(['christmasfairylights','multicolorchristmaslights','christmasiciclelights','halloweenpumpkinstring','halloweenghoststring']):
    m=Mesh();m.part('Base')
    for z in [-.95,.95]:m.box((.005,-.025,z-.025),(.03,.025,z+.025))
    m.part('Metal');m.tube((.035,0,-.96),(.035,0,.96),.008,n=6)
    n=6 if kind<3 else 4
    positions=[(-.8+i*.32 if kind<3 else -.72+i*.48,.12+(i%3)*.12 if kind==2 else .10) for i in range(n)]
    for z,length in positions:m.tube((.035,0,z),(.065,-length,z),.006,n=6)
    if kind==3:
        m.part('Accent')
        for z,length in positions:m.sphere((.10,-.16,z),(.09,.11,.12),n=12,rows=6)
    elif kind==4:
        m.part('Shade')
        for z,length in positions:
            m.sphere((.10,-.14,z),(.08,.10,.10),n=12,rows=6)
            m.box((.03,-.26,z-.10),(.16,-.14,z+.10))
    for i,(z,length) in enumerate(positions):
        if kind==3:
            m.part('Light'+str(i))
            for dz in [-.045,.045]:m.box((.184,-.14,z+dz-.018),(.192,-.10,z+dz+.018))
            m.box((.184,-.21,z-.055),(.192,-.19,z+.055))
        elif kind==4:
            m.part('Light'+str(i))
            for dz in [-.038,.038]:m.sphere((.182,-.14,z+dz),(.008,.022,.017),n=8,rows=4)
        else:lamp(m,i,(.08,-length,z),(.035,.07 if kind==2 else .035,.035))
    m.save(name)
m=Mesh();m.part('Base');m.cone(.005,.025,.10,.10,n=12)
points=[]
for i in range(10):
    a=pi/2+i*pi/5;r=.46 if i%2==0 else .19;points.append((.055,r*sin(a),r*cos(a)))
m.part('Metal')
for i in range(10):m.tube(points[i],points[(i+1)%10],.015,n=6)
m.part('Light0')
for i in range(10):
    a,b=points[i],points[(i+1)%10]
    m.face([(.075,0,0),(.075,b[1],b[2]),(.075,a[1],a[2])])
    m.face([(.04,0,0),(.04,a[1],a[2]),(.04,b[1],b[2])])
    m.face([(.04,a[1],a[2]),(.075,a[1],a[2]),(.075,b[1],b[2]),(.04,b[1],b[2])])
m.save('christmasstarlight')
m=Mesh();m.part('Base');m.box((.005,-.09,-.08),(.035,.09,.08))
m.part('Metal');m.box((.02,-.05,-.04),(.04,.05,.04))
outline=[(0,.12),(-.12,.32),(-.23,.46),(-.12,.43),(-.07,.27),(-.20,.19),(-.10,.13),(-.17,0),(-.10,-.13),(-.20,-.19),(-.07,-.27),(-.12,-.43),(-.23,-.46),(-.12,-.32),(0,-.12),(.14,-.08),(.19,-.10),(.16,0),(.19,.10),(.14,.08)]
m.part('Light0')
for i in range(len(outline)):
    a,b=outline[i],outline[(i+1)%len(outline)]
    m.face([(.075,0,0),(.075,a[0],a[1]),(.075,b[0],b[1])])
    m.face([(.04,0,0),(.04,b[0],b[1]),(.04,a[0],a[1])])
    m.face([(.04,a[0],a[1]),(.04,b[0],b[1]),(.075,b[0],b[1]),(.075,a[0],a[1])])
m.save('halloweenbatlight')
m=Mesh();m.part('Base');m.cone(.005,.035,.20,.20,n=16)
m.part('Accent');m.sphere((.36,0,0),(.31,.30,.30),n=16,rows=10)
m.part('Metal');m.cone(.64,.74,.035,.025,n=8)
m.part('Light0')
# A face on the forward side of the upright pumpkin (canonical +Y).
for z in [-.11,.11]:m.face([(.47,.285,z-.055),(.47,.285,z+.055),(.56,.25,z)])
m.box((.20,.245,-.16),(.27,.275,.16));m.save('jackolanternlamp')
