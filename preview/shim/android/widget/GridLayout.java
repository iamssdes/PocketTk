package android.widget;
import android.content.Context; import android.view.*; import java.util.*;
public class GridLayout extends ViewGroup {
    public static class Alignment { final int a; Alignment(int a) { this.a = a; } }
    public static final Alignment FILL = new Alignment(7), LEFT = new Alignment(3), RIGHT = new Alignment(5), CENTER = new Alignment(1), TOP = new Alignment(48), BOTTOM = new Alignment(80), START = LEFT, END = RIGHT, BASELINE = TOP;
    public static final int UNDEFINED = Integer.MIN_VALUE, HORIZONTAL = 0, VERTICAL = 1;
    public static class Spec { public int start, size = 1; public float weight; public Alignment align = FILL; }
    public static Spec spec(int start) { return spec(start, 1, FILL, 0f); }
    public static Spec spec(int start, int size) { return spec(start, size, FILL, 0f); }
    public static Spec spec(int start, Alignment a) { return spec(start, 1, a, 0f); }
    public static Spec spec(int start, float w) { return spec(start, 1, FILL, w); }
    public static Spec spec(int start, int size, float w) { return spec(start, size, FILL, w); }
    public static Spec spec(int start, int size, Alignment a) { return spec(start, size, a, 0f); }
    public static Spec spec(int start, int size, Alignment a, float w) { Spec p = new Spec(); p.start = start; p.size = size; p.align = a; p.weight = w; return p; }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public Spec rowSpec = spec(UNDEFINED), columnSpec = spec(UNDEFINED);
        public LayoutParams() { super(-2, -2); }
        public LayoutParams(Spec r, Spec c) { super(-2, -2); rowSpec = r; columnSpec = c; }
        public LayoutParams(ViewGroup.LayoutParams o) { super(o); if (o instanceof LayoutParams) { rowSpec = ((LayoutParams) o).rowSpec; columnSpec = ((LayoutParams) o).columnSpec; } }
    }
    int colCount = UNDEFINED, rowCount = UNDEFINED;
    int[] colW = new int[0], rowH = new int[0], colX, rowY; int[][] cell;
    public GridLayout(Context c) { super(c); }
    public void setColumnCount(int n) { colCount = n; requestLayout(); } public void setRowCount(int n) { rowCount = n; requestLayout(); }
    public void setOrientation(int o) {} public void setUseDefaultMargins(boolean b) {}
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }
    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }
    // resolve each child's row/col (auto placement for undefined)
    private int[][] place() {
        List<int[]> out = new ArrayList<>(); int cols = colCount; boolean[][] used = new boolean[512][]; int curR = 0, curC = 0;
        if (cols == UNDEFINED) { cols = 1; for (View c : kids) { LayoutParams p = (LayoutParams) c.lp; if (p.columnSpec.start != UNDEFINED) cols = Math.max(cols, p.columnSpec.start + p.columnSpec.size); } }
        for (View c : kids) {
            LayoutParams p = (LayoutParams) c.lp; int r = p.rowSpec.start, cc = p.columnSpec.start, rs = p.rowSpec.size, cs = p.columnSpec.size;
            if (c.visibility == GONE) { out.add(new int[]{0, 0, 1, 1}); continue; }
            if (r == UNDEFINED || cc == UNDEFINED) {
                while (true) {
                    if (curC + cs > cols) { curC = 0; curR++; }
                    if (curR >= used.length - 1) break;
                    boolean free = true; for (int i = 0; i < rs && free; i++) for (int j = 0; j < cs; j++) if (used[curR + i] != null && used[curR + i][curC + j]) { free = false; break; }
                    if (free) break; curC++;
                }
                if (r == UNDEFINED) r = curR; if (cc == UNDEFINED) cc = curC; curC = cc + cs;
            }
            for (int i = 0; i < rs; i++) { if (r + i >= used.length) break; if (used[r + i] == null) used[r + i] = new boolean[cols + 64]; for (int j = 0; j < cs; j++) if (cc + j < used[r + i].length) used[r + i][cc + j] = true; }
            out.add(new int[]{r, cc, rs, cs});
        }
        return out.toArray(new int[0][]);
    }
    @Override protected void onMeasure(int ws, int hs) {
        cell = place(); int nc = 1, nr = 1;
        for (int i = 0; i < kids.size(); i++) if (kids.get(i).visibility != GONE) { nc = Math.max(nc, cell[i][1] + cell[i][3]); nr = Math.max(nr, cell[i][0] + cell[i][2]); }
        colW = new int[nc]; rowH = new int[nr]; float[] cw = new float[nc], rw = new float[nr];
        int availW = MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 4 : MeasureSpec.getSize(ws) - padL - padR;
        for (int pass = 0; pass < 1; pass++) for (int i = 0; i < kids.size(); i++) {
            View c = kids.get(i); if (c.visibility == GONE) continue; LayoutParams p = (LayoutParams) c.lp;
            measureChildWithMargins(c, ws, 0, hs, 0);
            int w = c.mw + p.leftMargin + p.rightMargin, h = c.mh + p.topMargin + p.bottomMargin; int[] ce = cell[i];
            for (int j = 0; j < ce[3]; j++) { colW[ce[1] + j] = Math.max(colW[ce[1] + j], w / ce[3]); if (p.columnSpec.weight > 0) cw[ce[1] + j] = Math.max(cw[ce[1] + j], p.columnSpec.weight); }
            for (int j = 0; j < ce[2]; j++) { rowH[ce[0] + j] = Math.max(rowH[ce[0] + j], h / ce[2]); if (p.rowSpec.weight > 0) rw[ce[0] + j] = Math.max(rw[ce[0] + j], p.rowSpec.weight); }
        }
        int sumW = 0, sumH = 0; for (int x : colW) sumW += x; for (int x : rowH) sumH += x;
        int rwid = resolveSize(Math.max(sumW + padL + padR, getSuggestedMinimumWidth()), ws), rhei = resolveSize(Math.max(sumH + padT + padB, getSuggestedMinimumHeight()), hs);
        distribute(colW, cw, rwid - padL - padR - sumW); distribute(rowH, rw, rhei - padT - padB - sumH);
        for (int i = 0; i < kids.size(); i++) {            // re-measure children stretched by FILL alignment
            View c = kids.get(i); if (c.visibility == GONE) continue; LayoutParams p = (LayoutParams) c.lp; int[] ce = cell[i];
            int cwid = 0, chei = 0; for (int j = 0; j < ce[3]; j++) cwid += colW[ce[1] + j]; for (int j = 0; j < ce[2]; j++) chei += rowH[ce[0] + j];
            int nw = c.mw, nh = c.mh;
            if (p.columnSpec.align == FILL && p.width != -2) nw = cwid - p.leftMargin - p.rightMargin; else if (p.columnSpec.align == FILL && cw[ce[1]] > 0) nw = cwid - p.leftMargin - p.rightMargin;
            if (p.rowSpec.align == FILL && rw[ce[0]] > 0) nh = chei - p.topMargin - p.bottomMargin;
            if (nw != c.mw || nh != c.mh) c.measure(MeasureSpec.makeMeasureSpec(Math.max(0, nw), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, nh), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(rwid, rhei);
    }
    private static void distribute(int[] sz, float[] wt, int extra) {
        float tot = 0; for (float f : wt) tot += f; if (tot <= 0 || extra <= 0) return;
        int left = extra; float rem = tot;
        for (int i = 0; i < sz.length; i++) if (wt[i] > 0) { int e = (int) (left * wt[i] / rem); sz[i] += e; left -= e; rem -= wt[i]; }
    }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
        int[] cx = new int[colW.length + 1], ry = new int[rowH.length + 1]; cx[0] = padL; ry[0] = padT;
        for (int i = 0; i < colW.length; i++) cx[i + 1] = cx[i] + colW[i]; for (int i = 0; i < rowH.length; i++) ry[i + 1] = ry[i] + rowH[i];
        for (int i = 0; i < kids.size() && cell != null && i < cell.length; i++) {
            View c = kids.get(i); if (c.visibility == GONE) continue; LayoutParams p = (LayoutParams) c.lp; int[] ce = cell[i];
            if (ce[1] + ce[3] >= cx.length || ce[0] + ce[2] >= ry.length) continue;
            int x0 = cx[ce[1]], x1 = cx[ce[1] + ce[3]], y0 = ry[ce[0]], y1 = ry[ce[0] + ce[2]];
            int ax = p.columnSpec.align.a, x = x0 + p.leftMargin; if (ax == 5) x = x1 - p.rightMargin - c.mw; else if (ax == 1) x = x0 + (x1 - x0 - c.mw) / 2;
            int ay = p.rowSpec.align.a, y = y0 + p.topMargin; if (ay == 80) y = y1 - p.bottomMargin - c.mh; else if (ay == 1 || ay == 16) y = y0 + (y1 - y0 - c.mh) / 2;
            c.layout(x, y, x + c.mw, y + c.mh);
        }
    }
}
