package android.view;
public class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, KEYCODE_BACK = 4, KEYCODE_ENTER = 66, KEYCODE_DEL = 67, KEYCODE_FORWARD_DEL = 112,
        KEYCODE_DPAD_LEFT = 21, KEYCODE_DPAD_RIGHT = 22, KEYCODE_DPAD_UP = 19, KEYCODE_DPAD_DOWN = 20, KEYCODE_MOVE_HOME = 122, KEYCODE_MOVE_END = 123, KEYCODE_TAB = 61, KEYCODE_UNKNOWN = 0;
    public int action, code; public char ch;
    public KeyEvent(int action, int code) { this.action = action; this.code = code; }
    public int getKeyCode() { return code; } public int getAction() { return action; } public int getRepeatCount() { return 0; }
    public char getCharacter() { return ch; }
}
