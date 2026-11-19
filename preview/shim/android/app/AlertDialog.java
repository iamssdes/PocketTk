package android.app;
import android.content.*;
public class AlertDialog implements DialogInterface {
    public static class Builder {
        String t = "", m = ""; String[] b = new String[3]; DialogInterface.OnClickListener[] l = new DialogInterface.OnClickListener[3];
        public Builder(Context c) {}
        public Builder setTitle(CharSequence s) { t = s.toString(); return this; } public Builder setMessage(CharSequence s) { m = s.toString(); return this; }
        public Builder setPositiveButton(CharSequence s, DialogInterface.OnClickListener x) { b[0] = s.toString(); l[0] = x; return this; }
        public Builder setNegativeButton(CharSequence s, DialogInterface.OnClickListener x) { b[1] = s.toString(); l[1] = x; return this; }
        public Builder setCancelable(boolean c) { return this; }
        public AlertDialog show() { final AlertDialog d = new AlertDialog(); java.util.List<String> names = new java.util.ArrayList<>(); java.util.List<Runnable> acts = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) if (b[i] != null) { final int w = i == 0 ? DialogInterface.BUTTON_POSITIVE : DialogInterface.BUTTON_NEGATIVE; final DialogInterface.OnClickListener x = l[i]; names.add(b[i]); acts.add(() -> { if (x != null) x.onClick(d, w); }); }
            if (names.isEmpty()) { names.add("OK"); acts.add(() -> {}); }
            android.os.PreviewEnv.sink.dialog(t, m, names.toArray(new String[0]), acts.toArray(new Runnable[0])); return d; }
    }
    public void dismiss() {}
}
