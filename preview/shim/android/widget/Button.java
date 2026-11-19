package android.widget;
import android.content.Context; import android.graphics.*; import android.view.*;
public class Button extends TextView {
    public Button(Context c) {
        super(c); float d = c.getResources().getDisplayMetrics().density; gravity = Gravity.CENTER; minH = (int) (48 * d); minW = (int) (88 * d);
        padL = padR = (int) (8 * d); padT = padB = (int) (4 * d); allCaps = false;
    }
    @Override protected void drawDefaultBackground(Canvas c, int w, int h) {
        float d = mContext.getResources().getDisplayMetrics().density; Paint p = new Paint(); p.color = pressed ? 0xFFB5B6B6 : (enabled ? 0xFFD6D7D7 : 0xFFE6E6E6);
        c.drawRoundRect(new RectF(4 * d, 6 * d, w - 4 * d, h - 6 * d), 4 * d, 4 * d, p);
    }
}
