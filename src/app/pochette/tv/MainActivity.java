package app.pochette.tv;

import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;
import app.pochette.tv.party.GameView;
import app.pochette.tv.party.PartyView;
import app.pochette.tv.menu.AmbianceMenu;
import app.pochette.tv.menu.FavMenu;
import app.pochette.tv.menu.GamesMenu;
import app.pochette.tv.menu.MenuEnv;
import app.pochette.tv.menu.RoomsMenu;
import app.pochette.tv.menu.SettingsMenu;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.spotify.SpotifySetupView;
import app.pochette.tv.spotify.SpotifyClient;
import app.pochette.tv.theme.ThemePreview;
import app.pochette.tv.theme.Themes;
import app.pochette.tv.ui.GuideView;
import app.pochette.tv.ui.InfoView;
import app.pochette.tv.ui.MenuOverlay;
import app.pochette.tv.ui.NowPlayingView;
import app.pochette.tv.ui.ShareView;
import app.pochette.tv.ui.VideoBg;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Poller.Listener, MenuOverlay.Host, MenuEnv {
    static volatile boolean visible;

    private NowPlayingView view;
    private GameView gameView;
    private SpotifySetupView spotifyView;
    private InfoView infoView;
    private MenuOverlay menu;
    private Poller poller;
    private Prefs prefs;
    private Poller.Snapshot last;
    private long seekTarget = -1;
    private int heldDir;
    private boolean heldSeeking, soundsWarned;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        T.set(prefs.lang());
        view = new NowPlayingView(this);
        view.setMode(prefs.mode());
        view.setLyricsMode(prefs.lyricsMode());
        view.setKaraokeColor(prefs.karaokeColor());
        view.setVinylStyle(prefs.vinylStyle());
        view.setNoLyricsStyle(prefs.noLyricsStyle());
        view.setClockEnabled(prefs.showClock());
        view.setBurnIn(prefs.burnIn());
        menu = new MenuOverlay(this, this);
        menu.setTransparency(prefs.menuTransparency());
        menu.setAutoHide(prefs.stripSec());
        menu.setStripTransparency(prefs.stripTransparency());
        view.setLyricsOffset(prefs.lyricsOffset());
        app.pochette.tv.core.Journal.init(getFilesDir());
        try { app.pochette.tv.core.Journal.setVersion(getPackageManager().getPackageInfo(getPackageName(), 0).versionName); } catch (Exception ignored) {}
        app.pochette.tv.core.Journal.i("App", "démarrage v" + ver() + " Android " + android.os.Build.VERSION.RELEASE + " " + android.os.Build.MODEL);
        Themes.probe(this);
        ThemePreview.init(this, new Runnable() { @Override public void run() { runOnUiThread(new Runnable() { @Override public void run() { view.invalidate(); menu.invalidate(); } }); } });
        view.setWeatherCard(prefs.weatherCard(), app.pochette.tv.core.Weather.fahrenheit(prefs));
        if (prefs.theme() == app.pochette.tv.core.Ambiance.T_WEATHER) {      // the weather mode stays until it is switched off
            view.setTheme(prefs.theme(), "");
        } else if (prefs.theme() != 0 && System.currentTimeMillis() - prefs.themeAt() < 12 * 3600 * 1000L) {
            view.setTheme(prefs.theme(), prefs.themeName());
        } else if (prefs.theme() != 0) {
            prefs.theme(0, "");     // an ambiance screen does not stay forever
        }
        FrameLayout root = new FrameLayout(this);
        root.setSoundEffectsEnabled(false);
        videoBg = new VideoBg(this);
        root.addView(videoBg.view(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.setVideoHost(new NowPlayingView.VideoHost() {
            @Override public void video(String res) { videoBg.set(res); }
        });
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(menu, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gameView = new GameView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        spotifyView = new SpotifySetupView(this, new SpotifySetupView.Host() {
            @Override public void closed(boolean connected) {
                if (connected) view.showToast(T.t("Spotify est connecté."), 3000);
                menu.refresh();
            }
        });
        root.addView(spotifyView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        shareView = new ShareView(this);
        root.addView(shareView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        partyView = new PartyView(this);
        partyView.setHost(new PartyView.Host() { @Override public void partyClosed() { backEaten = true; } });
        root.addView(partyView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        infoView = new InfoView(this);
        root.addView(infoView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        guideView = new GuideView(this);
        guideView.setHost(new GuideView.Host() {
            @Override public void guideClosed() { backEaten = true; guideClosedAt = android.os.SystemClock.uptimeMillis(); menu.invalidate(); view.invalidate(); }
        });
        root.addView(guideView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        amb = new AmbianceMenu(this);
        fav = new FavMenu(this);
        games = new GamesMenu(this, gameView, partyView, shareView);
        roomsMenu = new RoomsMenu(this);
        settings = new SettingsMenu(this, spotifyView, shareView, guideView);
        view.setShortcuts(amb.list());
        setContentView(root);
        hideSystemUi();
        if (prefs.autoLaunch()) WatchService.start(this);
        if (!prefs.guideSeen()) {
            prefs.guideSeen(true);
            guideView.open();
        } else if (!prefs.hintShown()) {
            view.showToast(T.t("Appuie sur OK ou sur une flèche pour afficher le menu"), 8000);
            prefs.hintShown(true);
        }
    }

    private void hideSystemUi() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onStart() {
        super.onStart();
        resumeChecked = false;
        visible = true;
        poller = new Poller(prefs, 1000, this);
        poller.start();
        games.keepQueue();
        weatherRefresh(false);
    }

    // ---- weather mode: refreshed at the chosen period while the screen is on
    private final android.os.Handler wxHandler = new android.os.Handler();
    private final Runnable wxTick = new Runnable() {
        @Override public void run() {
            if (visible && wxWanted()) {
                app.pochette.tv.core.Weather.refresh(prefs, new Runnable() { @Override public void run() { runOnUiThread(new Runnable() { @Override public void run() { view.weatherChanged(); } }); } });
            }
            wxHandler.postDelayed(this, app.pochette.tv.core.Weather.REFRESH_MIN[prefs.weatherRefresh()] * 60000L);
        }
    };

    // ---- kitchen timer: rings once when the time is up, music comes back on its own
    private final Runnable ktTick = new Runnable() {
        @Override public void run() {
            if (app.pochette.tv.core.KitchenTimer.expired()) {
                if (poller != null) poller.chime(new Runnable() { @Override public void run() { app.pochette.tv.core.Chime.play(); } });
                else app.pochette.tv.core.Chime.play();
                view.invalidate();
            }
            if (app.pochette.tv.core.KitchenTimer.running()) wxHandler.postDelayed(this, 500);
            else view.invalidate();
        }
    };
    @Override public int timerMin() { return prefs.timerMin(); }
    @Override public void timerMin(int minutes) { prefs.timerMin(minutes); }
    @Override public void timerStart(long ms) {
        app.pochette.tv.core.KitchenTimer.start(ms);
        wxHandler.removeCallbacks(ktTick);
        wxHandler.postDelayed(ktTick, 500);
        view.invalidate();
    }
    @Override public void timerCancel() {
        app.pochette.tv.core.KitchenTimer.cancel();
        wxHandler.removeCallbacks(ktTick);
        view.invalidate();
    }

    // ---- demo of the weather backgrounds
    private int wxDemoStep = -1, wxDemoPrevTheme;
    private final Runnable wxDemoTick = new Runnable() {
        @Override public void run() {
            if (wxDemoStep < 0) return;
            if (wxDemoStep >= app.pochette.tv.core.Weather.DEMO_STEPS) { weatherDemo(false); return; }
            app.pochette.tv.core.Weather.demoStep(wxDemoStep++);
            app.pochette.tv.core.Weather.Data d = app.pochette.tv.core.Weather.data();
            view.weatherChanged();
            view.showToast(T.t("Démo") + " : " + T.t(app.pochette.tv.core.Weather.COND_NAMES[d.cond]) + (d.cond == app.pochette.tv.core.Weather.SNOW || d.cond == app.pochette.tv.core.Weather.STORM ? "" : " · " + T.t(d.day ? "jour" : "nuit")), 2500);
            wxHandler.postDelayed(this, 6000);
        }
    };

    @Override public void weatherDemo(boolean on) {
        wxHandler.removeCallbacks(wxDemoTick);
        if (on) {
            wxDemoPrevTheme = view.getTheme();
            wxDemoStep = 0;
            view.setTheme(app.pochette.tv.core.Ambiance.T_WEATHER, "");
            menu.hide();
            wxHandler.post(wxDemoTick);
        } else {
            wxDemoStep = -1;
            app.pochette.tv.core.Weather.demoStep(-1);
            view.setTheme(wxDemoPrevTheme, prefs.themeName());
            view.weatherChanged();
            menu.refresh();
        }
    }

    private boolean wxWanted() { return view.getTheme() == app.pochette.tv.core.Ambiance.T_WEATHER || prefs.weatherCard(); }

    /** Refreshes now and restarts the timer; menuAfter redraws the menu when the answer is in. */
    @Override public void weatherRefresh(final boolean menuAfter) {
        wxHandler.removeCallbacks(wxTick);
        view.setWeatherCard(prefs.weatherCard(), app.pochette.tv.core.Weather.fahrenheit(prefs));
        if (visible && wxWanted()) {
            app.pochette.tv.core.Weather.refresh(prefs, new Runnable() {
                @Override public void run() { runOnUiThread(new Runnable() { @Override public void run() { view.weatherChanged(); if (menuAfter) menu.refresh(); } }); }
            });
        } else view.weatherChanged();
        wxHandler.postDelayed(wxTick, app.pochette.tv.core.Weather.REFRESH_MIN[prefs.weatherRefresh()] * 60000L);
    }

    @Override protected void onStop() {
        super.onStop();
        visible = false;
        wxHandler.removeCallbacks(wxTick);
        games.shutdown();
        if (spotifyView != null && spotifyView.active()) spotifyView.close(false);
        if (infoView != null && infoView.active()) infoView.close();
        if (partyView != null && partyView.active()) partyView.close();
        settings.shutdown();
        if (poller != null) poller.stop();
        poller = null;
        menu.hide();
    }

    @Override protected void onPause() {
        super.onPause();
        videoBg.pause();
    }

    @Override protected void onResume() {
        super.onResume();
        videoBg.resume();
        // Shown at the first three launches only (a launch counts when the sounds were on); the menu row explains it any time.
        int warned = prefs.gameInt("soundwarn", 0);
        if (!soundsWarned && warned < 3 && settings.systemSoundsOn()) {
            soundsWarned = true;
            prefs.setGameInt("soundwarn", warned + 1);
            view.showNotice(T.t("Sons système de la télé activés") + "  (" + T.t("avertissement ") + (warned + 1) + T.t(" sur ") + "3)",
                    settings.soundsHelp(), 14000);
        }
        menu.refresh();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private boolean resumeChecked;

    @Override public void onSnapshot(Poller.Snapshot s) {
        last = s;
        if (!resumeChecked && s.state != null) {
            resumeChecked = true;
            boolean playing = s.state.hasMusic() && s.state.isPlaying();
            if (prefs.resumeOnOpen() && !playing) resume();
        }
        String tk = s.state != null && s.state.hasMusic() ? s.state.trackKey() : "";
        lastTrackKey = tk;   // the controls strip no longer pops up by itself when the track changes
        games.onSnapshot(s);
        view.setSnapshot(s);
        menu.invalidate();
        if (view.isShowingMusic()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ================================================================ remote control

    private String lastTrackKey = "";

    private String ver() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    @Override public boolean stripOn() { return view.isShowingMusic() && !view.isSleeping(); }   // dark screen: the bar steps aside too

    @Override public int spotifyState() {
        SpotifyClient sp = poller != null ? poller.spotify : null;
        if (sp == null || !sp.configured()) return 0;
        if (!sp.connected()) return 2;
        if (!prefs.spotifyOn()) return 0;
        return sp.needsReconnect() ? 2 : 1;
    }

    @Override public int volume() { return poller == null ? -1 : poller.volume(); }
    @Override public boolean muted() { return poller != null && poller.muted(); }
    @Override public void volumeStep(int dir, int repeat) {
        if (poller == null) return;
        poller.volumeStep(dir * (repeat < 12 ? 2 : 4));
        menu.invalidate();
    }

    @Override public String[] roomNames() {
        if (poller == null) return new String[0];
        List<SonosClient.Room> r = poller.groupRooms();
        String[] n = new String[r.size()];
        for (int i = 0; i < n.length; i++) n[i] = r.get(i).name;
        return n;
    }
    @Override public int[] roomVolumes() { return poller == null ? new int[0] : poller.roomVolumes(); }
    @Override public void roomVolumeSet(int idx, int vol) { if (poller != null) { poller.setRoomVolume(idx, vol); menu.invalidate(); } }
    @Override public void roomVolumesLoad() {
        if (poller != null) poller.loadRoomVolumes(new Runnable() { @Override public void run() { menu.postInvalidate(); } });
    }

    @Override public String roomsLabel() {
        TrackState st = view.state();
        return st == null || st.roomsLabel == null ? "" : st.roomsLabel;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (view.isSleeping()) { view.touch(); menu.invalidate(); return true; }   // the first key only wakes the screen
        view.touch();
        if (guideView != null && guideView.active()) {
            if (e.getRepeatCount() == 0) guideView.handleKey(code);
            return true;
        }
        if (shareView != null && shareView.active()) {
            if (e.getRepeatCount() == 0) { shareView.handleKey(code); if (code == KeyEvent.KEYCODE_BACK) backEaten = true; }
            return true;
        }
        if (partyView != null && partyView.active()) {
            if (e.getRepeatCount() == 0 || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) partyView.handleKey(code);
            return true;
        }
        if (infoView != null && infoView.active()) {
            if (e.getRepeatCount() == 0 || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) infoView.handleKey(code);
            return true;
        }
        if (spotifyView != null && spotifyView.active()) {
            if (e.getRepeatCount() == 0) spotifyView.handleKey(code);
            return true;
        }
        if (games.gameActive()) {
            if (e.getRepeatCount() == 0 || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) games.gameKey(code);
            return true;
        }
        boolean stripWasUp = menu.stripUp();
        menu.wake();
        // Media keys always act directly.
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) transport(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (e.getRepeatCount() == 0) resume();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (e.getRepeatCount() == 0 && poller != null) { poller.playPause(true); view.flash("pause"); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (e.getRepeatCount() == 0) transport(2);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (e.getRepeatCount() == 0) transport(0);
                return true;
        }
        if (e.getRepeatCount() == 0 && code != KeyEvent.KEYCODE_BACK) view.hideNotice();
        else if (code == KeyEvent.KEYCODE_BACK && e.getRepeatCount() == 0 && view.hideNotice()) { backEaten = true; return true; }
        if (code == KeyEvent.KEYCODE_BACK && stripWasUp && !menu.isOpen()) {   // first Back only hides the bottom bar
            if (e.getRepeatCount() == 0) { menu.dismissStrip(); backEaten = true; }
            return true;
        }
        if (view.homeOn() && !menu.isOpen()) {
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && !view.idleActive())) {
                if (e.getRepeatCount() == 0) view.setHome(false);   // back to the music
                return true;
            }
        } else if (code == KeyEvent.KEYCODE_BACK && e.getRepeatCount() == 0 && !menu.isOpen() && view.isShowingMusic() && view.hasShortcuts()) {
            view.setHome(true);   // Back on the music = the ambiance home; a second Back leaves the app
            backEaten = true;
            return true;
        }
        if (!menu.isOpen() && view.isIdle() && view.hasShortcuts()) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int d = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                if (e.getRepeatCount() == 0) {
                    if (view.idleAtEdge(d)) { view.idleClear(); menu.show(true); }   // past the last tile, toward the menu: open it
                    else view.idleMove(d);
                }
                return true;
            }
            if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && view.idleActive()) {
                if (e.getRepeatCount() == 0) amb.launch(view.idleSelected());
                return true;
            }
        }
        if (!menu.isOpen()) {
            // Menu closed: ▲ ▼ change the display style, ◀ ▶ held = rewind / fast-forward, short = show controls.
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (e.getRepeatCount() == 0) {
                    int n = view.modeCount();
                    int[] ids = view.visibleModes();
                    view.setMode(ids[(view.modePosition() + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : n - 1)) % n]);
                    prefs.mode(view.getMode());
                    view.showToast(T.t("Affichage : ") + T.t(NowPlayingView.MODE_NAMES[view.getMode()]));
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                if (e.getRepeatCount() == 0) { heldDir = dir; heldSeeking = false; }
                else if (heldDir == dir && view.isShowingMusic()) { heldSeeking = true; seekStep(dir, e.getRepeatCount()); }
                return true;
            }
        }
        boolean wasOpen = menu.isOpen();
        if (menu.onKey(code, e)) {
            if (code == KeyEvent.KEYCODE_BACK && wasOpen) backEaten = true;   // this press only closed the menu: its key-up must not quit
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (infoView != null && infoView.active()) return true;
        if (spotifyView != null && spotifyView.active()) return true;
        if (games.gameActive()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (heldDir != 0) {
                if (heldSeeking) seekCommit(); else menu.show(false);
                heldDir = 0;
                heldSeeking = false;
                return true;
            }
            if (menu.onKeyUp(code)) return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (menu.onKeyUp(code)) return true;
        }
        if (guideView != null && guideView.active()) return true;
        if (android.os.SystemClock.uptimeMillis() - guideClosedAt < 500) return true;   // the key that closed the guide must not act again
        if (partyView != null && partyView.active()) return true;
        if (shareView != null && shareView.active()) return true;
        if (code == KeyEvent.KEYCODE_BACK && (backEaten || (!e.isCanceled() && menu.isOpen()))) { backEaten = false; return true; }
        return super.onKeyUp(code, e);
    }

    @Override public void insets(float left, float right, float bottom) {
        // The music stays where it is when the menu opens (the menu is drawn over it, semi-transparent).
        // The bottom panel has its own progress bar, so the one under the title steps aside.
        view.setHideProgress(bottom > 0);
        view.setInsets(0, 0, bottom);
    }

    // ================================================================ queue

    @Override public List<SonosClient.Item> upNext() {
        if (last == null || !view.isShowingMusic()) return new ArrayList<SonosClient.Item>();
        return last.upNext;
    }

    @Override public void queueJump(int index) {
        List<SonosClient.Item> q = upNext();
        if (poller == null || index < 0 || index >= q.size()) return;
        if (q.get(index).kind == 5) poller.next();
        else if (q.get(index).kind == 4) poller.spotifyJump(q.get(index).trackNr);
        else poller.jumpTo(q.get(index).trackNr);
    }

    // ================================================================ menu sections

    private VideoBg videoBg;
    private PartyView partyView;
    private ShareView shareView;
    private GuideView guideView;
    private long guideClosedAt;
    private AmbianceMenu amb;
    private FavMenu fav;
    private GamesMenu games;
    private RoomsMenu roomsMenu;
    private SettingsMenu settings;
    /** Set when a Back press only closed something: its key-up must not quit the app. */
    private boolean backEaten;

    @Override public boolean ambRows() { return amb.editing(); }
    @Override public boolean ambReorder() { return amb.reordering(); }
    @Override public boolean ambGrabbed() { return amb.grabbed(); }
    @Override public void ambGrab() { amb.grab(); }
    @Override public int ambMove(int from, int to) { return amb.move(from, to); }
    @Override public void ambReorderBack() { amb.reorderBack(); }
    @Override public List<MenuOverlay.Card> cards() { return amb.cards(); }
    @Override public void cardLaunch(int index) { amb.cardLaunch(index); }
    @Override public void cardEdit(int index) { amb.cardEdit(index); }
    @Override public boolean gameGrid() { return games.grid(); }
    @Override public String gameTitle() { return games.title(); }
    @Override public List<MenuOverlay.Card> gameCards() { return games.cards(); }
    @Override public void gamePick(int index) { games.pick(index); }

    @Override public void onOpen(int section) {
        if (section == MenuOverlay.SEC_FAV) fav.open();
        else if (section == MenuOverlay.SEC_SETTINGS) settings.open();
        else if (section == MenuOverlay.SEC_ROOMS) roomsMenu.open();
    }

    @Override public boolean onBack(int section) {
        switch (section) {
            case MenuOverlay.SEC_AMB: return amb.back();
            case MenuOverlay.SEC_GAMES: return games.back();
            case MenuOverlay.SEC_SETTINGS: return settings.back();
            default: return false;
        }
    }

    @Override public List<MenuOverlay.Row> rows(int section) {
        switch (section) {
            case MenuOverlay.SEC_AMB: return amb.rows();
            case MenuOverlay.SEC_FAV: return fav.rows();
            case MenuOverlay.SEC_GAMES: return games.rows();
            case MenuOverlay.SEC_ROOMS: return roomsMenu.rows();
            default: return settings.rows(section);
        }
    }

    @Override public void onChoice(int section, MenuOverlay.Row row, int index) {
        switch (section) {
            case MenuOverlay.SEC_AMB: amb.choice(row.id, index); break;
            case MenuOverlay.SEC_GAMES: games.choice(row.id, index); break;
            default: settings.choice(section, row, index);
        }
    }

    @Override public void onActivate(int section, MenuOverlay.Row row) {
        if (row.id == null) return;
        switch (section) {
            case MenuOverlay.SEC_AMB: amb.activate(row.id); break;
            case MenuOverlay.SEC_FAV: fav.activate(row.id); break;
            case MenuOverlay.SEC_GAMES: games.activate(row.id); break;
            case MenuOverlay.SEC_ROOMS: roomsMenu.activate(row.id); break;
            default: settings.activate(row);
        }
    }

    // ---- what the sections need from the app

    @Override public Activity activity() { return this; }
    @Override public Prefs prefs() { return prefs; }
    @Override public NowPlayingView view() { return view; }
    @Override public MenuOverlay menu() { return menu; }
    @Override public Poller poller() { return poller; }
    @Override public String coordinatorUuid() { return last != null ? last.coordinatorUuid : null; }

    @Override public GamesMenu games() { return games; }
    @Override public void setAutoLaunch(boolean on) {
        if (on) WatchService.start(this); else WatchService.stop(this);
    }

    // ================================================================ seeking

    @Override public long positionMs() {
        TrackState st = view.state();
        return st == null ? 0 : view.displayPositionMs(st);
    }

    @Override public long durationMs() {
        TrackState st = view.state();
        return st == null || !view.isShowingMusic() ? 0 : st.durationSec * 1000L;
    }

    @Override public void seekStep(int dir, int repeat) {
        TrackState st = view.state();
        if (st == null || st.durationSec <= 0) {
            if (repeat <= 1) view.showToast(T.t("Pas d'avance rapide sur un direct"));
            return;
        }
        if (seekTarget < 0) seekTarget = view.displayPositionMs(st);
        long step = repeat <= 1 ? 5000 : (repeat < 20 ? 1000 : (repeat < 50 ? 2000 : 4000));
        seekTarget = Math.max(0, Math.min(st.durationSec * 1000L - 1000, seekTarget + dir * step));
        view.setSeekPreview(seekTarget, true);
        menu.invalidate();
    }

    @Override public void seekCommit() {
        if (seekTarget < 0 || poller == null) return;
        poller.seek((int) (seekTarget / 1000));
        view.setSeekPreview(seekTarget, false);
        seekTarget = -1;
        menu.invalidate();
    }

    // ================================================================ menu host

    @Override public boolean isPlaying() {
        TrackState st = view.state();
        return st != null && st.isPlaying() && !st.isTv;
    }

    @Override public void transport(int which) {
        if (poller == null) return;
        TrackState st = view.state();
        if (which == 3) {
            if (poller != null) { poller.toggleMute(); menu.invalidate(); }
            return;
        }
        if (which == 4) {
            if (st == null || !view.isShowingMusic()) { view.showToast(T.t("Pas d'information disponible"), 2500); return; }
            menu.hide();
            infoView.open(st.title, st.artist, st.album, st.roomsLabel);
            return;
        }
        if (which == 1) {
            if (st != null && st.isPlaying() && !st.isTv) { poller.playPause(true); view.flash("pause"); }
            else { resume(); }
        } else if (view.isShowingMusic()) {
            if (which == 2) { poller.next();  }
            else { poller.previous(); }
        }
        menu.invalidate();
    }

    /** Play: resume the group, or bring back the last music (also when the soundbar is on TV). */
    private void resume() {
        if (poller == null) return;
        view.flash("play");
        poller.resumeLast(new Poller.Done() {
            @Override public void done(String error) {
                if (error != null) view.showToast(T.t("Impossible de relancer : ") + error, 3500);
            }
        });
    }

    private static final String[] LYR_OFF_NAMES = {"Plus tôt", "Un peu plus tôt", "Normal", "Un peu plus tard", "Plus tard"};
    private static final int[] LYR_OFF_MS = {-800, -400, 0, 400, 800};

    @Override public List<SonosClient.Room> rooms() {
        return last != null ? last.rooms : new ArrayList<SonosClient.Room>();
    }
}
