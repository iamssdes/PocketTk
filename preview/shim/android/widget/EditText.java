package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*; import android.text.*; import java.util.*;
public class EditText extends TextView {
    int sel; public EditText(Context c) { super(c); editable = true; focusable = true; float d = c.getResources().getDisplayMetrics().density; padL = padR = (int) (4 * d); padT = padB = (int) (8 * d); minH = (int) (48 * d); }
    @Override public Editable getText() { return buf; }
    @Override protected void onTextSet() { sel = buf.length(); }
    public void setSelection(int i) { sel = Math.max(0, Math.min(i, buf.length())); invalidate(); } public void setSelection(int a, int b) { setSelection(b); }
    public int getSelectionStart() { return sel; } public int getSelectionEnd() { return sel; } public void selectAll() {}
    @Override public boolean performClick() { return super.performClick(); }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false;
        if (e.action == MotionEvent.ACTION_UP) { requestFocus(); sel = indexAt(e.x, e.y); invalidate(); }
        return true;
    }
    int indexAt(float x, float y) {
        java.awt.Font f = font(); float lh = lineH(); List<Ln> ls = lines(avail()); String t = shown();
        int li = (int) Math.max(0, Math.min(ls.size() - 1, (y - padT) / lh)); Ln l = ls.get(li); float px = x - padL;
        int idx = l.s; while (idx < l.e && width(f, t.substring(l.s, idx + 1)) < px + 0.5f * (width(f, t.substring(idx, idx + 1)))) idx++;
        return Math.min(idx, buf.length());
    }
    @Override protected void drawDefaultBackground(Canvas c, int w, int h) {
        float d = mContext.getResources().getDisplayMetrics().density; Paint p = new Paint(); boolean f = hasFocus(); p.color = f ? 0xFF4C7BD9 : 0xFF888888;
        c.drawRect(4 * d, h - (f ? 8 : 6) * d, w - 4 * d, h - (f ? 6 : 5) * d, p);
    }
    @Override protected void drawExtras(Canvas c, List<Ln> ls, java.awt.Font f, Paint.FontMetrics fm, float lh, float y0) {
        if (!hasFocus() || !caretOn) return;
        String t = shown(); int pos = Math.min(sel, t.length()); int li = 0;
        for (int i = 0; i < ls.size(); i++) if (pos >= ls.get(i).s) li = i;
        Ln l = ls.get(li); int a = Math.min(pos, l.e < l.s ? l.s : Math.max(l.s, Math.min(pos, t.length())));
        float x = padL + width(f, t.substring(l.s, Math.max(l.s, Math.min(a, t.length())))); int gh = Gravity.h(gravity); int availW = getWidth() - padL - padR;
        if (gh == 1) x += (availW - l.w) / 2; else if (gh == 5) x += availW - l.w;
        Paint p = new Paint(); p.color = 0xFF2A5BD7; p.strokeWidth = Math.max(1, mContext.getResources().getDisplayMetrics().density);
        c.drawLine(x, y0 + li * lh + 2, x, y0 + (li + 1) * lh - 2, p);
    }
    // ---- keyboard (the preview window forwards typing here)
    void edit(int a, int b, String ins) {
        String before = buf.toString(); for (TextWatcher w : new ArrayList<>(watchers)) w.beforeTextChanged(buf, a, b - a, ins.length());
        buf.sb.replace(a, b, ins); sel = a + ins.length();
        for (TextWatcher w : new ArrayList<>(watchers)) w.onTextChanged(buf, a, b - a, ins.length());
        requestLayout(); invalidate(); for (TextWatcher w : new ArrayList<>(watchers)) w.afterTextChanged(buf);
    }
    public void typeChar(char ch) {
        if (!enabled) return;
        int base = inputType & 15; if (base == 2 && !(Character.isDigit(ch) || ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0 && ch == '.') || ((inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0 && ch == '-'))) return;
        if (ch == '\n') { if (!multiLine) { if (editor != null) editor.onEditorAction(this, 6, null); return; } }
        edit(sel, sel, String.valueOf(ch));
    }
    public void paste(String s) { if (!multiLine) s = s.replace("\n", " "); edit(sel, sel, s); }
    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        switch (code) {
            case KeyEvent.KEYCODE_DEL: if (sel > 0) edit(sel - 1, sel, ""); return true;
            case KeyEvent.KEYCODE_FORWARD_DEL: if (sel < buf.length()) edit(sel, sel + 1, ""); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: setSelection(sel - 1); return true; case KeyEvent.KEYCODE_DPAD_RIGHT: setSelection(sel + 1); return true;
            case KeyEvent.KEYCODE_MOVE_HOME: setSelection(0); return true; case KeyEvent.KEYCODE_MOVE_END: setSelection(buf.length()); return true;
        }
        return false;
    }
}
