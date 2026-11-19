package android.widget;
import android.content.Context; import android.view.*;
public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int gravity = -1;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, int g) { super(w, h); gravity = g; }
        public LayoutParams(ViewGroup.LayoutParams o) { super(o); if (o instanceof LayoutParams) gravity = ((LayoutParams) o).gravity; }
    }
    public FrameLayout(Context c) { super(c); }
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-1, -1); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }
    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }
    @Override protected void onMeasure(int ws, int hs) {
        int mw = 0, mh = 0;
        boolean wrapW = MeasureSpec.getMode(ws) != MeasureSpec.EXACTLY, wrapH = MeasureSpec.getMode(hs) != MeasureSpec.EXACTLY;
        for (View c : kids) {
            if (c.visibility == GONE) continue;
            measureChildWithMargins(c, ws, 0, hs, 0); LayoutParams p = (LayoutParams) c.lp;
            mw = Math.max(mw, c.mw + p.leftMargin + p.rightMargin); mh = Math.max(mh, c.mh + p.topMargin + p.bottomMargin);
        }
        int rw = resolveSize(Math.max(mw + padL + padR, getSuggestedMinimumWidth()), ws), rh = resolveSize(Math.max(mh + padT + padB, getSuggestedMinimumHeight()), hs);
        setMeasuredDimension(rw, rh);
        if (wrapW || wrapH) for (View c : kids) {
            if (c.visibility == GONE) continue; LayoutParams p = (LayoutParams) c.lp;
            boolean mwp = p.width == -1 && wrapW, mhp = p.height == -1 && wrapH; if (!mwp && !mhp) continue;
            c.measure(mwp ? MeasureSpec.makeMeasureSpec(rw - padL - padR - p.leftMargin - p.rightMargin, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(c.mw, MeasureSpec.EXACTLY),
                      mhp ? MeasureSpec.makeMeasureSpec(rh - padT - padB - p.topMargin - p.bottomMargin, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(c.mh, MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (View c : kids) {
            if (c.visibility == GONE) continue; LayoutParams p = (LayoutParams) c.lp;
            int g = p.gravity >= 0 ? p.gravity : (Gravity.START | Gravity.TOP);
            int gh = Gravity.h(g), gv = Gravity.v(g), x, y;
            if (gh == 5) x = w - padR - p.rightMargin - c.mw; else if (gh == 1) x = padL + p.leftMargin + (w - padL - padR - p.leftMargin - p.rightMargin - c.mw) / 2; else x = padL + p.leftMargin;
            if (gv == Gravity.BOTTOM) y = h - padB - p.bottomMargin - c.mh; else if (gv == Gravity.CENTER_VERTICAL) y = padT + p.topMargin + (h - padT - padB - p.topMargin - p.bottomMargin - c.mh) / 2; else y = padT + p.topMargin;
            c.layout(x, y, x + c.mw, y + c.mh);
        }
    }
}
