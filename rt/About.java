package apple.rt;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "About TkDroid" page, opened from the link in the version label.
 * A plain scrolling page: what TkDroid is, how apps are made with it, who makes it, and whether you can use it
 * (yes: it is open source, with a button to the GitHub repo).
 * "Download the demo" copies the demo module out of the app itself (it is bundled in the APK / AAB as an asset) into
 * Downloads; "Submit a request" opens the Request page.
 */
public class About extends Activity {
    static final int BG = 0xFF101418, TEXT = 0xFFE6EDF3, DIM = 0xFFA9B4BF, HEAD = 0xFF8BE9FD, WARN = 0xFFFFC857;

    /** Where TkDroid lives now that it is open source. */
    static final String REPO_URL = "https://github.com/iamssdes/TkDroid";

    float density = 1f;
    TextView demoStatus, demoOpen;
    ProgressBar demoBar;
    boolean downloading;
    final AtomicBoolean cancelled = new AtomicBoolean();

    int dp(float v) { return Math.round(v * density); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        setTitle("About TkDroid");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(36), dp(20), dp(28));
        sv.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        text(page, "About TkDroid", 36, TEXT, true, 0);
        String built = Ui.builtWith;
        text(page, built != null && built.length() > 0 ? "This app was built with TkDroid " + built : "tkinter to Android, on your phone", 12, DIM, false, 4);

        heading(page, "What is TkDroid?");
        text(page, "TkDroid turns a Python tkinter program into a real Android app. You write the program the way you "
                + "would for a desktop window, with buttons, text boxes, labels and canvases, and TkDroid gives you an "
                + "installable APK. It all happens on the phone: TkDroid is a KernelSU module with a Termux command, "
                + "so no computer is needed.", 15, TEXT, false, 6);
        text(page, "tkinter is the window toolkit that comes with Python, so if you have written a small desktop "
                + "program in Python you already know most of what you need. Your Python code stays Python. TkDroid "
                + "takes care of the Android side for you: the manifest, the icon, signing and installing.", 15, TEXT, false, 10);

        heading(page, "Why?");
        text(page, "Because of an idea. I had a cool idea, so I started building.", 15, TEXT, false, 6);

        heading(page, "How are apps made with it?");
        step(page, "1.  Write a Python script that uses tkinter.");
        step(page, "2.  Run  tkdroid run app.py  in Termux.");
        step(page, "3.  TkDroid translates your Python into Java and compiles it together with its runtime, the part "
                + "that turns tkinter widgets into real Android views. Then it signs the APK and installs it.");
        step(page, "4.  The result is a native app. No Python runs on the phone while it is open.");
        step(page, "5.  Change the script and run  tkdroid update --id NAME  to rebuild it over the old app. "
                + "Its saved data is kept.");
        text(page, "Give an app an id with  --id NAME  and TkDroid remembers its name, icon, version and package, so "
                + "every later build installs over it as an update instead of as a new app.", 15, TEXT, false, 10);
        text(page, "Apps can also be floating windows over other apps, remember their variables between runs, play music "
                + "bundled inside the app, and open files with the system file picker. A debug build shows a log panel "
                + "under the app while you test a change.", 15, TEXT, false, 10);

        heading(page, "About the developer");
        text(page, "TkDroid is made by SSDES, a very indie developer. It is an independent project, made without a "
                + "company or a publisher behind it.", 15, TEXT, false, 6);
        text(page, "It started out as a tool called ApkApple and was renamed TkDroid. It is built to run entirely on a "
                + "phone, for people who use KernelSU and Termux.", 15, TEXT, false, 10);
        text(page, "TkDroid is tested on the developer's own phone. Phones differ, so results may vary depending on the "
                + "age of the device, its Android version and its root status.", 15, TEXT, false, 10);
        text(page, "It is still a work in progress: features are added and changed from version to version, and "
                + "things may still break.", 15, TEXT, false, 10);

        heading(page, "Can I use it?");
        text(page, "Short answer: Yes.", 19, TEXT, true, 8);
        text(page, "TkDroid is open source. You can use it, read the code, change it and share it. "
                + "The code, the install steps and the issue tracker are on GitHub.", 15, TEXT, false, 8);
        button(page, "Open the repo on GitHub", 16, new Runnable() { public void run() { openRepo(); } });
        TextView url = text(page, REPO_URL.replace("https://", ""), 15, HEAD, true, 10);
        url.setGravity(Gravity.CENTER);
        url.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openRepo(); } });

        TextView close = text(page, "Close", 15, HEAD, true, 34);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(12), dp(14), dp(12), dp(14));
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });

        setContentView(sv);
    }

    /** A big filled button (same look as the Send button on the Request page). */
    TextView button(LinearLayout parent, String label, int topDp, final Runnable onTap) {
        TextView b = text(parent, label, 16, BG, true, topDp);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(HEAD);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onTap.run(); } });
        return b;
    }

    // ------------------------------------------------------------------------------ demo download

    void setDemoStatus(String s, int color) {
        demoStatus.setText(s);
        demoStatus.setTextColor(color);
        demoStatus.setVisibility(View.VISIBLE);
    }

    /** The demo module zip bundled in the app (assets/). The full TkDroid adds it to every APK / AAB it builds. */
    static final String DEMO_ASSET = "TkDroid_Demo.zip";

    /** Is the demo module bundled in this app? (The demo edition cannot contain itself, so apps built with it are not.) */
    boolean hasDemoAsset() {
        InputStream in = null;
        try {
            in = getAssets().open(DEMO_ASSET);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { /* nothing to do */ }
        }
    }

    /** "Download the demo": copies the bundled demo module into Downloads. Nothing is downloaded from the internet. */
    void startDemoSave() {
        if (downloading) return;
        downloading = true;
        cancelled.set(false);
        demoOpen.setVisibility(View.GONE);
        demoBar.setIndeterminate(true);
        demoBar.setVisibility(View.VISIBLE);
        setDemoStatus("Saving the demo...", WARN);
        Thread th = new Thread(new Runnable() { public void run() { saveDemo(DEMO_ASSET, "TkDroid_Demo.zip"); } }, "tkdroid-demo-save");
        th.setDaemon(true);
        th.start();
    }

    /** Runs on a background thread. */
    void saveDemo(String asset, String fname) {
        Uri uri = null;
        File file = null;
        InputStream in = null;
        OutputStream out = null;
        String where = null, problem = null;
        try {
            in = getAssets().open(asset);
            byte[] buf = new byte[16384];
            int n = in.read(buf);
            // what is inside the app must really be a zip (a module zip starts with "PK\3\4")
            if (n < 4 || buf[0] != 'P' || buf[1] != 'K' || buf[2] != 3 || buf[3] != 4) {
                throw new Online.Failure("The demo bundled in this app is damaged. Build the app again with the full TkDroid.");
            }
            if (Build.VERSION.SDK_INT >= 29) {                    // the public Downloads folder, no permission needed
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fname);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Online.Failure("Could not create the file in Downloads.");
                out = getContentResolver().openOutputStream(uri);
            } else {                                              // older Android: the app's own Downloads folder
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) throw new Online.Failure("No storage is available.");
                dir.mkdirs();
                file = uniqueFile(dir, fname);
                out = new FileOutputStream(file);
            }
            if (out == null) throw new Online.Failure("Could not open the file for writing.");
            while (n > 0) {
                if (cancelled.get()) throw new Online.Failure("Cancelled.");
                out.write(buf, 0, n);
                n = in.read(buf);
            }
            out.close();
            out = null;
            if (uri != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                where = "Downloads/" + displayName(uri, fname);
            } else {
                where = file.getAbsolutePath();
            }
        } catch (Throwable t) {
            problem = Online.describe(t);
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { /* nothing to do */ }
            try { if (out != null) out.close(); } catch (Throwable ignored) { /* already failing */ }
        }
        if (problem != null) {                                    // no half-saved file is left behind
            try { if (uri != null) getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { /* best effort */ }
            if (file != null) file.delete();
        }
        final String fWhere = where, fProblem = problem;
        final boolean inDownloads = uri != null;
        runOnUiThread(new Runnable() {
            public void run() {
                downloading = false;
                if (isFinishing() || cancelled.get()) return;
                demoBar.setVisibility(View.GONE);
                if (fProblem != null) {
                    setDemoStatus(fProblem, WARN);
                } else {
                    setDemoStatus("Saved to " + fWhere + ". To install it, open the KernelSU app, go to Modules, tap Install "
                            + "and pick this file.", HEAD);
                    if (inDownloads) demoOpen.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /** The name MediaStore really gave the file (it adds " (1)" when the name was taken). */
    String displayName(Uri uri, String fallback) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[] { MediaStore.MediaColumns.DISPLAY_NAME }, null, null, null);
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Throwable ignored) { /* use the name we asked for */ } finally {
            if (c != null) c.close();
        }
        return fallback;
    }

    static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; f.exists(); i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }

    void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Throwable t) {
            Toast.makeText(this, "Open your Downloads folder in a file manager.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        cancelled.set(true);                                      // closing the page stops a running save
        super.onDestroy();
    }

    /** "Open the repo on GitHub": opens the repository in the browser. */
    void openRepo() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)));
        } catch (Throwable t) {
            Toast.makeText(this, "Open " + REPO_URL + " in a browser.", Toast.LENGTH_LONG).show();
        }
    }

    /** "Submit a request": opens the TkDroid Request page. */
    void openRequest() {
        try {
            startActivity(new Intent(this, Request.class));
        } catch (Throwable t) {
            Toast.makeText(this, "Could not open the request page", Toast.LENGTH_LONG).show();
        }
    }

    TextView text(LinearLayout parent, String s, float sp, int color, boolean bold, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        parent.addView(t, lp);
        return t;
    }

    /** Section headers are all big text. */
    void heading(LinearLayout parent, String s) { heading(parent, s, HEAD); }

    void heading(LinearLayout parent, String s, int color) { text(parent, s, 30, color, true, 34); }

    void step(LinearLayout parent, String s) { text(parent, s, 15, TEXT, false, 6); }
}
