package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*;
public class ScrollView extends FrameLayout {
    boolean fill; float downY, lastY; boolean dragging; boolean vertical = true;
    public ScrollView(Context c) { super(c); }
    public void setFillViewport(boolean f) { fill = f; requestLayout(); }
    int contentH() { View c = kids.isEmpty() ? null : kids.get(0); return c == null ? 0 : c.getHeight(); }
    int maxScroll() { return Math.max(0, contentH() + padT + padB - getHeight()); }
    @Override protected void onMeasure(int ws, int hs) {
        View c = kids.isEmpty() ? null : kids.get(0);
        int w = resolveSize(Math.max(minW, c == null ? 0 : c.mw + padL + padR), ws);
        if (c != null) {
            MarginLayoutParams p = (MarginLayoutParams) c.lp;
            int cws = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(ws), MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY), padL + padR + p.leftMargin + p.rightMargin, p.width);
            if (p.width == -2) cws = getChildMeasureSpec(ws, padL + padR + p.leftMargin + p.rightMargin, p.width);
            c.measure(cws, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            w = resolveSize(Math.max(minW, c.mw + padL + padR + p.leftMargin + p.rightMargin), ws);
        }
        int h = resolveSize(c == null ? 0 : c.mh + padT + padB, hs);
        setMeasuredDimension(w, h);
        if (c != null && fill && c.mh + padT + padB < h) c.measure(MeasureSpec.makeMeasureSpec(c.mw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h - padT - padB, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
        for (View c : kids) if (c.visibility != GONE) c.layout(padL, padT, padL + c.mw, padT + c.mh);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
    }
    @Override public void scrollTo(int x, int y) { scrollY = Math.max(0, Math.min(y, maxScroll())); invalidate(); }
    public boolean fullScroll(int d) { scrollTo(0, d == View.FOCUS_DOWN ? maxScroll() : 0); return true; }
    public void smoothScrollTo(int x, int y) { scrollTo(x, y); }
    public boolean arrowScroll(int d) { return false; }
    @Override protected boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.action == MotionEvent.ACTION_DOWN) { downY = e.y; lastY = e.y; dragging = false; return false; }
        if (e.action == MotionEvent.ACTION_MOVE && !dragging && Math.abs(e.y - downY) > 8 && maxScroll() > 0) { dragging = true; lastY = e.y; return true; }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.action) {
            case MotionEvent.ACTION_DOWN: downY = lastY = e.y; return maxScroll() > 0;
            case MotionEvent.ACTION_MOVE: scrollTo(0, scrollY + (int) (lastY - e.y)); lastY = e.y; return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: dragging = false; return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean onWheel(float x, float y, float dy) {
        if (super.onWheel(x, y, dy)) return true;
        if (maxScroll() == 0) return false;
        scrollTo(0, scrollY + (int) (dy * 40 * mContext.getResources().getDisplayMetrics().density / 2)); return true;
    }
    @Override protected void onDrawForeground(Canvas c) {
        int max = maxScroll(); if (max <= 0) return;
        float d = mContext.getResources().getDisplayMetrics().density; int h = getHeight();
        float th = Math.max(24 * d, h * (float) h / (h + max)), ty = (h - th) * scrollY / max; Paint p = new Paint(); p.color = 0x66000000;
        c.drawRoundRect(new RectF(getWidth() - 3 * d, ty, getWidth() - 1 * d, ty + th), d, d, p);
    }
}
