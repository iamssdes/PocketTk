#!/data/data/com.termux/files/usr/bin/sh
# PocketTk for Termux WITHOUT root. Run once from the unzipped folder, as the normal Termux user:
#     sh termux_setup.sh
set -e
export DEBIAN_FRONTEND=noninteractive      # never stop to ask a question: it must not wait for an answer
D=$(cd "$(dirname "$0")" && pwd)
echo "- installing the build tools (python, java, aapt2, apksigner, dx)"
pkg update -y -o Dpkg::Options::=--force-confnew
pkg install -y -o Dpkg::Options::=--force-confnew python openjdk-21 aapt2 apksigner dx termux-tools unzip curl android-tools
if [ -f "$D/install_termux.sh" ]; then      # not there when this runs from the copy inside Termux (the command is already installed)
  echo "- installing the pockettk command"
  sh "$D/install_termux.sh" "$D"
fi
# android.jar (the Android API, needed to compile): copy yours to ~/storage/shared/PocketTk/android.jar, or let this fetch it
if [ -z "$(ls "$HOME"/android-sdk/platforms/android-*/android.jar 2>/dev/null)" ] && [ ! -f /sdcard/PocketTk/android.jar ] && [ ! -f "$HOME/.pockettk/android.jar" ]; then
  echo "- fetching android.jar (about 65 MB, one time)"
  T=$(mktemp -d)
  if curl -fL -o "$T/p.zip" https://dl.google.com/android/repository/platform-34_r03.zip && unzip -qo "$T/p.zip" -d "$T/p"; then
    J=$(ls "$T"/p/*/android.jar | head -n 1)
    mkdir -p "$HOME/.pockettk" && cp "$J" "$HOME/.pockettk/android.jar"
    echo "  saved $HOME/.pockettk/android.jar"
  else
    echo "  could not download it. Copy any android.jar (API 26+) to /sdcard/PocketTk/android.jar and run: pockettk doctor"
  fi
  rm -rf "$T"
fi
echo "- done."
echo "  Try:  pockettk doctor"
