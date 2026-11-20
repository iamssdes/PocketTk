#!/usr/bin/env python3
"""The PocketTk window: choose a .py file, press "Transpile & Export", get an APK (or a Google Play bundle).

No terminal needed. It is a terminal-style page (webui/) shown in a native window by pywebview (the system's own web view; there is
no HTTP server and no network port). The page talks to the Api class below through pywebview's JavaScript bridge.

    python pockettk.py gui                 open the window
    python pockettk.py gui app.py          ... with a file already chosen
    python pockettk.py gui --pick          ... and open the file picker right away

Builds run as a child process of the normal command line tool (`pockettk.py build ...`), so the window shows exactly
what the terminal would, and Cancel can stop the whole thing. Everything private starts with an underscore: pywebview
publishes every public name of the Api object to the web page.
"""
import os, sys, re, json, time, signal, threading, subprocess, contextlib, platform

import pockettk as tk
import pockettk_deps as deps

HOME = os.path.expanduser("~")
HERE = os.path.dirname(os.path.abspath(__file__))
WEBUI = os.path.join(HERE, "webui")
SETTINGS_FILE = os.path.join(tk.KS, "gui.json")
IMAGE_EXT = (".png", ".jpg", ".jpeg", ".webp")
BUMPS = ("patch", "small", "medium", "large")
MAX_EVENTS = 20000

# what the build prints -> which step is running (ids are the ones webui/core.js lists)
STEP_PATTERNS = (
    (re.compile(r"^- transpiling"), "transpile"),
    (re.compile(r"^- compiling Java"), "compile"),
    (re.compile(r"^- dexing"), "dex"),
    (re.compile(r"^- adding icon"), "link"),
    (re.compile(r"^- linking manifest"), "link"),
    (re.compile(r"^- packaging"), "package"),
    (re.compile(r"^- building the bundle"), "bundle"),
    (re.compile(r"^- signing"), "sign"),
)


def classify_line(line):
    for rx, sid in STEP_PATTERNS:
        if rx.match(line):
            return sid
    return None


# ---------------------------------------------------------------- one running job
class Ctl:
    """What a running job reports to (and what Cancel acts on). Also the 'ctl' that pockettk_deps expects."""

    def __init__(self, api, kind):
        self._api = api
        self.kind = kind
        self.cancelled = False
        self.proc = None
        self._lic = threading.Event()
        self._lic_answer = False

    def log(self, text):
        self._api._emit(self.kind, "log", text=text)

    def status(self, text):
        self._api._emit(self.kind, "status", text=text)

    def step(self, sid, state):
        self._api._emit(self.kind, "step", id=sid, state=state)

    def progress(self, done, total, label=""):
        self._api._emit(self.kind, "progress", done=done, total=total, label=label)

    def check(self):
        if self.cancelled:
            raise deps.Cancelled()

    def set_proc(self, p):
        self.proc = p
        if p is not None and self.cancelled:
            self.kill()

    def license(self, text):
        self._lic.clear()
        self._lic_answer = False
        self._api._emit(self.kind, "license", text=text)
        while not self._lic.wait(0.2):
            if self.cancelled:
                return False
        return self._lic_answer

    def answer_license(self, yes):
        self._lic_answer = bool(yes)
        self._lic.set()

    def cancel(self):
        self.cancelled = True
        self.kill()
        self._lic.set()

    def kill(self):
        p = self.proc
        if p is None or p.poll() is not None:
            return
        try:
            if os.name == "nt":
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(p.pid)], capture_output=True)
            else:
                os.killpg(p.pid, signal.SIGTERM)
                threading.Timer(3.0, self._kill9, (p,)).start()
        except (OSError, ProcessLookupError):
            pass

    @staticmethod
    def _kill9(p):
        try:
            if p.poll() is None:
                os.killpg(p.pid, signal.SIGKILL)
        except (OSError, ProcessLookupError):
            pass


class _LineSink:
    """File-like object that turns what a function print()s into log lines (for the adb helpers of pockettk.py)."""

    def __init__(self, ctl):
        self._ctl, self._buf = ctl, ""

    def write(self, s):
        self._buf += s
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            if line.strip():
                self._ctl.log(line)
        return len(s)

    def flush(self):
        if self._buf.strip():
            self._ctl.log(self._buf)
        self._buf = ""


class WebviewDialogs:
    """The native macOS / Windows / Linux file chooser, through pywebview."""

    def __init__(self, get_window):
        self._get_window = get_window

    def open(self, types, folder=False, directory=""):
        import webview
        w = self._get_window()
        fd = getattr(webview, "FileDialog", None)
        if fd is not None:
            kind = fd.FOLDER if folder else fd.OPEN
        else:
            kind = webview.FOLDER_DIALOG if folder else webview.OPEN_DIALOG
        res = w.create_file_dialog(kind, directory=directory or "", allow_multiple=False, file_types=tuple(types))
        if not res:
            return None
        return res[0] if isinstance(res, (list, tuple)) else res


# ---------------------------------------------------------------- the object the web page talks to
class Api:
    def __init__(self, script=None, pick=False, dialogs=None, cli=None):
        self._script, self._pick = script, pick
        self._dialogs = dialogs
        self._cli = cli or os.environ.get("POCKETTK_GUI_CLI") or os.path.join(HERE, "pockettk.py")
        self._lock = threading.RLock()
        self._events, self._base = [], 0
        self._jobs = {}          # slot ("work" | "preview") -> (thread, Ctl)
        self._window = None
        self._cwd = None         # the folder the window's `cd` / `ls` work in

    # ---- internals (private: not visible to the page)
    def _attach(self, window):
        self._window = window
        if self._dialogs is None:
            self._dialogs = WebviewDialogs(lambda: self._window)

    def _emit(self, job, t, **data):
        ev = dict(data, job=job, t=t)
        with self._lock:
            ev["i"] = self._base + len(self._events)
            self._events.append(ev)
            if len(self._events) > MAX_EVENTS:
                drop = len(self._events) - MAX_EVENTS // 2
                del self._events[:drop]
                self._base += drop

    def _busy(self, slot):
        j = self._jobs.get(slot)
        return bool(j and j[0].is_alive())

    def _start(self, slot, kind, fn):
        with self._lock:
            if self._busy(slot):
                return {"ok": False, "error": "something is already running: wait for it to finish or press Cancel"}
            if slot == "work" and self._busy("work"):
                return {"ok": False, "error": "busy"}
            ctl = Ctl(self, kind)

            def runner():
                try:
                    fn(ctl)
                except deps.Cancelled:
                    self._emit(kind, "error", message="Cancelled.", cancelled=True)
                except (deps.DepsError, tk.Fail) as e:
                    self._emit(kind, "error", message=str(e))
                except Exception as e:                                   # never leave the page waiting
                    self._emit(kind, "error", message="%s: %s" % (type(e).__name__, e))

            t = threading.Thread(target=runner, name="pockettk-" + kind, daemon=True)
            self._jobs[slot] = (t, ctl)
            self._emit(kind, "start")
            t.start()
        return {"ok": True}

    def _settings_load(self):
        try:
            with open(SETTINGS_FILE, encoding="utf-8") as f:
                d = json.load(f)
            return d if isinstance(d, dict) else {}
        except (OSError, ValueError):
            return {}

    def _settings_save(self, d):
        try:
            os.makedirs(tk.KS, exist_ok=True)
            tmp = SETTINGS_FILE + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(d, f, indent=1)
            os.replace(tmp, SETTINGS_FILE)
        except OSError:
            pass

    def _out_dir(self):
        return self._settings_load().get("outputDir") or tk.out_dir()

    def _ids(self):
        out = []
        d = os.path.join(tk.KS, "ids")
        for name in sorted(os.listdir(d)) if os.path.isdir(d) else []:
            rec = tk.read_id(name)
            if not rec:
                continue
            src = rec.get("src", "")
            ver = rec.get("ver") or "1.0.0"
            p = int(rec.get("patch") or 0)
            out.append({"id": name, "name": rec.get("name") or name, "pkg": rec.get("pkg", ""), "src": src,
                        "srcExists": bool(src and os.path.isfile(src)), "icon": rec.get("icon", ""),
                        "version": ver + (" Patch %d" % p if p else ""), "code": rec.get("vc", "")})
        return out

    def _env(self, extra=None):
        env = dict(os.environ)
        env["PYTHONUNBUFFERED"] = "1"
        env["PYTHONIOENCODING"] = "utf-8"
        out = self._out_dir()
        if out:
            env["POCKETTK_OUT"] = out
        env.update(extra or {})
        return env

    def _run_cli(self, ctl, args, env, parse):
        """Runs `pockettk.py <args>` and follows its output. Returns (exit code, last lines)."""
        cmd = [sys.executable, self._cli] + list(args)
        try:
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env,
                                 **deps._group_kwargs())
        except OSError as e:
            raise tk.Fail("could not start the build: %s" % e)
        ctl.set_proc(p)
        tail = []
        try:
            for raw in iter(p.stdout.readline, b""):
                line = raw.decode("utf-8", "replace").rstrip()
                if not line:
                    continue
                tail.append(line)
                del tail[:-40]
                parse(line)
                ctl.log(line)
            p.wait()
        finally:
            ctl.set_proc(None)
            try:
                p.stdout.close()
            except OSError:
                pass
        ctl.check()
        return p.returncode, tail

    @staticmethod
    def _error_message(tail):
        for line in reversed(tail):
            if line.startswith("pockettk:"):
                return line[len("pockettk:"):].strip()
        return tail[-1] if tail else "the build failed without a message"

    # ---- start-up
    def startup(self):
        s = self._settings_load()
        script = self._script if self._script and os.path.isfile(self._script) else None
        return {"version": tk.VERSION, "platform": platform.system(), "machine": platform.machine(),
                "script": self._describe(os.path.abspath(script)) if script else None,
                "pick": bool(self._pick), "outputDir": self._out_dir(), "ready": not deps.needs_setup(tk),
                "autoSetupDone": bool(s.get("autoSetupDone")), "cwd": self._get_cwd(), "home": HOME, "sep": os.sep}

    # ---- folders: the window is a terminal, so it has a current folder (`cd`, `ls`, tab completion)
    def _get_cwd(self):
        if not self._cwd or not os.path.isdir(self._cwd):
            start = self._settings_load().get("lastDir")
            self._cwd = start if start and os.path.isdir(start) else HOME
        return self._cwd

    def resolve(self, path):
        """~ and relative paths -> an absolute path (relative to the current folder). Does not check that it exists."""
        p = os.path.expanduser(str(path or "").strip())
        if not p:
            return self._get_cwd()
        if not os.path.isabs(p):
            p = os.path.join(self._get_cwd(), p)
        return os.path.normpath(p)

    def pwd(self):
        return {"cwd": self._get_cwd(), "home": HOME, "sep": os.sep}

    def cd(self, path=""):
        target = self.resolve(path or "~")
        if not os.path.isdir(target):
            return {"ok": False, "error": "no such folder: %s" % path}
        self._cwd = target
        st = self._settings_load()
        st["lastDir"] = target
        self._settings_save(st)
        return {"ok": True, "cwd": target}

    def ls(self, path=""):
        target = self.resolve(path)
        if os.path.isfile(target):
            return {"ok": True, "dir": os.path.dirname(target), "entries": [{"name": os.path.basename(target), "dir": False}]}
        try:
            names = sorted(os.listdir(target), key=str.lower)
        except OSError as e:
            return {"ok": False, "error": "cannot read %s (%s)" % (path or target, e.strerror or e)}
        out = []
        for n in names:
            if n.startswith("."):
                continue
            out.append({"name": n, "dir": os.path.isdir(os.path.join(target, n))})
        out.sort(key=lambda e: (not e["dir"], e["name"].lower()))
        return {"ok": True, "dir": target, "entries": out}

    def complete_path(self, text, exts=None, dirs_only=False):
        """Tab completion: what could `text` (a path as typed) continue with? Folders end with a separator."""
        text = str(text or "")
        head, base = os.path.split(text)
        if text.endswith(("/", os.sep)):
            head, base = text, ""
        folder = self.resolve(head) if head else self._get_cwd()
        try:
            names = sorted(os.listdir(folder), key=str.lower)
        except OSError:
            return []
        want = tuple(e.lower() for e in (exts or ()))
        out = []
        for n in names:
            if n.startswith(".") and not base.startswith("."):
                continue
            if not n.lower().startswith(base.lower()):
                continue
            full = os.path.join(folder, n)
            if os.path.isdir(full):
                out.append(head + ("" if (not head or head.endswith(("/", os.sep))) else os.sep) + n + os.sep)
            elif not dirs_only and (not want or n.lower().endswith(want)):
                out.append(head + ("" if (not head or head.endswith(("/", os.sep))) else os.sep) + n)
        return out

    # ---- file choosing (the native chooser)
    def _choose(self, types, folder=False):
        if self._dialogs is None:
            return None
        start = self._settings_load().get("lastDir") or os.path.expanduser("~")
        path = self._dialogs.open(types, folder=folder, directory=start if os.path.isdir(start) else "")
        if path:
            s = self._settings_load()
            s["lastDir"] = path if folder else os.path.dirname(path)
            self._settings_save(s)
        return path

    def _describe(self, path):
        """What the page needs to know about a chosen script (and which app id it already belongs to)."""
        base = os.path.splitext(os.path.basename(path))[0]
        rec = next((r for r in self._ids() if r["src"] and os.path.abspath(r["src"]) == path), None)
        return {"path": path, "file": os.path.basename(path), "name": rec["name"] if rec else base,
                "id": rec["id"] if rec else "", "version": rec["version"] if rec else "",
                "folder": os.path.dirname(path)}

    def pick_file(self):
        """Opens the file chooser for a Python script. Returns the description of the file, or None when cancelled."""
        path = self._choose(("Python files (*.py)", "All files (*.*)"))
        return self.use_path(path) if path else None

    def use_path(self, path):
        """The page got a path (typed, or dropped on the window): check it and describe it."""
        path = self.resolve(path) if path else path
        if not path or not os.path.isfile(path):
            return {"error": "that file does not exist"}
        if not path.lower().endswith(".py"):
            return {"error": "choose a Python file (.py)"}
        return self._describe(os.path.abspath(path))

    def pick_icon(self):
        path = self._choose(("Images (*.png;*.jpg;*.jpeg;*.webp)", "All files (*.*)"))
        if not path:
            return None
        if not path.lower().endswith(IMAGE_EXT):
            return {"error": "the icon must be a .png, .jpg or .webp picture"}
        return {"path": path}

    def pick_keystore(self):
        path = self._choose(("Keystores (*.jks;*.keystore;*.p12;*.pfx)", "All files (*.*)"))
        return {"path": path} if path else None

    def outdir(self, path=None):
        """Show (no argument) or change the folder builds are written to."""
        if path:
            d = self.resolve(path)
            if not os.path.isdir(d):
                return {"ok": False, "error": "no such folder: %s" % path}
            st = self._settings_load()
            st["outputDir"] = d
            self._settings_save(st)
        return {"ok": True, "dir": self._out_dir()}

    def pick_folder(self):
        path = self._choose((), folder=True)
        if not path:
            return None
        s = self._settings_load()
        s["outputDir"] = path
        self._settings_save(s)
        return {"path": path}

    # ---- checking
    def check(self, src):
        """Transpile only: the warnings, without building anything."""
        if not src or not os.path.isfile(src):
            return {"ok": False, "error": "choose a Python file first"}
        tmp = os.path.join(tk.WORK, "gui-check")
        import shutil
        shutil.rmtree(tmp, ignore_errors=True)
        os.makedirs(tmp, exist_ok=True)
        try:
            p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "transpile.py"), "gen", src, tmp, "--no-assets"],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True,
                               encoding="utf-8", errors="replace", timeout=120)
        except (OSError, subprocess.SubprocessError) as e:
            return {"ok": False, "error": str(e)}
        lines = [l for l in p.stdout.splitlines() if l.strip()]
        warnings = [l[len("warning:"):].strip() for l in lines if l.startswith("warning:")]
        notes = [l[len("note:"):].strip() for l in lines if l.startswith("note:")]
        if p.returncode != 0:
            msg = next((l for l in reversed(lines) if not l.startswith(("note:", "warning:"))), "the script could not be transpiled")
            return {"ok": False, "error": msg, "warnings": warnings, "notes": notes}
        return {"ok": True, "warnings": warnings, "notes": notes}

    # ---- building
    def build(self, opts):
        """Transpile and export: APK, or an Android App Bundle for Google Play."""
        o = opts or {}
        src = o.get("src") or ""
        if not os.path.isfile(src) or not src.lower().endswith(".py"):
            return {"ok": False, "error": "choose a Python file first"}
        ident = tk.safe_name(o.get("ident") or "") if o.get("ident") else ""
        args = ["build", src]
        name = (o.get("name") or "").strip()
        if name:
            args += ["-n", name]
        if ident:
            args += ["--id", ident]
            bump = o.get("bump") or "patch"
            if bump not in BUMPS:
                return {"ok": False, "error": "unknown version change"}
            args += ["--" + bump]
        icon = o.get("icon") or ""
        if icon:
            if not os.path.isfile(icon) or not icon.lower().endswith(IMAGE_EXT):
                return {"ok": False, "error": "the icon must be an existing .png, .jpg or .webp file"}
            args += ["--icon", icon]
        extra_env = {}
        aab = o.get("format") == "aab"
        if aab:
            args += ["--aab"]
            ks = o.get("keystore") or ""
            if ks:
                if not os.path.isfile(ks):
                    return {"ok": False, "error": "that keystore file does not exist"}
                if not o.get("ksPass"):
                    return {"ok": False, "error": "type the keystore password"}
                args += ["--keystore", ks]
                if o.get("ksAlias"):
                    args += ["--ks-alias", str(o["ksAlias"])]
                extra_env["POCKETTK_KS_PASS"] = str(o["ksPass"])
        sdk = str(o.get("targetSdk") or "")
        if sdk:
            if not sdk.isdigit():
                return {"ok": False, "error": "the target SDK must be a number"}
            args += ["--target-sdk", sdk]
        env = self._env(extra_env)
        return self._start("work", "build", lambda ctl: self._build_job(ctl, args, env, aab))

    def update(self, ident, bump="patch", src=None):
        """Rebuild an app that has an id (version goes up). Builds only: use install_on_phone afterwards."""
        ident = tk.safe_name(ident or "")
        rec = tk.read_id(ident) if ident else None
        if not rec:
            return {"ok": False, "error": "unknown app id"}
        src = src or rec.get("src") or ""
        if not os.path.isfile(src):
            return {"ok": False, "error": "the source file of '%s' is missing (%s): choose it again in the Build tab" % (ident, src)}
        if bump not in BUMPS:
            return {"ok": False, "error": "unknown version change"}
        args = ["build", src, "--id", ident, "--" + bump]
        env = self._env()
        return self._start("work", "build", lambda ctl: self._build_job(ctl, args, env, False))

    def _build_job(self, ctl, args, env, aab):
        state = {"path": "", "info": ""}

        def parse(line):
            sid = classify_line(line)
            if sid:
                ctl.step(sid, "running")
            m = re.match(r"^- built: (.+)$", line)
            if m:
                state["path"] = m.group(1).strip()
            m = re.match(r"^- app id '([^']*)' -> (\S+) \(version (.*)\)", line)
            if m:
                state["info"] = line[2:]
        code, tail = self._run_cli(ctl, args, env, parse)
        if code == 0 and state["path"]:
            self._emit("build", "done", ok=True, path=state["path"], aab=aab or state["path"].endswith(".aab"),
                       info=state["info"], pkg=self._package_of(state["path"]) if not aab else "")
        else:
            self._emit("build", "error", message=self._error_message(tail))

    @staticmethod
    def _package_of(apk):
        aapt2 = tk.find_tool("aapt2")
        if not aapt2 or not os.path.isfile(apk):
            return ""
        try:
            p = subprocess.run([aapt2, "dump", "packagename", apk], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                               universal_newlines=True, timeout=30)
            return p.stdout.strip().splitlines()[0] if p.returncode == 0 and p.stdout.strip() else ""
        except (OSError, subprocess.SubprocessError):
            return ""

    # ---- preview window (a phone-shaped window on this computer)
    def preview(self, src, width=None, height=None):
        if not src or not os.path.isfile(src):
            return {"ok": False, "error": "choose a Python file first"}
        args = ["preview", src]
        for flag, val in (("--width", width), ("--height", height)):
            if val:
                if not str(val).isdigit():
                    return {"ok": False, "error": "width and height must be numbers"}
                args += [flag, str(val)]
        env = self._env()

        def job(ctl):
            code, tail = self._run_cli(ctl, args, env, lambda line: None)
            if code != 0:
                self._emit("preview", "error", message=self._error_message(tail))
            else:
                self._emit("preview", "done", ok=True)
        return self._start("preview", "preview", job)

    # ---- the phone (adb)
    def devices(self):
        try:
            adb = tk.find_tool("adb")
            if not adb:
                return {"adb": False, "devices": []}
            return {"adb": True, "devices": tk.adb_devices()}
        except tk.Fail as e:
            return {"adb": False, "devices": [], "error": str(e)}

    def install_on_phone(self, apk, pkg=""):
        if not apk or not os.path.isfile(apk) or not apk.endswith(".apk"):
            return {"ok": False, "error": "there is no APK to install"}
        pkg = pkg or self._package_of(apk)
        if not pkg:
            return {"ok": False, "error": "cannot read the package name of this APK"}

        def job(ctl):
            sink = _LineSink(ctl)
            with contextlib.redirect_stdout(sink):
                tk.adb_install(apk, pkg)
            sink.flush()
            self._emit("install", "done", ok=True, pkg=pkg)
        return self._start("work", "install", job)

    # ---- setup: JDK, Android SDK, bundletool
    def doctor(self):
        items = []

        def add(i, label, ok, detail, optional=False, hint=""):
            items.append({"id": i, "label": label, "ok": bool(ok), "detail": detail, "optional": optional, "hint": hint})
        add("python", "Python", True, "%s (%s)" % (tk.python_exe(), platform.python_version()))
        for n in ("javac", "java", "keytool"):
            p = tk.find_tool(n)
            add(n, "Java: " + n, p, p or "not installed yet")
        for n in ("aapt2", "d8", "apksigner"):
            p = tk.find_tool(n) or tk.find_lib(n + ".jar")
            runs, hint = True, ""
            if p and n == "aapt2":
                try:
                    r = subprocess.run([p, "version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
                    runs = r.returncode == 0
                except (OSError, subprocess.SubprocessError):
                    runs = False
                if not runs and platform.system() == "Darwin" and platform.machine() == "arm64":
                    hint = "rosetta"
            add(n, "Android: " + n, p and runs, (p if runs else "found but it does not start (%s)" % p) if p else "not installed yet", hint=hint)
        aj = tk.find_android_jar()
        add("android.jar", "Android: android.jar", aj, aj or "not installed yet")
        a = tk.find_tool("adb")
        add("adb", "adb (to install on a phone)", a, a or "optional", optional=True)
        bt = tk.find_bundletool()
        add("bundletool", "bundletool (Google Play bundles)", bt, bt or "optional", optional=True)
        add("jarsigner", "jarsigner (Google Play bundles)", tk.find_tool("jarsigner"), tk.find_tool("jarsigner") or "comes with the JDK", optional=True)
        ready = all(i["ok"] for i in items if not i["optional"])
        return {"items": items, "ready": ready, "dataDir": tk.KS, "outputDir": self._out_dir()}

    def start_setup(self):
        s = self._settings_load()
        s["autoSetupDone"] = True
        self._settings_save(s)

        def job(ctl):
            deps.run_setup(tk, ctl)
            self._emit("setup", "done", ok=True)
        return self._start("work", "setup", job)

    def answer_license(self, accept):
        j = self._jobs.get("work")
        if j and j[1].kind == "setup":
            j[1].answer_license(bool(accept))
            return {"ok": True}
        return {"ok": False}

    def install_rosetta(self):
        """Apple Silicon only: Google's tools may need Rosetta. Asks for the administrator password in a normal Mac dialog."""
        if platform.system() != "Darwin":
            return {"ok": False, "error": "only needed on a Mac"}

        def job(ctl):
            ctl.log("asking macOS to install Rosetta")
            script = 'do shell script "/usr/sbin/softwareupdate --install-rosetta --agree-to-license" with administrator privileges'
            code, out = deps.run_stream(["osascript", "-e", script], ctl)
            if code != 0:
                raise deps.DepsError("Rosetta was not installed (%s)" % (out.strip().splitlines()[-1] if out.strip() else "cancelled"))
            self._emit("rosetta", "done", ok=True)
        return self._start("work", "rosetta", job)

    # ---- apps, files, misc
    def apps(self):
        return self._ids()

    def reveal(self, path):
        if not path or not os.path.exists(path):
            return {"ok": False, "error": "that file is gone"}
        try:
            if sys.platform == "darwin":
                subprocess.Popen(["open", "-R", path])
            elif os.name == "nt":
                subprocess.Popen(["explorer", "/select,", os.path.normpath(path)])
            else:
                subprocess.Popen(["xdg-open", os.path.dirname(path)])
        except OSError as e:
            return {"ok": False, "error": str(e)}
        return {"ok": True}

    def open_data_folder(self):
        os.makedirs(tk.KS, exist_ok=True)
        return self.reveal(tk.KS)

    # ---- the event stream the page polls
    def poll(self, since=0):
        with self._lock:
            start = max(int(since or 0) - self._base, 0)
            evs = self._events[start:]
            nxt = self._base + len(self._events)
        return {"events": evs, "next": nxt, "busy": {k: self._busy(k) for k in ("work", "preview")},
                "workKind": self._jobs["work"][1].kind if "work" in self._jobs and self._busy("work") else ""}

    def cancel(self):
        for slot in ("work", "preview"):
            j = self._jobs.get(slot)
            if j and j[0].is_alive():
                j[1].cancel()
        return {"ok": True}

    def quit(self):
        self.cancel()
        if self._window is not None:
            self._window.destroy()
        return {"ok": True}

    def _shutdown(self):
        self.cancel()


# ---------------------------------------------------------------- launcher
def page_html():
    """index.html with core.js, shell.js and term.js written into it (a window loaded from a string needs no file server)."""
    def read(n):
        with open(os.path.join(WEBUI, n), encoding="utf-8") as f:
            return f.read()
    html = read("index.html")
    for key, name in (("CORE", "core.js"), ("SHELL", "shell.js"), ("TERM", "term.js")):
        html = html.replace("/*@%s@*/" % key, read(name))
    return html


def selftest():
    """`POCKETTK_GUI_SELFTEST=1`: check that this install is complete (used by the Mac app's build, no window opens)."""
    problems = []
    for f in ("pockettk.py", "transpile.py", "pockettk_deps.py", "webui/index.html", "webui/core.js", "webui/shell.js", "webui/term.js"):
        if not os.path.isfile(os.path.join(HERE, f)):
            problems.append("missing " + f)
    for d in ("rt", "preview"):
        if not os.path.isdir(os.path.join(HERE, d)):
            problems.append("missing " + d + "/")
    try:
        import webview            # noqa: F401
    except ImportError as e:
        problems.append("pywebview: %s" % e)
    try:
        import ssl
        deps.ssl_context()
        ssl.create_default_context()
    except Exception as e:
        problems.append("ssl: %s" % e)
    try:
        html = page_html()
        if "/*@" in html:
            problems.append("web page not assembled")
    except OSError as e:
        problems.append("web page: %s" % e)
    print(tk.VERSION, "| python", platform.python_version(), "|", sys.executable)
    for p in problems:
        print("PROBLEM:", p)
    print("selftest:", "FAILED" if problems else "ok")
    return 1 if problems else 0


def _ensure_streams():
    """pythonw.exe (the Windows app) has no stdout/stderr: point them at ~/.pockettk/gui.log so print() cannot crash."""
    if sys.stdout is not None and sys.stderr is not None:
        return
    try:
        d = os.path.join(os.path.expanduser("~"), ".pockettk")
        os.makedirs(d, exist_ok=True)
        f = open(os.path.join(d, "gui.log"), "a", encoding="utf-8", buffering=1)
    except OSError:
        f = open(os.devnull, "w")
    if sys.stdout is None:
        sys.stdout = f
    if sys.stderr is None:
        sys.stderr = f


def _no_webview_help():
    if os.name == "nt":
        return "Install it with:   py -m pip install pywebview   (it also needs Microsoft Edge WebView2, which Windows 11 has)"
    if sys.platform == "darwin":
        return "Install it with:   %s -m pip install pywebview" % (sys.executable or "python3")
    return ("Install it with:   %s -m pip install pywebview[qt]   (or: sudo apt install python3-gi gir1.2-webkit2-4.1)"
            % (sys.executable or "python3"))


def _smoke(window, hold):
    """`POCKETTK_GUI_SMOKE=1` (used by the GitHub test workflow): once the window is open, check that the page really loaded and
    the Python side answers, keep the window open `hold` seconds (so a screenshot can be taken), print `smoke: ok` or
    `smoke: FAILED (why)`, close the window. main() then returns 0 or 1."""
    global _SMOKE_RESULT
    why = "the page did not load in 60 seconds"
    deadline = time.time() + 60
    while time.time() < deadline:
        try:
            if window.evaluate_js("document.readyState") == "complete":
                have = window.evaluate_js("document.getElementById('screen') !== null && document.title")
                if have == "PocketTk":
                    why = ""
                    break
                why = "page loaded but the screen element or title is missing (%r)" % (have,)
        except Exception as e:                       # the window is still starting
            why = "evaluate_js: %s" % e
        time.sleep(0.5)
    if not why:
        try:
            if not callable(getattr(window, "evaluate_js", None)):
                why = "no evaluate_js"
            else:
                res = window.evaluate_js("typeof window.pywebview !== 'undefined' && typeof window.pywebview.api !== 'undefined'")
                if res is not True:
                    why = "the Python bridge (pywebview.api) is not available in the page"
        except Exception as e:
            why = "bridge check: %s" % e
    if not why:
        time.sleep(max(0.0, hold))
    _SMOKE_RESULT = why
    print("smoke: %s" % ("ok (%s, python %s)" % (sys.platform, platform.python_version()) if not why else "FAILED (%s)" % why), flush=True)
    try:
        window.destroy()
    except Exception:
        pass


_SMOKE_RESULT = None


def main(script=None, pick=False):
    _ensure_streams()
    if os.environ.get("POCKETTK_GUI_SELFTEST"):
        return selftest()
    try:
        import webview
    except ImportError:
        sys.stderr.write("The PocketTk window needs pywebview.  %s\n"
                         "(the PocketTk app for your system has it built in)\n" % _no_webview_help())
        return 1
    if os.environ.get("POCKETTK_GUI_PICK"):
        pick = True
    api = Api(script, pick)
    window = webview.create_window("PocketTk", html=page_html(), js_api=api, width=980, height=660, min_size=(640, 420),
                                   background_color="#1b2140")
    api._attach(window)
    smoke = os.environ.get("POCKETTK_GUI_SMOKE")
    try:
        if smoke:
            try:
                hold = float(os.environ.get("POCKETTK_GUI_SMOKE_HOLD") or "0")
            except ValueError:
                hold = 0.0
            webview.start(_smoke, (window, hold), debug=bool(os.environ.get("POCKETTK_GUI_DEBUG")))
        else:
            webview.start(debug=bool(os.environ.get("POCKETTK_GUI_DEBUG")))
    finally:
        api._shutdown()
    if smoke:
        if _SMOKE_RESULT is None:
            print("smoke: FAILED (the window closed before the check ran)", flush=True)
            return 1
        return 1 if _SMOKE_RESULT else 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else None, "--pick" in sys.argv))
