package app.pochette.tv.menu;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.party.Game;
import app.pochette.tv.party.GameView;
import app.pochette.tv.party.PartyView;
import app.pochette.tv.party.QueueServer;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.spotify.SpotifyClient;
import app.pochette.tv.ui.MenuOverlay;
import app.pochette.tv.ui.NowPlayingView;
import app.pochette.tv.ui.ShareView;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The Games section: the grid of games, their options, and the shared-queue card. */
public final class GamesMenu {
    private static final int[] ROUND_VALUES = {10, 20, 30, 0};
    private static final String[] ROUND_NAMES = {"10", "20", "30", "Infini"};
    private static final int[] Q_ROUNDS = {5, 10, 15, 20}, TURN_VALUES = {1, 2, 3, 5}, DUR_VALUES = {45, 60, 90, 120};
    /** Card index of the shared queue (after the seven games). */
    private static final int SHARE_CARD = 7;

    private final MenuEnv env;
    private final Prefs prefs;
    private final NowPlayingView view;
    private final MenuOverlay menu;
    private final Favorites favorites;
    private final GameView gameView;
    private final PartyView partyView;
    private final ShareView shareView;
    private Game game;
    private QueueServer queueServer;
    /** Selected card: -1 = the grid, 0-3 music games, 4-6 party games. */
    private int gameSel = -1;
    /** True while the section shows the playlist picker of the music games. */
    private boolean gamePick;

    public GamesMenu(MenuEnv env, GameView gameView, PartyView partyView, ShareView shareView) {
        this.env = env;
        prefs = env.prefs();
        view = env.view();
        menu = env.menu();
        favorites = new Favorites(env);
        this.gameView = gameView;
        this.partyView = partyView;
        this.shareView = shareView;
    }

    /** True while the grid is shown. */
    public boolean grid() { return gameSel < 0; }

    public void pick(int index) {
        if (index == 7) { openShare(); return; }
        gameSel = index;
        if (index < 4) prefs.setGameInt("mode", index);
        menu.ambChanged();
    }
    public List<MenuOverlay.Card> cards() {
        List<MenuOverlay.Card> out = new ArrayList<MenuOverlay.Card>();
        String[] names = {"Blind test", T.t("Chaise musicale"), T.t("N'oubliez pas les paroles"), T.t("Pochette pixellisée"), "Quiz", T.t("Devine-moi !"), T.t("Dessine-moi !")};
        String[] subs = {T.t("Devinez le morceau"), T.t("Dansez, stop !"), T.t("Finissez la phrase"), T.t("Reconnaissez la pochette"), T.t("Culture générale"), T.t("Faites deviner sans le dire"), T.t("Dessinez, les autres devinent")};
        int[][] cols = {{0xFF8A3FD8, 0xFF2A0F4A}, {0xFFE8674A, 0xFF6A1E12}, {0xFF2B9FD0, 0xFF0F3050}, {0xFF3FC88A, 0xFF0C4028},
                {0xFFF0BE3A, 0xFF7A4A08}, {0xFFE8524F, 0xFF5A0E1E}, {0xFF4A9AF0, 0xFF0F3070}};
        for (int i = 0; i < names.length; i++) if (i != Game.PIXEL) out.add(new MenuOverlay.Card(T.t(names[i]), T.t(subs[i]), cols[i][0], cols[i][1], i));
        SpotifyClient sp = env.poller() != null ? env.poller().spotify : null;
        if (sp != null && sp.connected() && prefs.spotifyOn())
            out.add(new MenuOverlay.Card(T.t("File d'attente partagée"), T.t("Chacun ajoute un morceau"), 0xFF1DB954, 0xFF0B4A22, 7));
        return out;
    }
    public List<MenuOverlay.Row> rows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        if (gamePick) {
            out.add(MenuOverlay.Row.text(T.t("Choisis la playlist du jeu parmi tes favoris et playlists Sonos.")));
            if (favorites.list() == null) out.add(MenuOverlay.Row.text(T.t("Chargement des favoris…")));
            else if (favorites.error() != null) out.add(MenuOverlay.Row.text(T.t("Impossible de lire les favoris : ") + favorites.error()));
            else if (favorites.list().isEmpty()) out.add(MenuOverlay.Row.text(T.t("Aucun favori pour l'instant. Dans l'appli Sonos : ⋯ sur une playlist › « Ajouter à Mes favoris Sonos ».")));
            else for (int i = 0; i < favorites.list().size(); i++) {
                SonosClient.Item f = favorites.list().get(i);
                out.add(MenuOverlay.Row.check("gfav:" + i, favorites.text(f), Ambiance.keyOf(f).equals(prefs.gameFav())));
            }
            return out;
        }
        if (gameSel >= 4) {
            out.add(MenuOverlay.Row.button("pstart", T.t("▶  LANCER LA PARTIE")));
            out.add(MenuOverlay.Row.chips("pteams", T.t("Équipes"), new String[]{"2", "3", "4", "5", "6"}, Math.max(0, Math.min(4, prefs.gameInt("pteams", 3) - 2))));
            if (gameSel == 4) {
                out.add(MenuOverlay.Row.text(T.t("Catégories (rien de coché = toutes)")));
                String[] cn = {T.t("Culture générale"), T.t("France"), T.t("Québec"), T.t("Geek"), T.t("Pop")};
                int m = prefs.gameInt("pm", 0);
                for (int i = 0; i < 5; i++) out.add(MenuOverlay.Row.check("pcat:" + i, cn[i], (m & (1 << i)) != 0));
            }
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
        out.add(MenuOverlay.Row.chips("grounds", T.t("Manches"), ROUND_NAMES, Choices.indexOf(ROUND_VALUES, prefs.gameInt("rounds", 20))));
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
    public void activate(String id) {
        if ("gpick".equals(id)) {
            gamePick = true;
            favorites.load();
            menu.refresh();
        } else if (id.startsWith("gfav:") && favorites.list() != null) {
            int i = Integer.parseInt(id.substring(5));
            if (i < favorites.list().size()) { prefs.gameFav(Ambiance.keyOf(favorites.list().get(i))); gamePick = false; menu.refresh(); }
        } else if ("pstart".equals(id)) {
            menu.hide();
            int gs = gameSel;
            gameSel = -1;
            partyView.setWifi(prefs.wifiQr(), prefs.wifiSsid());
            partyView.start(gs - 4, prefs.gameInt("pteams", 3), gs == 4 ? Q_ROUNDS[Math.max(0, Math.min(3, prefs.gameInt("pq", 1)))] : TURN_VALUES[Math.max(0, Math.min(3, prefs.gameInt("pt", 1)))],
                    DUR_VALUES[Math.max(0, Math.min(3, prefs.gameInt("pd", 1)))], prefs.gameInt("pm", 0));
        } else if (id.startsWith("pcat:")) {
            prefs.setGameInt("pm", prefs.gameInt("pm", 0) ^ (1 << Integer.parseInt(id.substring(5))));
            menu.refresh();
        } else if ("glaunch".equals(id)) {
            launchGame();
        }
    }
    private void launchGame() {
        if (env.poller() == null) return;
        if (prefs.gameFav().length() == 0) {
            view.showToast(T.t("Choisis d'abord la playlist du jeu"), 3000);
            gamePick = true;
            favorites.load();
            menu.refresh();
            return;
        }
        Ambiance a = new Ambiance();
        a.name = T.t("Jeu");
        a.fav = prefs.gameFav();
        a.shuffle = true;
        a.volume = -1;
        game = new Game(env.poller(), new Game.Host() {
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
        gameSel = -1;
        game.start(a);
        gameView.invalidate();
    }
    /** Starts the shared-queue page if needed; false when the port could not be opened. */
    private boolean startQueue() {
        if (queueServer != null && queueServer.isRunning()) return true;
        queueServer = new QueueServer(env.poller().spotify, prefs.queueToken(), new QueueServer.Listener() {
            @Override public boolean enqueue(String uri, String title) throws Exception { return env.poller().enqueueSonos(uri, title); }
            @Override public void added(final String title, final String artist) {
                env.activity().runOnUiThread(new Runnable() { @Override public void run() { view.showToast(T.t("Ajouté : ") + title, 4000); } });
            }
        });
        return queueServer.start();
    }

    /** Keeps the shared-queue page up while Pochette is open, so a link saved by a guest keeps working. */
    public void keepQueue() {
        if (env.poller() != null && prefs.queueKeep()) startQueue();
    }

    /** New secret: links saved by guests stop working. */
    public void renewQueueLink() {
        prefs.renewQueueToken();
        if (queueServer != null) queueServer.stop();
        queueServer = null;
        keepQueue();
    }

    private void openShare() {
        if (env.poller() == null) return;
        if (!startQueue()) { view.showToast(T.t("Impossible de démarrer la page pour téléphone"), 3000); return; }
        menu.hide();
        shareView.open(queueServer, prefs.wifiQr(), prefs.wifiSsid());
    }

    // ---- running game

    /** True while a music game is on screen. */
    public boolean gameActive() { return game != null && game.active; }

    public void gameKey(int code) { gameView.handleKey(code); }

    public void onSnapshot(Poller.Snapshot s) { if (gameActive()) game.onSnapshot(s); }

    /** Stops what runs in the background (game, shared-queue page); used when the app stops. */
    public void shutdown() {
        if (gameActive()) { game.abort(); gameView.setVisibility(View.GONE); }
        if (queueServer != null) queueServer.stop();
    }

    // ---- menu events

    /** Option of a row changed. */
    public void choice(String id, int index) {
        if ("pteams".equals(id)) prefs.setGameInt("pteams", index + 2);
        else if ("prounds".equals(id)) prefs.setGameInt(gameSel == 4 ? "pq" : "pt", index);
        else if ("pdur".equals(id)) prefs.setGameInt("pd", index);
        else if ("gmode".equals(id)) { prefs.setGameInt("mode", index); menu.refresh(); }
        else if ("gteams".equals(id)) prefs.setGameInt("teams", index + 2);
        else if ("grounds".equals(id)) prefs.setGameInt("rounds", ROUND_VALUES[index]);
        else if ("gstart".equals(id)) prefs.setGameInt("start", index);
        else if ("gdiff".equals(id)) prefs.setGameInt("diff", index);
    }

    /** Back inside the section: picker or options to the grid. False when already on the grid. */
    public boolean back() {
        if (gamePick) { gamePick = false; return true; }
        if (gameSel >= 0) { gameSel = -1; return true; }
        return false;
    }
}
