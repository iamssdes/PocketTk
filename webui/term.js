/* PocketTk window: the terminal page. Talks to Python through window.pywebview.api (see pockettk_gui.py).
   The command language (parsing, completion, help text) is in shell.js; this file is the screen and the glue. */
(function () {
  "use strict";

  const $ = function (id) { return document.getElementById(id); };
  const out = $("out"), live = $("live"), inp = $("inp"), screen = $("screen");

  const LOGO = '<svg class="logo" viewBox="0 0 512 512" role="img" aria-label="PocketTk"><rect x="16" y="16" width="480" height="480" rx="112" fill="#f3ede1"/>' +
    '<rect x="156" y="56" width="200" height="330" rx="40" fill="#222a4d"/><rect x="176" y="80" width="160" height="260" rx="22" fill="#f3ede1"/>' +
    '<g fill="none" stroke-width="19" stroke-linecap="round" stroke-linejoin="round"><path d="M190 124 H238 M214 124 V212" stroke="#222a4d"/>' +
    '<path d="M268 112 V212 M312 146 L268 176 M284 166 L314 212" stroke="#e8744f"/></g>' +
    '<path d="M84 262 H428 V386 Q428 438 376 438 H136 Q84 438 84 386 Z" fill="#e8744f"/>' +
    '<path d="M106 288 H406" stroke="#f3ede1" stroke-width="5" stroke-linecap="round" stroke-dasharray="14 12" fill="none"/></svg>';

  const MAX_LINES = 4000;
  const S = {
    api: null, startup: {}, cwd: "", home: "", sep: "/", script: null, lastApk: "", lastPkg: "", lastBuild: "",
    cursor: 0, busy: {}, fg: "", history: [], hpos: 0, draft: "", apps: [], mode: null, lastTab: "", licenseAsked: false, ready: true,
  };

  // ------------------------------------------------------------ small helpers
  function store(key, val) { try { if (val === undefined) return window.localStorage.getItem(key); window.localStorage.setItem(key, val); } catch (e) { /* no storage: fine */ } return null; }

  function call(name) {
    const args = Array.prototype.slice.call(arguments, 1);
    return Promise.resolve().then(function () { return S.api[name].apply(S.api, args); });
  }

  function toBottom() { screen.scrollTop = screen.scrollHeight; }

  /* One output line. parts: a string, or [[text, class, onclick?], ...]. */
  function line(parts, cls) {
    const d = document.createElement("div");
    d.className = "ln" + (cls ? " " + cls : "");
    if (typeof parts === "string") d.textContent = parts;
    else parts.forEach(function (p) {
      const sp = document.createElement("span");
      sp.textContent = p[0];
      if (p[1]) sp.className = p[1];
      if (p[2]) { sp.classList.add("link"); sp.addEventListener("click", p[2]); }
      d.appendChild(sp);
    });
    out.appendChild(d);
    while (out.childNodes.length > MAX_LINES) out.removeChild(out.firstChild);
    toBottom();
    return d;
  }
  function lines(arr, cls) { arr.forEach(function (l) { line(l, cls); }); }
  function err(msg) { lines(String(msg).split("\n"), "bad"); }

  function setLive(text) { live.textContent = text || ""; toBottom(); }

  // ------------------------------------------------------------ the prompt
  function promptText() {
    if (S.mode) return [[S.mode.prompt, "acc"]];
    const where = Shell.tilde(S.cwd, S.home);
    const parts = [[where, "cwd"]];
    if (S.script) parts.push([" [" + S.script.file + "]", "dim"]);
    parts.push([" $ ", ""]);
    return parts;
  }

  function renderPrompt() {
    const ps = $("ps");
    ps.textContent = "";
    promptText().forEach(function (p) { const sp = document.createElement("span"); sp.textContent = p[0]; if (p[1]) sp.className = p[1]; ps.appendChild(sp); });
    ps.className = "ps";
    inp.type = S.mode && S.mode.secret ? "password" : "text";
  }

  function echo(text) {
    const parts = promptText().map(function (p) { return [p[0], p[1] === "cwd" ? "ps cwd" : "ps"]; });
    line(parts.concat([[text, ""]]));
  }

  function setBusy(kind) {
    S.fg = kind || "";
    document.body.classList.toggle("busy", !!kind);
    if (!kind) inp.focus();
  }

  /* Ask a question on the prompt line; resolves with the typed text. */
  function ask(prompt, secret) {
    return new Promise(function (resolve) {
      S.mode = { prompt: prompt, secret: !!secret, done: resolve };
      document.body.classList.remove("busy");
      renderPrompt();
      inp.focus();
    });
  }

  function endMode(value) {
    const m = S.mode;
    S.mode = null;
    renderPrompt();
    if (m) m.done(value);
  }

  // ------------------------------------------------------------ scripts and jobs
  function setScript(d) {
    S.script = d;
    line([["using ", "dim"], [d.path, "acc"]]);
    if (d.id) line("  already app '" + d.id + "', version " + d.version, "dim");
    renderPrompt();
  }

  async function needScript(arg) {
    if (arg) {
      const d = await call("use_path", arg);
      if (d.error) throw new Error(d.error + (d.error === "that file does not exist" ? ": " + arg : ""));
      if (!S.script || S.script.path !== d.path) setScript(d);
      else S.script = d;
      return d;
    }
    if (S.script) return S.script;
    throw new Error("no script chosen. Type pick, or give a file: build FILE");
  }

  function started(res, what) {
    if (!res || !res.ok) { err((res && res.error) || ("could not start " + what)); return false; }
    return true;
  }

  function jobStart(kind) {
    S.licenseAsked = false;
    if (kind === "preview") return;
    setBusy(kind);
    if (kind === "setup") line("== setup: Java JDK, Android build tools", "acc");
  }

  function jobEnd() {
    setLive("");
    setBusy("");
  }

  function route(ev) {
    const kind = ev.job;
    switch (ev.t) {
      case "start": jobStart(kind); break;
      case "log": line(ev.text, Shell.classifyLog(ev.text)); break;
      case "status": setLive(ev.text); break;
      case "progress": {
        const pct = Core.percent({ done: ev.done, total: ev.total });
        const bytes = ev.total ? "  " + Core.formatBytes(ev.done) + " of " + Core.formatBytes(ev.total) : "";
        setLive((ev.label ? ev.label + "\n" : "") + (pct != null ? Shell.bar(pct) : "") + bytes);
        break;
      }
      case "license": if (!S.licenseAsked) { S.licenseAsked = true; askLicense(ev.text); } break;
      case "done": jobDone(kind, ev); break;
      case "error":
        jobEnd();
        if (ev.cancelled) line("^C  cancelled", "warn"); else err("error: " + ev.message);
        if (kind === "setup") refreshReady();
        break;
    }
  }

  function askLicense(text) {
    line("");
    lines(String(text).split("\n"), "dim");
    line("");
    ask("Accept the Android SDK license? [y/N] ").then(function (a) {
      const yes = /^y(es)?$/i.test(a.trim());
      line([["Accept the Android SDK license? [y/N] ", "ps"], [a, ""]]);
      call("answer_license", yes);
      if (!yes) { call("cancel"); }
      document.body.classList.add("busy");
    });
  }

  function jobDone(kind, ev) {
    jobEnd();
    if (kind === "build") {
      S.lastBuild = ev.path;
      if (!ev.aab) { S.lastApk = ev.path; S.lastPkg = ev.pkg || ""; }
      line("");
      line([[ev.aab ? "bundle  " : "built   ", "good"], [ev.path, "", function () { call("reveal", ev.path); }]]);
      if (ev.info) line("        " + ev.info, "dim");
      refreshApps();
      if (!ev.aab) {
        call("devices").then(function (d) {
          if (d && d.devices && d.devices.length) line("        " + d.devices.length + " phone" + (d.devices.length > 1 ? "s" : "") + " connected: type install", "dim");
          else line("        install on a phone: connect it with USB, then type install", "dim");
        }).catch(function () {});
      }
    } else if (kind === "setup") {
      line("setup finished.", "good");
      refreshReady();
    } else if (kind === "install") {
      line("installed on the phone.", "good");
    } else if (kind === "preview") {
      line("preview closed.", "dim");
    } else if (kind === "rosetta") {
      line("Rosetta installed. Type doctor to check.", "good");
    }
  }

  function poll() {
    call("poll", S.cursor).then(function (r) {
      S.cursor = r.next;
      S.busy = r.busy || {};
      (r.events || []).forEach(route);
      if (S.fg && !S.busy.work && !S.mode) setBusy("");
    }).catch(function () { /* the window is closing */ });
  }

  function refreshApps() { return call("apps").then(function (a) { S.apps = a || []; }).catch(function () {}); }
  function refreshReady() { return call("doctor").then(function (d) { S.ready = d.ready; }).catch(function () {}); }

  // ------------------------------------------------------------ commands
  const cmds = {
    help: async function (r) { lines(r.pos[0] ? Shell.helpFor(r.pos[0]) : Shell.helpText()); },

    pick: async function (r) {
      const what = r.pos[0] || "";
      if (what === "icon") {
        const x = await call("pick_icon");
        if (!x) return line("nothing chosen", "dim");
        if (x.error) return err(x.error);
        line([["icon: ", "dim"], [Shell.quoteIfNeeded(x.path), "acc"]]);
        return line("use it with:  build --icon " + Shell.quoteIfNeeded(x.path), "dim");
      }
      if (what === "keystore") {
        const x = await call("pick_keystore");
        if (!x) return line("nothing chosen", "dim");
        line([["keystore: ", "dim"], [Shell.quoteIfNeeded(x.path), "acc"]]);
        return line("use it with:  build --aab --keystore " + Shell.quoteIfNeeded(x.path), "dim");
      }
      if (what === "folder") {
        const x = await call("pick_folder");
        if (!x) return line("nothing chosen", "dim");
        return line([["builds go to ", "dim"], [x.path, "acc"]]);
      }
      if (what) throw new Error("pick: expected icon, keystore or folder");
      const d = await call("pick_file");
      if (!d) return line("nothing chosen", "dim");
      if (d.error) return err(d.error);
      setScript(d);
    },

    use: async function (r) { await needScript(r.pos[0]); },

    check: async function (r) {
      const d = await needScript(r.pos[0]);
      setLive("checking " + d.file + " ...");
      const x = await call("check", d.path);
      setLive("");
      if (!x.ok) { (x.warnings || []).forEach(function (w) { line("warning: " + w, "warn"); }); return err("error: " + (x.error || "the script could not be transpiled")); }
      if (!(x.warnings || []).length) line("no problems found.", "good");
      (x.warnings || []).forEach(function (w) { line("warning: " + w, "warn"); });
      (x.notes || []).forEach(function (n) { line("note: " + n, "dim"); });
    },

    build: async function (r) {
      const d = await needScript(r.pos[0]);
      const aab = !!r.opts.aab;
      if (aab && r.opts.apk) throw new Error("build: choose --aab or --apk, not both");
      if (!aab && (r.opts.keystore || r.opts.alias)) throw new Error("build: --keystore and --alias go with --aab");
      let icon = "", keystore = "", ksPass = "";
      if (r.opts.icon) icon = await call("resolve", r.opts.icon);
      if (aab && r.opts.keystore) {
        keystore = await call("resolve", r.opts.keystore);
        ksPass = await ask("keystore password: ", true);
        line([["keystore password: ", "ps"], ["", ""]]);
        if (!ksPass) throw new Error("build: no password given");
      }
      const opts = { src: d.path, name: r.opts.name || d.name || "", ident: r.opts.id || d.id || "", bump: r.bump || "patch",
                     icon: icon, format: aab ? "aab" : "apk", keystore: keystore, ksAlias: r.opts.alias || "", ksPass: ksPass,
                     targetSdk: r.opts["target-sdk"] || "" };
      if (!S.ready) line("(build tools are missing: if this fails, type setup)", "warn");
      started(await call("build", opts), "the build");
    },

    preview: async function (r) {
      const d = await needScript(r.pos[0]);
      if (started(await call("preview", d.path, r.opts.width || null, r.opts.height || null), "the preview")) line("preview window opening: close it, or type cancel to stop it", "dim");
    },

    install: async function (r) {
      let apk = S.lastApk, pkg = S.lastPkg;
      if (r.pos[0]) { apk = await call("resolve", r.pos[0]); pkg = apk === S.lastApk ? S.lastPkg : ""; }
      if (!apk) throw new Error("install: no APK yet. Build one first, or: install FILE.apk");
      started(await call("install_on_phone", apk, pkg), "the install");
    },

    devices: async function () {
      const d = await call("devices");
      if (d.error) return err("error: " + d.error);
      if (!d.adb) return line("adb is not installed yet. Type setup.", "warn");
      if (!d.devices.length) return line("no phone found: connect it with USB and allow USB debugging", "warn");
      d.devices.forEach(function (x) { line(typeof x === "string" ? x : JSON.stringify(x)); });
    },

    update: async function (r) {
      const id = r.pos[0];
      if (!S.apps.length) await refreshApps();
      let src = null;
      if (r.opts.src) src = await call("resolve", r.opts.src);
      started(await call("update", id, r.bump || "patch", src), "the update");
    },

    apps: async function () {
      await refreshApps();
      if (!S.apps.length) return line("no apps with an id yet. Build with: build FILE --id NAME", "dim");
      const rows = S.apps.map(function (a) { return [a.id, "v" + a.version, a.pkg, a.srcExists ? Core.shorten(a.src, 48) : "(source file missing)"]; });
      Shell.padRows(rows).forEach(function (t, i) { line(t, S.apps[i].srcExists ? "" : "warn"); });
    },

    doctor: async function () {
      const d = await call("doctor");
      S.ready = d.ready;
      const rows = d.items.map(function (i) { return [i.ok ? "[ok]" : (i.optional ? "[--]" : "[!!]"), i.label, i.detail]; });
      Shell.padRows(rows).forEach(function (t, i) { const it = d.items[i]; line(t, it.ok ? "" : (it.optional ? "dim" : "bad")); });
      line("");
      line(d.ready ? "everything PocketTk needs is installed." : "something is missing: type setup", d.ready ? "good" : "warn");
      line("data: " + d.dataDir + "    builds: " + d.outputDir, "dim");
    },

    setup: async function () { started(await call("start_setup"), "the setup"); },
    rosetta: async function () { started(await call("install_rosetta"), "the Rosetta install"); },

    reveal: async function (r) {
      const p = r.pos[0] ? await call("resolve", r.pos[0]) : S.lastBuild;
      if (!p) throw new Error("reveal: nothing built yet. Give a path: reveal PATH");
      const x = await call("reveal", p);
      if (!x.ok) err("error: " + x.error);
    },

    outdir: async function (r) {
      const x = await call("outdir", r.pos[0] || null);
      if (!x.ok) return err("error: " + x.error);
      line([[r.pos[0] ? "builds now go to " : "builds go to ", "dim"], [x.dir, "acc"]]);
    },

    cd: async function (r) {
      const x = await call("cd", r.pos[0] || "");
      if (!x.ok) return err("cd: " + x.error);
      S.cwd = x.cwd;
      renderPrompt();
    },

    ls: async function (r) {
      const x = await call("ls", r.pos[0] || "");
      if (!x.ok) return err("ls: " + x.error);
      if (!x.entries.length) return line("(empty)", "dim");
      const parts = [];
      x.entries.forEach(function (e, i) { if (i) parts.push(["  ", ""]); parts.push([e.name + (e.dir ? "/" : ""), e.dir ? "acc" : (/\.py$/i.test(e.name) ? "" : "dim")]); });
      line(parts);
    },

    pwd: async function () { line(S.cwd); },
    cancel: async function () { await call("cancel"); line("nothing is running", "dim"); },
    clear: async function () { out.textContent = ""; },

    theme: async function (r) {
      const t = r.pos[0];
      if (!t) return line(Shell.THEMES.map(function (n) { return n === document.body.dataset.theme ? "[" + n + "]" : n; }).join("  "));
      if (Shell.THEMES.indexOf(t) < 0) throw new Error("theme: choose one of " + Shell.THEMES.join(", "));
      document.documentElement.dataset.theme = t;
      document.body.dataset.theme = t;
      store("pk-theme", t);
    },

    history: async function () { S.history.forEach(function (h, i) { line(("   " + (i + 1)).slice(-4) + "  " + h); }); },

    about: async function () {
      const d = document.createElement("div");
      d.innerHTML = LOGO;
      out.appendChild(d);
      lines([S.startup.version || "PocketTk", "tkinter Python scripts to Android apps (APK, Google Play bundle)", "by SSDES  -  MIT licence",
             "github.com/iamssdes/PocketTk"]);
      toBottom();
    },

    version: async function () { line(S.startup.version || "PocketTk"); },
    exit: async function () { await call("quit"); },
  };

  async function run(text) {
    const r = Shell.parse(text);
    if (r.empty) return;
    if (r.error) return err(r.error);
    try {
      await cmds[r.cmd](r);
    } catch (e) {
      err((e && e.message) || String(e));
    }
  }

  // ------------------------------------------------------------ keyboard
  function pushHistory(text) {
    if (S.history[S.history.length - 1] !== text) S.history.push(text);
    S.history = S.history.slice(-200);
    store("pk-history", JSON.stringify(S.history));
    S.hpos = S.history.length;
  }

  function submit() {
    const text = inp.value;
    inp.value = "";
    if (S.mode) {
      if (S.mode.secret) { /* never echoed */ }
      return endMode(text);
    }
    echo(text);
    if (!text.trim()) return;
    pushHistory(text);
    run(text);
  }

  async function tab() {
    const v = inp.value;
    const c = Shell.completion(v, { ids: S.apps.map(function (a) { return a.id; }) });
    if (!c) return;
    let items;
    if (c.kind === "list") items = c.items;
    else items = await call("complete_path", c.word, c.exts, c.dirsOnly);
    if (!items || !items.length) return;
    if (items.length === 1) { inp.value = Shell.applyCompletion(v, c, items[0], true); return; }
    const p = Shell.commonPrefix(items);
    if (p.length > c.word.length) { inp.value = Shell.applyCompletion(v, c, p, false); return; }
    if (S.lastTab === v) { echo(v); line(items.join("  "), "dim"); }
    S.lastTab = v;
  }

  function interrupt() {
    if (S.mode) {
      const wasLicense = /license/i.test(S.mode.prompt);
      line([[S.mode.prompt, "ps"], [inp.value + "^C", ""]]);
      inp.value = "";
      endMode("");
      if (wasLicense) return;
      return;
    }
    if (S.fg || S.busy.work || S.busy.preview) { line("^C", "warn"); call("cancel"); return; }
    if (inp.value) { echo(inp.value + "^C"); inp.value = ""; }
  }

  inp.addEventListener("keydown", function (e) {
    if (e.ctrlKey && !e.altKey && !e.metaKey) {
      const k = e.key.toLowerCase();
      if (k === "c" && (S.fg || S.mode || !window.getSelection().toString())) { e.preventDefault(); return interrupt(); }
      if (k === "l") { e.preventDefault(); out.textContent = ""; return; }
      if (k === "u") { e.preventDefault(); inp.value = ""; return; }
    }
    if (S.fg && !S.mode) { e.preventDefault(); return; }
    if (e.key === "Enter") { e.preventDefault(); return submit(); }
    if (S.mode) return;
    if (e.key === "Tab") { e.preventDefault(); return tab(); }
    if (e.key === "ArrowUp") {
      e.preventDefault();
      if (S.hpos === S.history.length) S.draft = inp.value;
      if (S.hpos > 0) { S.hpos--; inp.value = S.history[S.hpos]; }
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      if (S.hpos < S.history.length) { S.hpos++; inp.value = S.hpos === S.history.length ? S.draft : S.history[S.hpos]; }
    }
  });

  screen.addEventListener("mouseup", function () { if (!window.getSelection().toString()) inp.focus(); });
  window.addEventListener("dragover", function (e) { e.preventDefault(); $("drop").classList.add("on"); });
  window.addEventListener("dragleave", function () { $("drop").classList.remove("on"); });
  window.addEventListener("drop", function (e) {
    e.preventDefault();
    $("drop").classList.remove("on");
    const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    const p = f && (f.pywebviewFullPath || f.path);
    if (!p) return err("dropping files is not available here: type  use FILE  or  pick");
    echo("use " + Shell.quoteIfNeeded(p));
    run("use " + Shell.quoteIfNeeded(p));
  });

  // ------------------------------------------------------------ start
  function begin() {
    renderPrompt();
    call("startup").then(function (st) {
      S.startup = st; S.cwd = st.cwd; S.home = st.home; S.sep = st.sep; S.ready = st.ready;
      renderPrompt();
      line([[st.version, "acc"], ["   tkinter to Android apps", "dim"]]);
      line("type help for the commands.  quick start:  pick   then   build", "dim");
      line("");
      refreshApps();
      if (st.script) setScript(st.script);
      if (!st.ready && !st.autoSetupDone) {
        line("first run: PocketTk needs a Java JDK and Google's Android build tools (a few hundred MB, one time).", "warn");
        call("start_setup").then(function (r) { if (!r.ok) err(r.error); });
      } else if (!st.ready) {
        line("some build tools are missing: type setup", "warn");
      }
      if (st.pick) run("pick");
      setInterval(poll, 150);
      inp.focus();
    });
  }

  function boot() {
    const t = store("pk-theme");
    if (t && Shell.THEMES.indexOf(t) >= 0) { document.documentElement.dataset.theme = t; document.body.dataset.theme = t; }
    try { S.history = JSON.parse(store("pk-history") || "[]").filter(function (x) { return typeof x === "string"; }); } catch (e) { S.history = []; }
    S.hpos = S.history.length;
    if (window.pywebview && window.pywebview.api) { S.api = window.pywebview.api; return begin(); }
    window.addEventListener("pywebviewready", function () { S.api = window.pywebview.api; begin(); }, { once: true });
    setTimeout(function () {
      if (!S.api) lines(["pockettk: cannot reach the engine behind this window (the pywebview bridge did not start).",
                         "Close the window and start PocketTk again; run  pockettk doctor  in a terminal if it keeps happening."], "bad");
    }, 1500);
  }

  document.addEventListener("DOMContentLoaded", boot);
})();
