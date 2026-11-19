package android.os;
import java.util.concurrent.*;
public class Looper {
    static final Looper MAIN = new Looper(true); static final ThreadLocal<Looper> TL = new ThreadLocal<>();
    final boolean main; final LinkedBlockingQueue<Runnable> q = new LinkedBlockingQueue<>(); volatile boolean quit;
    Looper(boolean m) { main = m; }
    public static void prepare() { TL.set(new Looper(false)); }
    public static void prepareMainLooper() {}
    public static Looper myLooper() { return javax.swing.SwingUtilities.isEventDispatchThread() ? MAIN : TL.get(); }
    public static Looper getMainLooper() { return MAIN; }
    public Thread getThread() { return null; }
    public static void loop() {
        Looper l = TL.get();
        while (!l.quit) { try { Runnable r = l.q.poll(200, TimeUnit.MILLISECONDS); if (r != null) { long t = System.nanoTime(); try { r.run(); } catch (Throwable t2) { t2.printStackTrace(); } PreviewEnv.throttle(System.nanoTime() - t); } } catch (InterruptedException e) { return; } }
    }
    public void quit() { quit = true; }
    void post(Runnable r) { if (main) javax.swing.SwingUtilities.invokeLater(PreviewEnv.edt(r)); else q.add(r); }
}
