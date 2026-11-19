package android.os;
import java.util.*; import java.util.concurrent.*;
public class Handler {
    static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "pockettk-timer"); t.setDaemon(true); return t; });
    final Looper l; final Map<Runnable, List<ScheduledFuture<?>>> pending = new ConcurrentHashMap<>();
    public interface Callback { boolean handleMessage(Object m); }
    public Handler() { this(Looper.myLooper()); }
    public Handler(Looper l) { this.l = l; }
    public boolean post(Runnable r) { l.post(r); return true; }
    public boolean postDelayed(final Runnable r, long d) {
        ScheduledFuture<?> f = TIMER.schedule(() -> l.post(r), Math.max(0, d), TimeUnit.MILLISECONDS);
        pending.computeIfAbsent(r, k -> new CopyOnWriteArrayList<>()).add(f); return true;
    }
    public void removeCallbacks(Runnable r) { List<ScheduledFuture<?>> fs = pending.remove(r); if (fs != null) for (ScheduledFuture<?> f : fs) f.cancel(false); }
    public void removeCallbacksAndMessages(Object o) { for (Runnable r : new ArrayList<>(pending.keySet())) removeCallbacks(r); }
    public Looper getLooper() { return l; }
}
