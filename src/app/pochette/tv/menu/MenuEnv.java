package app.pochette.tv.menu;

import android.app.Activity;

import app.pochette.tv.core.Prefs;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.ui.MenuOverlay;
import app.pochette.tv.ui.NowPlayingView;

import java.util.List;

/** What the menu sections need from the app; implemented by the main activity. */
public interface MenuEnv {
    Activity activity();
    Prefs prefs();
    NowPlayingView view();
    MenuOverlay menu();
    /** The running poller, or null while the app is stopped. */
    Poller poller();
    /** The rooms of the last snapshot (empty before the first one). */
    List<SonosClient.Room> rooms();
    /** UUID of the coordinator of the playing group, or null. */
    String coordinatorUuid();
    /** Starts or stops the background service that brings the app forward when music starts. */
    void setAutoLaunch(boolean on);
    /** The Games section controller (also owns the shared-queue page). */
    GamesMenu games();
}
