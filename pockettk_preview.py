"""PC preview: runs the app's real Java code on the desktop JVM inside a phone-shaped window.

The window needs only a JDK (javac + java). It does not need the Android SDK, adb or a phone.
Called by pockettk.py:  pockettk run app.py --preview   /   pockettk preview app.py
"""
import os, sys, glob, shutil, subprocess, zlib, tempfile

PREVIEW_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")


def _sources():
    out = []
    for root in (os.path.join(PREVIEW_DIR, "shim"), os.path.join(PREVIEW_DIR, "host"), os.path.join(os.path.dirname(PREVIEW_DIR), "rt")):
        for r, _, fs in os.walk(root):
            out += [os.path.join(r, f) for f in fs if f.endswith(".java")]
    return sorted(out)


def ensure_runtime(tk):
    javac = tk.find_tool("javac") or tk.die("javac not found: install a JDK 17+ (the preview needs only Java)")
    srcs = _sources()
    crc = 0
    for s in srcs:
        with open(s, "rb") as f:
            crc = zlib.crc32(f.read(), crc)
    base = os.path.join(tk.KS, "preview-rt")
    d = os.path.join(base, "%08x" % crc)
    if os.path.isdir(os.path.join(d, "pockettk")):
        return d
    tk.say("preparing the preview (one time)")
    os.makedirs(base, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix="tmp.", dir=base)
    lst = os.path.join(tmp, "files.txt")
    with open(lst, "w", encoding="utf-8") as f:
        for s in srcs:
            f.write('"%s"\n' % s.replace("\\", "/"))
    cls = os.path.join(tmp, "classes")
    os.makedirs(cls)
    tk.run([javac, "-nowarn", "-Xlint:none", "--release", "8", "-encoding", "UTF-8", "-d", cls, "@" + lst], log=os.path.join(tmp, "javac.log"))
    shutil.rmtree(d, ignore_errors=True)
    os.rename(cls, d)
    shutil.rmtree(tmp, ignore_errors=True)
    for old in glob.glob(os.path.join(base, "*")):
        if os.path.abspath(old) != os.path.abspath(d) and not os.path.basename(old).startswith("tmp."):
            shutil.rmtree(old, ignore_errors=True)
    return d


def build_app(tk, src, name, pkg):
    """transpile + compile only the app's own generated code (the runtime and the Android layer are already compiled)."""
    base = os.path.splitext(os.path.basename(src))[0]
    sn = tk.safe_name(base) or "app"
    bd = os.path.join(tk.WORK, "preview", sn)
    shutil.rmtree(bd, ignore_errors=True)
    os.makedirs(os.path.join(bd, "classes"))
    tk.say("transpiling %s" % src)
    p = tk.run([tk.python_exe(), "-I", os.path.join(tk.HERE, "transpile.py"), "gen", src, bd, "--pkg", pkg, "--label", name or base,
                "--audio-cache", os.path.join(tk.KS, "audio-cache"), "--apple", tk.VERSION.replace("PocketTk v", "")], check=False)
    if p.returncode != 0:
        sys.stdout.write(p.stdout)
        tk.die("transpile failed")
    for line in p.stdout.splitlines():
        if line.startswith(("note:", "audio:")):
            print(line)
    return bd


def run_preview(tk, src, width, height, dpi, cpu, name, keep):
    if not os.path.isfile(src):
        tk.die("no such file: %s" % src)
    java = tk.find_tool("java") or tk.die("java not found: install a JDK 17+")
    javac = tk.find_tool("javac") or tk.die("javac not found: install a JDK 17+")
    rt = ensure_runtime(tk)
    base = os.path.splitext(os.path.basename(src))[0]
    sn = tk.safe_name(base) or "app"
    pkg = tk.PKG_PREFIX + sn
    settings = os.path.join(tk.KS, "preview.properties")
    os.makedirs(tk.KS, exist_ok=True)
    while True:
        bd = build_app(tk, src, name, pkg)
        srcs = []
        for r, _, fs in os.walk(os.path.join(bd, "src")):
            srcs += [os.path.join(r, f) for f in fs if f.endswith(".java")]
        tk.say("compiling Java")
        lst = os.path.join(bd, "files.txt")
        with open(lst, "w", encoding="utf-8") as f:
            for s in srcs:
                f.write('"%s"\n' % s.replace("\\", "/"))
        tk.run([javac, "-nowarn", "-Xlint:none", "--release", "8", "-encoding", "UTF-8", "-cp", rt, "-d", os.path.join(bd, "classes"), "@" + lst],
               log=os.path.join(bd, "javac.log"))
        while True:
            cmd = [java] + (["-Xdock:name=PocketTk", "-Dapple.awt.application.name=PocketTk"] if sys.platform == "darwin" else []) + ["-Dpockettk.package=" + pkg, "-Dpockettk.assets=" + os.path.join(bd, "assets"), "-Dpockettk.title=" + (name or base), "-Dpockettk.versionName=1.0.0",
                   "-cp", os.pathsep.join([os.path.join(bd, "classes"), rt]), "pockettk.preview.PhoneHost",
                   "--settings", settings, "--title", name or base]
            for k, v in (("width", width), ("height", height), ("dpi", dpi), ("cpu", cpu)):
                if v is not None:
                    cmd += ["--" + k, str(v)]
            extra = os.environ.get("POCKETTK_PREVIEW_ARGS")
            if extra:
                cmd += extra.split("\x1f") if "\x1f" in extra else extra.split(" ", 1)
            tk.say("preview window open (close it to stop)")
            code = subprocess.call(cmd)
            width = height = dpi = cpu = None            # after the first start the window's own saved settings rule
            if code in (75, 77):
                continue
            break
        if code == 76:
            continue                                     # "Reload code": transpile and compile again
        if not keep:
            shutil.rmtree(bd, ignore_errors=True)
        return code
