package android.content;
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000, FLAG_GRANT_READ_URI_PERMISSION = 1, FLAG_GRANT_WRITE_URI_PERMISSION = 2, FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x40;
    public static final String ACTION_VIEW = "android.intent.action.VIEW", ACTION_SENDTO = "android.intent.action.SENDTO", ACTION_SEND = "android.intent.action.SEND", ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT",
        ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT", ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT", CATEGORY_OPENABLE = "android.intent.category.OPENABLE",
        EXTRA_TEXT = "android.intent.extra.TEXT", EXTRA_SUBJECT = "android.intent.extra.SUBJECT", EXTRA_EMAIL = "android.intent.extra.EMAIL", EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE", EXTRA_TITLE = "android.intent.extra.TITLE";
    public String action, type, cls; public android.net.Uri data; public ClipData clip; public java.util.Map<String, Object> extras = new java.util.HashMap<>();
    public Intent() {} public Intent(String a) { action = a; } public Intent(String a, android.net.Uri u) { action = a; data = u; }
    public Intent(Context c, Class<?> k) { cls = k.getName(); }
    public Intent addCategory(String c) { return this; } public Intent setType(String t) { type = t; return this; } public Intent addFlags(int f) { return this; } public Intent setFlags(int f) { return this; }
    public Intent setData(android.net.Uri u) { data = u; return this; } public Intent setAction(String a) { action = a; return this; }
    public Intent putExtra(String k, boolean v) { extras.put(k, v); return this; } public Intent putExtra(String k, String v) { extras.put(k, v); return this; } public Intent putExtra(String k, String[] v) { extras.put(k, v); return this; }
    public Intent putExtra(String k, int v) { extras.put(k, v); return this; } public Intent putExtra(String k, android.os.Parcelable v) { extras.put(k, v); return this; }
    public int getIntExtra(String k, int d) { Object o = extras.get(k); return o instanceof Integer ? (Integer) o : d; } public android.os.Bundle getBundleExtra(String k) { Object o = extras.get(k); return o instanceof android.os.Bundle ? (android.os.Bundle) o : null; }
    public Intent setClassName(String p, String c) { cls = c; return this; } public Intent setPackage(String p) { return this; }
    public android.net.Uri getData() { return data; } public ClipData getClipData() { return clip; } public String getAction() { return action; }
    @Override public String toString() { return "Intent{" + (action != null ? action : "") + (cls != null ? " cls=" + cls : "") + (data != null ? " data=" + data : "") + "}"; }
}
