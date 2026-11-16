package apple.rt;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Background music for `#use: audio` apps. The tracks listed by `#audio:` / `#playlist:` are bundled
 * in the APK (assets/audio/) at build time; here they are copied once into the cache dir and played
 * in order with MediaPlayer. Pauses while the app is in the background, resumes when it comes back.
 * All control methods are called on the main thread.
 */
public final class Audio {
    private Audio() {}

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static Context ctx;
    private static String[] tracks;
    private static boolean loop;
    private static MediaPlayer mp;
    private static int index;
    private static int failures;     // consecutive tracks that could not be played
    private static boolean started;
    private static boolean paused;   // app is in the background
    private static boolean ready;    // mp is prepared
    private static boolean pruned;
    private static int gen;          // bumped for every track request; callbacks of older requests are ignored

    /** Starts playing `names` (asset names under assets/audio/). Safe to call twice. */
    public static void start(Context c, String[] names, boolean looping) {
        if (started || names == null || names.length == 0) { resume(); return; }
        try {
            ctx = c.getApplicationContext();
            tracks = names;
            loop = looping;
            started = true;
            paused = false;
            failures = 0;
            play(0);
        } catch (Throwable t) {
            started = false;
            log("start failed: " + t);
        }
    }

    public static void pause() {
        paused = true;
        try { if (mp != null && ready && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { /* player gone */ }
    }

    public static void resume() {
        if (!started) return;
        paused = false;
        try { if (mp != null && ready && !mp.isPlaying()) mp.start(); } catch (Throwable ignored) { /* player gone */ }
    }

    public static void stop() {
        gen++;
        started = false;
        release();
    }

    // ------------------------------------------------------------------------------ internals

    private static void play(final int i) {
        final int g = ++gen;
        index = i;
        release();
        Thread th = new Thread(new Runnable() {
            public void run() {
                if (!pruned) { pruned = true; prune(); }
                final File f = materialize(tracks[i]);
                UI.post(new Runnable() { public void run() { if (g == gen) begin(f, i, g); } });
            }
        }, "apple-audio");
        th.setDaemon(true);
        th.start();
    }

    private static void begin(File f, final int i, final int g) {
        if (f == null) { failed(i); return; }
        try {
            MediaPlayer m = new MediaPlayer();
            mp = m;
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            m.setDataSource(f.getAbsolutePath());
            m.setLooping(loop && tracks.length == 1);
            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer p) {
                    if (g != gen) return;
                    ready = true;
                    failures = 0;
                    if (!paused) p.start();
                }
            });
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer p) { if (g == gen) next(); }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (g == gen) failed(i);
                    return true;
                }
            });
            m.prepareAsync();
        } catch (Throwable t) {
            log("cannot play " + tracks[i] + ": " + t);
            failed(i);
        }
    }

    private static void next() {
        int n = index + 1;
        if (n >= tracks.length) {
            if (!loop) { gen++; release(); return; }
            n = 0;
        }
        play(n);
    }

    private static void failed(int i) {
        failures++;
        log("track " + tracks[i] + " failed (" + failures + "/" + tracks.length + ")");
        if (failures >= tracks.length) { gen++; release(); return; }   // nothing playable: give up quietly
        next();
    }

    private static void release() {
        ready = false;
        MediaPlayer m = mp;
        mp = null;
        if (m == null) return;
        try { m.stop(); } catch (Throwable ignored) { /* not started */ }
        try { m.release(); } catch (Throwable ignored) { /* already released */ }
    }

    /** Copies assets/audio/NAME into the cache dir (names carry a content hash, so an existing copy is current). */
    private static File materialize(String name) {
        InputStream in = null;
        OutputStream os = null;
        try {
            File dir = new File(ctx.getCacheDir(), "audio");
            dir.mkdirs();
            File out = new File(dir, name);
            if (out.isFile() && out.length() > 0) return out;
            File tmp = new File(dir, name + ".tmp");
            in = ctx.getAssets().open("audio/" + name);
            os = new FileOutputStream(tmp);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            os = null;
            if (!tmp.renameTo(out)) return null;
            return out;
        } catch (Throwable t) {
            log("cannot unpack " + name + ": " + t);
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { /* ignore */ }
            try { if (os != null) os.close(); } catch (Throwable ignored) { /* ignore */ }
        }
    }

    /** Removes cached tracks that belong to an older build of the app. */
    private static void prune() {
        try {
            File[] fs = new File(ctx.getCacheDir(), "audio").listFiles();
            if (fs == null) return;
            for (File f : fs) {
                boolean keep = f.getName().endsWith(".tmp");
                for (String t : tracks) if (f.getName().equals(t)) keep = true;
                if (!keep) f.delete();
            }
        } catch (Throwable ignored) { /* best effort */ }
    }

    private static void log(String s) {
        try { android.util.Log.w("ApkApple", "audio: " + s); } catch (Throwable ignored) { /* no logger */ }
    }
}
