package android.text;
public class SpannableStringBuilder extends SpanBase {
    public SpannableStringBuilder() {} public SpannableStringBuilder(CharSequence c) { append(c); }
    public SpannableStringBuilder append(CharSequence s) { int st = sb.length(); sb.append(s); if (s instanceof SpanBase) for (Object[] o : ((SpanBase) s).spans) spans.add(new Object[]{o[0], (Integer) o[1] + st, (Integer) o[2] + st}); return this; }
    public SpannableStringBuilder append(CharSequence s, Object what, int flags) { int st = sb.length(); sb.append(s); spans.add(new Object[]{what, st, sb.length()}); return this; }
}
