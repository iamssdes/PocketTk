package android.graphics;
public class Color {
    public static final int BLACK=0xFF000000, WHITE=0xFFFFFFFF, RED=0xFFFF0000, GREEN=0xFF00FF00, BLUE=0xFF0000FF,
        YELLOW=0xFFFFFF00, CYAN=0xFF00FFFF, MAGENTA=0xFFFF00FF, GRAY=0xFF888888, DKGRAY=0xFF444444, LTGRAY=0xFFCCCCCC, TRANSPARENT=0;
    public static int parseColor(String s) {
        if (s.startsWith("#")) {
            String h = s.substring(1);
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            if (h.length() == 6) h = "FF" + h;
            if (h.length() != 8) throw new IllegalArgumentException("Unknown color");
            return (int) Long.parseLong(h, 16);
        }
        switch (s.toLowerCase()) {
            case "red": return RED; case "blue": return BLUE; case "green": return 0xFF008000; case "black": return BLACK;
            case "white": return WHITE; case "yellow": return YELLOW; case "cyan": return CYAN; case "magenta": return MAGENTA;
            case "gray": case "grey": return GRAY; case "lightgray": case "lightgrey": return LTGRAY; case "darkgray": case "darkgrey": return DKGRAY;
            case "orange": return 0xFFFFA500; case "purple": return 0xFF800080; case "pink": return 0xFFFFC0CB; case "brown": return 0xFFA52A2A;
            case "transparent": return 0;
        }
        throw new IllegalArgumentException("Unknown color");
    }
    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return (c >> 16) & 255; }
    public static int green(int c) { return (c >> 8) & 255; }
    public static int blue(int c) { return c & 255; }
    public static int rgb(int r, int g, int b) { return 0xFF000000 | (r << 16) | (g << 8) | b; }
    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int HSVToColor(float[] hsv) { return java.awt.Color.HSBtoRGB(hsv[0] / 360f, hsv[1], hsv[2]) | 0xFF000000; }
    public static void colorToHSV(int c, float[] hsv) {
        float[] o = java.awt.Color.RGBtoHSB(red(c), green(c), blue(c), null); hsv[0] = o[0] * 360f; hsv[1] = o[1]; hsv[2] = o[2];
    }
}
