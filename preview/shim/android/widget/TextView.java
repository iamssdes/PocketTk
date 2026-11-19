package android.widget;
import android.content.Context; import android.graphics.*; import android.text.*; import android.view.*;
import java.awt.font.TextAttribute; import java.awt.Font; import java.text.AttributedString; import java.util.*;
public class TextView extends View {
    public interface OnEditorActionListener { boolean onEditorAction(TextView v, int actionId, KeyEvent e); }
    public static class BufferType { public static final BufferType NORMAL = new BufferType(), EDITABLE = new BufferType(), SPANNABLE = new BufferType(); }
    public static boolean caretOn = true;
    protected final Editable buf = new Editable();
    protected final List<TextWatcher> watchers = new ArrayList<>();
    protected OnEditorActionListener editor;
    int color = 0xFF000000, hintColor = 0x99888888, linkColor; float sizePx; Typeface face = Typeface.DEFAULT; int gravity = Gravity.START | Gravity.TOP;
    boolean single, allCaps, editable; int minLines, maxLines = Integer.MAX_VALUE, lines = -1, minEms; CharSequence hint; float lineAdd, lineMul = 1f; CharSequence error;
    protected boolean multiLine = true; protected int inputType;
    public TextView(Context c) {
        super(c); sizePx = 14 * c.getResources().getDisplayMetrics().scaledDensity;
        buf.onChange = () -> { requestLayout(); invalidate(); };
    }
    // ---- text
    public void setText(CharSequence s) { setText(s, BufferType.NORMAL); }
    public void setText(CharSequence s, BufferType t) {
        if (s == null) s = "";
        String before = buf.toString(), now = s.toString();
        for (TextWatcher w : new ArrayList<>(watchers)) w.beforeTextChanged(buf, 0, before.length(), now.length());
        buf.sb.setLength(0); buf.sb.append(now); buf.spans.clear(); if (s instanceof SpanBase) buf.spans.addAll(((SpanBase) s).spans);
        onTextSet(); for (TextWatcher w : new ArrayList<>(watchers)) w.onTextChanged(buf, 0, before.length(), now.length());
        requestLayout(); invalidate();
        for (TextWatcher w : new ArrayList<>(watchers)) w.afterTextChanged(buf);
    }
    protected void onTextSet() {}
    public void setText(int resId) {} public void append(CharSequence s) { setText(buf.toString() + s); }
    public CharSequence getText() { return buf; }
    public int length() { return buf.length(); }
    public void addTextChangedListener(TextWatcher w) { watchers.add(w); } public void removeTextChangedListener(TextWatcher w) { watchers.remove(w); }
    public void setOnEditorActionListener(OnEditorActionListener l) { editor = l; }
    // ---- look
    public void setTextColor(int c) { color = c; invalidate(); } public int getCurrentTextColor() { return color; }
    public void setHintTextColor(int c) { hintColor = c; invalidate(); } public void setLinkTextColor(int c) { linkColor = c; } public void setHighlightColor(int c) {}
    public void setTextSize(float sp) { setTextSize(2, sp); }
    public void setTextSize(int unit, float v) { android.util.DisplayMetrics m = mContext.getResources().getDisplayMetrics(); sizePx = unit == 0 ? v : unit == 1 ? v * m.density : unit == 2 ? v * m.scaledDensity : v; requestLayout(); invalidate(); }
    public float getTextSize() { return sizePx; }
    public void setTypeface(Typeface t) { face = t == null ? Typeface.DEFAULT : t; requestLayout(); invalidate(); }
    public void setTypeface(Typeface t, int style) { face = Typeface.create(t, style); requestLayout(); invalidate(); }
    public Typeface getTypeface() { return face; }
    public void setGravity(int g) { gravity = g; invalidate(); } public int getGravity() { return gravity; }
    public void setSingleLine() { setSingleLine(true); } public void setSingleLine(boolean b) { single = b; multiLine = !b; requestLayout(); }
    public void setMaxLines(int n) { maxLines = n; requestLayout(); } public void setMinLines(int n) { minLines = n; requestLayout(); } public void setLines(int n) { minLines = maxLines = n; requestLayout(); }
    public void setEllipsize(Object w) {} public void setAllCaps(boolean b) { allCaps = b; requestLayout(); } public void setMinEms(int e) { minEms = e; } public void setMaxEms(int e) {}
    public void setMinHeight(int h) { minH = h; requestLayout(); } public void setMinWidth(int w) { minW = w; requestLayout(); }
    public void setHint(CharSequence h) { hint = h; invalidate(); } public CharSequence getHint() { return hint; }
    public void setError(CharSequence e) { error = e; invalidate(); } public CharSequence getError() { return error; }
    public void setLineSpacing(float add, float mul) { lineAdd = add; lineMul = mul; requestLayout(); }
    public void setInputType(int t) { inputType = t; if ((t & 15) == 1) multiLine = (t & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0; else if ((t & 15) != 0) multiLine = false; }
    public int getInputType() { return inputType; }
    protected Object keyListener = this; public void setKeyListener(Object k) { keyListener = k; } public Object getKeyListener() { return keyListener; } public void setTextIsSelectable(boolean b) {} public void setCursorVisible(boolean b) {} public void setImeOptions(int o) {}
    public void setMovementMethod(Object m) {} public void setLinksClickable(boolean b) {} public void setAutoLinkMask(int m) {} public void setIncludeFontPadding(boolean b) {}
    public void setLetterSpacing(float f) {} public void setPaintFlags(int f) {} public void setHorizontallyScrolling(boolean b) {} public void setFilters(Object[] f) {} public void setTransformationMethod(Object t) {}
    public void setCompoundDrawablePadding(int p) {} public void setBreakStrategy(int s) {} public void setHyphenationFrequency(int f) {}
    public Paint getPaint() { Paint p = new Paint(); p.typeface = face; p.textSize = sizePx; return p; }
    public int getLineCount() { return lines(avail()).size(); }
    // ---- text layout
    static final class Ln { int s, e; float w; }
    protected Font font() { return fontFor(face, sizePx); }
    static final Map<String, Font> FONTS = new HashMap<>();
    static Font fontFor(Typeface t, float px) { String k = t.family + t.style + "/" + px; Font f = FONTS.get(k); if (f == null) { f = t.font(px); FONTS.put(k, f); } return f; }
    static final Map<String, Float> WIDTHS = new HashMap<>();
    static float width(Font f, String s) { if (s.isEmpty()) return 0; String k = f.getName() + f.getStyle() + f.getSize2D() + "|" + s; Float w = WIDTHS.get(k); if (w == null) { if (WIDTHS.size() > 20000) WIDTHS.clear(); w = Canvas.measure(f, s); WIDTHS.put(k, w); } return w; }
    protected String shown() { String t = buf.toString(); if ((inputType & 0xFFF) == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) && !t.isEmpty()) { StringBuilder b = new StringBuilder(); for (int i = 0; i < t.length(); i++) b.append('•'); return b.toString(); } return allCaps ? t.toUpperCase() : t; }
    protected int avail() { return Math.max(1, getWidth() - padL - padR - compoundLeft()); }
    protected int compoundLeft() { return 0; }
    List<Ln> lines(float maxW) { return layoutLines(shown(), font(), single ? Float.MAX_VALUE : maxW); }
    static List<Ln> layoutLines(String t, Font f, float maxW) {
        List<Ln> out = new ArrayList<>(); int n = t.length(), i = 0;
        while (true) {
            int e = t.indexOf('\n', i); if (e < 0) e = n;
            if (e == i) { Ln l = new Ln(); l.s = l.e = i; out.add(l); }
            else wrapPara(t, i, e, f, maxW, out);
            if (e >= n) break; i = e + 1;
            if (i == n) { Ln l = new Ln(); l.s = l.e = n; out.add(l); break; }
        }
        return out;
    }
    static void wrapPara(String t, int s, int e, Font f, float maxW, List<Ln> out) {
        int ls = s; float lw = 0; int i = s;
        while (i < e) {
            int j = i; while (j < e && t.charAt(j) != ' ') j++; while (j < e && t.charAt(j) == ' ') j++;
            String word = t.substring(i, j); float ww = width(f, word); float trimmed = ww;
            if (lw + trimmed > maxW && i > ls) { Ln l = new Ln(); l.s = ls; l.e = i; l.w = lw; out.add(l); ls = i; lw = 0; }
            if (ww > maxW && maxW > 0) {                        // a single long word: break by characters
                int k = i;
                while (k < j) { int m = k + 1; while (m < j && width(f, t.substring(ls, m + 1)) <= maxW) m++; if (m >= j) { lw = width(f, t.substring(ls, j)); break; } Ln l = new Ln(); l.s = ls; l.e = m; l.w = width(f, t.substring(ls, m)); out.add(l); ls = m; k = m; lw = 0; }
            } else lw += ww;
            i = j;
        }
        Ln l = new Ln(); l.s = ls; l.e = e; l.w = width(f, t.substring(ls, e).replaceAll(" +$", "")); out.add(l);
    }
    protected float lineH() { Paint.FontMetrics m = Canvas.metrics(font()); return (m.descent - m.ascent + m.leading) * lineMul + lineAdd; }
    @Override protected void onMeasure(int ws, int hs) {
        int wm = MeasureSpec.getMode(ws), wsz = MeasureSpec.getSize(ws), hm = MeasureSpec.getMode(hs), hsz = MeasureSpec.getSize(hs);
        int extra = padL + padR + compoundLeft(); float maxW = wm == MeasureSpec.UNSPECIFIED ? Float.MAX_VALUE : Math.max(1, wsz - extra);
        String txt = shown(); boolean showHint = txt.isEmpty() && hint != null; if (showHint) txt = hint.toString();
        List<Ln> ls = layoutLines(txt, font(), single ? Float.MAX_VALUE : maxW);
        float tw = 0; for (Ln l : ls) tw = Math.max(tw, l.w);
        if (minEms > 0) tw = Math.max(tw, minEms * width(font(), "M"));
        int w = wm == MeasureSpec.EXACTLY ? wsz : (int) Math.ceil(tw) + extra; if (wm == MeasureSpec.AT_MOST) w = Math.min(w, wsz); w = Math.max(w, minW);
        int n = Math.min(Math.max(ls.size(), minLines), maxLines); if (lines >= 0) n = lines;
        int h = (int) Math.ceil(n * lineH()) + padT + padB; h = Math.max(h, minH);
        if (hm == MeasureSpec.EXACTLY) h = hsz; else if (hm == MeasureSpec.AT_MOST) h = Math.min(h, hsz);
        setMeasuredDimension(w, h);
    }
    @Override protected void onDraw(Canvas c) {
        String txt = shown(); boolean hintMode = txt.isEmpty() && hint != null; if (hintMode) txt = hint.toString();
        Font f = font(); Paint.FontMetrics fm = Canvas.metrics(f); float lh = lineH();
        int availW = getWidth() - padL - padR - compoundLeft(), availH = getHeight() - padT - padB;
        List<Ln> ls = layoutLines(txt, f, single ? Float.MAX_VALUE : availW);
        int n = Math.min(ls.size(), maxLines); float th = n * lh;
        float y0 = padT; int gv = Gravity.v(gravity); if (gv == Gravity.CENTER_VERTICAL) y0 += (availH - th) / 2; else if (gv == Gravity.BOTTOM) y0 += availH - th;
        int gh = Gravity.h(gravity);
        c.save(); c.clipRect(padL, padT, getWidth() - padR, getHeight() - padB);
        java.awt.Graphics2D g = c.g; g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        for (int i = 0; i < n; i++) {
            Ln l = ls.get(i); if (l.e <= l.s) continue;
            float x = padL + compoundLeft(); if (gh == 1) x += (availW - l.w) / 2; else if (gh == 5) x += availW - l.w;
            float by = y0 + i * lh + (lh - (fm.descent - fm.ascent)) / 2 - fm.ascent;
            String piece = txt.substring(l.s, l.e).replaceAll("\\s+$", ""); if (piece.isEmpty()) continue;
            int col = hintMode ? hintColor : (enabled ? color : (color & 0xFFFFFF) | 0x66000000);
            if (!hintMode && !buf.spans.isEmpty()) drawSpanned(g, piece, l.s, f, x, by, col); else { g.setFont(f); g.setColor(new java.awt.Color(col, true)); g.drawString(piece, x, by); }
        }
        c.restore(); drawExtras(c, ls, f, fm, lh, y0);
    }
    private void drawSpanned(java.awt.Graphics2D g, String piece, int off, Font f, float x, float y, int col) {
        AttributedString as = new AttributedString(piece); as.addAttribute(TextAttribute.FONT, f); as.addAttribute(TextAttribute.FOREGROUND, new java.awt.Color(col, true));
        for (Object[] o : buf.spans) {
            int a = Math.max(0, (Integer) o[1] - off), b = Math.min(piece.length(), (Integer) o[2] - off); if (a >= b) continue;
            if (o[0] instanceof android.text.style.UnderlineSpan) as.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, a, b);
            else if (o[0] instanceof android.text.style.ForegroundColorSpan) as.addAttribute(TextAttribute.FOREGROUND, new java.awt.Color(((android.text.style.ForegroundColorSpan) o[0]).color, true), a, b);
        }
        g.drawString(as.getIterator(), x, y);
    }
    protected void drawExtras(Canvas c, List<Ln> ls, Font f, Paint.FontMetrics fm, float lh, float y0) {}
    @Override public boolean onTouchEvent(MotionEvent e) { return super.onTouchEvent(e); }
}
