#!/system/bin/sh
# Shared by customize.sh and service.sh: copy toolchain into Termux and create commands.
MODDIR="$1"
TXBIN=/data/data/com.termux/files/usr/bin
TXAPP=/data/data/com.termux/files/usr/share/pockettk
[ -d "$TXBIN" ] || exit 0
rm -rf "$TXAPP" /data/data/com.termux/files/usr/share/tkdroid /data/data/com.termux/files/usr/share/apkapple /data/data/com.termux/files/usr/share/apple
mkdir -p "$TXAPP"
cp -r "$MODDIR/apkapple.sh" "$MODDIR/transpile.py" "$MODDIR/migrate.sh" "$MODDIR/rt" "$TXAPP/"
[ -d "$MODDIR/demo" ] && cp -r "$MODDIR/demo" "$TXAPP/"      # the demo module zip that gets bundled into every app
# Put back the request email saved by  pockettk set-email  (so re-flashing the module does not reset it).
EF=/data/data/com.termux/files/home/.pockettk/request_email
if [ -f "$EF" ]; then
  EM=$(head -n 1 "$EF" | tr -d ' \r\n')
  case "$EM" in
    ""|*[!A-Za-z0-9._%+@-]*) ;;
    *) sed -i "s|REQUEST_EMAIL = \"[^\"]*\"|REQUEST_EMAIL = \"$EM\"|" "$TXAPP/rt/Contact.java" ;;
  esac
fi
rm -f "$TXBIN/molt"
for n in pockettk tkdroid apkapple apple; do
  printf '#!/data/data/com.termux/files/usr/bin/sh\nPOCKETTK_CALLED=%s exec sh /data/data/com.termux/files/usr/share/pockettk/apkapple.sh "$@"\n' "$n" > "$TXBIN/$n"
  chmod 755 "$TXBIN/$n"
done
chmod 755 "$TXAPP/apkapple.sh"
chmod -R a+rX "$TXAPP"
chown -R "$(stat -c %u:%g "$TXBIN")" "$TXAPP" "$TXBIN/pockettk" "$TXBIN/tkdroid" "$TXBIN/apkapple" "$TXBIN/apple" 2>/dev/null
chcon -R --reference="$TXBIN" "$TXAPP" "$TXBIN/pockettk" "$TXBIN/tkdroid" "$TXBIN/apkapple" "$TXBIN/apple" 2>/dev/null
exit 0
