package android.widget;
public class Toast { public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1; String s;
    public static Toast makeText(android.content.Context c, CharSequence s, int d) { Toast t = new Toast(); t.s = s.toString(); return t; }
    public void show() { android.os.PreviewEnv.sink.toast(s); } }
