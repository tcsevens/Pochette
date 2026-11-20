[Version française](README.fr.md)

# Pochette — a "now playing" screen for Sonos on Android TV

Pochette fills your TV with what your Sonos is playing: cover art, title, artist, progress bar and optional synced lyrics.
It talks straight to your speakers on the local network. No account, no cloud. Plain Java, no Gradle, no AndroidX, Android 6+.
Tested on a Google TV Chromecast; interface in English, French and Spanish.

## Features
- **Four display styles**: classic, vinyl, lyrics, and wallpaper only. Up/Down switches style.
- **Synced lyrics** from [LRCLIB](https://lrclib.net) (optional, can be turned off).
- **Ambiances**: one OK starts a Sonos favourite (playlist), the rooms, the volume and a wallpaper (looping video or drawn scene), with an optional wake-up volume ramp and sleep timer. Back during playback opens the ambiance home.
- **Games with the TV as game master**: blind test, musical chairs, finish-the-lyrics, pixelated cover, quiz (general, France, Québec), guess-the-word and draw-and-guess. Phones join by QR code on the same Wi-Fi.
- **Shared queue** (optional, needs Spotify): guests add a song from their phone.
- **Up next**, rooms picker, screensaver mode, clock when idle, and a menu drawn by the app (no system click sounds).

## Install
Download `Pochette.apk` from the Releases page and install it on the TV.
- With a computer: enable Developer options and USB / wireless debugging on the TV, then `adb connect TV_IP` and `adb install Pochette.apk`.
- Without a computer: put the APK on the TV with an app such as *Send Files to TV* and open it (allow installs from that app).

The TV and the Sonos must be on the same network. If no speaker is found, enter a speaker's IP in Settings › More › Advanced.

## Remote control
| Key | Action |
|---|---|
| Up / Down (menu closed) | Change display style |
| Hold Left / Right | Seek in the track |
| OK, Left or Right | Show controls and menu |
| Back (during playback) | Ambiance home; Back again quits |

## Spotify (optional)
Create an app on the [Spotify developer dashboard](https://developer.spotify.com/dashboard), then Settings › More › Spotify and follow the phone page. Pochette uses your own Client ID; the Spotify Web API needs a Premium account. It enables Spotify Connect "up next" and the shared queue.

## Build
```
sudo apt install openjdk-17-jdk aapt zipalign apksigner dalvik-exchange android-sdk-platform-23
./build.sh
```
`build.sh` creates a signing key (`pochette.keystore`) on first run. Keep it: updates only install over an older build signed with the same key.
The ambiance videos in `res/raw/` are not under the MIT license: they come from Pixabay and keep their own license (see `NOTICE.md`). Without them the app still builds and the ambiances show a gradient.

## Layout of the code
Sources live in `src/app/pochette/tv/`:
- `core/` preferences, translations (`T`), track state, ambiance list, helpers
- `sonos/` `SonosClient` (local UPnP/SOAP) and `Poller` (state, queue, ambiances)
- `spotify/` Web API client and the phone-based setup flow
- `media/` lyrics (LRCLIB) and artist bio
- `ui/` `NowPlayingView` (rendering), `MenuOverlay`, drawing helpers (`DiscArt`, `RadioScene`, `Icons`, `Draw`), info/share views, video background
- `theme/` wallpapers: `Themes`, `Scenes*`, `ThemePreview`
- `party/` games, phone pages and the shared queue server
- `menu/` menu controllers (ambiances, games, rooms, settings, favorites)
- `MainActivity` wires everything and routes remote-control keys

## Not affiliated
Independent project, not affiliated with Sonos or Spotify. Sonos relies on an unofficial local API that may change. See `NOTICE.md`. Code under the MIT license (`LICENSE`), except the videos (see `NOTICE.md`). Built with the help of Claude (Anthropic).

## Credits
Ambiance videos from [Pixabay](https://pixabay.com), under the Pixabay Content License. Thanks to their authors: Monk_Vadim, JusticeTran, LofiPlace, Joe_hackney and BackgroundsCHAIR (links in `NOTICE.md`).
