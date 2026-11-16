#!/system/bin/sh
ui_print "- TkDroid v3.1.4 Patch 2: tkinter -> APK"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/apkapple.sh" 0 0 0755
set_perm "$MODPATH/install_termux.sh" 0 0 0755
set_perm "$MODPATH/system/bin/tkdroid" 0 2000 0755 u:object_r:system_file:s0
set_perm "$MODPATH/system/bin/apkapple" 0 2000 0755 u:object_r:system_file:s0
set_perm "$MODPATH/system/bin/apple" 0 2000 0755 u:object_r:system_file:s0
. "$MODPATH/migrate.sh"
tkdroid_migrate_all                       # ApkApple folders and data -> TkDroid
mkdir -p /data/adb/tkdroid
# the cut-down modules (TkDroid Demo, TkDroid Consumer) must not stay next to the full one (they would take over the commands)
for m in tkdroid_demo tkdroid_consumer; do
  if [ -d /data/adb/modules/$m ]; then touch /data/adb/modules/$m/remove; ui_print "- $m will be removed at the next reboot"; fi
done
sh "$MODPATH/install_termux.sh" "$MODPATH"
[ -x /data/data/com.termux/files/usr/bin/tkdroid ] && ui_print "- Termux commands installed: tkdroid (old names apkapple, apple still work)"
ui_print "- Usage (normal Termux user, NOT tsu):  tkdroid run app.py"
ui_print "- First run: tkdroid doctor"
ui_print "- No reboot needed"
