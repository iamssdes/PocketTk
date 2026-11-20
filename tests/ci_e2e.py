#!/usr/bin/env python3
"""The desktop first-run path, for real (used by the GitHub "Desktop test" workflow; needs internet, takes a few minutes):

    1. install the JDK, the Android SDK tools and bundletool exactly as the PocketTk window does on its first run
    2. `doctor`, `check`, and build a real APK and a Google Play bundle from a small tkinter script
    3. look inside what came out (manifest, classes.dex, signature)
    4. compile the PC preview runtime

    python tests/ci_e2e.py [OUTPUT_FOLDER]       exit code 0 only when every step passed (skipped steps are shown, not failures)
A table is printed and, on GitHub, added to the run's summary page."""
import os, sys, time, zipfile, tempfile, traceback, subprocess

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
OUT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.join(HERE, "ci-out")
os.makedirs(OUT, exist_ok=True)
os.environ.setdefault("POCKETTK_HOME", tempfile.mkdtemp(prefix="pockettk-ci-"))
import pockettk as tk            # noqa: E402
import pockettk_deps as deps     # noqa: E402

SAMPLE = '''import tkinter as tk
from tkinter import ttk

root = tk.Tk()
root.title("CI sample")
count = tk.IntVar(value=0)

def bump():
    count.set(count.get() + 1)
    label.config(text="Taps: " + str(count.get()))

nb = ttk.Notebook(root)
page = tk.Frame(nb)
label = tk.Label(page, text="Taps: 0", font=("Arial", 20))
label.pack(pady=20)
tk.Button(page, text="Tap me", command=bump).pack()
entry = tk.Entry(page)
entry.pack(pady=10)
nb.add(page, text="Main")
nb.add(tk.Frame(nb), text="Other")
nb.pack(fill="both", expand=True)
root.mainloop()
'''


class Ctl:
    """Answers the window's questions without a window: accepts Google's license, never cancels."""
    def log(self, text): print("   " + str(text), flush=True)
    def status(self, text): print("   . " + str(text), flush=True) if text else None
    def step(self, sid, state): print("   step %s: %s" % (sid, state), flush=True)
    def progress(self, done, total, label=""): pass
    def check(self): pass
    def set_proc(self, p): pass
    def license(self, text):
        print("   (accepting Google's Android SDK license, as the window does after you press Accept)", flush=True)
        return True


rows = []        # (name, "ok"|"FAILED"|"skipped", seconds, detail)


def step(name):
    def deco(fn):
        t0 = time.time()
        print("\n== " + name, flush=True)
        try:
            res = fn()
            state, detail = ("skipped", res[1]) if isinstance(res, tuple) and res[0] == "skip" else ("ok", res or "")
        except Exception as e:
            traceback.print_exc()
            state, detail = "FAILED", "%s: %s" % (type(e).__name__, e)
        rows.append((name, state, time.time() - t0, str(detail)))
        print("-- %s: %s %s" % (name, state, detail), flush=True)
        return fn
    return deco


def cli(*args, expect=0):
    p = subprocess.run([sys.executable, os.path.join(HERE, "pockettk.py")] + list(args), capture_output=True, text=True,
                       encoding="utf-8", errors="replace", cwd=OUT)
    print(p.stdout[-3000:] + p.stderr[-3000:], flush=True)
    if p.returncode != expect:
        raise RuntimeError("pockettk %s exited %d (expected %d)" % (" ".join(args), p.returncode, expect))
    return p.stdout + p.stderr


script = os.path.join(OUT, "sample.py")
with open(script, "w", encoding="utf-8") as f:
    f.write(SAMPLE)


@step("first-run setup (JDK, Android SDK, bundletool)")
def _setup():
    st = deps.run_setup(tk, Ctl())
    return "jdk=%s packages=%s bundletool=%s" % (st["jdk"], st["packages"], st["bundletool"])


@step("pockettk version")
def _version():
    return cli("version").strip()


@step("pockettk doctor")
def _doctor():
    cli("doctor")


@step("pockettk check")
def _check():
    cli("check", script)


apk = os.path.join(OUT, "ci_sample.apk")


@step("build an APK")
def _build():
    cli("build", script, "--id", "ci_sample", "-o", apk)
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
    need = {"AndroidManifest.xml", "classes.dex", "resources.arsc"}
    if not need <= names:
        raise RuntimeError("the APK lacks %s" % sorted(need - names))
    return "%d KB, has %s" % (os.path.getsize(apk) // 1024, ", ".join(sorted(need)))


@step("the APK is signed")
def _signed():
    exe = tk.find_tool("apksigner")
    if not exe:
        return ("skip", "apksigner not found")
    cmd = [exe, "verify", apk] if os.name != "nt" else ["cmd", "/c", exe, "verify", apk]
    p = subprocess.run(cmd, capture_output=True, text=True)
    print(p.stdout + p.stderr, flush=True)
    if p.returncode != 0:
        raise RuntimeError("apksigner verify failed")
    return "apksigner verify ok"


aab = os.path.join(OUT, "ci_sample.aab")


@step("build a Google Play bundle (.aab)")
def _aab():
    if not tk.find_bundletool():
        return ("skip", "bundletool is not installed")
    cli("build", script, "--aab", "--id", "ci_sample_aab", "-o", aab)
    with zipfile.ZipFile(aab) as z:
        names = set(z.namelist())
    if "BundleConfig.pb" not in names:
        raise RuntimeError("the .aab has no BundleConfig.pb")
    return "%d KB" % (os.path.getsize(aab) // 1024)


@step("the PC preview runtime compiles")
def _preview():
    import pockettk_preview as pv
    rt = pv.ensure_runtime(tk)
    if not os.path.isdir(rt):
        raise RuntimeError("no runtime folder at %s" % rt)
    return "ok"


# ---- the table
width = max(len(r[0]) for r in rows)
lines = ["| step | result | time | detail |", "|---|---|---|---|"]
for name, state, secs, detail in rows:
    mark = {"ok": "ok", "FAILED": "**FAILED**", "skipped": "skipped"}[state]
    lines.append("| %s | %s | %ds | %s |" % (name, mark, secs, detail.replace("|", "/").replace("\n", " ")[:160]))
table = "\n".join(lines)
print("\n" + table, flush=True)
summ = os.environ.get("GITHUB_STEP_SUMMARY")
if summ:
    with open(summ, "a", encoding="utf-8") as f:
        f.write("### Desktop end-to-end (%s, Python %s)\n\n%s\n" % (sys.platform, sys.version.split()[0], table))
sys.exit(1 if any(r[1] == "FAILED" for r in rows) else 0)
