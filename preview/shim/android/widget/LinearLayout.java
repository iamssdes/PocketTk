package android.widget;
import android.content.Context; import android.view.*;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1, SHOW_DIVIDER_NONE = 0;
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight; public int gravity = -1;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float wt) { super(w, h); weight = wt; }
        public LayoutParams(ViewGroup.LayoutParams o) { super(o); if (o instanceof LayoutParams) { weight = ((LayoutParams) o).weight; gravity = ((LayoutParams) o).gravity; } }
    }
    int orientation = HORIZONTAL, gravity = Gravity.START | Gravity.TOP;
    public LinearLayout(Context c) { super(c); }
    public void setOrientation(int o) { orientation = o; requestLayout(); } public int getOrientation() { return orientation; }
    public void setGravity(int g) { gravity = g; requestLayout(); }
    public void setWeightSum(float w) {} public void setBaselineAligned(boolean b) {} public void setShowDividers(int d) {}
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return orientation == VERTICAL ? new LayoutParams(-1, -2) : new LayoutParams(-2, -2); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }
    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }

    @Override protected void onMeasure(int ws, int hs) {
        boolean v = orientation == VERTICAL;
        int mainSpec = v ? hs : ws, crossSpec = v ? ws : hs;
        int mainMode = MeasureSpec.getMode(mainSpec), mainSize = MeasureSpec.getSize(mainSpec);
        int crossMode = MeasureSpec.getMode(crossSpec), crossSize = MeasureSpec.getSize(crossSpec);
        int padMain = v ? padT + padB : padL + padR, padCross = v ? padL + padR : padT + padB;
        float totalW = 0; int used = 0, maxCross = 0;
        for (View c : kids) {
            if (c.visibility == GONE) continue;
            LayoutParams p = (LayoutParams) c.lp; totalW += p.weight;
            int mMain = v ? p.topMargin + p.bottomMargin : p.leftMargin + p.rightMargin;
            int mCross = v ? p.leftMargin + p.rightMargin : p.topMargin + p.bottomMargin;
            int dimMain = v ? p.height : p.width;
            boolean excess = p.weight > 0 && dimMain == 0;
            if (excess && mainMode == MeasureSpec.EXACTLY) { used += mMain; continue; }
            int oldDim = dimMain; if (excess) { if (v) p.height = -2; else p.width = -2; }
            if (v) measureChildWithMargins(c, ws, 0, hs, mainMode == MeasureSpec.UNSPECIFIED ? 0 : used);
            else measureChildWithMargins(c, ws, mainMode == MeasureSpec.UNSPECIFIED ? 0 : used, hs, 0);
            if (excess) { if (v) p.height = oldDim; else p.width = oldDim; }
            used += (v ? c.mh : c.mw) + mMain;
            maxCross = Math.max(maxCross, (v ? c.mw : c.mh) + mCross);
        }
        int total = used + padMain;
        int resolvedMain = resolveSize(Math.max(total, v ? getSuggestedMinimumHeight() : getSuggestedMinimumWidth()), mainSpec);
        int remaining = resolvedMain - total;
        if (totalW > 0 && mainMode != MeasureSpec.UNSPECIFIED) {
            int share = remaining; float left = totalW; maxCross = 0; used = 0;
            for (View c : kids) {
                if (c.visibility == GONE) continue;
                LayoutParams p = (LayoutParams) c.lp;
                int mMain = v ? p.topMargin + p.bottomMargin : p.leftMargin + p.rightMargin;
                int mCross = v ? p.leftMargin + p.rightMargin : p.topMargin + p.bottomMargin;
                int cm = v ? c.mh : c.mw; boolean measured = !(p.weight > 0 && (v ? p.height : p.width) == 0 && mainMode == MeasureSpec.EXACTLY);
                if (p.weight > 0) {
                    int extra = left > 0 ? (int) (share * p.weight / left) : 0; share -= extra; left -= p.weight;
                    int size = (measured ? cm : 0) + extra; if (size < 0) size = 0;
                    int ms = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
                    if (v) c.measure(getChildMeasureSpec(ws, padL + padR + p.leftMargin + p.rightMargin, p.width), ms);
                    else c.measure(ms, getChildMeasureSpec(hs, padT + padB + p.topMargin + p.bottomMargin, p.height));
                }
                used += (v ? c.mh : c.mw) + mMain;
                maxCross = Math.max(maxCross, (v ? c.mw : c.mh) + mCross);
            }
            total = used + padMain;
        }
        int resolvedCross = resolveSize(Math.max(maxCross + padCross, v ? getSuggestedMinimumWidth() : getSuggestedMinimumHeight()), crossSpec);
        if (crossMode != MeasureSpec.EXACTLY) {            // match_parent children get the final cross size
            for (View c : kids) {
                if (c.visibility == GONE) continue;
                LayoutParams p = (LayoutParams) c.lp;
                if ((v ? p.width : p.height) == LayoutParams.MATCH_PARENT) {
                    int cs = MeasureSpec.makeMeasureSpec(resolvedCross - padCross - (v ? p.leftMargin + p.rightMargin : p.topMargin + p.bottomMargin), MeasureSpec.EXACTLY);
                    int ms = MeasureSpec.makeMeasureSpec(v ? c.mh : c.mw, MeasureSpec.EXACTLY);
                    if (v) c.measure(cs, ms); else c.measure(ms, cs);
                }
            }
        }
        if (v) setMeasuredDimension(resolvedCross, resolvedMain); else setMeasuredDimension(resolvedMain, resolvedCross);
    }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
        boolean v = orientation == VERTICAL;
        int w = r - l, h = b - t, usedMain = 0;
        for (View c : kids) if (c.visibility != GONE) { LayoutParams p = (LayoutParams) c.lp; usedMain += v ? c.mh + p.topMargin + p.bottomMargin : c.mw + p.leftMargin + p.rightMargin; }
        int availMain = v ? h - padT - padB : w - padL - padR;
        int pos = v ? padT : padL;
        int g = gravity;
        if (v) { int gv = Gravity.v(g); if (gv == Gravity.BOTTOM) pos = h - padB - usedMain; else if (gv == Gravity.CENTER_VERTICAL) pos = padT + (availMain - usedMain) / 2; }
        else { int gh = Gravity.h(g); if (gh == 5) pos = w - padR - usedMain; else if (gh == 1) pos = padL + (availMain - usedMain) / 2; }
        for (View c : kids) {
            if (c.visibility == GONE) continue;
            LayoutParams p = (LayoutParams) c.lp;
            int cg = p.gravity >= 0 ? p.gravity : g;
            if (v) {
                int x; int gh = Gravity.h(cg); int availX = w - padL - padR - p.leftMargin - p.rightMargin;
                if (gh == 5) x = w - padR - p.rightMargin - c.mw; else if (gh == 1) x = padL + p.leftMargin + (availX - c.mw) / 2; else x = padL + p.leftMargin;
                pos += p.topMargin; c.layout(x, pos, x + c.mw, pos + c.mh); pos += c.mh + p.bottomMargin;
            } else {
                int y; int gv = Gravity.v(cg); int availY = h - padT - padB - p.topMargin - p.bottomMargin;
                if (gv == Gravity.BOTTOM) y = h - padB - p.bottomMargin - c.mh; else if (gv == Gravity.CENTER_VERTICAL) y = padT + p.topMargin + (availY - c.mh) / 2; else y = padT + p.topMargin;
                pos += p.leftMargin; c.layout(pos, y, pos + c.mw, y + c.mh); pos += c.mw + p.rightMargin;
            }
        }
    }
}
