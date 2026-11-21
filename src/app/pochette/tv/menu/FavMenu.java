package app.pochette.tv.menu;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.ui.MenuOverlay;

import java.util.ArrayList;
import java.util.List;

/** The Favourites section: plays a Sonos favourite or playlist in the rooms already playing, without touching volume, rooms or ambiance. */
public final class FavMenu {
    private final MenuEnv env;
    private final Favorites favorites;

    public FavMenu(MenuEnv env) {
        this.env = env;
        favorites = new Favorites(env);
    }

    /** The section was opened: reload the list. */
    public void open() { favorites.load(); }

    public List<MenuOverlay.Row> rows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        out.add(MenuOverlay.Row.text(T.t("Lance une playlist ou un favori Sonos dans les pièces qui jouent déjà. Le volume, le fond d'écran et l'affichage ne changent pas.")));
        out.add(MenuOverlay.Row.toggle("fshuffle", T.t("Lecture aléatoire"), env.prefs().gameInt("favshuffle", 0) == 1));
        if (favorites.list() == null && favorites.error() == null) {
            out.add(MenuOverlay.Row.text(T.t("Chargement des favoris…")));
        } else if (favorites.error() != null) {
            out.add(MenuOverlay.Row.text(T.t("Impossible de lire les favoris : ") + favorites.error()));
        } else if (favorites.list().isEmpty()) {
            out.add(MenuOverlay.Row.text(T.t("Aucun favori pour l'instant. Dans l'appli Sonos : ⋯ sur une playlist › « Ajouter à Mes favoris Sonos ».")));
        } else {
            for (int i = 0; i < favorites.list().size(); i++) out.add(MenuOverlay.Row.action("f:" + i, favorites.text(favorites.list().get(i))));
        }
        return out;
    }

    public void activate(String id) {
        if ("fshuffle".equals(id)) {
            env.prefs().setGameInt("favshuffle", env.prefs().gameInt("favshuffle", 0) == 1 ? 0 : 1);
            env.menu().refresh();
        } else if (id.startsWith("f:") && favorites.list() != null && env.poller() != null) {
            int i = Integer.parseInt(id.substring(2));
            if (i >= favorites.list().size()) return;
            SonosClient.Item f = favorites.list().get(i);
            final String label = favorites.text(f);
            Ambiance a = new Ambiance();
            a.name = label;
            a.fav = Ambiance.keyOf(f);
            a.shuffle = env.prefs().gameInt("favshuffle", 0) == 1;
            a.volume = -1;
            env.view().showToast(T.t("Lecture : ") + label + "…", 4000);
            env.menu().hide();
            env.poller().launchAmbiance(a, new Poller.Done() {
                @Override public void done(String error) {
                    if (error != null) env.view().showToast(T.t("Impossible de lancer « ") + label + T.t(" » : ") + error, 6000);
                }
            });
        }
    }
}
