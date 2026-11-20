/* PocketTk window: the logic that has no DOM in it (so it can be tested with node). */
const Core = (function () {
  "use strict";

  const BUILD_STEPS = [["transpile", "Transpile"], ["compile", "Compile"], ["dex", "Dex"], ["link", "Resources"], ["package", "Package"]];
  const STEPS = {
    apk: BUILD_STEPS.concat([["sign", "Sign"]]),
    aab: BUILD_STEPS.concat([["bundle", "Bundle"], ["sign", "Sign"]]),
    setup: [["jdk", "Java JDK"], ["sdk", "Android SDK"], ["bundletool", "bundletool"]],
  };
  const MAX_LOG = 3000;

  function stepsFor(kind) {
    return (STEPS[kind] || STEPS.apk).map(function (s) { return { id: s[0], label: s[1], state: "todo" }; });
  }

  /* A job is everything the page shows about one build / setup run. */
  function newJob(kind) {
    return { kind: kind, steps: stepsFor(kind), status: "", log: [], progress: null, license: null,
             result: null, error: null, running: true, explicit: kind === "setup" };
  }

  function applyEvent(job, ev) {
    switch (ev.t) {
      case "log":
        job.log.push(ev.text);
        if (job.log.length > MAX_LOG) job.log.splice(0, job.log.length - MAX_LOG);
        break;
      case "status": job.status = ev.text || ""; break;
      case "progress": job.progress = { done: ev.done, total: ev.total, label: ev.label || "" }; break;
      case "license": job.license = ev.text; break;
      case "step": {
        var idx = -1;
        job.steps.forEach(function (s, i) { if (s.id === ev.id) idx = i; });
        if (idx < 0) break;
        if (job.explicit) {
          job.steps[idx].state = ev.state;
        } else {
          for (var i = 0; i < idx; i++) job.steps[i].state = "done";
          job.steps[idx].state = "running";
        }
        break;
      }
      case "done":
        job.running = false;
        job.result = ev;
        job.status = "";
        job.progress = null;
        job.steps.forEach(function (s) { s.state = "done"; });
        break;
      case "error":
        job.running = false;
        job.error = ev.message || "failed";
        job.status = "";
        job.progress = null;
        job.license = null;
        job.steps.forEach(function (s) { if (s.state === "running") s.state = "error"; });
        break;
    }
    return job;
  }

  function percent(p) {
    if (!p || !p.total) return null;
    return Math.max(0, Math.min(100, Math.round(p.done * 100 / p.total)));
  }

  function formatBytes(n) {
    if (!n && n !== 0) return "";
    var u = ["B", "KB", "MB", "GB"], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (i === 0 ? n : n.toFixed(n >= 100 ? 0 : 1)) + " " + u[i];
  }

  /* "1.2.3" / "1.2.3 Patch 2" -> what the version becomes with that kind of update (same rules as the tool). */
  function nextVersion(version, bump) {
    var m = /^(\d+)\.(\d+)\.(\d+)(?:\s+Patch\s+(\d+))?$/.exec((version || "").trim());
    if (!m) return "";
    var A = +m[1], B = +m[2], C = +m[3], P = +(m[4] || 0);
    if (bump === "large") { A++; B = 0; C = 0; P = 0; }
    else if (bump === "medium") { B++; C = 0; P = 0; }
    else if (bump === "small") { C++; P = 0; }
    else { P++; }
    return A + "." + B + "." + C + (P > 0 ? " Patch " + P : "");
  }

  function defaultName(file) {
    return (file || "").replace(/\.py$/i, "").replace(/[_\-]+/g, " ").replace(/\s+/g, " ").trim();
  }

  function shorten(path, max) {
    max = max || 64;
    if (!path || path.length <= max) return path || "";
    return path.slice(0, 18) + "\u2026" + path.slice(path.length - (max - 19));
  }

  return { stepsFor: stepsFor, newJob: newJob, applyEvent: applyEvent, percent: percent, formatBytes: formatBytes,
           nextVersion: nextVersion, defaultName: defaultName, shorten: shorten };
})();

if (typeof module !== "undefined" && module.exports) module.exports = Core;
