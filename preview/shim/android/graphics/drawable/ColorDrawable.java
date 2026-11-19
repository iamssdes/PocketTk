package android.graphics.drawable;
import android.graphics.*;
public class ColorDrawable extends Drawable {
    int color; public ColorDrawable(int c) { color = c; }
    public void draw(Canvas c) { Paint p = new Paint(); p.color = color; c.drawRect(l, t, r, b, p); }
}
