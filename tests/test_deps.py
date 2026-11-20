"""python3 -m unittest tests.test_deps  - the dependency installer, against local servers and fake tools (no internet)."""
import os, sys, io, stat, json, tarfile, zipfile, hashlib, shutil, tempfile, threading, unittest, http.server, functools, socketserver

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
_HOME = tempfile.mkdtemp(prefix="pockettk-test-home-")
os.environ["POCKETTK_HOME"] = _HOME
import pockettk as tk            # noqa: E402
import pockettk_deps as deps    # noqa: E402

WIN = os.name == "nt"


class FakeCtl:
    def __init__(self, accept=True):
        self.lines, self.statuses, self.steps, self.progresses, self.licenses = [], [], [], [], []
        self.accept, self.cancelled, self.proc = accept, False, None

    def log(self, t): self.lines.append(t)
    def status(self, t): self.statuses.append(t)
    def step(self, i, s): self.steps.append((i, s))
    def progress(self, d, t, l=""): self.progresses.append((d, t, l))
    def check(self):
        if self.cancelled: raise deps.Cancelled()
    def set_proc(self, p): self.proc = p
    def license(self, text):
        self.licenses.append(text)
        return self.accept


class Server:
    """Serves a folder on 127.0.0.1 for the length of a test."""
    def __init__(self, root):
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root)
        handler.log_message = lambda *a, **k: None
        class S(socketserver.ThreadingMixIn, http.server.HTTPServer): daemon_threads = True
        self.httpd = S(("127.0.0.1", 0), handler)
        self.url = "http://127.0.0.1:%d/" % self.httpd.server_address[1]
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
    def close(self): self.httpd.shutdown(); self.httpd.server_close()


def make_tar(path, files):
    """files: {name: (content, mode)}"""
    with tarfile.open(path, "w:gz") as t:
        for name, (content, mode) in files.items():
            ti = tarfile.TarInfo(name); ti.size = len(content); ti.mode = mode
            t.addfile(ti, io.BytesIO(content))


class Extract(unittest.TestCase):
    def setUp(self): self.d = tempfile.mkdtemp()
    def tearDown(self): shutil.rmtree(self.d, ignore_errors=True)

    def test_tar_ok_keeps_exec_bit(self):
        a = os.path.join(self.d, "a.tar.gz"); make_tar(a, {"jdk/bin/javac": (b"#!/bin/sh\n", 0o755), "jdk/readme": (b"x", 0o644)})
        out = os.path.join(self.d, "out"); deps.extract(a, out)
        self.assertTrue(os.path.isfile(os.path.join(out, "jdk", "readme")))
        if not WIN: self.assertTrue(os.stat(os.path.join(out, "jdk", "bin", "javac")).st_mode & stat.S_IXUSR)

    def test_tar_path_traversal_refused(self):
        a = os.path.join(self.d, "evil.tar.gz"); make_tar(a, {"../escape.txt": (b"x", 0o644)})
        with self.assertRaises(deps.DepsError): deps.extract(a, os.path.join(self.d, "out"))
        self.assertFalse(os.path.exists(os.path.join(self.d, "escape.txt")))

    def test_tar_symlink_escape_refused(self):
        a = os.path.join(self.d, "link.tar.gz")
        with tarfile.open(a, "w:gz") as t:
            ti = tarfile.TarInfo("sneaky"); ti.type = tarfile.SYMTYPE; ti.linkname = "../../etc"; t.addfile(ti)
        with self.assertRaises(deps.DepsError): deps.extract(a, os.path.join(self.d, "out"))

    def test_zip_slip_refused_and_exec_bit_restored(self):
        z = os.path.join(self.d, "evil.zip")
        with zipfile.ZipFile(z, "w") as f: f.writestr("../escape.txt", "x")
        with self.assertRaises(deps.DepsError): deps.extract(z, os.path.join(self.d, "out"))
        good = os.path.join(self.d, "good.zip")
        with zipfile.ZipFile(good, "w") as f:
            zi = zipfile.ZipInfo("t/bin/tool"); zi.external_attr = 0o755 << 16; f.writestr(zi, "#!/bin/sh\n")
        deps.extract(good, os.path.join(self.d, "out2"))
        if not WIN: self.assertTrue(os.stat(os.path.join(self.d, "out2", "t", "bin", "tool")).st_mode & stat.S_IXUSR)


class Download(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp(); self.srv = Server(self.d)
        self.data = os.urandom(300000)
        with open(os.path.join(self.d, "f.bin"), "wb") as f: f.write(self.data)
    def tearDown(self): self.srv.close(); shutil.rmtree(self.d, ignore_errors=True)

    def test_ok_with_checksum_and_progress(self):
        ctl = FakeCtl(); dest = os.path.join(self.d, "got.bin")
        deps.download(self.srv.url + "f.bin", dest, ctl, hashlib.sha256(self.data).hexdigest(), "x")
        self.assertEqual(open(dest, "rb").read(), self.data)
        self.assertEqual(ctl.progresses[-1][:2], (len(self.data), len(self.data)))

    def test_bad_checksum_leaves_nothing(self):
        dest = os.path.join(self.d, "got.bin")
        with self.assertRaises(deps.DepsError): deps.download(self.srv.url + "f.bin", dest, FakeCtl(), "0" * 64)
        self.assertFalse(os.path.exists(dest)); self.assertFalse(os.path.exists(dest + ".part"))

    def test_cancel_stops_and_cleans_up(self):
        ctl = FakeCtl(); ctl.cancelled = True; dest = os.path.join(self.d, "got.bin")
        with self.assertRaises(deps.Cancelled): deps.download(self.srv.url + "f.bin", dest, ctl)
        self.assertFalse(os.path.exists(dest + ".part"))

    def test_404_is_a_clear_error(self):
        with self.assertRaises(deps.DepsError): deps.download(self.srv.url + "missing", os.path.join(self.d, "x"), FakeCtl())


class JdkLookup(unittest.TestCase):
    def test_adoptium_parsing(self):
        sample = [{"binary": {"package": {"link": "https://x/jdk.tar.gz", "name": "jdk.tar.gz", "checksum": "ab", "size": 5}}}]
        orig = deps.fetch_json; deps.fetch_json = lambda url: (self.assertIn("architecture=aarch64", url) or sample)
        try:
            a = deps.adoptium_asset(21, ("mac", "aarch64"))
        finally:
            deps.fetch_json = orig
        self.assertEqual((a["link"], a["checksum"]), ("https://x/jdk.tar.gz", "ab"))
        deps.fetch_json = lambda url: []
        try:
            with self.assertRaises(deps.DepsError): deps.adoptium_asset(21, ("linux", "x64"))
        finally:
            deps.fetch_json = orig

    def test_mac_layout_and_pockettk_finds_it(self):
        base = os.path.join(tk.KS, "jdk"); shutil.rmtree(base, ignore_errors=True)
        home = os.path.join(base, "jdk-21.0.1+1", "Contents", "Home")
        os.makedirs(os.path.join(home, "bin"))
        for n in ("javac", "java", "keytool"): open(os.path.join(home, "bin", n + tk.EXE), "w").write("x")
        self.assertEqual(deps.jdk_home_in(os.path.join(base, "jdk-21.0.1+1")), home)
        self.assertIn(home, tk.local_jdks())
        self.assertIn(home, tk.java_homes())
        old = dict(os.environ)
        try:
            os.environ.pop("JAVA_HOME", None)
            self.assertEqual(tk.export_java_home(), home)
            self.assertTrue(os.environ["PATH"].startswith(os.path.join(home, "bin")))
        finally:
            os.environ.clear(); os.environ.update(old)
        shutil.rmtree(base, ignore_errors=True)


@unittest.skipIf(WIN, "uses shell scripts as fake SDK tools")
class FullSetup(unittest.TestCase):
    """run_setup end to end: JDK, command-line tools, license, packages, bundletool, all from local files."""

    def setUp(self):
        self.d = tempfile.mkdtemp(); self.www = os.path.join(self.d, "www"); os.makedirs(self.www)
        self.ks = os.path.join(self.d, "ks"); os.makedirs(self.ks)
        # a JDK archive (Mac layout) and a command-line-tools zip whose sdkmanager is a shell script
        make_tar(os.path.join(self.www, "jdk.tar.gz"), {
            "jdk-21+1/Contents/Home/bin/javac": (b"#!/bin/sh\n", 0o755), "jdk-21+1/Contents/Home/bin/java": (b"#!/bin/sh\n", 0o755),
            "jdk-21+1/Contents/Home/bin/keytool": (b"#!/bin/sh\n", 0o755)})
        sdkmanager = b"""#!/bin/sh
SDK=""; LIC=0; INST=""
for a in "$@"; do case "$a" in --sdk_root=*) SDK="${a#--sdk_root=}";; --licenses) LIC=1;; *) INST="$INST $a";; esac; done
if [ "$LIC" = 1 ]; then
  if [ -f "$SDK/licenses/accepted" ]; then echo "All SDK package licenses accepted."; exit 0; fi
  read first
  echo "Review licenses that have not been accepted (y/N)? "
  echo "---------------------------------------"
  echo "Terms and Conditions: THE FAKE LICENSE TEXT"
  echo "---------------------------------------"
  echo "Accept? (y/N): "
  read ans
  if [ "$ans" = y ]; then mkdir -p "$SDK/licenses"; echo ok > "$SDK/licenses/accepted"; fi
  exit 0
fi
[ -f "$SDK/licenses/accepted" ] || { echo "License not accepted"; exit 1; }
echo "[=====     ] 50% Downloading"
mkdir -p "$SDK/build-tools/34.0.0" "$SDK/platforms/android-34" "$SDK/platform-tools"
for n in aapt2 d8 apksigner; do echo x > "$SDK/build-tools/34.0.0/$n"; chmod +x "$SDK/build-tools/34.0.0/$n"; done
echo jar > "$SDK/platforms/android-34/android.jar"; echo x > "$SDK/platform-tools/adb"
"""
        with zipfile.ZipFile(os.path.join(self.www, "cmd.zip"), "w") as z:
            zi = zipfile.ZipInfo("cmdline-tools/bin/sdkmanager"); zi.external_attr = 0o755 << 16; z.writestr(zi, sdkmanager)
        self.srv = Server(self.www)
        self.sdk = os.path.join(self.ks, "sdk")
        d = self
        class FakeTk:
            KS = d.ks
            EXE = ""
            def find_tool(s, n):
                for h in [p for p in __import__("glob").glob(os.path.join(d.ks, "jdk", "*", "Contents", "Home"))]:
                    f = os.path.join(h, "bin", n)
                    if os.path.isfile(f): return f
                f = os.path.join(d.sdk, "build-tools", "34.0.0", n)
                if os.path.isfile(f): return f
                f = os.path.join(d.sdk, "platform-tools", n)
                return f if os.path.isfile(f) else None
            def find_lib(s, n): return None
            def find_android_jar(s):
                f = os.path.join(d.sdk, "platforms", "android-34", "android.jar"); return f if os.path.isfile(f) else None
            def find_bundletool(s):
                f = os.path.join(d.ks, "bundletool.jar"); return f if os.path.isfile(f) else None
            def export_java_home(s):
                for h in __import__("glob").glob(os.path.join(d.ks, "jdk", "*", "Contents", "Home")): return h
                return ""
        self.tk = FakeTk()
        self._orig = (deps.adoptium_asset, deps.cmdline_tools_url, deps.install_bundletool)
        sha = hashlib.sha256(open(os.path.join(self.www, "jdk.tar.gz"), "rb").read()).hexdigest()
        deps.adoptium_asset = lambda *a, **k: {"link": self.srv.url + "jdk.tar.gz", "name": "jdk.tar.gz", "checksum": sha, "size": 1}
        deps.cmdline_tools_url = lambda: self.srv.url + "cmd.zip"
        def fake_bt(dest, ctl):
            p = os.path.join(dest, "bundletool.jar"); open(p, "w").write("jar"); return p
        deps.install_bundletool = fake_bt

    def tearDown(self):
        deps.adoptium_asset, deps.cmdline_tools_url, deps.install_bundletool = self._orig
        self.srv.close(); shutil.rmtree(self.d, ignore_errors=True)

    def test_everything_gets_installed_after_the_license_is_accepted(self):
        self.assertTrue(deps.needs_setup(self.tk))
        ctl = FakeCtl(accept=True)
        after = deps.run_setup(self.tk, ctl)
        self.assertTrue(after["jdk"] and after["packages"] and after["bundletool"])
        self.assertFalse(deps.needs_setup(self.tk))
        self.assertEqual(len(ctl.licenses), 1); self.assertIn("FAKE LICENSE TEXT", ctl.licenses[0])
        self.assertNotIn("Accept?", ctl.licenses[0])
        self.assertIn(("jdk", "done"), ctl.steps); self.assertIn(("sdk", "done"), ctl.steps)
        self.assertTrue(any("50%" in s for s in ctl.statuses))       # progress lines went to the status line

    def test_declining_the_license_installs_nothing_of_the_sdk(self):
        ctl = FakeCtl(accept=False)
        with self.assertRaises(deps.Cancelled): deps.run_setup(self.tk, ctl)
        self.assertFalse(os.path.exists(os.path.join(self.sdk, "build-tools")))
        self.assertTrue(deps.status(self.tk)["jdk"])                  # the JDK part had already happened

    def test_second_run_does_nothing(self):
        deps.run_setup(self.tk, FakeCtl())
        ctl = FakeCtl(); deps.run_setup(self.tk, ctl)
        self.assertEqual(ctl.licenses, []); self.assertEqual(ctl.progresses, [])

    def test_bundletool_failure_is_not_fatal(self):
        def boom(dest, ctl): raise deps.DepsError("github down")
        deps.install_bundletool = boom
        after = deps.run_setup(self.tk, FakeCtl())
        self.assertTrue(after["packages"] and not after["bundletool"])


@unittest.skipIf(WIN, "uses shell scripts")
class Streaming(unittest.TestCase):
    def test_carriage_return_progress_goes_to_status_and_exit_code_is_returned(self):
        d = tempfile.mkdtemp(); s = os.path.join(d, "t.sh")
        open(s, "w").write("#!/bin/sh\nprintf '[===   ] 30%% x\\r[======] 100%% x\\rplain line\\n'\nexit 3\n"); os.chmod(s, 0o755)
        ctl = FakeCtl(); code, out = deps.run_stream([s], ctl)
        self.assertEqual(code, 3); self.assertEqual(ctl.statuses, ["[===   ] 30% x", "[======] 100% x"]); self.assertEqual(ctl.lines, ["plain line"])
        shutil.rmtree(d, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
