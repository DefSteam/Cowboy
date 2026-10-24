# Cowboy

Cowboy is a feedlot health tracker delivered as an installable Progressive Web App (PWA) and an Android APK. The canonical app UI, records, and feedlot configuration live in [`web/`](web/). Gradle copies those files into the Android app's generated assets, and Android opens that same app in a WebView. Make animal-workflow changes in `web/` only.

The Android wrapper retains Android-only functions for sharing debug crash reports and checking/installing APK releases. It does not contain a second animal-tracking UI or data model.

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

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Gradle synchronizes `web/` into generated Android assets before building, so edits to the shared app are included automatically. Install on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Host the web app

The static site is in `web/` and can be served by any HTTPS static host. For GitHub Pages, enable **GitHub Actions** as the Pages source and run the **Deploy Cowboy web app** workflow in the Actions tab.

The PWA shell is cached for offline startup. Animal records and settings are stored in local browser storage on each device; there is no shared cloud database yet. Use **Settings → Export backup** and **Import backup** to move data between devices.

## Release Android

Sign in with GitHub CLI (`gh auth login`), then run:

```sh
./release.sh
```

The script commits and pushes local source changes to `master`, builds the Android APK, and publishes `Cowboy-<version>.apk` to the matching GitHub Release. If that release tag already exists, it offers to increment `versionName` and `versionCode`. `Check for update` in the Android app downloads a newer compatible APK release.

Debug builds save uncaught Android crash details locally. In the Android app, open **Settings → Share crash report** to send the latest report through an app on the device.
