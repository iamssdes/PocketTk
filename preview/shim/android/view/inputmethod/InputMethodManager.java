package android.view.inputmethod;
public class InputMethodManager {
    public static final int SHOW_IMPLICIT = 1, SHOW_FORCED = 2, HIDE_NOT_ALWAYS = 1;
    public boolean hideSoftInputFromWindow(android.os.IBinder b, int f) { return true; }
    public boolean showSoftInput(android.view.View v, int f) { return true; }
    public void toggleSoftInput(int a, int b) {}
}
