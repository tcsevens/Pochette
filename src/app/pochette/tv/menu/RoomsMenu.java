package app.pochette.tv.menu;

import app.pochette.tv.core.T;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.ui.MenuOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Rooms section: tick the rooms that should play, then apply. */
public final class RoomsMenu {
    private final MenuEnv env;
    /** Rooms ticked in the menu, not applied yet. */
    private final Map<String, Boolean> pendingRooms = new HashMap<String, Boolean>();

    public RoomsMenu(MenuEnv env) { this.env = env; }

    /** The section opens: tick the rooms that play now. */
    public void open() {
        pendingRooms.clear();
        String coord = env.coordinatorUuid();
        for (SonosClient.Room r : env.rooms()) pendingRooms.put(r.uuid, coord != null && coord.equals(r.coordinatorUuid));
    }

    public List<MenuOverlay.Row> rows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        List<SonosClient.Room> rs = env.rooms();
        if (rs.isEmpty() || env.coordinatorUuid() == null) {
            out.add(MenuOverlay.Row.text(T.t("Aucune enceinte trouvée pour l'instant.")));
            return out;
        }
        out.add(MenuOverlay.Row.text(T.t("Coche les pièces où la musique doit jouer.")));
        for (SonosClient.Room r : rs) {
            Boolean on = pendingRooms.get(r.uuid);
            out.add(MenuOverlay.Row.check("room:" + r.uuid, r.name, on != null && on));
        }
        out.add(MenuOverlay.Row.action("apply", T.t("Appliquer")));
        return out;
    }

    public void activate(String id) {
        if (id.startsWith("room:")) {
            String uuid = id.substring(5);
            Boolean on = pendingRooms.get(uuid);
            pendingRooms.put(uuid, on == null || !on);
        } else if ("apply".equals(id)) {
            applyRooms();
        }
    }

    private void applyRooms() {
        final String coord = env.coordinatorUuid();
        if (coord == null || env.poller() == null) return;
        List<String> wanted = new ArrayList<String>();
        if (Boolean.TRUE.equals(pendingRooms.get(coord))) wanted.add(coord);
        for (SonosClient.Room r : env.rooms()) {
            if (!r.uuid.equals(coord) && Boolean.TRUE.equals(pendingRooms.get(r.uuid))) wanted.add(r.uuid);
        }
        if (wanted.isEmpty()) {
            env.view().showToast(T.t("Choisis au moins une pièce"));
            return;
        }
        env.view().showToast(T.t("Changement des pièces…"));
        env.poller().applyGroup(coord, wanted, new Poller.Done() {
            @Override public void done(String error) {
                env.view().showToast(error == null ? T.t("Pièces mises à jour") : T.t("Impossible : ") + error, 3000);
            }
        });
        env.menu().hide();
    }
}
