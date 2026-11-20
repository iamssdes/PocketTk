package apple.rt;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.*;

/**
 * The `termux` module (`#use: termux-connect`): an app runs a command inside Termux and gets its output back.
 * It uses Termux's own RUN_COMMAND intent, so there is no server, no open port and nothing that has to be started first:
 * the app sends one intent per command and Termux answers with a PendingIntent (see TermuxResult).
 *
 *   termux.state()                  "ok", "missing" (Termux is not installed) or "permission" (not allowed yet)
 *   termux.ask()                    shows Android's "allow running commands in Termux" question
 *   termux.run(argv, callback)      callback(exit_code, stdout, stderr) when the command ended; exit_code is -1 when it
 *                                   could not be started (stderr then says why)
 */
public final class Termux {
    private Termux() { }

    static final String PKG = "com.termux";
    static final String PERM = "com.termux.permission.RUN_COMMAND";
    static final String PREFIX = "/data/data/com.termux/files/usr";
    static final String HOME = "/data/data/com.termux/files/home";

    static final Map<Integer, Object> pending = new HashMap<Integer, Object>();
    static int nextId = 1;

    public static Object call(String fn, Object[] a) {
        switch (fn) {
            case "state": return state();
            case "ask": ask(); return null;
            case "run": {
                Object wd = Py.kwv(a, "workdir", null);
                List<Object> argv = new ArrayList<Object>();
                for (Object x : Py.iter(Py.arg(a, 0))) argv.add(x);
                run(argv, Py.arg(a, 1), wd == null ? null : Py.str(wd));
                return null;
            }
        }
        throw new Py.PyError("AttributeError", "module 'termux' has no attribute '" + fn + "'");
    }

    static String state() {
        Context c = Ui.ctx;
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
        } catch (Throwable t) {
            return "missing";
        }
        return c.checkSelfPermission(PERM) == 0 ? "ok" : "permission";
    }

    static void ask() {
        final Activity a = Ui.act;
        if (a == null) return;
        Ui.postUi(new Runnable() {
            public void run() {
                try { a.requestPermissions(new String[] { PERM }, 4207); } catch (Throwable ignored) { /* nothing to ask */ }
            }
        });
    }

    /** Sends one RUN_COMMAND intent. argv[0] is a command name (looked up in Termux's bin folder) or an absolute path. */
    static void run(List<Object> argv, Object cb, String workdir) {
        if (argv.isEmpty()) throw new Py.PyError("ValueError", "termux.run needs a command");
        String exe = Py.str(argv.get(0));
        if (!exe.startsWith("/")) exe = PREFIX + "/bin/" + exe;
        String[] args = new String[argv.size() - 1];
        for (int i = 1; i < argv.size(); i++) args[i - 1] = Py.str(argv.get(i));
        int id;
        synchronized (pending) {
            id = nextId++;
            pending.put(id, cb);
        }
        try {
            Context c = Ui.ctx;
            Intent i = new Intent();
            i.setClassName(PKG, "com.termux.app.RunCommandService");
            i.setAction("com.termux.RUN_COMMAND");
            i.putExtra("com.termux.RUN_COMMAND_PATH", exe);
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args);
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir != null ? workdir : HOME);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            Intent back = new Intent(c, TermuxResult.class);
            back.putExtra("id", id);
            // mutable: Termux adds the result to this intent before it sends it back
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",
                    PendingIntent.getBroadcast(c, id, back, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            c.startService(i);
        } catch (Throwable t) {
            finish(id, -1, "", String.valueOf(t));
        }
    }

    /** Called by TermuxResult when Termux answers. */
    static void deliver(Intent in) {
        int id = in.getIntExtra("id", 0);
        Bundle r = in.getBundleExtra("result");
        int code = -1;
        String out = "", err = "";
        if (r != null) {
            code = r.getInt("exitCode", -1);
            out = nz(r.getString("stdout"));
            err = nz(r.getString("stderr"));
            String em = nz(r.getString("errmsg"));
            if (em.length() > 0 && code == -1) err = em;
        } else {
            err = "Termux sent no result";
        }
        finish(id, code, out, err);
    }

    static void finish(int id, int code, String out, String err) {
        Object cb;
        synchronized (pending) { cb = pending.remove(id); }
        if (cb == null) return;           // the app was restarted since: nobody is waiting for this one
        Ui.call(cb, Long.valueOf(code), out, err);
    }

    static String nz(String s) { return s == null ? "" : s; }
}
