package android.os;
public class Bundle { final java.util.Map<String, Object> m = new java.util.HashMap<>(); public void putString(String k, String v) { m.put(k, v); } public String getString(String k) { return (String) m.get(k); } }
