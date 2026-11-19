package android.graphics;
public class Paint {
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    public static final int ANTI_ALIAS_FLAG = 1;
    public int color = 0xFF000000; public Style style = Style.FILL; public float strokeWidth = 1f, textSize = 14f; public boolean aa = true;
    public Typeface typeface = Typeface.DEFAULT; public Align align = Align.LEFT;
    public Paint() {} public Paint(int flags) {}
    public void setColor(int c) { color = c; } public int getColor() { return color; }
    public void setStyle(Style s) { style = s; } public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setTextSize(float s) { textSize = s; } public float getTextSize() { return textSize; }
    public void setAntiAlias(boolean b) { aa = b; } public void setTypeface(Typeface t) { typeface = t == null ? Typeface.DEFAULT : t; }
    public void setTextAlign(Align a) { align = a; } public void setAlpha(int a) { color = (color & 0xFFFFFF) | (a << 24); }
    public void setStrokeCap(Object o) {} public void setStrokeJoin(Object o) {}
    public float measureText(String s) { return Canvas.measure(typeface.font(textSize), s); }
    public float measureText(CharSequence s, int a, int b) { return measureText(s.subSequence(a, b).toString()); }
    public static class FontMetrics { public float ascent, descent, top, bottom, leading; }
    public FontMetrics getFontMetrics() { return Canvas.metrics(typeface.font(textSize)); }
    public float descent() { return getFontMetrics().descent; } public float ascent() { return getFontMetrics().ascent; }
}
