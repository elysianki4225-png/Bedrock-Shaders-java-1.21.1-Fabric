#!/usr/bin/env python3
"""Parses a Mojang Bedrock lighting/atmospherics file the same way LightingSettings.java does."""
import json, sys

def curve(obj, default=0.0):
    pts = []
    for k, v in (obj or {}).items():
        try:
            pts.append((float(k), float(v)))
        except (TypeError, ValueError):
            pass
    pts.sort()
    return pts or [(0.0, default)]

def sample(pts, x):
    if not pts: return 0.0
    if x <= pts[0][0]: return pts[0][1]
    if x >= pts[-1][0]: return pts[-1][1]
    lo = 0
    hi = len(pts) - 1
    while lo + 1 < hi:
        mid = (lo + hi) // 2
        if pts[mid][0] <= x: lo = mid
        else: hi = mid
    span = pts[hi][0] - pts[lo][0]
    t = 0.0 if span <= 1e-6 else (x - pts[lo][0]) / span
    return pts[lo][1] + t * (pts[hi][1] - pts[lo][1])

if __name__ == '__main__':
    src = sys.argv[1]
    data = json.load(open(src))['minecraft:lighting_settings']
    directional = data['directional_lights']
    container = directional.get('orbital', directional)
    sun = container['sun']['illuminance']
    pts = curve(sun)
    print(f"{src}: {len(pts)} sun keyframes, noon={sample(pts, 0.0):.2f} lux, sunset={sample(pts, 0.28):.3f}")
