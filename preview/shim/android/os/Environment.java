package android.os;
public class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Download", DIRECTORY_DOCUMENTS = "Documents", DIRECTORY_PICTURES = "Pictures";
    public static java.io.File getExternalStorageDirectory() { java.io.File f = new java.io.File(PreviewEnv.dataDir, "sdcard"); f.mkdirs(); return f; }
    public static java.io.File getExternalStoragePublicDirectory(String d) { java.io.File f = new java.io.File(getExternalStorageDirectory(), d); f.mkdirs(); return f; }
}
