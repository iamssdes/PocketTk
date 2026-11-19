package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*;
public class SeekBar extends View {
    public interface OnSeekBarChangeListener { void onProgressChanged(SeekBar b, int p, boolean user); void onStartTrackingTouch(SeekBar b); void onStopTrackingTouch(SeekBar b); }
    int max = 100, progress; OnSeekBarChangeListener l;
    public SeekBar(Context c) { super(c); minH = (int) (48 * c.getResources().getDisplayMetrics().density); }
    public void setMax(int m) { max = Math.max(1, m); invalidate(); } public int getMax() { return max; } public int getProgress() { return progress; }
    public void setProgress(int p) { setP(p, false); } public void setOnSeekBarChangeListener(OnSeekBarChangeListener x) { l = x; }
    void setP(int p, boolean user) { p = Math.max(0, Math.min(max, p)); if (p == progress && user) return; progress = p; invalidate(); if (l != null) l.onProgressChanged(this, p, user); }
    @Override protected void onMeasure(int ws, int hs) { setMeasuredDimension(resolveSize(minW > 0 ? minW : (int) (200 * mContext.getResources().getDisplayMetrics().density), ws), resolveSize(minH, hs)); }
    @Override protected void onDraw(Canvas c) {
        float d = mContext.getResources().getDisplayMetrics().density, pad = 16 * d, w = getWidth() - 2 * pad, cy = getHeight() / 2f; Paint p = new Paint();
        p.color = 0xFF9E9E9E; c.drawRoundRect(new RectF(pad, cy - d, pad + w, cy + d), d, d, p);
        float x = pad + w * progress / max; p.color = 0xFF4C7BD9; c.drawRoundRect(new RectF(pad, cy - d, x, cy + d), d, d, p); c.drawCircle(x, cy, 8 * d, p);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false; float d = mContext.getResources().getDisplayMetrics().density, pad = 16 * d, w = getWidth() - 2 * pad;
        if (e.action == MotionEvent.ACTION_DOWN && l != null) l.onStartTrackingTouch(this);
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) setP(Math.round((e.x - pad) / w * max), true);
        if ((e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) && l != null) l.onStopTrackingTouch(this);
        return true;
    }
}
