package app.pochette.tv.menu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.core.Weather;
import app.pochette.tv.ui.MenuOverlay;

import java.util.ArrayList;
import java.util.List;

/** The weather settings; they live inside the editor of the Météo ambiance. */
final class WeatherMenu {
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private final MenuEnv env;
    private final Prefs prefs;
    private final MenuOverlay menu;

    WeatherMenu(MenuEnv env) {
        this.env = env;
        prefs = env.prefs();
        menu = env.menu();
    }

    boolean owns(String id) { return id.startsWith("wx"); }

    List<MenuOverlay.Row> rows() {
        List<MenuOverlay.Row> out = new ArrayList<MenuOverlay.Row>();
        out.add(MenuOverlay.Row.text(T.t("Réglages de la météo (ils servent aussi à la carte « Afficher la météo » du menu Affichage).")));
        Weather.Data d = Weather.data();
        String where = prefs.weatherLat().length() > 0 ? prefs.weatherCity()
                : T.t("automatique") + (prefs.weatherAutoCity().length() > 0 ? " (" + prefs.weatherAutoCity() + ")" : "");
        out.add(MenuOverlay.Row.action("wxcity", T.t("Ville : ") + where));
        out.add(MenuOverlay.Row.chips("wxunits", T.t("Unité de température"), new String[]{T.t("Selon le pays"), "°C", "°F"}, prefs.weatherUnits()));
        out.add(MenuOverlay.Row.chips("wxrefresh", T.t("Mise à jour de la météo"), new String[]{"15 min", "30 min", "1 h", "3 h", "6 h"}, prefs.weatherRefresh()));
        if (d != null) {
            out.add(MenuOverlay.Row.text(Weather.temp(d.temp, Weather.fahrenheit(prefs)) + "  " + T.t(Weather.COND_NAMES[d.cond]) + (d.place.length() > 0 ? "  ·  " + d.place : "")
                    + "  ·  " + new java.text.SimpleDateFormat("HH:mm", T.locale()).format(new java.util.Date(d.at))));
        } else {
            out.add(MenuOverlay.Row.text(T.t("Pas encore de météo : elle se charge quand l'ambiance ou la carte est active.")));
        }
        out.add(MenuOverlay.Row.action("wxnow", T.t("Mettre à jour maintenant")));
        out.add(MenuOverlay.Row.action("wxdemo", Weather.demoActive() ? T.t("Arrêter la démo") : T.t("Voir tous les fonds en démo (1 minute)")));
        out.add(MenuOverlay.Row.text("Weather data by Open-Meteo.com (CC BY 4.0) : https://open-meteo.com/"));
        out.add(MenuOverlay.Row.text(T.t("Sans ville choisie, ta position approximative est trouvée par ton adresse IP. Seules les coordonnées de la ville sont envoyées à Open-Meteo, aucune permission de localisation n'est demandée.")));
        return out;
    }

    void choice(String id, int index) {
        if ("wxunits".equals(id)) { prefs.weatherUnits(index); env.weatherRefresh(true); }
        else if ("wxrefresh".equals(id)) { prefs.weatherRefresh(index); env.weatherRefresh(false); }
    }

    void activate(String id) {
        if ("wxcity".equals(id)) askCity();
        else if ("wxnow".equals(id)) env.weatherRefresh(true);
        else if ("wxdemo".equals(id)) env.weatherDemo(!Weather.demoActive());
    }

    private void askCity() {
        final android.app.Activity activity = env.activity();
        final EditText et = new EditText(activity);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint(T.t("ex. Québec"));
        et.setText(prefs.weatherCity());
        new AlertDialog.Builder(activity, DIALOG_THEME)
                .setTitle(T.t("Ville pour la météo"))
                .setMessage(T.t("Laisse vide pour utiliser ta position approximative (adresse IP)."))
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final String name = et.getText().toString().trim();
                        if (name.length() == 0) { prefs.weatherPlace("", "", ""); env.weatherRefresh(true); return; }
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final String[] g = Weather.geocode(name, T.locale().getLanguage());
                                activity.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (g == null) { menu.say("", T.t("Ville introuvable (ou pas d'internet)."), 3500); return; }
                                        prefs.weatherPlace(g[2], g[0], g[1]);
                                        env.weatherRefresh(true);
                                    }
                                });
                            }
                        }, "geocode").start();
                    }
                })
                .setNegativeButton(T.t("Annuler"), null)
                .show();
    }
}
