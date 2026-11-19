package android.text;
public class Editable extends SpanBase {
    public Runnable onChange = () -> {};
    public Editable insert(int p, CharSequence t) { sb.insert(p, t); onChange.run(); return this; }
    public Editable delete(int a, int b) { sb.delete(a, b); onChange.run(); return this; }
    public Editable replace(int a, int b, CharSequence t) { sb.replace(a, b, t.toString()); onChange.run(); return this; }
    public Editable append(CharSequence t) { sb.append(t); onChange.run(); return this; }
    public void clear() { sb.setLength(0); onChange.run(); }
}
