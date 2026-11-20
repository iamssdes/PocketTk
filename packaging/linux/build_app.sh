#!/bin/bash
# Builds PocketTk-<version>-linux-<arch>.tar.gz and PocketTk-<version>-linux-<arch>.AppImage. Run it on Linux (or in the GitHub Actions Linux runner):
#     packaging/linux/build_app.sh [x86_64|aarch64]          (default: this computer's architecture)
# Needs: python3 and curl (python3 only reads the version and draws the icon), internet.
#
# Inside: a relocatable CPython (python-build-standalone) with pywebview + Qt (PyQt6 + QtWebEngine) + certifi, and PocketTk.
# Qt comes from pip wheels, so the app needs no GTK / WebKit packages and works on any common distribution; the price is size
# (about 250 MB unpacked). NOT inside: a JDK and the Android SDK; the app downloads those on its first run.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/packaging/linux"
ARCH="${1:-$(uname -m)}"
case "$ARCH" in
  x86_64|x64|amd64)  ARCH=x86_64;  TRIPLE=x86_64-unknown-linux-gnu;;
  aarch64|arm64)     ARCH=aarch64; TRIPLE=aarch64-unknown-linux-gnu;;
  *) echo "unknown architecture: $ARCH" >&2; exit 2;;
esac
if [ "$(uname -m)" != "$ARCH" ]; then
  echo "build the $ARCH app on a $ARCH computer (the bundled Python has to run here to install pywebview)" >&2; exit 2
fi
PYSERIES="${PYSERIES:-3.12}"
VERSION="$(sed -n 's/^VERSION = "PocketTk v\(.*\)"/\1/p' "$ROOT/pockettk.py")"
[ -n "$VERSION" ] || { echo "cannot read the version from pockettk.py" >&2; exit 1; }

OUT="$ROOT/dist/linux-$ARCH"
APP="$OUT/PocketTk"
TGZ="$ROOT/dist/PocketTk-$VERSION-linux-$ARCH.tar.gz"
CACHE="$ROOT/dist/cache"
rm -rf "$OUT" "$TGZ"; mkdir -p "$OUT" "$CACHE" "$APP"
echo "== PocketTk $VERSION for Linux $ARCH"

# ---- 1. the Python
PYTGZ="$CACHE/python-$PYSERIES-$TRIPLE.tar.gz"
if [ ! -f "$PYTGZ" ]; then
  echo "== finding python-build-standalone $PYSERIES ($TRIPLE)"
  URL="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
        https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest | python3 -c '
import json, sys, re
series, triple = sys.argv[1], sys.argv[2]
rel = json.load(sys.stdin)
pat = re.compile(r"^cpython-%s\.\d+\+\d+-%s-install_only\.tar\.gz$" % (re.escape(series), re.escape(triple)))
hits = sorted((a["name"], a["browser_download_url"]) for a in rel["assets"] if pat.match(a["name"]))
print(hits[-1][1] if hits else "")' "$PYSERIES" "$TRIPLE")"
  [ -n "$URL" ] || { echo "no python-build-standalone asset for $PYSERIES/$TRIPLE in the latest release" >&2; exit 1; }
  echo "== downloading $URL"
  curl -fL --retry 3 -o "$PYTGZ.part" "$URL" && mv "$PYTGZ.part" "$PYTGZ"
fi
tar -xzf "$PYTGZ" -C "$APP"                                   # unpacks to python/
PY="$APP/python/bin/python3"
[ -x "$PY" ] || { echo "unexpected layout: no python/bin/python3" >&2; exit 1; }
"$PY" -I -c "import sys; print('prefix:', sys.prefix)"

# ---- 2. pywebview with Qt, and certifi
echo "== installing pywebview, Qt and certifi"
"$PY" -m pip install --disable-pip-version-check --no-warn-script-location --no-compile pywebview qtpy PyQt6 PyQt6-WebEngine certifi
PYNAME="$(basename "$(ls -d "$APP"/python/lib/python3.[0-9]* | head -n 1)")"
rm -rf "$APP/python/lib/$PYNAME/test" "$APP/python/lib/$PYNAME/idlelib" "$APP/python/lib/$PYNAME/turtledemo"
find "$APP" -name "__pycache__" -prune -exec rm -rf {} +

# ---- 3. PocketTk itself
echo "== copying PocketTk"
mkdir -p "$APP/app"
for f in pockettk.py pockettk_gui.py pockettk_deps.py pockettk_preview.py pockettk_ipa.py transpile.py LICENSE README.md CHANGELOG.md; do
  [ -f "$ROOT/$f" ] && cp "$ROOT/$f" "$APP/app/"
done
for d in rt preview ipa webui demo; do [ -d "$ROOT/$d" ] && cp -R "$ROOT/$d" "$APP/app/"; done
find "$APP/app" -name "__pycache__" -prune -exec rm -rf {} +

# ---- 4. launchers, icon, installer for the menu entry
cat > "$APP/pockettk-gui" <<'LAUNCH'
#!/bin/sh
# Starts the PocketTk window with the Python inside this folder.
D="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1 PYWEBVIEW_GUI=qt
# QtWebEngine refuses to run as root without this
[ "$(id -u)" = "0" ] && export QTWEBENGINE_CHROMIUM_FLAGS="${QTWEBENGINE_CHROMIUM_FLAGS:-} --no-sandbox"
exec "$D/python/bin/python3" "$D/app/pockettk_gui.py" "$@"
LAUNCH
cat > "$APP/pockettk" <<'LAUNCH'
#!/bin/sh
# The command line tool with the same Python:   ./pockettk build app.py   /   ./pockettk doctor   /   ./pockettk gui
D="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1 PYWEBVIEW_GUI=qt
exec "$D/python/bin/python3" "$D/app/pockettk.py" "$@"
LAUNCH
chmod 755 "$APP/pockettk-gui" "$APP/pockettk"
python3 "$ROOT/packaging/macos/make_icon.py" "$APP/pockettk.png" 256
cp "$HERE/install.sh" "$HERE/READ ME FIRST.txt" "$APP/"
chmod 755 "$APP/install.sh"

# ---- 5. does the finished app work? (no window opens)
echo "== self-test"
POCKETTK_GUI_SELFTEST=1 "$APP/pockettk-gui"
QT_QPA_PLATFORM=offscreen "$PY" -I -c "from PyQt6 import QtWebEngineWidgets, QtWebEngineCore; print('Qt WebEngine imports')"
"$APP/pockettk" version

# ---- 6. the archive
echo "== packing"
tar -C "$OUT" -czf "$TGZ" PocketTk
ls -lh "$TGZ"

# ---- 7. the AppImage: one file to download and double-click (no terminal, no install). APPIMAGE=0 skips it.
if [ "${APPIMAGE:-1}" != "0" ]; then
  echo "== building the AppImage"
  AIMG="$ROOT/dist/PocketTk-$VERSION-linux-$ARCH.AppImage"
  TOOL="$CACHE/appimagetool-$ARCH.AppImage"
  if [ ! -x "$TOOL" ]; then
    curl -fL --retry 3 -o "$TOOL.part" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-$ARCH.AppImage" \
      && chmod +x "$TOOL.part" && mv "$TOOL.part" "$TOOL" || { echo "WARNING: could not get appimagetool: no AppImage this time (the .tar.gz is fine)" >&2; rm -f "$TOOL.part"; exit 0; }
  fi
  AD="$OUT/AppDir"
  rm -rf "$AD" "$AIMG"
  mkdir -p "$AD"
  cp -a "$APP/python" "$APP/app" "$AD/"
  cp "$HERE/AppRun" "$AD/AppRun"; chmod 755 "$AD/AppRun"
  cp "$HERE/pockettk.desktop" "$AD/pockettk.desktop"
  cp "$APP/pockettk.png" "$AD/pockettk.png"
  ln -s pockettk.png "$AD/.DirIcon"
  POCKETTK_GUI_SELFTEST=1 "$AD/AppRun"
  ARCH="$ARCH" "$TOOL" --appimage-extract-and-run "$AD" "$AIMG"
  chmod 755 "$AIMG"
  echo "== testing the AppImage"
  POCKETTK_GUI_SELFTEST=1 APPIMAGE_EXTRACT_AND_RUN=1 "$AIMG"
  ls -lh "$AIMG"
fi
