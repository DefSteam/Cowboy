# Cowboy

Cowboy records sick animals found during feedlot pen walks and tracks pulling, treatment, and destination pens. Records and feedlot settings are stored on the device, so the app works offline.

## First-run setup

Open **Settings** from the top app bar's more menu and enter all pens under East or West, one per line. Pen names can include letters, numbers, or combined identifiers such as `2 and 15`. Pens are selected through an East/West section picker and a tap-friendly grid. The health issue list order is the order shown when entering an animal. Destination choices can include their purpose, for example `8 — Hospital` or `44SP — Buller / fat ration`.

The setup screen can export a versioned JSON configuration to share with another device, or import one. Configuration files contain choice lists, not animal records.

The interface uses Google's Material Components for Android, including a top app bar, Material buttons, cards, input fields, and selection dialogs.

## Debug crash reports

Debug builds save uncaught crash details on the device, including the app version, Android version, device model, crashing thread, and Java stack trace. The next time the app opens, it offers to share the report. You can also use **Share crash report** from the top app bar menu. Reports stay on the device unless you choose a share destination; no crash upload service is configured.

## Daily workflow

- **Enter new sick animal:** record its found-in pen, health issue, color, regular tag if visible, notes, and any existing treatment tag.
- **Today's pull and treatment list:** mark animals pulled, record treatment and notes, add a treatment tag if needed, and select a destination pen.
- **History:** review each day's records and follow-up status.

## Build and publish a release locally

Requirements: Java 17 or newer, Gradle 9.6 or newer, Android SDK Platform 37, and GitHub CLI (`gh`). Sign in once with `gh auth login`.

Build a debug APK for local testing:

```sh
./build.sh
```

Install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To build and publish a GitHub Release with the APK attached:

```sh
./release.sh
```

The script stages and commits local changes, pushes the current branch to `origin`, then builds the APK locally and creates release tag `v<versionName>` at that pushed commit. It uploads the APK as `Cowboy-<versionName>.apk` and `Cowboy.apk` (the compatibility name for older app builds). Increase both `versionName` and `versionCode` for each release. The default release repository is `DefSteam/Cowboy`; set `COWBOY_RELEASE_REPO=OWNER/REPO` to publish to a different repository.

If that release tag already exists, `release.sh` offers to add a `Cowboy.apk` compatibility asset to the existing release or increment the patch version (for example, `0.1.0` to `0.1.1`) and `versionCode` before pushing and publishing the next release.

**Check for update** asks GitHub for the latest public release. If a newer `v<versionName>` release is available, Cowboy downloads its matching `Cowboy-<versionName>.apk` asset and opens Android’s package installer. The first update may ask you to allow installs from Cowboy in Android settings. The update APK must be signed with the same local debug key as the installed app; building and releasing from the same computer preserves that key. The repository must be public for anonymous in-app checks and downloads.
