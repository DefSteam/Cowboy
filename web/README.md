# Cowboy installable web app

This directory is the single source for Cowboy's interface, records, and feedlot configuration. It is a static Progressive Web App (PWA) that can be hosted from any HTTPS static host; no server-side build or database is required. The Android Gradle build packages these exact files into the APK. The repository includes a manual GitHub Pages workflow to publish this directory.

## Run locally

From the repository root, run `./web/serve.sh` and open `http://localhost:8080`. Pass a port to use another one, for example `./web/serve.sh 9000`. Service workers require HTTPS in production (localhost is allowed for development).

## Install

- Android: open the HTTPS site in Chrome and use the browser's Install app / Add to Home screen option.
- iPhone/iPad: open the HTTPS site in Safari, tap Share, then Add to Home Screen.

## Publish with GitHub Pages

In the repository settings, enable GitHub Pages with **GitHub Actions** as the source. Then open Actions → **Deploy Cowboy web app** → Run workflow and select this branch. The first run publishes the site and provides its URL in the Pages environment.

## Data and hosting notes

The app shell is cached for offline startup. Records and configuration are kept in browser local storage on each device. This initial version does not synchronize between devices; use Settings → Export backup to save a JSON copy. Add a shared backend before relying on multiple devices seeing the same live records.

The Android project at the repository root is a small WebView shell around these assets. It provides the Android release updater and debug crash-report sharing without duplicating the feedlot interface or data model.
