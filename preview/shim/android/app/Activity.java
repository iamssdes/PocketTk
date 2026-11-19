package android.app;
import android.content.*; import android.view.*; import android.os.*;
public class Activity extends Context {
    public View content; CharSequence title = System.getProperty("pockettk.title", "App");
    public static Runnable contentChanged = () -> {};
    protected void onCreate(Bundle b) {} protected void onResume() {} protected void onPause() {} protected void onStop() {} protected void onDestroy() {} public void onBackPressed() { finish(); }
    public void setContentView(View v) { content = v; contentChanged.run(); }
    public void moveTaskToBack(boolean b) { PreviewEnv.sink.note("moveTaskToBack"); }
    public boolean isFinishing() { return false; }
    public void startActivityForResult(Intent i, int r) { PreviewEnv.sink.startActivity(i); } protected void onActivityResult(int a, int b, Intent d) {}
    public void finish() { PreviewEnv.sink.finish(); }
    public void setTitle(CharSequence t) { title = t; } public CharSequence getTitle() { return title; }
    public void runOnUiThread(Runnable r) { if (javax.swing.SwingUtilities.isEventDispatchThread()) r.run(); else new Handler(Looper.getMainLooper()).post(r); }
    public Window getWindow() { return new Window(); }
    public void setRequestedOrientation(int o) {}
    public Intent getIntent() { return new Intent(); }
    public void onConfigurationChanged(android.content.res.Configuration c) {}
    public void onRequestPermissionsResult(int a, String[] b, int[] c) {} public void requestPermissions(String[] p, int c) {}
}
