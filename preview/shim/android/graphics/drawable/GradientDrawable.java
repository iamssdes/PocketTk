package android.graphics.drawable;
import android.graphics.*;
public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0, OVAL = 1;
    int shape, fill = 0, strokeW, strokeC; float radius; boolean hasFill;
    public GradientDrawable() {}
    public void setShape(int s) { shape = s; }
    public void setColor(int c) { fill = c; hasFill = true; }
    public void setCornerRadius(float r) { radius = r; }
    public void setStroke(int w, int c) { strokeW = w; strokeC = c; }
    public void draw(Canvas c) {
        RectF rc = new RectF(l, t, r, b); Paint p = new Paint();
        if (hasFill) { p.color = fill; if (shape == OVAL) c.drawOval(rc, p); else if (radius > 0) c.drawRoundRect(rc, radius, radius, p); else c.drawRect(rc, p); }
        if (strokeW > 0) {
            p.color = strokeC; p.style = Paint.Style.STROKE; p.strokeWidth = strokeW;
            RectF in = new RectF(l + strokeW / 2f, t + strokeW / 2f, r - strokeW / 2f, b - strokeW / 2f);
            if (shape == OVAL) c.drawOval(in, p); else if (radius > 0) c.drawRoundRect(in, radius, radius, p); else c.drawRect(in, p);
        }
    }
}
