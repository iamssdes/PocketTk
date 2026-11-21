package android.util;
public class Base64 {
    public static final int DEFAULT = 0, NO_WRAP = 2, URL_SAFE = 8;
    public static byte[] decode(String s, int flags) { String t = s.replaceAll("\\s", ""); try { return ((flags & URL_SAFE) != 0 ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder()).decode(t); } catch (IllegalArgumentException e) { throw e; } }
    public static String encodeToString(byte[] b, int flags) { return java.util.Base64.getEncoder().encodeToString(b); }
}
