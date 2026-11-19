package android.text;
public class SpannableString extends SpanBase { public SpannableString(CharSequence c) { sb.append(c); if (c instanceof SpanBase) spans.addAll(((SpanBase) c).spans); } }
