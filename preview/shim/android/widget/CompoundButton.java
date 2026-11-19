package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*;
public class CompoundButton extends Button {
    public interface OnCheckedChangeListener { void onCheckedChanged(CompoundButton b, boolean checked); }
    boolean checked; OnCheckedChangeListener cl; boolean radio;
    public CompoundButton(Context c) { super(c); float d = c.getResources().getDisplayMetrics().density; gravity = Gravity.CENTER_VERTICAL | Gravity.START; minW = 0; minH = (int) (48 * d); padL = padR = (int) (4 * d); }
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { cl = l; }
    public void setChecked(boolean b) { if (b != checked) { checked = b; invalidate(); if (cl != null) cl.onCheckedChanged(this, b); } }
    public boolean isChecked() { return checked; } public void toggle() { setChecked(!checked); }
    public void setButtonDrawable(Object d) {} public void setButtonTintList(Object o) {}
    @Override public boolean performClick() { toggle(); return super.performClick(); }
    @Override protected int compoundLeft() { return (int) (32 * mContext.getResources().getDisplayMetrics().density); }
    @Override protected void drawDefaultBackground(Canvas c, int w, int h) {}
    @Override protected void onDraw(Canvas cv) {
        float d = mContext.getResources().getDisplayMetrics().density; float s = 18 * d, x = padL + 4 * d, y = (getHeight() - s) / 2; Paint p = new Paint();
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = checked ? 0xFF4C7BD9 : 0xFF777777;
        if (radio) { cv.drawCircle(x + s / 2, y + s / 2, s / 2 - d, p); if (checked) { p.style = Paint.Style.FILL; cv.drawCircle(x + s / 2, y + s / 2, s / 4, p); } }
        else { if (checked) { p.style = Paint.Style.FILL; cv.drawRoundRect(new RectF(x, y, x + s, y + s), 2 * d, 2 * d, p); p.style = Paint.Style.STROKE; p.color = 0xFFFFFFFF; Path pa = new Path(); pa.moveTo(x + s * .22f, y + s * .52f); pa.lineTo(x + s * .43f, y + s * .72f); pa.lineTo(x + s * .8f, y + s * .3f); cv.drawPath(pa, p); }
               else cv.drawRoundRect(new RectF(x, y, x + s, y + s), 2 * d, 2 * d, p); }
        super.onDraw(cv);
    }
}
