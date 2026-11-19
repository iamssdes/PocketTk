package android.os;
/** State the desktop preview window shares with the Android classes (density, screen size, hooks into the window). */
public class PreviewEnv {
    public static float density = 2.625f, scaledDensity = 2.625f; public static int widthPx = 945, heightPx = 2100, cpu = 100;
    public static java.io.File dataDir = new java.io.File(System.getProperty("java.io.tmpdir"), "pockettk-preview");
    public interface Sink {
        void toast(String s); void startActivity(android.content.Intent i); void finish(); void note(String s);
        void dialog(String title, String msg, String[] buttons, Runnable[] actions);
    }
    public static Sink sink = new Sink() {
        public void toast(String s) { System.out.println("[toast] " + s); } public void startActivity(android.content.Intent i) { System.out.println("[startActivity] " + i); }
        public void finish() { System.out.println("[finish]"); } public void note(String s) { System.out.println("[preview] " + s); }
        public void dialog(String t, String m, String[] b, Runnable[] a) { System.out.println("[dialog] " + t + ": " + m); }
    };
    public static android.view.WindowManager windowManager;
    public static Runnable edt(Runnable r) { return () -> { long t = System.nanoTime(); try { r.run(); } finally { throttle(System.nanoTime() - t); } }; }
    /** CPU strength simulation: after work that took t ns, sleep so the work looks (100/cpu) times slower. */
    public static void throttle(long ns) {
        if (cpu >= 100 || cpu <= 0) return; long extra = (long) (ns * (100.0 / cpu - 1.0)) / 1_000_000L;
        if (extra > 0) try { Thread.sleep(Math.min(extra, 2000)); } catch (InterruptedException e) {}
    }
}
