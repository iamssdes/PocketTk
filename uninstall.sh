#!/system/bin/sh
# Keeps the signing key so apps you built can still be updated.
rm -rf /data/local/tmp/apple /data/local/tmp/apkapple /data/local/tmp/tkdroid
for n in molt tkdroid apkapple apple; do rm -f /data/data/com.termux/files/usr/bin/$n; done
rm -rf /data/data/com.termux/files/usr/share/tkdroid /data/data/com.termux/files/usr/share/apkapple /data/data/com.termux/files/usr/share/apple
