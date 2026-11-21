"""Treeview and LabeledScale are real widgets now (they used to be placeholder labels)."""
import unittest
from tests.test_images import gen


class MoreWidgets(unittest.TestCase):
    def test_treeview(self):
        p, java, out = gen('import tkinter as tk\nfrom tkinter import ttk\nroot = tk.Tk()\n'
                           'tv = ttk.Treeview(root, columns=("a", "b"), show="headings")\n'
                           'tv.heading("a", text="A")\ntv.insert("", "end", values=(1, 2))\n'
                           'tv.bind("<<TreeviewSelect>>", lambda e: print(tv.selection()))\nroot.mainloop()\n')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn('Ui.w("Treeview"', java)
        self.assertNotIn("not supported yet", p.stdout + p.stderr)

    def test_labeled_scale(self):
        p, java, out = gen('import tkinter as tk\nfrom tkinter import ttk\nroot = tk.Tk()\n'
                           'v = tk.IntVar()\nttk.LabeledScale(root, variable=v, from_=0, to=9).pack()\nroot.mainloop()\n')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn("Ui.labeledScale(", java)


if __name__ == "__main__":
    unittest.main()
