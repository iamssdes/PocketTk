#!/bin/bash
# Builds PocketTk-<version>-linux-<arch>.tar.gz and PocketTk-<version>-linux-<arch>.AppImage. Run it on Linux (or in the GitHub Actions Linux runner):
#     packaging/linux/build_app.sh [x86_64|aarch64]          (default: this computer's architecture)
# Needs: python3 and curl (python3 only reads the version and draws the icon), internet.
#
# Inside: a relocatable CPython (python-build-standalone) with pywebview + Qt (PyQt6 + QtWebEngine, or PySide6 where there
# are no PyQt6 WebEngine wheels, as on some aarch64 systems) + certifi, and PocketTk.
#     QT_BINDING=auto|pyqt6|pyside6   which Qt to install (default auto: PyQt6 first, then PySide6)
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
echo "== installing pywebview, Qt and certifi   ($(uname -m), $(ldd --version 2>&1 | head -n 1))"
pipi() { "$PY" -m pip install --disable-pip-version-check --no-warn-script-location --no-compile "$@"; }
QT_BINDING="${QT_BINDING:-auto}"
QTLOG="$OUT/pip-qt.log"
pipi pywebview qtpy certifi
# Only binary wheels: when a platform has none, pip must say so at once instead of trying (and failing) to build Qt from source.
install_qt() {   # install_qt pyqt6|pyside6
  case "$1" in
    pyqt6)   pipi --only-binary=:all: PyQt6 PyQt6-WebEngine;;
    pyside6) pipi --only-binary=:all: PySide6;;
  esac
}
drop_qt() {      # remove what a failed attempt left behind, so two bindings are never mixed
  "$PY" -m pip uninstall -y -q PyQt6 PyQt6-Qt6 PyQt6-sip PyQt6-WebEngine PyQt6-WebEngine-Qt6 PySide6 PySide6-Essentials PySide6-Addons shiboken6 >/dev/null 2>&1 || true
}
QT_API_NAME=""
for b in $( [ "$QT_BINDING" = auto ] && echo "pyqt6 pyside6" || echo "$QT_BINDING" ); do
  echo "== trying $b"
  if install_qt "$b" >"$QTLOG" 2>&1; then QT_API_NAME="$b"; break; fi
  echo "-- $b could not be installed on $ARCH; the last lines of pip say why:" >&2
  tail -n 12 "$QTLOG" >&2
  drop_qt
done
[ -n "$QT_API_NAME" ] || { echo "no Qt WebEngine wheels for $ARCH (tried: $QT_BINDING). The window needs Qt; see the messages above." >&2; exit 1; }
echo "== Qt binding: $QT_API_NAME"
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
echo "$QT_API_NAME" > "$APP/qt-api"          # read by the launchers and AppRun: which Qt this build has
cat > "$APP/pockettk-gui" <<'LAUNCH'
#!/bin/sh
# Starts the PocketTk window with the Python inside this folder.
D="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1 PYWEBVIEW_GUI=qt
[ -f "$D/qt-api" ] && export QT_API="$(cat "$D/qt-api")"
# QtWebEngine refuses to run as root without this
[ "$(id -u)" = "0" ] && export QTWEBENGINE_CHROMIUM_FLAGS="${QTWEBENGINE_CHROMIUM_FLAGS:-} --no-sandbox"
exec "$D/python/bin/python3" "$D/app/pockettk_gui.py" "$@"
LAUNCH
cat > "$APP/pockettk" <<'LAUNCH'
#!/bin/sh
# The command line tool with the same Python:   ./pockettk build app.py   /   ./pockettk doctor   /   ./pockettk gui
D="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1 PYWEBVIEW_GUI=qt
[ -f "$D/qt-api" ] && export QT_API="$(cat "$D/qt-api")"
exec "$D/python/bin/python3" "$D/app/pockettk.py" "$@"
LAUNCH
chmod 755 "$APP/pockettk-gui" "$APP/pockettk"
python3 "$ROOT/packaging/macos/make_icon.py" "$APP/pockettk.png" 256
cp "$HERE/install.sh" "$APP/"
chmod 755 "$APP/install.sh"
# the READ ME names the system libraries from the one list (packaging/linux/system-libs.txt), so it can never drift from CI
LIBS="$(grep -v '^#' "$HERE/system-libs.txt" | grep -v '^$' | tr '\n' ' ' | sed 's/ $//')"
sed "s|@LIBS@|$LIBS|" "$HERE/READ ME FIRST.txt" > "$APP/READ ME FIRST.txt"

# ---- 5. does the finished app work? (no window opens)
echo "== self-test"
POCKETTK_GUI_SELFTEST=1 "$APP/pockettk-gui"
deb_for() {       # deb_for libopus.so.0  ->  the Debian/Ubuntu 24.04 package that has it (nothing if it is not in the table)
  case "$1" in
    libopus.so.*) echo libopus0;;                         libminizip.so.*) echo libminizip1t64;;
    libnss3.so|libnssutil3.so|libsmime3.so) echo libnss3;;   libnspr4.so) echo libnspr4;;
    libasound.so.*) echo libasound2t64;;                  libxkbcommon-x11.so.*) echo libxkbcommon-x11-0;;
    libxkbcommon.so.*) echo libxkbcommon0;;               libxcb-cursor.so.*) echo libxcb-cursor0;;
    libxcb-icccm.so.*) echo libxcb-icccm4;;               libxcb-keysyms.so.*) echo libxcb-keysyms1;;
    libxcb-shape.so.*) echo libxcb-shape0;;               libxcb-image.so.*) echo libxcb-image0;;
    libxcb-render-util.so.*) echo libxcb-render-util0;;   libEGL.so.*) echo libegl1;;
    libGL.so.*) echo libgl1;;                             libgbm.so.*) echo libgbm1;;
    libdbus-1.so.*) echo libdbus-1-3;;                    libfontconfig.so.*) echo libfontconfig1;;
    libXcomposite.so.*) echo libxcomposite1;;             libXdamage.so.*) echo libxdamage1;;
    libXrandr.so.*) echo libxrandr2;;                     libXtst.so.*) echo libxtst6;;
    libxkbfile.so.*) echo libxkbfile1;;                   libxshmfence.so.*) echo libxshmfence1;;
  esac
}
qt_diagnose() {   # the usual reason this fails on a fresh machine: a system library Chromium needs is not installed
  echo "---- Qt WebEngine does not start on this $ARCH system ----" >&2
  gone=""
  for so in $(find "$APP/python" \( -name 'libQt6WebEngineCore.so*' -o -name 'QtWebEngineProcess' \) -type f | head -n 2); do
    echo "libraries $so needs and cannot find:" >&2
    miss="$(ldd "$so" 2>&1 | grep "not found" || true)"
    if [ -n "$miss" ]; then echo "$miss" >&2; gone="$gone $miss"; else echo "  (none)" >&2; fi
  done
  pk=""
  for l in $(echo "$gone" | tr ' ' '\n' | grep '\.so' | sort -u); do p="$(deb_for "$l")"; [ -n "$p" ] && pk="$pk $p"; done
  [ -n "$pk" ] && echo "Missing here. On Debian/Ubuntu 24.04: sudo apt install$pk" >&2
  echo "Everything Qt WebEngine usually needs (packaging/linux/system-libs.txt): sudo apt install $(grep -v '^#' "$HERE/system-libs.txt" | grep -v '^$' | tr '\n' ' ')" >&2
  echo "If a library is missing that is not in that list, add it to the list: it is the only place CI and the README take it from." >&2
}
QT_API="$QT_API_NAME" QT_QPA_PLATFORM=offscreen "$PY" -I -c "import qtpy; from qtpy import QtWebEngineWidgets, QtWebEngineCore; print('Qt WebEngine imports (' + qtpy.API_NAME + ')')" \
  || { qt_diagnose; exit 1; }
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
  cp "$APP/qt-api" "$AD/qt-api"
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
