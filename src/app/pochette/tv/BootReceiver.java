package app.pochette.tv;

import app.pochette.tv.core.Prefs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (new Prefs(c).autoLaunch()) WatchService.start(c);
    }
}
