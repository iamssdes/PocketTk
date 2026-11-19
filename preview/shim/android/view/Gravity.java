package android.view;
public class Gravity {
    public static final int NO_GRAVITY = 0, LEFT = 3, RIGHT = 5, TOP = 48, BOTTOM = 80, START = 0x800003, END = 0x800005, CENTER_HORIZONTAL = 1, CENTER_VERTICAL = 16,
        CENTER = 17, FILL = 119, FILL_HORIZONTAL = 7, FILL_VERTICAL = 112, HORIZONTAL_GRAVITY_MASK = 7, VERTICAL_GRAVITY_MASK = 112;
    /** horizontal part: 0 none, 1 center, 3 left, 5 right, 7 fill (START/END as left/right) */
    public static int h(int g) { if ((g & 0x800000) != 0) return (g & 7) == 5 ? 5 : 3; return g & 7; }
    public static int v(int g) { return g & 112; }
}
