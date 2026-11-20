#!/bin/sh
# Adds PocketTk to the application menu (this user only) and puts `pockettk` on the PATH:   ./install.sh
# To remove: delete ~/.local/share/applications/pockettk.desktop, ~/.local/bin/pockettk and this folder.
set -e
D="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"
BIN="$HOME/.local/bin"
mkdir -p "$APPS" "$ICONS" "$BIN"
cp "$D/pockettk.png" "$ICONS/pockettk.png"
cat > "$APPS/pockettk.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=PocketTk
Comment=Turn tkinter Python scripts into Android apps
Exec="$D/pockettk-gui"
Icon=pockettk
Terminal=false
Categories=Development;
StartupWMClass=pockettk
DESK
ln -sf "$D/pockettk" "$BIN/pockettk"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true
echo "PocketTk is in your application menu. Command line: pockettk  (make sure $BIN is on your PATH)"
