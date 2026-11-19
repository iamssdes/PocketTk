# Changelog

## v4.0.0
- **Renamed to PocketTk** (was TkDroid, before that ApkApple). Command `pockettk`; `tkdroid`, `apkapple` and `apple` still work but are deprecated and print a warning.
- New app packages are `pockettk.ssdes.<id>`. `update` / `update-all` move apps from `tkdroid.ssdes.*` and older to the new package, copying their data (needs root; without root the old app is kept).
- Flashing the module moves `~/.tkdroid`, `/data/adb/tkdroid` and the TkDroid folders on the SD card to their PocketTk names, signing key included.
- **Runs without root** in Termux: installs through Android's installer, or silently with adb. `termux_setup.sh` installs the tools.
- **Windows, Linux and macOS**: `pockettk.py` (Python 3.8+, JDK) builds, installs over adb, and has `--aab` for Google Play bundles.
- **PC preview window**: run the app's real code in a phone-shaped window with selectable Height, Width, density and CPU strength (approximate, not Android).
- **iOS, macOS only, experimental**: `--ipa` translates a core subset of tkinter to Swift and builds an .ipa; `--simulator` runs it in the iOS Simulator. Never compiled on a real Mac yet.
- About page updated.
