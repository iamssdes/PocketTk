package android.text;
import java.util.*;
/** Shared storage for SpannableString, SpannableStringBuilder and Editable: characters plus spans. */
public class SpanBase implements CharSequence, Spannable {
    public final StringBuilder sb = new StringBuilder();
    public final List<Object[]> spans = new ArrayList<>();   // {what, start, end}
    public int length() { return sb.length(); } public char charAt(int i) { return sb.charAt(i); }
    public CharSequence subSequence(int a, int b) { return sb.subSequence(a, b); }
    public void setSpan(Object w, int s, int e, int f) { spans.add(new Object[]{w, s, e}); }
    public void removeSpan(Object w) { spans.removeIf(o -> o[0] == w); }
    public <T> T[] getSpans(int a, int b, Class<T> k) { List<T> r = new ArrayList<>(); for (Object[] o : spans) if (k.isInstance(o[0]) && (Integer) o[1] < b && (Integer) o[2] > a) r.add(k.cast(o[0])); @SuppressWarnings("unchecked") T[] arr = (T[]) java.lang.reflect.Array.newInstance(k, r.size()); return r.toArray(arr); }
    public int getSpanStart(Object w) { for (Object[] o : spans) if (o[0] == w) return (Integer) o[1]; return -1; }
    public int getSpanEnd(Object w) { for (Object[] o : spans) if (o[0] == w) return (Integer) o[2]; return -1; }
    @Override public String toString() { return sb.toString(); }
    public static SpanBase copyOf(CharSequence c) { SpanBase b = new SpanBase(); b.sb.append(c); if (c instanceof SpanBase) b.spans.addAll(((SpanBase) c).spans); return b; }
}
