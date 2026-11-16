# TkDroid v3.1.4 Patch 2

KernelSU module: turn a Python **tkinter** script into an installable Android APK, from the Termux terminal.
(Python is transpiled to Java; there is no embedded CPython.)

## About this project
Made by Palalgon & Claude. The code was written by AI (Claude, made by Anthropic), directed by Palalgon. It started as a fun idea. I do not take pride in this work as if it were mine: the AI did the work, I only had the idea and steered it.

## Demo, consumer and full version
The **About TkDroid** page (the link in the version label of every app built with TkDroid) answers "Can I use it?" with **Yes**: TkDroid is open source, and the page has a button to the repo, https://github.com/iamssdes/TkDroid. The **full** version is that repo. The **demo** and **consumer** editions below are cut-down builds of it. **Consumer does not mean full.**
- **Full** has everything. **Consumer** has everything except `#use: builtin-editing` and the `#builtins:` lines (they are ignored with a warning), so apps built with it always show the version label (the link to About TkDroid). **Demo** is the cut-down edition described below.
- **Download the demo:** the About page no longer has this button (it points to the repo instead); its code (`startDemoSave` in `rt/About.java`) is still there, unused. It copied the demo module zip out of the app itself into the phone's Downloads folder, then told the person to install it from the KernelSU app (Modules > Install). Nothing is downloaded from the internet and there is no link to set: the full TkDroid bundles the demo zip inside **every app it builds**, in the `.apk` and in the `.aab` (as `assets/TkDroid_Demo.zip`, about 170 KB), from the file `demo/TkDroid_Demo.zip` in the module folder. It checks that what it copies really is a zip and never leaves a half file behind. Closing the page cancels a running save. Apps built with the TkDroid Demo cannot contain the demo (it cannot contain itself), so their About page says so instead of showing the button.
- **Request the consumer version:** the About page no longer has a button to the **TkDroid Request** page (`rt/Request.java`), but the page is still in the module. It asks for name (optional), email and request details (both required). *Send request* sends the request from inside the app, without leaving it: a JSON post to `Contact.FORM_ENDPOINT + Contact.REQUEST_EMAIL` (default: the free FormSubmit service, which emails it to `Contact.REQUEST_EMAIL`; it sends a one-time confirmation email to that address after the first request, and delivers requests once you confirm it). The page tells the person which service the request goes through. If the post fails or is not accepted (offline, not yet confirmed), the person's email app opens with everything filled in instead, or the request is copied if there is no email app. If an address or link is not set in `rt/Contact.java`, the page says so instead of failing.
- Set the email once with `tkdroid set-email you@example.com` (Termux, normal user). It is saved in `~/.tkdroid/request_email` and put back every time the module is flashed, so re-flashing does not reset it; `tkdroid set-email` alone shows the current one. By hand instead, before you flash (in the module folder, in Termux):
  ```sh
  sed -i 's|REQUEST_EMAIL = "[^"]*"|REQUEST_EMAIL = "you@example.com"|' rt/Contact.java
  ```
  (an address with `&`, `|` or `\` in it needs those escaped with a backslash for `sed`). After the module is flashed, the copy Termux builds from is `$PREFIX/share/tkdroid/rt/Contact.java`, so the same `sed` on that file changes the address for the next builds until the module is flashed again. The request page needs the INTERNET permission, so **every app now has it** (see Supported).
- `python3 make_demo.py` (a separate file, not part of the module zip) builds the **TkDroid Demo** module from this folder (it leaves the `demo/` folder out by itself); put the resulting zip at `demo/TkDroid_Demo.zip` in the full module, which is what gets bundled into apps. The demo builds plain tkinter scripts of up to 100 lines with `#use: fppe` only; no `--id` / update / update-all / revert / delete / `--icon` / `--dispose` / `-p` / `--aab`; no Canvas, Toplevel, Notebook, Menu, OptionMenu, PanedWindow, Scale or Spinbox; no network, files (`open`, `os`, `csv`), threads or sqlite3; the version label always shows and says "Demo". Flashing the full module removes the demo module.
- `python3 make_consumer.py` (also a separate file, not part of the module zip) builds the **TkDroid Consumer** module from this folder. Its only limit is the `builtin-editing` one: `CONSUMER` in `transpile.py` and `consumer_notes` in `apkapple.sh`. It keeps the `demo/` folder, so apps built with it still bundle the demo. Flashing the full module removes the consumer and demo modules; flashing the consumer removes the demo; the demo refuses to install over either.
- The limits are plain code in `transpile.py` (`DEMO_*` constants at the top) and `apkapple.sh` (`demo_block`), so anyone who edits the scripts can lift them; this is a demo, not copy protection.

## Commands (run in Termux as a normal user, not tsu)
    tkdroid run app.py [--dispose] [--logs] [--debug] [--icon IMG] [--id NAME]   build + install + launch
    tkdroid build app.py [-n NAME] [-p PKG] [-o OUT.apk] [--icon IMG] [--id NAME] [--debug] [-i] [-k]
    tkdroid build app.py --aab [--id NAME] [--target-sdk N] [--keystore FILE [--ks-alias A]]   Android App Bundle for Google Play
    tkdroid update --id NAME [code.py] [--patch|--small|--medium|--large] [--debug]
    tkdroid revert --id NAME                  restore the version saved by update --debug (normally automatic)
    tkdroid update-all [--force] [-j N] [--patch|--small|--medium|--large]   (also moves apple.app.* and tkdroid.molt.* apps to tkdroid.palalgon.*)
    tkdroid ids
    tkdroid delete --id NAME [-y]            uninstall the app (its data goes too) and remove the id
    tkdroid delete-uninstalled                remove the ids of apps that are no longer installed
    tkdroid logs [PKG|app.py] [-f] [-e] [-c]  errors/prints with Python line numbers
    tkdroid check app.py                      transpile only, show warnings
    tkdroid install FILE.apk [PKG]
    tkdroid list | uninstall [PKG|app.py|--all] | demo | doctor
    tkdroid help [--brief]                    --brief: what TkDroid is and how to use it, in a few lines
The old names `apkapple` and `apple` still work. First run: `tkdroid doctor`. `tkdroid demo` writes example scripts.

## Directives (comments at the top of the script)
    #use: fppe, dpooa, filepick, datasaving, audio, builtin-editing     (any number, comma separated)
    #builtins: version-label: disable
    #dpooa: draggable
    #audio: dQw4w9WgXcQ
    #playlist: id1 id2 id3
- **fppe** – floating-point fix: 0.1+0.2 prints 0.3.
- **dpooa** – shows the app as a window over other apps (needs "display over other apps"; the install grants it, otherwise a settings page opens). With `run --dispose` it is removed only when fully closed. **New windows:** with dpooa active, `Toplevel(root)` opens a floating window of its own (title bar with minimize and close, drag by the title bar, `#dpooa: resizable` adds the resize grip, `#dpooa: alpha`, `bg` and `bar` apply too) instead of a panel inside the main window; each new one is offset a little so it does not cover the one that opened it. On a Toplevel `title()`, `protocol("WM_DELETE_WINDOW", fn)`, `geometry()`, `attributes("-alpha", x)`, `iconify()`, `withdraw()`, `deiconify()`, `lift()` and `destroy()` act on that window; dialogs (`messagebox`, `simpledialog`) open in the window you last touched. **Keyboard:** dpooa windows never hold on to the keyboard. A window only takes it while you type in one of its fields (it opens when you tap a field, or when the script calls `focus_set()` or shows an input dialog) and hands it back the moment you touch anything outside that window (another app, another dpooa window), press back, minimize or close it. So typing in another app or in a different window of the same app works while a dpooa window is open, and the back key goes to the app behind when you are not typing. Without dpooa (or when the permission is missing) a Toplevel stays a panel inside the page as before. Control the window with `#dpooa:` comments (header only; several lines allowed; commas or spaces):

  | option | effect |
  |---|---|
  | `draggable` | drag the window by its title bar |
  | `resizable` | corner grip (bottom-right) to resize |
  | `snap` | after a drag, snaps to the nearest left/right screen edge |
  | `size 320x400` | width x height in dp; also `90%x50%`, `300xauto`, `fullx300` (or `width N`, `height N`) |
  | `pos 40 120` | x y in dp from top-left; or `pos center/top/bottom/left/right/topleft/topright/bottomleft/bottomright` |
  | `alpha 0.8` | window transparency, 0.1-1 |
  | `title "My HUD"` | fixed title-bar text (script `root.title()` won't change it) |
  | `bar #RRGGBB` / `bg #RRGGBB` | title bar / window background color |
  | `minimize off` / `close off` | hide the – / × button (then close it from code with `root.destroy()`) |
  | `startminimized` | start collapsed to the title bar |
  | `clickthrough` | window ignores touches (display-only HUD; close it from code) |

  Example: `#dpooa: draggable, resizable, size 320x50%, pos topright, alpha 0.9, title "Notes"`

  From your code, inside a dpooa window: `root.geometry("300x200+20+40")` (dp; negative offsets count from the right/bottom), `root.geometry()` (read), `root.attributes("-alpha", 0.6)`, `root.iconify()` / `root.deiconify()` (collapse / restore).
- **filepick** (aliases `native filepicker`, `native`) – real Android file picker behind `filedialog` (`askopenfilename`, `asksaveasfilename`, ...). Works as `f = askopenfilename()` or with `callback=`/`command=`.
- **datasaving** – all module-level variables survive leaving and relaunching. (Don't combine with `--dispose`: dispose uninstalls the app and its data.)
  - **Windows remember their place (with `dpooa`):** with both `#use: dpooa` and `#use: datasaving`, the main window and every `Toplevel` save where they are (position, size if you resized it, collapsed or not) whenever you move, resize or collapse them, and come back there the next time they are created. The saved place wins over `#dpooa: pos/size`, which only decide the very first start. A `Toplevel` is matched by its name: write `tk.Toplevel(root, name="note_3")` (a normal tkinter argument; give each window its own name, and the same name again later to get the same place). Without `name=`, windows are matched by creation order (the 1st, 2nd, ... Toplevel made in a run). A closed window keeps its remembered place. TkDroid cannot rebuild a window's contents by itself, so keep a module-level list of the windows that are open (it is saved like any variable) and create them again at startup. The place of a window you did not open this time is kept. `#datasaving: datareset` also forgets all window places. Switch it off with `#builtins: window-memory: disable`.
  - **Reset the data:** put the comment `#datasaving: datareset` inside a function. Whenever that function runs, the saved data is wiped and the module-level variables go back to the values your code first gave them (an `IntVar`/`StringVar` is reset in place, so widgets bound to it update). Needs `#use: datasaving`; ignored with a warning outside a function. The comment can be the whole body:
    ```python
    def resetdata():
        #datasaving: datareset
    ```
    **Confirmation:** the reset asks "Erase all saved data and start over?" first. If the user taps Cancel, nothing is wiped and the function ends right there (the lines after the comment don't run). Write `#datasaving: datareset: noconfirm` to reset immediately, with no question.
    ```python
    def resetdata():
        #datasaving: datareset: noconfirm
    ```
    Labels you set by hand (`label.config(text=...)`) are not refreshed automatically - update them after calling the function. `reset`, `resetdata` also work as the word after the colon, and `noconfirm` can be written `no-confirm` / `no_confirm`.

- **audio** (aliases `music`, `sound`) – plays music in the app. It is comment-only: nothing to import or call, and it does nothing in an IDE; it plays in the built app.

  | line | effect |
  |---|---|
  | `#audio: dQw4w9WgXcQ` | one track: a YouTube video id (the `v=` part), a YouTube URL, any other URL, or a file |
  | `#playlist: id1 id2 id3` | several tracks, played in order (spaces or commas; several lines add up) |
  | `noloop` / `loop` | in either line: play once / repeat (default: repeat the track or the whole list) |

  Sources can be mixed: `#playlist: dQw4w9WgXcQ https://example.com/a.mp3 /sdcard/Music/b.m4a "my songs/c.ogg"`. Relative paths are looked up next to the script. `#audio:` alone also switches audio on, so `#use: audio` is optional.

  **How it works:** tracks are fetched when you build and bundled inside the APK, so the app plays them offline and needs no permissions. Downloads are cached in `~/.tkdroid/audio-cache`, so later builds/updates are fast. Direct audio links are downloaded with Python; YouTube and other page links need `yt-dlp` plus a JavaScript runtime in Termux (YouTube requires one): `pkg install yt-dlp nodejs` (or `pip install -U "yt-dlp[default]"`). `tkdroid doctor` shows whether they are found. If a track cannot be fetched the build still finishes, with a warning that says why. Music pauses when the app goes to the background and resumes when you come back (dpooa overlays keep playing until closed). Large playlists make a large APK (about 4 MB per song).

## Built-ins (on by default) and how to switch them off
| name | what it does |
|---|---|
| `version-label` | small app version text plus the TkDroid version it was built with and an underlined **About TkDroid** link (e.g. `v1.2.3 Patch 2, Built With TkDroid 3.1.2`) at the bottom right corner, drawn over your UI; tapping the link opens the About page, everywhere else touches pass through it (with a dpooa resize grip it sits just left of the grip) |
| `datareset-confirm` | the question asked before `#datasaving: datareset` wipes the data |
| `error-toast` | the toast shown when a callback raises (the traceback still goes to `error.log` / logcat) |
| `window-memory` | with `#use: dpooa` **and** `datasaving`: the dpooa windows remember their position, size and collapsed state (see below) |

To turn one off, enable builtin editing and name it:

    #use: builtin-editing
    #builtins: version-label: disable

Several names can share a line (`#builtins: version-label, error-toast: disable`) and the line can be repeated. `enable` turns one back on. Without `#use: builtin-editing` the `#builtins:` lines are ignored with a warning, and an unknown name gives a warning. The same line also works for the opt-in features (`fppe`, `dpooa`, `filepick`, `datasaving`, `audio`): `#builtins: audio: disable` switches audio off even if `#audio:` lines are present. A per-line `#datasaving: datareset: confirm` / `noconfirm` always wins over `datareset-confirm`.

## Google Play upload (`--aab`)
`tkdroid build app.py --id notes --aab` makes `notes.aab` (default folder `/sdcard/Download/TkDroid/`) instead of an APK. An `.aab` cannot be installed on the phone; it is only for uploading in Play Console. `run`, `-i` and `--debug` do not work with it.
- **One-time setup:** `pkg install openjdk-21 aapt2 dx`, and download `bundletool-all-VERSION.jar` from github.com/google/bundletool/releases to `~/bundletool.jar` (`tkdroid doctor` shows whether it was found; `BUNDLETOOL=/path/to.jar` also works).
- **Upload key:** the first time, an upload key is created in the module's data folder (`apple.keystore` is the APK key and is not used for this; the new one is `upload.keystore` with its password in `upload.pass`). **Back both files up**: Play needs the same upload key for every upload. Or use your own with `--keystore FILE` (password from the `TKDROID_KS_PASS` variable or a prompt; `--ks-alias NAME` if it has several keys). The SHA-256 fingerprint is printed after signing. Play signs the installed app with its own key (Play App Signing).
- **Version code:** Play refuses an upload whose versionCode is not higher than the last one. Build with `--id NAME` so it counts up on every build (`--small/--medium/--large/--patch` as usual).
- **Target SDK:** `--aab` targets API 36 by default (Play requires a recent target API; change with `--target-sdk N`). Plain APK builds stay at 34. Android 15+ draws apps edge to edge when the target is 35 or higher, so check how your layout looks.
- **Not done by TkDroid:** Play Console account, app listing, icon/screenshots, privacy policy and the testing tracks.
Untested on a real device and with the real aapt2 and bundletool; only the packing and signing steps were run on a desktop.

## Icon
`--icon logo.png` (png, webp or jpg; square, 512x512 or larger is best) sets the launcher icon for `build` and `run`.

## Updating an app (like a store update)
    tkdroid run app.py --id notes        # first time: registers id "notes" (fixed package name)
    tkdroid update --id notes code.py -i # later: rebuild and install over the old app
    tkdroid update --id notes            # reuse the saved source path
    tkdroid ids                          # list your ids
The id pins the package name (new apps get `tkdroid.palalgon.NAME`; apps built before that change keep their older `tkdroid.molt.NAME` or `apple.app.NAME` package until `update-all` moves them) and the signing key stays the same, and every build bumps the version code, so Android treats it as an update: the app's data (including `datasaving` state) is kept. Name and icon are remembered unless you pass new ones. Don't use `--dispose` with ids you want to keep updating.

## Updating all apps to a new TkDroid version
An app contains the runtime it was built with, so installing a newer TkDroid does not change apps you already built. `tkdroid update-all` rebuilds every app that has an id from its saved source and installs it over the old one (data is kept, apps are not launched). Apps already built with the current TkDroid are skipped, as are apps that are no longer installed; `--force` rebuilds those anyway. The update kind flags work as in `update` (default `--patch`) and apply to all apps. One failing app does not stop the others; they are listed at the end. It is fast on purpose: the TkDroid runtime is compiled once (cached in `~/.tkdroid/rt`, rebuilt automatically when TkDroid or android.jar changes) and every app only compiles its own code; the first app builds alone, the rest are built `-j N` at a time (default: half the CPU cores, 1 to 3; `-j 1` builds one by one with live output). Apps whose source files share a file name are always built one at a time. The runtime sits in each APK as a second dex (`classes2.dex`). If the cache ever misbehaves, `APKAPPLE_NO_RTCACHE=1 apkapple update-all` builds the old way. If an app's source file moved, fix it with `tkdroid update --id NAME code.py`. Apps built without `--id` are not tracked and are not touched. `tkdroid ids` marks apps built with an older TkDroid.

## Version numbers (what the version label shows)
An app built with an id has a version `A.B.C` plus a separate patch counter. You say what kind of update it is when you rebuild:

| flag | effect | example |
|---|---|---|
| `--patch` (default) | raise the patch counter only | `v1.2.3` -> `v1.2.3 Patch 1` -> `Patch 2` |
| `--small` | raise the last number, reset patch | `v1.2.3 Patch 2` -> `v1.2.4` |
| `--medium` | raise the middle number, reset the last number and patch | `v1.2.4` -> `v1.3.0` |
| `--large` | raise the first number, reset the others and patch | `v1.3.0` -> `v2.0.0` |

    tkdroid update --id notes code.py --small
    tkdroid run app.py --id notes --medium

The first build of an id is `v1.0.0`. Ids made before v2.3.1 continue from `1.0.<old number>`. Android's internal version code still goes up by one on every build, whatever the flag. Without `--id` the version stays `v1.0.0`. `tkdroid ids` lists each app's current version.

## Debug builds (`--debug`)
`--debug` (on `build`, `run` and `update`) adds a **log panel under the app**: everything the program prints (`print`, `sys.stdout/stderr`), uncaught errors with Python line numbers, and runtime warnings (unsupported widget/option/method). The panel has COPY, CLEAR and a collapse button; it keeps the last 500 lines. It works in dpooa windows too.

    tkdroid update --id notes code.py --debug

`update --debug` is for trying a change safely:
1. It saves the installed version of the app, builds the new code with the log panel, installs it over the old one (data kept) and launches it.
2. When you **close the app** (back button, or swipe it away; going to the home screen does not count), the previous version is installed back over it. Again nothing is uninstalled, so the app's data stays.
3. The id record (version numbers, source path) goes back to what it was, so the debug build does not count as a release.

How the restore works: Android refuses to install a lower version code over a higher one, so TkDroid makes a copy of the old APK with its version code raised past the debug build and signs it with your TkDroid key. The old app's code and version label are unchanged.
If the restore does not happen (for example the phone rebooted first), run `tkdroid revert --id NAME`; a second `update --debug` of the same id is refused until then. If the id has no installed app, the debug build simply stays installed. The result of each restore is appended to `/data/local/tmp/apkapple-debug.log`.

## Folders and the move from ApkApple
TkDroid keeps its files here (they used to be named ApkApple / `.apple`):

| What | Where |
|---|---|
| Signing key, app ids, audio cache, work files | `~/.tkdroid` in Termux (`/data/adb/tkdroid` for root) |
| Demo scripts and built APKs | `/sdcard/Download/TkDroid/` |
| Your own `android.jar` / `sdk` (optional) | `/sdcard/TkDroid/` |
| The tool itself, copied into Termux | `$PREFIX/share/tkdroid` |

**Flashing this module moves everything over:** the old `~/.apple`, `/data/adb/apple`, `/sdcard/Download/ApkApple` and `/sdcard/ApkApple` become the new folders (the signing key included, so your apps keep updating), and the `src=` / `icon=` paths inside the app ids are rewritten to match. If a new folder already exists, only the files it lacks are copied. If Termux was not ready at flash time, the first `tkdroid` command moves `~/.apple` by itself. The old `/sdcard/ApkApple/...` locations are still searched for `android.jar` and `sdk`.

**Package names:** new apps use `tkdroid.palalgon.NAME` (earlier versions used `tkdroid.molt.NAME` and, before that, `apple.app.NAME`). `tkdroid update-all` moves every app that is still on one of the older names to `tkdroid.palalgon.NAME` (even if it was already built with this version): it builds the app under the new package, copies the old app's saved data (`files`, `shared_prefs`, `databases`, so `datasaving` state survives) into it as root, then uninstalls the old one. If the data copy fails, the old app is kept and you are told. An id whose app is not installed just gets its package name changed in the record. Apps with a custom `-p` package are left alone. For one app: `tkdroid update --id NAME --new-pkg`.

## About TkDroid page
Every app has a small underlined **About TkDroid** link at the end of its version label (switch the label off with `#builtins: version-label: disable`). It opens a plain page inside the app that explains what TkDroid is, why it exists, how apps are made with it, who makes it, and the "Can I use it?" answer (yes, it is open source; the **Open the repo on GitHub** button opens https://github.com/iamssdes/TkDroid, set as `REPO_URL` in `rt/About.java`). Close it with the Close button or the back button. The pages are `rt/About.java`, `rt/Request.java` and `rt/Contact.java` (the email address), with the networking in `rt/Online.java`, so their text is easy to edit.

## Deleting apps
`tkdroid delete --id NAME` **uninstalls the app, including its saved data**, and removes the id (version record, leftover `update --debug` files). It asks first when run in a terminal; `-y` skips the question. Your `.py` script is never touched. The id's package name is freed, so building the same id again starts over at `v1.0.0`.

`tkdroid delete-uninstalled` removes the ids of apps that are no longer installed (for example ones you uninstalled by hand). It reads the phone's package list first and does nothing if it cannot, so it never deletes an id by mistake.

## Dispose
`run --dispose` uninstalls and deletes the app the moment it leaves the foreground (dpooa apps: when fully closed).

## Errors
Uncaught errors show a Python-style traceback with your line numbers, in logcat (tag `ApkApple`), `filesDir/error.log`, and a toast.

## Supported
Widgets (Label, Button, Entry, Text, Checkbutton, Radiobutton, Scale, Listbox, Combobox, Spinbox, Progressbar, Separator, Canvas, Frame, **Notebook**, **Menu**...), pack/grid/place, StringVar etc., messagebox/simpledialog/**colorchooser**, input(), after(); `with`, comprehensions, `*args/**kwargs`, nonlocal, del, custom exceptions.

**Notebook** has real tabs: a black tab bar, equal-width tabs, the selected one white and bold with a crimson underline, idle ones gray. `add/insert/forget/hide/select/index/tabs/tab`, `<<NotebookTabChanged>>`, `enable_traversal`. Colors: `nb.config(bg=, fg=, accent=, idle=, tabbg=)`.

**Menu**: `root.config(menu=bar)` shows a menu bar under the title; tapping a name opens a popup list. `add_command/add_cascade/add_separator/add_checkbutton/add_radiobutton`, `insert_*`, `delete`, `entryconfig`, `entrycget`, `index`, `invoke`, `accelerator=` (shown as text), `state="disabled"`, nested cascades (with a Back row), and `menu.tk_popup(x, y)` for context menus. Checks and radios use your `BooleanVar`/`StringVar`.

**colorchooser.askcolor(color=None, title=None)** shows a dialog (palette + R/G/B sliders) and returns `((r, g, b), "#rrggbb")`, or `(None, None)` on Cancel.

**Classes**: inheritance and multiple inheritance (C3 order), `super()`, several instances of one class, subclassing `tk.Frame`/`tk.Tk`/`threading.Thread`, `@staticmethod`, `@classmethod`, `@property` (+ setter), `@dataclass`, `Enum`, and dunder methods (`__str__ __repr__ __eq__ __lt__ __add__ __getitem__ __len__ __iter__ __call__ __contains__ __enter__ __exit__`...).
**Generators** (`yield`, `yield from`, `.send`, `.close`), **decorators** (functions and classes), **bytes/bytearray** and binary file modes (`"rb"`, `"wb"`).

**Libraries**: json, os, sys, re, datetime, threading, collections, math, random, time, string, plus itertools, functools (reduce, partial, wraps, lru_cache, cache, cmp_to_key), copy, operator, statistics, hashlib (md5/sha1/sha2), base64, uuid, csv (reader, writer, DictReader, DictWriter), socket (TCP/UDP), urllib.request / urllib.parse, a small `requests` (get/post/put/delete, params, json, data, headers, timeout) and sqlite3 (connect, cursor, execute, executemany, fetch*, commit; `?` parameters). Every app has the INTERNET permission (the About page downloads the demo and the Request page sends requests); importing socket, urllib or requests additionally allows plain-http (cleartext) traffic for that app.

## Not supported / limits
Audio: no live streams or YouTube playlist links (list the video ids with `#playlist:`), no playback control from Python, no playback after the app is closed.
Treeview; images/PhotoImage; askdirectory returns the app's files dir. Subclassing built-in types (dict, list...) is not supported. Exception classes with their own methods/attributes are only partly supported. sqlite3 named parameters (`:name`) and row factories are not supported; `csv` dialects other than delimiter/quotechar/quoting are ignored. `requests` is a minimal built-in; **pip packages cannot be used** (there is no embedded CPython).

## Tested on a real device
All features (version label and builtin switches, datareset and its confirmation dialog, audio, overlay permission, native file picker, dispose watcher and the runtime's dexing) were tested on a real phone, the author's own. Results may vary depending on your device's age, Android version and root status. Report anything odd with `tkdroid logs -e`.
The log panel and `update --debug` / `revert` / `help --brief` were added after that testing: the generated code, the shell flow (with stubs) and the version-code patching were checked on a desktop, not yet on a phone.

v3.1.0 (Notebook tabs, Menu, colorchooser, classes, generators, decorators, bytes, new libraries) was checked on a desktop with stubs and compared with CPython; none of it has been tried on a phone yet. sqlite3 and the Menu/Notebook/colorchooser look in particular are untested on a device.
The small version label now reads `<app version>, Built With TkDroid <version>`.
v3.1.4 Patch 2 (`tkdroid set-email`, and the module puts the saved address back on every flash) was checked on a desktop with fake folders: setting and showing the address, refusing bad addresses, and re-applying after a fresh copy (a tampered saved file is ignored). It has not been run on a phone yet.
v3.1.4 (the demo is bundled inside every app and copied out of it, not downloaded) was checked on a desktop: the copy logic (good zip, web page, empty file, cancel), the build step that adds the asset (full edition adds it, demo edition does not), the install-script copy and the email `sed` command. `About.java` was only syntax-checked, because there are no Android classes here to compile it against. It has not been run on a phone yet, including inside an `.aab`.
v3.1.3 (the in-app demo download and the in-app request) was checked on a desktop: the networking (`Online`) against a local test server (redirects, a web page instead of a zip, 404, truncated and cancelled downloads, accepted and refused posts) and the About/Request pages by compiling them against stubs of the Android classes. It has not been run on a phone yet. Not tried either: the real FormSubmit service, and saving into Downloads on different Android versions.
v3.1.2 (the About TkDroid link and page) has not been tried on a phone yet, including opening it from a dpooa floating window; the folder move on flash and the `update-all` package move are also untested on a device.
