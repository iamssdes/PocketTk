package android.view;
import java.util.*;
public class ViewTreeObserver {
    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public interface OnWindowFocusChangeListener { void onWindowFocusChanged(boolean f); }
    public interface OnGlobalFocusChangeListener { void onGlobalFocusChanged(View a, View b); }
    public interface OnPreDrawListener { boolean onPreDraw(); }
    final List<OnGlobalLayoutListener> gl = new ArrayList<>();
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l) { gl.add(l); }
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l) { gl.remove(l); }
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener l) { gl.remove(l); }
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {}
    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l) {}
    public void addOnPreDrawListener(OnPreDrawListener l) {}
    public boolean isAlive() { return true; }
    void fireLayout() { for (OnGlobalLayoutListener l : new ArrayList<>(gl)) l.onGlobalLayout(); }
}
