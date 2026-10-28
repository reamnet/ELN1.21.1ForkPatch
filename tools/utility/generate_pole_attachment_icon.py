#!/usr/bin/env python3
"""Original removable pole-lamp item icon; placed geometry reuses the baked pole fixture."""
from pathlib import Path
from PIL import Image,ImageDraw
p=Path(__file__).resolve().parents[2]/'src/main/resources/assets/eln/textures/items/polestreetlightattachment.png'
im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
d.rounded_rectangle((10,25,19,55),radius=3,fill='#7b8589',outline='#c4cdd0',width=2)
d.polygon([(16,32),(39,17),(43,21),(19,39)],fill='#a5afb2',outline='#5a656b')
d.polygon([(32,12),(52,13),(59,23),(43,29),(28,22)],fill='#56636c',outline='#c4cbd0')
d.polygon([(32,23),(43,29),(57,24),(48,21)],fill='#e2e8dc',outline='#949f9a')
d.rectangle((9,31,21,35),fill='#566167');d.rectangle((9,46,21,50),fill='#566167')
im.save(p)
