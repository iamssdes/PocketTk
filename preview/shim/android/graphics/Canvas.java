package android.graphics;
import java.awt.*; import java.awt.geom.*; import java.awt.font.*; import java.awt.image.BufferedImage; import java.util.ArrayDeque;
public class Canvas {
    public Graphics2D g; private final ArrayDeque<Object[]> stack = new ArrayDeque<>();
    static final Graphics2D MEASURE = measureG();
    static Graphics2D measureG() {
        Graphics2D m = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        m.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        m.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return m;
    }
    public Canvas(Graphics2D g) { this.g = g; }
    public static synchronized float measure(Font f, String s) { return (float) f.getStringBounds(s, MEASURE.getFontRenderContext()).getWidth(); }
    public static synchronized Paint.FontMetrics metrics(Font f) {
        LineMetrics lm = f.getLineMetrics("Ag", MEASURE.getFontRenderContext());
        Paint.FontMetrics m = new Paint.FontMetrics(); m.ascent = -lm.getAscent(); m.descent = lm.getDescent(); m.top = m.ascent; m.bottom = m.descent; m.leading = lm.getLeading(); return m;
    }
    public int save() { stack.push(new Object[]{g.getTransform(), g.getClip()}); return stack.size(); }
    public void restore() { if (stack.isEmpty()) return; Object[] s = stack.pop(); g.setTransform((AffineTransform) s[0]); g.setClip((Shape) s[1]); }
    public void translate(float x, float y) { g.translate(x, y); } public void scale(float x, float y) { g.scale(x, y); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void clipRect(float l, float t, float r, float b) { g.clipRect((int) Math.floor(l), (int) Math.floor(t), (int) Math.ceil(r - l), (int) Math.ceil(b - t)); }
    public void clipRect(int l, int t, int r, int b) { g.clipRect(l, t, r - l, b - t); }
    private void paintShape(Shape s, Paint p) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, p.aa ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new java.awt.Color(p.color, true));
        if (p.style != Paint.Style.STROKE) g.fill(s);
        if (p.style != Paint.Style.FILL) { g.setStroke(new BasicStroke(Math.max(p.strokeWidth, 0.5f))); g.draw(s); }
    }
    public void drawColor(int c) { Shape cl = g.getClip(); g.setColor(new java.awt.Color(c, true)); if (cl == null) g.fillRect(-100000, -100000, 200000, 200000); else g.fill(cl); }
    public void drawRect(float l, float t, float r, float b, Paint p) { paintShape(new Rectangle2D.Float(l, t, r - l, b - t), p); }
    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { paintShape(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p); }
    public void drawOval(RectF r, Paint p) { paintShape(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawCircle(float x, float y, float rad, Paint p) { paintShape(new Ellipse2D.Float(x - rad, y - rad, rad * 2, rad * 2), p); }
    public void drawLine(float a, float b, float c, float d, Paint p) { Paint q = new Paint(); q.color = p.color; q.style = Paint.Style.STROKE; q.strokeWidth = p.strokeWidth; q.aa = p.aa; paintShape(new Line2D.Float(a, b, c, d), q); }
    public void drawArc(RectF r, float start, float sweep, boolean center, Paint p) { paintShape(new Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), p); }
    public void drawPath(Path path, Paint p) { paintShape(path.p, p); }
    public void drawText(String t, float x, float y, Paint p) {
        Font f = p.typeface.font(p.textSize); float w = 0;
        if (p.align != Paint.Align.LEFT) w = measure(f, t);
        if (p.align == Paint.Align.CENTER) x -= w / 2; else if (p.align == Paint.Align.RIGHT) x -= w;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(f); g.setColor(new java.awt.Color(p.color, true)); g.drawString(t, x, y);
    }
    public void drawBitmap(Bitmap bm, float x, float y, Paint p) { g.drawImage(bm.img, Math.round(x), Math.round(y), null); }
    public void drawBitmap(Bitmap bm, Rect src, RectF dst, Paint p) {
        int sx = 0, sy = 0, sw = bm.getWidth(), sh = bm.getHeight(); if (src != null) { sx = src.left; sy = src.top; sw = src.right - src.left; sh = src.bottom - src.top; }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(bm.img, Math.round(dst.left), Math.round(dst.top), Math.round(dst.right), Math.round(dst.bottom), sx, sy, sx + sw, sy + sh, null);
    }
    public int getWidth() { Rectangle r = g.getClipBounds(); return r == null ? 0 : r.width; }
    public int getHeight() { Rectangle r = g.getClipBounds(); return r == null ? 0 : r.height; }
}
