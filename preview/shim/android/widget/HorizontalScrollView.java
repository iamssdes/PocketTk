package android.widget;
import android.content.Context; import android.view.*;
public class HorizontalScrollView extends FrameLayout {
    float downX, lastX; boolean dragging; boolean fill;
    public HorizontalScrollView(Context c) { super(c); }
    public void setFillViewport(boolean f) { fill = f; requestLayout(); }
    int contentW() { View c = kids.isEmpty() ? null : kids.get(0); return c == null ? 0 : c.getWidth(); }
    int maxScroll() { return Math.max(0, contentW() + padL + padR - getWidth()); }
    @Override protected void onMeasure(int ws, int hs) {
        View c = kids.isEmpty() ? null : kids.get(0); int h = 0;
        if (c != null) { MarginLayoutParams p = (MarginLayoutParams) c.lp;
            c.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), getChildMeasureSpec(hs, padT + padB + p.topMargin + p.bottomMargin, p.height)); h = c.mh + padT + padB; }
        int w = resolveSize(0, ws); setMeasuredDimension(MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED ? (c == null ? 0 : c.mw) : w, resolveSize(h, hs));
        if (c != null && fill && c.mw < mw) c.measure(MeasureSpec.makeMeasureSpec(mw - padL - padR, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(c.mh, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) { for (View c : kids) if (c.visibility != GONE) c.layout(padL, padT, padL + c.mw, padT + c.mh); scrollX = Math.max(0, Math.min(scrollX, maxScroll())); }
    @Override public void scrollTo(int x, int y) { scrollX = Math.max(0, Math.min(x, maxScroll())); invalidate(); }
    @Override protected boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.action == MotionEvent.ACTION_DOWN) { downX = lastX = e.x; dragging = false; return false; }
        if (e.action == MotionEvent.ACTION_MOVE && !dragging && Math.abs(e.x - downX) > 8 && maxScroll() > 0) { dragging = true; lastX = e.x; return true; }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.action) {
            case MotionEvent.ACTION_DOWN: downX = lastX = e.x; return maxScroll() > 0;
            case MotionEvent.ACTION_MOVE: scrollTo(scrollX + (int) (lastX - e.x), 0); lastX = e.x; return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: dragging = false; return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean onWheel(float x, float y, float dy) { if (super.onWheel(x, y, dy)) return true; if (maxScroll() == 0) return false; scrollTo(scrollX + (int) (dy * 40), 0); return true; }
}
