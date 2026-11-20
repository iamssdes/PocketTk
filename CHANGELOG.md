# Changelog

## v4.1.0
- **The PocketTk window for macOS, Windows and Linux (no system terminal needed).** It is terminal-style: a prompt with history and Tab completion (commands, options, app ids, paths), our own commands (`pick`, `use`, `build`, `check`, `preview`, `install`, `devices`, `update`, `apps`, `doctor`, `setup`, `cd`, `ls`, `reveal`, `outdir`, `theme`, `help`), live build output, Ctrl+C to cancel, drag a `.py` onto the window, the Android SDK licence and keystore password asked on the prompt line, four colour themes. APK or Google Play `.aab`. No local web server or network port (pywebview's own bridge). `pockettk gui [file] [--pick]` opens it. `webui/`, `pockettk_gui.py`.
- **Packages:** macOS `.dmg` (Apple Silicon, Intel), Windows installer (per-user, no admin rights) and portable `.zip`, Linux `.AppImage` (one file, double-click; needs FUSE) and `.tar.gz` with `./install.sh`. Each carries its own Python and pywebview (Linux: Qt WebEngine from pip wheels). `packaging/macos/`, `packaging/windows/`, `packaging/linux/`, built by `.github/workflows/apps.yml` on a `v*` tag (a tag with a `-`, like `v4.1.0-rc1`, makes a pre-release).
- **The app installs its own dependencies** on first start: a JDK (Eclipse Temurin), Google's Android build tools (you accept the SDK licence on the prompt) and bundletool, all into `~/.pockettk`. No administrator password. `pockettk_deps.py`. A JDK PocketTk downloaded itself is found by every command.
- The Windows app runs without a console: output goes to `~/.pockettk/gui.log`, build tools do not flash console windows.
- **New logo** (a phone with "Tk" peeking out of a pocket): `packaging/logo.svg`; the app icons are drawn from it. **Flashing the module prints the logo** as plain ASCII (no colour, no box-drawing characters: the KernelSU flash screen does not render them reliably).
- **macOS bundle id is `com.ssdes.pockettk`.** Old `tkdroid.palalgon.*` apps are still recognized and moved by `update`.
- **Android has no window.** `pockettk gui` and `pockettk make-gui` just say so; use the commands. (Flashing the module, or the install step, removes leftovers of earlier test builds of an Android window.)
- **New directive `#use: termux-connect` and `import termux`** for any app: `termux.state()`, `termux.ask()`, `termux.run(args, callback)`. It runs a command in Termux through Termux's `RUN_COMMAND` intent and returns exit code, stdout and stderr; the directive adds the `com.termux.permission.RUN_COMMAND` permission, a package query and a result receiver to the manifest, and `import termux` without the directive is an error. Needs `allow-external-apps = true` in Termux's `termux.properties` (add it yourself, then `termux-reload-settings`).
- Tests: `tests/test_deps.py`, `test_gui.py`, `test_packaging.py`, `test_termux_connect.py`, `test_terminal_page.py` (real page in headless Chromium when Playwright is installed), `test_core.js`, `test_shell.js`.
- **Not tested on real systems yet:** the window on a real Mac, Windows or Linux desktop, the CI builds, the installer and AppImage, the first-run downloads, and `#use: termux-connect` on a phone were only tested here with stand-ins.

## v4.0.0
- **Renamed to PocketTk** (was TkDroid, before that ApkApple). Command `pockettk`; `tkdroid`, `apkapple` and `apple` still work but are deprecated and print a warning.
- New app packages are `pockettk.ssdes.<id>`. `update` / `update-all` move apps from `tkdroid.ssdes.*` and older to the new package, copying their data (needs root; without root the old app is kept).
- Flashing the module moves `~/.tkdroid`, `/data/adb/tkdroid` and the TkDroid folders on the SD card to their PocketTk names, signing key included.
- **Runs without root** in Termux: installs through Android's installer, or silently with adb. `termux_setup.sh` installs the tools.
- **Windows, Linux and macOS**: `pockettk.py` (Python 3.8+, JDK) builds, installs over adb, and has `--aab` for Google Play bundles.
- **PC preview window**: run the app's real code in a phone-shaped window with selectable Height, Width, density and CPU strength (approximate, not Android).
- **iOS, macOS only, experimental**: `--ipa` translates a core subset of tkinter to Swift and builds an .ipa; `--simulator` runs it in the iOS Simulator. Never compiled on a real Mac yet.
- About page updated.
