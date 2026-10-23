# Ironfield

A War Thunder-style top-down tank and aircraft game. Play it in the browser, on PC or phone:

**https://umvtt.github.io/ironfield/**

- It always has the newest version: refresh the page to update.
- **On phones:** open the link in Chrome or Safari, then **Add to Home Screen** (or the **Install on this device** button in the hangar). It then opens full screen from its own icon, like an app.
- **Co-op:** one player hosts a room and shares the 6-character code; up to 14 players. The host can kick players and lock the room from the room view or the in-battle menu.
- **Offline copy for PC:** every release on GitHub has `ironfield.html`. Save it and double-click it. When a newer version is out it shows a **Game update available** banner.

Saves stay in each browser (local storage), so use the same browser to keep your progress.

## Publish a game update

1. Change `web/index.html` and commit to `main`. The first line of the commit message is shown to players as "What's new".
2. **Publish game update** deploys the site to GitHub Pages in about a minute, and stamps the build number shown in the hangar's corner.
3. **Release** makes a GitHub Release (tag `game-<build>`) with a fresh `ironfield.html`.

## One-time setup

1. The repository must be **Public** (GitHub Pages is free for public repositories).
2. **Settings → Pages → Build and deployment → Source:** choose **GitHub Actions**.
3. Run **Actions → Publish game update → Run workflow** once.

## Old Android app

The Android app has been retired; the website is the way to play. Phones that still have the app keep working: it loads the game from the website and still offers game updates. They just won't be offered new APKs. Players can uninstall it and use the website (or install it from the browser) instead.
