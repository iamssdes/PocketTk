package android.content;
public class SharedPreferences { final java.util.Map<String, Object> m = new java.util.HashMap<>(); public String getString(String k, String d) { Object v = m.get(k); return v == null ? d : (String) v; }
    public Editor edit() { return new Editor(); } public class Editor { public Editor putString(String k, String v) { m.put(k, v); return this; } public void apply() {} public boolean commit() { return true; } } }
