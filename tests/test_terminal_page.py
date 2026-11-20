"""python3 -m unittest tests.test_terminal_page  - drives the real page (webui/) in headless Chromium against a stand-in Api.
Skipped when Playwright is not installed (it is a development check, not needed to use PocketTk)."""
import os, sys, json, tempfile, unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
os.environ.setdefault("POCKETTK_HOME", tempfile.mkdtemp(prefix="pockettk-page-home-"))
try:
    from playwright.sync_api import sync_playwright
except ImportError:                                                       # pragma: no cover
    sync_playwright = None
import pockettk_gui as gui       # noqa: E402

STUB = r'''
(function () {
  const calls = []; let events = []; let next = 0; window.__calls = calls;
  window.__push = function (e) { events.push(e); };
  const files = { "/home/u": [["apps/", 1], ["notes.py", 0], ["my app.py", 0], ["readme.txt", 0], ["notes2.py", 0]] };
  const api = {
    startup: () => ({ version: "PocketTk v9.9.9", platform: "Linux", machine: "x86_64", script: null, pick: false, outputDir: "/home/u/PocketTk",
                      ready: window.__ready !== false, autoSetupDone: true, cwd: "/home/u", home: "/home/u", sep: "/" }),
    apps: () => [{ id: "notes", name: "Notes", pkg: "pockettk.ssdes.notes", src: "/home/u/notes.py", srcExists: true, version: "1.0.1 Patch 2" }],
    doctor: () => ({ items: [{ id: "java", label: "Java: javac", ok: true, detail: "/usr/bin/javac", optional: false },
                              { id: "adb", label: "adb", ok: false, detail: "optional", optional: true }], ready: true, dataDir: "/home/u/.pockettk", outputDir: "/home/u/PocketTk" }),
    poll: (since) => { const evs = events; events = []; next += evs.length; return { events: evs, next: next, busy: window.__busy || {}, workKind: "" }; },
    resolve: (p) => p.startsWith("/") ? p : "/home/u/" + p,
    use_path: (p) => { calls.push(["use_path", p]); const full = p.startsWith("/") ? p : "/home/u/" + p;
                       if (!/\.py$/.test(full)) return { error: "choose a Python file (.py)" };
                       if (full.endsWith("nope.py")) return { error: "that file does not exist" };
                       const b = full.split("/").pop().replace(".py", "");
                       return { path: full, file: full.split("/").pop(), name: b === "notes" ? "Notes" : b, id: b === "notes" ? "notes" : "", version: b === "notes" ? "1.0.1 Patch 2" : "", folder: "/home/u" }; },
    complete_path: (t, exts, dirsOnly) => { const base = t.split("/").pop(); return files["/home/u"].filter(f => (!dirsOnly || f[1]) && f[0].startsWith(base) && (f[1] || !exts || !exts.length || exts.some(e => f[0].endsWith(e)))).map(f => f[0]); },
    build: (o) => { calls.push(["build", o]); return { ok: true }; },
    check: (p) => ({ ok: true, warnings: ["line 3: unsupported thing"], notes: ["uses 2 widgets"] }),
    cd: (p) => ({ ok: true, cwd: p === ".." ? "/home" : "/home/u/" + p }),
    ls: () => ({ ok: true, dir: "/home/u", entries: files["/home/u"].map(f => ({ name: f[0].replace("/", ""), dir: !!f[1] })) }),
    pwd: () => ({ cwd: "/home/u" }), reveal: (p) => { calls.push(["reveal", p]); return { ok: true }; },
    devices: () => ({ adb: true, devices: [] }), cancel: () => { calls.push(["cancel"]); return { ok: true }; },
    start_setup: () => ({ ok: true }), answer_license: (y) => { calls.push(["license", y]); return { ok: true }; },
    install_on_phone: (a, p) => { calls.push(["install", a, p]); return { ok: true }; },
    update: (id, bump, src) => { calls.push(["update", id, bump, src]); return { ok: true }; },
    outdir: (p) => ({ ok: true, dir: p || "/home/u/PocketTk" }), quit: () => { calls.push(["quit"]); return { ok: true }; },
  };
  window.pywebview = { api: api };
})();
'''


@unittest.skipIf(sync_playwright is None, "playwright is not installed")
class Page(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pw = sync_playwright().start()
        cls.browser = cls.pw.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.pw.stop()

    def setUp(self):
        self.page = self.browser.new_page(viewport={"width": 980, "height": 640})
        self.errors = []
        self.page.on("pageerror", lambda e: self.errors.append(str(e)))
        self.page.set_content(gui.page_html().replace("<script>", "<script>" + STUB + "</script>\n<script>", 1))
        self.page.wait_for_function("document.querySelectorAll('#out .ln').length >= 2")

    def tearDown(self):
        self.assertEqual(self.errors, [])
        self.page.close()

    def type(self, text):
        self.page.fill("#inp", text)
        self.page.press("#inp", "Enter")
        self.page.wait_for_timeout(120)

    def text(self):
        return self.page.inner_text("#out")

    def calls(self):
        return self.page.evaluate("window.__calls")

    def test_banner_and_prompt(self):
        t = self.text()
        self.assertIn("PocketTk v9.9.9", t)
        self.assertIn("type help", t)
        self.assertEqual(self.page.inner_text("#ps").strip(), "~ $")

    def test_help_and_unknown_command(self):
        self.type("help")
        self.assertIn("build", self.text())
        self.type("help build")
        self.assertIn("--aab", self.text())
        self.type("frobnicate")
        self.assertIn("command not found", self.text())
        self.type("build --nope")
        self.assertIn("unknown option --nope", self.text())

    def test_build_flow_with_events(self):
        self.type("build")
        self.assertIn("no script chosen", self.text())
        self.type("build notes.py --id notes --small")
        c = [x for x in self.calls() if x[0] == "build"][0][1]
        self.assertEqual((c["src"], c["ident"], c["bump"], c["format"], c["name"]), ("/home/u/notes.py", "notes", "small", "apk", "Notes"))
        self.page.evaluate("""__busy = {work: true}; __push({job:'build', t:'start'}); __push({job:'build', t:'log', text:'- transpiling notes.py'});
            __push({job:'build', t:'log', text:'warning: something'}); __push({job:'build', t:'status', text:'dexing...'})""")
        self.page.wait_for_timeout(500)
        self.assertIn("- transpiling notes.py", self.text())
        self.assertIn("dexing...", self.page.inner_text("#live"))
        self.assertTrue(self.page.evaluate("document.body.classList.contains('busy')"))
        self.page.evaluate("""__busy = {}; __push({job:'build', t:'done', ok:true, path:'/home/u/PocketTk/notes.apk', aab:false, info:"app id 'notes' -> pockettk.ssdes.notes (version 1.0.1)", pkg:'pockettk.ssdes.notes'})""")
        self.page.wait_for_timeout(500)
        t = self.text()
        self.assertIn("built   /home/u/PocketTk/notes.apk", t)
        self.assertIn("type install", t)
        self.assertFalse(self.page.evaluate("document.body.classList.contains('busy')"))
        self.type("install")
        self.assertIn(["install", "/home/u/PocketTk/notes.apk", "pockettk.ssdes.notes"], self.calls())

    def test_failed_build_and_ctrl_c(self):
        self.type("build notes.py")
        self.page.evaluate("__busy = {work: true}; __push({job:'build', t:'start'})")
        self.page.wait_for_timeout(400)
        self.page.press("#inp", "Control+c")
        self.page.wait_for_timeout(100)
        self.assertIn(["cancel"], self.calls())
        self.page.evaluate("__busy = {}; __push({job:'build', t:'error', message:'Cancelled.', cancelled:true})")
        self.page.wait_for_timeout(400)
        self.assertIn("cancelled", self.text())
        self.page.evaluate("__push({job:'build', t:'start'}); __push({job:'build', t:'error', message:'javac failed'})")
        self.page.wait_for_timeout(400)
        self.assertIn("error: javac failed", self.text())

    def test_tab_completion(self):
        self.page.fill("#inp", "bu")
        self.page.press("#inp", "Tab")
        self.page.wait_for_timeout(150)
        self.assertEqual(self.page.input_value("#inp"), "build ")
        self.page.fill("#inp", "build no")
        self.page.press("#inp", "Tab")
        self.page.wait_for_timeout(150)
        self.assertEqual(self.page.input_value("#inp"), "build notes")           # common prefix of notes.py / notes2.py
        self.page.fill("#inp", "build my")
        self.page.press("#inp", "Tab")
        self.page.wait_for_timeout(150)
        self.page.wait_for_timeout(100)
        self.assertEqual(self.page.input_value("#inp"), 'build "my app.py" ')
        self.page.fill("#inp", "update n")
        self.page.press("#inp", "Tab")
        self.page.wait_for_timeout(150)
        self.assertEqual(self.page.input_value("#inp"), "update notes ")

    def test_history(self):
        self.type("pwd")
        self.type("version")
        self.page.press("#inp", "ArrowUp")
        self.assertEqual(self.page.input_value("#inp"), "version")
        self.page.press("#inp", "ArrowUp")
        self.assertEqual(self.page.input_value("#inp"), "pwd")
        self.page.press("#inp", "ArrowDown")
        self.page.press("#inp", "ArrowDown")
        self.assertEqual(self.page.input_value("#inp"), "")

    def test_license_prompt_and_password(self):
        self.page.evaluate("__busy = {work: true}; __push({job:'setup', t:'start'}); __push({job:'setup', t:'license', text:'THE LICENSE TEXT'})")
        self.page.wait_for_timeout(500)
        self.assertIn("THE LICENSE TEXT", self.text())
        self.assertIn("Accept the Android SDK license?", self.page.inner_text("#ps"))
        self.page.fill("#inp", "y")
        self.page.press("#inp", "Enter")
        self.page.wait_for_timeout(200)
        self.assertIn(["license", True], self.calls())
        self.page.evaluate("__busy = {}; __push({job:'setup', t:'done', ok:true})")
        self.page.wait_for_timeout(400)
        self.assertIn("setup finished.", self.text())
        self.type("build notes.py --aab --keystore my.jks")
        self.assertEqual(self.page.get_attribute("#inp", "type"), "password")
        self.page.fill("#inp", "s3cret")
        self.page.press("#inp", "Enter")
        self.page.wait_for_timeout(300)
        b = [x for x in self.calls() if x[0] == "build"][0][1]
        self.assertEqual((b["format"], b["keystore"], b["ksPass"]), ("aab", "/home/u/my.jks", "s3cret"))
        self.assertNotIn("s3cret", self.text())
        self.assertEqual(self.page.get_attribute("#inp", "type"), "text")

    def test_misc_commands(self):
        self.type("check notes.py")
        self.assertIn("warning: line 3: unsupported thing", self.text())
        self.type("apps")
        self.assertIn("pockettk.ssdes.notes", self.text())
        self.type("ls")
        self.assertIn("apps/", self.text())
        self.type("doctor")
        self.assertIn("[ok]", self.text())
        self.type("theme paper")
        self.assertEqual(self.page.evaluate("document.body.dataset.theme"), "paper")
        self.type("theme nope")
        self.assertIn("choose one of", self.text())
        self.type("update notes --medium")
        self.assertIn(["update", "notes", "medium", None], self.calls())
        self.type("about")
        self.assertIn("SSDES", self.text())
        self.type("clear")
        self.assertEqual(self.page.inner_text("#out").strip(), "")
        self.type("exit")
        self.assertIn(["quit"], self.calls())

    def test_screenshot(self):
        self.type("pick")
        self.type("build notes.py --id notes")
        shot = os.environ.get("POCKETTK_SHOT")
        if shot:
            self.page.screenshot(path=shot)


if __name__ == "__main__":
    unittest.main()
