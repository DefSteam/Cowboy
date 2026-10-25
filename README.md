# Cowboy · Feedlot Health Tracker

Cowboy helps feedlot crews record sick cattle during pen checks, organize pulls and treatments, and follow animals through daily rechecks. It runs as an installable web app on Android and iPhone/iPad, with an Android APK for crews who prefer a native install.

Track pen and animal tags, diagnosis and symptom flags, animal appearance and color, treatment products, destination, notes, and daily history. Pens, diagnoses, symptoms, colors, products, and treatment rules can be configured for each feedlot.

The shared app interface, records, and feedlot configuration live in [`web/`](web/). Android separates these into a stable native shell and a downloadable web runtime. The APK contains a fallback copy of the runtime; signed runtime bundles can update the screens and workflows without rebuilding the APK.

The Android shell handles WebView hosting, file access, debug crash-report sharing, and APK installation. In Settings, one update button checks both the APK and runtime: runtime bundles download and activate on the next app launch, while APK updates keep the existing download and install flow. The PWA continues to use the hosted files and service worker.

## Test the web app

Run the local Python server from the repository root:

```sh
./web/serve.sh
```

Open `http://localhost:8080`. Choose another port with `./web/serve.sh 9000`. Stop the server with Ctrl+C. The script needs Python 3.

To install it, visit the HTTPS website from Chrome on Android and choose **Install app**. On iPhone or iPad, open it in Safari, tap **Share**, then **Add to Home Screen**.

## Build Android

Requirements: Java 17 or newer, Gradle, and Android SDK Platform 37. Build a debug APK with:

```sh
./build.sh
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Gradle packages `web/` as the shell's offline fallback runtime. Install on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Host the web app

The static site is in `web/` and can be served by any HTTPS static host. For GitHub Pages, enable **GitHub Actions** as the Pages source and run the **Deploy Cowboy web app** workflow in the Actions tab.

The PWA shell is cached for offline startup. Animal records and settings are stored in local browser storage on each device; there is no shared cloud database yet. Use **Settings → Export backup** and **Import backup** to move data between devices, or export/import feedlot configuration separately without animal records.

## Release updates

Sign in with GitHub CLI (`gh auth login`). Run the release script without an argument to compare local changes with the latest APK and runtime releases:

```sh
./release.sh
```

If Android shell files changed, it explains why an APK is needed and asks before building and publishing it. If web runtime files changed too, it publishes their signed runtime bundle after the APK. If only web files changed, it publishes a runtime update without building an APK. Use an explicit mode to choose manually:

```sh
./release.sh runtime
```

This increments `runtime/VERSION`, commits and pushes local source changes to `master`, packages `web/` into a versioned ZIP, signs a manifest that contains the bundle checksum, and publishes it with a single-file `.cowboy-runtime` installer package to a `runtime-vX.Y.Z` GitHub Release. It then pushes the signed latest-runtime pointer. Android verifies the signature and checksum before activating a downloaded runtime. The same signed installer package can be selected from **Settings → Advanced settings → Install runtime bundle**. Runtime releases do not build or upload an APK.

To explicitly publish only an APK:

```sh
./release.sh app
```

That builds the APK, checks its Android signature, package ID, version name, and version code, then pushes the source and publishes `Cowboy-<version>.apk` to a `v<version>` release. The Android app update checker filters out runtime-only releases, and the installer verifies the downloaded APK has the same signing certificate as the installed app before opening Android's installer.

The first rollout of this split requires one APK release because the currently installed shell does not yet know how to load or verify runtime bundles. After that shell is installed, routine `web/` changes can use runtime releases alone.

Runtime signing uses the private key at `.secrets/runtime-signing-private.pem` (or the path in `COWBOY_RUNTIME_SIGNING_KEY`). The public verification key is embedded in the APK at `app/src/main/res/raw/runtime_signing_public.pem`. Back up the private key securely; losing it requires an APK update to install a replacement public key. The private key is ignored by Git and must never be committed.

Debug builds save uncaught Android crash details locally. In the Android app, open **Settings → Share crash report** to send the latest report through an app on the device.
