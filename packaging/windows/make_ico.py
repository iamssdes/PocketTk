#!/usr/bin/env python3
"""Makes PocketTk.ico (16-256 px, PNG-compressed entries) from the same drawing as the Mac icon.
usage: make_ico.py OUT.ico"""
import os, sys, struct
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "macos"))
import make_icon

SIZES = (16, 24, 32, 48, 64, 128, 256)


def build():
    pngs = [make_icon.png(s, make_icon.render(s)) for s in SIZES]
    out = struct.pack("<HHH", 0, 1, len(SIZES))
    offset = 6 + 16 * len(SIZES)
    for s, data in zip(SIZES, pngs):
        out += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    return out + b"".join(pngs)


if __name__ == "__main__":
    with open(sys.argv[1] if len(sys.argv) > 1 else "PocketTk.ico", "wb") as f:
        f.write(build())
    print("wrote", sys.argv[1] if len(sys.argv) > 1 else "PocketTk.ico")
