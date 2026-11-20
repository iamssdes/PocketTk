package android.app;
import android.content.*;
public class PendingIntent implements android.os.Parcelable {
    public static final int FLAG_MUTABLE = 0x02000000, FLAG_UPDATE_CURRENT = 0x08000000;
    public static PendingIntent getBroadcast(Context c, int code, Intent i, int flags) { return new PendingIntent(); }
}
