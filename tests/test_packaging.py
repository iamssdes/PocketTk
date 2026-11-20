"""python3 -m unittest tests.test_packaging  - the Windows / Linux / macOS packaging pieces that can be checked without those systems."""
import os, re, sys, struct, subprocess, tempfile, unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
_HOME = tempfile.mkdtemp(prefix="pockettk-pkg-home-")
os.environ["POCKETTK_HOME"] = _HOME
sys.path.insert(0, os.path.join(HERE, "packaging", "windows"))


def read(*p):
    with open(os.path.join(HERE, *p), encoding="utf-8") as f:
        return f.read()


class Packaging(unittest.TestCase):
    def test_ico_is_valid(self):
        import make_ico
        d = make_ico.build()
        reserved, kind, n = struct.unpack("<HHH", d[:6])
        self.assertEqual((reserved, kind), (0, 1))
        self.assertEqual(n, len(make_ico.SIZES))
        for i in range(n):
            w, h, _, _, _, _, size, off = struct.unpack("<BBBBHHII", d[6 + 16 * i:22 + 16 * i])
            self.assertEqual(d[off:off + 8], b"\x89PNG\r\n\x1a\n")
            self.assertEqual(off + size <= len(d), True)

    def test_icon_png_draws(self):
        sys.path.insert(0, os.path.join(HERE, "packaging", "macos"))
        import make_icon
        rows = make_icon.render(64)
        self.assertEqual(len(rows), 64)
        self.assertEqual(rows[0][3], 0)                                   # the corner is transparent
        self.assertEqual(tuple(rows[32][4 * 32:4 * 32 + 3]) != (0, 0, 0), True)
        self.assertTrue(make_icon.png(64, rows).startswith(b"\x89PNG"))
        self.assertTrue(os.path.isfile(os.path.join(HERE, "packaging", "logo.svg")))

    def test_version_is_readable_by_all_build_scripts(self):
        v = re.search(r'^VERSION = "PocketTk v(.*)"', read("pockettk.py"), re.M)
        self.assertTrue(v and re.fullmatch(r"\d+\.\d+\.\d+", v.group(1)), "build scripts need a plain x.y.z version")
        for name in ("README.md", "customize.sh", "apkapple.sh"):
            self.assertIn("PocketTk v" + v.group(1), read(name))
        self.assertIn("version=v" + v.group(1), read("module.prop"))

    def test_bundle_id_is_ssdes(self):
        self.assertIn("com.ssdes.pockettk", read("packaging", "macos", "build_app.sh"))
        for f in ("packaging/macos/build_app.sh", "packaging/macos/Info.plist.in", ".github/workflows/apps.yml"):
            self.assertNotIn("palalgon", read(*f.split("/")))

    def test_shell_scripts_parse(self):
        for f in ("packaging/linux/build_app.sh", "packaging/linux/install.sh", "packaging/linux/AppRun", "packaging/macos/build_app.sh"):
            r = subprocess.run(["bash", "-n", os.path.join(HERE, f)], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, f + ": " + r.stderr)

    def test_flash_logo_is_plain_and_aligned(self):
        r = subprocess.run(["sh", "-c", ". ./logo.sh; pockettk_logo echo"], cwd=HERE, capture_output=True, text=True)
        plain = r.stdout
        self.assertTrue(plain.isascii(), "the flash screen's fallback font does not align box-drawing or block characters")
        rows = [l for l in plain.split("\n") if l.strip()]
        pocket = [l for l in rows if l.startswith("   ") and not l.startswith("      ")]
        phone = [l for l in rows if l.startswith("      ")]
        self.assertEqual(len({len(l) for l in pocket}), 1, pocket)
        self.assertEqual(len({len(l) for l in phone}), 1, phone)
        self.assertTrue(all(len(l) <= 36 for l in rows))
        self.assertEqual(len(pocket[0]), len(pocket[-1]))                 # fits a phone's flash screen
        self.assertIn("P o c k e t T k", plain)
        c = read("customize.sh")
        self.assertIn('. "$MODPATH/logo.sh"', c)
        self.assertLess(c.index("pockettk_logo ui_print"), c.index("tkinter -> APK"))

    def test_appimage_pieces(self):
        desk = read("packaging", "linux", "pockettk.desktop")
        for k in ("Name=PocketTk", "Exec=AppRun", "Icon=pockettk", "Type=Application"):
            self.assertIn(k, desk)
        self.assertTrue(os.access(os.path.join(HERE, "packaging", "linux", "AppRun"), os.X_OK))
        self.assertIn("AppImage", read(".github", "workflows", "apps.yml"))

    def test_ci_uses_runner_labels_that_exist(self):
        yml = read(".github", "workflows", "apps.yml")
        self.assertNotIn("runner: macos-13", yml, "macos-13 was retired by GitHub")
        self.assertNotIn("runner: macos-14", yml)
        self.assertIn("macos-15-intel", yml)
        self.assertIn("{ runner: macos-15, arch: arm64 }", yml)

    def test_arm_linux_cannot_block_the_release(self):
        yml = read(".github", "workflows", "apps.yml")
        self.assertIn("continue-on-error: ${{ matrix.experimental }}", yml)
        self.assertIn("ubuntu-24.04-arm, arch: aarch64, experimental: true", yml)
        self.assertIn("ubuntu-latest, arch: x86_64, experimental: false", yml)

    def test_linux_build_falls_back_to_pyside6_and_says_why(self):
        sh = read("packaging", "linux", "build_app.sh")
        for k in ("--only-binary=:all:", "PySide6", "qt-api", "qt_diagnose", "QT_BINDING"):
            self.assertIn(k, sh)
        self.assertLess(sh.index("PyQt6 PyQt6-WebEngine"), sh.index("pipi --only-binary=:all: PySide6"))
        self.assertIn("qt-api", read("packaging", "linux", "AppRun"))

    def test_every_script_the_apps_copy_exists(self):
        for f in ("windows/build_app.ps1", "linux/build_app.sh", "macos/build_app.sh"):
            text = read("packaging", *f.split("/"))
            m = re.search(r"pockettk\.py[ ,\"]+pockettk_gui\.py[^\n]*", text)
            self.assertTrue(m, f)
            for name in re.findall(r"[a-z_]+\.py", m.group(0)):
                self.assertTrue(os.path.isfile(os.path.join(HERE, name)), f + " copies missing " + name)
            for d in re.search(r"\bd in ([^;)]*\brt\b[^;)]*)", text).group(1).replace('"', "").replace(",", " ").split():
                self.assertTrue(os.path.isdir(os.path.join(HERE, d)), f + " copies missing folder " + d)

    def test_no_stdout_does_not_crash_the_window(self):
        code = ("import sys, os; sys.stdout = None; sys.stderr = None; os.environ['HOME'] = %r; os.environ['USERPROFILE'] = %r\n"
                "sys.path.insert(0, %r)\nimport pockettk_gui as g\ng._ensure_streams()\nprint('hello'); sys.stderr.write('x')\n"
                "print('ok', file=sys.__stdout__)" % (_HOME, _HOME, HERE))
        r = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, env=dict(os.environ, POCKETTK_HOME=_HOME))
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(os.path.isfile(os.path.join(_HOME, ".pockettk", "gui.log")))


if __name__ == "__main__":
    unittest.main()
