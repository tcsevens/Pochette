package app.pochette.tv;

import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;
import app.pochette.tv.sonos.Poller;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Light background watcher: when a Sonos group starts playing music (not TV audio),
 * bring the now-playing screen to the front. Re-arms after 45 s without music, so
 * leaving the screen with "Back" is respected for the rest of the listening session.
 */
public class WatchService extends Service implements Poller.Listener {
    private static final String CHANNEL = "watch";
    private static final long REARM_MS = 45000;

    private Poller poller;
    private boolean armed = true;
    private long idleSince = -1;

    public static void start(Context c) {
        Intent i = new Intent(c, WatchService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Context.class.getMethod("startForegroundService", Intent.class).invoke(c, i);
            } else {
                c.startService(i);
            }
        } catch (Exception ignored) {
            try { c.startService(i); } catch (Exception ignored2) {}
        }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, WatchService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        app.pochette.tv.core.Journal.init(getFilesDir());
        T.set(new Prefs(this).lang());
        goForeground();
        poller = new Poller(new Prefs(this), 4000, this);
        poller.setFetchArt(false);
        poller.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!new Prefs(this).autoLaunch()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (poller != null) poller.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onSnapshot(Poller.Snapshot s) {
        TrackState st = s.state;
        boolean music = st != null && st.hasMusic() && st.isPlaying();
        long now = SystemClock.elapsedRealtime();
        if (music) {
            idleSince = -1;
            if (MainActivity.visible || NowPlayingDream.active) {
                armed = false;
            } else if (armed && screenOn()) {
                armed = false;
                launch();
            }
        } else {
            if (idleSince < 0) idleSince = now;
            if (now - idleSince > REARM_MS) armed = true;
        }
    }

    private boolean screenOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    private void launch() {
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this)) return; // Android would block it anyway
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    /** Foreground notification (required on Android 8+). Built by reflection because we compile against API 23. */
    @SuppressWarnings("deprecation")
    private void goForeground() {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                Class<?> chCls = Class.forName("android.app.NotificationChannel");
                Object ch = chCls.getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(CHANNEL, T.t("Surveillance de la musique"), 1 /* IMPORTANCE_MIN */);
                NotificationManager.class.getMethod("createNotificationChannel", chCls).invoke(nm, ch);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(this, CHANNEL);
            } else {
                b = new Notification.Builder(this);
                b.setPriority(Notification.PRIORITY_MIN);
            }
            b.setContentTitle("Pochette")
                    .setContentText(T.t("Prêt à afficher ta musique Sonos"))
                    .setSmallIcon(R.drawable.ic_note)
                    .setOngoing(true);
            startForeground(1, b.build());
        } catch (Exception ignored) {}
    }
}
