package android.util;
public class Log {
    public static int d(String t, String m) { return 0; } public static int i(String t, String m) { System.err.println("I/" + t + ": " + m); return 0; }
    public static int w(String t, String m) { System.err.println("W/" + t + ": " + m); return 0; }
    public static int e(String t, String m) { System.err.println("E/" + t + ": " + m); return 0; }
    public static int e(String t, String m, Throwable x) { System.err.println("E/" + t + ": " + m + " " + x); return 0; }
    public static String getStackTraceString(Throwable t) { java.io.StringWriter w = new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(w)); return w.toString(); }
}
