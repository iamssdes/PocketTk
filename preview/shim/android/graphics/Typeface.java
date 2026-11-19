package android.graphics;
public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2, BOLD_ITALIC = 3;
    public static final Typeface DEFAULT = new Typeface("SansSerif", 0), DEFAULT_BOLD = new Typeface("SansSerif", 1),
        MONOSPACE = new Typeface("Monospaced", 0), SERIF = new Typeface("Serif", 0), SANS_SERIF = new Typeface("SansSerif", 0);
    public final String family; public final int style;
    Typeface(String f, int s) { family = f; style = s; }
    public static Typeface create(Typeface base, int style) { return new Typeface(base == null ? "SansSerif" : base.family, style); }
    public static Typeface create(String fam, int style) {
        String f = "SansSerif"; if (fam != null) { String l = fam.toLowerCase(); if (l.contains("mono")) f = "Monospaced"; else if (l.contains("serif") && !l.contains("sans")) f = "Serif"; }
        return new Typeface(f, style);
    }
    public int getStyle() { return style; }
    public java.awt.Font font(float px) { return new java.awt.Font(family, ((style & 1) != 0 ? java.awt.Font.BOLD : 0) | ((style & 2) != 0 ? java.awt.Font.ITALIC : 0), 1).deriveFont(px); }
}
