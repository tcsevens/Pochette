# Privacy Policy

**Pochette** is a free, open-source Android TV app that shows what your Sonos speakers are playing.
Last updated: November 21, 2026.

**Short version:** Pochette has no account, no analytics, no advertising and no server of its own.
The developer does not collect, receive or store any data about you.

## What the app does with your data

### On your local network (never leaves your home)
- Pochette talks directly to your Sonos speakers on your local network to read what is playing and to control playback.
- When you use the party games, the shared queue or the setup pages, the app runs small web servers on your TV so that
  phones on the same Wi-Fi network can connect (by QR code). This traffic stays on your local network.
- Settings, your favourites, your game scores and the videos you add are stored only on your TV, in the app's private storage.
- A small error log (at most 256 KB) is kept in the app's private storage to help diagnose crashes. It is never sent anywhere on its own. Settings › Advanced › "Log and report a bug…" can show a redacted copy of it on your phone (IP addresses, Sonos household and speaker IDs and serial numbers are masked); it leaves your devices only if you copy it or press "Send by e-mail" yourself, which opens your e-mail app addressed to pochette.support@gmail.com.

### Requests sent to the Internet
Only these, and only when the related feature is used:

| Service | Why | What is sent |
|---|---|---|
| [LRCLIB](https://lrclib.net) | Synced lyrics (can be turned off in the menu) | The title, artist, album and duration of the current track |
| [Wikipedia](https://www.wikipedia.org) | Artist biography on the info page | The artist name, as a search query |
| [Deezer](https://developers.deezer.com) and [iTunes Search](https://performance-partners.apple.com/search-api) | Cover of the next track, only when your speaker plays through Spotify Connect and gives no picture | The artist name and track title, as a search query (iTunes only if Deezer finds nothing) |
| [Spotify](https://developer.spotify.com) (optional) | "Up next" with Spotify Connect and the shared queue | Requests made with **your own** Spotify developer app and your own Spotify account; see below |

These services have their own privacy policies and receive your IP address like any website would.
The developer of Pochette does not operate them and does not receive this data.

### Spotify (optional)
If you choose to connect Spotify, you create your own app in the Spotify developer dashboard and sign in with your account.
The access token and your Client ID are stored only on your TV. Pochette uses them to read your playback queue and, when a guest
adds a song, to search Spotify and add it to your queue. You can disconnect at any time in the app's settings, and you can
revoke access from your Spotify account page.

## What the app does not do
- It does not use analytics, crash-reporting services, advertising or trackers.
- It does not access your contacts, camera, microphone, location or files.
- It does not sell or share your data. There is no data to sell: the developer receives none.

## Permissions
- **Internet and network state**: to reach your speakers, LRCLIB, Wikipedia, Deezer, iTunes Search and (optionally) Spotify.
- **Notifications and foreground service**: to keep reading what your speaker is playing while the app is running.
- **Start at boot**: optional, to open the app when the TV starts.
- **Display over other apps**: optional, used only to bring the screen back when music starts. The app works without it.
- The app does not change any system setting. If the TV's system click sounds are on, it only shows a tip explaining how to turn them off.

## Children
Pochette is not directed at children and does not knowingly collect any personal information.

## Changes
If this policy changes, the new version will be published in this repository with an updated date.

## Contact
Open an issue at <https://github.com/tcsevens/Pochette/issues> or write to pochette.support@gmail.com.

*Sonos is a trademark of Sonos, Inc. and Spotify is a trademark of Spotify AB. Pochette is an independent project, not affiliated with or endorsed by either company.*
