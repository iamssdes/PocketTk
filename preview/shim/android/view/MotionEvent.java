package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3, ACTION_OUTSIDE = 4, ACTION_MASK = 0xff;
    public int action; public float x, y, rawX, rawY; public long downTime, eventTime;
    public MotionEvent(int a, float x, float y) { action = a; this.x = x; this.y = y; rawX = x; rawY = y; eventTime = System.currentTimeMillis(); }
    public static MotionEvent obtain(int a, float x, float y, float rx, float ry) { MotionEvent e = new MotionEvent(a, x, y); e.rawX = rx; e.rawY = ry; return e; }
    public static MotionEvent obtain(MotionEvent o) { MotionEvent e = new MotionEvent(o.action, o.x, o.y); e.rawX = o.rawX; e.rawY = o.rawY; return e; }
    public int getAction() { return action; } public int getActionMasked() { return action; }
    public float getX() { return x; } public float getY() { return y; } public float getRawX() { return rawX; } public float getRawY() { return rawY; }
    public float getX(int i) { return x; } public float getY(int i) { return y; } public int getPointerCount() { return 1; }
    public long getEventTime() { return eventTime; } public long getDownTime() { return downTime; }
    public void offsetLocation(float dx, float dy) { x += dx; y += dy; }
    public void setAction(int a) { action = a; } public void recycle() {}
}
