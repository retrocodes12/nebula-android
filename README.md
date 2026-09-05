# Nebula for Android

The Android surface of [Nebula](https://github.com/retrocodes12/nebula-player), a streaming
player for the TV, the desktop and the phone. One native app for phones and Android TV: add your
add-on, browse its catalogs, press play. Encrypted streams are decrypted on the device itself.

**Site:** https://play.rifflehq.in · **APK:** https://github.com/retrocodes12/nebula-android/releases/latest/download/Nebula.apk

## Install

- **Phone / tablet:** download `Nebula.apk` from the latest release and open it.
- **Android TV / Fire TV:** in the Downloader app enter the code **7664693** (or the short link
  `da.gd/nebulaapk`). The app appears in the TV launcher.
- **Updates:** the app checks the latest release and shows a card when a newer one exists.

Android 8.0 (API 26) or newer.

## What it does

- **Add-ons.** Point it at an add-on and its catalogs, title pages, streams and subtitles show up.
  Several add-ons combine into one row of streams, labelled by resolution, size and language.
- **Playback.** Adaptive and encrypted streams play natively; quality, audio track, subtitle and
  speed pickers; add-on subtitles with your own styling and a timing nudge; a picture-quality
  policy (auto, best available, data saver); picture-in-picture.
- **P2P streams.** Some add-ons answer with a file shared between viewers rather than a link.
  Those streams are listed and played by a BitTorrent engine running on the phone, which pulls the
  file in order and feeds it to the player over loopback. Off until you turn it on in Settings ›
  Streams, because while one plays every other peer in that swarm can see your address.
- **The quiet parts.** Pause for a moment and a board fades in with the title, synopsis, time
  left and what plays next. A playback HUD shows resolution, bitrate, buffer and codec.
- **Instant next episode.** The app remembers which source you chose for a show and resolves the
  next episode's stream during the last minutes, so autoplay starts without a source list.
- **Evenings.** A sleep timer that pauses after 15–90 minutes or when the episode ends, and
  survives the hop into the next one. Start over from the streams page or a Continue Watching
  card. Surprise me picks a random aired episode.
- **Series.** One page per show: seasons, episodes, air dates, and a watch cursor that knows
  where you left off.
- **My List, Continue Watching, ratings, an upcoming-episode calendar.** Home rows can be hidden
  and reordered in Settings; Search remembers your recent looks.
- **Nebula Profile.** An @handle and a password — no email. Add-ons, progress, My List and
  ratings follow you to every device; the TV signs in with a short code you approve from a phone.
  A profile is optional; everything works without one.
- **Support Nebula.** Optional, and nothing that exists sits behind it. A supporter gets a small mark
  beside their name, three more accent colours, and their name on the wall if they choose. The row
  appears once a support link is configured on the server.
- **Friends and watch parties.** Find each other by @handle, see what friends rate, recommend a
  title to one; a short code puts everyone on the same second, live streams included.
- **Made for the couch.** Full D-pad navigation on Android TV; touch and long-press on phones.

## Design

Apple's tvOS player is the reference: flat near-black, one accent, hairline panels, two type
registers, white focus rings, glass player chrome. No gradients, no glow. The web, webOS and
Windows builds share one player; this app draws the same screens in Jetpack Compose.

## Repo layout

```
app/src/main/kotlin/com/nuvio/ckplayer/
  MainActivity.kt      navigation stack and every screen
  PlayerChrome.kt      glass player chrome, pause board, playback HUD, subtitle timing panel
  PlayerExtras.kt      playback info rows, codec names, subtitle re-timing
  NextEpisode.kt       instant next episode: stream fingerprinting and the remembered pick
  Stremio.kt           add-on protocol client (manifests, catalogs, meta, streams, subtitles)
  P2p.kt               P2P streams: the torrent engine, its settings and the preparing sheet
  P2pServer.kt         loopback HTTP server that turns downloading pieces into a seekable file
  StreamBadges.kt      stream labels (resolution, size, language, source)
  HomeRows.kt          Home row order and visibility        SearchRecents.kt  recent searches
  Cloud.kt / Account.kt / Profile.kt / Social.kt            profile, sync, friends
  Party.kt             watch-party socket                   Updates.kt        release check
  Library.kt / Progress.kt / Ratings.kt / Prefs.kt / SubStyle.kt / SubStylePanel.kt
.github/workflows/     build.yml releases on every push to main; ci.yml builds branches
```

Kotlin, Jetpack Compose, Media3 ExoPlayer. `compileSdk 36`, `targetSdk 34`, `minSdk 26`.

## Building

Releases are built, signed and published by GitHub Actions on every push to `main`
(`gradle assembleRelease` with the signing keystore from repository secrets). Locally, JDK 17 and
an Android SDK with platform 36 are enough for `gradle assembleDebug`.

## Security

See [SECURITY.md](SECURITY.md).
