#!/usr/bin/env python3
"""Original voltage/phase nameplates. SI units and phase letters are language-neutral."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
labels=[('230 kV','#bc5815'),('69 kV','#16599c'),('69 kV','#bc5815'),('12.5 kV','#16599c'),('A','#9b2626'),('B','#8e701b'),('C','#235789'),('230 kV','#16599c')]
image=Image.new('RGBA',(512,512));draw=ImageDraw.Draw(image)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',54)
for row,(text,color) in enumerate(labels):
 y=row*64
 draw.rectangle((0,y,511,y+63),fill='#171c21')
 draw.rectangle((3,y+3,508,y+60),fill=color)
 draw.text((256,y+30),text,font=font,anchor='mm',fill='white',stroke_width=1)
path=Path('src/main/resources/assets/eln/model/transmissionmarkings/labels.png')
path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
