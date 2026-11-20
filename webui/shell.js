/* PocketTk window: the command language. No DOM in here, so it can be tested with node (tests/test_shell.js). */
const Shell = (function () {
  "use strict";

  const THEMES = ["dusk", "green", "paper", "mono"];
  const BUMPS = ["patch", "small", "medium", "large"];

  /* name -> { args (usage), sum (one line), pos [min,max] positionals, opts {flag: type}, long (help text) }.
     Types: "bool", "str" (a value), "file" (a path to a file), "dir", "exts:.a,.b" (a path with these endings). */
  const BUMP_FLAGS = { patch: "bool", small: "bool", medium: "bool", large: "bool" };
  function flags(extra) { return Object.assign({}, extra); }

  const COMMANDS = {
    help: { args: "[command]", sum: "list the commands, or explain one", pos: [0, 1], opts: {}, pathPos: "cmd" },
    pick: { args: "[icon|keystore|folder]", sum: "choose a file in the system file chooser (default: a Python script)", pos: [0, 1], opts: {}, words: ["icon", "keystore", "folder"] },
    use: { args: "FILE", sum: "choose the script the next commands work on (dropping a .py on the window does the same)", pos: [1, 1], opts: {}, pathPos: "exts:.py" },
    build: {
      args: "[FILE] [-n NAME] [--id ID] [--icon IMG] [--aab] [--patch|--small|--medium|--large]",
      sum: "transpile and export an APK (or a Google Play bundle with --aab)", pos: [0, 1], pathPos: "exts:.py",
      opts: flags(Object.assign({ name: "str", id: "str", icon: "exts:.png,.jpg,.jpeg,.webp", aab: "bool", apk: "bool", keystore: "exts:.jks,.keystore,.p12,.pfx", alias: "str", "target-sdk": "str" }, BUMP_FLAGS)),
      short: { n: "name" },
      long: [
        "  -n, --name NAME     the app's name on the phone (default: the file name, or the name it already has)",
        "      --id ID         give the app an id: the version then counts up with every build, and `update ID` works",
        "      --icon IMG      a .png, .jpg or .webp picture",
        "      --aab           build a Google Play bundle instead of an APK",
        "      --keystore FILE sign the bundle with your own key (the password is asked for; --alias NAME if needed)",
        "      --target-sdk N  Android target API level",
        "      --patch|--small|--medium|--large   how the version changes (with --id; default --patch)",
        "",
        "  examples:  build notes.py",
        "             build notes.py --id notes --small -n \"My Notes\"",
        "             build notes.py --aab --id notes",
      ],
    },
    check: { args: "[FILE]", sum: "transpile only: show warnings, build nothing", pos: [0, 1], opts: {}, pathPos: "exts:.py" },
    preview: { args: "[FILE] [--width N] [--height N]", sum: "run the script in a phone-shaped window on this computer", pos: [0, 1], pathPos: "exts:.py", opts: { width: "str", height: "str" } },
    install: { args: "[FILE.apk]", sum: "install an APK on a phone connected with USB (default: the last one built)", pos: [0, 1], opts: {}, pathPos: "exts:.apk" },
    devices: { args: "", sum: "list the phones adb can see", pos: [0, 0], opts: {} },
    update: {
      args: "ID [--patch|--small|--medium|--large]", sum: "rebuild an app that has an id; its version goes up", pos: [1, 1], opts: flags(Object.assign({ src: "exts:.py" }, BUMP_FLAGS)), pathPos: "id",
      long: ["  ID is one of the ids `apps` lists. --src FILE rebuilds from another file."],
    },
    apps: { args: "", sum: "the apps you built with an id: version, package, source file", pos: [0, 0], opts: {} },
    doctor: { args: "", sum: "check the build tools", pos: [0, 0], opts: {} },
    setup: { args: "", sum: "install what is missing (Java JDK, Android build tools) into ~/.pockettk", pos: [0, 0], opts: {} },
    rosetta: { args: "", sum: "Apple Silicon only: install Rosetta (Google's tools may need it)", pos: [0, 0], opts: {} },
    reveal: { args: "[PATH]", sum: "show a file in the file manager (default: the last build)", pos: [0, 1], opts: {}, pathPos: "any" },
    outdir: { args: "[FOLDER]", sum: "show or change where builds are written", pos: [0, 1], opts: {}, pathPos: "dir" },
    cd: { args: "[FOLDER]", sum: "change the current folder", pos: [0, 1], opts: {}, pathPos: "dir" },
    ls: { args: "[PATH]", sum: "list a folder", pos: [0, 1], opts: {}, pathPos: "any" },
    pwd: { args: "", sum: "print the current folder", pos: [0, 0], opts: {} },
    cancel: { args: "", sum: "stop what is running (Ctrl+C does the same)", pos: [0, 0], opts: {} },
    clear: { args: "", sum: "clear the screen (Ctrl+L)", pos: [0, 0], opts: {} },
    theme: { args: "[NAME]", sum: "colours: " + THEMES.join(", "), pos: [0, 1], opts: {}, words: THEMES },
    history: { args: "", sum: "the commands you typed", pos: [0, 0], opts: {} },
    about: { args: "", sum: "what this is", pos: [0, 0], opts: {} },
    version: { args: "", sum: "print the version", pos: [0, 0], opts: {} },
    exit: { args: "", sum: "close the window", pos: [0, 0], opts: {}, alias: ["quit"] },
  };
  const ALIASES = { quit: "exit", ids: "apps", ll: "ls", dir: "ls", "?": "help" };

  function commandName(word) {
    if (COMMANDS[word]) return word;
    return ALIASES[word] || null;
  }

  /* "build \"my app.py\" --id x" -> [{value, start, end, open}]. Quotes group words; a backslash is just a character
     (Windows paths), so a path with spaces needs quotes. */
  function tokenize(line) {
    const out = [];
    let i = 0;
    const n = line.length;
    while (i < n) {
      while (i < n && (line[i] === " " || line[i] === "\t")) i++;
      if (i >= n) break;
      const start = i;
      let val = "", quote = null;
      while (i < n) {
        const c = line[i];
        if (quote) { if (c === quote) quote = null; else val += c; i++; continue; }
        if (c === '"' || c === "'") { quote = c; i++; continue; }
        if (c === " " || c === "\t") break;
        val += c; i++;
      }
      out.push({ value: val, start: start, end: i, open: !!quote });
    }
    return out;
  }

  function quoteIfNeeded(s) {
    return /[\s"']/.test(s) ? (s.indexOf('"') < 0 ? '"' + s + '"' : "'" + s + "'") : s;
  }

  /* Tokens -> { cmd, pos[], opts{}, bump, error }. Errors read like a terminal's: short, with the usage. */
  function parse(line) {
    const toks = tokenize(line);
    if (!toks.length) return { empty: true };
    const word = toks[0].value;
    const cmd = commandName(word);
    if (!cmd) return { error: "pockettk: " + word + ": command not found. Type help." };
    const spec = COMMANDS[cmd];
    const r = { cmd: cmd, pos: [], opts: {}, bump: "" };
    for (let i = 1; i < toks.length; i++) {
      let t = toks[i].value;
      if (t === "--") { for (i++; i < toks.length; i++) r.pos.push(toks[i].value); break; }
      if (t.length > 1 && t[0] === "-") {
        let name, val = null;
        if (t.slice(0, 2) === "--") {
          const eq = t.indexOf("=");
          name = eq > 0 ? t.slice(2, eq) : t.slice(2);
          if (eq > 0) val = t.slice(eq + 1);
        } else {
          name = (spec.short || {})[t.slice(1)] || null;
          if (!name) return { error: cmd + ": unknown option " + t + usage(cmd) };
        }
        const type = spec.opts[name];
        if (!type) return { error: cmd + ": unknown option --" + name + usage(cmd) };
        if (type === "bool") {
          if (val !== null) return { error: cmd + ": --" + name + " takes no value" };
          if (BUMPS.indexOf(name) >= 0) r.bump = name; else r.opts[name] = true;
        } else {
          if (val === null) {
            if (i + 1 >= toks.length) return { error: cmd + ": --" + name + " needs a value" + usage(cmd) };
            val = toks[++i].value;
          }
          r.opts[name] = val;
        }
        continue;
      }
      r.pos.push(t);
    }
    if (r.pos.length < spec.pos[0]) return { error: cmd + ": missing " + (cmd === "update" ? "app id" : "argument") + usage(cmd) };
    if (r.pos.length > spec.pos[1]) return { error: cmd + ": too many arguments" + usage(cmd) };
    return r;
  }

  function usage(cmd) { return "\nusage: " + cmd + (COMMANDS[cmd].args ? " " + COMMANDS[cmd].args : ""); }

  function commonPrefix(list) {
    if (!list.length) return "";
    let p = list[0];
    for (let i = 1; i < list.length; i++) { while (list[i].indexOf(p) !== 0) p = p.slice(0, -1); }
    return p;
  }

  /* What can Tab do at the end of `line`? ctx: { ids: [app ids] }.
     -> { start, word, kind: "list", items: [...] }  or  { start, word, kind: "path", exts: [...], dirsOnly }  or null. */
  function completion(line, ctx) {
    const toks = tokenize(line);
    const endsOpen = /\s$/.test(line) || !toks.length;
    const cur = endsOpen ? { value: "", start: line.length } : toks[toks.length - 1];
    const prev = endsOpen ? toks : toks.slice(0, -1);
    const word = cur.value, start = cur.start;
    if (!prev.length) {
      const names = Object.keys(COMMANDS).concat(Object.keys(ALIASES).filter(function (a) { return a !== "?"; }));
      return { start: start, word: word, kind: "list", items: names.filter(function (n) { return n.indexOf(word) === 0; }).sort() };
    }
    const cmd = commandName(prev[0].value);
    if (!cmd) return null;
    const spec = COMMANDS[cmd];
    const last = prev[prev.length - 1].value;
    if (prev.length > 1 && last.slice(0, 2) === "--" && last.indexOf("=") < 0) {
      const type = spec.opts[last.slice(2)];
      if (type && type !== "bool") return typeCompletion(type, start, word);
    }
    if (word[0] === "-") {
      const all = Object.keys(spec.opts).map(function (o) { return "--" + o; });
      return { start: start, word: word, kind: "list", items: all.filter(function (o) { return o.indexOf(word) === 0; }).sort() };
    }
    if (spec.words) return { start: start, word: word, kind: "list", items: spec.words.filter(function (w) { return w.indexOf(word) === 0; }) };
    if (spec.pathPos === "cmd") return { start: start, word: word, kind: "list", items: Object.keys(COMMANDS).filter(function (n) { return n.indexOf(word) === 0; }).sort() };
    if (spec.pathPos === "id") return { start: start, word: word, kind: "list", items: ((ctx && ctx.ids) || []).filter(function (n) { return n.indexOf(word) === 0; }) };
    if (spec.pathPos) return typeCompletion(spec.pathPos, start, word);
    return null;
  }

  function typeCompletion(type, start, word) {
    if (type === "dir") return { start: start, word: word, kind: "path", exts: [], dirsOnly: true };
    if (type === "any" || type === "str") return type === "any" ? { start: start, word: word, kind: "path", exts: [], dirsOnly: false } : null;
    if (type.indexOf("exts:") === 0) return { start: start, word: word, kind: "path", exts: type.slice(5).split(","), dirsOnly: false };
    return null;
  }

  /* Apply a chosen completion: replace the word with `text` (quoted when it has spaces). */
  function applyCompletion(line, c, text, addSpace) {
    const done = text.slice(-1) === "/" || text.slice(-1) === "\\";
    return line.slice(0, c.start) + quoteIfNeeded(text) + (addSpace && !done ? " " : "");
  }

  function helpText() {
    const names = Object.keys(COMMANDS);
    const w = Math.max.apply(null, names.map(function (n) { return n.length; })) + 2;
    const rows = names.map(function (n) { return "  " + (n + "            ").slice(0, w) + COMMANDS[n].sum; });
    return ["PocketTk turns a tkinter Python script into an Android app. Quick start:",
      "",
      "  pick                   choose your script (or drop it on this window)",
      "  build                  make the app",
      "",
      "Commands:"].concat(rows, ["", "Tab completes, Up/Down shows history, Ctrl+C stops a build, `help COMMAND` explains one."]);
  }

  function helpFor(name) {
    const cmd = commandName(name);
    if (!cmd) return ["help: no command called " + name];
    const spec = COMMANDS[cmd];
    return ["usage: " + cmd + (spec.args ? " " + spec.args : ""), "  " + spec.sum].concat(spec.long ? ["", ].concat(spec.long) : []);
  }

  /* "~/a/b/c" style short path for the prompt. */
  function tilde(path, home) {
    if (!path) return "";
    if (home && (path === home || path.indexOf(home + "/") === 0 || path.indexOf(home + "\\") === 0)) return "~" + path.slice(home.length);
    return path;
  }

  function padRows(rows, gap) {
    const w = [];
    rows.forEach(function (r) { r.forEach(function (c, i) { w[i] = Math.max(w[i] || 0, String(c).length); }); });
    return rows.map(function (r) { return r.map(function (c, i) { return i === r.length - 1 ? String(c) : (String(c) + " ".repeat(w[i] + (gap || 2))).slice(0, w[i] + (gap || 2)); }).join(""); });
  }

  /* A text progress bar: [=====     ]  45% */
  function bar(pct, width) {
    width = width || 24;
    const n = Math.round(Math.max(0, Math.min(100, pct)) * width / 100);
    return "[" + "=".repeat(n) + " ".repeat(width - n) + "] " + (pct < 10 ? "  " : pct < 100 ? " " : "") + pct + "%";
  }

  function classifyLog(text) {
    if (/^(error|pockettk: .*(failed|cannot|could not|error))/i.test(text) || /\bFAILED\b/.test(text)) return "bad";
    if (/^warning:/i.test(text)) return "warn";
    if (/^- built:/.test(text)) return "good";
    return "";
  }

  return { COMMANDS: COMMANDS, THEMES: THEMES, BUMPS: BUMPS, tokenize: tokenize, parse: parse, completion: completion, applyCompletion: applyCompletion,
           commonPrefix: commonPrefix, quoteIfNeeded: quoteIfNeeded, helpText: helpText, helpFor: helpFor, tilde: tilde, padRows: padRows,
           bar: bar, classifyLog: classifyLog, commandName: commandName };
})();

if (typeof module !== "undefined" && module.exports) module.exports = Shell;
