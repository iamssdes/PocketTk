package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config { ARGB_8888, RGB_565, ALPHA_8 }
    public BufferedImage img;
    public Bitmap(BufferedImage i) { img = i; }
    public static Bitmap createBitmap(int w, int h, Config c) { return new Bitmap(new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB)); }
    public static Bitmap createScaledBitmap(Bitmap b, int w, int h, boolean filter) {
        BufferedImage o = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D g = o.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, filter ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(b.img, 0, 0, o.getWidth(), o.getHeight(), null); g.dispose(); return new Bitmap(o);
    }
    public Bitmap copy(Config c, boolean mutable) { BufferedImage o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D g = o.createGraphics(); g.drawImage(img, 0, 0, null); g.dispose(); return new Bitmap(o); }
    public int getWidth() { return img.getWidth(); } public int getHeight() { return img.getHeight(); }
    public int getPixel(int x, int y) { return img.getRGB(x, y); } public void setPixel(int x, int y, int c) { img.setRGB(x, y, c); }
    public void eraseColor(int c) { for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, c); }
    public boolean isMutable() { return true; } public void recycle() {}
}
