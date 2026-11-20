#!/bin/bash
# Builds PocketTk.app and PocketTk-<version>-<arch>.dmg. Run it on a Mac (or in the GitHub Actions macOS runner):
#     packaging/macos/build_app.sh [arm64|x86_64]          (default: this Mac's architecture)
# Needs: macOS command line tools (hdiutil, iconutil, sips, codesign), python3 and curl.
#
# What goes into the app:
#   * a relocatable CPython (python-build-standalone) with pywebview + certifi installed, so the Mac needs no Python
#   * PocketTk itself (pockettk*.py, transpile.py, rt/, preview/, ipa/, webui/)
# NOT inside: a JDK and the Android SDK. The app downloads those on its first run (the SDK license has to be accepted
# by the user, so it may not be bundled).
#
# Optional signing (otherwise the app is ad-hoc signed and the user has to click "Open Anyway" once):
#     CODESIGN_IDENTITY="Developer ID Application: Name (TEAMID)"   sign with a Developer ID (hardened runtime)
#     NOTARY_PROFILE=name                                            then notarize with `xcrun notarytool` (stored profile)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/packaging/macos"
ARCH="${1:-$(uname -m)}"
case "$ARCH" in
  arm64|aarch64) ARCH=arm64;  TRIPLE=aarch64-apple-darwin;;
  x86_64|x64)    ARCH=x86_64; TRIPLE=x86_64-apple-darwin;;
  *) echo "unknown architecture: $ARCH" >&2; exit 2;;
esac
if [ "$(uname -m)" != "$ARCH" ]; then
  echo "build the $ARCH app on a $ARCH Mac (the bundled Python has to run here to install pywebview)" >&2; exit 2
fi
PYSERIES="${PYSERIES:-3.12}"
BUNDLE_ID="${BUNDLE_ID:-com.ssdes.pockettk}"
VERSION="$(sed -n 's/^VERSION = "PocketTk v\(.*\)"/\1/p' "$ROOT/pockettk.py")"
[ -n "$VERSION" ] || { echo "cannot read the version from pockettk.py" >&2; exit 1; }

OUT="$ROOT/dist/macos-$ARCH"
APP="$OUT/PocketTk.app"
DMG="$ROOT/dist/PocketTk-$VERSION-$ARCH.dmg"
CACHE="$ROOT/dist/cache"
rm -rf "$OUT" "$DMG"; mkdir -p "$OUT" "$CACHE" "$APP/Contents/MacOS" "$APP/Contents/Resources/app"
echo "== PocketTk $VERSION for $ARCH"

# ---- 1. the Python (newest python-build-standalone release for this series and architecture)
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
TMP="$OUT/_py"; mkdir -p "$TMP"; tar -xzf "$PYTGZ" -C "$TMP"            # unpacks to python/
PYBIN="$(ls "$TMP"/python/bin/python3.[0-9]* | grep -v config | head -n 1)"
PYNAME="$(basename "$PYBIN")"                                           # python3.12
[ -f "$TMP/python/lib/lib$PYNAME.dylib" ] || { echo "unexpected layout: no lib/lib$PYNAME.dylib" >&2; exit 1; }

# Layout: the interpreter sits in Contents/MacOS (so macOS treats the process as this app: Dock icon, name) and
# finds its standard library by itself in Contents/lib (it looks for lib/pythonX.Y/os.py next to its folder).
cp "$PYBIN" "$APP/Contents/MacOS/python3"
cp -R "$TMP/python/lib" "$APP/Contents/lib"
rm -rf "$TMP"
PYLIB="$APP/Contents/lib/$PYNAME"
if ! otool -L "$APP/Contents/MacOS/python3" | grep -q "@executable_path/../lib/lib$PYNAME.dylib"; then
  echo "== relinking libpython next to the app's executable"
  OLD="$(otool -L "$APP/Contents/MacOS/python3" | awk -v n="lib$PYNAME.dylib" '$1 ~ n {print $1; exit}')"
  [ -n "$OLD" ] && install_name_tool -change "$OLD" "@executable_path/../lib/lib$PYNAME.dylib" "$APP/Contents/MacOS/python3"
fi
PY="$APP/Contents/MacOS/python3"
"$PY" -I -c "import sys; print('prefix:', sys.prefix)"

# ---- 2. pywebview and certifi into the app's own Python
echo "== installing pywebview and certifi"
"$PY" -m pip install --disable-pip-version-check --no-warn-script-location --no-compile pywebview certifi
rm -rf "$APP/Contents/lib/$PYNAME/test" "$APP/Contents/lib/$PYNAME/idlelib" "$APP/Contents/lib/$PYNAME/turtledemo"
find "$APP/Contents" -name "__pycache__" -prune -exec rm -rf {} +

# ---- 3. PocketTk itself
echo "== copying PocketTk"
APPDIR="$APP/Contents/Resources/app"
for f in pockettk.py pockettk_gui.py pockettk_deps.py pockettk_preview.py pockettk_ipa.py transpile.py LICENSE README.md CHANGELOG.md; do
  [ -f "$ROOT/$f" ] && cp "$ROOT/$f" "$APPDIR/"
done
for d in rt preview ipa webui demo; do [ -d "$ROOT/$d" ] && cp -R "$ROOT/$d" "$APPDIR/"; done
find "$APPDIR" -name "__pycache__" -prune -exec rm -rf {} +

# ---- 4. launcher, icon, Info.plist
cat > "$APP/Contents/MacOS/PocketTk" <<'LAUNCH'
#!/bin/sh
# Starts PocketTk's window with the Python inside this app. (exec: the app's process becomes Python, same folder.)
D="$(cd "$(dirname "$0")" && pwd)"
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1
exec "$D/python3" "$D/../Resources/app/pockettk_gui.py" "$@"
LAUNCH
chmod 755 "$APP/Contents/MacOS/PocketTk" "$APP/Contents/MacOS/python3"

ICONSET="$OUT/PocketTk.iconset"; mkdir -p "$ICONSET"
python3 "$HERE/make_icon.py" "$OUT/icon1024.png" 1024
for s in 16 32 128 256 512; do
  sips -z $s $s "$OUT/icon1024.png" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
  sips -z $((s*2)) $((s*2)) "$OUT/icon1024.png" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/PocketTk.icns"
sed -e "s/@VERSION@/$VERSION/g" -e "s/@BUNDLE_ID@/$BUNDLE_ID/g" "$HERE/Info.plist.in" > "$APP/Contents/Info.plist"
plutil -lint "$APP/Contents/Info.plist"

# ---- 5. does the finished app work? (no window opens: POCKETTK_GUI_SELFTEST)
echo "== self-test"
POCKETTK_GUI_SELFTEST=1 "$APP/Contents/MacOS/PocketTk"
PYTHONNOUSERSITE=1 "$PY" "$APPDIR/pockettk.py" version

# ---- 6. sign
if [ -n "${CODESIGN_IDENTITY:-}" ]; then
  echo "== signing with $CODESIGN_IDENTITY (hardened runtime)"
  find "$APP" \( -name "*.so" -o -name "*.dylib" \) -exec codesign --force --timestamp --options runtime \
       --entitlements "$HERE/entitlements.plist" -s "$CODESIGN_IDENTITY" {} +
  codesign --force --timestamp --options runtime --entitlements "$HERE/entitlements.plist" -s "$CODESIGN_IDENTITY" "$APP/Contents/MacOS/python3"
  codesign --force --timestamp --options runtime --entitlements "$HERE/entitlements.plist" -s "$CODESIGN_IDENTITY" "$APP"
else
  echo "== ad-hoc signing (no Developer ID given)"
  find "$APP" \( -name "*.so" -o -name "*.dylib" \) -exec codesign --force -s - {} +
  codesign --force -s - "$APP/Contents/MacOS/python3"
  codesign --force -s - "$APP"
fi
codesign --verify --verbose=1 "$APP"

# ---- 7. the disk image
echo "== building the .dmg"
STAGE="$OUT/dmg"; mkdir -p "$STAGE"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
cp "$HERE/READ ME FIRST.txt" "$STAGE/"
hdiutil create -volname "PocketTk $VERSION" -srcfolder "$STAGE" -ov -format UDZO "$DMG"
[ -n "${CODESIGN_IDENTITY:-}" ] && codesign --force --timestamp -s "$CODESIGN_IDENTITY" "$DMG"
if [ -n "${CODESIGN_IDENTITY:-}" ] && [ -n "${NOTARY_PROFILE:-}" ]; then
  echo "== notarizing"
  xcrun notarytool submit "$DMG" --keychain-profile "$NOTARY_PROFILE" --wait
  xcrun stapler staple "$DMG"
fi
echo "== done: $DMG"
