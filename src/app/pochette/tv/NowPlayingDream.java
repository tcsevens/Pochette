package app.pochette.tv;

import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.ui.NowPlayingView;

import android.service.dreams.DreamService;

/** Screensaver: shows the music when something plays, a drifting clock otherwise. */
public class NowPlayingDream extends DreamService implements Poller.Listener {
    static volatile boolean active;

    private NowPlayingView view;
    private Poller poller;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        Prefs prefs = new Prefs(this);
        T.set(prefs.lang());
        view = new NowPlayingView(this);
        view.setMode(prefs.mode());
        view.setLyricsMode(prefs.lyricsMode());
        view.setVinylStyle(prefs.vinylStyle());
        view.setNoLyricsStyle(prefs.noLyricsStyle());
        view.setClockEnabled(prefs.showClock());
        if (System.currentTimeMillis() - prefs.themeAt() < 12 * 3600 * 1000L) view.setTheme(prefs.theme(), prefs.themeName());
        setContentView(view);
        poller = new Poller(prefs, 1000, this);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        active = true;
        if (poller != null) poller.start();
    }

    @Override public void onDreamingStopped() {
        active = false;
        if (poller != null) poller.stop();
        super.onDreamingStopped();
    }

    @Override public void onSnapshot(Poller.Snapshot s) {
        if (view != null) view.setSnapshot(s);
    }
}
