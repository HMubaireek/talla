# Bayti Auto · بيتي في السيارة: notes for Claude

The owner's personal Android Auto app. It is the in-car companion of Bayti TV (https://github.com/HMubaireek/bayti-tv, local
clone `C:\BaytiTV`; read it, never change it from this repo's work). It runs on the owner's phone and draws a Bayti-style screen
on the car's display.

## The owner and how they work

- They write in Arabic or English; reply in the language they use. The UI text is Arabic, right to left.
- **Ask before outward actions:** creating repos, publishing releases, installing tools on the PC or the phone, downloading
  third-party APKs. When asking about a download, give the file name, source and size.
- Shia: prayer times use the **Jafari** method by default. Home: **Al-Mallaha village, Al-Qatif, Eastern Province, Saudi Arabia**
  (26.5132, 49.9988, Asia/Riyadh, from OpenStreetMap).
- Football team: **Al-Hilal** (TheSportsDB id 136013).
- Their own photos: use only folders they name. The app reads a folder they pick on the phone (SAF tree URI). Never bundle their
  photos in the repo.
- Git email hmubaireek@gmail.com is set globally. Push over **SSH** (`git@github.com:HMubaireek/bayti-auto.git`): HTTPS push
  has no credentials.

## The car and the phone

- **Car:** 2019 Mazda CX-9, Mazda Connect, wired Android Auto. 8" screen, about 800 × 480 (not confirmed); the layout scales to
  any size. The touchscreen is locked while moving, so input is the commander knob.
- **Phone:** Samsung Galaxy S24+ (SM-S926B), **Android 17** (SDK 37), Android Auto **18.0**, adb serial `RZCXB0B1YNF`. It has a
  managed work profile (Android Device Policy / clouddpc; extra users 11 and 150), so `adb shell` commands touching every user can
  fail with "Shell does not have permission to access user 150".
- **Seen in the car** (photos, 2026-10-30). The surface is the screen right of Android Auto's rail; in split view the surface stays
  full size and the visible area shrinks to the left ~2/3 panel. The stable area leaves out a band at the top for the action strip
  and once came with a large left inset, which moved the whole layout, so only the visible area's sides are used now. If the car
  shows something wrong, ask for a photo of the screen.

## Why it is built this way

- Android Auto apps use the Jetpack Car App Library (`androidx.car.app`). Templates mean system fonts and no custom drawing.
  Only map-type templates give a drawing Surface (needs `androidx.car.app.ACCESS_SURFACE`).
- The **weather** category's `MapWithContentTemplate` *requires* a content template, which puts a list panel over part of the
  screen. So the app is in the **NAVIGATION** category and uses `NavigationTemplate`: the whole screen is ours, with only an
  action strip that Android Auto auto-hides. Google Play wouldn't accept that for a non-navigation app; it's a personal app, so
  that doesn't matter.
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (personal app). `minCarApiLevel` 1.

## Installing on the phone: read this before any update

Android Auto (17.x/18.x) shows a sideloaded Car App Library app only when its install record says **installer =
`com.android.vending`** and **initiating package = the system package installer** (not `com.android.shell`). The "Unknown sources"
developer switch doesn't cover template apps.

Tried on this phone, 2026-10-30:

- **adb from the laptop does NOT work** on this Android 17 build:
  - `am start ... InstallStart ... --es android.intent.extra.INSTALLER_PACKAGE_NAME com.android.vending` gets
    *"The given installer package name com.android.vending is invalid. Remove it."*
  - a `file://` URI gets *"Invalid URI: file"*.
- **What works: UnknownAuto** (https://github.com/GitMag/UnknownAuto, MIT; v1.1 installed on the phone; signer SHA-256
  `fcd1ea20…e604`).
  - Its first, Shizuku/shell attempt *loses* the Play mark on this phone.
  - Then tap **Update**: its second way (the app itself opens the installer) keeps it. Expect to tap Update every time.
- **Shizuku must be running** for UnknownAuto. Its service dies on reboot and sometimes on its own. With the phone on USB, start it
  from Git Bash:
  ```bash
  export MSYS_NO_PATHCONV=1; A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
  P=$("$A" shell pm path moe.shizuku.privileged.api | tr -d '\r' | sed 's/^package://; s#/base.apk$##')
  "$A" shell "$P/lib/arm64/libshizuku.so"     # then: "$A" shell 'ps -A | grep shizuku_server'
  ```
  The owner can also start it with Shizuku's *Start via Wireless debugging*. That route needs pairing first; the adb start doesn't.
- **Update steps:**
  1. Download the new APK from the apk-latest release.
  2. `adb push 'C:\...\bayti-auto.apk' /sdcard/Download/bayti-auto.apk` (with `MSYS_NO_PATHCONV=1`).
  3. Start Shizuku.
  4. The owner, in UnknownAuto: *Load APK…* → Download → bayti-auto.apk → Install, then **Update**.
- **Check the result:**
  ```bash
  "$A" shell 'dumpsys package sa.bayti.auto | grep -E "versionName|installerPackageName|initiatingPackageName|lastUpdateTime"'
  ```
  Good: `installerPackageName=com.android.vending` and `initiatingPackageName=com.google.android.packageinstaller`.
- If the car doesn't list the app after an update: force-stop Android Auto on the phone and reconnect, because Android Auto caches
  its decision per app version.
- Fallback if this ever stops working: a $25 Play developer account with an internal test track.

## Build, sign, release

- Gradle 8.9 wrapper, AGP 8.5.2, compileSdk/targetSdk 34, minSdk 26, Java 8 source level, `androidx.car.app:app:1.7.0`. The
  only dependency is the Car App Library; the rest is plain framework views.
- **Local build** (needs JDK 17; the default `java` on this PC is 18):
  ```bash
  cd /c/BaytiAuto && export JAVA_HOME="/c/Program Files/Java/jdk-17"
  ANDROID_KEYSTORE=/c/Users/hmuba/bayti-auto-signing-key.p12 \
  ANDROID_KEYSTORE_PASSWORD="$(cat /c/Users/hmuba/bayti-auto-signing-key-password.txt)" ./gradlew --no-daemon -q assembleRelease
  ```
  Without `ANDROID_KEYSTORE` it signs with the debug key, which **can't update** the installed app.
- `local.properties` (gitignored) must be written with the Write tool, because Bash halves the backslashes:
  `sdk.dir=C\:\\Users\\hmuba\\AppData\\Local\\Android\\Sdk`.
- **Signing key:**
  - Files: `C:\Users\hmuba\bayti-auto-signing-key.p12` + `bayti-auto-signing-key-password.txt`, alias `baytiauto`, PKCS12.
  - Cert SHA-256: `de31369901481f99f23024e4c29e18c5f09973a4ce56dce111c1fa65bc330cb0`.
  - Never commit them or print the password. The repo secrets `ANDROID_KEYSTORE_B64` and `ANDROID_KEYSTORE_PASSWORD` hold the
    same key.
- **CI:** `.github/workflows/android.yml` runs on push to `master` (or by hand). It builds, then publishes `bayti-auto.apk` to
  the **apk-latest** release: https://github.com/HMubaireek/bayti-auto/releases/download/apk-latest/bayti-auto.apk.
  `versionCode` = `GITHUB_RUN_NUMBER`, `versionName` = `1.<run>`.
- The `gh` CLI here is **old**:
  - `gh repo create` has no `--source`.
  - `gh run list` has no `--json`.
  - `gh run rerun` has no `--failed`.
  - Pass `-R HMubaireek/bayti-auto`.
  - `gh run watch <id> -R … --exit-status` works.
- `gradlew` must stay executable in git (`git update-index --chmod=+x gradlew`); Windows git doesn't track the mode by itself.

## Checking changes without the car

- Run the emulator in the background:
  ```bash
  "$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd Pixel_5_API_30 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect
  ```
- Install, `am start -n sa.bayti.auto/.SettingsActivity`, and take screenshots with `adb exec-out screencap -p > file.png`.
- The settings screen's preview (`PreviewView`) draws the **same `Renderer`** at 800 × 480, so it shows what the car will.
- Stop the emulator with `adb emu kill`. With both the phone and the emulator attached, pass `-s <serial>` to adb.
- No Desktop Head Unit is installed. It would need Android Auto on the emulator, which the Google APIs image lacks.

## Code map (`app/src/main/java/sa/bayti/auto`)

| File | What it does |
|---|---|
| `CarService` | The `CarAppService` with one `Session`, whose screen is `DashScreen`. |
| `DashScreen` | `NavigationTemplate` with one pale icon action, `ic_next_photo` = next background (the template requires an action strip and the strip needs one action; the host draws its chip and hides it after a few seconds). Implements `SurfaceCallback` (`lockHardwareCanvas` on the surface; visible area, sides only; `onFling` = next carousel photo). Dims by `night_dim` while `CarContext.isDarkMode()`; `CarService`'s session redraws on `onCarConfigurationChanged`. |
| `Hub` | Singleton that owns the state on one HandlerThread, `bayti-draw`; every drawing happens on it. Two IO threads fetch and decode. Frames: ~30 fps only while a fade, settle or push-in runs, otherwise one per minute and on new data. A `Deck` = the photo list, index, cur/prev bitmaps and timing for the background or the carousel. A prefs listener redraws at once when the phone's settings change. |
| `Renderer` | Draws everything on a Canvas, laid out on a 480-unit-high design space: background photo (cover at 26% from the top; 60 s settle from 1.055 to 1.03 scale; darkness 1-d; 2 s crossfade), veil gradients, grain, top and bottom veils. Right column: Hijri, Gregorian, gold rule, 112-unit clock, the phrase. Left: weather, then the carousel (rounded 16:9, 650 ms fade, 9 s push-in from 1.06, dots with a gold dash). Bottom corners, straight on the photo (no card backgrounds): the next prayer on the right (only in the hour before it unless `prayer_soon` is off), Al-Hilal's match on the left. Under the weather, one amber weather warning (`Weather.warning`). Night: the photo dims by the full `dim`, the words by a third of it. The photo is centred on the visible panel; type shrinks to 0.8 in a narrow panel and the clock to fit its column. |
| `Settings` | All preferences (file `bayti_auto`): `show_<item>` toggles, `greet_morning/day/evening/night`, method, hide_asr/isha, prayer_soon, city/lat/lon/tz, weather_here, digits, clock24, hijri_adj, motion, night_dim (percent), bg/carousel sources and seconds, own_folder. |
| `SettingsActivity` + `PreviewView` | The phone UI: plain framework views, RTL, Tajawal, live preview (switches show it as the split view and at night). |
| `PrayerCalc` / `Prayers` | Our own solar-position prayer arithmetic (matches Aladhan within 1 minute for Jafari and Umm al-Qura, tested on 4 dates), and the next prayer / part of the day. |
| `Texts` | Arabic digits, Hijri date (ICU islamic-umalqura + adjustment), Gregorian date, clock, sky words, «بعد ساعتين و…», match day words. |
| `Weather` | Open-Meteo forecast + air quality (dust), and the next 3 hours' worst code, strongest gust and lowest visibility for `warning()` (storms, thick dust, visibility < 1 km, heavy rain, gusts ≥ 50 km/h, fog, dust, high ≥ 47°);  the phone's last known location (no new GPS fix) with Geocoder for an Arabic place name; every 30 minutes; hidden when older than 3 hours. |
| `Hilal` | TheSportsDB `eventsnext.php?id=136013` with free key `123` (key `3` is dead); `strTimestamp` is UTC; our own Arabic club-name table; competition names in Arabic. |
| `PhotoSources` | Copied from Bayti TV's screensaver (post-fork, so MIT is fine): Saudi set (bayti-tv repo photos/index.json), Wikimedia Commons featured categories, POTD, Bing. |
| `Photos` | The source lists + the own SAF folder, a daily shuffle, a download cache (`files/photos`, 80 kept), and decoding at the drawn size. |

## Licence care: important

Bayti TV is a fork of UrTV (tshi-dev), **which has no licence**. Only Bayti TV's own post-fork additions (after commit
`51b9680`) are MIT. Before copying anything from `C:\BaytiTV`, check its origin:

```bash
git log --diff-filter=A --follow --format=%h -- <file> | tail -1   # then: git merge-base --is-ancestor 51b9680 <hash>
```

Known UrTV (don't copy):
- the launcher's `PrayerMath.java`, `GreetingText.java` and `PrayerTimes.java`;
- `assets/shared/teams-ar.json`;
- most of `src/template.html`.

The photo look (veil, grain, settle) is reimplemented here in our own Java, keeping only the colours and timings.

Known Bayti-own (fine to reuse): `Carousel.java`, `PhotoSources.java`, `Prefs.GREET_PARTS`, `Prefs.PRAYER_METHODS`, the Tajawal
files (also OFL).

## Windows / Git Bash gotchas

- Set `MSYS_NO_PATHCONV=1` for adb commands with device paths. Otherwise `/data/...` becomes `C:/Program Files/Git/data/...`.
- Heredoc Python edits halve backslashes, so use the Edit tool for strings with backslashes. Python `open('w')` without
  `newline=''` writes CRLF.
- The shell's cwd resets between calls, so use absolute paths.
- The TMDB key from Bayti TV is never put in a repo or printed (not used here).

## Ideas not done yet

- Check the new layout (corners, no buttons, split view) on the real car screen.
- An option for a second team, or several.
- A "show credits" line for Wikimedia photos (Bayti TV shows them small at the bottom left).
- Bayti as a media app too (its dashboard as the media card's art), so it could sit beside Google Maps in split view; it would
  take the media card from YouTube Music. Asked the owner, 2026-10-31.
