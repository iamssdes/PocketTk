#!/usr/bin/env python3
"""Draws the PocketTk app icon (a phone with "Tk" on its screen, peeking out of a pocket) as a PNG with the standard library only.
The same drawing as the logo SVG.   usage: make_icon.py OUT.png [SIZE]
(the Mac build turns it into PocketTk.icns with sips + iconutil, the Windows build into an .ico, the Linux build uses it as is)"""
import sys, zlib, struct, math

CREAM, INDIGO, CORAL = (0xF3, 0xED, 0xE1), (0x22, 0x2A, 0x4D), (0xE8, 0x74, 0x4F)


def rrect(cx, cy, hw, hh, r):
    def f(px, py):
        qx, qy = abs(px - cx) - (hw - r), abs(py - cy) - (hh - r)
        return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r
    return f, (cx - hw, cy - hh, cx + hw, cy + hh)


def capsule(x1, y1, x2, y2, r):
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy or 1.0

    def f(px, py):
        t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
        return math.hypot(px - (x1 + t * dx), py - (y1 + t * dy)) - r
    return f, (min(x1, x2) - r, min(y1, y2) - r, max(x1, x2) + r, max(y1, y2) + r)


def union(a, b):
    fa, ba = a
    fb, bb = b
    return (lambda px, py: min(fa(px, py), fb(px, py))), (min(ba[0], bb[0]), min(ba[1], bb[1]), max(ba[2], bb[2]), max(ba[3], bb[3]))


def shapes():
    """(sdf, bbox, colour) from back to front, in a 512 x 512 space (the SVG's viewBox)."""
    s = [(*rrect(256, 256, 240, 240, 112), CREAM),                    # the tile
         (*rrect(256, 221, 100, 165, 40), INDIGO),                    # the phone
         (*rrect(256, 210, 80, 130, 22), CREAM)]                      # its screen
    for seg in ((190, 124, 238, 124), (214, 124, 214, 212)):          # T
        s.append((*capsule(*seg, 9.5), INDIGO))
    for seg in ((268, 112, 268, 212), (312, 146, 268, 176), (284, 166, 314, 212)):    # k
        s.append((*capsule(*seg, 9.5), CORAL))
    s.append((*union(rrect(256, 350, 172, 88, 52), rrect(256, 300, 172, 38, 0.001)), CORAL))      # the pocket (round at the bottom only)
    x = 106.0
    while x < 406:                                                    # its stitching
        s.append((*capsule(x, 288, min(x + 14, 406), 288, 2.5), CREAM))
        x += 26
    return s


def render(size):
    sh = shapes()
    k = 512.0 / size
    aa = 1.2 * k
    rows = []
    for y in range(size):
        py = (y + 0.5) * k
        row = bytearray()
        for x in range(size):
            px = (x + 0.5) * k
            col, alpha = (0.0, 0.0, 0.0), 0.0
            for i, (f, (x0, y0, x1, y1), c) in enumerate(sh):
                if px < x0 - 2 or px > x1 + 2 or py < y0 - 2 or py > y1 + 2:
                    continue
                a = max(0.0, min(1.0, 0.5 - f(px, py) / aa))
                if a <= 0.0:
                    continue
                if i == 0:                                            # the tile defines the icon's outline
                    col, alpha = tuple(float(v) for v in c), a
                else:
                    col = tuple(col[j] * (1 - a) + c[j] * a for j in range(3))
            row += bytes((int(col[0]), int(col[1]), int(col[2]), int(255 * alpha)))
        rows.append(bytes(row))
    return rows


def png(size, rows):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + r for r in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "icon.png"
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
    with open(out, "wb") as f:
        f.write(png(size, render(size)))
    print("wrote %s (%dx%d)" % (out, size, size))
