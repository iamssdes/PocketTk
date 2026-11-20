// node tests/test_core.js  - the logic of the window that has no DOM in it
const assert = require("assert");
const Core = require("../webui/core.js");

// steps for the two formats
assert.deepStrictEqual(Core.stepsFor("apk").map(s => s.id), ["transpile", "compile", "dex", "link", "package", "sign"]);
assert.deepStrictEqual(Core.stepsFor("aab").map(s => s.id), ["transpile", "compile", "dex", "link", "package", "bundle", "sign"]);
assert.deepStrictEqual(Core.stepsFor("setup").map(s => s.id), ["jdk", "sdk", "bundletool"]);

// a build: a step marks the earlier ones done
let j = Core.newJob("apk");
Core.applyEvent(j, { t: "step", id: "dex", state: "running" });
assert.deepStrictEqual(j.steps.map(s => s.state), ["done", "done", "running", "todo", "todo", "todo"]);
Core.applyEvent(j, { t: "log", text: "hello" });
Core.applyEvent(j, { t: "status", text: "working" });
assert.strictEqual(j.log[0], "hello"); assert.strictEqual(j.status, "working");
Core.applyEvent(j, { t: "done", ok: true, path: "/x/a.apk" });
assert.ok(j.steps.every(s => s.state === "done")); assert.strictEqual(j.running, false); assert.strictEqual(j.result.path, "/x/a.apk");

// a failed build marks the running step
j = Core.newJob("apk");
Core.applyEvent(j, { t: "step", id: "compile" });
Core.applyEvent(j, { t: "error", message: "javac failed" });
assert.strictEqual(j.steps[1].state, "error"); assert.strictEqual(j.error, "javac failed"); assert.strictEqual(j.running, false);

// setup steps are set explicitly, in any order
j = Core.newJob("setup");
Core.applyEvent(j, { t: "step", id: "sdk", state: "running" });
assert.deepStrictEqual(j.steps.map(s => s.state), ["todo", "running", "todo"]);
Core.applyEvent(j, { t: "license", text: "LICENSE" });
assert.strictEqual(j.license, "LICENSE");
Core.applyEvent(j, { t: "progress", done: 50, total: 200, label: "Java JDK" });
assert.strictEqual(Core.percent(j.progress), 25);
Core.applyEvent(j, { t: "error", message: "Cancelled." });
assert.strictEqual(j.license, null);

// unknown step ids are ignored; the log is capped
j = Core.newJob("apk");
Core.applyEvent(j, { t: "step", id: "nope" });
assert.ok(j.steps.every(s => s.state === "todo"));
for (let i = 0; i < 3500; i++) Core.applyEvent(j, { t: "log", text: "l" + i });
assert.strictEqual(j.log.length, 3000); assert.strictEqual(j.log[2999], "l3499");

// numbers and names
assert.strictEqual(Core.percent({ done: 1, total: 0 }), null);
assert.strictEqual(Core.percent({ done: 300, total: 100 }), 100);
assert.strictEqual(Core.formatBytes(512), "512 B"); assert.strictEqual(Core.formatBytes(1536), "1.5 KB"); assert.strictEqual(Core.formatBytes(150 * 1024 * 1024), "150 MB");
assert.strictEqual(Core.defaultName("my_cool-app.py"), "my cool app");
assert.strictEqual(Core.shorten("/a/b/c"), "/a/b/c"); assert.ok(Core.shorten("x".repeat(200), 40).length <= 40);

// the same version rules as the command line tool
assert.strictEqual(Core.nextVersion("1.2.3", "patch"), "1.2.3 Patch 1");
assert.strictEqual(Core.nextVersion("1.2.3 Patch 2", "patch"), "1.2.3 Patch 3");
assert.strictEqual(Core.nextVersion("1.2.3 Patch 2", "small"), "1.2.4");
assert.strictEqual(Core.nextVersion("1.2.3 Patch 2", "medium"), "1.3.0");
assert.strictEqual(Core.nextVersion("1.2.3", "large"), "2.0.0");
assert.strictEqual(Core.nextVersion("garbage", "small"), "");
console.log("core.js: all tests passed");
