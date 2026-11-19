package android.util;
public class DisplayMetrics { public float density, scaledDensity; public int widthPixels, heightPixels, densityDpi;
    public DisplayMetrics() { density = android.os.PreviewEnv.density; scaledDensity = android.os.PreviewEnv.scaledDensity; widthPixels = android.os.PreviewEnv.widthPx; heightPixels = android.os.PreviewEnv.heightPx; densityDpi = (int) (density * 160); } }
