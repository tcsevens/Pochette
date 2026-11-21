package app.pochette.tv.menu;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.CustomBg;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.spotify.SetupServer;
import app.pochette.tv.spotify.SpotifyClient;
import app.pochette.tv.spotify.SpotifySetupView;
import app.pochette.tv.party.BgSetupServer;
import app.pochette.tv.party.WifiSetupServer;
import app.pochette.tv.ui.MenuOverlay;
import app.pochette.tv.ui.ShareView;
import app.pochette.tv.ui.NowPlayingView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/** The Display, Settings and About sections of the menu. */
public final class SettingsMenu {
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private static final String[] LYR_OFF_NAMES = {"Plus tôt", "Un peu plus tôt", "Normal", "Un peu plus tard", "Plus tard"};
    private static final int[] LYR_OFF_MS = {-800, -400, 0, 400, 800};

    private final MenuEnv env;
    private final Activity activity;
    private final Prefs prefs;
    private final NowPlayingView view;
    private final MenuOverlay menu;
    private final SpotifySetupView spotifyView;
    private SetupServer setupServer;
    /** Settings page shown: 0 main, 1 Spotify, 2 advanced. */
    private int setSub;
    private final ShareView shareView;
    private WifiSetupServer wifiServer;
    private BgSetupServer bgServer;

    public SettingsMenu(MenuEnv env, SpotifySetupView spotifyView, ShareView shareView) {
        this.shareView = shareView;
        this.env = env;
        activity = env.activity();
        prefs = env.prefs();
        view = env.view();
        menu = env.menu();
        this.spotifyView = spotifyView;
    }

    /** The Settings section opens on its main page. */
    public void open() { setSub = 0; }

    /** Back inside Settings: sub-page to main page. False when already on the main page. */
    public boolean back() {
        if (setSub == 0) return false;
        setSub = 0;
        return true;
    }

    /** Stops the Spotify sign-in page server; used when the app stops. */
    public void shutdown() {
        if (setupServer != null) setupServer.stop();
        if (wifiServer != null) wifiServer.stop();
    }

    public List<MenuOverlay.Row> rows(int section) {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        switch (section) {
            case MenuOverlay.SEC_DISPLAY:
                out.add(MenuOverlay.Row.head(T.t("Musique")));
                out.add(MenuOverlay.Row.cards("style", T.t("Style"), java.util.Arrays.copyOf(NowPlayingView.MODE_NAMES, view.modeCount()), view.getMode()));
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
                    out.add(MenuOverlay.Row.action("sounds", T.t("Sons système de la télé") + (systemSoundsOn() ? "  ⚠" : "  ✓")));
                    String ip2 = prefs.manualIp().length() > 0 ? prefs.manualIp() : "auto";
                    out.add(MenuOverlay.Row.action("ip", T.t("Adresse IP de l'enceinte : ") + ip2));
                    out.add(MenuOverlay.Row.head(T.t("Invités")));
                    out.add(MenuOverlay.Row.toggle("qkeep", T.t("File d'attente toujours active (lien permanent)"), prefs.queueKeep()));
                    out.add(MenuOverlay.Row.action("qrenew", T.t("Renouveler le lien d'invitation")));
                    out.add(MenuOverlay.Row.action("wifi", T.t("Wi-Fi des invités : ") + (prefs.wifiSsid().length() > 0 ? prefs.wifiSsid() : T.t("non configuré"))));
                    if (prefs.wifiSsid().length() > 0) {
                        out.add(MenuOverlay.Row.toggle("wifioffer", T.t("Proposer le QR Wi-Fi aux invités"), prefs.wifiOffer()));
                        out.add(MenuOverlay.Row.action("wififorget", T.t("Oublier le Wi-Fi des invités")));
                    }
                    out.add(MenuOverlay.Row.head(T.t("Mes fonds d'écran")));
                    out.add(MenuOverlay.Row.action("bgadd", T.t("Ajouter une vidéo depuis le téléphone…")));
                    for (int i = 0; i < CustomBg.SLOTS; i++)
                        if (CustomBg.has(i)) out.add(MenuOverlay.Row.action("bgdel:" + i, T.t("Supprimer") + " « " + T.t(Ambiance.name(CustomBg.themeOf(i))) + " »"));
                    out.add(MenuOverlay.Row.head(T.t("Dépannage")));
                    out.add(MenuOverlay.Row.action("logview", T.t("Voir le journal…")));
                    out.add(MenuOverlay.Row.action("logclear", T.t("Effacer le journal")));
                    break;
                }
                List<SonosClient.Room> rs = env.rooms();
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
                out.add(MenuOverlay.Row.slider("opacity", T.t("Transparence du menu"), prefs.menuTransparency(), 10));
                out.add(MenuOverlay.Row.head(T.t("Plus")));
                out.add(MenuOverlay.Row.action("sub1", T.t("Spotify…")));
                out.add(MenuOverlay.Row.action("sub2", T.t("Avancé…")));
                break;
            }
            case MenuOverlay.SEC_HELP:
                out.add(MenuOverlay.Row.text("Pochette " + versionName()));
                out.add(MenuOverlay.Row.text(T.t("Un écran « en cours de lecture » pour tes enceintes Sonos, avec des ambiances et des jeux. Projet indépendant, non affilié à Sonos ni à Spotify.")));
                out.add(MenuOverlay.Row.text(T.t("Vie privée : Pochette parle à tes enceintes sur ton réseau local, sans compte ni cloud. Seules les paroles (lrclib.net) et les résumés (Wikipédia, CC BY-SA) sont cherchés en ligne, et Spotify si tu l'actives.")));
                out.add(MenuOverlay.Row.text(T.t("Vidéos d'ambiance sur Pixabay, merci à leurs auteurs : Monk_Vadim, JusticeTran, LofiPlace, Joe_hackney, victorsteep et BackgroundsCHAIR.")));
                out.add(MenuOverlay.Row.text(T.t("Questions du quiz « Pop & geek » : Open Trivia DB (opentdb.com), licence CC BY-SA 4.0, sélectionnées et traduites.")));
                out.add(MenuOverlay.Row.text(T.t("Sonos et Spotify sont des marques de leurs propriétaires.")));
                out.add(MenuOverlay.Row.text(T.t("Développé avec l'IA Claude.")));
                break;
        }
        return out;
    }

    /** Option of a row changed. */
    public void choice(int section, MenuOverlay.Row row, int index) {
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
        else if ("lang".equals(id)) { prefs.lang(index); T.set(index); activity.recreate(); }
        else if ("strip".equals(id)) { int v = index == 0 ? 0 : 10; prefs.stripSec(v); menu.setAutoHide(v); }
        else if ("opacity".equals(id)) { prefs.menuTransparency(index); menu.setTransparency(index); }
        else if ("lyroff".equals(id)) { prefs.lyricsOffset(LYR_OFF_MS[index]); view.setLyricsOffset(LYR_OFF_MS[index]); }
        else if ("side".equals(id)) { prefs.menuRight(index == 1); menu.setRight(index == 1); }
        else if ("follow".equals(id)) {
            List<SonosClient.Room> rs = env.rooms();
            prefs.roomUuid(index == 0 || index - 1 >= rs.size() ? "" : rs.get(index - 1).uuid);
            if (env.poller() != null) env.poller().poke();
        }
    }

    /** A row was activated. */
    public void activate(MenuOverlay.Row row) {
        String id = row.id;
        if (id == null) return;
        if ("resume".equals(id)) {
            prefs.resumeOnOpen(!prefs.resumeOnOpen());
        } else if ("sounds".equals(id)) {
            toggleSystemSounds();
        } else if ("spon".equals(id)) {
            prefs.spotifyOn(!prefs.spotifyOn());
        } else if ("sub1".equals(id) || "sub2".equals(id)) {
            setSub = "sub1".equals(id) ? 1 : 2;
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
                env.setAutoLaunch(true);
                if (needsOverlayPermission()) menu.say("", T.t("Il manque une autorisation : voir « Autoriser l'affichage automatique »"), 4000);
            } else {
                env.setAutoLaunch(false);
            }
        } else if ("perm".equals(id)) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName())));
            } catch (Exception ex) {
                menu.say("", T.t("Écran absent sur cette télé : utilise la commande adb du guide"), 5000);
            }
        } else if ("ip".equals(id)) {
            askIp();
        } else if ("qkeep".equals(id)) {
            prefs.queueKeep(!prefs.queueKeep());
            env.games().keepQueue();
        } else if ("qrenew".equals(id)) {
            env.games().renewQueueLink();
            menu.say("", T.t("Nouveau lien : les anciens ne marchent plus."), 3000);
        } else if ("wifi".equals(id)) {
            startWifiSetup();
        } else if ("bgadd".equals(id)) {
            if (bgServer == null) bgServer = new BgSetupServer();
            if (!bgServer.start()) { menu.say("", T.t("Impossible de démarrer la page pour téléphone"), 3000); return; }
            menu.hide();
            shareView.openBg(bgServer, false);
        } else if ("logview".equals(id)) {
            if (bgServer == null) bgServer = new BgSetupServer();
            if (!bgServer.start()) { menu.say("", T.t("Impossible de démarrer la page pour téléphone"), 3000); return; }
            menu.hide();
            shareView.openBg(bgServer, true);
        } else if ("logclear".equals(id)) {
            app.pochette.tv.core.Journal.clear();
            menu.say("", T.t("Journal effacé."), 2500);
        } else if (id.startsWith("bgdel:")) {
            int slot = Integer.parseInt(id.substring(6));
            if (view.getTheme() == CustomBg.themeOf(slot)) { prefs.theme(0, ""); view.setTheme(0, ""); }
            CustomBg.delete(slot);
            app.pochette.tv.theme.ThemePreview.forget(CustomBg.themeOf(slot));
            menu.refresh();
        } else if ("wifioffer".equals(id)) {
            prefs.wifiOffer(!prefs.wifiOffer());
        } else if ("wififorget".equals(id)) {
            prefs.wifi("", "");
            menu.refresh();
        } else if ("rescan".equals(id)) {
            prefs.lastIp("");
            if (env.poller() != null) env.poller().reset();
            menu.say("", T.t("Recherche des enceintes…"), 2500);
        }
    }

    public boolean systemSoundsOn() {
        try {
            return Settings.System.getInt(activity.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
        } catch (Exception e) {
            return false;
        }
    }
    /** Pochette cannot (and no longer asks to) change system settings: it only explains how to turn the sounds off. */
    private void toggleSystemSounds() {
        if (!systemSoundsOn()) { menu.say("", T.t("Sons système désactivés"), 2500); return; }
        menu.say(T.t("Sons système de la télé activés"), soundsHelp(), 15000);
    }
    public String soundsHelp() {
        return T.t("Si ta Sonos est une barre de son branchée sur la télé, chaque son de navigation peut couper la musique. ")
                + T.t("Pour une utilisation optimale, désactive-les : ")
                + T.t("Paramètres de la télé › Écran et son › Sons système. ");
    }
    private int lyrOffIndex() {
        int v = prefs.lyricsOffset();
        for (int i = 0; i < LYR_OFF_MS.length; i++) if (LYR_OFF_MS[i] == v) return i;
        return 2;
    }
    private boolean needsOverlayPermission() {
        return Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(activity);
    }
    private String versionName() {
        try { return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }
    private List<MenuOverlay.Row> spotifyRows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        SpotifyClient sp = env.poller() != null ? env.poller().spotify : new SpotifyClient(prefs);
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
    private void startSpotifySetup() {
        if (env.poller() == null) return;
        if (setupServer == null) {
            setupServer = new SetupServer(env.poller().spotify, prefs, new SetupServer.Listener() {
                @Override public void changed() {
                    activity.runOnUiThread(new Runnable() { @Override public void run() { menu.refresh(); } });
                }
            });
        }
        menu.hide();
        spotifyView.open(setupServer, env.poller().spotify, !env.poller().spotify.configured());
    }
    private void startWifiSetup() {
        if (wifiServer == null) wifiServer = new WifiSetupServer(prefs);
        if (!wifiServer.start()) { menu.say("", T.t("Impossible de démarrer la page pour téléphone"), 3000); return; }
        menu.hide();
        shareView.openSetup(wifiServer);
    }
    private void askIp() {
        final EditText et = new EditText(activity);
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setHint("ex. 192.168.1.42 (vide = automatique)");
        et.setText(prefs.manualIp());
        new AlertDialog.Builder(activity, DIALOG_THEME)
                .setTitle(T.t("Adresse IP d'une enceinte Sonos"))
                .setMessage(T.t("Laisse vide pour la détection automatique. L'IP se trouve dans l'appli Sonos : Réglages › Système › À propos."))
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.manualIp(et.getText().toString().trim());
                        if (env.poller() != null) env.poller().reset();
                        menu.refresh();
                    }
                })
                .setNegativeButton(T.t("Annuler"), null)
                .show();
    }
}
