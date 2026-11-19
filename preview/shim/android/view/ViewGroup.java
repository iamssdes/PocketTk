package android.view;
import android.content.Context; import android.graphics.*; import java.util.*;
public class ViewGroup extends View implements ViewParent {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2, FILL_PARENT = -1;
        public int width, height;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(LayoutParams o) { width = o.width; height = o.height; }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public MarginLayoutParams(int w, int h) { super(w, h); }
        public MarginLayoutParams(LayoutParams o) { super(o); if (o instanceof MarginLayoutParams) { MarginLayoutParams m = (MarginLayoutParams) o; leftMargin = m.leftMargin; topMargin = m.topMargin; rightMargin = m.rightMargin; bottomMargin = m.bottomMargin; } }
        public void setMargins(int l, int t, int r, int b) { leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b; }
    }
    public final List<View> kids = new ArrayList<>();
    public boolean isRootOfWindow;
    public ViewGroup(Context c) { super(c); }
    public int getChildCount() { return kids.size(); }
    public View getChildAt(int i) { return i >= 0 && i < kids.size() ? kids.get(i) : null; }
    public int indexOfChild(View v) { return kids.indexOf(v); }
    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    protected LayoutParams generateLayoutParams(LayoutParams p) { return new LayoutParams(p); }
    protected boolean checkLayoutParams(LayoutParams p) { return p != null; }
    public void addView(View v) { addView(v, -1); }
    public void addView(View v, int i) { addView(v, i, v.lp != null ? v.lp : generateDefaultLayoutParams()); }
    public void addView(View v, LayoutParams p) { addView(v, -1, p); }
    public void addView(View v, int w, int h) { addView(v, -1, generateLayoutParams(new LayoutParams(w, h))); }
    public void addView(View v, int i, LayoutParams p) {
        if (v.parent != null) throw new IllegalStateException("The specified child already has a parent. You must call removeView() on the child's parent first.");
        if (!checkLayoutParams(p)) p = generateLayoutParams(p);
        v.lp = p; v.parent = this; if (i < 0 || i > kids.size()) kids.add(v); else kids.add(i, v);
        requestLayout();
    }
    public void removeView(View v) { if (kids.remove(v)) { v.parent = null; if (View.focused == v) View.focused = null; requestLayout(); } }
    public void removeViewAt(int i) { if (i >= 0 && i < kids.size()) removeView(kids.get(i)); }
    public void removeAllViews() { for (View v : new ArrayList<>(kids)) removeView(v); }
    public void updateViewLayout(View v, LayoutParams p) { v.lp = p; requestLayout(); }
    public View findViewById(int i) { if (id == i) return this; for (View v : kids) { View f = v.findViewById(i); if (f != null) return f; } return null; }
    public ViewParent getParent() { return parent; }
    // ---- measuring helpers
    public static int getChildMeasureSpec(int spec, int pad, int dim) {
        int m = MeasureSpec.getMode(spec), size = Math.max(0, MeasureSpec.getSize(spec) - pad), rs = 0, rm = 0;
        if (m == MeasureSpec.EXACTLY) {
            if (dim >= 0) { rs = dim; rm = MeasureSpec.EXACTLY; } else if (dim == LayoutParams.MATCH_PARENT) { rs = size; rm = MeasureSpec.EXACTLY; } else { rs = size; rm = MeasureSpec.AT_MOST; }
        } else if (m == MeasureSpec.AT_MOST) {
            if (dim >= 0) { rs = dim; rm = MeasureSpec.EXACTLY; } else if (dim == LayoutParams.MATCH_PARENT) { rs = size; rm = MeasureSpec.AT_MOST; } else { rs = size; rm = MeasureSpec.AT_MOST; }
        } else {
            if (dim >= 0) { rs = dim; rm = MeasureSpec.EXACTLY; } else { rs = 0; rm = MeasureSpec.UNSPECIFIED; }
        }
        return MeasureSpec.makeMeasureSpec(rs, rm);
    }
    protected void measureChild(View c, int ws, int hs) {
        LayoutParams p = c.lp;
        c.measure(getChildMeasureSpec(ws, padL + padR, p.width), getChildMeasureSpec(hs, padT + padB, p.height));
    }
    protected void measureChildWithMargins(View c, int ws, int usedW, int hs, int usedH) {
        MarginLayoutParams p = (MarginLayoutParams) c.lp;
        c.measure(getChildMeasureSpec(ws, padL + padR + p.leftMargin + p.rightMargin + usedW, p.width),
                  getChildMeasureSpec(hs, padT + padB + p.topMargin + p.bottomMargin + usedH, p.height));
    }
    protected void measureChildren(int ws, int hs) { for (View c : kids) if (c.visibility != GONE) measureChild(c, ws, hs); }
    @Override protected void onMeasure(int ws, int hs) { measureChildren(ws, hs); int w = 0, h = 0; for (View c : kids) if (c.visibility != GONE) { w = Math.max(w, c.mw); h = Math.max(h, c.mh); } setMeasuredDimension(resolveSize(w + padL + padR, ws), resolveSize(h + padT + padB, hs)); }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) { for (View c : kids) if (c.visibility != GONE) c.layout(padL, padT, padL + c.mw, padT + c.mh); }
    // ---- drawing (children clipped to this view)
    @Override protected void dispatchDraw(Canvas c) {
        c.save(); c.clipRect(0, 0, getWidth(), getHeight());
        for (View v : new ArrayList<>(kids)) {
            if (v.visibility != VISIBLE) continue;
            c.save(); c.translate(v.left - scrollX, v.top - scrollY); v.draw(c); c.restore();
        }
        c.restore();
    }
    // ---- touch: top-most child under the finger gets the gesture
    private View touchTarget;
    protected boolean onInterceptTouchEvent(MotionEvent e) { return false; }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (touch != null && touch.onTouch(this, e)) return true;
        int a = e.action;
        if (a == MotionEvent.ACTION_DOWN) {
            touchTarget = null;
            if (!onInterceptTouchEvent(e)) {
                float x = e.x + scrollX, y = e.y + scrollY;
                for (int i = kids.size() - 1; i >= 0; i--) {
                    View c = kids.get(i);
                    if (c.visibility != VISIBLE) continue;
                    if (x >= c.left && x < c.right && y >= c.top && y < c.bottom) {
                        MotionEvent ce = MotionEvent.obtain(e); ce.offsetLocation(scrollX - c.left, scrollY - c.top);
                        if (c.dispatchTouchEvent(ce)) { touchTarget = c; return true; }
                    }
                }
            }
            return onTouchEvent(e);
        }
        if (touchTarget != null) {
            if (a == MotionEvent.ACTION_MOVE && onInterceptTouchEvent(e)) {
                MotionEvent ce = MotionEvent.obtain(e); ce.action = MotionEvent.ACTION_CANCEL; ce.offsetLocation(scrollX - touchTarget.left, scrollY - touchTarget.top);
                touchTarget.dispatchTouchEvent(ce); touchTarget = null;
                MotionEvent de = MotionEvent.obtain(e); de.action = MotionEvent.ACTION_DOWN; onTouchEvent(de);
                return onTouchEvent(e);
            }
            MotionEvent ce = MotionEvent.obtain(e); ce.offsetLocation(scrollX - touchTarget.left, scrollY - touchTarget.top);
            View t = touchTarget; if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) touchTarget = null;
            return t.dispatchTouchEvent(ce);
        }
        return onTouchEvent(e);
    }
    @Override public boolean onWheel(float x, float y, float dy) {
        float px = x + scrollX, py = y + scrollY;
        for (int i = kids.size() - 1; i >= 0; i--) {
            View c = kids.get(i);
            if (c.visibility == VISIBLE && px >= c.left && px < c.right && py >= c.top && py < c.bottom && c.onWheel(px - c.left, py - c.top, dy)) return true;
        }
        return false;
    }
    public void fireGlobalLayout() { if (vto != null) vto.fireLayout(); for (View v : kids) { if (v instanceof ViewGroup) ((ViewGroup) v).fireGlobalLayout(); else if (v.vto != null) v.vto.fireLayout(); } }
    public void setClipChildren(boolean b) {}
    public void setMotionEventSplittingEnabled(boolean b) {}
    public void setDescendantFocusability(int f) {}
    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000, FOCUS_AFTER_DESCENDANTS = 0x40000, FOCUS_BLOCK_DESCENDANTS = 0x60000;
}
