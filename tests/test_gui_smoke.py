"""python3 -m unittest tests.test_gui_smoke  - the window's POCKETTK_GUI_SMOKE mode (what the GitHub desktop test runs), with a stand-in for pywebview."""
import os, sys, types, unittest, tempfile
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
os.environ.setdefault("POCKETTK_HOME", tempfile.mkdtemp(prefix="pockettk-smoke-"))
import pockettk_gui as G   # noqa: E402


class FakeWindow:
    def __init__(self, answers):
        self.answers, self.destroyed = answers, False

    def evaluate_js(self, js):
        for key, val in self.answers.items():
            if key in js:
                if isinstance(val, Exception):
                    raise val
                return val
        return None

    def destroy(self):
        self.destroyed = True


def fake_webview(window):
    wv = types.ModuleType("webview")
    wv.create_window = lambda *a, **k: window
    wv.start = lambda func=None, args=(), **k: func(*args) if func else None
    return wv


GOOD = {"document.readyState": "complete", "getElementById": "PocketTk", "pywebview.api": True}


class Smoke(unittest.TestCase):
    def run_main(self, answers):
        w = FakeWindow(answers)
        old = sys.modules.get("webview")
        sys.modules["webview"] = fake_webview(w)
        os.environ["POCKETTK_GUI_SMOKE"] = "1"
        os.environ["POCKETTK_GUI_SMOKE_HOLD"] = "0"
        G._SMOKE_RESULT = None
        try:
            code = G.main()
        finally:
            os.environ.pop("POCKETTK_GUI_SMOKE", None)
            os.environ.pop("POCKETTK_GUI_SMOKE_HOLD", None)
            if old is None:
                sys.modules.pop("webview", None)
            else:
                sys.modules["webview"] = old
        return code, w

    def test_ok(self):
        code, w = self.run_main(dict(GOOD))
        self.assertEqual(code, 0)
        self.assertTrue(w.destroyed)

    def test_bridge_missing_fails(self):
        code, w = self.run_main(dict(GOOD, **{"pywebview.api": False}))
        self.assertEqual(code, 1)
        self.assertTrue(w.destroyed)

    def test_normal_mode_unchanged(self):
        w = FakeWindow({})
        old = sys.modules.get("webview")
        sys.modules["webview"] = fake_webview(w)
        try:
            self.assertEqual(G.main(), 0)
        finally:
            if old is None:
                sys.modules.pop("webview", None)
            else:
                sys.modules["webview"] = old
        self.assertFalse(w.destroyed)


if __name__ == "__main__":
    unittest.main()
