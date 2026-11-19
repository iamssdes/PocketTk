#!/system/bin/sh
# Re-sync the Termux copy at boot (e.g. after Termux was reinstalled)
MODDIR=/data/adb/modules/apple
[ -x /data/data/com.termux/files/usr/bin/pockettk ] || sh "$MODDIR/install_termux.sh" "$MODDIR"
