package android.widget;
public class RadioButton extends CompoundButton { public RadioButton(android.content.Context c) { super(c); radio = true; } @Override public void toggle() { if (!checked) setChecked(true); } }
