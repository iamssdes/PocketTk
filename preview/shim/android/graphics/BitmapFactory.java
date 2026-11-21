package android.graphics;
import java.io.*; import javax.imageio.ImageIO; import java.awt.image.BufferedImage;
public class BitmapFactory {
    static Bitmap wrap(BufferedImage i) { if (i == null) return null; BufferedImage o = new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D g = o.createGraphics(); g.drawImage(i, 0, 0, null); g.dispose(); return new Bitmap(o); }
    public static Bitmap decodeStream(InputStream in) { try { return wrap(ImageIO.read(in)); } catch (IOException e) { return null; } }
    public static Bitmap decodeFile(String p) { try { return wrap(ImageIO.read(new File(p))); } catch (IOException e) { return null; } }
    public static Bitmap decodeByteArray(byte[] b, int off, int len) { try { return wrap(ImageIO.read(new ByteArrayInputStream(b, off, len))); } catch (IOException e) { return null; } }
}
