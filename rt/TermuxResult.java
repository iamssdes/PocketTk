package apple.rt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the result of a command that `termux.run` sent to Termux (declared in the manifest by `#use: termux-connect`). */
public class TermuxResult extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        try { Termux.deliver(in); } catch (Throwable ignored) { /* a result nobody can use */ }
    }
}
