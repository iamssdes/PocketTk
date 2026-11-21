package android.graphics.drawable;
import android.graphics.*;
public class BitmapDrawable extends Drawable {
    final Bitmap bm; public BitmapDrawable(android.content.res.Resources r, Bitmap b) { bm = b; }
    public int getIntrinsicWidth() { return bm.getWidth(); } public int getIntrinsicHeight() { return bm.getHeight(); }
    public void draw(Canvas c) { c.drawBitmap(bm, null, new RectF(l, t, r, b), null); }
}
