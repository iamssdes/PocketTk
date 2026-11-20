#!/usr/bin/env python3
"""Installs what PocketTk needs, with no terminal: a JDK, the Android SDK tools and bundletool.

Used by the PocketTk window (pockettk_gui.py) on its first run. Standard library only.

Everything lands in ~/.pockettk (jdk/ and sdk/), nothing needs an administrator password, and nothing outside that
folder is touched. Downloads come from Adoptium (the JDK, Eclipse Temurin) and from Google (the Android SDK, whose
license the user has to accept in the window first) and GitHub (bundletool).

The functions report to a "ctl" object, so the window (or a test) decides how to show it:
    ctl.log(text)  ctl.status(text)  ctl.step(id, state)  ctl.progress(done, total, label)
    ctl.check()                 raise Cancelled when the user pressed Cancel
    ctl.set_proc(popen_or_None) so Cancel can stop a running child process
    ctl.license(text) -> bool   show Google's SDK license and wait for the answer
"""
import os, re, sys, json, ssl, time, shutil, hashlib, tarfile, zipfile, platform, subprocess, glob
import urllib.request
import xml.etree.ElementTree as ET

WIN = os.name == "nt"
EXE = ".exe" if WIN else ""
BAT = ".bat" if WIN else ""
JDK_FEATURE = 21
SDK_PACKAGES = ["build-tools;34.0.0", "platforms;android-34", "platform-tools"]


class DepsError(Exception):
    pass


class Cancelled(Exception):
    pass


# ---------------------------------------------------------------- downloading
_CTX = None


def ssl_context():
    """A bundled Python often has no CA list of its own: use certifi when it is there, else the system's file."""
    global _CTX
    if _CTX is not None:
        return _CTX
    try:
        import certifi
        _CTX = ssl.create_default_context(cafile=certifi.where())
        return _CTX
    except Exception:
        pass
    _CTX = ssl.create_default_context()
    try:
        have = bool(_CTX.get_ca_certs())
    except Exception:
        have = False
    if not have:
        for p in ("/etc/ssl/cert.pem", "/private/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt"):
            if os.path.isfile(p):
                _CTX = ssl.create_default_context(cafile=p)
                break
    return _CTX


def _open(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "PocketTk"})
    return urllib.request.urlopen(req, timeout=timeout, context=ssl_context())


def fetch_json(url):
    try:
        with _open(url) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception as e:
        raise DepsError("could not reach %s (%s)" % (url, e))


def download(url, dest, ctl, sha256=None, label=""):
    """Streams url to dest (through dest.part), checks the SHA-256 when one is given."""
    tmp = dest + ".part"
    h = hashlib.sha256()
    try:
        with _open(url) as r, open(tmp, "wb") as f:
            total = int(r.headers.get("Content-Length") or 0)
            done, last = 0, 0.0
            while True:
                ctl.check()
                b = r.read(1 << 16)
                if not b:
                    break
                f.write(b)
                h.update(b)
                done += len(b)
                if time.time() - last > 0.2:
                    ctl.progress(done, total, label)
                    last = time.time()
            ctl.progress(done, total, label)
    except (Cancelled, DepsError):
        _rm(tmp)
        raise
    except Exception as e:
        _rm(tmp)
        raise DepsError("download failed: %s (%s)" % (url, e))
    if sha256 and h.hexdigest().lower() != sha256.strip().lower():
        _rm(tmp)
        raise DepsError("the downloaded file is damaged (checksum mismatch): %s" % url)
    os.replace(tmp, dest)
    return dest


def _rm(p):
    try:
        os.remove(p)
    except OSError:
        pass


# ---------------------------------------------------------------- unpacking (refuses paths that leave the folder)
def _inside(root, target):
    return target == root or target.startswith(root + os.sep)


def extract(archive, dest):
    os.makedirs(dest, exist_ok=True)
    root = os.path.realpath(dest)
    if archive.lower().endswith(".zip"):
        with zipfile.ZipFile(archive) as z:
            infos = z.infolist()
            for m in infos:
                if not _inside(root, os.path.realpath(os.path.join(dest, m.filename))):
                    raise DepsError("unsafe path inside the archive: " + m.filename)
            z.extractall(dest)
            for m in infos:                                   # zipfile drops the executable bit: put it back
                mode = (m.external_attr >> 16) & 0o777
                t = os.path.join(dest, m.filename)
                if mode and not m.is_dir() and os.path.isfile(t):
                    os.chmod(t, mode | 0o600)
    else:
        with tarfile.open(archive) as t:
            for m in t.getmembers():
                target = os.path.realpath(os.path.join(dest, m.name))
                if not _inside(root, target):
                    raise DepsError("unsafe path inside the archive: " + m.name)
                if m.issym() and not _inside(root, os.path.realpath(os.path.join(os.path.dirname(target), m.linkname))):
                    raise DepsError("unsafe link inside the archive: " + m.name)
                if m.islnk() and not _inside(root, os.path.realpath(os.path.join(dest, m.linkname))):
                    raise DepsError("unsafe link inside the archive: " + m.name)
            try:
                t.extractall(dest, filter="fully_trusted")    # checked above; the keyword needs a recent Python
            except TypeError:
                t.extractall(dest)


# ---------------------------------------------------------------- child processes
def _group_kwargs():
    if WIN:      # no console window flashing up for every child (the Windows app runs without a console)
        return {"creationflags": getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "CREATE_NO_WINDOW", 0)}
    return {"start_new_session": True}


_PROGRESS = re.compile(r"^\s*\[[=\- ]*\]\s*\d+%")


def run_stream(cmd, ctl, env=None, input_text=None, cwd=None):
    """Runs cmd, shows its output live ("[====  ] 40% ..." lines go to the status line), returns (code, text)."""
    cmd = [str(c) for c in cmd]
    if WIN and cmd[0].lower().endswith((".bat", ".cmd")):
        cmd = ["cmd", "/c"] + cmd
    try:
        p = subprocess.Popen(cmd, stdin=subprocess.PIPE if input_text is not None else subprocess.DEVNULL,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, cwd=cwd, **_group_kwargs())
    except OSError as e:
        raise DepsError("could not run %s (%s)" % (cmd[0], e))
    ctl.set_proc(p)
    if input_text is not None:
        try:
            p.stdin.write(input_text.encode("utf-8"))
            p.stdin.close()
        except (BrokenPipeError, OSError):
            pass
    out, buf = [], b""
    try:
        while True:
            chunk = p.stdout.read1(4096)
            if not chunk:
                break
            buf += chunk
            parts = re.split(rb"[\r\n]+", buf)
            buf = parts.pop()
            for part in parts:
                line = part.decode("utf-8", "replace").rstrip()
                if not line:
                    continue
                out.append(line)
                if _PROGRESS.match(line):
                    ctl.status(line.strip())
                else:
                    ctl.log(line)
        if buf.strip():
            line = buf.decode("utf-8", "replace").rstrip()
            out.append(line)
            ctl.log(line)
        p.wait()
    finally:
        ctl.set_proc(None)
        try:
            p.stdout.close()
        except OSError:
            pass
    ctl.check()
    return p.returncode, "\n".join(out)


# ---------------------------------------------------------------- the JDK (Eclipse Temurin from Adoptium)
def jdk_target():
    """('mac'|'windows'|'linux', 'aarch64'|'x64') for this computer."""
    system = {"Darwin": "mac", "Windows": "windows", "Linux": "linux"}.get(platform.system())
    m = platform.machine().lower()
    arch = "aarch64" if m in ("arm64", "aarch64") else "x64" if m in ("x86_64", "amd64") else None
    if not system or not arch:
        raise DepsError("no JDK download is known for %s %s: install a JDK 17 or newer yourself" % (platform.system(), platform.machine()))
    return system, arch


def adoptium_asset(feature=JDK_FEATURE, target=None):
    """The newest Temurin JDK for this computer: {link, name, checksum, size}."""
    system, arch = target or jdk_target()
    url = ("https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&image_type=jdk&os=%s&vendor=eclipse"
           % (feature, arch, system))
    for item in fetch_json(url) or []:
        pkg = (item.get("binary") or {}).get("package") or {}
        if pkg.get("link") and pkg.get("name"):
            return {"link": pkg["link"], "name": pkg["name"], "checksum": pkg.get("checksum"), "size": pkg.get("size", 0)}
    raise DepsError("Adoptium lists no JDK %d for %s/%s" % (feature, system, arch))


def jdk_home_in(d):
    """The folder that holds bin/javac inside an unpacked JDK (a Mac keeps it under Contents/Home)."""
    for cand in (os.path.join(d, "Contents", "Home"), d):
        if os.path.isfile(os.path.join(cand, "bin", "javac" + EXE)):
            return cand
    return ""


def install_jdk(base, ctl, asset=None):
    """Downloads and unpacks a JDK into base/<name>; returns its home."""
    os.makedirs(base, exist_ok=True)
    asset = asset or adoptium_asset()
    ctl.log("downloading %s" % asset["name"])
    arc = os.path.join(base, asset["name"])
    download(asset["link"], arc, ctl, asset.get("checksum"), "Java JDK")
    tmp = os.path.join(base, "_unpack")
    shutil.rmtree(tmp, ignore_errors=True)
    ctl.status("unpacking the JDK")
    try:
        extract(arc, tmp)
    finally:
        _rm(arc)
    tops = [d for d in sorted(os.listdir(tmp)) if os.path.isdir(os.path.join(tmp, d))]
    home = ""
    for top in tops:
        if jdk_home_in(os.path.join(tmp, top)):
            final = os.path.join(base, top)
            shutil.rmtree(final, ignore_errors=True)
            shutil.move(os.path.join(tmp, top), final)
            home = jdk_home_in(final)
            break
    shutil.rmtree(tmp, ignore_errors=True)
    if not home:
        raise DepsError("the JDK archive did not contain bin/javac")
    ctl.log("JDK installed: %s" % home)
    return home


# ---------------------------------------------------------------- the Android SDK (Google)
def sdkmanager_path(sdk):
    return os.path.join(sdk, "cmdline-tools", "latest", "bin", "sdkmanager" + BAT)


def cmdline_tools_url():
    try:
        with _open("https://dl.google.com/android/repository/repository2-3.xml") as r:
            xml = r.read()
    except Exception as e:
        raise DepsError("could not reach dl.google.com (%s)" % e)
    osn = {"Windows": "windows", "Darwin": "macosx"}.get(platform.system(), "linux")
    best = None
    for rp in ET.fromstring(xml).iter("remotePackage"):
        if rp.get("path") == "cmdline-tools;latest":
            for ar in rp.iter("archive"):
                if ar.findtext("host-os") == osn:
                    best = ar.findtext("complete/url")
    if not best:
        raise DepsError("Google lists no Android command-line tools for this system")
    return "https://dl.google.com/android/repository/" + best


def install_cmdline_tools(sdk, ctl, url=None):
    os.makedirs(sdk, exist_ok=True)
    url = url or cmdline_tools_url()
    z = os.path.join(sdk, "cmdline.zip")
    ctl.log("downloading " + url.rsplit("/", 1)[-1])
    download(url, z, ctl, None, "Android command-line tools")
    tmp = os.path.join(sdk, "_x")
    shutil.rmtree(tmp, ignore_errors=True)
    try:
        extract(z, tmp)
    finally:
        _rm(z)
    os.makedirs(os.path.join(sdk, "cmdline-tools"), exist_ok=True)
    shutil.rmtree(os.path.join(sdk, "cmdline-tools", "latest"), ignore_errors=True)
    shutil.move(os.path.join(tmp, "cmdline-tools"), os.path.join(sdk, "cmdline-tools", "latest"))
    shutil.rmtree(tmp, ignore_errors=True)
    if not WIN:
        for f in glob.glob(os.path.join(sdk, "cmdline-tools", "latest", "bin", "*")):
            os.chmod(f, 0o755)


def sdk_env(java_home):
    env = dict(os.environ)
    if java_home:
        env["JAVA_HOME"] = java_home
        env["PATH"] = os.path.join(java_home, "bin") + os.pathsep + env.get("PATH", "")
    return env


def license_text(sdk, env, ctl):
    """Google's SDK license(s) that still need accepting, or "" when they are all accepted already."""
    code, out = run_stream([sdkmanager_path(sdk), "--sdk_root=" + sdk, "--licenses"], ctl, env=env,
                           input_text="y\n" + "n\n" * 30)
    if "All SDK package licenses accepted" in out and "Accept?" not in out:
        return ""
    keep = [l for l in out.splitlines()
            if not l.startswith(("Warning:", "Review licenses", "Accept?", "[=", "Loading", "Fetch")) and "Unable to read" not in l]
    text = "\n".join(keep).strip()
    return text or ("The Android SDK license could not be shown here. Read it at "
                    "https://developer.android.com/studio/terms before you accept.")


def accept_licenses(sdk, env, ctl):
    code, out = run_stream([sdkmanager_path(sdk), "--sdk_root=" + sdk, "--licenses"], ctl, env=env, input_text="y\n" * 60)
    if code != 0:
        raise DepsError("could not record the license answer (sdkmanager exit %d)" % code)


def install_packages(sdk, env, ctl, packages=None):
    code, out = run_stream([sdkmanager_path(sdk), "--sdk_root=" + sdk] + list(packages or SDK_PACKAGES), ctl, env=env)
    if code != 0:
        raise DepsError("the SDK download failed (sdkmanager exit %d)" % code)


# ---------------------------------------------------------------- bundletool (only for Google Play bundles)
def install_bundletool(dest_dir, ctl):
    rel = fetch_json("https://api.github.com/repos/google/bundletool/releases/latest")
    asset = next((x for x in rel.get("assets", []) if re.match(r"bundletool-all-.*\.jar$", x.get("name", ""))), None)
    if not asset:
        raise DepsError("no bundletool-all jar in the latest bundletool release")
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "bundletool.jar")
    download(asset["browser_download_url"], dest, ctl, None, "bundletool")
    return dest


# ---------------------------------------------------------------- what is missing, and the whole setup
def status(tk):
    """{jdk, sdk_tools, packages, bundletool}: True when that part is present. tk is the pockettk module."""
    jdk = bool(tk.find_tool("javac") and tk.find_tool("java") and tk.find_tool("keytool"))
    packages = bool(tk.find_tool("aapt2") and (tk.find_tool("d8") or tk.find_lib("d8.jar"))
                    and (tk.find_tool("apksigner") or tk.find_lib("apksigner.jar")) and tk.find_android_jar())
    sdk = sdk_root(tk)
    return {"jdk": jdk, "packages": packages, "sdk_tools": os.path.isfile(sdkmanager_path(sdk)),
            "bundletool": bool(tk.find_bundletool())}


def sdk_root(tk):
    return os.environ.get("POCKETTK_SDK") or os.path.join(tk.KS, "sdk")


def needs_setup(tk):
    st = status(tk)
    return not (st["jdk"] and st["packages"])


def run_setup(tk, ctl):
    """Installs whatever is missing, in this order: JDK, SDK command-line tools, (license), SDK packages, bundletool."""
    st = status(tk)
    ctl.step("jdk", "done" if st["jdk"] else "running")
    if not st["jdk"]:
        install_jdk(os.path.join(tk.KS, "jdk"), ctl)
        ctl.step("jdk", "done")
    java_home = tk.export_java_home()
    if not java_home:
        raise DepsError("the JDK was installed but PocketTk cannot find it")

    sdk = sdk_root(tk)
    if st["packages"]:
        ctl.step("sdk", "done")
    else:
        ctl.step("sdk", "running")
        if not os.path.isfile(sdkmanager_path(sdk)):
            install_cmdline_tools(sdk, ctl)
        env = sdk_env(java_home)
        ctl.status("checking the Android SDK license")
        text = license_text(sdk, env, ctl)
        if text:
            if not ctl.license(text):
                raise Cancelled()
            ctl.status("recording your answer")
            accept_licenses(sdk, env, ctl)
        ctl.status("")
        ctl.log("downloading the Android build tools (this takes a few minutes)")
        install_packages(sdk, env, ctl)
        ctl.step("sdk", "done")

    ctl.step("bundletool", "done" if st["bundletool"] else "running")
    if not st["bundletool"]:
        try:
            install_bundletool(tk.KS, ctl)
        except (DepsError, OSError) as e:                     # optional: only Google Play bundles need it
            ctl.log("bundletool was not installed (%s). Only .aab builds need it; you can retry later." % e)
    ctl.step("bundletool", "done")

    after = status(tk)
    if not (after["jdk"] and after["packages"]):
        raise DepsError("setup finished but something is still missing: " + ", ".join(k for k in ("jdk", "packages") if not after[k]))
    ctl.log("everything is installed")
    return after
