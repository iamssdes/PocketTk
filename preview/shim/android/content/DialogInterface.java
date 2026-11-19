package android.content;
public interface DialogInterface { interface OnClickListener { void onClick(DialogInterface d, int which); } void dismiss(); int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2; }
