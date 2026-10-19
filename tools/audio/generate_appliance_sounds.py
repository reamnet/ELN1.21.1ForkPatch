#!/usr/bin/env python3
"""Generate original mono appliance samples. Requires Python 3 and ffmpeg/libvorbis.

Deterministic synthesis; no external recordings or build-time dependencies.
Run from any directory to regenerate the checked-in OGG files.
"""
import array
import json
import math
from pathlib import Path
import random
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/eln'
RATE = 22050
TAU = math.tau
# pitch, motor hum, airflow/sizzle, blade/bubble modulation frequency
LOOPS = {
    'microwave': (60, .38, .10, 0),
    'toaster': (60, .08, .12, 6),
    'stove': (120, .05, .48, 19),
    'fridge': (60, .32, .12, 2),
    'coffee': (90, .20, .45, 7),
    'kettle': (120, .06, .56, 11),
    'blender': (180, .40, .40, 24),
    'rice': (120, .05, .32, 5),
    'oven': (60, .22, .21, 12),
    'heater': (60, .16, .35, 15),
    'ceiling_fan': (60, .08, .34, 12),
    'window_ac': (60, .38, .40, 18),
    'minisplit': (90, .12, .32, 15),
    'box_fan': (120, .18, .42, 20),
    'industrial_fan': (60, .43, .70, 12),
    'battery_hiss': (120, .025, .75, 13),
}

def save(name, samples):
    pcm = array.array('h', (round(max(-.95, min(.95, v))*32767) for v in samples))
    path = ROOT / 'sounds' / (name + '.ogg')
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / 'sample.wav'
        with wave.open(str(wav), 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav),
                        '-c:a', 'libvorbis', '-q:a', '4', '-ac', '1', str(path)], check=True)

for seed, (name, (pitch, hum, noise, mod)) in enumerate(LOOPS.items()):
    rng = random.Random(2200 + seed)
    samples = []
    smooth = 0
    for i in range(RATE*2):
        t = i/RATE
        white = rng.uniform(-1, 1)
        smooth = .65*smooth + .35*white
        air = white-smooth if name == 'battery_hiss' else smooth
        envelope = .78 + .22*math.sin(TAU*mod*t) if mod else 1
        motor = math.sin(TAU*pitch*t) + .28*math.sin(TAU*pitch*2*t) + .12*math.sin(TAU*pitch*4*t)
        samples.append(.6*(hum*motor + noise*air*envelope))
    # Short cosine fades make the noise-loop seam click-free.
    n = RATE//125
    for i in range(n):
        gain = .5 - .5*math.cos(math.pi*i/(n-1))
        samples[i] *= gain
        samples[-1-i] *= gain
    save('appliance_'+name, samples)

for name in ['done', 'toaster_pop']:
    rng = random.Random(9022)
    samples = []
    for i in range(RATE//2):
        t = i/RATE
        if name == 'done':
            env = min(1, t/.008)*math.exp(-8*t)
            v = .30*env*(math.sin(TAU*880*t)+.35*math.sin(TAU*1320*t))
        else:
            v = .40*math.exp(-24*t)*(rng.uniform(-1,1)*.6 + math.sin(TAU*(180*t-80*t*t)))
            if t > .13: v += .12*math.exp(-40*(t-.13))*rng.uniform(-1,1)
        samples.append(v)
    save('appliance_'+name, samples)

path = ROOT/'sounds.json'
data = json.loads(path.read_text())
for name in list(LOOPS) + ['done', 'toaster_pop']:
    key = 'appliance_'+name
    data[key] = {'sounds': [{'name': 'eln:'+key, 'stream': False}]}
path.write_text(json.dumps(data, indent=2)+'\n')
print('Generated 18 original appliance sound samples.')
