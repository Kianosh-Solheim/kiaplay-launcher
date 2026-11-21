# Kiaplay

A retro, 80s-style home screen for **Chromecast with Google TV**. Black background, cream and red, CRT scanlines and film grain, and a startup ident that switches on like an old picture tube.

## What it does

- **Startup animation** with synthesised sound, every time the TV turns on, only after a restart, or never. Length, sound, volume and skip-on-key can be set.
- **Home**: a featured "keep watching" title with backdrop, finish time ("ferdig kl. 21:47") and progress, a row of covers and your favourite apps.
- **Films, series and music** straight from your own **Jellyfin** server: genre filters, sorting, seasons and episodes, favourite and watched toggles.
- **Apps**: every installed TV app, with favourites that show up on the home screen.
- **Quick menu** (☰ or the Menu key): TV settings, Wi‑Fi and volume.
- **Weather** from MET Norway for Bergen, Oslo, Trondheim, Stavanger or Tromsø.
- **CRT effects** (scanlines, rounded corners, grain) that can be switched off.

## Install

1. Download `kiaplay.apk` from the latest [release](../../releases) (or the Actions artifact) and install it with `adb install kiaplay.apk`.
2. Make Kiaplay the home screen. Google TV does not let you pick another launcher, so the stock one has to be switched off over adb:

   ```sh
   adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   ```

   To undo it, run the same commands with `pm enable` instead.
3. Open **Innstillinger** in Kiaplay and log in to Jellyfin (address like `192.168.1.20:8096`).
4. Add the repo to **Obtainium** to get updates.

## Playback

"Spill av" sends the film to an external player such as VLC, starting where you left off. If no player is installed, or "Jellyfin-appen" is chosen in the settings, the Jellyfin app opens instead. Progress made in an external player is not reported back to Jellyfin.

## Building

GitHub Actions builds a release APK on every push to `main`. Signed releases need these repository secrets: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`. Without them the build still runs, but the APK is signed with a throwaway debug key and is only uploaded as an Actions artifact, not as a release.

Fonts: Anton and VT323, both under the SIL Open Font License.
