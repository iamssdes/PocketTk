package android.view;
import android.content.Context; import android.graphics.*; import android.graphics.drawable.*; import java.util.*;
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8, FOCUS_DOWN = 130, FOCUS_UP = 33, OVER_SCROLL_NEVER = 2, SCROLLBARS_INSIDE_OVERLAY = 0x1000000,
        LAYOUT_DIRECTION_LTR = 0, TEXT_ALIGNMENT_CENTER = 4, SYSTEM_UI_FLAG_LAYOUT_STABLE = 256, IMPORTANT_FOR_AUTOFILL_NO = 2, LAYER_TYPE_SOFTWARE = 1;
    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0, EXACTLY = 1 << 30, AT_MOST = 2 << 30, MODE_MASK = 3 << 30;
        public static int makeMeasureSpec(int size, int mode) { return (size & ~MODE_MASK) | mode; }
        public static int getMode(int s) { return s & MODE_MASK; } public static int getSize(int s) { return s & ~MODE_MASK; }
    }
    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }
    public interface OnFocusChangeListener { void onFocusChange(View v, boolean has); }
    public interface OnKeyListener { boolean onKey(View v, int code, KeyEvent e); }
    public interface OnLayoutChangeListener { void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob); }
    public interface OnGenericMotionListener { boolean onGenericMotion(View v, MotionEvent e); }

    protected Context mContext;
    public int left, top, right, bottom, mw, mh, padL, padT, padR, padB, minW, minH, scrollX, scrollY;
    ViewGroup parent; public ViewGroup.LayoutParams lp; Drawable bgd; int bgColor; boolean hasBgColor;
    public int visibility = VISIBLE; public boolean enabled = true, clickable, longClickable, focusable, pressed, selected; public float alpha = 1f;
    Object tag; int id = -1;
    OnClickListener click; OnLongClickListener longClick; OnTouchListener touch; OnFocusChangeListener focusL; OnKeyListener keyL;
    List<OnLayoutChangeListener> layoutL = new ArrayList<>(); ViewTreeObserver vto;
    boolean needsLayout = true; public static View focused;
    public View(Context c) { mContext = c; }
    public Context getContext() { return mContext; }
    // ---- geometry
    public int getLeft() { return left; } public int getTop() { return top; } public int getRight() { return right; } public int getBottom() { return bottom; }
    public int getWidth() { return right - left; } public int getHeight() { return bottom - top; }
    public int getMeasuredWidth() { return mw; } public int getMeasuredHeight() { return mh; }
    public float getX() { return left; } public float getY() { return top; } public float getAlpha() { return alpha; } public void setAlpha(float a) { alpha = a; invalidate(); }
    public int getPaddingLeft() { return padL; } public int getPaddingTop() { return padT; } public int getPaddingRight() { return padR; } public int getPaddingBottom() { return padB; }
    public void setPadding(int l, int t, int r, int b) { padL = l; padT = t; padR = r; padB = b; requestLayout(); }
    public int getScrollX() { return scrollX; } public int getScrollY() { return scrollY; }
    public void setMinimumHeight(int h) { minH = h; requestLayout(); } public void setMinimumWidth(int w) { minW = w; requestLayout(); }
    public int getMinimumHeight() { return minH; } public int getMinimumWidth() { return minW; }
    public void getLocationOnScreen(int[] o) { int x = 0, y = 0; View v = this; while (v != null) { x += v.left - (v.parent != null ? v.parent.scrollX : 0); y += v.top - (v.parent != null ? v.parent.scrollY : 0); v = v.parent; } o[0] = x + RootInfo.originX; o[1] = y + RootInfo.originY; }
    public void getLocationInWindow(int[] o) { getLocationOnScreen(o); o[0] -= RootInfo.originX; o[1] -= RootInfo.originY; }
    public void getHitRect(Rect r) { r.set(left, top, right, bottom); }
    public static class RootInfo { public static int originX, originY; }
    // ---- layout params and tree
    public ViewGroup.LayoutParams getLayoutParams() { return lp; }
    public void setLayoutParams(ViewGroup.LayoutParams p) { lp = p; requestLayout(); }
    public ViewParent getParent() { return parent; }
    public View getRootView() { View v = this; while (v.parent != null) v = v.parent; return v; }
    public boolean isAttachedToWindow() { return getRootView() instanceof ViewGroup && ((ViewGroup) getRootView()).isRootOfWindow; }
    public android.os.IBinder getWindowToken() { return null; }
    public ViewTreeObserver getViewTreeObserver() { if (vto == null) vto = new ViewTreeObserver(); return vto; }
    public View findViewById(int i) { return id == i ? this : null; } public int getId() { return id; } public void setId(int i) { id = i; }
    public Object getTag() { return tag; } public void setTag(Object t) { tag = t; }
    public void addOnLayoutChangeListener(OnLayoutChangeListener l) { layoutL.add(l); }
    public void removeOnLayoutChangeListener(OnLayoutChangeListener l) { layoutL.remove(l); }
    // ---- look
    public void setBackgroundColor(int c) { bgColor = c; hasBgColor = true; bgd = null; invalidate(); }
    public void setBackground(Drawable d) { bgd = d; hasBgColor = false; invalidate(); }
    public void setBackgroundDrawable(Drawable d) { setBackground(d); }
    public Drawable getBackground() { return bgd; }
    public void setVisibility(int v) { if (visibility != v) { visibility = v; requestLayout(); } }
    public int getVisibility() { return visibility; }
    public void setEnabled(boolean b) { enabled = b; invalidate(); } public boolean isEnabled() { return enabled; }
    public void setSelected(boolean b) { selected = b; invalidate(); } public boolean isSelected() { return selected; }
    public boolean isPressed() { return pressed; }
    public void setFocusable(boolean b) { focusable = b; } public void setFocusableInTouchMode(boolean b) { focusable = b; } public boolean isFocusable() { return focusable; }
    public void setClickable(boolean b) { clickable = b; } public boolean isClickable() { return clickable || click != null; }
    public void setLongClickable(boolean b) { longClickable = b; }
    public void setElevation(float e) {} public void setClipToPadding(boolean b) {} public void setOverScrollMode(int m) {} public void setLayerType(int t, Paint p) {}
    public void setImportantForAutofill(int i) {} public void setScrollBarStyle(int s) {} public void setVerticalScrollBarEnabled(boolean b) {} public void setHorizontalScrollBarEnabled(boolean b) {}
    public void setSystemUiVisibility(int f) {} public void setContentDescription(CharSequence c) {} public void setLayoutDirection(int d) {} public void setTextAlignment(int a) {}
    // ---- listeners
    public void setOnClickListener(OnClickListener l) { click = l; if (l != null) clickable = true; }
    public void setOnLongClickListener(OnLongClickListener l) { longClick = l; }
    public void setOnTouchListener(OnTouchListener l) { touch = l; }
    public void setOnFocusChangeListener(OnFocusChangeListener l) { focusL = l; }
    public void setOnKeyListener(OnKeyListener l) { keyL = l; }
    public boolean performClick() { if (click != null) { click.onClick(this); return true; } return false; }
    public boolean performLongClick() { if (longClick != null) return longClick.onLongClick(this); return false; }
    // ---- focus
    public boolean requestFocus() { return requestFocus(FOCUS_DOWN); }
    public boolean requestFocus(int d) {
        if (focused == this) return true;
        View old = focused; focused = this;
        if (old != null) { old.onFocusChanged(false); }
        onFocusChanged(true); invalidate(); return true;
    }
    public void clearFocus() { if (focused == this) { focused = null; onFocusChanged(false); invalidate(); } }
    public boolean hasFocus() { return focused == this; } public boolean isFocused() { return focused == this; }
    public View findFocus() { return focused; }
    protected void onFocusChanged(boolean has) { if (focusL != null) focusL.onFocusChange(this, has); invalidate(); }
    public boolean onKeyDown(int code, KeyEvent e) { return false; }
    public void onWindowFocusChanged(boolean has) {}
    protected void onAttachedToWindow() {}
    // ---- posting
    public boolean post(Runnable r) { new android.os.Handler(android.os.Looper.getMainLooper()).post(r); return true; }
    public boolean postDelayed(Runnable r, long d) { new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, d); return true; }
    public void removeCallbacks(Runnable r) { }
    // ---- measure / layout / draw
    public void requestLayout() { needsLayout = true; if (parent != null) parent.requestLayout(); else Hook.layoutRequested(); }
    public void invalidate() { Hook.repaint(); }
    public void postInvalidate() { Hook.repaint(); }
    public static class Hook { public static Runnable onLayout = () -> {}, onPaint = () -> {}; static void layoutRequested() { onLayout.run(); } static void repaint() { onPaint.run(); } }
    public final void measure(int wSpec, int hSpec) { if (visibility == GONE) { mw = mh = 0; return; } onMeasure(wSpec, hSpec); }
    protected void onMeasure(int wSpec, int hSpec) { setMeasuredDimension(getDefaultSize(Math.max(getSuggestedMinimumWidth(), 0), wSpec), getDefaultSize(Math.max(getSuggestedMinimumHeight(), 0), hSpec)); }
    protected int getSuggestedMinimumWidth() { return minW; } protected int getSuggestedMinimumHeight() { return minH; }
    public static int getDefaultSize(int size, int spec) { int m = MeasureSpec.getMode(spec), s = MeasureSpec.getSize(spec); return m == MeasureSpec.UNSPECIFIED ? size : (m == MeasureSpec.AT_MOST ? Math.min(size, s) : s); }
    public static int resolveSize(int size, int spec) { int m = MeasureSpec.getMode(spec), s = MeasureSpec.getSize(spec); return m == MeasureSpec.AT_MOST ? Math.min(size, s) : (m == MeasureSpec.EXACTLY ? s : size); }
    public static int resolveSizeAndState(int size, int spec, int st) { return resolveSize(size, spec); }
    public final void setMeasuredDimension(int w, int h) { mw = w; mh = h; }
    public void layout(int l, int t, int r, int b) {
        int ol = left, ot = top, or = right, ob = bottom;
        boolean changed = l != left || t != top || r != right || b != bottom;
        left = l; top = t; right = r; bottom = b; needsLayout = false;
        onLayout(changed, l, t, r, b);
        if (changed && !layoutL.isEmpty()) for (OnLayoutChangeListener x : new ArrayList<>(layoutL)) x.onLayoutChange(this, l, t, r, b, ol, ot, or, ob);
    }
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}
    public void draw(Canvas c) {
        if (visibility != VISIBLE) return;
        if (alpha < 1f) { java.awt.Graphics2D g = c.g; java.awt.Composite old = g.getComposite(); g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)); drawSelf(c); g.setComposite(old); } else drawSelf(c);
    }
    private void drawSelf(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (hasBgColor) { Paint p = new Paint(); p.color = bgColor; c.drawRect(0, 0, w, h, p); }
        else if (bgd != null) { bgd.setBounds(0, 0, w, h); bgd.draw(c); }
        else drawDefaultBackground(c, w, h);
        onDraw(c); dispatchDraw(c); onDrawForeground(c);
    }
    protected void drawDefaultBackground(Canvas c, int w, int h) {}
    protected void onDraw(Canvas c) {} protected void dispatchDraw(Canvas c) {} protected void onDrawForeground(Canvas c) {}
    // ---- touch
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (touch != null && touch.onTouch(this, e)) return true;
        return onTouchEvent(e);
    }
    public boolean onTouchEvent(MotionEvent e) {
        boolean can = isClickable() || longClick != null || longClickable;
        if (!can || !enabled) return can;
        switch (e.action) {
            case MotionEvent.ACTION_DOWN: pressed = true; invalidate(); armLong(); break;
            case MotionEvent.ACTION_UP: { boolean was = pressed && !longFired; pressed = false; invalidate(); if (was && e.x >= -8 && e.y >= -8 && e.x <= getWidth() + 8 && e.y <= getHeight() + 8) performClick(); longFired = false; break; }
            case MotionEvent.ACTION_CANCEL: pressed = false; longFired = false; invalidate(); break;
        }
        return true;
    }
    boolean longFired; long downToken;
    void armLong() {
        if (longClick == null) return; final long tok = ++downToken; longFired = false;
        postDelayed(() -> { if (pressed && tok == downToken && !longFired) { longFired = performLongClick(); } }, 500);
    }
    public boolean dispatchKeyEvent(KeyEvent e) { return false; }
    /** scrollable ancestors take mouse-wheel input; returns true when used */
    public boolean onWheel(float x, float y, float dy) { return false; }
    public void setPressed(boolean p) { pressed = p; invalidate(); }
    public boolean isShown() { View v = this; while (v != null) { if (v.visibility != VISIBLE) return false; v = v.parent; } return true; }
    public void bringToFront() { }
    public void scrollTo(int x, int y) { scrollX = x; scrollY = y; invalidate(); } public void scrollBy(int x, int y) { scrollTo(scrollX + x, scrollY + y); }
}
