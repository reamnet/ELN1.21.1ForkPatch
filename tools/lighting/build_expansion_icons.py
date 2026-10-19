"""Original 16-pixel UPS cell icons for the port's flat descriptor-item renderer."""
from PIL import Image,ImageDraw
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/textures/items'
for index,name,color in [(0,'upsagmbatterycell',(48,96,164)),(1,'upslifepo4batterycell',(35,140,78)),(2,'upsindustrialbatterymodule',(207,135,30))]:
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    left,right=(2,13) if index<2 else (1,14)
    d.rectangle((left,4,right,14),fill=(30,34,40),outline=(15,18,23))
    d.rectangle((left+1,5,right-1,12),fill=(66,74,82))
    d.rectangle((left,3,right,5),fill=(91,99,106),outline=(24,28,33))
    d.rectangle((left+2,1,left+3,3),fill=(196,61,45));d.rectangle((right-3,1,right-2,3),fill=(155,166,175))
    d.rectangle((left+2,7,right-2,11),fill=color)
    d.line((left+2,6,right-2,6),fill=(130,140,146))
    # One, two or three marks distinguish the cell tiers even without color.
    for n in range(index+1):d.line((left+3+n*2,8,left+3+n*2,10),fill=(240,240,219))
    d.point((left+1,13),fill=(150,158,164));d.point((right-1,13),fill=(150,158,164))
    if index==2:
        d.line((0,6,0,12),fill=(142,150,155));d.line((15,6,15,12),fill=(142,150,155))
    im.save(root/(name+'.png'))
