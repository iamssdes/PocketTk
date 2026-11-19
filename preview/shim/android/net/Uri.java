package android.net;
public class Uri { final String s; Uri(String s) { this.s = s; } public static Uri parse(String x) { return new Uri(x); } public static Uri fromFile(java.io.File f) { return new Uri("file://" + f.getAbsolutePath()); }
    public String getLastPathSegment() { String t = s.endsWith("/") ? s.substring(0, s.length() - 1) : s; return t.substring(t.lastIndexOf('/') + 1); } public String getPath() { return s; }
    @Override public String toString() { return s; } @Override public boolean equals(Object o) { return o instanceof Uri && ((Uri) o).s.equals(s); } @Override public int hashCode() { return s.hashCode(); } }
