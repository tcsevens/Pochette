[English version](README.md)

# Pochette — écran « en cours de lecture » pour Sonos sur Android TV

Pochette affiche en plein écran sur ta télé ce que joue ta Sonos : pochette, titre, artiste, progression, et paroles synchronisées en option.
Elle parle directement aux enceintes sur ton réseau local, sans compte ni cloud. Seules les paroles sont cherchées en ligne, sur lrclib.net.

## Installer l'APK sur la télé

**Option simple, sans ordinateur :**
1. Sur la télé, installe **Send Files to TV** depuis le Play Store. Installe-la aussi sur ton téléphone.
2. Envoie `Pochette.apk` du téléphone vers la télé.
3. Sur la télé, ouvre le fichier reçu. Android demande d'autoriser l'installation d'applis inconnues pour Send Files to TV : accepte, puis installe.

**Option avec un ordinateur (adb) :**
1. Sur la télé : Paramètres › Système › À propos. Clique 7 fois sur « Build du système d'exploitation Android TV » pour activer le mode développeur.
2. Paramètres › Système › Options pour les développeurs : active **Débogage USB** (et **Débogage sans fil** sur Android 11 et plus).
3. Sur l'ordinateur : `adb connect IP_DE_LA_TELE` (sur Android 11+, utilise `adb pair` avec le code affiché), puis `adb install Pochette.apk`.

Pochette apparaît ensuite dans la rangée de tes applis.

## Utilisation (télécommande)

| Touche | Action |
|---|---|
| ▲ ▼ (menu fermé) | Changer d'affichage |
| Maintenir ◀ ▶ | Reculer / avancer dans le morceau |
| OK, ◀ ou ▶ | Affiche les commandes (⏮ ⏯ ⏭), la barre de progression et la barre de menu |
| Dans les commandes | ◀ ▶ choisir, OK valider ; ▲ va sur la barre de progression (maintenir ◀ ▶ pour se déplacer), ▲ encore ou ◀ depuis ⏮ va dans la barre de menu |
| Dans la barre | ▲ ▼ choisir une section, ▶ ou OK l'ouvrir |
| Dans une section | ▲ ▼ ◀ ▶ se déplacer, OK pour choisir l'option sous le curseur, Retour pour revenir |
| Touches lecture / suivant / précédent | Agissent directement |
| Retour (menu fermé) | Quitter |

Sections : **Lecture**, **Pièces** (choisir où joue la musique), **Affichage** (style, couleur du vinyle, paroles, horloge),
**Réglages** (enceinte suivie, affichage auto, interface, Spotify, avancé), **À propos**.
Le menu est dessiné par l'appli : il ne fait aucun son système.

Quand rien ne joue, ou quand la barre de son est sur le son de la TV, Pochette affiche une horloge discrète qui se déplace doucement (anti-marquage).
L'écran peut alors se mettre en veille normalement.

## Choisir les pièces

Maintiens OK, puis « Diffuser sur… » : coche les pièces qui doivent jouer la musique. Si tu décoches la pièce qui joue, la musique passe sur une des pièces cochées.

## Affichage automatique quand la musique démarre

Activé par défaut. Pour qu'Android laisse Pochette passer au premier plan toute seule, il faut l'autorisation de superposition :
- Réglages Pochette › « Afficher automatiquement… » › **Ouvrir les réglages**, puis autorise Pochette.
- Si cet écran n'existe pas sur ta télé (fréquent sur Google TV), lance cette commande une seule fois depuis un ordinateur :
  ```
  adb shell appops set app.pochette.tv SYSTEM_ALERT_WINDOW allow
  ```

Si tu quittes avec Retour pendant la musique, Pochette ne revient pas toute seule. Elle se réarme après 45 secondes sans musique.

## En économiseur d'écran (optionnel)

Pochette est aussi un économiseur d'écran : la musique s'affiche quand la télé passe en veille d'écran, et une horloge sinon.
Paramètres › Préférences de l'appareil › Économiseur d'écran › **Pochette**.
Si Google TV ne propose pas les économiseurs tiers :
```
adb shell settings put secure screensaver_components app.pochette.tv/.NowPlayingDream
```

## Play et reprise

Play relance la dernière musique écoutée (file d'attente, playlist ou radio), même si la barre de son est passée sur la TV.
Option dans Réglages : « Relancer la dernière musique à l'ouverture ». La musique lancée depuis l'appli Spotify (Spotify Connect) ne peut pas être reprise par Pochette.

## Sons système de la télé

Si les sons de navigation de la télé sont actifs, Pochette l'indique au lancement : avec une barre de son, chaque son peut couper la musique.
Menu › Réglages › Sons système permet de les couper (la télé demande une fois l'autorisation « modifier les paramètres système »).
Sinon : Paramètres › Écran et son › Sons système, ou `adb shell settings put system sound_effects_enabled 0`.

## Dépannage

- **« Aucune enceinte trouvée »** : la télé et la Sonos doivent être sur le même réseau. Sinon, entre l'IP d'une enceinte dans les réglages Pochette.
  L'IP se trouve dans l'appli Sonos : Réglages › Système › À propos de mon système.
- **Plusieurs pièces** : par défaut, Pochette suit le groupe qui joue. Tu peux fixer une pièce dans les réglages.
- **La musique se coupe quand j'appuie sur la télécommande** : certaines barres de son basculent sur l'entrée TV dès que la télé émet un son.
  Coupe les sons de navigation de la télé (Paramètres › Préférences de l'appareil › Son › Sons système).
- **Pas de pochette** : certaines sources (entrée ligne, certaines radios) n'en fournissent pas.

## Compiler soi-même

Le projet n'utilise ni Gradle ni AndroidX : Java pur et API Android du framework (min. Android 6).
Sur Ubuntu ou Debian :
```
sudo apt install openjdk-17-jdk aapt zipalign apksigner dalvik-exchange android-sdk-platform-23
./build.sh
```
`build.sh` crée une clé de signature `pochette.keystore` au premier lancement. Garde-la : sans elle, les mises à jour ne s'installent pas par-dessus l'ancienne version.
Les vidéos d'ambiance (`res/raw/`) ne sont pas sous licence MIT : elles viennent de Pixabay et gardent leur licence (voir `NOTICE.md`). Sans elles, l'app compile et les ambiances affichent un dégradé.

### Organisation du code
Sources dans `src/app/pochette/tv/`:
- `core/` préférences, traductions (`T`), état du morceau, liste des ambiances, utilitaires
- `sonos/` `SonosClient` (UPnP/SOAP local) et `Poller` (état, file d'attente, ambiances)
- `spotify/` client Web API et configuration depuis le téléphone
- `media/` paroles (LRCLIB) et bio de l'artiste
- `ui/` `NowPlayingView` (affichage), `MenuOverlay`, aides de dessin (`IdleScreen`, `DiscArt`, `RadioScene`, `Icons`, `Draw`), vues info/partage, fond vidéo
- `theme/` fonds d'écran : `Themes`, `Scenes*`, `ThemePreview`
- `party/` jeux, pages téléphone et serveur de file partagée
- `menu/` contrôleurs de menu (ambiances, jeux, pièces, réglages, favoris)
- `MainActivity` assemble le tout et route les touches de la télécommande

## v2.0 — Ambiances et À suivre
- **Ambiances** (1re section du menu) : un OK sur une carte lance la playlist (un *favori Sonos*, ajoutez vos playlists Spotify aux favoris dans l'app Sonos), les pièces, le volume et le thème d'écran. OK maintenu = modifier. Aucun son ajouté.
- **À suivre** : ▲ depuis les boutons de lecture, OK sur un titre pour y sauter. Fonctionne pour la lecture par file d'attente Sonos (pas Spotify Connect).


## Ambiances, jeux et invités
- **Ambiances** : cartes avec aperçu du fond d'écran. Retour pendant la lecture ouvre l'accueil des ambiances.
- **Jeux** : blind test, chaise musicale, n'oubliez pas les paroles, pochette pixellisée, quiz (culture générale, France, Québec), Devine-moi, Dessine-moi. Les téléphones se connectent par QR code, sur le même Wi-Fi.
- **File d'attente partagée** (avec Spotify) : les invités ajoutent un morceau depuis leur téléphone.

## Licence
Code sous licence MIT (voir `LICENSE`), sauf les vidéos (voir `NOTICE.md`). Contenus tiers et marques : voir `NOTICE.md`.

## Crédits
Vidéos d'ambiance de [Pixabay](https://pixabay.com), sous la licence de contenu Pixabay. Merci à leurs auteurs : Monk_Vadim, JusticeTran, LofiPlace, Joe_hackney et BackgroundsCHAIR (liens dans `NOTICE.md`).
