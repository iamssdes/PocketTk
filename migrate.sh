#!/system/bin/sh
# Moves everything ApkApple kept to its TkDroid names. This file only defines functions:
# customize.sh (on flash) calls tkdroid_migrate_all, and the tkdroid command calls tkdroid_migrate_home
# when it finds an old ~/.apple. Safe to run again: only an existing old folder is moved, nothing is overwritten.
MG_TERMUX=${MG_TERMUX:-/data/data/com.termux/files}
MG_ADB=${MG_ADB:-/data/adb}
MG_SD=${MG_SD:-/sdcard}
[ -d "$MG_SD" ] || { [ -d /storage/emulated/0 ] && MG_SD=/storage/emulated/0; }

mg_say() { if command -v ui_print >/dev/null 2>&1; then ui_print "$*"; else echo "$*"; fi; }

# mg_move OLD NEW: rename a folder. If NEW already exists, copy over only what it lacks, then drop OLD.
mg_move() {
  [ -d "$1" ] || return 0
  [ -e "$2" ] && [ ! -d "$2" ] && return 0
  if [ ! -e "$2" ]; then
    mkdir -p "$(dirname "$2")" 2>/dev/null
    mv "$1" "$2" && mg_say "- moved $1 -> $2"
  else
    cp -a -n "$1"/. "$2"/ 2>/dev/null && rm -rf "$1" && mg_say "- merged $1 into $2"
  fi
}

# mg_fix_ids KSDIR: point the src= and icon= paths of the id records at the renamed folders.
# (Written with cat so the file keeps its owner: Termux must still be able to edit it.)
mg_fix_ids() {
  [ -d "$1/ids" ] || return 0
  for _f in "$1"/ids/*; do
    [ -f "$_f" ] || continue
    _t="$_f.tmp.$$"
    sed -E '/^(src|icon)=/{s#/ApkApple/#/TkDroid/#g;s#/\.apple/#/.tkdroid/#g;}' "$_f" > "$_t" && cat "$_t" > "$_f"
    rm -f "$_t"
  done
}

# tkdroid_migrate_home HOME: Termux home folder (~/.apple -> ~/.tkdroid)
tkdroid_migrate_home() {
  mg_move "$1/.apple" "$1/.tkdroid"
  mg_fix_ids "$1/.tkdroid"
}

tkdroid_migrate_all() {
  mg_move "$MG_ADB/apple" "$MG_ADB/tkdroid"                  # root's data (signing key, ids)
  mg_fix_ids "$MG_ADB/tkdroid"
  [ -d "$MG_TERMUX/home" ] && tkdroid_migrate_home "$MG_TERMUX/home"
  mg_move "$MG_SD/Download/ApkApple" "$MG_SD/Download/TkDroid"   # demo scripts, built APKs
  mg_move "$MG_SD/ApkApple" "$MG_SD/TkDroid"                     # android.jar / sdk you put there
  rm -rf /data/local/tmp/apkapple                                # old temporary work folder
}
