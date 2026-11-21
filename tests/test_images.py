"""PhotoImage: the transpiler emits Ui.photo(...) and bundles a literal file next to the script into assets/img/."""
import os, subprocess, sys, tempfile, unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def gen(src, files=()):
    d = tempfile.mkdtemp()
    for name, data in files:
        open(os.path.join(d, name), "wb").write(data)
    script = os.path.join(d, "app.py")
    open(script, "w").write(src)
    out = os.path.join(d, "out")
    p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "transpile.py"), "gen", script, out],
                       capture_output=True, text=True)
    java = ""
    jp = os.path.join(out, "src", "apple", "gen", "Script.java")
    if os.path.isfile(jp):
        java = open(jp, encoding="utf-8").read()
    return p, java, out


PNG = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d49444154789c6360f8cfc0f01f0005000201a52f1fd10000000049454e44ae426082")


class Images(unittest.TestCase):
    def test_file_is_bundled(self):
        p, java, out = gen('import tkinter as tk\nroot = tk.Tk()\nimg = tk.PhotoImage(file="a.png")\ntk.Label(root, image=img).pack()\nroot.mainloop()\n',
                           [("a.png", PNG)])
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn("Ui.photo(", java)
        self.assertIn('"asset"', java)
        self.assertEqual(len(os.listdir(os.path.join(out, "assets", "img"))), 1)
        self.assertNotIn("not supported yet", p.stdout + p.stderr)

    def test_data_and_size(self):
        p, java, out = gen('import tkinter as tk\nroot = tk.Tk()\nimg = tk.PhotoImage(width=4, height=4)\nimg.put("red", to=(0, 0, 4, 4))\nroot.mainloop()\n')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn('"width"', java)

    def test_missing_file_warns(self):
        p, java, out = gen('import tkinter as tk\nroot = tk.Tk()\nimg = tk.PhotoImage(file="nope.png")\nroot.mainloop()\n')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn("not found next to the script", p.stdout + p.stderr)


if __name__ == "__main__":
    unittest.main()
