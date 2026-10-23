# Ironfield

A War Thunder-style tank game. Every release on GitHub has two downloads:

- **`ironfield.apk`** for Android: a small app that runs the game full screen.
- **`ironfield.html`** for PC: double-click it to play in any browser. When a newer version is out, it shows a **Game update available** banner with a **Download** button.

You can also play it in the browser at `https://umvtt.github.io/ironfield/`, which always has the newest version.

- **Game updates:** replace `web/index.html` on GitHub. The next time players open the app they see **Game update available** with your commit message as "What's new". They tap **Update** and the game downloads and reloads, with progress kept. No reinstall.
- **App updates:** these are rare and only needed if the Android shell itself changes. GitHub builds a new APK, and the installed app pops up "App update available" with a download button.
- **Offline:** the app plays the last version the player installed, or the copy built into the APK. Co-op needs internet anyway.

Everything is free: a GitHub account, GitHub Pages and GitHub Actions.

## One-time setup (about 15 minutes)

1. **Make a GitHub account** at github.com if you don't have one.
2. **Create a repository** named `ironfield`. It must be **Public**, because GitHub Pages is only free for public repositories. Don't add a README.
3. **Upload this folder's contents.** On the empty repository page, click **uploading an existing file** and drag in everything from this folder: `web`, `android`, `.github`, `README.md` and `.gitignore`. Then click **Commit changes**.
   - The `.github` folder is hidden on some computers. Turn on "show hidden files", or upload the files inside `.github/workflows` by hand into a folder you name `.github/workflows`.
4. **Turn on the website.** Go to **Settings → Pages → Build and deployment → Source** and choose **GitHub Actions**.
5. **Add the signing key.** Open the separate `ironfield-signing-key-KEEP-PRIVATE` folder and follow `READ-ME-FIRST.txt`. This adds two secrets under **Settings → Secrets and variables → Actions**.
6. **Publish the game.** Go to the **Actions** tab, then **Publish game update → Run workflow**. After a minute the game is live at `https://umvtt.github.io/ironfield/`, and it also works in any browser.
7. **Make the first release.** In **Actions**, run **Release → Run workflow**. It takes about 5 minutes. Then `ironfield.apk` and `ironfield.html` appear under **Releases** on the repository's main page.

## Give the game to people

- Android: `https://github.com/Umvtt/ironfield/releases/latest/download/ironfield.apk`. On the phone they open it, allow "install unknown apps" for their browser when Android asks, and install.
- PC: `https://github.com/Umvtt/ironfield/releases/latest/download/ironfield.html`. They save it anywhere and double-click it.
- Or send the releases page, `https://github.com/Umvtt/ironfield/releases/latest`, which lists both.

## Publish a game update

1. Rename the new `ironfield.html` to `index.html`. On GitHub, open the `web` folder, choose **Add file → Upload files**, and drop it in. It replaces the old one.
2. Commit. The commit message's first line is what players see as "What's new", so make it short and clear. The **Publish game update** workflow runs by itself.
3. About a minute later, Android players get the update prompt the next time they open the app, and PC players opening their `ironfield.html` see the update banner. The **Release** workflow also makes a new release with fresh `.apk` and `.html` files, ready about 5 minutes after you commit.

## Publish an app update (rarely needed)

Change anything under `android/` and commit, or run **Release** by hand. When `android/` has changed, installed apps offer the new APK when they start. Saved progress is kept because the app is signed with the same key. Release tags are `v<app>.<build>`. The first number only goes up when `android/` changes, so game-only releases don't ask Android players to reinstall.

## Notes

- Saves stay on each device. On PC, the browser keeps them, so replacing `ironfield.html` with a new download keeps your progress if you open it in the same browser.
- The Android back button works like Esc in the game. In the hangar, it closes the app.
- Google Play is a separate step: it needs a $25 developer account and a store listing. This setup is for sharing the APK directly.
