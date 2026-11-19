package android.view;
public interface WindowManager {
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int TYPE_APPLICATION_OVERLAY = 2038, TYPE_PHONE = 2002, FLAG_NOT_FOCUSABLE = 8, FLAG_NOT_TOUCH_MODAL = 0x20, FLAG_LAYOUT_NO_LIMITS = 0x200,
            FLAG_WATCH_OUTSIDE_TOUCH = 0x40000, FLAG_KEEP_SCREEN_ON = 128, FLAG_LAYOUT_IN_SCREEN = 0x100, FLAG_FULLSCREEN = 0x400, FLAG_NOT_TOUCHABLE = 0x10,
            SOFT_INPUT_ADJUST_RESIZE = 16, SOFT_INPUT_ADJUST_PAN = 32, SOFT_INPUT_STATE_UNCHANGED = 1, SOFT_INPUT_STATE_HIDDEN = 2, SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
        public int x, y, gravity, softInputMode, type, flags, format; public float alpha = 1f;
        public LayoutParams() { super(-2, -2); }
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, int t, int f, int fmt) { super(w, h); type = t; flags = f; format = fmt; }
    }
    void addView(View v, ViewGroup.LayoutParams p); void removeView(View v); void updateViewLayout(View v, ViewGroup.LayoutParams p);
}
