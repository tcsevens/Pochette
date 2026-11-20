package app.pochette.tv;

import app.pochette.tv.core.LocalNet;
import app.pochette.tv.core.MdnsResponder;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;
import app.pochette.tv.party.GameView;
import app.pochette.tv.party.PartyView;
import app.pochette.tv.menu.AmbianceMenu;
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
        LocalNet.setPreferIp(prefs.linkIp());
        T.set(prefs.lang());
        view = new NowPlayingView(this);
        view.setMode(prefs.mode());
        view.setLyricsMode(prefs.lyricsMode());
        view.setKaraokeColor(prefs.karaokeColor());
        view.setVinylStyle(prefs.vinylStyle());
        view.setNoLyricsStyle(prefs.noLyricsStyle());
        view.setClockEnabled(prefs.showClock());
        menu = new MenuOverlay(this, this);
        menu.setRight(prefs.menuRight());
        menu.setOpacity(prefs.menuOpacity());
        menu.setAutoHide(prefs.stripSec());
        view.setLyricsOffset(prefs.lyricsOffset());
        Themes.probe(this);
        ThemePreview.init(this, new Runnable() { @Override public void run() { runOnUiThread(new Runnable() { @Override public void run() { view.invalidate(); menu.invalidate(); } }); } });
        if (prefs.theme() != 0 && System.currentTimeMillis() - prefs.themeAt() < 12 * 3600 * 1000L) {
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
        amb = new AmbianceMenu(this);
        games = new GamesMenu(this, gameView, partyView, shareView);
        roomsMenu = new RoomsMenu(this);
        settings = new SettingsMenu(this, spotifyView, shareView);
        view.setShortcuts(amb.list());
        setContentView(root);
        hideSystemUi();
        if (prefs.autoLaunch()) WatchService.start(this);
        if (!prefs.hintShown()) {
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
        mdns.start(this);
        games.keepQueue();
    }

    @Override protected void onStop() {
        super.onStop();
        visible = false;
        games.shutdown();
        if (spotifyView != null && spotifyView.active()) spotifyView.close(false);
        if (infoView != null && infoView.active()) infoView.close();
        if (partyView != null && partyView.active()) partyView.close();
        settings.shutdown();
        mdns.stop();
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
        if (!soundsWarned && settings.systemSoundsOn()) {
            soundsWarned = true;
            view.showNotice(T.t("Sons système de la télé activés"),
                    T.t("Si ta Sonos est une barre de son branchée sur la télé, chaque son de navigation peut couper la musique. ")
                            + T.t("Pour une utilisation optimale, désactive-les : Menu › Réglages › Sons système, ")
                            + T.t("ou Paramètres de la télé › Écran et son › Sons système."), 14000);
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
        if (!tk.equals(lastTrackKey)) { lastTrackKey = tk; menu.wake(); }
        games.onSnapshot(s);
        view.setSnapshot(s);
        menu.invalidate();
        if (view.isShowingMusic()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ================================================================ remote control

    private String lastTrackKey = "";

    @Override public boolean stripOn() { return view.isShowingMusic(); }

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

    @Override public String roomsLabel() {
        TrackState st = view.state();
        return st == null || st.roomsLabel == null ? "" : st.roomsLabel;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (view.isSleeping()) { view.touch(); return true; }   // the first key only wakes the screen
        view.touch();
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
                if (e.getRepeatCount() == 0) view.idleMove(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
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
                    view.setMode(view.getMode() + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1));
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
        if (q.get(index).kind == 4) poller.spotifyJump(q.get(index).trackNr);
        else poller.jumpTo(q.get(index).trackNr);
    }

    // ================================================================ menu sections

    private VideoBg videoBg;
    private PartyView partyView;
    private ShareView shareView;
    private final MdnsResponder mdns = new MdnsResponder();
    private AmbianceMenu amb;
    private GamesMenu games;
    private RoomsMenu roomsMenu;
    private SettingsMenu settings;
    /** Set when a Back press only closed something: its key-up must not quit the app. */
    private boolean backEaten;

    @Override public boolean ambRows() { return amb.editing(); }
    @Override public List<MenuOverlay.Card> cards() { return amb.cards(); }
    @Override public void cardLaunch(int index) { amb.cardLaunch(index); }
    @Override public void cardEdit(int index) { amb.cardEdit(index); }
    @Override public boolean gameGrid() { return games.grid(); }
    @Override public List<MenuOverlay.Card> gameCards() { return games.cards(); }
    @Override public void gamePick(int index) { games.pick(index); }

    @Override public void onOpen(int section) {
        if (section == MenuOverlay.SEC_SETTINGS) settings.open();
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
