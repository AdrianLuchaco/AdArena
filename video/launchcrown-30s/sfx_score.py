#!/usr/bin/env python3
"""LaunchCrown · "A day in 30 seconds" — sound score on onetake's sfx_palette (one room, few events).
Run with the onetake venv:  ~/.cache/onetake-venv/bin/python sfx_score.py  → sfx.wav
Every event is a beat in comp.html's timeline; pans come from where the thing is on screen.
The tension beat (20.8–21.8 s, clock frozen at 00:00:03) is deliberately silent.
"""
import os, sys
sys.path.insert(0, os.path.expanduser("~/.claude/skills/onetake/scripts"))
from sfx_palette import Score, air, glass, wood, sub, bubble, pan_of
import numpy as np

rng = np.random.default_rng(11)
s = Score(dur=30.0, T60=1.2)
place = s.place

# 1 · hook: the clock at 24:00:00, two words — two low glass touches, then quiet
place(glass(523, 0.9, 0.5), 0.35, 0.15, -0.05, 0.5)
place(glass(784, 0.9, 0.5), 0.80, 0.14, 0.05, 0.5)
# 2 · pull out to the track: one long breath, the runners arrive as soft pops
place(air(1.2, 180, 1600, 1.1, 0.55), 2.60, 0.34, 0.0, 0.55)
for i in range(4):
    place(bubble(430 + i * 40, 0.2), 3.05 + i * 0.09 + 0.03, 0.14, pan_of(400), 0.3)
place(glass(1046, 1.0, 0.4), 3.62, 0.08, pan_of(1760), 0.6)                       # the homepage and its crown
# 3 · the burst of bids: three woods climbing, a felt landing under the first
for t, f in ((4.40, 190), (4.62, 215), (4.84, 240)):
    place(wood(f, 0.09), t, 0.30, pan_of(900), 0.2)
place(sub(70, 0.45), 4.42, 0.18, 0.0, 0.3)
# the day runs: sparse bids, fewer sounds than bids
for t, x in ((6.20, 700), (6.90, 900), (8.10, 600)):
    place(wood(200 + rng.uniform(-15, 15), 0.08), t, 0.18, pan_of(x), 0.2)
# 4 · whip down to "your turn"
place(air(0.7, 2400, 260, 1.3, 0.35), 8.35, 0.40, 0.0, 0.5)
place(sub(62, 0.5), 9.18, 0.16, 0.0, 0.3)
# earn: a real click, then points ticking up (glass steps), the +40 and the featured +100 brighter
place(wood(240, 0.08), 10.00, 0.32, pan_of(845), 0.2); place(wood(190, 0.07), 10.06, 0.20, pan_of(845), 0.2)
for i, t in enumerate((10.25, 10.45, 10.65, 10.85, 11.05, 11.25)):
    place(glass(880 + i * 60, 0.35, 0.7), t, 0.07, pan_of(400), 0.35)
place(glass(1318, 0.9, 0.6), 11.50, 0.13, pan_of(400), 0.5)
place(wood(240, 0.08), 12.05, 0.30, pan_of(1340), 0.2); place(wood(190, 0.07), 12.11, 0.20, pan_of(1340), 0.2)
place(glass(1568, 1.2, 0.6), 12.17, 0.15, pan_of(1340), 0.55); place(glass(2093, 1.2, 0.4), 12.19, 0.09, pan_of(1340), 0.55)
# (12.4–13.6: the rest — nothing)
# 5 · bid: click, the card flies up to lane 5, lands with a felt hit
place(wood(250, 0.08), 14.42, 0.34, pan_of(1340), 0.2); place(wood(195, 0.07), 14.48, 0.22, pan_of(1340), 0.2)
place(air(1.0, 260, 2600, 1.2, 0.6), 14.55, 0.42, 0.2, 0.5, pan_to=-0.1)
place(sub(58, 0.9), 15.55, 0.46, 0.0, 0.35); place(bubble(360, 0.25), 15.57, 0.20, 0.0, 0.3)
# the last minutes: rivals bid, Kiln overtakes with a heavier knock
place(wood(205, 0.08), 16.60, 0.18, pan_of(1000), 0.2)
place(wood(215, 0.08), 17.25, 0.16, pan_of(700), 0.2)
place(wood(160, 0.10), 18.80, 0.34, pan_of(1200), 0.2); place(sub(66, 0.5), 18.82, 0.22, 0.0, 0.3)
# the countdown's last seconds, getting quieter, then the frozen beat in silence
for i, t in enumerate(np.arange(19.9, 20.81, 0.3)):
    place(wood(420, 0.05), float(t), 0.07 + i * 0.012, 0.0, 0.15)
# 6 · the last-minute bid: click, heavy hit, +2:00 rings out
place(wood(260, 0.08), 21.82, 0.40, pan_of(1300), 0.2); place(wood(200, 0.07), 21.88, 0.26, pan_of(1300), 0.2)
place(sub(52, 1.1), 21.84, 0.60, 0.0, 0.4)
place(glass(1046, 1.4, 0.7), 21.87, 0.16, 0.3, 0.6); place(glass(1568, 1.4, 0.5), 21.89, 0.11, 0.3, 0.6)
# final seconds ticking faster
for i, t in enumerate(np.arange(22.6, 24.15, 0.22)):
    place(wood(430, 0.05), float(t), 0.06 + i * 0.008, 0.0, 0.15)
# 7 · midnight: the deep one, a chord for the win
place(sub(46, 1.6), 24.20, 0.70, 0.0, 0.5)
place(glass(523, 2.2, 0.5), 24.24, 0.14, -0.1, 0.7); place(glass(659, 2.2, 0.5), 24.26, 0.12, 0.0, 0.7); place(glass(784, 2.2, 0.5), 24.28, 0.11, 0.1, 0.7)
# into the homepage: rising air through the dive, a soft landing when it fills the frame
place(air(0.7, 300, 1800, 1.2, 0.6), 24.50, 0.30, pan_of(1300), 0.5, pan_to=0.4)
place(air(1.2, 200, 3200, 1.1, 0.75), 25.20, 0.50, 0.3, 0.6, pan_to=0.0)
place(sub(60, 0.9), 26.30, 0.30, 0.0, 0.45)
# (26.3–28.0: the homepage holds — room tail only)
# 8 · end card: the iris opens with a breath, the crown lands, letters as a light glass run
place(air(0.7, 400, 2400, 1.3, 0.4), 28.00, 0.30, -0.4, 0.5, pan_to=0.0)
place(sub(55, 0.9), 28.80, 0.36, 0.0, 0.45)
for i, f in enumerate((523, 587, 659, 784, 880, 1046)):
    place(glass(f, 0.5, 0.6), 28.45 + i * 0.09, 0.05, (i - 2.5) * 0.08, 0.4)
place(glass(1046, 2.0, 0.4), 29.10, 0.10, 0.0, 0.8)

s.write(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sfx.wav"))
print("wrote sfx.wav")
