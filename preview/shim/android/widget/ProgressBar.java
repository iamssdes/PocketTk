package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*;
public class ProgressBar extends View {
    int max = 100, progress; boolean indet;
    public ProgressBar(Context c) { super(c); indet = true; } public ProgressBar(Context c, Object attrs, int style) { super(c); indet = style == 0; }
    public void setMax(int m) { max = Math.max(1, m); invalidate(); } public void setProgress(int p) { progress = p; invalidate(); } public int getProgress() { return progress; } public int getMax() { return max; }
    public void setIndeterminate(boolean b) { indet = b; invalidate(); } public void setProgressTintList(Object o) {} public void setIndeterminateTintList(Object o) {}
    @Override protected void onMeasure(int ws, int hs) { float d = mContext.getResources().getDisplayMetrics().density; setMeasuredDimension(resolveSize((int) (indet ? 48 * d : 200 * d), ws), resolveSize((int) (indet ? 48 * d : 8 * d), hs)); }
    @Override protected void onDraw(Canvas c) {
        float d = mContext.getResources().getDisplayMetrics().density; Paint p = new Paint();
        if (indet) { p.style = Paint.Style.STROKE; p.strokeWidth = 4 * d; p.color = 0xFF4C7BD9; float r = Math.min(getWidth(), getHeight()) / 2f - 4 * d; float a = (System.currentTimeMillis() / 4) % 360;
            c.drawArc(new RectF(getWidth() / 2f - r, getHeight() / 2f - r, getWidth() / 2f + r, getHeight() / 2f + r), a, 270, false, p); postInvalidate(); }
        else { p.color = 0xFFBDBDBD; c.drawRect(0, 0, getWidth(), getHeight(), p); p.color = 0xFF4C7BD9; c.drawRect(0, 0, getWidth() * progress / (float) max, getHeight(), p); }
    }
}
