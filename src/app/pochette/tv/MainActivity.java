package app.pochette.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements Poller.Listener, MenuOverlay.Host {

    static volatile boolean visible;

    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private NowPlayingView view;
    private GameView gameView;
    private SpotifySetupView spotifyView;
    private InfoView infoView;
    private Game game;
    private boolean gamePick;
    private MenuOverlay menu;
    private Poller poller;
    private Prefs prefs;
    private Poller.Snapshot last;
    private final Map<String, Boolean> pendingRooms = new HashMap<String, Boolean>();
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
        menu = new MenuOverlay(this, this);
        menu.setRight(prefs.menuRight());
        menu.setOpacity(prefs.menuOpacity());
        menu.setAutoHide(prefs.stripSec());
        view.setLyricsOffset(prefs.lyricsOffset());
        ambs = prefs.ambiances();
        view.setShortcuts(ambs);
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
    }

    @Override protected void onStop() {
        super.onStop();
        visible = false;
        if (game != null && game.active) { game.abort(); gameView.setVisibility(View.GONE); }
        if (spotifyView != null && spotifyView.active()) spotifyView.close(false);
        if (infoView != null && infoView.active()) infoView.close();
        if (partyView != null && partyView.active()) partyView.close();
        if (setupServer != null) setupServer.stop();
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
        if (!soundsWarned && systemSoundsOn()) {
            soundsWarned = true;
            view.showNotice(T.t("Sons système de la télé activés"),
                    T.t("Si ta Sonos est une barre de son branchée sur la télé, chaque son de navigation peut couper la musique. ")
                            + T.t("Pour une utilisation optimale, désactive-les : Menu › Réglages › Sons système, ")
                            + T.t("ou Paramètres de la télé › Écran et son › Sons système."), 14000);
        }
        menu.refresh();
    }

    private boolean systemSoundsOn() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleSystemSounds() {
        boolean on = systemSoundsOn();
        if (Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, on ? 0 : 1);
                view.showToast(on ? T.t("Sons système désactivés") : T.t("Sons système réactivés"), 2500);
                return;
            } catch (Exception ignored) {}
        }
        String help = T.t("Autorise Pochette à « modifier les paramètres système », puis reviens ici et réessaie. ")
                + T.t("Sinon, désactive les sons toi-même : Paramètres de la télé › Écran et son › Sons système ")
                + T.t("(ou depuis le PC : adb shell settings put system sound_effects_enabled 0).");
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            view.showNotice(T.t("Autorisation nécessaire"), help, 15000);
        } catch (Exception ex) {
            view.showNotice(T.t("Impossible de les couper depuis l'appli"), help, 15000);
        }
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
        if (game != null && game.active) game.onSnapshot(s);
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
        if (game != null && game.active) {
            if (e.getRepeatCount() == 0 || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) gameView.handleKey(code);
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
                if (e.getRepeatCount() == 0) launch(view.idleSelected());
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
        if (game != null && game.active) return true;
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

    // ================================================================ ambiances

    private VideoBg videoBg;
    private PartyView partyView;
    private ShareView shareView;
    private QueueServer queueServer;
    private int gameSel = -1;
    private int setSub;   // Réglages: 0 main, 1 Spotify, 2 advanced
    private static final int[] Q_ROUNDS = {5, 10, 15, 20}, TURN_VALUES = {1, 2, 3, 5}, DUR_VALUES = {45, 60, 90, 120};
    private boolean backEaten;
    private List<Ambiance> ambs = new ArrayList<Ambiance>();
    private int ambEdit = -1;
    private boolean ambPick;
    private List<SonosClient.Item> favs;
    private String favsError;

    private static final String[] VOL_NAMES = {"Inchangé", "10 %", "15 %", "20 %", "25 %", "30 %", "35 %", "40 %", "50 %"};
    private static final String[] DISP_NAMES = {"Inchangé", "Classique", "Vinyle", "Paroles"};
    private static final int[] VOL_VALUES = {-1, 10, 15, 20, 25, 30, 35, 40, 50};
    private static final String[] SLEEP_NAMES = {"Non", "30 min", "1 h", "2 h"};
    private static final int[] SLEEP_VALUES = {0, 30, 60, 120};

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    private String roomsLabel(Ambiance a) {
        if (a.rooms.isEmpty()) return T.t("Pièces en cours");
        StringBuilder sb = new StringBuilder();
        for (SonosClient.Room r : rooms()) {
            if (a.rooms.contains(r.uuid)) { if (sb.length() > 0) sb.append(" + "); sb.append(r.name); }
        }
        return sb.length() == 0 ? a.rooms.size() + T.t(" pièce(s)") : sb.toString();
    }

    @Override public boolean gameGrid() { return gameSel < 0; }

    @Override public void gamePick(int index) {
        if (index == 7) { openShare(); return; }
        gameSel = index;
        if (index < 4) prefs.setGameInt("mode", index);
        menu.ambChanged();
    }

    @Override public List<MenuOverlay.Card> gameCards() {
        List<MenuOverlay.Card> out = new ArrayList<MenuOverlay.Card>();
        String[] names = {"Blind test", T.t("Chaise musicale"), T.t("N'oubliez pas les paroles"), T.t("Pochette pixellisée"), "Quiz", T.t("Devine-moi !"), T.t("Dessine-moi !")};
        String[] subs = {T.t("Devinez le morceau"), T.t("Dansez, stop !"), T.t("Finissez la phrase"), T.t("Reconnaissez la pochette"), T.t("Culture générale"), T.t("Faites deviner sans le dire"), T.t("Dessinez, les autres devinent")};
        int[][] cols = {{0xFF8A3FD8, 0xFF2A0F4A}, {0xFFE8674A, 0xFF6A1E12}, {0xFF2B9FD0, 0xFF0F3050}, {0xFF3FC88A, 0xFF0C4028},
                {0xFFF0BE3A, 0xFF7A4A08}, {0xFFE8524F, 0xFF5A0E1E}, {0xFF4A9AF0, 0xFF0F3070}};
        for (int i = 0; i < names.length; i++) out.add(new MenuOverlay.Card(T.t(names[i]), T.t(subs[i]), cols[i][0], cols[i][1], i));
        SpotifyClient sp = poller != null ? poller.spotify : null;
        if (sp != null && sp.connected() && prefs.spotifyOn())
            out.add(new MenuOverlay.Card(T.t("File d'attente partagée"), T.t("Chacun ajoute un morceau"), 0xFF1DB954, 0xFF0B4A22, 7));
        return out;
    }

    @Override public List<MenuOverlay.Card> cards() {
        List<MenuOverlay.Card> out = new ArrayList<MenuOverlay.Card>();
        for (Ambiance a : ambs) {
            String sub;
            if (a.fav.length() == 0) sub = T.t("Choisis une playlist");
            else {
                sub = roomsLabel(a);
                if (a.ramp) sub += T.t(" · volume qui monte");
                else if (a.volume >= 0) sub += " · " + a.volume + " %";
            }
            out.add(new MenuOverlay.Card(T.t(a.name), sub, a.theme));
        }
        out.add(new MenuOverlay.Card(T.t("Nouvelle ambiance"), T.t("À partir d'un favori Sonos"), -1));
        return out;
    }

    @Override public boolean ambRows() { return ambEdit >= 0; }

    private String favText(SonosClient.Item f) {
        if (f.kind == 3) return Ambiance.label(Ambiance.NOW) + "  ·  " + f.desc;
        return f.desc.length() > 0 ? f.title + "  ·  " + f.desc : f.title;
    }

    private void loadFavs() {
        favs = null;
        favsError = null;
        if (poller == null) return;
        poller.loadFavorites(new Poller.Favs() {
            @Override public void got(List<SonosClient.Item> l, String error) {
                favs = l;
                favsError = error;
                menu.refresh();
            }
        });
    }

    @Override public void cardLaunch(int index) {
        if (index >= ambs.size()) { cardEdit(index); menu.ambChanged(); return; }
        Ambiance a = ambs.get(index);
        if (a.fav.length() == 0) {
            ambEdit = index;
            ambPick = true;
            loadFavs();
            view.showToast(T.t("Choisis d'abord la playlist de « ") + a.name + T.t(" »"), 3000);
            menu.ambChanged();
            return;
        }
        launch(a);
    }

    @Override public void cardEdit(int index) {
        if (index >= ambs.size()) {
            ambs.add(new Ambiance());
            ambEdit = ambs.size() - 1;
            ambPick = true;
            loadFavs();
        } else {
            ambEdit = index;
            ambPick = false;
        }
    }

    @Override public boolean onBack(int section) {
        if (section == MenuOverlay.SEC_SETTINGS && setSub != 0) { setSub = 0; return true; }
        if (section == MenuOverlay.SEC_GAMES) {
            if (gamePick) { gamePick = false; return true; }
            if (gameSel >= 0) { gameSel = -1; return true; }
            return false;
        }
        if (section != MenuOverlay.SEC_AMB || ambEdit < 0) return false;
        Ambiance a = ambEdit < ambs.size() ? ambs.get(ambEdit) : null;
        if (ambPick && a != null && (a.fav.length() > 0 || a.preset)) {
            ambPick = false;                        // picker -> editor
        } else {
            if (a != null && a.fav.length() == 0 && !a.preset) ambs.remove(ambEdit);   // new one, abandoned
            ambEdit = -1;
            ambPick = false;
        }
        return true;
    }

    private void launch(final Ambiance a) {
        if (poller == null) return;
        view.showToast(T.t("Ambiance ") + T.t(a.name) + "…", 4000);
        menu.hide();
        poller.launchAmbiance(a, new Poller.Done() {
            @Override public void done(String error) {
                if (error != null) { view.showToast(T.t("Impossible de lancer « ") + a.name + T.t(" » : ") + error, 6000); return; }
                view.setHome(false);
                prefs.theme(a.theme, a.name);
                view.setTheme(a.theme, a.name);
                if (a.display >= 0) {
                    view.setMode(a.display); prefs.mode(a.display);
                    if (a.display == NowPlayingView.MODE_VINYL && a.theme == Ambiance.T_SOUL) { view.setVinylStyle(2); prefs.vinylStyle(2); }
                }
                view.showToast(T.t("Ambiance ") + T.t(a.name), 2500);
            }
        });
    }

    private List<MenuOverlay.Row> ambRowsList() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        if (ambEdit < 0 || ambEdit >= ambs.size()) return out;
        Ambiance a = ambs.get(ambEdit);
        if (ambPick) {
            out.add(MenuOverlay.Row.text(T.t("Choisis la musique de « ") + T.t(a.name) + T.t(" » parmi tes favoris et playlists Sonos.")));
            if (favs == null) out.add(MenuOverlay.Row.text(T.t("Chargement des favoris…")));
            else if (favsError != null) out.add(MenuOverlay.Row.text(T.t("Impossible de lire les favoris : ") + favsError));
            else if (favs.isEmpty()) out.add(MenuOverlay.Row.text(T.t("Aucun favori pour l'instant. Dans l'appli Sonos, ouvre une playlist, puis ⋯ › « Ajouter à Mes favoris Sonos ». Elle apparaîtra ici.")));
            else {
                for (int i = 0; i < favs.size(); i++) {
                    SonosClient.Item f = favs.get(i);
                    out.add(MenuOverlay.Row.check("fav:" + i, favText(f), Ambiance.keyOf(f).equals(a.fav)));
                }
                out.add(MenuOverlay.Row.text(T.t("Pour en ajouter : appli Sonos, ⋯ sur une playlist › « Ajouter à Mes favoris Sonos ».")));
            }
            return out;
        }
        out.add(MenuOverlay.Row.text(T.t("Ambiance « ") + a.name + T.t(" »")));
        out.add(MenuOverlay.Row.action("alaunch", T.t("Lancer maintenant")));
        out.add(MenuOverlay.Row.action("apick", T.t("Playlist : ") + (a.fav.length() > 0 ? Ambiance.label(a.fav) : T.t("à choisir"))));
        out.add(MenuOverlay.Row.toggle("ashuffle", T.t("Lecture aléatoire"), a.shuffle));
        out.add(MenuOverlay.Row.text(T.t("Pièces (aucune cochée = celles qui jouent déjà)")));
        for (SonosClient.Room r : rooms()) out.add(MenuOverlay.Row.check("aroom:" + r.uuid, r.name, a.rooms.contains(r.uuid)));
        out.add(MenuOverlay.Row.chips("avol", a.ramp ? T.t("Volume à atteindre") : T.t("Volume de départ"), VOL_NAMES, indexOf(VOL_VALUES, a.volume)));
        out.add(MenuOverlay.Row.toggle("aramp", T.t("Volume qui monte doucement (3 min)"), a.ramp));
        out.add(MenuOverlay.Row.chips("atheme", T.t("Écran"), Ambiance.shownNames(), Ambiance.toIndex(a.theme)));
        out.add(MenuOverlay.Row.chips("adisp", T.t("Affichage"), DISP_NAMES, a.display + 1));
        out.add(MenuOverlay.Row.chips("asleep", T.t("Arrêt automatique"), SLEEP_NAMES, indexOf(SLEEP_VALUES, a.sleepMin)));
        if (!a.preset) out.add(MenuOverlay.Row.action("adelete", T.t("Supprimer cette ambiance")));
        return out;
    }

    /** Handles the rows of the Ambiances section; true when the id was one of them. */
    private boolean ambActivate(String id) {
        if (ambEdit < 0 || ambEdit >= ambs.size()) return false;
        Ambiance a = ambs.get(ambEdit);
        if ("alaunch".equals(id)) {
            if (a.fav.length() == 0) { ambPick = true; loadFavs(); menu.ambChanged(); }
            else { ambEdit = -1; launch(a); }
        } else if ("apick".equals(id)) {
            ambPick = true; loadFavs(); menu.ambChanged();
        } else if ("ashuffle".equals(id)) {
            a.shuffle = !a.shuffle;
        } else if ("aramp".equals(id)) {
            a.ramp = !a.ramp;
        } else if (id.startsWith("aroom:")) {
            String u = id.substring(6);
            if (!a.rooms.remove(u)) a.rooms.add(u);
        } else if ("adelete".equals(id)) {
            ambs.remove(ambEdit);
            ambEdit = -1;
            prefs.ambiances(ambs);
            menu.ambChanged();
            return true;
        } else if (id.startsWith("fav:") && favs != null) {
            int i = Integer.parseInt(id.substring(4));
            if (i < favs.size()) {
                a.fav = Ambiance.keyOf(favs.get(i));
                if (!a.preset && "Nouvelle ambiance".equals(a.name)) a.name = Ambiance.label(a.fav);
                ambPick = false;
                menu.ambChanged();
            }
        } else {
            return false;
        }
        prefs.ambiances(ambs);
        return true;
    }


    // ================================================================ games

    private static final int[] ROUND_VALUES = {10, 20, 30, 0};
    private static final String[] ROUND_NAMES = {"10", "20", "30", "Infini"};

    private List<MenuOverlay.Row> gameRows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        if (gamePick) {
            out.add(MenuOverlay.Row.text(T.t("Choisis la playlist du jeu parmi tes favoris et playlists Sonos.")));
            if (favs == null) out.add(MenuOverlay.Row.text(T.t("Chargement des favoris…")));
            else if (favsError != null) out.add(MenuOverlay.Row.text(T.t("Impossible de lire les favoris : ") + favsError));
            else if (favs.isEmpty()) out.add(MenuOverlay.Row.text(T.t("Aucun favori pour l'instant. Dans l'appli Sonos : ⋯ sur une playlist › « Ajouter à Mes favoris Sonos ».")));
            else for (int i = 0; i < favs.size(); i++) {
                SonosClient.Item f = favs.get(i);
                out.add(MenuOverlay.Row.check("gfav:" + i, favText(f), Ambiance.keyOf(f).equals(prefs.gameFav())));
            }
            return out;
        }
        if (gameSel >= 4) {
            out.add(MenuOverlay.Row.button("pstart", T.t("▶  LANCER LA PARTIE")));
            out.add(MenuOverlay.Row.chips("pteams", T.t("Équipes"), new String[]{"2", "3", "4", "5", "6"}, Math.max(0, Math.min(4, prefs.gameInt("pteams", 3) - 2))));
            if (gameSel == 4) out.add(MenuOverlay.Row.chips("pcat", T.t("Catégorie"), new String[]{T.t("Culture générale"), T.t("France"), T.t("Québec"), T.t("Tout")}, Math.max(0, Math.min(3, prefs.gameInt("pc", 3)))));
            if (gameSel == 4) out.add(MenuOverlay.Row.chips("prounds", T.t("Questions"), new String[]{"5", "10", "15", "20"}, Math.max(0, Math.min(3, prefs.gameInt("pq", 1)))));
            else {
                out.add(MenuOverlay.Row.chips("prounds", T.t("Tours par équipe"), new String[]{"1", "2", "3", "5"}, Math.max(0, Math.min(3, prefs.gameInt("pt", 1)))));
                out.add(MenuOverlay.Row.chips("pdur", T.t("Durée d'un tour"), new String[]{"45 s", "60 s", "90 s", "120 s"}, Math.max(0, Math.min(3, prefs.gameInt("pd", 1)))));
            }
            String h;
            if (gameSel == 4) h = T.t("La télé pose les questions. Les équipes discutent, puis OK montre la réponse et ◀ ▶ + OK donnent le point à l'équipe qui a trouvé.");
            else if (gameSel == 5) h = T.t("Un joueur décrit un mot sans dire les mots interdits. Il le voit sur son téléphone (QR code), avec les boutons « Trouvé ! » et « Passer ». La télé gère le temps et les points.");
            else h = T.t("Un joueur dessine avec son doigt sur son téléphone (QR code), le dessin apparaît en grand sur la télé. Les autres devinent à voix haute.");
            out.add(MenuOverlay.Row.text(h));
            if (gameSel != 4) out.add(MenuOverlay.Row.text(T.t("Les téléphones doivent être sur le même Wi-Fi que la télé. Aucune enceinte Sonos n'est nécessaire.")));
            return out;
        }
        int mode = gameSel >= 0 && gameSel < 4 ? gameSel : prefs.gameInt("mode", 0);
        out.add(MenuOverlay.Row.button("glaunch", T.t("▶  LANCER LA PARTIE")));
        String fav = prefs.gameFav();
        out.add(MenuOverlay.Row.action("gpick", T.t("Playlist : ") + (fav.length() > 0 ? Ambiance.label(fav) : T.t("à choisir"))));
        if (mode != Game.CHAIRS) out.add(MenuOverlay.Row.chips("gteams", T.t("Équipes"), new String[]{"2", "3", "4", "5", "6"}, Math.max(0, Math.min(4, prefs.gameInt("teams", 3) - 2))));
        out.add(MenuOverlay.Row.chips("grounds", T.t("Manches"), ROUND_NAMES, indexOf(ROUND_VALUES, prefs.gameInt("rounds", 20))));
        if (mode == Game.CHAIRS) out.add(MenuOverlay.Row.chips("gstart", T.t("Départ du morceau"), new String[]{T.t("Début"), T.t("Milieu"), T.t("Au hasard")}, prefs.gameInt("start", 1)));
        if (mode == Game.LYRICS) out.add(MenuOverlay.Row.chips("gdiff", T.t("Difficulté"), new String[]{T.t("Facile"), T.t("Difficile")}, prefs.gameInt("diff", 0)));
        String help;
        if (mode == Game.BLIND) help = T.t("12 s pour deviner, 15 s de réponse : l'artiste, puis le titre. ◀ ▶ + OK donnent le point à une équipe. ▶ pendant le compte à rebours passe au morceau suivant.");
        else if (mode == Game.CHAIRS) help = T.t("La musique se coupe au hasard, entre 8 et 35 s après son départ. OK lance la musique suivante. ▶ pendant la musique la passe.");
        else if (mode == Game.PIXEL) help = T.t("La pochette apparaît en gros pixels et se précise en 20 s pendant que la musique joue. Idéal pour des musiques de film ou de dessins animés. ▶ passe au morceau suivant.");
        else help = T.t("Le karaoké s'arrête sur une phrase importante après environ 30 s de chant : à vous de la finir. Il faut des paroles synchronisées. ▶ passe au morceau suivant.");
        out.add(MenuOverlay.Row.text(help));
        out.add(MenuOverlay.Row.text(T.t("Les jeux demandent une playlist ou un album Sonos (pas Spotify Connect ni une radio).")));
        return out;
    }

    private void gameActivate(String id) {
        if ("gpick".equals(id)) {
            gamePick = true;
            loadFavs();
            menu.refresh();
        } else if (id.startsWith("gfav:") && favs != null) {
            int i = Integer.parseInt(id.substring(5));
            if (i < favs.size()) { prefs.gameFav(Ambiance.keyOf(favs.get(i))); gamePick = false; menu.refresh(); }
        } else if ("pstart".equals(id)) {
            menu.hide();
            partyView.start(gameSel - 4, prefs.gameInt("pteams", 3), gameSel == 4 ? Q_ROUNDS[Math.max(0, Math.min(3, prefs.gameInt("pq", 1)))] : TURN_VALUES[Math.max(0, Math.min(3, prefs.gameInt("pt", 1)))],
                    DUR_VALUES[Math.max(0, Math.min(3, prefs.gameInt("pd", 1)))], Math.max(0, Math.min(3, prefs.gameInt("pc", 3))));
        } else if ("glaunch".equals(id)) {
            launchGame();
        }
    }

    private void launchGame() {
        if (poller == null) return;
        if (prefs.gameFav().length() == 0) {
            view.showToast(T.t("Choisis d'abord la playlist du jeu"), 3000);
            gamePick = true;
            loadFavs();
            menu.refresh();
            return;
        }
        Ambiance a = new Ambiance();
        a.name = T.t("Jeu");
        a.fav = prefs.gameFav();
        a.shuffle = true;
        a.volume = -1;
        game = new Game(poller, new Game.Host() {
            @Override public void gameFinished() {
                gameView.setVisibility(View.GONE);
                view.showToast(T.t("Partie terminée"), 2500);
            }
        });
        game.lyricOffset = prefs.lyricsOffset();
        game.configure(prefs.gameInt("mode", 0), prefs.gameInt("teams", 3), prefs.gameInt("rounds", 20),
                prefs.gameInt("diff", 0), prefs.gameInt("start", 1));
        gameView.bind(game);
        gameView.setVisibility(View.VISIBLE);
        menu.hide();
        game.start(a);
        gameView.invalidate();
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

    private int lyrOffIndex() {
        int v = prefs.lyricsOffset();
        for (int i = 0; i < LYR_OFF_MS.length; i++) if (LYR_OFF_MS[i] == v) return i;
        return 2;
    }

    private List<SonosClient.Room> rooms() {
        return last != null ? last.rooms : new ArrayList<SonosClient.Room>();
    }

    @Override public void onOpen(int section) {
        if (section == MenuOverlay.SEC_SETTINGS) setSub = 0;
        if (section == MenuOverlay.SEC_ROOMS) {
            pendingRooms.clear();
            String coord = last != null ? last.coordinatorUuid : null;
            for (SonosClient.Room r : rooms()) pendingRooms.put(r.uuid, coord != null && coord.equals(r.coordinatorUuid));
        }
    }

    private boolean needsOverlayPermission() {
        return Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this);
    }

    @Override public List<MenuOverlay.Row> rows(int section) {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        if (section == MenuOverlay.SEC_AMB) return ambRowsList();
        if (section == MenuOverlay.SEC_GAMES) return gameRows();
        switch (section) {
            case MenuOverlay.SEC_ROOMS: {
                List<SonosClient.Room> rs = rooms();
                if (rs.isEmpty() || last == null || last.coordinatorUuid == null) {
                    out.add(MenuOverlay.Row.text(T.t("Aucune enceinte trouvée pour l'instant.")));
                    break;
                }
                out.add(MenuOverlay.Row.text(T.t("Coche les pièces où la musique doit jouer.")));
                for (SonosClient.Room r : rs) {
                    Boolean on = pendingRooms.get(r.uuid);
                    out.add(MenuOverlay.Row.check("room:" + r.uuid, r.name, on != null && on));
                }
                out.add(MenuOverlay.Row.action("apply", T.t("Appliquer")));
                break;
            }
            case MenuOverlay.SEC_DISPLAY:
                out.add(MenuOverlay.Row.head(T.t("Musique")));
                out.add(MenuOverlay.Row.chips("style", T.t("Style"), NowPlayingView.MODE_NAMES, view.getMode()));
                out.add(MenuOverlay.Row.chips("vinyl", T.t("Couleur du vinyle"), NowPlayingView.VINYL_NAMES, prefs.vinylStyle()));
                out.add(MenuOverlay.Row.head(T.t("Paroles")));
                out.add(MenuOverlay.Row.chips("lyrics", T.t("Paroles"), NowPlayingView.LYRICS_NAMES, prefs.lyricsMode()));
                if (prefs.lyricsMode() == 0) out.add(MenuOverlay.Row.chips("kcolor", T.t("Couleur du karaoké"), NowPlayingView.KARAOKE_COLOR_NAMES, prefs.karaokeColor()));
                out.add(MenuOverlay.Row.chips("nolyrics", T.t("Quand il n'y a pas de paroles"), NowPlayingView.NO_LYRICS_NAMES, prefs.noLyricsStyle()));
                out.add(MenuOverlay.Row.chips("lyroff", T.t("Décalage des paroles"), LYR_OFF_NAMES, lyrOffIndex()));
                out.add(MenuOverlay.Row.head(T.t("Écran")));
                out.add(MenuOverlay.Row.chips("theme", T.t("Fond d'écran"), Ambiance.shownNames(), Ambiance.toIndex(view.getTheme())));
                out.add(MenuOverlay.Row.toggle("clock", T.t("Horloge dans le coin"), prefs.showClock()));
                out.add(MenuOverlay.Row.chips("strip", T.t("Lecteur du bas"), new String[]{T.t("Toujours visible"), T.t("S'efface après 10 s")}, prefs.stripSec() == 0 ? 0 : 1));
                break;
            case MenuOverlay.SEC_SETTINGS: {
                if (setSub == 1) {
                    out.add(MenuOverlay.Row.head(T.t("Spotify")));
                    out.addAll(spotifyRows());
                    break;
                }
                if (setSub == 2) {
                    out.add(MenuOverlay.Row.head(T.t("Avancé")));
                    out.add(MenuOverlay.Row.toggle("sounds", T.t("Sons système de la télé") + (systemSoundsOn() ? "  ⚠" : ""), systemSoundsOn()));
                    String ip2 = prefs.manualIp().length() > 0 ? prefs.manualIp() : "auto";
                    out.add(MenuOverlay.Row.action("ip", T.t("Adresse IP de l'enceinte : ") + ip2));
                    break;
                }
                List<SonosClient.Room> rs = rooms();
                String[] names = new String[rs.size() + 1];
                names[0] = T.t("Automatique");
                int sel = 0;
                for (int i = 0; i < rs.size(); i++) {
                    names[i + 1] = rs.get(i).name;
                    if (rs.get(i).uuid.equals(prefs.roomUuid())) sel = i + 1;
                }
                out.add(MenuOverlay.Row.head(T.t("Enceintes")));
                out.add(MenuOverlay.Row.chips("follow", T.t("Enceinte suivie"), names, sel));
                out.add(MenuOverlay.Row.action("rescan", T.t("Relancer la recherche des enceintes")));
                out.add(MenuOverlay.Row.head(T.t("Lancement")));
                out.add(MenuOverlay.Row.toggle("auto", T.t("S'afficher quand la musique démarre"), prefs.autoLaunch()));
                out.add(MenuOverlay.Row.toggle("resume", T.t("Relancer la dernière musique à l'ouverture"), prefs.resumeOnOpen()));
                if (prefs.autoLaunch() && needsOverlayPermission()) {
                    out.add(MenuOverlay.Row.action("perm", T.t("Autoriser l'affichage automatique…")));
                }
                out.add(MenuOverlay.Row.head(T.t("Interface")));
                out.add(MenuOverlay.Row.chips("lang", T.t("Langue"), new String[]{T.t("Automatique"), "Français", "English", "Español"}, prefs.lang()));
                out.add(MenuOverlay.Row.chips("side", T.t("Position du menu"), new String[]{T.t("Gauche"), T.t("Droite")}, prefs.menuRight() ? 1 : 0));
                out.add(MenuOverlay.Row.chips("opacity", T.t("Transparence du menu"), new String[]{T.t("Forte"), T.t("Moyenne"), T.t("Faible")}, prefs.menuOpacity()));
                out.add(MenuOverlay.Row.head(T.t("Plus")));
                out.add(MenuOverlay.Row.action("sub1", T.t("Spotify…")));
                out.add(MenuOverlay.Row.action("sub2", T.t("Avancé…")));
                break;
            }
            case MenuOverlay.SEC_HELP:
                out.add(MenuOverlay.Row.text("Pochette " + versionName()));
                out.add(MenuOverlay.Row.text(T.t("Un écran « en cours de lecture » pour tes enceintes Sonos, avec des ambiances et des jeux. Projet indépendant, non affilié à Sonos ni à Spotify.")));
                out.add(MenuOverlay.Row.text(T.t("Vie privée : Pochette parle à tes enceintes sur ton réseau local, sans compte ni cloud. Seules les paroles (lrclib.net) et les résumés (Wikipédia) sont cherchés en ligne, et Spotify si tu l'actives.")));
                out.add(MenuOverlay.Row.text(T.t("Les vidéos d'ambiance viennent de banques de vidéos libres de droits.")));
                out.add(MenuOverlay.Row.text(T.t("Sonos et Spotify sont des marques de leurs propriétaires.")));
                out.add(MenuOverlay.Row.text(T.t("Développé avec l'aide de Claude, l'assistant d'Anthropic.")));
                break;
        }
        return out;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    @Override public void onChoice(int section, MenuOverlay.Row row, int index) {
        String id = row.id;
        if ("style".equals(id)) { view.setMode(index); prefs.mode(index); }
        else if ("vinyl".equals(id)) { prefs.vinylStyle(index); view.setVinylStyle(index); }
        else if ("lyrics".equals(id)) { prefs.lyricsMode(index); view.setLyricsMode(index); menu.refresh(); }
        else if ("kcolor".equals(id)) { prefs.karaokeColor(index); view.setKaraokeColor(index); }
        else if ("nolyrics".equals(id)) { prefs.noLyricsStyle(index); view.setNoLyricsStyle(index); }
        else if ("theme".equals(id)) {
            index = Ambiance.fromIndex(index);
            String name = index == 0 ? "" : (index == prefs.theme() ? prefs.themeName() : "");
            prefs.theme(index, name);
            view.setTheme(index, name);
        }
        else if (section == MenuOverlay.SEC_GAMES) {
            if ("pteams".equals(id)) prefs.setGameInt("pteams", index + 2);
            else if ("pcat".equals(id)) prefs.setGameInt("pc", index);
            else if ("prounds".equals(id)) prefs.setGameInt(gameSel == 4 ? "pq" : "pt", index);
            else if ("pdur".equals(id)) prefs.setGameInt("pd", index);
            else if ("gmode".equals(id)) { prefs.setGameInt("mode", index); menu.refresh(); }
            else if ("gteams".equals(id)) prefs.setGameInt("teams", index + 2);
            else if ("grounds".equals(id)) prefs.setGameInt("rounds", ROUND_VALUES[index]);
            else if ("gstart".equals(id)) prefs.setGameInt("start", index);
            else if ("gdiff".equals(id)) prefs.setGameInt("diff", index);
        }
        else if (section == MenuOverlay.SEC_AMB && ambEdit >= 0 && ambEdit < ambs.size()) {
            Ambiance a = ambs.get(ambEdit);
            if ("avol".equals(id)) a.volume = VOL_VALUES[index];
            else if ("atheme".equals(id)) {
                index = Ambiance.fromIndex(index);
                a.theme = index;
                if (index == Ambiance.T_PARTY && a.volume < 0) {          // a party starts with some energy: louder, shuffled, no sleep timer
                    a.volume = 40; a.shuffle = true; a.sleepMin = 0;
                    menu.ambChanged();
                }
            }
            else if ("adisp".equals(id)) a.display = index - 1;
            else if ("asleep".equals(id)) a.sleepMin = SLEEP_VALUES[index];
            prefs.ambiances(ambs);
        }
        else if ("lang".equals(id)) { prefs.lang(index); T.set(index); recreate(); }
        else if ("strip".equals(id)) { int v = index == 0 ? 0 : 10; prefs.stripSec(v); menu.setAutoHide(v); }
        else if ("opacity".equals(id)) { prefs.menuOpacity(index); menu.setOpacity(index); }
        else if ("lyroff".equals(id)) { prefs.lyricsOffset(LYR_OFF_MS[index]); view.setLyricsOffset(LYR_OFF_MS[index]); }
        else if ("side".equals(id)) { prefs.menuRight(index == 1); menu.setRight(index == 1); }
        else if ("follow".equals(id)) {
            List<SonosClient.Room> rs = rooms();
            prefs.roomUuid(index == 0 || index - 1 >= rs.size() ? "" : rs.get(index - 1).uuid);
            if (poller != null) poller.poke();
        }
    }

    // ================================================================ Spotify (optional, own Client ID)

    private SetupServer setupServer;

    private List<MenuOverlay.Row> spotifyRows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        SpotifyClient sp = poller != null ? poller.spotify : new SpotifyClient(prefs);
        if (sp.connected() && sp.needsReconnect()) {
            out.add(MenuOverlay.Row.status(T.t("Spotify : à reconnecter"), 2));
            out.add(MenuOverlay.Row.action("spsetup", T.t("Refaire la connexion…")));
        } else if (sp.connected()) {
            out.add(MenuOverlay.Row.status(T.t("Spotify : connecté"), prefs.spotifyOn() ? 1 : 3));
            out.add(MenuOverlay.Row.toggle("spon", T.t("Spotify Connect : À suivre et reprise de la lecture"), prefs.spotifyOn()));
            out.add(MenuOverlay.Row.action("spsetup", T.t("Reconfigurer Spotify depuis ton téléphone…")));
        } else {
            out.add(MenuOverlay.Row.status(sp.configured() ? T.t("Spotify : Client ID enregistré, connexion à terminer")
                    : T.t("Spotify : non configuré (facultatif)"), sp.configured() ? 2 : 3));
            out.add(MenuOverlay.Row.action("spsetup", T.t("Configurer Spotify depuis ton téléphone…")));
        }
        if (sp.configured()) out.add(MenuOverlay.Row.action("spout", T.t("Oublier Spotify (Client ID et connexion)")));
        return out;
    }

    private void openShare() {
        if (poller == null) return;
        if (queueServer == null || !queueServer.isRunning()) {
            queueServer = new QueueServer(poller.spotify, new QueueServer.Listener() {
                @Override public boolean enqueue(String uri, String title) throws Exception { return poller.enqueueSonos(uri, title); }
                @Override public void added(final String title, final String artist) {
                    runOnUiThread(new Runnable() { @Override public void run() { view.showToast(T.t("Ajouté : ") + title, 4000); } });
                }
            });
            if (!queueServer.start()) { view.showToast(T.t("Impossible de démarrer la page pour téléphone"), 3000); return; }
        }
        menu.hide();
        shareView.open(queueServer);
    }

    private void startSpotifySetup() {
        if (poller == null) return;
        if (setupServer == null) {
            setupServer = new SetupServer(poller.spotify, prefs, new SetupServer.Listener() {
                @Override public void changed() {
                    runOnUiThread(new Runnable() { @Override public void run() { menu.refresh(); } });
                }
            });
        }
        menu.hide();
        spotifyView.open(setupServer, poller.spotify, !poller.spotify.configured());
    }

    @Override public void onActivate(int section, MenuOverlay.Row row) {
        String id = row.id;
        if (id == null) return;
        if (section == MenuOverlay.SEC_AMB) { ambActivate(id); return; }
        if (section == MenuOverlay.SEC_GAMES) { gameActivate(id); return; }
        if (id.startsWith("room:")) {
            String uuid = id.substring(5);
            Boolean on = pendingRooms.get(uuid);
            pendingRooms.put(uuid, on == null || !on);
        } else if ("apply".equals(id)) {
            applyRooms();
        } else if ("resume".equals(id)) {
            prefs.resumeOnOpen(!prefs.resumeOnOpen());
        } else if ("sounds".equals(id)) {
            toggleSystemSounds();
        } else if ("spon".equals(id)) {
            prefs.spotifyOn(!prefs.spotifyOn());
        } else if ("sub1".equals(id) || "sub2".equals(id)) {
            setSub = "sub1".equals(id) ? 1 : 2;
            menu.refresh();
        } else if ("qshare".equals(id)) {
            openShare();
        } else if ("qstop".equals(id)) {
            if (queueServer != null) queueServer.stop();
            menu.refresh();
        } else if ("spsetup".equals(id)) {
            startSpotifySetup();
        } else if ("spstop".equals(id)) {
            if (setupServer != null) setupServer.stop();
            menu.refresh();
        } else if ("spout".equals(id)) {
            prefs.spDisconnect();
            prefs.spotifyOn(false);
            menu.refresh();
        } else if ("clock".equals(id)) {
            prefs.showClock(!prefs.showClock());
            view.setClockEnabled(prefs.showClock());
        } else if ("auto".equals(id)) {
            boolean on = !prefs.autoLaunch();
            prefs.autoLaunch(on);
            if (on) {
                WatchService.start(this);
                if (needsOverlayPermission()) view.showToast(T.t("Il manque une autorisation : voir « Autoriser l'affichage automatique »"), 4000);
            } else {
                WatchService.stop(this);
            }
        } else if ("perm".equals(id)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception ex) {
                view.showToast(T.t("Écran absent sur cette télé : utilise la commande adb du guide"), 5000);
            }
        } else if ("ip".equals(id)) {
            askIp();
        } else if ("rescan".equals(id)) {
            prefs.lastIp("");
            if (poller != null) poller.reset();
            view.showToast(T.t("Recherche des enceintes…"));
        }
    }

    private void applyRooms() {
        final String coord = last != null ? last.coordinatorUuid : null;
        if (coord == null || poller == null) return;
        List<String> wanted = new ArrayList<String>();
        if (Boolean.TRUE.equals(pendingRooms.get(coord))) wanted.add(coord);
        for (SonosClient.Room r : rooms()) {
            if (!r.uuid.equals(coord) && Boolean.TRUE.equals(pendingRooms.get(r.uuid))) wanted.add(r.uuid);
        }
        if (wanted.isEmpty()) {
            view.showToast(T.t("Choisis au moins une pièce"));
            return;
        }
        view.showToast(T.t("Changement des pièces…"));
        poller.applyGroup(coord, wanted, new Poller.Done() {
            @Override public void done(String error) {
                view.showToast(error == null ? T.t("Pièces mises à jour") : T.t("Impossible : ") + error, 3000);
            }
        });
        menu.hide();
    }

    private void askIp() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setHint("ex. 192.168.1.42 (vide = automatique)");
        et.setText(prefs.manualIp());
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(T.t("Adresse IP d'une enceinte Sonos"))
                .setMessage(T.t("Laisse vide pour la détection automatique. L'IP se trouve dans l'appli Sonos : Réglages › Système › À propos."))
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.manualIp(et.getText().toString().trim());
                        if (poller != null) poller.reset();
                        menu.refresh();
                    }
                })
                .setNegativeButton(T.t("Annuler"), null)
                .show();
    }
}
