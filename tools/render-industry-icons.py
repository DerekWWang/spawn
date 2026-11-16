"""Industry / arms / electronics icons, models, definitions, blockstates and lang keys.

Reproducible art source: pixel grids are drawn here and every PNG is written by
`qube texture write`; this script never encodes a PNG itself. Run from anywhere:

    python tools/render-industry-icons.py [--only name ...] [--skip-lang]
"""
from pathlib import Path
import argparse, json, math, os, random, shutil, subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/spawn_9b5fbb9'
WORK = ROOT / 'run/qube/icon-grids'
NS = 'spawn_9b5fbb9'
OUT = '#303244'


def mix(c, o, f):
    a = [int(c[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(o[i:i + 2], 16) for i in (1, 3, 5)]
    return '#' + ''.join(f'{round(x + (y - x) * f):02X}' for x, y in zip(a, b))


def ramp(h):
    """[darkest, shade, base, light, highlight]"""
    return [mix(h, '#000000', .62), mix(h, '#000000', .32), h, mix(h, '#FFFFFF', .3), mix(h, '#FFFFFF', .62)]


DIG = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'


class Cv:
    def __init__(s, block=False):
        s.p = [[None] * 16 for _ in range(16)]
        s.lo, s.hi = (0, 15) if block else (1, 14)

    def set(s, x, y, c):
        x, y = int(round(x)), int(round(y))
        if s.lo <= x <= s.hi and s.lo <= y <= s.hi:
            s.p[y][x] = c

    def get(s, x, y):
        return s.p[y][x] if 0 <= x < 16 and 0 <= y < 16 else None

    def rect(s, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                s.set(x, y, c)

    def line(s, x, y, X, Y, c):
        n = max(int(round(max(abs(X - x), abs(Y - y)))), 1)
        for i in range(n + 1):
            s.set(x + (X - x) * i / n, y + (Y - y) * i / n, c)

    def poly(s, pts, c):
        for y in range(16):
            for x in range(16):
                ins = False
                j = len(pts) - 1
                for i, (xi, yi) in enumerate(pts):
                    xj, yj = pts[j]
                    if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                        ins = not ins
                    j = i
                if ins:
                    s.set(x, y, c)
        for i, (x, y) in enumerate(pts):
            s.line(x, y, *pts[(i + 1) % len(pts)], c)

    def blob(s, cx, cy, rx, ry, R, ymin=-99, ymax=99, rough=None):
        for y in range(16):
            for x in range(16):
                if y < ymin or y > ymax:
                    continue
                nx, ny = (x - cx) / rx, (y - cy) / ry
                e = nx * nx + ny * ny
                if rough:
                    e *= rough(x, y)
                if e <= 1:
                    l = -.55 * nx - .8 * ny
                    i = 4 if l > .62 else 3 if l > .15 else 2 if l > -.35 else 1 if l > -.8 else 0
                    s.set(x, y, R[i])

    def ring(s, cx, cy, rx, ry, inner, R, ridges=False):
        for y in range(16):
            for x in range(16):
                nx, ny = (x - cx) / rx, (y - cy) / ry
                d = math.hypot(nx, ny)
                if inner < d <= 1:
                    l = -.55 * nx - .8 * ny
                    i = 3 if l > .3 else 2 if l > -.3 else 1
                    if ridges and int(d * 7) % 2:
                        i = max(0, i - 1)
                    s.set(x, y, R[i])

    def vshade(s, x0, y0, x1, y1, R, cols=None):
        """cylinder with vertical axis: shade across x (per-row extents via cols)"""
        for y in range(y0, y1 + 1):
            a, b = cols(y) if cols else (x0, x1)
            n = b - a + 1
            for x in range(a, b + 1):
                t = (x - a + .5) / n
                s.set(x, y, R[3] if t < .2 else R[4] if t < .36 else R[2] if t < .62 else R[1] if t < .86 else R[0])

    def hshade(s, x0, y0, x1, y1, R, rows=None):
        """cylinder with horizontal axis: shade across y (per-column extents via rows)"""
        for x in range(x0, x1 + 1):
            a, b = rows(x) if rows else (y0, y1)
            n = b - a + 1
            for y in range(a, b + 1):
                t = (y - a + .5) / n
                s.set(x, y, R[3] if t < .2 else R[4] if t < .36 else R[2] if t < .62 else R[1] if t < .86 else R[0])

    def box(s, x, y, w, h, d, R, top=None, side=None, front=None):
        top = top or R[3]
        side = side or R[1]
        front = front or R[2]
        for k in range(d):
            for xx in range(x + d - k, x + d - k + w):
                s.set(xx, y + k, top)
        for yy in range(y + d, y + d + h):
            for xx in range(x, x + w):
                s.set(xx, yy, front)
        for j in range(d):
            for yy in range(y + d - 1 - j, y + d - 1 - j + h):
                s.set(x + w + j, yy, side)

    def art(s, ox, oy, rows, legend):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in legend:
                    s.set(ox + i, oy + j, legend[ch])

    def gear(s, cx, cy, rb, rt, n, R, hole=1.3):
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                r = math.hypot(dx, dy)
                if r > rt:
                    continue
                if r > rb:
                    sec = (math.atan2(dy, dx) % (2 * math.pi)) / (2 * math.pi / n)
                    if sec % 1 > .5:
                        continue
                if r < hole:
                    s.set(x, y, R[0])
                    continue
                l = (-.55 * dx - .8 * dy) / max(r, 1)
                s.set(x, y, R[3] if l > .45 else R[2] if l > -.25 else R[1])

    def outline(s, c=OUT):
        src = [row[:] for row in s.p]
        for y in range(16):
            for x in range(16):
                if src[y][x] is None:
                    for X, Y in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                        if 0 <= X < 16 and 0 <= Y < 16 and src[Y][X] is not None:
                            s.p[y][x] = c
                            break

    def quantize(s, limit=9):
        """qube grids allow symbols 1-9: merge the rarest, closest colours until they fit."""
        def d2(a, b):
            return sum((int(a[i:i + 2], 16) - int(b[i:i + 2], 16)) ** 2 for i in (1, 3, 5))
        while True:
            cnt = {}
            for row in s.p:
                for c in row:
                    if c is not None:
                        cnt[c] = cnt.get(c, 0) + 1
            if len(cnt) <= limit:
                return
            best = None
            for a in cnt:
                for b in cnt:
                    if a < b:
                        lo, hi = (a, b) if cnt[a] <= cnt[b] else (b, a)
                        cost = d2(a, b) * cnt[lo]
                        if best is None or cost < best[0]:
                            best = (cost, lo, hi)
            _, lo, hi = best
            s.p = [[hi if c == lo else c for c in row] for row in s.p]

    def json(s):
        s.quantize()
        pal = ['#00000000']
        rows = []
        for row in s.p:
            r = ''
            for c in row:
                if c is None:
                    r += '0'
                else:
                    if c not in pal:
                        pal.append(c)
                    r += DIG[pal.index(c)]
            rows.append(r)
        return {'palette': pal, 'rows': rows}


def from_grid(g):
    cv = Cv(True)
    pal = g['palette']
    for y, row in enumerate(g['rows']):
        for x, ch in enumerate(row):
            i = DIG.index(ch)
            if i:
                cv.p[y][x] = pal[i][:7].upper()
    return cv


def shade_chunk(c, pts, R, hi=True):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    c.poly(pts, R[2])
    for y in range(16):
        for x in range(16):
            if c.p[y][x] == R[2]:
                v = (x - cx) + (y - cy)
                c.p[y][x] = R[3] if v < -1.5 else R[1] if v > 2 else R[2]
    if hi:
        top = min(pts, key=lambda p: p[1] + p[0] * .3)
        c.set(top[0], top[1] + 1, R[4])


# ---------------------------------------------------------------- items
def bottle(c, body, neck, glass, liquid, fill, cork, hi='#FFFFFF'):
    x0, y0, x1, y1 = body
    c.rect(x0, y0, x1, y1, glass)
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        c.p[y][x] = None
    nx0, ny0, nx1, ny1 = neck
    c.rect(nx0, ny0, nx1, ny1, glass)
    c.rect(x0 + 1, fill, x1 - 1, y1 - 1, liquid)
    c.rect(nx0 + 1, ny0, nx1 - 1, ny1, mix(glass, '#FFFFFF', .2))
    c.rect(nx0 + 1, max(ny0, fill - 1) if fill < ny1 else ny0, nx1 - 1, ny1, mix(glass, '#FFFFFF', .2))
    c.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 2, mix(liquid, '#FFFFFF', .55) if fill <= y0 + 1 else hi)
    c.set(x0 + 1, y0 + 1, hi)
    c.rect(x1 - 1, fill, x1 - 1, y1 - 1, mix(liquid, '#000000', .25))
    c.rect(x0 + 1, y1 - 1, x1 - 1, y1 - 1, mix(liquid, '#000000', .25))
    c.rect(nx0, ny0 - 2, nx1, ny0 - 1, cork)
    c.rect(nx0, ny0 - 2, nx1, ny0 - 2, mix(cork, '#FFFFFF', .25))


def pile(c, rnd, R, lumps, fleck=None, nf=0, base=(7.5, 11.5, 6.5, 5.0)):
    c.blob(base[0], base[1], base[2], base[3], R, ymax=13)
    for (x, y, rx, ry) in lumps:
        c.blob(x, y, rx, ry, R, ymax=13)
    if fleck:
        pts = [(x, y) for y in range(16) for x in range(16) if c.p[y][x] is not None]
        for x, y in rnd.sample(pts, min(nf, len(pts))):
            c.p[y][x] = fleck


def coil(c, cx, cy, rx, ry, inner, R):
    c.ring(cx, cy, rx, ry, inner, R, ridges=True)


def build_item(name, rnd):
    c = Cv()
    O = lambda h: ramp(h)
    if name in ('galena', 'pyrite'):
        R = O('#5f6672' if name == 'galena' else '#c9a93f')
        glint = '#F2F6FA' if name == 'galena' else '#FFF0A0'
        spec = [(8, 3, 4, 4, 2), (2, 7, 5, 5, 3), (10, 9, 3, 3, 2)] if name == 'galena' else [(2, 4, 4, 4, 2), (6, 7, 5, 5, 3), (11, 6, 3, 3, 2)]
        for (x, y, w, h, d) in spec:
            c.box(x, y, w, h, d, R)
            c.set(x + 1, y + d, glint)
            c.set(x + d + 1, y, glint)
            c.line(x, y + d, x, y + d + h - 1, R[3])
    elif name in ('lead_ingot', 'steel_ingot'):
        raise RuntimeError('template')
    elif name == 'quicklime':
        R = O('#e8e6dc')
        pile(c, rnd, R, [(5, 9, 3, 2.6), (10.5, 9, 3.2, 2.8), (7.5, 7, 2.8, 2.4)], '#b7b7ae', 7, base=(7.5, 11.5, 6.3, 3.6))
    elif name == 'wood_ash':
        R = O('#b4b2ac')
        pile(c, rnd, R, [(7.5, 8, 4, 3)], '#6d6c6a', 9, base=(7.5, 11.5, 6.5, 4.5))
    elif name == 'niter_earth':
        R = O('#7a5a3a')
        pile(c, rnd, R, [(7, 8, 4, 3)], '#F2F4F0', 10, base=(7.5, 11.5, 6.5, 4.8))
    elif name == 'saltpeter':
        R = O('#cfd6da')
        c.blob(7.5, 12, 6, 3, R, ymax=13)
        for (x, y, X, Y) in ((4, 10, 3, 5), (6, 10, 6, 3), (8, 10, 9, 4), (10, 10, 12, 5), (7, 10, 5, 6), (9, 10, 10, 7), (11, 11, 13, 8)):
            c.line(x, y, X, Y, '#E9F1F6')
            c.set(X, Y, '#FFFFFF')
            c.set(x + 1, y, '#9fb0bd')
    elif name == 'sulfur':
        R = O('#e6d535')
        for (x, y, rx, ry) in ((5, 10, 3.6, 3.2), (10.5, 10.5, 3.6, 3), (8, 6, 3.2, 3)):
            c.blob(x, y, rx, ry, R)
        for x, y in ((4, 11), (11, 11), (8, 7)):
            c.set(x, y, R[1])
    elif name == 'fermented_mash':
        Rc = O('#8a6a48')
        Rm = O('#cdb67f')
        c.blob(8, 8, 6.2, 5.8, Rc, ymin=8, ymax=13)
        c.blob(8, 8, 6.2, 2.7, Rc)
        c.blob(8, 8, 5, 2, Rm)
        c.blob(8, 6.6, 3.8, 2, Rm)
        for x, y in ((6, 6), (9, 6), (7, 8), (10, 8), (5, 8)):
            c.set(x, y, Rm[4])
            c.set(x + 1, y, Rm[1])
        c.line(3, 10, 12, 10, Rc[1])
    elif name == 'fuel_alcohol':
        bottle(c, (4, 6, 11, 13), (6, 3, 9, 5), '#d6eaf2', '#8cc9ee', 7, '#8a6238')
    elif name == 'sulfuric_acid':
        bottle(c, (2, 6, 13, 13), (5, 3, 10, 5), '#d3e6dc', '#c4d63c', 7, '#e0e0d8')
        c.rect(2, 13, 13, 13, '#8a8f80')
    elif name == 'photoresist':
        bottle(c, (5, 7, 10, 13), (7, 4, 8, 6), '#7a4418', '#e0502a', 9, '#202228', hi='#d9a26a')
    elif name == 'coke':
        R = O('#8d9096')
        for (x, y, rx, ry) in ((5, 9, 3.6, 3.2), (10.5, 9.5, 3.8, 3.4), (8, 5.5, 3.2, 2.8), (6.5, 12, 3, 1.8)):
            c.blob(x, y, rx, ry, R)
        for x, y in ((4, 9), (6, 8), (10, 8), (12, 10), (8, 5), (9, 7), (7, 11), (11, 12), (5, 11)):
            c.set(x, y, '#4a4d52')
            c.set(x - 1, y - 1, R[4])
    elif name == 'coal_tar':
        Ri = O('#6b6f78')
        T = '#14151a'
        for y in range(6, 14):
            a, b = 3 + (y - 6) * 0.3, 12 - (y - 6) * 0.3
            c.vshade(0, y, 0, y, Ri, cols=lambda yy, a=round(a), b=round(b): (a, b))
        c.line(4, 9, 11, 9, Ri[0])
        c.blob(7.5, 6, 5.5, 2.2, Ri)
        c.blob(7.5, 6, 4.5, 1.6, [T, T, T, T, T])
        c.line(5, 5, 7, 5, '#6a7080')
        c.set(9, 6, '#3a3f4c')
        c.set(12, 11, Ri[0])
    elif name == 'phenolic_resin':
        R = O('#b5651d')
        c.box(2, 5, 9, 5, 3, R, top=mix('#b5651d', '#FFD8A0', .45), side=R[1])
        c.line(3, 8, 10, 8, R[3])
        for x, y in ((4, 11), (8, 12), (6, 10), (10, 11)):
            c.set(x, y, R[0])
            c.set(x + 1, y - 1, R[4])
        c.set(5, 6 + 2, '#FFE6B8')
    elif name == 'metallurgical_silicon':
        R = O('#6b7078')
        shade_chunk(c, [(8, 5), (11, 3), (14, 6), (13, 10), (10, 11), (8, 9)], R)
        shade_chunk(c, [(10, 11), (13, 10), (14, 13), (11, 14)], R)
        shade_chunk(c, [(2, 10), (5, 7), (8, 8), (9, 12), (6, 13), (3, 13)], R)
        for x, y in ((4, 9), (12, 5), (7, 11), (11, 12)):
            c.set(x, y, '#C9D0DA')
        c.line(5, 9, 7, 10, R[0])
    elif name == 'polysilicon':
        R = O('#b7bec8')
        for pts in (([(2, 8), (4, 6), (7, 7), (7, 10), (4, 11)]), ([(8, 3), (11, 3), (12, 6), (9, 7)]), ([(9, 9), (13, 8), (14, 11), (11, 12)]), ([(3, 12), (7, 11), (8, 14), (4, 14)]), ([(5, 3), (7, 2), (7, 5), (5, 5)]), ([(10, 13), (13, 13), (13, 14), (10, 14)])):
            shade_chunk(c, pts, R)
        for x, y in ((4, 8), (10, 9), (9, 4), (6, 12)):
            c.set(x, y, '#FFFFFF')
    elif name == 'silicon_boule':
        R = O('#59606b')
        cols = lambda y: {1: (7, 8), 2: (6, 9), 3: (5, 10), 4: (4, 11), 13: (5, 10), 14: (6, 9)}.get(y, (4, 11))
        c.vshade(4, 1, 11, 14, R, cols=cols)
        for y in (8, 11):
            for x in range(4, 12, 2):
                c.set(x, y, R[1])
        c.set(7, 1, R[4])
        c.line(4, 5, 11, 5, R[1])
    elif name in ('silicon_wafer', 'patterned_wafer'):
        pat = name == 'patterned_wafer'
        c.blob(8, 9.2, 7, 4.2, [mix('#3d4658', '#000000', .2)] * 5)
        for y in range(16):
            for x in range(16):
                nx, ny = (x - 8) / 7, (y - 8) / 4.2
                if nx * nx + ny * ny <= 1:
                    if pat:
                        gx, gy = (x - 2) % 3 == 0, (y - 4) % 2 == 0
                        if gx or gy:
                            col = '#34406a'
                        else:
                            col = ['#8a6fd0', '#4fb0c8', '#c070b0', '#d6b24a'][((x - 2) // 3 + (y - 4) // 2) % 4]
                    else:
                        col = '#8996ab' if x + y * 1.5 > 17 else '#9fabc0'
                        if abs((x - 8) * .8 + (y - 8) * 1.4 + 1) < 1.1:
                            col = '#dce5f3'
                    c.set(x, y, col)
        if not pat:
            c.set(4, 8, '#6e7b92')
    elif name == 'silicon_die':
        c.box(3, 3, 9, 9, 1, ramp('#4b5776'), top='#8fa0c8', side='#2c3550', front='#4b5776')
        for y in (5, 7, 9, 11):
            for x in range(5, 11):
                if (x + y) % 5:
                    c.set(x, y, '#9db2e0')
        for y in (6, 8, 10):
            c.set(5 + (y % 4), y, '#9db2e0')
            c.set(9 - (y % 3), y, '#9db2e0')
        for y in (5, 7, 9, 11):
            c.set(3, y, '#d8b45a')
            c.set(11, y, '#d8b45a')
        c.rect(7, 6, 8, 7, '#d4e0ff')
    elif name in ('chip_package', 'chip'):
        pk = name == 'chip_package'
        body = '#d9ceb6' if pk else '#2a2b31'
        Rb = ramp(body)
        c.rect(2, 5, 13, 10, Rb[2])
        c.rect(2, 5, 13, 5, Rb[3] if pk else '#4a4c55')
        c.rect(2, 10, 13, 10, Rb[1])
        c.rect(13, 5, 13, 10, Rb[1])
        Rp = ramp('#b7bec6')
        for x in (3, 5, 7, 9, 11):
            c.rect(x, 3, x, 4, Rp[3])
            c.rect(x, 11, x, 12, Rp[2])
            c.set(x, 3, Rp[4])
        if pk:
            c.rect(5, 6, 10, 9, '#3b3328')
            c.rect(5, 6, 10, 6, '#241f18')
            for x in (5, 7, 9):
                c.set(x, 9, '#d8b45a')
            for x in (6, 8, 10):
                c.set(x, 6 + 1, '#d8b45a')
            c.rect(2, 7, 2, 8, '#8a7f66')
        else:
            c.rect(2, 7, 2, 8, '#101114')
            c.set(3, 9, '#6c6f7a')
            for x in (6, 8, 10, 11):
                c.set(x, 7, '#70747f')
            for x in (5, 6, 7, 9):
                c.set(x, 9, '#4f525c')
    elif name == 'copper_wire':
        R = O('#c8794a')
        coil(c, 7.5, 7.5, 6.3, 5.6, .38, R)
        c.set(7, 7, R[0])
        c.line(12, 11, 14, 13, R[2])
        c.set(14, 13, R[4])
    elif name == 'solder':
        R = O('#aeb6bd')
        coil(c, 6, 9, 5, 4.6, .4, R)
        c.line(9, 6, 14, 2, R[3])
        c.line(9, 7, 14, 3, R[1])
        c.set(14, 2, R[4])
    elif name == 'fine_wire':
        Rw = O('#8a6b48')
        for y in range(4, 12):
            for x in range(4, 13):
                c.set(x, y, '#d9a441' if (x % 2 == 0) else '#a8702a')
        c.rect(4, 4, 12, 4, '#f0c870')
        c.rect(4, 11, 12, 11, '#7a5220')
        c.blob(3.5, 8, 1.9, 5.8, Rw)
        c.blob(12.5, 8, 1.9, 5.8, Rw)
        c.set(3, 8, Rw[0])
        c.line(13, 12, 14, 14, '#d9a441')
    elif name == 'circuit_board':
        G, GD, CU = '#2e8b4e', '#1e5c34', '#e0a850'
        c.rect(2, 2, 13, 11, G)
        c.rect(2, 2, 13, 2, '#49a96a')
        c.rect(2, 11, 13, 11, GD)
        c.rect(13, 2, 13, 11, GD)
        for x in range(4, 12, 2):
            c.rect(x, 12, x, 13, '#d9b04a')
        for pts in (((3, 4), (6, 4), (6, 6)), ((12, 4), (9, 4), (9, 6)), ((3, 9), (6, 9), (6, 8)), ((12, 9), (9, 9), (9, 8)), ((3, 6), (4, 6)), ((12, 6), (11, 6))):
            for a, b in zip(pts, pts[1:]):
                c.line(*a, *b, CU)
        c.rect(6, 6, 9, 8, '#24252b')
        c.rect(6, 6, 9, 6, '#4a4c55')
        c.set(3, 3, '#14301c')
        c.set(12, 10, '#14301c')
    elif name == 'transformer':
        Ri, Rc = O('#8a929b'), O('#c8794a')
        c.rect(2, 2, 13, 13, Ri[2])
        c.rect(2, 2, 13, 3, Ri[3])
        c.rect(2, 12, 13, 13, Ri[1])
        for x in range(6, 10):
            for y in range(4, 12):
                c.set(x, y, Ri[2] if x % 2 else Ri[1])
        c.rect(7, 4, 8, 11, Ri[0])
        for x0 in (2, 10):
            c.rect(x0, 4, x0 + 3, 11, Rc[2])
            for y in range(4, 12, 2):
                c.rect(x0, y, x0 + 3, y, Rc[3])
            c.rect(x0 + 3, 4, x0 + 3, 11, Rc[1])
            c.rect(x0, 11, x0 + 3, 11, Rc[0])
    elif name == 'steel_plate':
        R = O('#8f9aa3')
        c.box(1, 3, 9, 3, 5, R, top=R[3])
        for (k, ox) in ((1, 1), (1, 7), (3, 1), (3, 7)):
            c.set(1 + 5 - k + ox, 3 + k, R[0])
        c.line(1, 8, 9, 8, R[4])
        c.line(1, 10, 9, 10, R[1])
    elif name == 'machine_parts_blank':
        R = O('#575b63')
        c.blob(8, 8, 5.9, 5.6, R, rough=lambda x, y: 1 + .12 * math.sin(x * 2.1 + y * 1.3))
        for x, y in ((5, 6), (10, 7), (7, 10), (11, 10), (4, 9)):
            c.set(x, y, '#7a5a44')
        c.set(8, 8, R[0])
        c.set(9, 8, R[0])
    elif name == 'machine_parts':
        R = O('#a3adb6')
        cx = cy = 6
        c.gear(cx, cy, 3.7, 3.7, 4, R, hole=1.4)
        for (x0, y0, x1, y1) in ((-1, -5, 1, -4), (-1, 4, 1, 5), (-5, -1, -4, 1), (4, -1, 5, 1)):
            c.rect(cx + x0, cy + y0, cx + x1, cy + y1, R[2])
        for sx in (-1, 1):
            for sy in (-1, 1):
                for (dx, dy) in ((3, 4), (4, 3), (4, 4)):
                    c.set(cx + sx * dx, cy + sy * dy, R[3] if (sx < 0 or sy < 0) else R[1])
        c.rect(cx - 1, cy - 5, cx + 1, cy - 5, R[3])
        c.rect(cx - 5, cy - 1, cx - 5, cy + 1, R[3])
        c.rect(cx - 1, cy + 5, cx + 1, cy + 5, R[1])
        c.rect(cx + 5, cy - 1, cx + 5, cy + 1, R[1])
        Rb = O('#7d8691')
        c.box(8, 11, 5, 2, 1, Rb)
        c.rect(7, 10, 8, 14, Rb[3])
        c.rect(7, 10, 7, 14, Rb[4])
        c.rect(8, 10, 8, 14, Rb[1])
        c.rect(7, 13, 9, 14, Rb[2])
        c.rect(9, 12, 14, 13, Rb[2])
        c.rect(9, 12, 14, 12, Rb[3])
        c.rect(9, 13, 14, 13, Rb[1])
        c.rect(12, 11, 14, 14, Rb[2])
        c.rect(12, 11, 14, 11, Rb[4])
        c.rect(12, 14, 14, 14, Rb[0])
    elif name == 'boiler':
        R = O('#7e8790')
        c.hshade(2, 5, 13, 11, R, rows=lambda x: (5, 11))
        c.rect(6, 5, 6, 11, R[1])
        c.rect(10, 5, 10, 11, R[1])
        for x in (6, 10):
            for y in (6, 8, 10):
                c.set(x, y, R[4])
        c.rect(7, 2, 9, 4, R[2])
        c.rect(7, 2, 9, 2, R[3])
        c.rect(9, 3, 9, 4, R[1])
        c.rect(2, 12, 13, 13, '#4a4f58')
        c.rect(3, 12, 3, 13, '#2f3238')
        c.rect(12, 12, 12, 13, '#2f3238')
        c.rect(2, 5, 2, 11, R[4])
        c.rect(13, 5, 13, 11, R[0])
        c.rect(13, 6, 13, 10, '#3b2c28')
    elif name == 'steam_engine':
        R, Rf = O('#7e8790'), O('#4a4f58')
        c.rect(1, 13, 14, 13, '#3b3f47')
        c.hshade(1, 5, 6, 11, R)
        c.rect(1, 3, 2, 4, R[1])
        c.rect(5, 4, 6, 5, R[3])
        c.rect(7, 8, 10, 8, '#d6dde3')
        c.rect(7, 9, 10, 9, '#8b949d')
        c.ring(11.5, 8.5, 3.8, 3.8, .55, Rf, ridges=False)
        for (X, Y) in ((11, 5), (11, 12), (8, 8), (15, 8), (9, 6), (14, 11), (14, 6), (9, 11)):
            c.line(11, 8, min(14, X), Y, Rf[3])
        c.blob(11.5, 8.5, 1.6, 1.6, R)
        c.rect(11, 12, 12, 13, '#3b3f47')
        c.rect(2, 12, 6, 12, R[0])
    elif name == 'dynamo':
        Rc, Ri = O('#c8794a'), O('#7e8790')
        c.rect(1, 13, 14, 13, '#3b3f47')
        c.rect(2, 5, 4, 12, Ri[2])
        c.rect(11, 5, 13, 12, Ri[2])
        c.rect(2, 5, 4, 6, '#c24a3a')
        c.rect(11, 5, 13, 6, '#3a5fc2')
        c.rect(2, 12, 4, 12, Ri[1])
        c.rect(11, 12, 13, 12, Ri[1])
        c.rect(2, 7, 2, 11, Ri[3])
        c.rect(11, 7, 11, 11, Ri[3])
        c.rect(5, 4, 10, 12, Rc[2])
        for y in range(4, 13, 2):
            c.rect(5, y, 10, y, Rc[3])
        c.rect(10, 4, 10, 12, Rc[1])
        c.rect(5, 12, 10, 12, Rc[0])
        c.rect(7, 7, 8, 9, Ri[0])
    elif name == 'combustion_engine':
        R = O('#6a6f78')
        c.rect(2, 8, 13, 13, R[2])
        c.rect(2, 8, 13, 8, R[3])
        c.rect(2, 13, 13, 13, R[0])
        c.rect(13, 8, 13, 13, R[1])
        for x0 in (3, 7, 11):
            c.rect(x0, 4, x0 + 2, 7, R[2])
            for y in (4, 6):
                c.rect(x0, y, x0 + 2, y, R[3])
            c.rect(x0 + 2, 4, x0 + 2, 7, R[1])
            c.set(x0 + 1, 2, '#e8e0c8')
            c.set(x0 + 1, 3, '#a8a49a')
        c.rect(4, 10, 11, 11, R[1])
        c.rect(4, 10, 11, 10, R[0])
        c.set(12, 10, '#c9a23c')
        c.set(12, 11, '#c9a23c')
        c.rect(1, 10, 1, 11, '#b09a60')
    elif name == 'steel_rail':
        R = O('#8f9aa3')
        prof = [(0, 0, 5), (2, 1, 2), (2, 2, 2), (2, 3, 2), (-1, 4, 7)]  # (x offset, y, width)
        for t in range(6, 0, -1):
            for (ox, y, w) in prof:
                for i in range(w):
                    c.set(2 + ox + i + t + 1, 8 + y - t, R[3] if y in (0, 4) else R[1])
        for (ox, y, w) in prof:
            for i in range(w):
                c.set(2 + ox + i + 1, 8 + y, R[4] if y in (0, 4) else R[2])
        for t in range(1, 7, 2):
            c.set(3 + 2 + t + 1, 8 - t, R[4])
    elif name == 'sleeper':
        R = O('#3b2a1c')
        c.box(1, 6, 11, 4, 3, R, top='#5a4330', side='#241810', front='#3b2a1c')
        for x in range(3, 12, 3):
            c.line(x, 9, x + 1, 12, R[1])
        c.line(2, 10, 10, 10, R[0])
        c.rect(12, 6, 14, 10, '#6a4e34')
        c.rect(12, 6, 14, 6, '#7d5f44')
    elif name == 'wooden_wheel':
        R = O('#9a7444')
        c.ring(7.5, 7.5, 6.6, 6.6, .66, R)
        for a in range(8):
            ang = a * math.pi / 4
            c.line(7.5, 7.5, 7.5 + 4.6 * math.cos(ang), 7.5 + 4.6 * math.sin(ang), R[3] if a % 2 else R[2])
        c.blob(7.5, 7.5, 2, 2, ramp('#6a4a28'))
        c.set(7, 7, '#26180c')
        c.set(8, 8, '#26180c')
    elif name == 'vehicle_frame':
        R = O('#9a7444')
        c.rect(1, 3, 14, 4, R[2])
        c.rect(1, 3, 14, 3, R[3])
        c.rect(1, 11, 14, 12, R[2])
        c.rect(1, 11, 14, 11, R[3])
        c.rect(1, 12, 14, 12, R[1])
        c.rect(1, 4, 14, 4, R[1])
        for x in (3, 7, 11):
            c.rect(x, 5, x + 1, 10, R[2])
            c.rect(x, 5, x, 10, R[3])
            c.rect(x + 1, 5, x + 1, 10, R[1])
            c.set(x, 3, '#3a3a40')
            c.set(x + 1, 12, '#3a3a40')
        c.rect(1, 5, 1, 10, R[1])
        c.rect(14, 5, 14, 10, R[1])
    elif name == 'harness':
        L = O('#8a5a30')
        c.ring(8, 6, 5.6, 5, .5, L)
        c.line(4, 11, 4, 14, L[2])
        c.line(5, 11, 5, 14, L[1])
        c.line(11, 11, 11, 14, L[2])
        c.line(12, 10, 12, 14, L[1])
        c.rect(7, 11, 8, 14, L[2])
        c.rect(7, 11, 7, 14, L[3])
        c.rect(6, 9, 9, 12, '#b8bfc7')
        c.rect(7, 10, 8, 11, '#303244')
        c.rect(6, 9, 9, 9, '#e8eef2')
        for x in (3, 5, 7, 9, 11):
            c.set(x, 2, L[3])
    elif name == 'gun_carriage':
        W = {'l': '#d0aa6c', 'w': '#a8824f', 'd': '#6a4e2c'}
        c.line(10, 6, 14, 12, W['w'])
        c.line(11, 6, 14, 11, W['d'])
        c.line(9, 6, 13, 12, W['l'])
        c.art(0, 0, [
            '................',
            '................',
            '....lw..wd......',
            '....lw..wd......',
            '....lwwwwd......',
            '....lwwwwd......',
            '....lllll.......',
            '...ll.w.ll......',
            '..llw.w.wll.....',
            '..ll.www.ll.....',
            '..llwwdwwll.....',
            '..ll.www.ll.....',
            '..llw.w.wll.....',
            '...ll.w.ll......',
            '....lllll.......',
        ], W)
        c.rect(2, 10, 3, 10, W['l'])
    elif name in ('musket', 'musket_stock'):
        W, I = O('#8a5a30'), O('#8f98a2')
        c.poly([(1, 14), (1, 11), (4, 9), (8, 6), (11, 4), (12, 5), (9, 9), (6, 12), (3, 14)], W[2])
        c.line(1, 11, 4, 9, W[3])
        c.line(4, 9, 11, 4, W[3])
        c.line(1, 14, 3, 14, W[1])
        c.line(3, 14, 12, 5, W[1])
        c.line(2, 12, 2, 13, W[3])
        if name == 'musket':
            c.line(7, 7, 14, 0, I[3])
            c.line(8, 7, 14, 1, I[2])
            c.line(9, 7, 14, 2, I[1])
            for (x, y) in ((11, 3), (13, 1)):
                c.set(x, y, '#c9a23c')
            c.rect(7, 7, 9, 9, I[2])
            c.set(7, 7, I[3])
            c.line(6, 6, 7, 7, I[3])
            c.set(5, 5, I[4])
            c.line(5, 12, 7, 13, I[1])
            c.set(8, 9, I[0])
    elif name == 'musket_barrel_blank':
        R = O('#4d5159')
        c.line(2, 13, 13, 2, R[3])
        c.line(3, 13, 14, 2, R[2])
        c.line(4, 13, 14, 3, R[1])
        for x, y in ((5, 9), (9, 5), (12, 4), (7, 8)):
            c.set(x, y, '#7a5a44')
        for x, y in ((6, 10), (8, 8), (11, 5), (4, 12)):
            c.set(x, y, R[0])
    elif name == 'musket_barrel':
        R = O('#a3adb6')
        c.line(2, 13, 13, 2, R[4])
        c.line(3, 13, 14, 2, R[3])
        c.line(4, 13, 14, 3, R[2])
        c.line(5, 13, 14, 4, R[1])
        for (x, y) in ((6, 9), (10, 5)):
            c.set(x, y, '#c9a23c')
            c.set(x + 1, y, '#a07f20')
            c.set(x + 2, y, '#a07f20')
        c.set(14, 2, '#303244')
        c.set(13, 2, R[0])
        c.rect(3, 13, 5, 13, R[2])
    elif name == 'musket_lock_blank':
        R = O('#4f535b')
        c.poly([(2, 8), (12, 8), (13, 10), (12, 12), (3, 12), (2, 11)], R[2])
        c.rect(3, 8, 11, 8, R[3])
        c.rect(3, 12, 12, 12, R[1])
        for x, y in ((4, 10), (7, 9), (10, 11), (9, 9), (5, 11)):
            c.set(x, y, R[0] if (x + y) % 2 else '#7a5a44')
        c.rect(6, 5, 7, 7, R[2])
        c.set(6, 5, R[3])
    elif name == 'musket_lock':
        S = {'S': '#8d959e', 's': '#59606b', 'L': '#c8cfd6', 'F': '#f0ece0', 'B': '#c9a23c', 'd': '#303244'}
        c.art(1, 3, [
            '..FFF..........',
            '..SSSS...LLL...',
            '...SSS..LSSs...',
            '....SS..LSs....',
            '....SSS.LSs....',
            '.....SSBBLs....',
            '.LLLLLLLLLLLL..',
            'LSSSSSSSSSSSSs.',
            'LSSdSSSSSSSdSs.',
            'LSSSSSSSSSSSSs.',
            '.ssssssssssss..',
        ], S)
    elif name == 'musket_ball':
        R = O('#7a8290')
        for (x, y) in ((4.5, 10.5), (10, 10.5), (7.2, 6)):
            c.blob(x, y, 3, 3, R)
            c.set(x - 1, y - 1, '#d9dee6')
    elif name == 'paper_cartridge':
        P = O('#d8c49a')
        for i, col in enumerate((P[4], P[3], P[2], P[1])):
            c.line(2 + i, 12, 9 + i, 5, col)
        c.line(2, 13, 4, 13, P[0])
        c.line(10, 5, 12, 3, P[2])
        c.line(11, 5, 13, 3, P[1])
        c.line(12, 3, 13, 2, P[3])
        c.line(9, 4, 12, 7, '#7a4a28')
        c.line(9, 5, 11, 7, '#7a4a28')
        c.line(5, 11, 8, 8, P[1])
        c.line(4, 10, 7, 7, P[2])
    elif name == 'round_shot':
        R = O('#454951')
        c.blob(8, 8, 5.6, 5.6, R)
        c.rect(5, 5, 6, 6, '#8f96a2')
        c.set(5, 5, '#E6EAF0')
        c.set(6, 6, '#6b717c')
    elif name == 'stone_shot':
        R = O('#8b887c')
        c.blob(8, 8, 5.6, 5.5, R, rough=lambda x, y: 1 + .28 * math.sin(x * 1.7 + y * 2.3) + (.18 if (x + y * 3) % 4 == 0 else 0))
        for x, y in ((6, 6), (10, 9), (7, 11), (9, 5)):
            c.set(x, y, R[0])
        for x, y in ((5, 8), (11, 7)):
            c.set(x, y, R[4])
    elif name == 'powder_charge':
        R = O('#c9c3b0')
        c.blob(8, 10, 5.4, 4, R)
        c.rect(6, 5, 9, 7, R[2])
        c.rect(6, 5, 6, 7, R[3])
        c.rect(9, 5, 9, 7, R[1])
        c.line(5, 2, 6, 5, R[3])
        c.line(8, 2, 8, 5, R[2])
        c.line(11, 2, 9, 5, R[1])
        c.rect(5, 6, 10, 6, '#7a4a28')
        c.set(10, 7, '#7a4a28')
        c.line(5, 10, 7, 12, R[1])
        c.line(10, 9, 11, 11, R[1])
        c.set(5, 7, '#6a6a68')
        c.set(11, 12, '#6a6a68')
    elif name == 'explosive_shell':
        S = O('#7d8691')
        cols = lambda y: {1: (7, 8), 2: (6, 9), 3: (6, 9), 4: (5, 10), 5: (5, 10)}.get(y, (5, 10))
        c.vshade(5, 1, 10, 13, S, cols=cols)
        c.rect(7, 1, 8, 3, '#d6ae44')
        c.rect(6, 2, 6, 3, '#e8c864')
        c.rect(9, 2, 9, 3, '#a07f20')
        c.rect(7, 1, 7, 2, '#fff0a0')
        c.rect(5, 10, 10, 11, '#c8794a')
        c.rect(5, 10, 10, 10, '#e8a070')
        c.rect(5, 11, 10, 11, '#8a4a2a')
        c.rect(5, 13, 10, 13, S[0])
        c.rect(6, 6, 6, 9, S[4])
    elif name == 'cannon_barrel':
        R = O('#b28b45')
        c.hshade(3, 0, 13, 0, R, rows=lambda x: (4, 11) if x < 7 else (5, 10) if x < 12 else (4, 11))
        c.rect(12, 4, 14, 11, R[2])
        c.hshade(12, 4, 14, 11, R)
        c.rect(6, 4, 6, 11, R[0])
        c.rect(11, 5, 11, 10, R[0])
        c.rect(8, 2, 9, 3, R[1])
        c.rect(8, 12, 9, 13, R[1])
        c.rect(8, 2, 9, 2, R[3])
        c.blob(1.8, 7.5, 1.8, 2.2, R)
        c.rect(3, 7, 3, 8, R[1])
        c.rect(14, 6, 14, 9, '#2a2420')
    elif name == 'bombard_barrel':
        R = O('#3d4048')
        Rh = O('#6a6e78')
        c.hshade(2, 3, 13, 12, R)
        for x in (3, 6, 9):
            c.rect(x, 3, x + 1, 12, Rh[2])
            c.rect(x, 3, x + 1, 3, Rh[4])
            c.rect(x, 12, x + 1, 12, Rh[0])
        c.blob(13, 7.5, 1.6, 3.6, R)
        c.rect(13, 5, 14, 10, '#14151a')
        c.rect(13, 5, 14, 5, Rh[3])
        c.rect(12, 3, 13, 3, Rh[3])
        c.rect(4, 5, 4, 5, R[4])
        c.rect(2, 3, 2, 12, R[0])
    else:
        raise KeyError(name)
    c.outline()
    return c


# ---------------------------------------------------------------- blocks
def planks(c, x0, y0, x1, y1, base, rnd):
    R = ramp(base)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            k = (y - y0) % 4
            c.set(x, y, R[1] if k == 3 else R[3] if k == 0 else R[2])
    for j, y in enumerate(range(y0, y1 + 1, 4)):
        c.set(x0 + 3 + j * 5 % (x1 - x0 + 1), y + 1, R[1])
        c.set(x0 + 3 + j * 5 % (x1 - x0 + 1), y + 2, R[1])


def bricks(c, x0, y0, x1, y1, base, mortar):
    R = ramp(base)
    for y in range(y0, y1 + 1):
        row = (y - y0) // 4
        for x in range(x0, x1 + 1):
            if (y - y0) % 4 == 3 or (x - x0 + (row % 2) * 4) % 8 == 7:
                c.set(x, y, mortar)
            else:
                c.set(x, y, R[3] if (y - y0) % 4 == 0 else R[2])


def build_block(name, rnd):
    c = Cv(True)
    if name == 'workshop':
        planks(c, 0, 0, 15, 15, '#b08a55', rnd)
        D = '#5a4028'
        c.rect(0, 0, 15, 1, D)
        c.rect(0, 14, 15, 15, D)
        c.rect(0, 0, 1, 15, D)
        c.rect(14, 0, 15, 15, D)
        c.rect(2, 7, 13, 7, D)
        c.rect(3, 3, 9, 5, '#c8cfd6')
        c.rect(3, 3, 9, 3, '#eef2f6')
        for x in range(3, 10):
            if x % 2:
                c.set(x, 6, '#6a717b')
        c.rect(10, 3, 12, 6, D)
        c.set(11, 4, '#b08a55')
        c.rect(3, 12, 9, 12, D)
        c.rect(10, 10, 13, 13, D)
        c.rect(10, 10, 13, 10, '#8a6a42')
        c.rect(10, 11, 10, 13, '#8a6a42')
    elif name == 'assembly_yard':
        planks(c, 0, 0, 15, 15, '#8a6b42', rnd)
        D = ramp('#4a3320')
        for x0 in (0, 13):
            c.rect(x0, 0, x0 + 2, 15, D[2])
            c.rect(x0, 0, x0, 15, D[3])
            c.rect(x0 + 2, 0, x0 + 2, 15, D[0])
        for y0 in (0, 13):
            c.rect(0, y0, 15, y0 + 2, D[2])
            c.rect(0, y0, 15, y0, D[3])
            c.rect(0, y0 + 2, 15, y0 + 2, D[0])
        c.rect(0, 6, 15, 7, D[2])
        c.rect(0, 6, 15, 6, D[3])
        c.rect(0, 7, 15, 7, D[0])
        c.line(3, 3, 12, 6, D[1])
        c.line(12, 3, 3, 6, D[1])
        c.line(3, 8, 12, 12, D[1])
        c.line(12, 8, 3, 12, D[1])
        c.line(8, 3, 8, 8, '#cbbf9a')
        c.rect(7, 9, 9, 10, '#7d8691')
        c.rect(7, 9, 9, 9, '#c8cfd6')
        c.rect(7, 11, 8, 12, '#3a3a40')
        c.set(7, 12, '#9a9aa2')
    elif name == 'niter_bed':
        c.rect(0, 0, 15, 15, '#3d2b1c')
        for y in range(16):
            for x in range(16):
                r = rnd.random()
                if r < .15:
                    c.p[y][x] = '#2a1c10'
                elif r < .3:
                    c.p[y][x] = '#51392a'
        D = ramp('#8a6b42')
        c.rect(0, 0, 15, 1, D[2])
        c.rect(0, 14, 15, 15, D[2])
        c.rect(0, 0, 1, 15, D[2])
        c.rect(14, 0, 15, 15, D[2])
        c.rect(0, 0, 15, 0, D[3])
        c.rect(0, 15, 15, 15, D[0])
        c.rect(15, 0, 15, 15, D[0])
        c.rect(0, 0, 0, 15, D[3])
        c.set(1, 1, D[0])
        c.set(14, 1, D[0])
        c.set(1, 14, D[0])
        c.set(14, 14, D[0])
        for (x, y, X, Y) in ((3, 4, 6, 3), (8, 6, 11, 5), (4, 9, 7, 10), (9, 11, 12, 10), (6, 13, 8, 12)):
            c.line(x, y, X, Y, '#d8c060')
            c.set(x, y, '#a89038')
        for (x, y) in ((5, 6), (10, 4), (11, 8), (3, 7), (7, 8), (6, 11), (12, 12), (9, 3), (4, 12)):
            c.set(x, y, '#F2F4F0')
            c.set(x + 1, y, '#b9c6d0')
    elif name == 'powder_mill':
        planks(c, 0, 0, 15, 15, '#9a7a4c', rnd)
        D = ramp('#4a3320')
        c.rect(0, 0, 15, 1, D[2])
        c.rect(0, 14, 15, 15, D[2])
        c.rect(0, 0, 1, 15, D[2])
        c.rect(14, 0, 15, 15, D[2])
        c.rect(0, 0, 15, 0, D[3])
        c.rect(0, 15, 15, 15, D[0])
        c.rect(15, 0, 15, 15, D[0])
        S = ramp('#8b887c')
        c.blob(7.5, 7.5, 5.8, 5.8, S)
        c.ring(7.5, 7.5, 5.8, 5.8, .68, S)
        c.blob(7.5, 7.5, 4, 4, [S[2], S[2], S[2], S[3], S[3]])
        for a in range(8):
            ang = a * math.pi / 4
            c.line(7.5, 7.5, 7.5 + 4.2 * math.cos(ang), 7.5 + 4.2 * math.sin(ang), S[1])
        c.blob(7.5, 7.5, 1.8, 1.8, ramp('#4a3320'))
        c.set(7, 7, '#1a1008')
    elif name == 'foundry':
        bricks(c, 0, 0, 15, 15, '#b0794f', '#6b5a50')
        D = ramp('#3b3836')
        c.rect(0, 15, 15, 15, D[1])
        for y in range(5, 15):
            a = 4 if y > 6 else 5 if y == 6 else 6
            b = 11 if y > 6 else 10 if y == 6 else 9
            if y == 5:
                a, b = 6, 9
            for x in range(a, b + 1):
                t = (y - 5) / 9
                edge = x in (a, b) or y == 5
                c.set(x, y, '#3a1a10' if edge else '#f08a28' if t < .5 else '#ffb43a')
        c.rect(6, 9, 9, 13, '#ffd24a')
        c.rect(7, 10, 8, 12, '#fff0a0')
        c.rect(3, 4, 12, 4, '#3b3836')
        c.rect(3, 3, 12, 3, '#6a6e78')
        c.rect(5, 13, 10, 14, '#3a1a10')
        c.rect(6, 14, 9, 14, '#d86a20')
    elif name == 'chemical_works':
        bricks(c, 0, 0, 15, 15, '#5e3c34', '#3a2420')
        Cu = ramp('#e08e52')
        c.rect(1, 13, 14, 15, '#4a4f58')
        c.blob(5, 10.5, 4.2, 4, Cu)
        c.rect(4, 5, 6, 7, Cu[2])
        c.rect(4, 5, 4, 7, Cu[3])
        c.rect(6, 5, 6, 7, Cu[1])
        c.rect(4, 2, 14, 3, Cu[2])
        c.rect(4, 2, 14, 2, Cu[3])
        c.rect(4, 3, 14, 3, Cu[1])
        c.rect(4, 3, 5, 6, Cu[2])
        c.rect(12, 2, 14, 12, Cu[2])
        c.rect(12, 2, 12, 12, Cu[3])
        c.rect(14, 2, 14, 12, Cu[0])
        for y in (5, 8, 11):
            c.rect(11, y, 15, y, Cu[0])
        c.rect(11, 12, 15, 13, '#8ab0c0')
        c.rect(11, 12, 15, 12, '#d6eaf2')
        c.set(4, 9, Cu[4])
        c.set(3, 10, Cu[4])
        c.rect(3, 12, 7, 12, Cu[0])
    elif name == 'machine_shop':
        bricks(c, 0, 0, 15, 15, '#6c6560', '#46413d')
        Ir = ramp('#7e8790')
        c.rect(1, 1, 14, 14, '#2c2e34')
        for (x0, y0, X, Y) in ((0, 0, 15, 1), (0, 14, 15, 15), (0, 0, 1, 15), (14, 0, 15, 15)):
            c.rect(x0, y0, X, Y, Ir[2])
        c.rect(0, 0, 15, 0, Ir[3])
        c.rect(0, 15, 15, 15, Ir[0])
        c.rect(15, 0, 15, 15, Ir[0])
        for p in ((0, 0), (14, 0), (0, 14), (14, 14)):
            c.set(p[0] + 1, p[1] + 1, Ir[4])
        c.rect(2, 2, 13, 13, '#2c2e34')
        c.line(4, 8, 11, 12, '#15161a')
        c.line(4, 5, 11, 9, '#15161a')
        c.gear(5.5, 5.5, 2.3, 3.6, 8, ramp('#a3adb6'), hole=.9)
        c.ring(10.5, 10.5, 3.6, 3.6, .62, ramp('#7e8790'))
        for a in range(4):
            ang = a * math.pi / 2 + .3
            c.line(10.5, 10.5, 10.5 + 2.4 * math.cos(ang), 10.5 + 2.4 * math.sin(ang), '#a3adb6')
        c.blob(10.5, 10.5, 1.3, 1.3, ramp('#a3adb6'))
    elif name == 'electronics_works':
        W = ramp('#e2e6ea')
        c.rect(0, 0, 15, 15, W[2])
        c.rect(0, 0, 15, 0, W[4])
        c.rect(0, 0, 0, 15, W[3])
        c.rect(0, 15, 15, 15, W[0])
        c.rect(15, 0, 15, 15, W[0])
        c.rect(0, 7, 15, 7, W[1])
        c.rect(0, 8, 15, 8, W[4])
        c.rect(2, 2, 13, 6, '#6a737c')
        c.rect(3, 3, 12, 6, '#2c4560')
        c.rect(3, 3, 12, 3, '#3e5f82')
        c.line(4, 6, 7, 3, '#7aa0c8')
        c.line(6, 6, 9, 3, '#5f88b2')
        c.rect(2, 7, 13, 7, '#4a5058')
        for (x, col) in ((3, '#3cdc5a'), (6, '#e0b030'), (9, '#e04a3a'), (12, '#3cdc5a')):
            c.rect(x, 10, x + 1, 11, col)
            c.set(x, 10, mix(col, '#FFFFFF', .5))
        c.rect(2, 13, 13, 13, W[1])
        c.rect(2, 14, 13, 14, W[3])
    elif name == 'computer':
        S = ramp('#8a929b')
        c.rect(0, 0, 15, 15, S[2])
        c.rect(0, 0, 15, 0, S[4])
        c.rect(0, 0, 0, 15, S[3])
        c.rect(0, 15, 15, 15, S[0])
        c.rect(15, 0, 15, 15, S[0])
        c.rect(2, 2, 13, 8, '#3a3f47')
        c.rect(3, 3, 12, 7, '#101820')
        for y, w in ((4, 6), (5, 4), (6, 7)):
            c.rect(4, y, 3 + w, y, '#4fe08a')
        c.set(10, 6, '#c9fbd8')
        c.rect(2, 10, 8, 10, '#1a1c20')
        c.rect(2, 11, 8, 11, S[1])
        for x in (10, 12):
            c.rect(x, 10, x, 12, '#303238')
            c.rect(x, 10, x, 10, '#e0e4e8')
        c.rect(3, 13, 4, 13, '#3cdc5a')
        c.rect(6, 13, 7, 13, '#e0b030')
        for p in ((1, 1), (14, 1), (1, 14), (14, 14)):
            c.set(p[0], p[1], S[1])
    elif name == 'server':
        K = ramp('#2a2d34')
        c.rect(0, 0, 15, 15, K[2])
        c.rect(0, 0, 15, 0, K[3])
        c.rect(0, 15, 15, 15, K[0])
        c.rect(0, 0, 1, 15, '#4a4f58')
        c.rect(14, 0, 15, 15, '#4a4f58')
        for i, y in enumerate((2, 6, 10)):
            c.rect(2, y, 13, y + 2, '#3a3e47')
            c.rect(2, y, 13, y, '#575c68')
            c.rect(2, y + 3, 13, y + 3, '#15161a')
            for x in (3, 6, 9):
                c.rect(x, y + 1, x + 1, y + 2, '#1a1c20')
            c.set(11, y + 1, '#3cdc5a' if i != 1 else '#e0b030')
            c.set(12, y + 1, '#8af0a0' if i != 1 else '#f0d070')
            c.set(12, y + 2, '#3cdc5a')
        for x in range(3, 13, 2):
            c.rect(x, 13, x, 14, '#15161a')
    else:
        raise KeyError(name)
    return c


def cube_icon(src):
    c = Cv()
    f = lambda v: int((v + .5) * 1.6)
    d = 3
    x0, y0 = 1, 4
    px = [src.p[y][x] for y in range(16) for x in range(16) if src.p[y][x]]
    rgb = [[int(h[i:i + 2], 16) for i in (1, 3, 5)] for h in px]
    avg = '#' + ''.join(f'{round(sum(v[i] for v in rgb) / len(rgb)):02X}' for i in range(3))
    top, side = mix(avg, '#FFFFFF', .3), mix(avg, '#000000', .42)
    for k in range(d):
        for j in range(10):
            c.set(x0 + d - k + j, 1 + k, top)
    for i in range(10):
        for j in range(10):
            c.set(x0 + j, y0 + i, src.p[f(i)][f(j)] or '#808080')
    for j in range(d):
        for i in range(10):
            c.set(x0 + 10 + j, y0 - 1 - j + i, side)
    c.outline()
    return c


# ---------------------------------------------------------------- manifest
ITEMS = {
    'galena': 'Galena', 'pyrite': 'Pyrite', 'lead_ingot': 'Lead Ingot', 'steel_ingot': 'Steel Ingot', 'quicklime': 'Quicklime', 'solder': 'Solder',
    'wood_ash': 'Wood Ash', 'niter_earth': 'Nitre Earth', 'saltpeter': 'Saltpetre', 'sulfur': 'Sulfur', 'fermented_mash': 'Fermented Mash',
    'fuel_alcohol': 'Fuel Alcohol', 'coke': 'Coke', 'coal_tar': 'Coal Tar', 'sulfuric_acid': 'Sulfuric Acid', 'phenolic_resin': 'Phenolic Resin',
    'photoresist': 'Photoresist', 'metallurgical_silicon': 'Metallurgical Silicon', 'polysilicon': 'Polysilicon', 'silicon_boule': 'Silicon Boule',
    'silicon_wafer': 'Silicon Wafer', 'patterned_wafer': 'Patterned Wafer', 'silicon_die': 'Silicon Die', 'chip_package': 'Ceramic Chip Package',
    'chip': 'Integrated Circuit', 'copper_wire': 'Copper Wire', 'fine_wire': 'Fine Bond Wire', 'circuit_board': 'Circuit Board', 'transformer': 'Transformer',
    'steel_plate': 'Steel Plate', 'machine_parts_blank': 'Machine Parts Blank', 'machine_parts': 'Machine Parts', 'boiler': 'Boiler', 'steam_engine': 'Steam Engine',
    'dynamo': 'Dynamo', 'combustion_engine': 'Combustion Engine', 'steel_rail': 'Steel Rail', 'sleeper': 'Railway Sleeper', 'wooden_wheel': 'Wooden Wheel',
    'vehicle_frame': 'Vehicle Frame', 'harness': 'Harness', 'gun_carriage': 'Gun Carriage', 'musket': 'Musket', 'musket_stock': 'Musket Stock',
    'musket_barrel_blank': 'Musket Barrel Blank', 'musket_barrel': 'Musket Barrel', 'musket_lock_blank': 'Musket Lock Blank', 'musket_lock': 'Flintlock',
    'musket_ball': 'Musket Balls', 'paper_cartridge': 'Paper Cartridge', 'round_shot': 'Round Shot', 'stone_shot': 'Stone Shot', 'powder_charge': 'Powder Charge',
    'explosive_shell': 'Explosive Shell', 'cannon_barrel': 'Cannon Barrel', 'bombard_barrel': 'Bombard Barrel',
}
BLOCKS = {
    'workshop': 'Workshop', 'assembly_yard': 'Assembly Yard', 'niter_bed': 'Nitre Bed', 'powder_mill': 'Powder Mill', 'foundry': 'Foundry',
    'chemical_works': 'Chemical Works', 'machine_shop': 'Machine Shop', 'electronics_works': 'Electronics Works', 'galena_ore': 'Galena Ore',
    'pyrite_ore': 'Pyrite Ore', 'computer': 'Computer', 'server': 'Server',
}
ORES = {'galena_ore': ('#5d6470', '#8B887C'), 'pyrite_ore': ('#c9a93f', '#8B887C')}
TEMPLATES = {'lead_ingot': ('ingot', '#6f7682'), 'steel_ingot': ('ingot', '#8f9aa3')}
VEHICLES = {'cart': 'Cart', 'wagon': 'Wagon', 'chariot': 'Chariot', 'motor_truck': 'Motor Truck', 'locomotive': 'Steam Locomotive',
            'rail_wagon': 'Goods Wagon', 'rail_carriage': 'Passenger Carriage', 'cannon': 'Cannon', 'bombard': 'Bombard', 'trebuchet': 'Trebuchet', 'field_gun': 'Field Gun'}


def lang_entries():
    e = {}
    for k, v in ITEMS.items():
        e[f'item.{NS}.{k}'] = v
    for k, v in BLOCKS.items():
        e[f'block.{NS}.{k}'] = v
    e[f'block.{NS}.rail_track'] = 'Railway Track'
    for k, v in VEHICLES.items():
        e[f'item.{NS}.{k}'] = v
    for k, v in VEHICLES.items():
        e[f'entity.{NS}.vehicle.{k}'] = v
    e[f'entity.{NS}.vehicle'] = 'Vehicle'
    e[f'entity.{NS}.shot'] = 'Shot'
    e['death.attack.gunshot'] = '%1$s was shot'
    e['death.attack.gunshot.player'] = '%1$s was shot by %2$s'
    e['death.attack.gunshot.item'] = '%1$s was shot by %2$s using %3$s'
    e['death.attack.artillery'] = '%1$s was struck by artillery fire'
    e['death.attack.artillery.player'] = "%1$s was struck by %2$s's artillery fire"
    return e


def write_lang():
    path = ASSETS / 'lang/en_us.json'
    raw = path.read_bytes().decode('utf-8')
    existing = json.loads(raw)
    new = {k: v for k, v in lang_entries().items() if k not in existing}
    if not new:
        return 0
    nl = '\r\n' if '\r\n' in raw else '\n'
    end = raw.rstrip().rfind('}')
    body = raw[:end].rstrip()
    tail = raw[end:]
    add = ''.join(f',{nl}  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}' for k, v in new.items())
    out = body + add + nl + tail
    json.loads(out)
    path.write_bytes(out.encode('utf-8'))
    return len(new)


def write_json_files(name, block):
    (ASSETS / 'items').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'models/item').mkdir(parents=True, exist_ok=True)
    dump = lambda p, o: p.write_text(json.dumps(o, indent=2) + '\n', encoding='utf-8')
    dump(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
    dump(ASSETS / f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})
    if block:
        (ASSETS / 'models/block').mkdir(parents=True, exist_ok=True)
        (ASSETS / 'blockstates').mkdir(parents=True, exist_ok=True)
        dump(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{name}'}})
        dump(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})


def find_qube():
    return shutil.which('qube') or os.environ.get('QUBE') or str(Path.home() / 'AppData/Local/Qube/qube/qube.exe')


def invoke(qube, name, kind, args):
    r = subprocess.run([qube, 'texture', 'write', NS, name, kind, *args, '--json'], cwd=ROOT, capture_output=True, encoding='utf-8', errors='replace', timeout=120)
    if r.returncode:
        raise RuntimeError(f'qube failed for {name}: {r.stdout}{r.stderr}')
    rep = json.JSONDecoder().raw_decode(r.stdout[r.stdout.find('{'):])[0]
    if rep.get('bindings_missing'):
        raise RuntimeError(f'{name}: bindings missing {rep["bindings_missing"]}')
    if rep['report'].get('warnings'):
        print('  warnings', name, kind, rep['report']['warnings'])
    return rep


def write_grid(qube, name, kind, cv):
    WORK.mkdir(parents=True, exist_ok=True)
    p = WORK / f'{name}.{kind}.json'
    p.write_text(json.dumps(cv.json()), encoding='utf-8')
    return invoke(qube, name, kind, ['--grid', '@' + str(p)])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', nargs='*')
    ap.add_argument('--skip-lang', action='store_true')
    a = ap.parse_args()
    qube = find_qube()
    for name in list(ITEMS) + list(BLOCKS):
        if a.only and name not in a.only:
            continue
        block = name in BLOCKS
        write_json_files(name, block)
        rnd = random.Random(name)
        if block:
            if name in ORES:
                col, host = ORES[name]
                rep = invoke(qube, name, 'block', ['--template', 'ore', '--color', col, '--secondary-color', host])
                face = from_grid(rep['grid'])
            else:
                face = build_block(name, rnd)
                rep = write_grid(qube, name, 'block', face)
                face = from_grid(rep['grid'])
            write_grid(qube, name, 'item', cube_icon(face))
        elif name in TEMPLATES:
            t, col = TEMPLATES[name]
            invoke(qube, name, 'item', ['--template', t, '--color', col])
        else:
            write_grid(qube, name, 'item', build_item(name, rnd))
        print('ok', name, flush=True)
    if not a.skip_lang:
        print('lang keys added:', write_lang())


if __name__ == '__main__':
    main()
