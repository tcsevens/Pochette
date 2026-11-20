# Third-party content and trademarks

The MIT license covers the source code and the artwork drawn in code. It does not cover the items below.

- **Ambiance videos** (`res/raw/vid_*.mp4`): royalty-free clips from stock-video libraries, each under its own
  license. They are not included in this repository; the released APK embeds them. To build without them, leave
  `res/raw/` empty (the ambiances then show a plain gradient), or drop in your own clips with the same names:
  `vid_chalet`, `vid_coin_du_feu`, `vid_jazz`, `vid_pluie`, `vid_noel` (H.264, 1280x720, no audio, looping).
- **Lyrics** are fetched at run time from [LRCLIB](https://lrclib.net) and are the property of their rights holders.
  Nothing is stored or redistributed by this project; the feature can be turned off in the menu.
- **Biographies** shown on the info page come from Wikipedia and are available under CC BY-SA 4.0.
- **Spotify** (optional): you use your own Client ID from the Spotify developer dashboard. Spotify is a trademark of
  Spotify AB. This project is not affiliated with or endorsed by Spotify.
- **Sonos** is a trademark of Sonos, Inc. This project is not affiliated with or endorsed by Sonos. It uses the local
  UPnP interface that Sonos speakers expose on the home network, which is not an official or supported API.
