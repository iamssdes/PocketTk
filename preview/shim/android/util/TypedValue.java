package android.util;
public class TypedValue { public static final int COMPLEX_UNIT_PX = 0, COMPLEX_UNIT_DIP = 1, COMPLEX_UNIT_SP = 2, COMPLEX_UNIT_PT = 3;
    public static float applyDimension(int u, float v, DisplayMetrics m) { return u == 0 ? v : u == 1 ? v * m.density : u == 2 ? v * m.scaledDensity : v * m.density * 160f / 72f; } }
