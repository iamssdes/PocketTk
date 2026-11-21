package android.graphics.drawable;
import android.graphics.*;
public abstract class Drawable {
    protected int l, t, r, b;
    public void setBounds(int l, int t, int r, int b) { this.l = l; this.t = t; this.r = r; this.b = b; }
    public abstract void draw(Canvas c);
    public void setAlpha(int a) {}
    public int getBounds_l() { return l; } public int getBounds_r() { return r; } public int getBounds_t() { return t; } public int getBounds_b() { return b; }
    public int getIntrinsicWidth() { return -1; } public int getIntrinsicHeight() { return -1; }
}
