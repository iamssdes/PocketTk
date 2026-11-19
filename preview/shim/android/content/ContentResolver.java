package android.content;
import android.net.Uri; import java.io.*;
public class ContentResolver {
    static File dir() { File d = new File(android.os.PreviewEnv.dataDir, "content"); d.mkdirs(); return d; }
    public InputStream openInputStream(Uri u) throws IOException { String s = u.toString(); if (s.startsWith("file://")) return new FileInputStream(s.substring(7)); throw new FileNotFoundException(s); }
    public OutputStream openOutputStream(Uri u, String mode) throws IOException { String s = u.toString(); File f = s.startsWith("file://") ? new File(s.substring(7)) : new File(dir(), u.getLastPathSegment()); return new FileOutputStream(f); }
    public OutputStream openOutputStream(Uri u) throws IOException { return openOutputStream(u, "w"); }
    public Uri insert(Uri u, ContentValues v) { Object n = v.m.get("_display_name"); return Uri.parse("content://preview/" + (n == null ? "file" : n)); }
    public int update(Uri u, ContentValues v, String w, String[] a) { return 1; } public int delete(Uri u, String w, String[] a) { return 1; }
    public android.database.Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    public void takePersistableUriPermission(Uri u, int f) {}
}
