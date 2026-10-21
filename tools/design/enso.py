#!/usr/bin/env python3
"""Generates Zeno's ensō (円相): a single brush stroke as a filled outline.

The stroke starts heavy at the lower left (the brush lands), circles clockwise
while thinning, and ends in a fine tail that tucks inside the start, the way a
real ensō is drawn in one breath. Output is SVG path data in a 108-unit
viewport (the Android adaptive-icon canvas).

usage: python3 tools/design/enso.py > enso.txt
"""
import math
def smooth(a,b,x):
    t=max(0.0,min(1.0,(x-a)/(b-a))); return t*t*(3-2*t)
def width(t, wmax):
    landing = 0.62 + 0.38*smooth(0.0, 0.10, t)         # brush lands, spreads
    body = 1.0 - 0.55*smooth(0.12, 0.80, t)              # gradual thinning
    tail = 1.0 - smooth(0.80, 1.0, t)**0.9               # taper to a point
    return max(wmax*landing*body*(0.08+0.92*tail), 0.25)
def centerline(t, cx, cy, R, start, sweep):
    th = math.radians(start + sweep*t)
    r = R*(1 + 0.025*math.sin(2*math.pi*t*1.3 + 0.9)) * (1 - 0.07*smooth(0.75,1.0,t))
    return cx + r*math.cos(th), cy + r*math.sin(th), th
def outline(cx=54, cy=54, R=20.5, wmax=8.6, start=150, sweep=338, n=140):
    o=[]; i=[]
    for k in range(n+1):
        t=k/n
        x,y,th=centerline(t,cx,cy,R,start,sweep)
        # tangent-based normal for accuracy
        x2,y2,_=centerline(min(1,t+1e-3),cx,cy,R,start,sweep); x1,y1,_=centerline(max(0,t-1e-3),cx,cy,R,start,sweep)
        tx,ty=x2-x1,y2-y1; L=math.hypot(tx,ty) or 1; nx,ny=ty/L,-tx/L   # normal pointing outward for clockwise on screen
        w=width(t,wmax)
        bias=0.18*(1-t)  # more ink on the outer edge at the start
        o.append((x+nx*w*(0.5+bias), y+ny*w*(0.5+bias)))
        i.append((x-nx*w*(0.5-bias), y-ny*w*(0.5-bias)))
    return o,i
def bez(P):
    d=[]
    for k in range(len(P)-1):
        p0=P[k-1] if k>0 else P[k]; p1=P[k]; p2=P[k+1]; p3=P[k+2] if k+2<len(P) else P[k+1]
        c1=(p1[0]+(p2[0]-p0[0])/6, p1[1]+(p2[1]-p0[1])/6); c2=(p2[0]-(p3[0]-p1[0])/6, p2[1]-(p3[1]-p1[1])/6)
        d.append(f"C{c1[0]:.2f} {c1[1]:.2f} {c2[0]:.2f} {c2[1]:.2f} {p2[0]:.2f} {p2[1]:.2f}")
    return d
def path(**kw):
    o,i=outline(**kw); i=i[::-1]
    # decimate for compactness
    o=o[::4]+([o[-1]] if (len(o)-1)%4 else []); i=i[::4]+([i[-1]] if (len(i)-1)%4 else [])
    d=[f"M{o[0][0]:.2f} {o[0][1]:.2f}"]+bez(o)+[f"L{i[0][0]:.2f} {i[0][1]:.2f}"]+bez(i)
    r=math.hypot(o[0][0]-i[-1][0], o[0][1]-i[-1][1])/2
    d.append(f"A{r:.2f} {r:.2f} 0 0 1 {o[0][0]:.2f} {o[0][1]:.2f}Z")
    return " ".join(d)


if __name__ == "__main__":
    print(path())
