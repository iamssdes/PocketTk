package android.os;
public class Bundle { final java.util.Map<String, Object> m = new java.util.HashMap<>(); public void putString(String k, String v) { m.put(k, v); } public String getString(String k) { return (String) m.get(k); } public int getInt(String k, int d) { Object o = m.get(k); return o instanceof Integer ? (Integer) o : d; } }
