package app.pochette.tv.menu;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.ui.MenuOverlay;
import app.pochette.tv.ui.NowPlayingView;

import java.util.ArrayList;
import java.util.List;

/** The Ambiances section: the cards, the editor of one ambiance and the playlist picker. */
public final class AmbianceMenu {
    private static final String[] VOL_NAMES = {"Inchangé", "10 %", "15 %", "20 %", "25 %", "30 %", "35 %", "40 %", "50 %"};
    private static final String[] DISP_NAMES = {"Inchangé", "Classique", "Illustré", "Paroles", "Fond d'écran"};
    private static final int[] VOL_VALUES = {-1, 10, 15, 20, 25, 30, 35, 40, 50};
    private static final String[] SLEEP_NAMES = {"Non", "30 min", "1 h", "2 h"};
    private static final int[] SLEEP_VALUES = {0, 30, 60, 120};

    private final MenuEnv env;
    private final Prefs prefs;
    private final NowPlayingView view;
    private final MenuOverlay menu;
    private final Favorites favorites;
    private final List<Ambiance> ambs;
    /** Index of the ambiance being edited, or -1. */
    private int ambEdit = -1;
    /** True while the editor shows the playlist picker. */
    private boolean ambPick;

    public AmbianceMenu(MenuEnv env) {
        this.env = env;
        prefs = env.prefs();
        view = env.view();
        menu = env.menu();
        favorites = new Favorites(env);
        ambs = prefs.ambiances();
    }

    /** The live list of ambiances (shared with the home screen tiles). */
    public List<Ambiance> list() { return ambs; }

    /** True while the section shows an editor instead of the cards. */
    public boolean editing() { return ambEdit >= 0; }

    private String roomsLabel(Ambiance a) {
        if (a.rooms.isEmpty()) return T.t("Pièces en cours");
        StringBuilder sb = new StringBuilder();
        for (SonosClient.Room r : env.rooms()) {
            if (a.rooms.contains(r.uuid)) { if (sb.length() > 0) sb.append(" + "); sb.append(r.name); }
        }
        return sb.length() == 0 ? a.rooms.size() + T.t(" pièce(s)") : sb.toString();
    }
    public List<MenuOverlay.Card> cards() {
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
    public void cardLaunch(int index) {
        if (index >= ambs.size()) { cardEdit(index); menu.ambChanged(); return; }
        Ambiance a = ambs.get(index);
        if (a.fav.length() == 0) {
            ambEdit = index;
            ambPick = true;
            favorites.load();
            menu.say("", T.t("Choisis d'abord la playlist de « ") + a.name + T.t(" »"), 3000);
            menu.ambChanged();
            return;
        }
        launch(a);
    }
    public void cardEdit(int index) {
        if (index >= ambs.size()) {
            ambs.add(new Ambiance());
            ambEdit = ambs.size() - 1;
            ambPick = true;
            favorites.load();
        } else {
            ambEdit = index;
            ambPick = false;
        }
    }
    /** Starts the ambiance: its playlist, rooms, volume and screen. */
    public void launch(final Ambiance a) {
        if (env.poller() == null) return;
        view.showToast(T.t("Ambiance ") + T.t(a.name) + "…", 4000);
        menu.hide();
        env.poller().launchAmbiance(a, new Poller.Done() {
            @Override public void done(String error) {
                if (error != null) { view.showToast(T.t("Impossible de lancer « ") + a.name + T.t(" » : ") + error, 6000); return; }
                view.setHome(false);
                prefs.theme(a.theme, a.name);
                view.setTheme(a.theme, a.name);
                if (a.display >= 0) {
                    view.setMode(a.display); prefs.mode(a.display);
                    if (a.display == NowPlayingView.MODE_ILLUSTRATED && a.theme == Ambiance.T_SOUL) { view.setVinylStyle(2); prefs.vinylStyle(2); }
                }
                view.showToast(T.t("Ambiance ") + T.t(a.name), 2500);
            }
        });
    }
    public List<MenuOverlay.Row> rows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        if (ambEdit < 0 || ambEdit >= ambs.size()) return out;
        Ambiance a = ambs.get(ambEdit);
        if (ambPick) {
            out.add(MenuOverlay.Row.text(T.t("Choisis la musique de « ") + T.t(a.name) + T.t(" » parmi tes favoris et playlists Sonos.")));
            if (favorites.list() == null) out.add(MenuOverlay.Row.text(T.t("Chargement des favoris…")));
            else if (favorites.error() != null) out.add(MenuOverlay.Row.text(T.t("Impossible de lire les favoris : ") + favorites.error()));
            else if (favorites.list().isEmpty()) out.add(MenuOverlay.Row.text(T.t("Aucun favori pour l'instant. Dans l'appli Sonos, ouvre une playlist, puis ⋯ › « Ajouter à Mes favoris Sonos ». Elle apparaîtra ici.")));
            else {
                for (int i = 0; i < favorites.list().size(); i++) {
                    SonosClient.Item f = favorites.list().get(i);
                    out.add(MenuOverlay.Row.check("fav:" + i, favorites.text(f), Ambiance.keyOf(f).equals(a.fav)));
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
        for (SonosClient.Room r : env.rooms()) out.add(MenuOverlay.Row.check("aroom:" + r.uuid, r.name, a.rooms.contains(r.uuid)));
        out.add(MenuOverlay.Row.chips("avol", a.ramp ? T.t("Volume à atteindre") : T.t("Volume de départ"), VOL_NAMES, Choices.indexOf(VOL_VALUES, a.volume)));
        out.add(MenuOverlay.Row.toggle("aramp", T.t("Volume qui monte doucement (3 min)"), a.ramp));
        out.add(MenuOverlay.Row.chips("atheme", T.t("Écran"), Ambiance.shownNames(), Ambiance.toIndex(a.theme)));
        out.add(MenuOverlay.Row.chips("adisp", T.t("Affichage"), a.theme == Ambiance.T_NORMAL ? java.util.Arrays.copyOf(DISP_NAMES, DISP_NAMES.length - 1) : DISP_NAMES, Math.min(a.display + 1, a.theme == Ambiance.T_NORMAL ? DISP_NAMES.length - 2 : DISP_NAMES.length - 1)));
        out.add(MenuOverlay.Row.chips("asleep", T.t("Arrêt automatique"), SLEEP_NAMES, Choices.indexOf(SLEEP_VALUES, a.sleepMin)));
        if (!a.preset) out.add(MenuOverlay.Row.action("adelete", T.t("Supprimer cette ambiance")));
        return out;
    }
    /** Handles the rows of the Ambiances section; true when the id was one of them. */
    public boolean activate(String id) {
        if (ambEdit < 0 || ambEdit >= ambs.size()) return false;
        Ambiance a = ambs.get(ambEdit);
        if ("alaunch".equals(id)) {
            if (a.fav.length() == 0) { ambPick = true; favorites.load(); menu.ambChanged(); }
            else { ambEdit = -1; launch(a); }
        } else if ("apick".equals(id)) {
            ambPick = true; favorites.load(); menu.ambChanged();
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
        } else if (id.startsWith("fav:") && favorites.list() != null) {
            int i = Integer.parseInt(id.substring(4));
            if (i < favorites.list().size()) {
                a.fav = Ambiance.keyOf(favorites.list().get(i));
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

    /** Option of an editor row changed. */
    public void choice(String id, int index) {
        if (ambEdit < 0 || ambEdit >= ambs.size()) return;
        Ambiance a = ambs.get(ambEdit);
        if ("avol".equals(id)) a.volume = VOL_VALUES[index];
        else if ("atheme".equals(id)) {
            index = Ambiance.fromIndex(index);
            a.theme = index;
            if (index == Ambiance.T_NORMAL && a.display == NowPlayingView.MODE_BARE) a.display = -1;      // no wallpaper, nothing to show alone
            if (index == Ambiance.T_PARTY && a.volume < 0) {          // a party starts with some energy: louder, shuffled, no sleep timer
                a.volume = 40; a.shuffle = true; a.sleepMin = 0;
                menu.ambChanged();
            }
        }
        else if ("adisp".equals(id)) a.display = index - 1;
        else if ("asleep".equals(id)) a.sleepMin = SLEEP_VALUES[index];
        prefs.ambiances(ambs);
    }

    /** Back inside the section: picker to editor, editor to cards. False when already on the cards. */
    public boolean back() {
        if (ambEdit < 0) return false;
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
}
