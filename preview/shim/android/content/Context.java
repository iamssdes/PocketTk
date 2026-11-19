package android.content;
import android.os.PreviewEnv;
public class Context {
    public static final String CLIPBOARD_SERVICE = "clipboard", WINDOW_SERVICE = "window", INPUT_METHOD_SERVICE = "input_method";
    public static final int MODE_PRIVATE = 0;
    ClipboardManager clip = new ClipboardManager();
    public Context getApplicationContext() { return this; }
    public Object getSystemService(String n) { switch (n) { case "window": return PreviewEnv.windowManager; case "clipboard": return clip; case "input_method": return new android.view.inputmethod.InputMethodManager(); } return null; }
    public android.content.res.Resources getResources() { return new android.content.res.Resources(); }
    public java.io.File getFilesDir() { java.io.File f = new java.io.File(PreviewEnv.dataDir, "files"); f.mkdirs(); return f; }
    public java.io.File getCacheDir() { java.io.File f = new java.io.File(PreviewEnv.dataDir, "cache"); f.mkdirs(); return f; }
    public java.io.File getExternalFilesDir(String s) { java.io.File f = new java.io.File(PreviewEnv.dataDir, "external" + (s == null ? "" : "/" + s)); f.mkdirs(); return f; }
    public String getPackageName() { return System.getProperty("pockettk.package", "pockettk.preview"); }
    public ContentResolver getContentResolver() { return new ContentResolver(); }
    public void startActivity(Intent i) { PreviewEnv.sink.startActivity(i); }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public android.content.SharedPreferences getSharedPreferences(String n, int m) { return new SharedPreferences(); }
}
