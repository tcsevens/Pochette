package app.pochette.tv.menu;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;

import java.util.List;

/** The Sonos favourites and playlists offered when picking music for an ambiance or a game. */
final class Favorites {
    private final MenuEnv env;
    private List<SonosClient.Item> list;
    private String error;

    Favorites(MenuEnv env) { this.env = env; }

    /** Null while loading. */
    List<SonosClient.Item> list() { return list; }
    String error() { return error; }

    /** Loads the favourites and refreshes the menu when they arrive. */
    void load() {
        list = null;
        error = null;
        if (env.poller() == null) return;
        env.poller().loadFavorites(new Poller.Favs() {
            @Override public void got(List<SonosClient.Item> l, String err) {
                list = l;
                error = err;
                env.menu().refresh();
            }
        });
    }

    /** One-line label of a favourite. */
    String text(SonosClient.Item f) {
        if (f.kind == 3) return Ambiance.label(Ambiance.NOW) + "  ·  " + f.desc;
        return f.desc.length() > 0 ? f.title + "  ·  " + f.desc : f.title;
    }
}
