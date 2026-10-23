# Cowboy

Cowboy records sick animals found during feedlot pen walks and tracks pulling, treatment, and destination pens. Records and feedlot settings are stored on the device, so the app works offline.

## First-run setup

Open **Settings** from the top app bar's more menu and enter all pens under East or West, one per line. Pen names can include letters, numbers, or combined identifiers such as `2 and 15`. Pens are selected through an East/West section picker and a tap-friendly grid. The health issue list order is the order shown when entering an animal. Destination choices can include their purpose, for example `8 — Hospital` or `44SP — Buller / fat ration`.

The setup screen can export a versioned JSON configuration to share with another device, or import one. Configuration files contain choice lists, not animal records.

The interface uses Google's Material Components for Android, including a top app bar, Material buttons, cards, input fields, and selection dialogs.

## Daily workflow

- **Enter new sick animal:** record its found-in pen, health issue, color, regular tag if visible, notes, and any existing treatment tag.
- **Today's pull and treatment list:** mark animals pulled, record treatment and notes, add a treatment tag if needed, and select a destination pen.
- **History:** review each day's records and follow-up status.

## Build a debug APK

Requirements: Java 17 or newer, Gradle 9.6 or newer, and Android SDK Platform 37.

```sh
./build.sh
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. You can install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To run another Gradle task, pass its Gradle path as the first argument, for example `./build.sh :app:clean`.
