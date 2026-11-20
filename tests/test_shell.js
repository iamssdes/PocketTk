// node tests/test_shell.js  - the command language of the window (no DOM)
const assert = require("assert");
const Shell = require("../webui/shell.js");
const eq = assert.deepStrictEqual;

// tokens: quotes group, backslashes stay (Windows paths)
eq(Shell.tokenize('build "my app.py" --id x').map(t => t.value), ["build", "my app.py", "--id", "x"]);
eq(Shell.tokenize("build 'a b.py'").map(t => t.value), ["build", "a b.py"]);
eq(Shell.tokenize("use C:\\Users\\me\\app.py").map(t => t.value), ["use", "C:\\Users\\me\\app.py"]);
eq(Shell.tokenize("   ").length, 0);
assert.strictEqual(Shell.tokenize('x "open').pop().open, true);

// parsing
let r = Shell.parse("build notes.py --id notes --small -n \"My Notes\" --aab --icon i.png");
eq([r.cmd, r.pos, r.bump], ["build", ["notes.py"], "small"]);
eq(r.opts, { id: "notes", name: "My Notes", aab: true, icon: "i.png" });
r = Shell.parse("build --id=abc");
eq(r.opts, { id: "abc" }); eq(r.pos, []);
eq(Shell.parse("ids").cmd, "apps");
eq(Shell.parse("quit").cmd, "exit");
eq(Shell.parse("").empty, true);
assert.ok(/command not found/.test(Shell.parse("frobnicate").error));
assert.ok(/unknown option --nope/.test(Shell.parse("build --nope").error));
assert.ok(/unknown option -x/.test(Shell.parse("build -x").error));
assert.ok(/needs a value/.test(Shell.parse("build --id").error));
assert.ok(/takes no value/.test(Shell.parse("build --aab=1").error));
assert.ok(/missing app id/.test(Shell.parse("update").error));
assert.ok(/usage: update ID/.test(Shell.parse("update").error));
assert.ok(/too many/.test(Shell.parse("doctor now").error));
eq(Shell.parse("build -- --odd.py").pos, ["--odd.py"]);
eq(Shell.parse("update notes --medium").bump, "medium");

// completion
let c = Shell.completion("bu", {});
eq([c.kind, c.start, c.items], ["list", 0, ["build"]]);
c = Shell.completion("", {}); assert.ok(c.items.includes("build") && c.items.includes("doctor") && !c.items.includes("?"));
c = Shell.completion("build no", {}); eq([c.kind, c.exts, c.word, c.start], ["path", [".py"], "no", 6]);
c = Shell.completion("build x.py --ic", {}); eq(c.items, ["--icon"]);
c = Shell.completion("build x.py --icon ", {}); eq([c.kind, c.exts[0]], ["path", ".png"]);
c = Shell.completion("build x.py --id ", {}); eq(c, null);
c = Shell.completion("update n", { ids: ["notes", "calc", "nz"] }); eq(c.items, ["notes", "nz"]);
c = Shell.completion("cd ", {}); eq([c.kind, c.dirsOnly], ["path", true]);
c = Shell.completion("theme p", {}); eq(c.items, ["paper"]);
c = Shell.completion("pick i", {}); eq(c.items, ["icon"]);
c = Shell.completion("help bu", {}); eq(c.items, ["build"]);
c = Shell.completion("nonsense x", {}); eq(c, null);
c = Shell.completion('build "my a', {}); eq([c.word, c.start], ["my a", 6]);

eq(Shell.commonPrefix(["notes.py", "notes2.py", "noted"]), "note");
eq(Shell.commonPrefix(["a"]), "a");
eq(Shell.commonPrefix([]), "");
assert.strictEqual(Shell.applyCompletion("build no", { start: 6 }, "notes.py", true), "build notes.py ");
assert.strictEqual(Shell.applyCompletion("build ", { start: 6 }, "my app.py", true), 'build "my app.py" ');
assert.strictEqual(Shell.applyCompletion("cd a", { start: 3 }, "apps/", true), "cd apps/");

// output helpers
eq(Shell.tilde("/home/me/apps", "/home/me"), "~/apps");
eq(Shell.tilde("/home/meow", "/home/me"), "/home/meow");
eq(Shell.tilde("C:\\Users\\me\\x", "C:\\Users\\me"), "~\\x");
eq(Shell.padRows([["a", "bb", "c"], ["aaa", "b", "d"]]), ["a    bb  c", "aaa  b   d"]);
eq(Shell.bar(50, 10), "[=====     ]  50%");
eq(Shell.bar(100, 4), "[====] 100%");
eq(Shell.bar(5, 4), "[    ]   5%");
eq([Shell.classifyLog("warning: x"), Shell.classifyLog("error: boom"), Shell.classifyLog("- built: /a.apk"), Shell.classifyLog("- dexing")], ["warn", "bad", "good", ""]);
assert.ok(Shell.helpText().some(l => /^  build /.test(l)));
assert.ok(Shell.helpFor("build").some(l => /--aab/.test(l)));
assert.ok(/no command called/.test(Shell.helpFor("zzz")[0]));
Object.keys(Shell.COMMANDS).forEach(n => { assert.ok(Shell.COMMANDS[n].sum, n + " has no summary"); assert.ok(Array.isArray(Shell.COMMANDS[n].pos)); });
console.log("shell.js: all tests passed");
