"""python3 -m unittest tests.test_termux_connect  - the transpiler's `#use: termux-connect` directive and `import termux`."""
import os, sys, subprocess, tempfile, unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

APP = '''#use: termux-connect
import tkinter as tk
import termux
root = tk.Tk()
def done(code, out, err):
    root.title(str(code))
if termux.state() == "ok":
    termux.run(["pockettk", "doctor"], done)
root.mainloop()
'''


class TranspileTests(unittest.TestCase):
    def gen(self, text):
        d = tempfile.mkdtemp()
        p = os.path.join(d, "a.py")
        with open(p, "w") as f:
            f.write(text)
        r = subprocess.run([sys.executable, os.path.join(ROOT, "transpile.py"), "gen", p, os.path.join(d, "o"), "--no-assets"], capture_output=True, text=True)
        return r, os.path.join(d, "o")

    def test_script_builds_with_permission_and_receiver(self):
        r, out = self.gen(APP)
        self.assertEqual(r.returncode, 0, r.stderr)
        with open(os.path.join(out, "AndroidManifest.xml")) as f:
            m = f.read()
        self.assertIn("com.termux.permission.RUN_COMMAND", m)
        self.assertIn("apple.rt.TermuxResult", m)

    def test_import_needs_the_directive(self):
        r, out = self.gen("import tkinter as tk\nimport termux\nroot = tk.Tk()\nroot.mainloop()\n")
        self.assertEqual(r.returncode, 2)
        self.assertIn("#use: termux-connect", r.stderr)

    def test_normal_apps_have_no_termux_permission(self):
        r, out = self.gen("import tkinter as tk\nroot = tk.Tk()\nroot.mainloop()\n")
        self.assertEqual(r.returncode, 0)
        with open(os.path.join(out, "AndroidManifest.xml")) as f:
            self.assertNotIn("termux", f.read())


class NoAndroidWindow(unittest.TestCase):
    def test_nothing_of_the_android_window_is_left(self):
        for f in ("pockettk_app.py", "termux_job.sh", "termux_launch.sh", "action.sh", "pockettk_tgui.py"):
            self.assertFalse(os.path.exists(os.path.join(ROOT, f)), f)
        for f in ("apkapple.sh", "install_termux.sh", "customize.sh", "termux_setup.sh", "service.sh"):
            with open(os.path.join(ROOT, f)) as fh:
                text = fh.read()
            for word in ("pockettk_app", "termux_job", "termux_launch", "cmd_make_gui", "termuxgui", "action.sh"):
                if word == "pockettk_app" and f in ("install_termux.sh", "customize.sh"):
                    continue                      # those two delete what older versions left behind
                self.assertNotIn(word, text, f + " still mentions " + word)

    def test_gui_command_says_there_is_no_window(self):
        r = subprocess.run(["sh", os.path.join(ROOT, "apkapple.sh"), "make-gui"], capture_output=True, text=True,
                           env=dict(os.environ, HOME=tempfile.mkdtemp()))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("no window on Android", r.stdout + r.stderr)


if __name__ == "__main__":
    unittest.main()
