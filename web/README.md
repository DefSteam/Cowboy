# Cowboy installable web app

This is a static Progressive Web App (PWA). It can be hosted from any HTTPS static host; no server-side build or database is required. GitHub Pages is one option. Serve the contents of `web/` as the site root, or configure the host to serve this directory.

## Run locally

From the repository root, run `python3 -m http.server 8080 --directory web` and open `http://localhost:8080`. Service workers require HTTPS in production (localhost is allowed for development).

## Install

- Android: open the HTTPS site in Chrome and use the browser's Install app / Add to Home screen option.
- iPhone/iPad: open the HTTPS site in Safari, tap Share, then Add to Home Screen.

## Data and hosting notes

The app shell is cached for offline startup. Records and configuration are kept in browser local storage on each device. This initial version does not synchronize between devices; use Settings → Export backup to save a JSON copy. Add a shared backend before relying on multiple devices seeing the same live records.

The native Android project remains at the repository root and is independent of this web app.
