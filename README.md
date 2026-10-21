# Plane Spotter

An Android app for a young aviation fan in Melbourne. It covers 95 aircraft, from the Qantas 737s and Jetstar A321s he'll see every week at Tullamarine to the Wright Flyer, Concorde, the SR-71 and the Australian-built MQ-28 Ghost Bat.

Each aircraft has a real photo, full specifications, fun facts, tips on how to recognise it, and where to spot it around Melbourne and Victoria. The app also has:

- a "Spot in Melbourne" list of 48 aircraft he's likely to see locally
- a spotter's log to tick off aircraft he has seen, with progress and badges
- a 10-question quiz (name the aircraft from its photo, which is faster, which flew first)
- a compare screen that puts any two aircraft head to head
- a Melbourne spotting guide covering Tullamarine, Avalon, Essendon Fields, Moorabbin, Point Cook and East Sale

Everything works offline. The app only goes online to check for updates.

## Installing

1. On the Android phone or tablet, open the [latest release](https://github.com/nugggy/plane-spotter/releases/latest) and download the `.apk` file.
2. Open the downloaded file. Android will ask to allow installs from the browser or file manager. Allow it, then tap Install.

Android 8.0 or newer is needed.

## Updates

The update channel is this repo's GitHub Releases. Every time the app opens, it checks the latest release. If the release is newer than the installed version, it offers to download and install it. The spotter's log and quiz score are kept. The first update will ask for permission to install updates from Plane Spotter itself.

To publish an update (on the build computer):

```powershell
.\scripts\release.ps1 -Version 1.1.0 -Notes "What changed, in a sentence or two"
```

This bumps the version, rebuilds the data, builds a signed APK, commits, tags, pushes and creates the GitHub release.

Every release must be signed with the same key (`keystore/plane-spotter-release.jks` plus `keystore.properties`). These files are deliberately not in the repo. If they are lost, installed copies cannot be updated, and the app would have to be uninstalled and reinstalled, losing the spotter's log.

## Building

Needs Android Studio (for the SDK and its bundled JDK) and Node.js.

```bash
node data/build_data.js          # merge data/parts/*.json and photo credits into app assets
./gradlew assembleRelease        # signed if keystore.properties exists
```

## Data and photos

- `data/master_list.tsv` is the list of aircraft, their categories and whether they're seen around Melbourne.
- `data/parts/*.json` holds the researched specifications and text. These were checked against English Wikipedia in October 2026 and rounded.
- `data/photos.json` lists the chosen Wikimedia Commons photo for each aircraft, with author and licence. `data/download_photos.js` fetches them at 1024 pixels wide.

All photos are from Wikimedia Commons and are used under their own licences (Creative Commons, GFDL, public domain and others). See [CREDITS.md](CREDITS.md) and the About screen in the app for per-photo credits.
