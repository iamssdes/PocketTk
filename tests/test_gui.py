"""python3 -m unittest tests.test_gui  - the window's Python side (Api), with a stub command line tool and a fake file chooser."""
import os, sys, time, json, shutil, tempfile, unittest, textwrap, subprocess

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
_HOME = tempfile.mkdtemp(prefix="pockettk-gui-home-")
os.environ["POCKETTK_HOME"] = _HOME
import pockettk as tk            # noqa: E402
import pockettk_gui as gui       # noqa: E402

WIN = os.name == "nt"

STUB = textwrap.dedent('''
    import os, sys, time, hashlib, subprocess
    a = sys.argv[1:]
    print("- args: " + " ".join(a), flush=True)
    print("- ks-pass-sha: " + hashlib.sha256(os.environ.get("POCKETTK_KS_PASS", "").encode()).hexdigest()[:8], flush=True)
    print("- out-env: " + os.environ.get("POCKETTK_OUT", ""), flush=True)
    mode = os.environ.get("STUB_MODE", "ok")
    if mode == "hang":
        child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
        open(os.environ["STUB_PIDFILE"], "w").write(str(child.pid))
        print("- transpiling x", flush=True)
        time.sleep(60)
    if mode == "fail":
        print("- transpiling x", flush=True)
        print("- compiling Java", flush=True)
        print("javac: some detail line", flush=True)
        print("pockettk: compiling failed (see above)", flush=True)
        sys.exit(1)
    for step in ("- transpiling x.py", "- compiling Java", "- dexing", "- adding icon", "- linking manifest", "- packaging", "- signing"):
        print(step, flush=True)
    print("- app id 'notes' -> tkdroid.palalgon.notes (version 1.0.1)", flush=True)
    out = os.environ.get("STUB_OUT", "/tmp/never.apk")
    print("- built: " + out, flush=True)
''')


class FakeDialogs:
    def __init__(self): self.answers, self.calls = [], []
    def open(self, types, folder=False, directory=""):
        self.calls.append((tuple(types), folder, directory))
        return self.answers.pop(0) if self.answers else None


def wait(api, pred, timeout=20):
    """Poll like the page does until pred(events) holds."""
    cursor, seen = 0, []
    end = time.time() + timeout
    while time.time() < end:
        r = api.poll(cursor); cursor = r["next"]; seen += r["events"]
        if pred(seen, r): return seen
        time.sleep(0.05)
    raise AssertionError("timed out; events: %r" % seen[-6:])


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp()
        self.stub = os.path.join(self.d, "stub.py"); open(self.stub, "w").write(STUB)
        self.script = os.path.join(self.d, "my_notes.py"); open(self.script, "w").write("import tkinter as tk\nroot = tk.Tk()\nroot.mainloop()\n")
        self.dialogs = FakeDialogs()
        self.api = gui.Api(dialogs=self.dialogs, cli=self.stub)
        for f in ("ids", "gui.json"):
            p = os.path.join(tk.KS, f); shutil.rmtree(p, ignore_errors=True) if os.path.isdir(p) else (os.path.exists(p) and os.remove(p))
        os.environ.pop("STUB_MODE", None)
        os.environ["STUB_OUT"] = os.path.join(self.d, "notes.apk")
        open(os.environ["STUB_OUT"], "w").write("apk")

    def tearDown(self):
        self.api.cancel(); shutil.rmtree(self.d, ignore_errors=True)

    def rec(self, name="notes", src=None, ver="1.2.3", patch="1"):
        os.makedirs(os.path.join(tk.KS, "ids"), exist_ok=True)
        open(os.path.join(tk.KS, "ids", name), "w").write("pkg=tkdroid.palalgon.%s\nvc=4\nname=Notes\nsrc=%s\nicon=\nver=%s\npatch=%s\n" % (name, src or self.script, ver, patch))

    # ---- the file chooser
    def test_pick_file_opens_the_chooser_and_describes_the_file(self):
        self.dialogs.answers = [self.script]
        r = self.api.pick_file()
        self.assertEqual((r["file"], r["name"], r["id"]), ("my_notes.py", "my_notes", ""))
        self.assertEqual(self.dialogs.calls[0][0][0], "Python files (*.py)"); self.assertFalse(self.dialogs.calls[0][1])
        self.assertEqual(json.load(open(gui.SETTINGS_FILE))["lastDir"], self.d)       # the chooser starts there next time

    def test_pick_file_cancelled_returns_none(self):
        self.assertIsNone(self.api.pick_file())

    def test_known_script_comes_back_with_its_app_id_and_version(self):
        self.rec()
        self.dialogs.answers = [self.script]
        r = self.api.pick_file()
        self.assertEqual((r["id"], r["name"], r["version"]), ("notes", "Notes", "1.2.3 Patch 1"))

    def test_use_path_rejects_wrong_files(self):
        self.assertIn("error", self.api.use_path(os.path.join(self.d, "nope.py")))
        txt = os.path.join(self.d, "a.txt"); open(txt, "w").write("x")
        self.assertIn("error", self.api.use_path(txt))
        self.assertEqual(self.api.use_path(self.script)["file"], "my_notes.py")

    def test_icon_and_folder_choosers(self):
        png = os.path.join(self.d, "i.png"); open(png, "w").write("x")
        self.dialogs.answers = [png, os.path.join(self.d, "readme.txt"), self.d]
        self.assertEqual(self.api.pick_icon(), {"path": png})
        self.assertIn("error", self.api.pick_icon())
        self.assertEqual(self.api.pick_folder(), {"path": self.d}); self.assertTrue(self.dialogs.calls[-1][1])
        self.assertEqual(self.api.startup()["outputDir"], self.d)

    def test_startup_preselects_a_script_and_can_ask_for_the_picker(self):
        api = gui.Api(script=self.script, pick=True, dialogs=self.dialogs, cli=self.stub)
        st = api.startup()
        self.assertTrue(st["pick"]); self.assertEqual(st["script"]["file"], "my_notes.py"); self.assertEqual(st["version"], tk.VERSION)

    # ---- building
    def test_build_streams_steps_and_ends_with_the_result(self):
        self.assertTrue(self.api.build({"src": self.script, "name": "My Notes"})["ok"])
        ev = wait(self.api, lambda seen, r: any(e["t"] in ("done", "error") for e in seen))
        steps = [e["id"] for e in ev if e["t"] == "step"]
        self.assertEqual([x for i, x in enumerate(steps) if i == 0 or x != steps[i - 1]],
                         ["transpile", "compile", "dex", "link", "package", "sign"])
        done = [e for e in ev if e["t"] == "done"][0]
        self.assertTrue(done["ok"]); self.assertEqual(done["path"], os.environ["STUB_OUT"]); self.assertIn("version 1.0.1", done["info"])
        args = next(e["text"] for e in ev if e["t"] == "log" and e["text"].startswith("- args:"))
        self.assertIn("build " + self.script, args); self.assertIn("-n My Notes", args)
        self.assertFalse(self.api.poll(0)["busy"]["work"])

    def test_build_passes_options_and_keeps_the_keystore_password_in_the_environment_only(self):
        ks = os.path.join(self.d, "up.jks"); open(ks, "w").write("k")
        icon = os.path.join(self.d, "i.png"); open(icon, "w").write("x")
        self.api.build({"src": self.script, "ident": "Notes App!", "bump": "small", "icon": icon, "format": "aab",
                        "keystore": ks, "ksAlias": "mykey", "ksPass": "s3cret"})
        ev = wait(self.api, lambda seen, r: any(e["t"] in ("done", "error") for e in seen))
        args = next(e["text"] for e in ev if e["t"] == "log" and e["text"].startswith("- args:"))
        for part in ("--id notes_app", "--small", "--icon " + icon, "--aab", "--keystore " + ks, "--ks-alias mykey"):
            self.assertIn(part, args)
        self.assertNotIn("s3cret", json.dumps(ev))                                   # never on a command line or in the log
        sha = next(e["text"] for e in ev if e.get("text", "").startswith("- ks-pass-sha:"))
        self.assertNotIn("e3b0c442", sha)                                              # (the stub did receive a non-empty password)

    def test_output_folder_setting_reaches_the_build(self):
        self.dialogs.answers = [self.d]; self.api.pick_folder()
        self.api.build({"src": self.script})
        ev = wait(self.api, lambda seen, r: any(e["t"] in ("done", "error") for e in seen))
        self.assertIn("- out-env: " + self.d, [e["text"] for e in ev if e["t"] == "log"])

    def test_failed_build_reports_the_tools_own_message(self):
        os.environ["STUB_MODE"] = "fail"
        self.api.build({"src": self.script})
        ev = wait(self.api, lambda seen, r: any(e["t"] in ("done", "error") for e in seen))
        err = [e for e in ev if e["t"] == "error"][0]
        self.assertEqual(err["message"], "compiling failed (see above)")
        self.assertIn("javac: some detail line", [e["text"] for e in ev if e["t"] == "log"])

    def test_bad_input_is_refused_before_anything_runs(self):
        cases = [({}, "choose"), ({"src": self.script, "ident": "a", "bump": "huge"}, "version"),
                 ({"src": self.script, "icon": "/nope.png"}, "icon"), ({"src": self.script, "format": "aab", "keystore": "/nope.jks"}, "keystore"),
                 ({"src": self.script, "format": "aab", "keystore": self.script}, "password"), ({"src": self.script, "targetSdk": "3;rm"}, "number")]
        for opts, word in cases:
            r = self.api.build(opts); self.assertFalse(r["ok"], opts); self.assertIn(word, r["error"])
        self.assertFalse(self.api.poll(0)["busy"]["work"])

    def test_only_one_job_at_a_time(self):
        os.environ["STUB_MODE"] = "hang"; os.environ["STUB_PIDFILE"] = os.path.join(self.d, "pid")
        self.assertTrue(self.api.build({"src": self.script})["ok"])
        self.assertFalse(self.api.build({"src": self.script})["ok"])
        self.api.cancel()
        wait(self.api, lambda seen, r: any(e["t"] == "error" for e in seen))

    @unittest.skipIf(WIN, "process groups")
    def test_cancel_stops_the_build_and_its_children(self):
        os.environ["STUB_MODE"] = "hang"; os.environ["STUB_PIDFILE"] = os.path.join(self.d, "pid")
        self.api.build({"src": self.script})
        end = time.time() + 10
        while not os.path.exists(os.environ["STUB_PIDFILE"]) or not open(os.environ["STUB_PIDFILE"]).read().strip():
            self.assertLess(time.time(), end); time.sleep(0.05)
        child = int(open(os.environ["STUB_PIDFILE"]).read())
        os.kill(child, 0)                                                              # alive
        self.api.cancel()
        ev = wait(self.api, lambda seen, r: any(e["t"] == "error" for e in seen))
        self.assertTrue([e for e in ev if e["t"] == "error"][0]["cancelled"])
        end = time.time() + 8
        while time.time() < end:
            try:
                os.kill(child, 0)
            except ProcessLookupError:
                break
            time.sleep(0.1)
        else:
            self.fail("the child process of the build is still running")

    # ---- updating apps
    def test_update_rebuilds_from_the_recorded_source(self):
        self.rec()
        self.assertTrue(self.api.update("notes", "medium")["ok"])
        ev = wait(self.api, lambda seen, r: any(e["t"] in ("done", "error") for e in seen))
        args = next(e["text"] for e in ev if e["t"] == "log" and e["text"].startswith("- args:"))
        self.assertIn("build " + self.script + " --id notes --medium", args)

    def test_update_with_missing_source_or_unknown_id_is_refused(self):
        self.rec(src="/definitely/not/here.py")
        self.assertIn("missing", self.api.update("notes")["error"])
        self.assertIn("unknown", self.api.update("ghost")["error"])
        apps = self.api.apps(); self.assertFalse(apps[0]["srcExists"]); self.assertEqual(apps[0]["version"], "1.2.3 Patch 1")

    # ---- check, doctor, page, misc
    def test_check_reports_ok_and_errors(self):
        r = self.api.check(self.script); self.assertTrue(r["ok"], r)
        bad = os.path.join(self.d, "bad.py"); open(bad, "w").write("def (:\n")
        r = self.api.check(bad); self.assertFalse(r["ok"]); self.assertTrue(r["error"])
        self.assertFalse(self.api.check("/nope.py")["ok"])

    def test_doctor_lists_required_and_optional_parts(self):
        d = self.api.doctor()
        ids = {i["id"]: i for i in d["items"]}
        for k in ("python", "javac", "aapt2", "d8", "apksigner", "android.jar", "adb", "bundletool"):
            self.assertIn(k, ids)
        self.assertTrue(ids["adb"]["optional"] and ids["bundletool"]["optional"] and not ids["javac"]["optional"])
        self.assertEqual(d["ready"], all(i["ok"] for i in d["items"] if not i["optional"]))

    def test_the_page_is_one_string_with_everything_in_it(self):
        html = gui.page_html()
        for key in ("CORE", "SHELL", "TERM"):
            self.assertNotIn("/*@%s@*/" % key, html)
        self.assertIn("const Core", html); self.assertIn("const Shell", html); self.assertIn("pywebview.api", html)
        self.assertNotIn("http://", html.replace("http://www.w3.org", ""))             # nothing is loaded from anywhere

    def test_nothing_private_is_published_to_the_page(self):
        public = [n for n in dir(self.api) if not n.startswith("_")]
        for bad in ("window", "dialogs", "lock", "events", "jobs", "cli", "script"):
            self.assertNotIn(bad, public)

    def test_terminal_folders_cd_ls_resolve_and_completion(self):
        os.makedirs(os.path.join(self.d, "sub dir"))
        open(os.path.join(self.d, "other.txt"), "w").close()
        open(os.path.join(self.d, ".hidden.py"), "w").close()
        self.assertTrue(self.api.cd(self.d)["ok"])
        self.assertEqual(self.api.pwd()["cwd"], self.d)
        self.assertEqual(self.api.resolve("my_notes.py"), self.script)
        self.assertEqual(self.api.resolve("sub dir/../my_notes.py"), self.script)
        self.assertEqual(self.api.resolve("~"), os.path.expanduser("~"))
        names = [(e["name"], e["dir"]) for e in self.api.ls()["entries"]]
        self.assertEqual(names[0], ("sub dir", True))                                    # folders first
        self.assertNotIn(".hidden.py", [n for n, _ in names])
        self.assertFalse(self.api.ls("nowhere")["ok"])
        self.assertFalse(self.api.cd("nowhere")["ok"])
        self.assertEqual(self.api.complete_path("my", [".py"]), ["my_notes.py"])
        self.assertIn("sub dir" + os.sep, self.api.complete_path("su", [".py"]))
        self.assertEqual(self.api.complete_path("o", [".py"]), [])                       # other.txt is not a .py
        self.assertEqual(self.api.complete_path("su", [], True), ["sub dir" + os.sep])
        self.assertEqual(self.api.complete_path("my", [], True), [])                     # dirs only
        # a typed relative path works for choosing a script
        self.assertEqual(self.api.use_path("my_notes.py")["path"], self.script)
        self.assertIn("error", self.api.use_path("other.txt"))
        # the output folder
        out = os.path.join(self.d, "out"); os.makedirs(out)
        self.assertEqual(self.api.outdir(out)["dir"], out)
        self.assertEqual(self.api.outdir()["dir"], out)
        self.assertFalse(self.api.outdir(os.path.join(self.d, "nope"))["ok"])

    def test_startup_tells_the_terminal_where_it_is(self):
        st = self.api.startup()
        self.assertTrue(os.path.isdir(st["cwd"]))
        self.assertEqual((st["home"], st["sep"]), (os.path.expanduser("~"), os.sep))

    def test_install_on_phone_refuses_a_non_apk(self):
        self.assertFalse(self.api.install_on_phone("/nope.apk")["ok"])
        self.assertFalse(self.api.install_on_phone(self.script)["ok"])

    def test_line_classification(self):
        c = gui.classify_line
        self.assertEqual([c(x) for x in ("- transpiling a.py", "- compiling Java", "- dexing", "- adding icon", "- linking manifest",
                                         "- packaging the base module", "- building the bundle", "- signing with the upload key", "- built: x")],
                         ["transpile", "compile", "dex", "link", "link", "package", "bundle", "sign", None])


if __name__ == "__main__":
    unittest.main()
